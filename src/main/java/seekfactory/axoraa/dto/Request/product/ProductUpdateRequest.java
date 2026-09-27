package seekfactory.axoraa.dto.Request.product;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Partial update of a seller's product: null fields are left unchanged. */
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

    private String unit;
    private String moq;
    private String categoryId;
    private Map<String, String> specs;

    /** Uploaded PDF path; an empty string removes the datasheet. */
    private String datasheetUrl;
    private String datasheetName;
}
