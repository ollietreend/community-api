package uk.gov.justice.digital.delius.service;

import com.microsoft.applicationinsights.TelemetryClient;
import io.vavr.control.Either;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.delius.config.FeatureSwitches;
import uk.gov.justice.digital.delius.controller.BadRequestException;
import uk.gov.justice.digital.delius.controller.NotFoundException;
import uk.gov.justice.digital.delius.data.api.Custody;
import uk.gov.justice.digital.delius.data.api.UpdateCustody;
import uk.gov.justice.digital.delius.data.api.UpdateCustodyBookingNumber;
import uk.gov.justice.digital.delius.jpa.standard.entity.CustodyHistory;
import uk.gov.justice.digital.delius.jpa.standard.entity.Event;
import uk.gov.justice.digital.delius.jpa.standard.entity.Offender;
import uk.gov.justice.digital.delius.jpa.standard.entity.RInstitution;
import uk.gov.justice.digital.delius.jpa.standard.repository.CustodyHistoryRepository;
import uk.gov.justice.digital.delius.jpa.standard.repository.InstitutionRepository;
import uk.gov.justice.digital.delius.jpa.standard.repository.OffenderRepository;
import uk.gov.justice.digital.delius.jpa.standard.repository.OffenderRepository.DuplicateOffenderException;
import uk.gov.justice.digital.delius.service.ConvictionService.DuplicateActiveCustodialConvictionsException;
import uk.gov.justice.digital.delius.transformers.ConvictionTransformer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static uk.gov.justice.digital.delius.service.CustodyService.PrisonLocationUpdateError.Reason.ConvictionNotFound;
import static uk.gov.justice.digital.delius.service.CustodyService.PrisonLocationUpdateError.Reason.MultipleCustodialSentences;
import static uk.gov.justice.digital.delius.service.CustodyService.PrisonLocationUpdateError.Reason.MultipleOffendersFound;
import static uk.gov.justice.digital.delius.service.CustodyService.PrisonLocationUpdateError.Reason.OffenderNotFound;

@Service
@Slf4j
public class CustodyService {
    private final Boolean updateCustodyFeatureSwitch;
    private final Boolean updateBookingNumberFeatureSwitch;
    private final TelemetryClient telemetryClient;
    private final OffenderRepository offenderRepository;
    private final ConvictionService convictionService;
    private final InstitutionRepository institutionRepository;
    private final CustodyHistoryRepository custodyHistoryRepository;
    private final ReferenceDataService referenceDataService;
    private final SpgNotificationService spgNotificationService;
    private final OffenderManagerService offenderManagerService;
    private final ContactService contactService;
    private final OffenderPrisonerService offenderPrisonerService;
    private final FeatureSwitches featureSwitches;

    public CustodyService(
            TelemetryClient telemetryClient,
            OffenderRepository offenderRepository,
            ConvictionService convictionService,
            InstitutionRepository institutionRepository,
            CustodyHistoryRepository custodyHistoryRepository,
            ReferenceDataService referenceDataService,
            SpgNotificationService spgNotificationService,
            OffenderManagerService offenderManagerService,
            ContactService contactService, OffenderPrisonerService offenderPrisonerService,
            FeatureSwitches featureSwitches) {
        this.updateCustodyFeatureSwitch = featureSwitches.getNoms().getUpdate().isCustody();
        this.updateBookingNumberFeatureSwitch = featureSwitches.getNoms().getUpdate().getBooking().isNumber();
        this.telemetryClient = telemetryClient;
        this.offenderRepository = offenderRepository;
        this.convictionService = convictionService;
        this.institutionRepository = institutionRepository;
        this.custodyHistoryRepository = custodyHistoryRepository;
        this.referenceDataService = referenceDataService;
        this.spgNotificationService = spgNotificationService;
        this.offenderManagerService = offenderManagerService;
        this.contactService = contactService;
        this.offenderPrisonerService = offenderPrisonerService;
        this.featureSwitches = featureSwitches;
    }

