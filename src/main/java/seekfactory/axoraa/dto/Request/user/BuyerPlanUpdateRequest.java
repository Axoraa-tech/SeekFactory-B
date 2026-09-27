package seekfactory.axoraa.dto.Request.user;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuyerPlanUpdateRequest {
    /** free, pro or enterprise */
    @NotBlank(message = "Plan is required")
    private String plan;
}
