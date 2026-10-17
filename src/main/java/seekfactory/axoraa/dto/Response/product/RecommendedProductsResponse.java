package seekfactory.axoraa.dto.Response.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Products for the feed's side panel: matched to the viewer's interests, topped up with trending.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedProductsResponse {

    /** true when at least some products come from the viewer's own activity or searches. */
    private boolean personalized;
    private List<ProductResponse> products;
    /** The factories behind {@link #products}, once each (enough for a product card). */
    private List<Factory> manufacturers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Factory {
        private String id;
        private String slug;
        private String name;
        private String logo;
        private String country;
        private Boolean verified;
    }
}
