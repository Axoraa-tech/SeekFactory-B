package seekfactory.axoraa.dto.Response.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {
    private String id;
    private ProductResponse product;
    private ManufacturerResponse manufacturer;
    private int quantity;
    /** Minimum order quantity parsed from the product's MOQ (1 when not numeric). */
    private int minQuantity;
    /** Per-unit INR price after bulk tiers for this quantity. */
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
}
