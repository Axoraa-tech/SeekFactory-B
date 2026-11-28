package seekfactory.axoraa.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the display exchange rates (platform_settings "exchange_rates", 1 INR = rate units) at market
 * level: every few hours it fetches the latest rates from a public rates service and stores the
 * currencies the site shows. Only the API server calls the service, never the browser, so visitors in
 * mainland China are not affected by where it is hosted. A failed or implausible answer changes
 * nothing: the last stored rates stay in use.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.fx.refresh-enabled", havingValue = "true", matchIfMissing = true)
public class ExchangeRateRefresher {

    /** The currencies the web app offers (CURRENCIES in regional-context.tsx). */
    static final List<String> CURRENCIES = List.of("INR", "USD", "EUR", "GBP", "CNY", "JPY", "AED", "CAD", "AUD", "SGD");
    /** A new rate further than this factor from the stored one is treated as a bad answer. */
    static final double MAX_JUMP = 1.5;

    private final JdbcTemplate jdbcTemplate;
    private final String url;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public ExchangeRateRefresher(JdbcTemplate jdbcTemplate,
                                 @Value("${app.fx.url:https://open.er-api.com/v6/latest/INR}") String url) {
        this.jdbcTemplate = jdbcTemplate;
        this.url = url;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .proxy(ProxySelector.getDefault())
                .build();
    }

    @Scheduled(initialDelayString = "${app.fx.initial-delay:60000}", fixedDelayString = "${app.fx.refresh-interval:21600000}")
    public void refresh() {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Exchange rates not refreshed: HTTP {}", response.statusCode());
                return;
            }
            Map<String, Double> rates = parse(response.body());
            String problem = implausible(rates, storedRates());
            if (problem != null) {
                log.warn("Exchange rates not refreshed: {}", problem);
                return;
            }
            store(rates);
            log.info("Exchange rates refreshed: 1 INR = {} USD, {} CNY", rates.get("USD"), rates.get("CNY"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Exchange rates not refreshed: {}", e.getMessage());
        }
    }

    /** Rates from an INR-based answer ({"base_code": "INR", "rates": {...}}), only for the site's currencies. */
    Map<String, Double> parse(String body) throws java.io.IOException {
        JsonNode root = json.readTree(body);
        if (!"INR".equalsIgnoreCase(root.path("base_code").asText())) {
            throw new IllegalArgumentException("rates are not based on INR");
        }
        JsonNode all = root.path("rates");
        Map<String, Double> rates = new LinkedHashMap<>();
        rates.put("INR", 1.0);
        for (String code : CURRENCIES) {
            JsonNode rate = all.path(code);
            if (!"INR".equals(code) && rate.isNumber() && rate.asDouble() > 0) rates.put(code, rate.asDouble());
        }
        return rates;
    }

    /** Why the new rates must not be used, or null when they look right. */
    static String implausible(Map<String, Double> rates, Map<String, Double> stored) {
        for (String required : List.of("USD", "CNY")) {
            if (!rates.containsKey(required)) return "missing " + required;
        }
        for (Map.Entry<String, Double> e : rates.entrySet()) {
            Double before = stored.get(e.getKey());
            if (before == null || before <= 0) continue;
            double ratio = e.getValue() / before;
            if (ratio > MAX_JUMP || ratio < 1 / MAX_JUMP) {
                return e.getKey() + " moved from " + before + " to " + e.getValue();
            }
        }
        return null;
    }

    private Map<String, Double> storedRates() {
        Map<String, Double> stored = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT r.key AS code, r.value::double precision AS rate
                FROM platform_settings s, jsonb_each_text(s.value->'rates') r
                WHERE s.key = 'exchange_rates'""", rs -> {
            stored.put(rs.getString("code"), rs.getDouble("rate"));
        });
        return stored;
    }

    private void store(Map<String, Double> rates) throws com.fasterxml.jackson.core.JsonProcessingException {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("base", "INR");
        value.put("rates", rates);
        value.put("updatedAt", Instant.now().toString());
        value.put("source", URI.create(url).getHost());
        jdbcTemplate.update("""
                INSERT INTO platform_settings (key, value, updated_at) VALUES ('exchange_rates', ?::jsonb, now())
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()""",
                json.writeValueAsString(value));
    }
}
