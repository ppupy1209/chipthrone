package dev.yeonwoo.chipthrone.quote.client;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice;
import dev.yeonwoo.chipthrone.quote.service.QuoteMetrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AlpacaMarketDataClient {

    private final RestClient restClient;
    private final QuoteMetrics metrics;
    private final String snapshotsUrl;
    private final String apiKey;
    private final String apiSecret;
    private final String feed;

    public AlpacaMarketDataClient(
            RestClient restClient,
            QuoteMetrics metrics,
            @Value("${alpaca.snapshots-url:https://data.alpaca.markets/v2/stocks/snapshots}") String snapshotsUrl,
            @Value("${alpaca.api-key:}") String apiKey,
            @Value("${alpaca.api-secret:}") String apiSecret,
            @Value("${alpaca.feed:iex}") String feed
    ) {
        this.restClient = restClient;
        this.metrics = metrics;
        this.snapshotsUrl = snapshotsUrl;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.feed = feed;
    }

    public boolean enabled() {
        return !apiKey.isBlank() && !apiSecret.isBlank();
    }

    public Map<String, MarketAssetPrice> fetchSnapshots(Set<String> symbols) {
        if (!enabled() || symbols.isEmpty()) {
            return Map.of();
        }
        metrics.externalCall("alpaca", "batch_snapshots");
        JsonNode response = restClient.get()
                .uri(snapshotsUrl + "?symbols={symbols}&feed={feed}", String.join(",", symbols), feed)
                .header("APCA-API-KEY-ID", apiKey)
                .header("APCA-API-SECRET-KEY", apiSecret)
                .retrieve()
                .body(JsonNode.class);
        JsonNode snapshots = response == null ? null : response.path("snapshots");
        if (snapshots == null || snapshots.isMissingNode()) {
            snapshots = response;
        }
        if (snapshots == null || !snapshots.isObject()) {
            throw new IllegalStateException("Unexpected Alpaca snapshots response");
        }

        Map<String, MarketAssetPrice> prices = new LinkedHashMap<>();
        for (String symbol : symbols) {
            JsonNode snapshot = snapshots.path(symbol);
            BigDecimal latest = decimal(snapshot.path("latestTrade").path("p"));
            if (latest == null) {
                latest = decimal(snapshot.path("dailyBar").path("c"));
            }
            BigDecimal previous = decimal(snapshot.path("prevDailyBar").path("c"));
            if (latest != null && previous != null && latest.signum() > 0 && previous.signum() > 0) {
                prices.put(symbol, new MarketAssetPrice(symbol, latest, previous));
            }
        }
        return prices;
    }

    private BigDecimal decimal(JsonNode node) {
        return node == null || !node.isNumber() ? null : node.decimalValue();
    }
}
