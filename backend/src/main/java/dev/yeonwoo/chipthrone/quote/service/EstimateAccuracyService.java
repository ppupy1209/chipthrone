package dev.yeonwoo.chipthrone.quote.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.yeonwoo.chipthrone.quote.client.ExchangeRateClient;
import dev.yeonwoo.chipthrone.quote.client.MarketDataClient;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.EstimateAccuracy;
import dev.yeonwoo.chipthrone.quote.model.OfficialStockPrice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 확정 종가와 같은 시점의 추정가를 비교해 실제 괴리율을 구한다.
 *
 * <p>현재가와 전일 종가를 비교하면 시점이 어긋나 등락률이 섞인다. 확정 종가 기준일의
 * 정규장 마감 시각 캔들과 그날 고시환율을 써야 추정 자체의 오차만 남는다.
 */
@Service
public class EstimateAccuracyService {

    private static final Logger log = LoggerFactory.getLogger(EstimateAccuracyService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime KRX_CLOSE = LocalTime.of(15, 30);
    private static final Duration RETRY_BACKOFF = Duration.ofMinutes(30);
    private static final int SCALE = 12;

    private final MarketDataClient marketDataClient;
    private final ExchangeRateClient exchangeRateClient;
    private final Clock clock;
    private final Map<String, EstimateAccuracy> accuracyByCode = new ConcurrentHashMap<>();
    private final Map<String, Instant> nextRetryAtByCode = new ConcurrentHashMap<>();

    public EstimateAccuracyService(
            MarketDataClient marketDataClient,
            ExchangeRateClient exchangeRateClient,
            Clock clock
    ) {
        this.marketDataClient = marketDataClient;
        this.exchangeRateClient = exchangeRateClient;
        this.clock = clock;
    }

    /** 확정 종가 기준일이 바뀐 종목만 다시 계산하고, 나머지는 캐시를 그대로 돌려준다. */
    public Map<String, EstimateAccuracy> accuracies(
            List<QuoteProperties.Asset> assets,
            Map<String, OfficialStockPrice> officialByCode
    ) {
        if (!exchangeRateClient.enabled()) {
            return Map.copyOf(accuracyByCode);
        }
        Instant now = clock.instant();
        assets.stream()
                .filter(asset -> asset.market() == QuoteProperties.Market.KRX)
                .forEach(asset -> refresh(asset, officialByCode.get(asset.code()), now));
        return Map.copyOf(accuracyByCode);
    }

    private void refresh(QuoteProperties.Asset asset, OfficialStockPrice official, Instant now) {
        String code = asset.code();
        if (official == null || official.closeDate() == null
                || official.close() == null || official.close().signum() <= 0) {
            return;
        }
        EstimateAccuracy cached = accuracyByCode.get(code);
        if (cached != null && cached.closeDate().equals(official.closeDate())) {
            return;
        }
        Instant retryAt = nextRetryAtByCode.get(code);
        if (retryAt != null && now.isBefore(retryAt)) {
            return;
        }
        try {
            compute(asset, official).ifPresentOrElse(
                    accuracy -> {
                        accuracyByCode.put(code, accuracy);
                        nextRetryAtByCode.remove(code);
                    },
                    () -> nextRetryAtByCode.put(code, now.plus(RETRY_BACKOFF))
            );
        } catch (RuntimeException ex) {
            nextRetryAtByCode.put(code, now.plus(RETRY_BACKOFF));
            log.warn("Failed to compute estimate divergence for {}. Keeping cached value.", code, ex);
        }
    }

    private Optional<EstimateAccuracy> compute(QuoteProperties.Asset asset, OfficialStockPrice official) {
        LocalDate closeDate;
        try {
            closeDate = LocalDate.parse(official.closeDate());
        } catch (DateTimeParseException ex) {
            log.warn("Unparseable official close date for {}: {}", asset.code(), official.closeDate());
            return Optional.empty();
        }
        Instant closedAt = closeDate.atTime(KRX_CLOSE).atZone(KST).toInstant();
        return marketDataClient.fetchCloseAt(asset.symbol(), closedAt)
                .map(estimateUsd -> {
                    // 캔들과 같은 순간의 환율을 쓴다. 그날 종일 고정된 값을 쓰면 환율 변동이 오차에 섞인다.
                    BigDecimal fxRate = exchangeRateClient.fetchUsdKrw(closedAt).rate();
                    BigDecimal estimateKrw = estimateUsd.multiply(fxRate);
                    BigDecimal divergencePct = estimateKrw
                            .divide(official.close(), SCALE, RoundingMode.HALF_UP)
                            .subtract(BigDecimal.ONE)
                            .multiply(BigDecimal.valueOf(100));
                    return new EstimateAccuracy(asset.code(), official.closeDate(), estimateKrw, divergencePct);
                });
    }
}
