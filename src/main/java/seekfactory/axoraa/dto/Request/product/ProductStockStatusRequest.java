package seekfactory.axoraa.dto.Request.product;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import seekfactory.axoraa.enums.ProductStockStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductStockStatusRequest {

    /** IN_STOCK, LOW_STOCK or OUT_OF_STOCK. */
    @NotNull(message = "stockStatus is required")
    private ProductStockStatus stockStatus;
}
