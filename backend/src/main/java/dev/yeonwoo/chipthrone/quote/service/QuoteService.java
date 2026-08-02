package dev.yeonwoo.chipthrone.quote.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.yeonwoo.chipthrone.alert.AlertEvent;
import dev.yeonwoo.chipthrone.alert.AlertService;
import dev.yeonwoo.chipthrone.quote.client.AlpacaMarketDataClient;
import dev.yeonwoo.chipthrone.quote.client.ExchangeRateClient;
import dev.yeonwoo.chipthrone.quote.client.KisMarketDataClient;
import dev.yeonwoo.chipthrone.quote.client.MarketDataClient;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.KisClosingPrice;
import dev.yeonwoo.chipthrone.quote.model.KisStockQuote;
import dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice;
import dev.yeonwoo.chipthrone.quote.model.MarketMode;
import dev.yeonwoo.chipthrone.quote.model.QuoteSnapshot;
import dev.yeonwoo.chipthrone.quote.model.StockQuote;
import dev.yeonwoo.chipthrone.quote.web.QuoteBroadcaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String KRX_MARKET_DIVISION_CODE = "J";
    private static final String NXT_MARKET_DIVISION_CODE = "NX";
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(15, 30);
    private static final Duration CLOSE_RETRY_BACKOFF = Duration.ofSeconds(30);
    private static final Duration FX_RETRY_BACKOFF = Duration.ofMinutes(1);

    private final MarketDataClient marketDataClient;
    private final AlpacaMarketDataClient alpacaMarketDataClient;
    private final KisMarketDataClient kisMarketDataClient;
    private final ExchangeRateClient exchangeRateClient;
    private final String dex;
    private final AssetCatalog catalog;
    private final QuoteSnapshotFactory snapshotFactory;
    private final QuoteBroadcaster broadcaster;
    private final MarketModeService marketModeService;
    private final AlertService alertService;
    private final QuoteMetrics metrics;
    private final Clock clock;
    private final AtomicReference<QuoteSnapshot> latestSnapshot = new AtomicReference<>();
    private final AtomicReference<BigDecimal> latestFxRate;
    private final AtomicReference<LocalDate> latestFxFetchedOn = new AtomicReference<>();
    private final AtomicReference<Instant> nextFxRetryAt = new AtomicReference<>();
    private final Map<String, StockQuote> latestStockByCode = new ConcurrentHashMap<>();
    private final Map<String, Instant> latestStockAtByCode = new ConcurrentHashMap<>();
    private final Map<String, KisStockQuote> latestCurrentQuoteByCode = new ConcurrentHashMap<>();
    private final Map<String, KisClosingPrice> closingPriceByCode = new ConcurrentHashMap<>();
    private final Map<String, Instant> nextCloseRetryAtByCode = new ConcurrentHashMap<>();
    private final Map<String, Boolean> closeFailureLoggedByCode = new ConcurrentHashMap<>();

    public QuoteService(
            MarketDataClient marketDataClient,
            AlpacaMarketDataClient alpacaMarketDataClient,
            KisMarketDataClient kisMarketDataClient,
            ExchangeRateClient exchangeRateClient,
            QuoteProperties properties,
            AssetCatalog catalog,
            QuoteSnapshotFactory snapshotFactory,
            QuoteBroadcaster broadcaster,
            MarketModeService marketModeService,
            AlertService alertService,
            QuoteMetrics metrics,
            Clock clock
    ) {
        this.marketDataClient = marketDataClient;
        this.alpacaMarketDataClient = alpacaMarketDataClient;
        this.kisMarketDataClient = kisMarketDataClient;
        this.exchangeRateClient = exchangeRateClient;
        this.dex = properties.dex();
        this.catalog = catalog;
        this.snapshotFactory = snapshotFactory;
        this.broadcaster = broadcaster;
        this.marketModeService = marketModeService;
        this.alertService = alertService;
        this.metrics = metrics;
        this.clock = clock;
        this.latestFxRate = new AtomicReference<>(BigDecimal.valueOf(properties.initialFxRate()));
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
        Instant startedAt = clock.instant();
        long startedNanos = System.nanoTime();
        MarketMode mode = marketModeService.determine(startedAt);

        if (marketModeService.isNoTradeBreak(startedAt) && hasAllCached(symbols)) {
            QuoteSnapshot frozen = snapshotFromCache(symbols, startedAt).orElseThrow();
            latestSnapshot.set(snapshotFromAllCached(startedAt));
            broadcaster.publish(frozen);
            metrics.poll(mode, true, System.nanoTime() - startedNanos);
            return Optional.of(frozen);
        }

        AlpacaFetchResult alpacaFetchResult = fetchAlpacaQuotesOrEmpty(assets);
        List<MarketAssetPrice> prices = fetchHyperliquidIfNeeded(assets, alpacaFetchResult);
        if (prices == null) {
            alertService.recordFailure(AlertEvent.QUOTE_SOURCE);
            metrics.poll(mode, false, System.nanoTime() - startedNanos);
            return currentSnapshot(symbols);
        }
        BigDecimal fxRate = fetchFxRateOrFallback();
        KisQuoteFetchResult kisQuoteFetchResult = fetchKisQuotesOrEmpty(assets, mode);
        try {
            QuoteSnapshot refreshed = snapshotFactory.create(
                    overlayAlpacaPrices(prices, assets, alpacaFetchResult.pricesByCode()),
                    fxRate,
                    kisQuoteFetchResult.quotesByCode(),
                    assets,
                    alpacaFetchResult.pricesByCode().keySet());
            refreshed.stocks().forEach(stock -> {
                latestStockByCode.put(stock.code(), stock);
                latestStockAtByCode.put(stock.code(), refreshed.at());
            });
            latestSnapshot.set(snapshotFromAllCached(refreshed.at()));
            broadcaster.publish(refreshed);
            alertService.recordSuccess(AlertEvent.QUOTE_SOURCE);
            if (kisQuoteFetchResult.regularEstimateFallback()) {
                alertService.recordFailure(AlertEvent.KIS_PERSISTENT);
            } else if (mode == MarketMode.REGULAR) {
                alertService.recordSuccess(AlertEvent.KIS_PERSISTENT);
            }
            metrics.poll(mode, true, System.nanoTime() - startedNanos);
            return Optional.of(refreshed);
        } catch (RuntimeException ex) {
            alertService.recordFailure(AlertEvent.QUOTE_SOURCE);
            metrics.poll(mode, false, System.nanoTime() - startedNanos);
            log.warn("Failed to build quote snapshot. Keeping last quote snapshot.", ex);
            return currentSnapshot(symbols);
        }
    }

    private BigDecimal fetchFxRateOrFallback() {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        Instant now = clock.instant();
        Instant retryAt = nextFxRetryAt.get();
        if (today.equals(latestFxFetchedOn.get()) || (retryAt != null && now.isBefore(retryAt))) {
            return latestFxRate.get();
        }
        try {
            BigDecimal fxRate = exchangeRateClient.fetchUsdKrw();
            latestFxRate.set(fxRate);
            latestFxFetchedOn.set(today);
            nextFxRetryAt.set(null);
            return fxRate;
        } catch (RuntimeException ex) {
            nextFxRetryAt.set(now.plus(FX_RETRY_BACKOFF));
            log.warn("Failed to fetch USD/KRW rate. Using last rate: {}", latestFxRate.get(), ex);
            return latestFxRate.get();
        }
    }

    private KisQuoteFetchResult fetchKisQuotesOrEmpty(List<QuoteProperties.Asset> assets, MarketMode mode) {
        if (!kisMarketDataClient.enabled()) {
            return new KisQuoteFetchResult(Map.of(), false);
        }
        List<KisQuoteResult> results = assets.stream()
                .filter(asset -> asset.market() == QuoteProperties.Market.KRX)
                .map(asset -> kisQuoteFor(asset, mode))
                .toList();
        Map<String, KisStockQuote> quotesByCode = results.stream()
                .map(KisQuoteResult::quote)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(KisStockQuote::code, Function.identity(), (left, right) -> left));
        boolean regularEstimateFallback = results.stream().anyMatch(KisQuoteResult::regularEstimateFallback);
        return new KisQuoteFetchResult(quotesByCode, regularEstimateFallback);
    }

    private AlpacaFetchResult fetchAlpacaQuotesOrEmpty(List<QuoteProperties.Asset> assets) {
        Set<String> symbols = assets.stream()
                .filter(asset -> asset.market() == QuoteProperties.Market.US)
                .map(QuoteProperties.Asset::code)
                .collect(Collectors.toSet());
        if (symbols.isEmpty() || !alpacaMarketDataClient.enabled()) {
            return new AlpacaFetchResult(Map.of());
        }
        try {
            return new AlpacaFetchResult(alpacaMarketDataClient.fetchSnapshots(symbols));
        } catch (RuntimeException ex) {
            log.warn("Failed to fetch Alpaca IEX snapshots. Using Hyperliquid estimate fallback.", ex);
            return new AlpacaFetchResult(Map.of());
        }
    }

    private List<MarketAssetPrice> fetchHyperliquidIfNeeded(
            List<QuoteProperties.Asset> assets,
            AlpacaFetchResult alpacaResult
    ) {
        boolean needed = assets.stream().anyMatch(asset ->
                asset.market() == QuoteProperties.Market.KRX
                        || !alpacaResult.pricesByCode().containsKey(asset.code()));
        if (!needed) {
            return List.of();
        }
        try {
            return marketDataClient.fetchAssetPrices(dex);
        } catch (RuntimeException ex) {
            log.warn("Failed to fetch Hyperliquid fallback prices. Keeping last quote snapshot.", ex);
            return null;
        }
    }

    private List<MarketAssetPrice> overlayAlpacaPrices(
            List<MarketAssetPrice> hyperliquidPrices,
            List<QuoteProperties.Asset> assets,
            Map<String, MarketAssetPrice> alpacaPricesByCode
    ) {
        if (alpacaPricesByCode.isEmpty()) {
            return hyperliquidPrices;
        }
        Map<String, MarketAssetPrice> bySymbol = hyperliquidPrices.stream()
                .collect(Collectors.toMap(MarketAssetPrice::symbol, Function.identity(), (left, right) -> left));
        assets.forEach(asset -> {
            MarketAssetPrice alpaca = alpacaPricesByCode.get(asset.code());
            if (alpaca != null) {
                bySymbol.put(asset.symbol(), new MarketAssetPrice(
                        asset.symbol(), alpaca.markPx(), alpaca.prevDayPx()));
            }
        });
        return List.copyOf(bySymbol.values());
    }

    private KisQuoteResult kisQuoteFor(QuoteProperties.Asset asset, MarketMode mode) {
        Optional<KisClosingPrice> closingPrice = refreshClosingPriceIfNeeded(asset.code());
        CurrentKisQuoteResult currentQuote = fetchCurrentKisQuoteOrCached(asset, mode);
        if (currentQuote.isEmpty() && closingPrice.isEmpty()) {
            return new KisQuoteResult(Optional.empty(), currentQuote.regularEstimateFallback());
        }

        KisStockQuote current = currentQuote.quote().orElse(null);
        KisClosingPrice close = closingPrice.orElse(null);
        return new KisQuoteResult(Optional.of(new KisStockQuote(
                asset.code(),
                current == null ? null : current.priceKrw(),
                current == null ? null : current.changePct(),
                current == null ? null : current.previousRegularClose(),
                close == null ? null : close.regularClose(),
                close == null ? null : close.regularCloseDate(),
                close == null ? null : close.regularHigh(),
                close == null ? null : close.nxtClose(),
                close == null ? null : close.nxtCloseDate()
        )), currentQuote.regularEstimateFallback());
    }

    private CurrentKisQuoteResult fetchCurrentKisQuoteOrCached(QuoteProperties.Asset asset, MarketMode mode) {
        if (mode == MarketMode.ESTIMATE) {
            return new CurrentKisQuoteResult(Optional.empty(), false);
        }
        try {
            Optional<KisStockQuote> quote = kisMarketDataClient
                    .fetchCurrentStockQuote(asset.code(), currentMarketDivisionCode(mode))
                    .filter(value -> isPositive(value.priceKrw()));
            quote.ifPresent(value -> latestCurrentQuoteByCode.put(asset.code(), value));
            Optional<KisStockQuote> fallback = quote.or(() -> currentQuoteFallback(asset.code(), mode));
            return new CurrentKisQuoteResult(fallback, mode == MarketMode.REGULAR && fallback.isEmpty());
        } catch (RuntimeException ex) {
            KisStockQuote fallback = currentQuoteFallback(asset.code(), mode).orElse(null);
            log.warn("Failed to fetch KIS current quote for code {}. Using cached/estimate fallback.", asset.code(), ex);
            return new CurrentKisQuoteResult(Optional.ofNullable(fallback), mode == MarketMode.REGULAR && fallback == null);
        }
    }

    private Optional<KisStockQuote> currentQuoteFallback(String code, MarketMode mode) {
        return mode == MarketMode.PREMARKET
                ? Optional.empty()
                : Optional.ofNullable(latestCurrentQuoteByCode.get(code));
    }

    private String currentMarketDivisionCode(MarketMode mode) {
        return mode == MarketMode.PREMARKET || mode == MarketMode.NXT
                ? NXT_MARKET_DIVISION_CODE
                : KRX_MARKET_DIVISION_CODE;
    }

    private Optional<KisClosingPrice> refreshClosingPriceIfNeeded(String code) {
        KisClosingPrice cached = closingPriceByCode.get(code);
        if (!shouldRefreshClosingPrice(cached) || isCloseRetryBackoffActive(code)) {
            return Optional.ofNullable(cached);
        }
        try {
            Optional<KisClosingPrice> fetched = kisMarketDataClient.fetchClosingPrice(code);
            if (fetched.isPresent()) {
                closingPriceByCode.put(code, fetched.orElseThrow());
                nextCloseRetryAtByCode.remove(code);
                closeFailureLoggedByCode.remove(code);
                return fetched;
            }
            markClosingPriceFailure(code, "KIS close response was empty", null);
            return Optional.ofNullable(cached);
        } catch (RuntimeException ex) {
            markClosingPriceFailure(code, "Failed to fetch KIS close", ex);
            return Optional.ofNullable(cached);
        }
    }

    private boolean shouldRefreshClosingPrice(KisClosingPrice cached) {
        if (cached == null || cached.regularCloseDate() == null) {
            return true;
        }
        return latestClosedRegularTradingDate().isAfter(LocalDate.parse(cached.regularCloseDate()));
    }

    private boolean isCloseRetryBackoffActive(String code) {
        Instant nextRetryAt = nextCloseRetryAtByCode.get(code);
        return nextRetryAt != null && clock.instant().isBefore(nextRetryAt);
    }

    private void markClosingPriceFailure(String code, String message, RuntimeException ex) {
        nextCloseRetryAtByCode.put(code, clock.instant().plus(CLOSE_RETRY_BACKOFF));
        boolean alreadyLogged = Boolean.TRUE.equals(closeFailureLoggedByCode.put(code, true));
        if (!alreadyLogged) {
            if (ex == null) {
                log.warn("{} for code {}. Keeping cached close.", message, code);
            } else {
                log.warn("{} for code {}. Keeping cached close.", message, code, ex);
            }
        }
    }

    private boolean hasAllCached(Set<String> symbols) {
        return symbols.stream().allMatch(latestStockByCode::containsKey);
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
        return Optional.of(new QuoteSnapshot(
                marketModeService.determine(clock.instant()), at, latestFxRate.get().doubleValue(), stocks));
    }

    private QuoteSnapshot snapshotFromAllCached(Instant at) {
        return snapshotFromCache(catalog.allCodes(), at).orElseThrow();
    }

    private LocalDate latestClosedRegularTradingDate() {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        LocalTime time = LocalTime.now(clock.withZone(KST));
        if (isWeekday(today) && !time.isBefore(REGULAR_CLOSE)) {
            return today;
        }
        return previousWeekday(today.minusDays(1));
    }

    private LocalDate previousWeekday(LocalDate date) {
        LocalDate candidate = date;
        while (!isWeekday(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    private boolean isWeekday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private record KisQuoteFetchResult(Map<String, KisStockQuote> quotesByCode, boolean regularEstimateFallback) {
    }

    private record AlpacaFetchResult(Map<String, MarketAssetPrice> pricesByCode) {
    }

    private record KisQuoteResult(Optional<KisStockQuote> quote, boolean regularEstimateFallback) {
    }

    private record CurrentKisQuoteResult(Optional<KisStockQuote> quote, boolean regularEstimateFallback) {
        private boolean isEmpty() {
            return quote.isEmpty();
        }
    }
}
