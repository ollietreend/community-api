package uk.gov.justice.digital.delius.service;

import io.vavr.control.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.digital.delius.jpa.standard.repository.OffenderPrimaryIdentifiersRepository;
import uk.gov.justice.digital.delius.jpa.standard.repository.OffenderRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.digital.delius.util.EntityHelper.anOffender;

@ExtendWith(MockitoExtension.class)
class OffenderServiceTest {
    @Mock
    private OffenderRepository offenderRepository;
    @Mock
    private OffenderPrimaryIdentifiersRepository offenderPrimaryIdentifiersRepository;
    @Mock
    private ConvictionService convictionService;

    private OffenderService service;

    @BeforeEach
    void setUp() {
        service = new OffenderService(offenderRepository, offenderPrimaryIdentifiersRepository, convictionService);
    }



    @Nested
    @DisplayName("mostLikelyOffenderIdOfNomsNumber")
    class MostLikelyOffenderIdOfNomsNumber {
        @Test
        @DisplayName("will return offender id of the most likely offender")
        void willReturnOffenderIdOfTheMostLikelyOffender() {
            when(offenderRepository.findMostLikelyByNomsNumber(any()))
                .thenReturn(Either.right(Optional.of(anOffender().toBuilder().offenderId(99L)
                    .build())));

            assertThat(service.mostLikelyOffenderIdOfNomsNumber("A1234ZZ").get()).hasValue(99L);
        }

        @Test
        @DisplayName("will return empty if no offender found")
        void willReturnEmptyWhenNoFoundFund() {
            when(offenderRepository.findMostLikelyByNomsNumber(any()))
                .thenReturn(Either.right(Optional.empty()));

            assertThat(service.mostLikelyOffenderIdOfNomsNumber("A1234ZZ").get()).isEmpty();
        }

        @Test
        @DisplayName("will return error if duplicates found")
        void willReturnAnErrorForDuplicates() {
            when(offenderRepository.findMostLikelyByNomsNumber(any()))
                .thenReturn(Either.left(new OffenderRepository.DuplicateOffenderException("two found!")));

            assertThat(service.mostLikelyOffenderIdOfNomsNumber("A1234ZZ").isLeft()).isTrue();
        }

    }
    @Nested
    @DisplayName("getMostLikelyOffenderByNomsNumber")
    class GetMostLikelyOffenderByNomsNumber {
        @Test
        @DisplayName("will return the most likely offender")
        void willReturnOffenderIdOfTheMostLikelyOffender() {
            when(offenderRepository.findMostLikelyByNomsNumber(any()))
                .thenReturn(Either.right(Optional.of(anOffender().toBuilder().offenderId(99L)
                    .build())));

            assertThat(service.getMostLikelyOffenderByNomsNumber("A1234ZZ").get()).isPresent();
        }

        @Test
        @DisplayName("will return empty if no offender found")
        void willReturnEmptyWhenNoFoundFund() {
            when(offenderRepository.findMostLikelyByNomsNumber(any()))
                .thenReturn(Either.right(Optional.empty()));

            assertThat(service.getMostLikelyOffenderByNomsNumber("A1234ZZ").get()).isEmpty();
        }

        @Test
        @DisplayName("will return error if duplicates found")
        void willReturnAnErrorForDuplicates() {
            when(offenderRepository.findMostLikelyByNomsNumber(any()))
                .thenReturn(Either.left(new OffenderRepository.DuplicateOffenderException("two found!")));

            assertThat(service.getMostLikelyOffenderByNomsNumber("A1234ZZ").isLeft()).isTrue();
        }

    }
    @Nested
    @DisplayName("getMostLikelyOffenderSummaryByNomsNumber")
    class GetMostLikelyOffenderSummaryByNomsNumber {
        @Test
        @DisplayName("will return the most likely offender")
        void willReturnOffenderIdOfTheMostLikelyOffender() {
            when(offenderRepository.findMostLikelyByNomsNumber(any()))
                .thenReturn(Either.right(Optional.of(anOffender().toBuilder().offenderId(99L).build())));

            assertThat(service.getMostLikelyOffenderSummaryByNomsNumber("A1234ZZ").get()).isPresent();
        }

        @Test
        @DisplayName("will return empty if no offender found")
        void willReturnEmptyWhenNoFoundFund() {
            when(offenderRepository.findMostLikelyByNomsNumber(any()))
                .thenReturn(Either.right(Optional.empty()));

            assertThat(service.getMostLikelyOffenderSummaryByNomsNumber("A1234ZZ").get()).isEmpty();
        }

