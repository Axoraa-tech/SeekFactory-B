package seekfactory.axoraa.dto.Response.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import seekfactory.axoraa.entity.PriceTier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private String id;
    private String slug;
    private String manufacturerId;
    private String name;
    private String imageUrl;
    private String description;
    private BigDecimal priceInr;
    private String unit;
    private String moq;
    private String categoryId;
    private Map<String, String> specs;
    /** Gallery in display order (first = imageUrl). */
    private List<String> imageUrls;
    private String datasheetUrl;
    private String datasheetName;
    /** false when the seller has paused the listing. */
    private Boolean listed;
    /** Bulk price breaks, ascending by minQty; empty when the factory set none. */
    private List<PriceTier> priceTiers;
    /** Viewer's own state; omitted for guests. */
    private Boolean savedByMe;
}