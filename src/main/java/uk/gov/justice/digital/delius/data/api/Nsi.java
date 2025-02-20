package uk.gov.justice.digital.delius.data.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@With
@NoArgsConstructor
@AllArgsConstructor
public class Nsi {
    private Long nsiId;
    private KeyValue nsiType;
    private KeyValue nsiSubType;
    private KeyValue nsiOutcome;
    private Requirement requirement;
    private KeyValue nsiStatus;
    private LocalDateTime statusDateTime;
    private LocalDate actualStartDate;
    private LocalDate expectedStartDate;
    private LocalDate referralDate;
    private Long length;
    private String lengthUnit;
    private List<NsiManager> nsiManagers;
    private String notes;
    private ProbationArea intendedProvider;
}
