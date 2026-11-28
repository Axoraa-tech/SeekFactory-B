package seekfactory.axoraa.services;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExchangeRateRefresherTest {
    /** Shape of https://open.er-api.com/v6/latest/INR (trimmed). */
    private static final String ANSWER = """
            {"result":"success","base_code":"INR","time_last_update_unix":1764201601,
             "rates":{"INR":1,"USD":0.01118,"EUR":0.009652,"GBP":0.008471,"CNY":0.07952,"JPY":1.745,
                      "AED":0.04106,"CAD":0.01571,"AUD":0.01716,"SGD":0.01452,"BRL":0.0598,"XAU":0.0000027}}""";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test
    void keepsOnlyTheSiteCurrencies() throws Exception {
        Map<String, Double> rates = new ExchangeRateRefresher(jdbc, "http://unused").parse(ANSWER);
        assertThat(rates.keySet()).containsExactlyElementsOf(ExchangeRateRefresher.CURRENCIES);
        assertThat(rates).containsEntry("INR", 1.0).containsEntry("CNY", 0.07952).doesNotContainKey("BRL");
    }

    @Test
    void refusesAnswersOnAnotherBase() {
        assertThatThrownBy(() -> new ExchangeRateRefresher(jdbc, "http://unused")
                .parse("{\"base_code\":\"USD\",\"rates\":{\"INR\":89.4}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesMissingOrWildlyMovedRates() {
        assertThat(ExchangeRateRefresher.implausible(Map.of("INR", 1.0, "USD", 0.0112), Map.of())).contains("CNY");
        assertThat(ExchangeRateRefresher.implausible(Map.of("INR", 1.0, "USD", 0.0112, "CNY", 0.0795),
                Map.of("USD", 0.01149, "CNY", 0.0833))).isNull();
        assertThat(ExchangeRateRefresher.implausible(Map.of("INR", 1.0, "USD", 1.12, "CNY", 0.0795),
                Map.of("USD", 0.01149))).contains("USD");
    }

    @Test
    void storesFreshRatesFromTheService() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v6/latest/INR", exchange -> {
            byte[] body = ANSWER.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v6/latest/INR";
            new ExchangeRateRefresher(jdbc, url).refresh();
        } finally {
            server.stop(0);
        }
        verify(jdbc).update(contains("ON CONFLICT (key) DO UPDATE"),
                (Object) argThat((String v) -> v.contains("\"CNY\":0.07952") && v.contains("\"SGD\":0.01452")
                        && v.contains("\"updatedAt\"") && !v.contains("BRL")));
    }

    @Test
    void aFailedFetchChangesNothing() {
        new ExchangeRateRefresher(jdbc, "http://127.0.0.1:1/unreachable").refresh();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
