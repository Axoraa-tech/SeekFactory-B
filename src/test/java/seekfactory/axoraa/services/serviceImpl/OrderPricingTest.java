package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import seekfactory.axoraa.entity.PriceTier;
import seekfactory.axoraa.entity.Product;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPricingTest {

    private static Product product(String base, PriceTier... tiers) {
        return Product.builder()
                .priceInr(new BigDecimal(base))
                .priceTiers(List.of(tiers))
                .build();
    }

    @Test
    void usesBasePriceWithoutTiers() {
        assertThat(OrderServiceImpl.unitPriceFor(product("1500"), 3)).isEqualByComparingTo("1500.00");
    }

    @Test
    void picksHighestTierTheQuantityReaches() {
        // Tiers are deliberately unsorted: storage order must not matter
        Product p = product("1500",
                new PriceTier(50, new BigDecimal("1200")),
                new PriceTier(10, new BigDecimal("1400")));

        assertThat(OrderServiceImpl.unitPriceFor(p, 9)).isEqualByComparingTo("1500");
        assertThat(OrderServiceImpl.unitPriceFor(p, 10)).isEqualByComparingTo("1400");
        assertThat(OrderServiceImpl.unitPriceFor(p, 49)).isEqualByComparingTo("1400");
        assertThat(OrderServiceImpl.unitPriceFor(p, 50)).isEqualByComparingTo("1200");
        assertThat(OrderServiceImpl.unitPriceFor(p, 5000)).isEqualByComparingTo("1200");
    }

    @Test
    void ignoresIncompleteTiers() {
        Product p = product("1000", new PriceTier(null, new BigDecimal("1")), new PriceTier(5, null));
        assertThat(OrderServiceImpl.unitPriceFor(p, 100)).isEqualByComparingTo("1000");
    }
}