    @Transactional
    public Custody updateCustodyPrisonLocation(final String nomsNumber,
                                               final String bookingNumber,
                                               final UpdateCustody updateCustody) {
        final var telemetryProperties = Map.of("offenderNo", nomsNumber,
                "bookingNumber", bookingNumber,
                "toAgency", updateCustody.getNomsPrisonInstitutionCode());

        final var result = updateCustodyPrisonLocation(nomsNumber,
                this::getAllActiveCustodialEvents,
                updateCustody.getNomsPrisonInstitutionCode());
        return result.map(success -> {
            switch (success.outcome) {
                case Updated:
                    telemetryClient.trackEvent("P2PTransferPrisonUpdated", add(telemetryProperties, "updatedCount", String.valueOf(success.custodyRecordsUpdated.size())), null);
                    break;
                case NoUpdateRequired:
                    telemetryClient.trackEvent("P2PTransferPrisonUpdateIgnored", telemetryProperties, null);
                    break;
            }
            return success.custodyRecordsUpdated
                .stream()
                .max(Comparator.comparing(Custody::getSentenceStartDate))
                .orElseThrow();
        }).getOrElseThrow((error -> {
            switch (error.reason) {
                case TransferPrisonNotFound:
                    telemetryClient.trackEvent("P2PTransferPrisonNotFound", telemetryProperties, null);
                    break;
                case CustodialSentenceNotFoundInCorrectState:
                    telemetryClient.trackEvent("P2PTransferPrisonUpdateIgnored", telemetryProperties, null);
                    break;
                case ConvictionNotFound:
                    telemetryClient.trackEvent("P2PTransferBookingNumberNotFound", telemetryProperties, null);
                    break;
                case MultipleCustodialSentences:
                    telemetryClient.trackEvent("P2PTransferBookingNumberHasDuplicates", telemetryProperties, null);
                    break;
                case OffenderNotFound:
                    telemetryClient.trackEvent("P2PTransferOffenderNotFound", telemetryProperties, null);
                    break;
                case MultipleOffendersFound:
                    telemetryClient.trackEvent("P2PTransferMultipleOffendersFound", telemetryProperties, null);
                    break;
            }
            return new NotFoundException(error.getMessage());
        }));
    }

    @Transactional
    public void updateCustodyPrisonLocation(final String nomsNumber, final String nomsPrisonInstitutionCode) {
        final var telemetryProperties = Map.of("offenderNo", nomsNumber,
                "toAgency", nomsPrisonInstitutionCode);
        final var additionalTelemetryProperties = new HashMap<String, String>();

        final var result = updateCustodyPrisonLocation(nomsNumber,
                this::getAllActiveCustodialEvents,
                nomsPrisonInstitutionCode);
        final Optional<String> telemetryName = result.fold(error -> {
            switch (error.reason) {
                case TransferPrisonNotFound:
                    return Optional.of("POMLocationPrisonNotFound");
                case CustodialSentenceNotFoundInCorrectState:
                    return Optional.of("POMLocationCustodialStatusNotCorrect");
                case ConvictionNotFound:
                    return Optional.of("POMLocationNoEvents");
                case MultipleCustodialSentences:
                    return Optional.of("POMLocationMultipleEvents");
                case OffenderNotFound:
                    return Optional.of("POMLocationOffenderNotFound");
                case MultipleOffendersFound:
                    return Optional.of("POMLocationMultipleOffenders");
            }
            return Optional.empty();
        }, success -> {
            switch (success.outcome) {
                case Updated:
                    additionalTelemetryProperties.put("updatedCount", String.valueOf(success.custodyRecordsUpdated.size()));
                    return Optional.of("POMLocationUpdated");
                case NoUpdateRequired:
                    return Optional.of("POMLocationCorrect");
            }
            return Optional.empty();
        });

        telemetryName.ifPresent(name -> telemetryClient.trackEvent(name, add(telemetryProperties, additionalTelemetryProperties), null));

    }


    private Either<PrisonLocationUpdateError, PrisonLocationUpdateSuccess> updateCustodyPrisonLocation(final String nomsNumber,
                                                                                                       final Function<Offender, Either<PrisonLocationUpdateError, List<Event>>> eventSupplier,
                                                                                                       final String nomsPrisonInstitutionCode) {
        return findByNomsNumber(nomsNumber)
                .flatMap(offender -> eventSupplier.apply(offender)
                        .flatMap(events -> atLeastOneInCustodyOrAboutToStartACustodySentence(events)
                                .flatMap(eventsToUpdate -> findByNomisCdeCode(nomsPrisonInstitutionCode)
                                        .flatMap(institution -> updateInstitutionsWhenDifferent(offender, eventsToUpdate, institution)))
                        ));
    }


