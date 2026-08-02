package dev.yeonwoo.chipthrone.quote.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import dev.yeonwoo.chipthrone.quote.service.QuoteMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HyperliquidMarketDataClientTest {

    private static final String INFO_URL = "https://hl.example.test/info";
    // 2026-06-19 15:30 KST
    private static final Instant KRX_CLOSE = Instant.parse("2026-06-19T06:30:00Z");

    @Test
    void picksLatestCandleClosedAtRequestedInstant() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HyperliquidMarketDataClient client = new HyperliquidMarketDataClient(
                builder.build(), new QuoteMetrics(registry), INFO_URL);

        server.expect(requestTo(INFO_URL))
                .andExpect(jsonPath("$.type").value("candleSnapshot"))
                .andExpect(jsonPath("$.req.coin").value("xyz:SMSN"))
                .andExpect(jsonPath("$.req.interval").value("15m"))
                .andRespond(withSuccess("""
                        [
                          {"t":1781847900000,"T":1781848799999,"c":"238.5"},
                          {"t":1781848800000,"T":1781849699999,"c":"239.7"},
                          {"t":1781849700000,"T":1781850599999,"c":"241.2"},
                          {"t":1781850600000,"T":1781851499999,"c":"999.9"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        Optional<BigDecimal> close = client.fetchCloseAt("xyz:SMSN", KRX_CLOSE);

        // 06:30:00Z 이후에 마감되는 세 번째 캔들은 제외한다.
        assertThat(close).contains(new BigDecimal("241.2"));
        assertThat(registry.counter(
                "chipthrone.quote.external.api.calls",
                "source", "hyperliquid",
                "operation", "candle_snapshot").count()).isEqualTo(1);
        server.verify(Duration.ofSeconds(1));
    }

    @Test
    void returnsEmptyWhenNoCandlesInWindow() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HyperliquidMarketDataClient client = new HyperliquidMarketDataClient(
                builder.build(), new QuoteMetrics(new SimpleMeterRegistry()), INFO_URL);

        server.expect(requestTo(INFO_URL)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.fetchCloseAt("xyz:SMSN", KRX_CLOSE)).isEmpty();
        server.verify(Duration.ofSeconds(1));
    }
}
