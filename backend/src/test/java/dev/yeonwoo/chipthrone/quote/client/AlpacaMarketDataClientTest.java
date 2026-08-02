package dev.yeonwoo.chipthrone.quote.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice;
import dev.yeonwoo.chipthrone.quote.service.QuoteMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AlpacaMarketDataClientTest {

    @Test
    void fetchesRequestedSymbolsInOneAuthenticatedBatch() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AlpacaMarketDataClient client = new AlpacaMarketDataClient(
                builder.build(),
                new QuoteMetrics(registry),
                "https://data.example.test/v2/stocks/snapshots",
                "test-key",
                "test-secret",
                "iex"
        );

        server.expect(requestTo("https://data.example.test/v2/stocks/snapshots?symbols=SNDK%2CMU&feed=iex"))
                .andExpect(queryParam("symbols", "SNDK%2CMU"))
                .andExpect(queryParam("feed", "iex"))
                .andExpect(header("APCA-API-KEY-ID", "test-key"))
                .andExpect(header("APCA-API-SECRET-KEY", "test-secret"))
                .andRespond(withSuccess("""
                        {
                          "SNDK": {
                            "latestTrade": {"p": 81.25},
                            "dailyBar": {"c": 80.90},
                            "prevDailyBar": {"c": 79.50}
                          },
                          "MU": {
                            "latestTrade": null,
                            "dailyBar": {"c": 154.75},
                            "prevDailyBar": {"c": 151.20}
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Map<String, MarketAssetPrice> prices = client.fetchSnapshots(
                new LinkedHashSet<>(List.of("SNDK", "MU")));

        assertThat(prices).containsOnlyKeys("SNDK", "MU");
        assertThat(prices.get("SNDK").markPx()).isEqualByComparingTo("81.25");
        assertThat(prices.get("MU").markPx()).isEqualByComparingTo("154.75");
        assertThat(registry.get("chipthrone.quote.external.api.calls")
                .tags("source", "alpaca", "operation", "batch_snapshots")
                .counter().count()).isEqualTo(1);
        server.verify(Duration.ofSeconds(1));
    }
}
