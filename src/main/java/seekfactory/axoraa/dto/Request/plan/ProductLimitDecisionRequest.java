package seekfactory.axoraa.dto.Request.plan;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Admin decision on a limit request. {@code approvedLimit} defaults to the requested limit. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductLimitDecisionRequest {

    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 1000, message = "Limit can be at most 1000")
    private Integer approvedLimit;

    @Size(max = 1000, message = "Note must be at most 1000 characters")
    private String note;
}