        @Test
        @DisplayName("will return error if duplicates found")
        void willReturnAnErrorForDuplicates() {
            when(offenderRepository.findMostLikelyByNomsNumber(any()))
                .thenReturn(Either.left(new OffenderRepository.DuplicateOffenderException("two found!")));

            assertThat(service.getMostLikelyOffenderSummaryByNomsNumber("A1234ZZ").isLeft()).isTrue();
        }

    }
    @Nested
    @DisplayName("getSingleOffenderByNomsNumber")
    class GetSingleOffenderByNomsNumber {
        @Test
        @DisplayName("will return offender id of the most likely offender")
        void willReturnOffenderIdOfTheMostLikelyOffender() {
            when(offenderRepository.findAllByNomsNumber(any()))
                .thenReturn(List.of(anOffender().toBuilder().offenderId(99L).build()));

            assertThat(service.getSingleOffenderByNomsNumber("A1234ZZ").get()).isPresent();
        }

        @Test
        @DisplayName("will return empty if no offender found")
        void willReturnEmptyWhenNoFoundFound() {
            when(offenderRepository.findAllByNomsNumber(any()))
                .thenReturn(List.of());

            assertThat(service.getSingleOffenderByNomsNumber("A1234ZZ").get()).isEmpty();
        }

        @Test
        @DisplayName("will return error if duplicates found")
        void willReturnAnErrorForDuplicates() {
            when(offenderRepository.findAllByNomsNumber(any()))
                .thenReturn(List.of(
                    anOffender().toBuilder().offenderId(99L).build(),
                    anOffender().toBuilder().offenderId(98L).build()
                    ));

            assertThat(service.getSingleOffenderByNomsNumber("A1234ZZ").isLeft()).isTrue();
        }

    }
    @Nested
    @DisplayName("getSingleOffenderSummaryByNomsNumber")
    class GetSingleOffenderSummaryByNomsNumber {
        @Test
        @DisplayName("will return offender id of the most likely offender")
        void willReturnOffenderIdOfTheMostLikelyOffender() {
            when(offenderRepository.findAllByNomsNumber(any()))
                .thenReturn(List.of(anOffender().toBuilder().offenderId(99L).build()));

            assertThat(service.getSingleOffenderSummaryByNomsNumber("A1234ZZ").get()).isPresent();
        }

        @Test
        @DisplayName("will return empty if no offender found")
        void willReturnEmptyWhenNoFoundFound() {
            when(offenderRepository.findAllByNomsNumber(any()))
                .thenReturn(List.of());

            assertThat(service.getSingleOffenderSummaryByNomsNumber("A1234ZZ").get()).isEmpty();
        }

        @Test
        @DisplayName("will return error if duplicates found")
        void willReturnAnErrorForDuplicates() {
            when(offenderRepository.findAllByNomsNumber(any()))
                .thenReturn(List.of(
                    anOffender().toBuilder().offenderId(99L).build(),
                    anOffender().toBuilder().offenderId(98L).build()
                    ));

            assertThat(service.getSingleOffenderSummaryByNomsNumber("A1234ZZ").isLeft()).isTrue();
        }

    }
    @Nested
    @DisplayName("singleOffenderIdOfNomsNumber")
    class SingleOffenderIdOfNomsNumber {
        @Test
        @DisplayName("will return offender id of the most likely offender")
        void willReturnOffenderIdOfTheMostLikelyOffender() {
            when(offenderRepository.findAllByNomsNumber(any()))
                .thenReturn(List.of(anOffender().toBuilder().offenderId(99L).build()));

            assertThat(service.singleOffenderIdOfNomsNumber("A1234ZZ").get()).isPresent();
        }

        @Test
        @DisplayName("will return empty if no offender found")
        void willReturnEmptyWhenNoFoundFound() {
            when(offenderRepository.findAllByNomsNumber(any()))
                .thenReturn(List.of());

            assertThat(service.singleOffenderIdOfNomsNumber("A1234ZZ").get()).isEmpty();
        }

        @Test
        @DisplayName("will return error if duplicates found")
        void willReturnAnErrorForDuplicates() {
            when(offenderRepository.findAllByNomsNumber(any()))
                .thenReturn(List.of(
                    anOffender().toBuilder().offenderId(99L).build(),
                    anOffender().toBuilder().offenderId(98L).build()
                    ));

            assertThat(service.singleOffenderIdOfNomsNumber("A1234ZZ").isLeft()).isTrue();
        }
    }
}
