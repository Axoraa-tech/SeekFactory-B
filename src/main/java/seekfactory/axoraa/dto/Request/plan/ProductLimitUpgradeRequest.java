package seekfactory.axoraa.dto.Request.plan;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A manufacturer asking an admin for room for more products. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductLimitUpgradeRequest {

    @NotNull(message = "Choose how many products you need")
    @Min(value = 1, message = "Requested limit must be at least 1")
    @Max(value = 1000, message = "Requested limit can be at most 1000")
    private Integer requestedLimit;

    @Size(max = 1000, message = "Message must be at most 1000 characters")
    private String message;
}