    @Transactional
    public Custody updateCustodyBookingNumber(String nomsNumber, UpdateCustodyBookingNumber updateCustodyBookingNumber) {
        final var telemetryProperties = Map.of("offenderNo", nomsNumber,
                "bookingNumber", updateCustodyBookingNumber.getBookingNumber(),
                "sentenceStartDate", updateCustodyBookingNumber.getSentenceStartDate().format(DateTimeFormatter.ISO_DATE));

        final var offender = offenderRepository.findByNomsNumber(nomsNumber).orElseThrow(() -> {
            telemetryClient.trackEvent("P2PImprisonmentStatusOffenderNotFound", telemetryProperties, null);
            return new NotFoundException(String.format("offender with nomsNumber %s not found", nomsNumber));
        });
        final var event = convictionService.getSingleActiveConvictionIdByOffenderIdAndCloseToSentenceDate(offender.getOffenderId(), updateCustodyBookingNumber.getSentenceStartDate())
                .onError(error -> {
                    telemetryClient.trackEvent("P2PImprisonmentStatusCustodyEventsHasDuplicates", telemetryProperties, null);
                    return new NotFoundException(String.format("no single conviction with sentence date around %s found, instead %d duplicates found", updateCustodyBookingNumber.getSentenceStartDate().format(DateTimeFormatter.ISO_LOCAL_DATE), error.getConvictionCount()));
                }).orElseThrow(() -> {
                    telemetryClient.trackEvent("P2PImprisonmentStatusCustodyEventNotFound", telemetryProperties, null);
                    return new NotFoundException(String.format("conviction with sentence date close to  %s not found", updateCustodyBookingNumber.getSentenceStartDate().format(DateTimeFormatter.ISO_LOCAL_DATE)));
                });

        final var maybeExistingBookingNumber = Optional.ofNullable(event.getDisposal().getCustody().getPrisonerNumber()).filter(StringUtils::isNotBlank);
        final Predicate<String> sameAsNewBookingNumber = existingBookingNumber -> existingBookingNumber.equals(updateCustodyBookingNumber.getBookingNumber());

        if (maybeExistingBookingNumber.filter(sameAsNewBookingNumber).isPresent()) {
            telemetryClient.trackEvent("P2PImprisonmentStatusBookingNumberAlreadySet", telemetryProperties, null);
            return ConvictionTransformer.custodyOf(event.getDisposal().getCustody());
        } else {
            final var eventName = maybeExistingBookingNumber.isPresent() ? "P2PImprisonmentStatusBookingNumberUpdated" : "P2PImprisonmentStatusBookingNumberInserted";
            telemetryClient.trackEvent(eventName, telemetryProperties, null);
            return ConvictionTransformer
                    .custodyOf(updateBookingNumberFor(offender, event, updateCustodyBookingNumber.getBookingNumber()).getDisposal().getCustody());
        }
    }


    @Transactional(readOnly = true)
    public Custody getCustodyByBookNumber(String nomsNumber, String bookingNumber) {
        final var offender = offenderRepository.findByNomsNumber(nomsNumber)
                .orElseThrow(() -> new NotFoundException(String.format("offender with nomsNumber %s not found", nomsNumber)));
        try {
            final var event = convictionService.getSingleActiveConvictionByOffenderIdAndPrisonBookingNumber(offender.getOffenderId(), bookingNumber)
                    .orElseThrow(() -> new NotFoundException(String.format("conviction with bookNumber %s not found", bookingNumber)));
            return ConvictionTransformer.custodyOf(event.getDisposal().getCustody());
        } catch (DuplicateActiveCustodialConvictionsException e) {
            throw new NotFoundException(String.format("no single conviction with bookingNumber %s found, instead %d duplicates found", bookingNumber, e.getConvictionCount()));
        }
    }

    @Transactional(readOnly = true)
    public Custody getCustodyByConvictionId(String crn, Long convictionId) {
        final var offender = offenderRepository.findByCrn(crn)
                .orElseThrow(() -> new NotFoundException(String.format("offender with crn %s not found", crn)));
        return Optional.ofNullable(convictionService.convictionFor(offender.getOffenderId(), convictionId)
                .orElseThrow(() -> new NotFoundException(String.format("conviction with convictionId %d not found", convictionId))).getCustody())
                .orElseThrow(() -> new BadRequestException(String.format("The conviction with convictionId %d is not a custodial sentence", convictionId)));
    }

    private Event updateBookingNumberFor(Offender offender, Event event, String bookingNumber) {
        if (updateBookingNumberFeatureSwitch) {
            event.getDisposal().getCustody().setPrisonerNumber(bookingNumber);
            offenderPrisonerService.refreshOffenderPrisonersFor(offender);
            spgNotificationService.notifyUpdateOfCustody(offender, event);
            contactService.addContactForBookingNumberUpdate(offender, event);
        } else {
            log.warn("Update booking number will be ignored, this feature is switched off ");
        }
        return event;
    }


