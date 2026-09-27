package seekfactory.axoraa.dto.Response.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Display conversion rates: 1 unit of base = rate units of each currency.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRatesResponse {
    private String base;
    private Map<String, Double> rates;
}
