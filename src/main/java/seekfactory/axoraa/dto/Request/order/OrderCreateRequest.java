package seekfactory.axoraa.dto.Request.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Buyer places an order request for a product (identified by its public slug). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateRequest {

    @NotBlank(message = "Product is required")
    @Size(max = 255)
    private String productSlug;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 1_000_000, message = "Quantity is too large")
    private Integer quantity;

    @Size(max = 2000, message = "Note must be at most 2000 characters")
    private String note;
}
