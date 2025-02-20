package uk.gov.justice.digital.delius.jpa.standard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static java.util.function.Predicate.not;

@Data
@EqualsAndHashCode(callSuper = true)
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity(name = "CUSTODY")
@ToString(exclude = "disposal")
public class Custody extends AuditableEntity {
    enum CustodialStatus {
        SENTENCED_IN_CUSTODY("A"),
        IN_CUSTODY("D"),
        RELEASED_ON_LICENCE("B"),
        RECALLED("C"),
        TERMINATED("T"),
        POST_SENTENCE_SUPERVISION("P"),
        MIGRATED_DATA("-1"),
        IN_CUSTODY_RoTL("R"),
        IN_CUSTODY_IRC("I"),
        AUTO_TERMINATED("AT");

        private final String code;

        CustodialStatus(String code) {
            this.code = code;
        }
        public String getCode() {
            return code;
        }
    }

    @Id
    @Column(name = "CUSTODY_ID")
    private Long custodyId;

    @JoinColumn(name = "DISPOSAL_ID", referencedColumnName = "DISPOSAL_ID")
    @OneToOne
    private Disposal disposal;

    @Column(name = "SOFT_DELETED")
    private Long softDeleted;

    @Column(name = "OFFENDER_ID")
    private Long offenderId;

    @Column(name = "PRISONER_NUMBER")
    private String prisonerNumber;

    @ManyToOne
    @JoinColumns({
            @JoinColumn(name = "INSTITUTION_ID"),
            @JoinColumn(name = "ESTABLISHMENT")})
    private RInstitution institution;

    @OneToMany(cascade = {CascadeType.ALL}, orphanRemoval=true, mappedBy = "custody")
    private List<KeyDate> keyDates;

    @OneToMany
    @JoinColumn(name = "CUSTODY_ID")
    private List<Release> releases;

    @Column(name = "STATUS_CHANGE_DATE")
    private LocalDate statusChangeDate;

    @Column(name = "LOCATION_CHANGE_DATE")
    private LocalDate locationChangeDate;

    @Column(name = "PSS_START_DATE")
    private LocalDate pssStartDate;

    @OneToMany(cascade = {CascadeType.ALL}, orphanRemoval=true, mappedBy = "custody")
    private List<PssRequirement> pssRequirements;

    @JoinColumn(name = "CUSTODIAL_STATUS_ID")
    @ManyToOne
    private StandardReference custodialStatus;

    public Optional<Release> findLatestRelease() {
        return this.getReleases().stream()
                .filter(not(Release::isSoftDeleted))
                .max(Comparator.comparing(Release::getActualReleaseDate));
    }

    public boolean isSoftDeleted() {
        return this.softDeleted != 0L;
    }

    public boolean isInCustody() {
        return Optional.ofNullable(custodialStatus)
                .map(status -> status.getCodeValue().equals(CustodialStatus.IN_CUSTODY.getCode()) )
                .orElse(false);
    }
    public boolean isAboutToEnterCustody() {
        return Optional.ofNullable(custodialStatus)
                .map(status -> status.getCodeValue().equals(CustodialStatus.SENTENCED_IN_CUSTODY.getCode()) )
                .orElse(false);
    }

    public boolean isPostSentenceSupervision() {
        return Optional.ofNullable(custodialStatus)
                .map(status -> status.getCodeValue().equals(CustodialStatus.POST_SENTENCE_SUPERVISION.getCode()) )
                .orElse(false);
    }

}
