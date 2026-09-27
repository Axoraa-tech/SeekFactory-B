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
    /** Certifications the factory declared; empty when it has none. */
    private List<String> certifications;
    /** Share of recent RFQs the factory quoted on; null until it has received any. */
    private Double responseRatePercent;
    private Double avgResponseTimeHours;
    /** Whether the viewer follows this factory; omitted for guests. */
    private Boolean followedByMe;
}