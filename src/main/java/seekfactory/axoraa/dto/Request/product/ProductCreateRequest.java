package seekfactory.axoraa.dto.Request.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreateRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    @NotBlank(message = "Image URL is required")
    private String imageUrl;

    private String description;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal priceInr;

    private String unit;
    private String moq;

    @NotBlank(message = "Category ID is required")
    private String categoryId;

    private Map<String, String> specs;

    /** Optional full gallery (max 8); when set, the first entry replaces imageUrl as the cover. */
    @Size(max = 8, message = "At most 8 images per product")
    private List<String> imageUrls;

    private String datasheetUrl;
    private String datasheetName;
}