    private boolean currentlyAtDifferentInstitution(Event event, RInstitution institution) {
        return Optional.ofNullable(event.getDisposal().getCustody().getInstitution())
                .map(currentInstitution -> !currentInstitution.equals(institution))
                .orElse(true);
    }

    private boolean isInCustodyOrAboutToStartACustodySentence(uk.gov.justice.digital.delius.jpa.standard.entity.Custody custody) {
        return custody.isAboutToEnterCustody() || custody.isInCustody();
    }

    private void updateInstitutionOnEvent(Offender offender, Event event, RInstitution institution) {
        if (updateCustodyFeatureSwitch) {
            final var custody = event.getDisposal().getCustody();
            custody.setInstitution(institution);
            custody.setLocationChangeDate(LocalDate.now());
            savePrisonLocationChangeCustodyHistoryEvent(offender, custody, institution);
            if (custody.isAboutToEnterCustody()) {
                custody.setStatusChangeDate(LocalDate.now());
                custody.setCustodialStatus(referenceDataService.getInCustodyCustodyStatus());
                saveCustodyStatusChangeCustodyHistoryEvent(offender, custody);
            }
            spgNotificationService.notifyUpdateOfCustodyLocationChange(offender, event);
            spgNotificationService.notifyUpdateOfCustody(offender, event);
            updatePrisonOffenderManager(offender, institution);
            contactService.addContactForPrisonLocationChange(offender, event);
        } else {
            log.warn("Update institution will be ignored, this feature is switched off ");
        }
    }

    private void updatePrisonOffenderManager(Offender offender, RInstitution institution) {
        if (!offenderManagerService.isPrisonOffenderManagerAtInstitution(offender, institution)) {
            offenderManagerService.autoAllocatePrisonOffenderManagerAtInstitution(offender, institution);
        }
    }

    private void savePrisonLocationChangeCustodyHistoryEvent(Offender offender, uk.gov.justice.digital.delius.jpa.standard.entity.Custody custody, RInstitution institution) {
        final var history = CustodyHistory
                .builder()
                .custody(custody)
                .offender(offender)
                .detail(institution.getDescription())
                .when(LocalDate.now())
                .custodyEventType(referenceDataService.getPrisonLocationChangeCustodyEvent())
                .build();
        custodyHistoryRepository.save(history);
    }

    private void saveCustodyStatusChangeCustodyHistoryEvent(Offender offender, uk.gov.justice.digital.delius.jpa.standard.entity.Custody custody) {
        final var history = CustodyHistory
                .builder()
                .custody(custody)
                .offender(offender)
                .detail("DSS auto update in custody")
                .when(LocalDate.now())
                .custodyEventType(referenceDataService.getCustodyStatusChangeCustodyEvent())
                .build();
        custodyHistoryRepository.save(history);
    }

    private Either<PrisonLocationUpdateError, Offender> findByNomsNumber(String nomsNumber) {
        final Supplier<Either<PrisonLocationUpdateError, Offender>> notFoundError = () -> Either
                .left(PrisonLocationUpdateError.offenderNotFound(nomsNumber));

        final Function<DuplicateOffenderException, Either<PrisonLocationUpdateError, Offender>> multipleOffenderError = duplicateError -> Either
                .left(PrisonLocationUpdateError.multipleOffenders(duplicateError));

        final Function<Optional<Offender>, Either<PrisonLocationUpdateError, Offender>> offenderOrNotFoundError = maybeOffender -> maybeOffender
                .map((Function<Offender, Either<PrisonLocationUpdateError, Offender>>) Either::right)
                .orElseGet(notFoundError);

        return offenderRepository
                .findMostLikelyByNomsNumber(nomsNumber)
                .fold(multipleOffenderError, offenderOrNotFoundError);
    }

    private Either<PrisonLocationUpdateError, List<Event>> getAllActiveCustodialEvents(Offender offender) {
        final var events = convictionService.getAllActiveCustodialEvents(offender.getOffenderId());
        if (events.isEmpty()) {
            return Either.left(new PrisonLocationUpdateError(ConvictionNotFound, String
                .format("No active custodial events found for offender %s", offender.getCrn())));
        } else {
            // legacy behaviour throw NotFoundException
            if (events.size() > 1 && !featureSwitches.getNoms().getUpdate().getMultipleEvents().isUpdatePrisonLocation()) {
                return Either.left(new PrisonLocationUpdateError(MultipleCustodialSentences, String
                    .format("Multiple active custodial events found for offender %s. %d found", offender.getCrn(), events.size())));
            }
            return Either.right(events);
        }
    }

