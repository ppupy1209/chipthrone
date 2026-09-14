package dev.yeonwoo.chipthrone.quote.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import dev.yeonwoo.chipthrone.alert.AlertEvent;
import dev.yeonwoo.chipthrone.alert.AlertService;
import dev.yeonwoo.chipthrone.quote.client.ExchangeRateClient;
import dev.yeonwoo.chipthrone.quote.client.MarketDataClient;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote;
import dev.yeonwoo.chipthrone.quote.model.MarketMode;
import dev.yeonwoo.chipthrone.quote.model.QuoteSnapshot;
import dev.yeonwoo.chipthrone.quote.model.SessionClose;
import dev.yeonwoo.chipthrone.quote.model.StockQuote;
import dev.yeonwoo.chipthrone.quote.web.QuoteBroadcaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);
    // 업비트 공개 시세는 서버에서만 조회한다. 5분 주기면 하루 최대 288회로 호출량을 제한하면서
    // 원화 환산값이 장시간 고정되는 문제도 피할 수 있다.
    private static final Duration FX_REFRESH_INTERVAL = Duration.ofMinutes(5);
    private static final Duration FX_RETRY_BACKOFF = Duration.ofMinutes(10);
    private static final Duration MAX_LAST_FX_AGE = Duration.ofMinutes(30);
    private static final BigDecimal MAX_FX_CHANGE_RATIO = new BigDecimal("0.05");

    private final MarketDataClient marketDataClient;
    private final ExchangeRateClient exchangeRateClient;
    private final UsSessionCloseService usSessionCloseService;
    private final String dex;
    private final AssetCatalog catalog;
    private final QuoteSnapshotFactory snapshotFactory;
    private final QuoteBroadcaster broadcaster;
    private final AlertService alertService;
    private final QuoteMetrics metrics;
    private final Clock clock;
    private final AtomicReference<QuoteSnapshot> latestSnapshot = new AtomicReference<>();
    private final AtomicReference<ExchangeRateQuote> latestFxRate = new AtomicReference<>();
    private final AtomicReference<Instant> latestFxFetchedAt = new AtomicReference<>();
    private final AtomicReference<Instant> nextFxRetryAt = new AtomicReference<>();
    private final Map<String, StockQuote> latestStockByCode = new ConcurrentHashMap<>();
    private final Map<String, Instant> latestStockAtByCode = new ConcurrentHashMap<>();

    public QuoteService(
            MarketDataClient marketDataClient,
            ExchangeRateClient exchangeRateClient,
            UsSessionCloseService usSessionCloseService,
            QuoteProperties properties,
            AssetCatalog catalog,
            QuoteSnapshotFactory snapshotFactory,
            QuoteBroadcaster broadcaster,
            AlertService alertService,
            QuoteMetrics metrics,
            Clock clock
    ) {
        this.marketDataClient = marketDataClient;
        this.exchangeRateClient = exchangeRateClient;
        this.usSessionCloseService = usSessionCloseService;
        this.dex = properties.dex();
        this.catalog = catalog;
        this.snapshotFactory = snapshotFactory;
        this.broadcaster = broadcaster;
        this.alertService = alertService;
        this.metrics = metrics;
        this.clock = clock;
    }

    public Optional<QuoteSnapshot> currentSnapshot() {
        return Optional.ofNullable(latestSnapshot.get());
    }

    public synchronized Optional<QuoteSnapshot> currentSnapshot(Set<String> symbols) {
        catalog.requireAssets(symbols);
        return snapshotFromCache(symbols, oldestUpdate(symbols).orElse(clock.instant()));
    }

    public synchronized Optional<QuoteSnapshot> refreshIfStale(Set<String> symbols, Duration staleAfter) {
        return hasFresh(symbols, staleAfter) ? currentSnapshot(symbols) : refresh(symbols);
    }

    public synchronized boolean hasFresh(Set<String> symbols, Duration maxAge) {
        Instant threshold = clock.instant().minus(maxAge);
        return symbols.stream()
                .map(latestStockAtByCode::get)
                .allMatch(updatedAt -> updatedAt != null && !updatedAt.isBefore(threshold));
    }

    public synchronized Optional<QuoteSnapshot> refresh() {
        return refresh(catalog.allCodes());
    }

    public synchronized Optional<QuoteSnapshot> refresh(Set<String> symbols) {
        if (symbols.isEmpty()) {
            return currentSnapshot();
        }
        List<QuoteProperties.Asset> assets = catalog.requireAssets(symbols);
        long startedNanos = System.nanoTime();
        try {
            List<dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice> prices =
                    marketDataClient.fetchAssetPrices(dex);
            ExchangeRateQuote fxRate = fetchFxRateOrFallback();
            Map<String, SessionClose> sessionCloses = usSessionCloseService.sessionCloses(assets);
            QuoteSnapshot refreshed = snapshotFactory.create(prices, fxRate, sessionCloses, assets);
            refreshed.stocks().forEach(stock -> {
                latestStockByCode.put(stock.code(), stock);
                latestStockAtByCode.put(stock.code(), refreshed.at());
            });
            latestSnapshot.set(snapshotFromAllCached(refreshed.at()));
            broadcaster.publish(refreshed);
            alertService.recordSuccess(AlertEvent.QUOTE_SOURCE);
            metrics.poll(MarketMode.ESTIMATE, true, System.nanoTime() - startedNanos);
            return Optional.of(refreshed);
        } catch (RuntimeException ex) {
            alertService.recordFailure(AlertEvent.QUOTE_SOURCE);
            metrics.poll(MarketMode.ESTIMATE, false, System.nanoTime() - startedNanos);
            log.warn("Failed to refresh quote snapshot. Keeping last quote snapshot.", ex);
            return currentSnapshot(symbols);
        }
    }

    private ExchangeRateQuote fetchFxRateOrFallback() {
        if (!exchangeRateClient.enabled()) {
            throw new IllegalStateException("Exchange rate source is disabled");
        }
        Instant now = clock.instant();
        Instant retryAt = nextFxRetryAt.get();
        Instant fetchedAt = latestFxFetchedAt.get();
        if (fetchedAt != null && now.isBefore(fetchedAt.plus(FX_REFRESH_INTERVAL))) {
            return latestFxRate.get();
        }
        if (retryAt != null && now.isBefore(retryAt)) {
            if (fetchedAt == null || now.isAfter(fetchedAt.plus(MAX_LAST_FX_AGE))) {
                throw new IllegalStateException("No recent exchange rate is available");
            }
            return latestFxRate.get();
        }
        try {
            ExchangeRateQuote fxRate = exchangeRateClient.fetchUsdKrw();
            ExchangeRateQuote previous = latestFxRate.get();
            if (previous != null && fetchedAt != null && !now.isAfter(fetchedAt.plus(MAX_LAST_FX_AGE))) {
                BigDecimal changeRatio = fxRate.rate()
                        .subtract(previous.rate())
                        .abs()
                        .divide(previous.rate(), MathContext.DECIMAL64);
                if (changeRatio.compareTo(MAX_FX_CHANGE_RATIO) > 0) {
                    throw new IllegalStateException("Exchange rate changed by more than 5% in one refresh");
                }
            }
            latestFxRate.set(fxRate);
            latestFxFetchedAt.set(now);
            nextFxRetryAt.set(null);
            return fxRate;
        } catch (RuntimeException ex) {
            nextFxRetryAt.set(now.plus(FX_RETRY_BACKOFF));
            Instant lastFetchedAt = latestFxFetchedAt.get();
            if (lastFetchedAt == null || now.isAfter(lastFetchedAt.plus(MAX_LAST_FX_AGE))) {
                throw new IllegalStateException("No recent exchange rate is available", ex);
            }
            log.warn("Failed to fetch Upbit KRW-USDC rate. Using recent rate: {}", latestFxRate.get().rate(), ex);
            return latestFxRate.get();
        }
    }

    private Optional<Instant> oldestUpdate(Set<String> symbols) {
        return symbols.stream().map(latestStockAtByCode::get).filter(java.util.Objects::nonNull).min(Instant::compareTo);
    }

    private Optional<QuoteSnapshot> snapshotFromCache(Set<String> symbols, Instant at) {
        List<StockQuote> stocks = catalog.all().stream()
                .filter(asset -> symbols.contains(asset.code()))
                .map(asset -> latestStockByCode.get(asset.code()))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (stocks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new QuoteSnapshot(MarketMode.ESTIMATE, at, stocks));
    }

    private QuoteSnapshot snapshotFromAllCached(Instant at) {
        return snapshotFromCache(catalog.allCodes(), at).orElseThrow();
    }
}
