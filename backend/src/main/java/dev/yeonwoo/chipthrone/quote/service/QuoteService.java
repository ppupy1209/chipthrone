package dev.yeonwoo.chipthrone.quote.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.yeonwoo.chipthrone.quote.client.ExchangeRateClient;
import dev.yeonwoo.chipthrone.quote.client.KisMarketDataClient;
import dev.yeonwoo.chipthrone.quote.client.MarketDataClient;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.KisStockQuote;
import dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice;
import dev.yeonwoo.chipthrone.quote.model.QuoteSnapshot;
import dev.yeonwoo.chipthrone.quote.web.QuoteBroadcaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final MarketDataClient marketDataClient;
    private final KisMarketDataClient kisMarketDataClient;
    private final ExchangeRateClient exchangeRateClient;
    private final QuoteProperties properties;
    private final QuoteSnapshotFactory snapshotFactory;
    private final QuoteBroadcaster broadcaster;
    private final AtomicReference<QuoteSnapshot> latestSnapshot = new AtomicReference<>();
    private final AtomicReference<BigDecimal> latestFxRate;

    public QuoteService(
            MarketDataClient marketDataClient,
            KisMarketDataClient kisMarketDataClient,
            ExchangeRateClient exchangeRateClient,
            QuoteProperties properties,
            QuoteSnapshotFactory snapshotFactory,
            QuoteBroadcaster broadcaster
    ) {
        this.marketDataClient = marketDataClient;
        this.kisMarketDataClient = kisMarketDataClient;
        this.exchangeRateClient = exchangeRateClient;
        this.properties = properties;
        this.snapshotFactory = snapshotFactory;
        this.broadcaster = broadcaster;
        this.latestFxRate = new AtomicReference<>(BigDecimal.valueOf(properties.initialFxRate()));
    }

    public Optional<QuoteSnapshot> currentSnapshot() {
        return Optional.ofNullable(latestSnapshot.get());
    }

    public synchronized Optional<QuoteSnapshot> refresh() {
        List<MarketAssetPrice> prices;
        try {
            prices = marketDataClient.fetchAssetPrices(properties.dex());
        } catch (RuntimeException ex) {
            log.warn("Failed to fetch market prices. Keeping last quote snapshot.", ex);
            return currentSnapshot();
        }

        BigDecimal fxRate = fetchFxRateOrFallback();
        Map<String, KisStockQuote> kisQuoteByCode = fetchKisQuotesOrEmpty();
        try {
            QuoteSnapshot snapshot = snapshotFactory.create(prices, fxRate, kisQuoteByCode);
            latestSnapshot.set(snapshot);
            broadcaster.publish(snapshot);
            return Optional.of(snapshot);
        } catch (RuntimeException ex) {
            log.warn("Failed to build quote snapshot. Keeping last quote snapshot.", ex);
            return currentSnapshot();
        }
    }

    private BigDecimal fetchFxRateOrFallback() {
        try {
            BigDecimal fxRate = exchangeRateClient.fetchUsdKrw();
            latestFxRate.set(fxRate);
            return fxRate;
        } catch (RuntimeException ex) {
            log.warn("Failed to fetch USD/KRW rate. Using last rate: {}", latestFxRate.get(), ex);
            return latestFxRate.get();
        }
    }

    private Map<String, KisStockQuote> fetchKisQuotesOrEmpty() {
        if (!kisMarketDataClient.enabled()) {
            return Map.of();
        }
        return properties.assets().stream()
                .map(this::fetchKisQuoteOrEmpty)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(KisStockQuote::code, Function.identity(), (left, right) -> left));
    }

    private Optional<KisStockQuote> fetchKisQuoteOrEmpty(QuoteProperties.Asset asset) {
        try {
            return kisMarketDataClient.fetchStockQuote(asset.code());
        } catch (RuntimeException ex) {
            log.warn("Failed to fetch KIS quote for code {}. Falling back for this stock.", asset.code(), ex);
            return Optional.empty();
        }
    }
}