    private Either<PrisonLocationUpdateError, List<Event>> atLeastOneInCustodyOrAboutToStartACustodySentence(List<Event> events) {
        final var eventsInCustody = events
            .stream()
            .filter(event -> isInCustodyOrAboutToStartACustodySentence(event.getDisposal().getCustody()))
            .collect(toList());
        return eventsInCustody.isEmpty()
            ? Either.left(new PrisonLocationUpdateError(PrisonLocationUpdateError.Reason.CustodialSentenceNotFoundInCorrectState, String
            .format("conviction with custodial status of In Custody or Sentenced Custody not found. Status was %s", allCustodialStatuses(events))))
            : Either.right(eventsInCustody);
    }

    private String allCustodialStatuses(List<Event> events) {
        return events
            .stream()
            .map(event -> event.getDisposal().getCustody().getCustodialStatus().getCodeDescription())
            .collect(Collectors.joining());
    }

    private Either<PrisonLocationUpdateError, RInstitution> findByNomisCdeCode(String nomisCdeCode) {
        return institutionRepository.findByNomisCdeCode(nomisCdeCode)
                .map((Function<RInstitution, Either<PrisonLocationUpdateError, RInstitution>>) Either::right)
                .orElseGet(() -> Either.left(new PrisonLocationUpdateError(PrisonLocationUpdateError.Reason.TransferPrisonNotFound, String.format("prison institution with nomis code  %s not found", nomisCdeCode))));
    }

    private Either<PrisonLocationUpdateError, PrisonLocationUpdateSuccess> updateInstitutionsWhenDifferent(Offender offender, List<Event> events, RInstitution institution) {
        final var allAtDifferentInstitution = events
            .stream()
            .filter(event -> currentlyAtDifferentInstitution(event, institution))
            .collect(toList());

        if (allAtDifferentInstitution.isEmpty()) {
            return Either.right(PrisonLocationUpdateSuccess.noUpdateRequired(allOf(events)));
        }

        allAtDifferentInstitution.forEach(event -> updateInstitutionOnEvent(offender, event, institution));

        return Either.right(PrisonLocationUpdateSuccess.updated(allOf(allAtDifferentInstitution)));
    }

    private List<Custody> allOf(List<Event> events) {
        return events.stream().map(event -> event.getDisposal().getCustody()).map(ConvictionTransformer::custodyOf).collect(toList());
    }

    @Data
    static class PrisonLocationUpdateError {
        enum Reason {
            TransferPrisonNotFound,
            CustodialSentenceNotFoundInCorrectState,
            ConvictionNotFound,
            MultipleCustodialSentences,
            OffenderNotFound,
            MultipleOffendersFound
        }
        final private Reason reason;
        final private String message;

        public static PrisonLocationUpdateError offenderNotFound(String nomsNumber) {
            return new PrisonLocationUpdateError(OffenderNotFound, String.format("offender with nomsNumber %s not found", nomsNumber));
        }

        public static PrisonLocationUpdateError multipleOffenders(DuplicateOffenderException duplicateError) {
            return new PrisonLocationUpdateError(MultipleOffendersFound, duplicateError.getMessage());
        }
    }
    @Data
    static class PrisonLocationUpdateSuccess {
        static PrisonLocationUpdateSuccess updated(List<Custody> custodyRecordsUpdated) {
            return new PrisonLocationUpdateSuccess(Outcome.Updated, custodyRecordsUpdated);
        }
        static PrisonLocationUpdateSuccess noUpdateRequired(List<Custody> custodyRecordsUpdated) {
            return new PrisonLocationUpdateSuccess(Outcome.NoUpdateRequired, custodyRecordsUpdated);
        }

        enum Outcome {
            Updated,
            NoUpdateRequired
        }
        final private Outcome outcome;
        final private List<Custody> custodyRecordsUpdated;
    }

    static <K, V> Map<K, V> add(Map<K, V> map, @SuppressWarnings("SameParameterValue") K key, V value) {
        final var all = new HashMap<>(map);
        all.put(key, value);
        return Map.copyOf(all);
    }
    static <K, V> Map<K, V> add(Map<K, V> map1, Map<K, V> map2) {
        final var all = new HashMap<>(map1);
        all.putAll(map2);
        return Map.copyOf(all);
    }
}