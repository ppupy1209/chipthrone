package dev.yeonwoo.chipthrone.quote;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final MarketDataClient marketDataClient;
    private final ExchangeRateClient exchangeRateClient;
    private final QuoteProperties properties;
    private final QuoteSnapshotFactory snapshotFactory;
    private final QuoteBroadcaster broadcaster;
    private final AtomicReference<QuoteSnapshot> latestSnapshot = new AtomicReference<>();
    private final AtomicReference<BigDecimal> latestFxRate;

    public QuoteService(
            MarketDataClient marketDataClient,
            ExchangeRateClient exchangeRateClient,
            QuoteProperties properties,
            QuoteSnapshotFactory snapshotFactory,
            QuoteBroadcaster broadcaster
    ) {
        this.marketDataClient = marketDataClient;
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
        try {
            QuoteSnapshot snapshot = snapshotFactory.create(prices, fxRate);
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
}
