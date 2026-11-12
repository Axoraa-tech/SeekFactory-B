package seekfactory.axoraa.dto.Request.product;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Partial update: omitted fields are unchanged; an explicit null maximum clears the range. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdateRequest {

    @Size(min = 1, max = 255, message = "Product name must be 1-255 characters")
    private String name;

    /** Full gallery in display order; the first image becomes the cover. */
    @Size(min = 1, max = 8, message = "A product needs 1 to 8 images")
    private List<String> imageUrls;

    private String description;

    @Positive(message = "Price must be positive")
    private BigDecimal priceInr;

    /**
     * Omitted: retain the saved maximum, including for legacy clients sending only priceInr.
     * Explicit null: clear the maximum. A value: replace it, validated against the effective minimum.
     */
    @PositiveOrZero(message = "Maximum price cannot be negative")
    private BigDecimal priceMaxInr;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean priceMaxInrPresent;

    @JsonSetter(value = "priceMaxInr", nulls = Nulls.SET)
    public void setPriceMaxInr(BigDecimal value) {
        this.priceMaxInr = value;
        this.priceMaxInrPresent = true;
    }

    @JsonIgnore
    public boolean isPriceMaxInrPresent() {
        return priceMaxInrPresent;
    }

    /** Keep programmatic builders consistent with JSON field presence, including explicit null. */
    public static class ProductUpdateRequestBuilder {
        public ProductUpdateRequestBuilder priceMaxInr(BigDecimal value) {
            this.priceMaxInr = value;
            this.priceMaxInrPresent = true;
            return this;
        }
    }

    private String unit;
    private String moq;
    private String categoryId;
    private Map<String, String> specs;

    /** Uploaded PDF path; an empty string removes the datasheet. */
    private String datasheetUrl;
    private String datasheetName;

    /** Optional stock indicator; defaults to IN_STOCK on create, unchanged on update when omitted. */
    private seekfactory.axoraa.enums.ProductStockStatus stockStatus;
}
