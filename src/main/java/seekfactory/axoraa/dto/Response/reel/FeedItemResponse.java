package seekfactory.axoraa.dto.Response.reel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;

import java.util.List;

/**
 * A feed item = reel + its manufacturer info + optional primary product slug.
 * This is what the frontend FeedRepository.list() returns.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedItemResponse {

    private ReelResponse reel;
    private ManufacturerResponse manufacturer;
    private String primaryProductSlug;
    /** Active products tagged on the reel (powers the buyer "View Products" strip and feed filters). */
    private List<ProductResponse> products;
}