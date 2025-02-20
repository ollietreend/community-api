package uk.gov.justice.digital.delius.controller.secure;


import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.digital.delius.controller.advice.SecureControllerAdvice;
import uk.gov.justice.digital.delius.data.api.AccessLimitation;
import uk.gov.justice.digital.delius.data.api.OffenderDetail;
import uk.gov.justice.digital.delius.helpers.CurrentUserSupplier;
import uk.gov.justice.digital.delius.service.AssessmentService;
import uk.gov.justice.digital.delius.service.ContactService;
import uk.gov.justice.digital.delius.service.ConvictionService;
import uk.gov.justice.digital.delius.service.CustodyService;
import uk.gov.justice.digital.delius.service.NsiService;
import uk.gov.justice.digital.delius.service.OffenderManagerService;
import uk.gov.justice.digital.delius.service.OffenderService;
import uk.gov.justice.digital.delius.service.SentenceService;
import uk.gov.justice.digital.delius.service.TierService;
import uk.gov.justice.digital.delius.service.UserAccessService;
import uk.gov.justice.digital.delius.service.UserService;

import java.util.Optional;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

public class OffenderResource_CheckUserAccess {

    private static final String SOME_CRN_NUMBER = "X320741";

    private final OffenderService offenderService = mock(OffenderService.class);
    private final ContactService contactService = mock(ContactService.class);
    private final ConvictionService convictionService = mock(ConvictionService.class);
    private final OffenderManagerService offenderManagerService = mock(OffenderManagerService.class);
    private final NsiService nsiService = mock(NsiService.class);
    private final SentenceService sentenceService = mock(SentenceService.class);
    private final UserService userService = mock(UserService.class);
    private final CustodyService custodyService = mock(CustodyService.class);
    private final CurrentUserSupplier currentUserSupplier = mock(CurrentUserSupplier.class);
    private final UserAccessService userAccessService = mock(UserAccessService.class);
    private final AssessmentService assessmentService = mock(AssessmentService.class);
    private final TierService tierService = mock(TierService.class);

    @BeforeEach
    public void setup() {
        RestAssuredMockMvc.standaloneSetup(
                new OffendersResource(offenderService, contactService, convictionService, nsiService, offenderManagerService, sentenceService, userService, currentUserSupplier, custodyService, userAccessService, assessmentService, tierService),
                new SecureControllerAdvice()
        );
    }

    @Test
    void willCheckUserAccessIsNotRestrictedOrExcluded(){
        final var offender = theOffender();
        given(offenderService.getOffenderByCrn(SOME_CRN_NUMBER)).willReturn(offender);
        given(currentUserSupplier.username()).willReturn(Optional.of("BOB"));
        given(userService.accessLimitationOf("BOB", offender.orElseThrow())).willReturn(accessLimitationNone());

        final var accessLimitation =  given()
                .contentType(APPLICATION_JSON_VALUE)
                .when()
                .get(String.format("/secure/offenders/crn/%s/userAccess", SOME_CRN_NUMBER))
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(AccessLimitation.class);

        assertThat(accessLimitation.isUserExcluded()).isFalse();
        assertThat(accessLimitation.getExclusionMessage()).isNullOrEmpty();
        assertThat(accessLimitation.isUserRestricted()).isFalse();
        assertThat(accessLimitation.getRestrictionMessage()).isNullOrEmpty();

    }

    @Test
    void willCheckUserAccessIsRestrictedAndExcluded(){
        final var offender = theOffender();
        given(offenderService.getOffenderByCrn(SOME_CRN_NUMBER)).willReturn(offender);
        given(currentUserSupplier.username()).willReturn(Optional.of("BOB"));
        given(userService.accessLimitationOf("BOB", offender.orElseThrow())).willReturn(accessLimitationRestrictedAndExcluded());

        final var accessLimitation =  given()
                .contentType(APPLICATION_JSON_VALUE)
                .when()
                .get(String.format("/secure/offenders/crn/%s/userAccess", SOME_CRN_NUMBER))
                .then()
                .statusCode(403)
                .extract()
                .body()
                .as(AccessLimitation.class);

        assertThat(accessLimitation.isUserExcluded()).isTrue();
        assertThat(accessLimitation.getExclusionMessage()).isEqualTo("Your excluded");
        assertThat(accessLimitation.isUserRestricted()).isTrue();
        assertThat(accessLimitation.getRestrictionMessage()).isEqualTo("Your Restricted");


    }
    @Test
    void willCheckUserAccessOffenderNotFound(){
        given(offenderService.getOffenderByCrn("34562X")).willReturn(Optional.empty());

        given()
                .contentType(APPLICATION_JSON_VALUE)
                .when()
                .get(String.format("/secure/offenders/crn/%s/userAccess", "34562X"))
                .then()
                .statusCode(404);
    }

    private Optional<OffenderDetail> theOffender() {
        return Optional.of(OffenderDetail.builder()
                .firstName("Bob")
                .surname("Jones")
                .currentExclusion(Boolean.TRUE)
                .exclusionMessage("the exclusion message")
                .currentRestriction(Boolean.TRUE)
                .restrictionMessage("the restriction message")
                .build());
    }

    private AccessLimitation accessLimitationRestrictedAndExcluded() {
        return AccessLimitation.builder()
                .userExcluded(true)
                .exclusionMessage("Your excluded")
                .userRestricted(true)
                .restrictionMessage("Your Restricted")
                .build();
    }

    private AccessLimitation accessLimitationNone() {
        return AccessLimitation.builder()
                .userExcluded(false)
                .userRestricted(false)
                .build();
    }
}
