package seekfactory.axoraa.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Bulk price break stored in products.price_tiers: orders of at least minQty pay priceInr per unit.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceTier {
    private Integer minQty;
    private BigDecimal priceInr;
}
