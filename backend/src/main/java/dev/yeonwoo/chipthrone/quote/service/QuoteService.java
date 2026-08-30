package dev.yeonwoo.chipthrone.quote.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
import dev.yeonwoo.chipthrone.quote.client.OfficialStockPriceClient;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.EstimateAccuracy;
import dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote;
import dev.yeonwoo.chipthrone.quote.model.MarketMode;
import dev.yeonwoo.chipthrone.quote.model.OfficialStockPrice;
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
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration DAILY_SOURCE_RETRY_BACKOFF = Duration.ofMinutes(10);
    private static final LocalTime FSC_PUBLISH_TIME = LocalTime.of(13, 5);
    // Pyth는 초 단위로 갱신되고 키·호출 한도가 없다. 환율 지연이 추정가 오차의 가장 큰 항이라
    // 1분까지 좁힌다. 시세 batch와 합치지 않는 이유는 polling cycle당 호출 1회 원칙을 지키기 위해서다.
    private static final Duration FX_REFRESH_INTERVAL = Duration.ofMinutes(1);

    private final MarketDataClient marketDataClient;
    private final OfficialStockPriceClient officialStockPriceClient;
    private final ExchangeRateClient exchangeRateClient;
    private final EstimateAccuracyService estimateAccuracyService;
    private final UsSessionCloseService usSessionCloseService;
    private final String dex;
    private final AssetCatalog catalog;
    private final QuoteSnapshotFactory snapshotFactory;
    private final QuoteBroadcaster broadcaster;
    private final AlertService alertService;
    private final QuoteMetrics metrics;
    private final Clock clock;
    private final AtomicReference<QuoteSnapshot> latestSnapshot = new AtomicReference<>();
    private final AtomicReference<ExchangeRateQuote> latestFxRate;
    private final AtomicReference<Instant> latestFxFetchedAt = new AtomicReference<>();
    private final AtomicReference<Instant> nextFxRetryAt = new AtomicReference<>();
    private final Map<String, OfficialStockPrice> officialByCode = new ConcurrentHashMap<>();
    private final Map<String, Instant> officialFetchedAtByCode = new ConcurrentHashMap<>();
    private final Map<String, Instant> nextOfficialRetryAtByCode = new ConcurrentHashMap<>();
    private final Map<String, StockQuote> latestStockByCode = new ConcurrentHashMap<>();
    private final Map<String, Instant> latestStockAtByCode = new ConcurrentHashMap<>();

    public QuoteService(
            MarketDataClient marketDataClient,
            OfficialStockPriceClient officialStockPriceClient,
            ExchangeRateClient exchangeRateClient,
            EstimateAccuracyService estimateAccuracyService,
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
        this.officialStockPriceClient = officialStockPriceClient;
        this.exchangeRateClient = exchangeRateClient;
        this.estimateAccuracyService = estimateAccuracyService;
        this.usSessionCloseService = usSessionCloseService;
        this.dex = properties.dex();
        this.catalog = catalog;
        this.snapshotFactory = snapshotFactory;
        this.broadcaster = broadcaster;
        this.alertService = alertService;
        this.metrics = metrics;
        this.clock = clock;
        this.latestFxRate = new AtomicReference<>(new ExchangeRateQuote(
                BigDecimal.valueOf(properties.initialFxRate()), null, "CONFIG_FALLBACK"));
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
            Map<String, OfficialStockPrice> official = fetchOfficialPrices(assets);
            Map<String, EstimateAccuracy> accuracy = estimateAccuracyService.accuracies(assets, official);
            Map<String, SessionClose> sessionCloses = usSessionCloseService.sessionCloses(assets);
            QuoteSnapshot refreshed =
                    snapshotFactory.create(prices, fxRate, official, accuracy, sessionCloses, assets);
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
            log.warn("Failed to refresh Hyperliquid estimate. Keeping last quote snapshot.", ex);
            return currentSnapshot(symbols);
        }
    }

    private Map<String, OfficialStockPrice> fetchOfficialPrices(List<QuoteProperties.Asset> assets) {
        if (!officialStockPriceClient.enabled()) {
            return Map.copyOf(officialByCode);
        }
        Instant now = clock.instant();
        boolean attempted = false;
        boolean failed = false;
        for (QuoteProperties.Asset asset : assets) {
            if (asset.market() != QuoteProperties.Market.KRX) {
                continue;
            }
            String code = asset.code();
            Instant retryAt = nextOfficialRetryAtByCode.get(code);
            if (!shouldRefreshDaily(officialFetchedAtByCode.get(code), FSC_PUBLISH_TIME)
                    || (retryAt != null && now.isBefore(retryAt))) {
                continue;
            }
            attempted = true;
            try {
                Optional<OfficialStockPrice> latest = officialStockPriceClient.fetchLatest(code);
                if (latest.isPresent()) {
                    officialByCode.put(code, latest.get());
                    officialFetchedAtByCode.put(code, now);
                    nextOfficialRetryAtByCode.remove(code);
                } else {
                    failed = true;
                    nextOfficialRetryAtByCode.put(code, now.plus(DAILY_SOURCE_RETRY_BACKOFF));
                    log.warn("Official daily price was empty for {}. Keeping cached value.", code);
                }
            } catch (RuntimeException ex) {
                failed = true;
                nextOfficialRetryAtByCode.put(code, now.plus(DAILY_SOURCE_RETRY_BACKOFF));
                log.warn("Failed to fetch official daily price for {}. Keeping cached value.", code, ex);
            }
        }
        if (attempted) {
            if (failed) {
                alertService.recordFailure(AlertEvent.OFFICIAL_CLOSE);
            } else {
                alertService.recordSuccess(AlertEvent.OFFICIAL_CLOSE);
            }
        }
        return Map.copyOf(officialByCode);
    }

    private ExchangeRateQuote fetchFxRateOrFallback() {
        if (!exchangeRateClient.enabled()) {
            return latestFxRate.get();
        }
        Instant now = clock.instant();
        Instant retryAt = nextFxRetryAt.get();
        Instant fetchedAt = latestFxFetchedAt.get();
        if ((fetchedAt != null && now.isBefore(fetchedAt.plus(FX_REFRESH_INTERVAL)))
                || (retryAt != null && now.isBefore(retryAt))) {
            return latestFxRate.get();
        }
        try {
            ExchangeRateQuote fxRate = exchangeRateClient.fetchUsdKrw();
            latestFxRate.set(fxRate);
            latestFxFetchedAt.set(now);
            nextFxRetryAt.set(null);
            return fxRate;
        } catch (RuntimeException ex) {
            nextFxRetryAt.set(now.plus(DAILY_SOURCE_RETRY_BACKOFF));
            log.warn("Failed to fetch Pyth USD/KRW rate. Using last rate: {}", latestFxRate.get().rate(), ex);
            return latestFxRate.get();
        }
    }

    private boolean shouldRefreshDaily(Instant fetchedAt, LocalTime publishTime) {
        if (fetchedAt == null) {
            return true;
        }
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(KST));
        ZonedDateTime fetched = fetchedAt.atZone(KST);
        if (!fetched.toLocalDate().equals(now.toLocalDate())) {
            return true;
        }
        return fetched.toLocalTime().isBefore(publishTime) && !now.toLocalTime().isBefore(publishTime);
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
        ExchangeRateQuote fxRate = latestFxRate.get();
        return Optional.of(new QuoteSnapshot(
                MarketMode.ESTIMATE,
                at,
                fxRate.rate().doubleValue(),
                fxRate.asOfDate(),
                fxRate.source(),
                fxRate.fetchedAt(),
                stocks
        ));
    }

    private QuoteSnapshot snapshotFromAllCached(Instant at) {
        return snapshotFromCache(catalog.allCodes(), at).orElseThrow();
    }
}
