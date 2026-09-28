package seekfactory.axoraa.dto.Response.pricing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuyerPlanResponse {
    /** Lower case: free, pro, enterprise */
    private String code;
    private String name;
    private BigDecimal priceInr;
    private BigDecimal priceCny;
    private List<String> features;
}
