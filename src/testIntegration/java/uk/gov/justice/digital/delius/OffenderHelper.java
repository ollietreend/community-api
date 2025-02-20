package uk.gov.justice.digital.delius;

import org.assertj.core.util.Lists;
import uk.gov.justice.digital.delius.jpa.standard.entity.Disability;
import uk.gov.justice.digital.delius.jpa.standard.entity.Offender;
import uk.gov.justice.digital.delius.jpa.standard.entity.OffenderAddress;
import uk.gov.justice.digital.delius.jpa.standard.entity.OffenderAlias;
import uk.gov.justice.digital.delius.jpa.standard.entity.OffenderManager;
import uk.gov.justice.digital.delius.jpa.standard.entity.Officer;
import uk.gov.justice.digital.delius.jpa.standard.entity.PartitionArea;
import uk.gov.justice.digital.delius.jpa.standard.entity.ProbationArea;
import uk.gov.justice.digital.delius.jpa.standard.entity.StandardReference;

import java.time.LocalDate;

public interface OffenderHelper {
     static Offender anOffender() {
        return Offender.builder()
                .allowSMS("Y")
                .crn("crn123")
                .croNumber("cro123")
                .currentDisposal(1L)
                .currentHighestRiskColour("AMBER")
                .currentRemandStatus("ON_REMAND")
                .dateOfBirthDate(LocalDate.of(1970, 1, 1))
                .emailAddress("bill@sykes.com")
                .establishment('A')
                .ethnicity(StandardReference.builder().codeDescription("IC1").build())
                .exclusionMessage("exclusion message")
                .firstName("Bill")
                .gender(StandardReference.builder().codeDescription("M").build())
                .immigrationNumber("IM123")
                .immigrationStatus(StandardReference.builder().codeDescription("N/A").build())
                .institutionId(4L)
                .interpreterRequired("N")
                .language(StandardReference.builder().codeDescription("ENGLISH").build())
                .languageConcerns("None")
                .mobileNumber("0718118055")
                .nationality(StandardReference.builder().codeDescription("BRITISH").build())
                .mostRecentPrisonerNumber("PN123")
                .niNumber("NI1234567")
                .nomsNumber("NOMS1234")
                .offenderId(5L)
                .pendingTransfer(6L)
                .pncNumber("PNC1234")
                .previousSurname("Jones")
                .religion(StandardReference.builder().codeDescription("COFE").build())
                .restrictionMessage("Restriction message")
                .secondName("Arthur")
                .surname("Sykes")
                .telephoneNumber("018118055")
                .title(StandardReference.builder().codeDescription("Mr").build())
                .secondNationality(StandardReference.builder().codeDescription("EIRE").build())
                .sexualOrientation(StandardReference.builder().codeDescription("STR").build())
                .previousConvictionDate(LocalDate.of(2016, 1, 1))
                .prevConvictionDocumentName("CONV1234")
                .offenderAliases(Lists.newArrayList(OffenderAlias.builder().build()))
                .offenderAddresses(Lists.newArrayList(OffenderAddress.builder().build()))
                .partitionArea(PartitionArea.builder().area("Fulchester").build())
                .softDeleted(0L)
                .currentHighestRiskColour("FUSCHIA")
                .currentDisposal(0L)
                .currentRestriction(0L)
                .currentExclusion(0L)
                .offenderManagers(Lists.newArrayList(OffenderManager.builder()
                        .activeFlag(1L)
                        .allocationDate(LocalDate.now())
                        .officer(Officer.builder().surname("Jones").build())
                        .probationArea(ProbationArea.builder().code("A").description("B").privateSector(1L).build())
                        .build()))
                .disabilities(Lists.newArrayList(Disability
                        .builder()
                        .softDeleted(0L)
                        .disabilityId(1L)
                        .startDate(LocalDate.now())
                        .disabilityType(StandardReference.builder().codeValue("SI").codeDescription("Speech Impairment").build())
                        .build()))
                .build();
    }
}
