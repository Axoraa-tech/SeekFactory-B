package seekfactory.axoraa.dto.Response.manufacturer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.dto.Response.reel.ReelResponse;

import java.util.List;

/**
 * Detailed manufacturer profile — includes the manufacturer's products and reels.
 * Used by the /manufacturers/{slug} detail page on both web and app.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManufacturerDetailResponse {

    private ManufacturerResponse manufacturer;
    private List<ProductResponse> products;
    private List<ReelResponse> reels;
}