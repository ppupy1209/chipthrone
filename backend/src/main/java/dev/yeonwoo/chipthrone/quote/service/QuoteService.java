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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.yeonwoo.chipthrone.quote.client.ExchangeRateClient;
import dev.yeonwoo.chipthrone.quote.client.KisMarketDataClient;
import dev.yeonwoo.chipthrone.quote.client.MarketDataClient;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.KisClosingPrice;
import dev.yeonwoo.chipthrone.quote.model.KisStockQuote;
import dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice;
import dev.yeonwoo.chipthrone.quote.model.MarketMode;
import dev.yeonwoo.chipthrone.quote.model.QuoteSnapshot;
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

    private final MarketDataClient marketDataClient;
    private final KisMarketDataClient kisMarketDataClient;
    private final ExchangeRateClient exchangeRateClient;
    private final QuoteProperties properties;
    private final QuoteSnapshotFactory snapshotFactory;
    private final QuoteBroadcaster broadcaster;
    private final MarketModeService marketModeService;
    private final Clock clock;
    private final AtomicReference<QuoteSnapshot> latestSnapshot = new AtomicReference<>();
    private final AtomicReference<BigDecimal> latestFxRate;
    private final Map<String, KisStockQuote> latestCurrentQuoteByCode = new ConcurrentHashMap<>();
    private final Map<String, KisClosingPrice> closingPriceByCode = new ConcurrentHashMap<>();
    private final Map<String, Instant> nextCloseRetryAtByCode = new ConcurrentHashMap<>();
    private final Map<String, Boolean> closeFailureLoggedByCode = new ConcurrentHashMap<>();

    public QuoteService(
            MarketDataClient marketDataClient,
            KisMarketDataClient kisMarketDataClient,
            ExchangeRateClient exchangeRateClient,
            QuoteProperties properties,
            QuoteSnapshotFactory snapshotFactory,
            QuoteBroadcaster broadcaster,
            MarketModeService marketModeService,
            Clock clock
    ) {
        this.marketDataClient = marketDataClient;
        this.kisMarketDataClient = kisMarketDataClient;
        this.exchangeRateClient = exchangeRateClient;
        this.properties = properties;
        this.snapshotFactory = snapshotFactory;
        this.broadcaster = broadcaster;
        this.marketModeService = marketModeService;
        this.clock = clock;
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
        MarketMode mode = marketModeService.determine(clock.instant());
        Map<String, KisStockQuote> kisQuoteByCode = fetchKisQuotesOrEmpty(mode);
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

    private Map<String, KisStockQuote> fetchKisQuotesOrEmpty(MarketMode mode) {
        if (!kisMarketDataClient.enabled()) {
            return Map.of();
        }
        return properties.assets().stream()
                .map(asset -> kisQuoteFor(asset, mode))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(KisStockQuote::code, Function.identity(), (left, right) -> left));
    }

    private Optional<KisStockQuote> kisQuoteFor(QuoteProperties.Asset asset, MarketMode mode) {
        Optional<KisClosingPrice> closingPrice = refreshClosingPriceIfNeeded(asset.code());
        Optional<KisStockQuote> currentQuote = fetchCurrentKisQuoteOrCached(asset, mode);
        if (currentQuote.isEmpty() && closingPrice.isEmpty()) {
            return Optional.empty();
        }

        KisStockQuote current = currentQuote.orElse(null);
        KisClosingPrice close = closingPrice.orElse(null);
        return Optional.of(new KisStockQuote(
                asset.code(),
                current == null ? null : current.priceKrw(),
                current == null ? null : current.changePct(),
                current == null ? null : current.previousRegularClose(),
                close == null ? null : close.regularClose(),
                close == null ? null : close.regularCloseDate(),
                close == null ? null : close.regularHigh(),
                close == null ? null : close.nxtClose(),
                close == null ? null : close.nxtCloseDate()
        ));
    }

    private Optional<KisStockQuote> fetchCurrentKisQuoteOrCached(QuoteProperties.Asset asset, MarketMode mode) {
        if (mode == MarketMode.ESTIMATE) {
            return Optional.empty();
        }
        String marketDivisionCode = currentMarketDivisionCode(mode);
        try {
            Optional<KisStockQuote> quote = kisMarketDataClient.fetchCurrentStockQuote(asset.code(), marketDivisionCode)
                    .filter(value -> isPositive(value.priceKrw()));
            quote.ifPresent(value -> latestCurrentQuoteByCode.put(asset.code(), value));
            return quote.or(() -> currentQuoteFallback(asset.code(), mode));
        } catch (RuntimeException ex) {
            KisStockQuote fallback = currentQuoteFallback(asset.code(), mode).orElse(null);
            if (fallback == null) {
                log.warn("Failed to fetch KIS current quote for code {}. Falling back for this stock.", asset.code(), ex);
            } else {
                log.warn("Failed to fetch KIS current quote for code {}. Using last current quote.", asset.code(), ex);
            }
            return Optional.ofNullable(fallback);
        }
    }

    private Optional<KisStockQuote> currentQuoteFallback(String code, MarketMode mode) {
        if (mode == MarketMode.PREMARKET) {
            return Optional.empty();
        }
        return Optional.ofNullable(latestCurrentQuoteByCode.get(code));
    }

    private String currentMarketDivisionCode(MarketMode mode) {
        if (mode == MarketMode.PREMARKET || mode == MarketMode.NXT) {
            return NXT_MARKET_DIVISION_CODE;
        }
        return KRX_MARKET_DIVISION_CODE;
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
        LocalDate latestClosedDate = latestClosedRegularTradingDate();
        LocalDate cachedDate = LocalDate.parse(cached.regularCloseDate());
        return latestClosedDate.isAfter(cachedDate);
    }

    private boolean isCloseRetryBackoffActive(String code) {
        Instant nextRetryAt = nextCloseRetryAtByCode.get(code);
        return nextRetryAt != null && clock.instant().isBefore(nextRetryAt);
    }

    private void markClosingPriceFailure(String code, String message, RuntimeException ex) {
        nextCloseRetryAtByCode.put(code, clock.instant().plus(CLOSE_RETRY_BACKOFF));
        boolean alreadyLogged = Boolean.TRUE.equals(closeFailureLoggedByCode.put(code, true));
        if (alreadyLogged) {
            return;
        }
        if (ex == null) {
            log.warn("{} for code {}. Keeping cached close.", message, code);
        } else {
            log.warn("{} for code {}. Keeping cached close.", message, code, ex);
        }
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
}
