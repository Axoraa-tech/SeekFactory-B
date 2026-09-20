package seekfactory.axoraa.dto.Response.pricing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanResponse {
    private String id;
    private String name;
    private BigDecimal priceUsd;
    private String featuresJson;
}
