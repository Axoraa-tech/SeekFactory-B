package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import seekfactory.axoraa.dto.Request.product.ProductUpdateRequest;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.exceptions.BadRequestException;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class ProductPriceUpdateCompatibilityTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private ProductUpdateRequest read(String body) { return json.readValue(body, ProductUpdateRequest.class); }
    private Product range() { return Product.builder().priceInr(new BigDecimal("100")).priceMaxInr(new BigDecimal("250")).build(); }

    @Test void jsonDistinguishesOmittedNullAndValue() {
        assertThat(read("{\"priceInr\":100}").isPriceMaxInrPresent()).isFalse();
        assertThat(read("{\"priceMaxInr\":null}").isPriceMaxInrPresent()).isTrue();
        assertThat(read("{\"priceMaxInr\":300}").getPriceMaxInr()).isEqualByComparingTo("300");
    }
    @Test void internalPresenceCannotBeSpoofed() {
        assertThat(read("{\"priceMaxInr\":null,\"priceMaxInrPresent\":false}").isPriceMaxInrPresent()).isTrue();
        assertThat(read("{\"priceMaxInrPresent\":true}").isPriceMaxInrPresent()).isFalse();
    }
    @Test void builderPreservesPresenceSemantics() {
        assertThat(ProductUpdateRequest.builder().build().isPriceMaxInrPresent()).isFalse();
        assertThat(ProductUpdateRequest.builder().priceMaxInr(null).build().isPriceMaxInrPresent()).isTrue();
    }
    @Test void legacyRenameSavePreservesRange() {
        Product p = range();
        FactoryServiceImpl.applyProductPriceUpdate(p, read("{\"name\":\"Renamed\",\"priceInr\":100}"));
        assertThat(p.getPriceMaxInr()).isEqualByComparingTo("250");
    }
    @Test void metadataOnlyPreservesBothBounds() {
        Product p = range(); FactoryServiceImpl.applyProductPriceUpdate(p, read("{\"name\":\"Renamed\"}"));
        assertThat(p.getPriceInr()).isEqualByComparingTo("100");
        assertThat(p.getPriceMaxInr()).isEqualByComparingTo("250");
    }
    @Test void nullClearsWithOrWithoutMinimum() {
        for (String body : new String[]{"{\"priceMaxInr\":null}","{\"priceInr\":100,\"priceMaxInr\":null}"}) {
            Product p = range(); FactoryServiceImpl.applyProductPriceUpdate(p, read(body));
            assertThat(p.getPriceMaxInr()).isNull();
        }
    }
    @Test void maximumOnlyUpdatesAgainstStoredMinimum() {
        Product p = range(); FactoryServiceImpl.applyProductPriceUpdate(p, read("{\"priceMaxInr\":300}"));
        assertThat(p.getPriceMaxInr()).isEqualByComparingTo("300");
        assertThat(p.getPriceInr()).isEqualByComparingTo("100");
    }
    @Test void invalidPreservedRangeDoesNotMutateBounds() {
        Product p = range();
        assertThatThrownBy(() -> FactoryServiceImpl.applyProductPriceUpdate(p, read("{\"priceInr\":300}")))
            .isInstanceOf(BadRequestException.class);
        assertThat(p.getPriceInr()).isEqualByComparingTo("100");
        assertThat(p.getPriceMaxInr()).isEqualByComparingTo("250");
    }
    @Test void invalidMaximumOnlyIsRejected() {
        assertThatThrownBy(() -> FactoryServiceImpl.applyProductPriceUpdate(range(), read("{\"priceMaxInr\":99}")))
            .isInstanceOf(BadRequestException.class);
    }
    @Test void equalBoundsNormalizeAndExplicitClearAllowsHigherMinimum() {
        Product p = range(); FactoryServiceImpl.applyProductPriceUpdate(p, read("{\"priceInr\":250}"));
        assertThat(p.getPriceMaxInr()).isNull();
        FactoryServiceImpl.applyProductPriceUpdate(p, read("{\"priceInr\":300,\"priceMaxInr\":null}"));
        assertThat(p.getPriceInr()).isEqualByComparingTo("300");
        assertThat(p.getPriceMaxInr()).isNull();
    }
}
