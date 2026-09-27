package seekfactory.axoraa.dto.Response.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.dto.Response.reel.FeedItemResponse;

import java.util.List;

/**
 * Buyer search across products, factories and seeks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {
    private List<ProductResponse> products;
    private List<ManufacturerResponse> manufacturers;
    private List<FeedItemResponse> reels;
}
