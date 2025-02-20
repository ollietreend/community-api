package uk.gov.justice.digital.delius.data.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.digital.delius.jpa.standard.entity.Staff;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamCreationResult {
    @ApiModelProperty(value = "List of teams created")
    @JsonInclude(ALWAYS)
    private List<Team> teams;
    @ApiModelProperty(value = "List of unallocated staff created")
    @JsonInclude(ALWAYS)
    private List<StaffHuman> unallocatedStaff;
}
