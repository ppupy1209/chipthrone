package dev.yeonwoo.chipthrone.quote.client;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice;
import dev.yeonwoo.chipthrone.quote.service.QuoteMetrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HyperliquidMarketDataClient implements MarketDataClient {

    private final RestClient restClient;
    private final QuoteMetrics metrics;
    private final String infoUrl;

    public HyperliquidMarketDataClient(
            RestClient restClient,
            QuoteMetrics metrics,
            @Value("${chipthrone.source.hyperliquid-url:https://api.hyperliquid.xyz/info}") String infoUrl
    ) {
        this.restClient = restClient;
        this.metrics = metrics;
        this.infoUrl = infoUrl;
    }

    @Override
    public List<MarketAssetPrice> fetchAssetPrices(String dex) {
        metrics.externalCall("hyperliquid", "batch_quotes");
        JsonNode response = restClient.post()
                .uri(infoUrl)
                .body(Map.of("type", "metaAndAssetCtxs", "dex", dex))
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.isArray() || response.size() < 2) {
            throw new IllegalStateException("Unexpected Hyperliquid info response");
        }

        JsonNode universe = response.get(0).path("universe");
        JsonNode assetCtxs = response.get(1);
        if (!universe.isArray() || !assetCtxs.isArray()) {
            throw new IllegalStateException("Unexpected Hyperliquid universe/assetCtxs response");
        }

        int size = Math.min(universe.size(), assetCtxs.size());
        List<MarketAssetPrice> prices = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String symbol = universe.get(i).path("name").asText(null);
            String markPx = assetCtxs.get(i).path("markPx").asText(null);
            String prevDayPx = assetCtxs.get(i).path("prevDayPx").asText(null);
            if (symbol != null && markPx != null && prevDayPx != null) {
                prices.add(new MarketAssetPrice(symbol, new BigDecimal(markPx), new BigDecimal(prevDayPx)));
            }
        }
        return prices;
    }
}
