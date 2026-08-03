package dev.yeonwoo.chipthrone.quote.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.yeonwoo.chipthrone.quote.client.MarketDataClient;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.SessionClose;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 미국 종목의 "종가 대비" 기준가. 직전 정규장 마감(16:00 ET) 시점의 추정가를 구한다.
 *
 * <p>Hyperliquid가 주는 {@code prevDayPx}는 24시간 롤링 기준가라 증권앱의 전일 종가 대비 등락률과 다르다.
 * 기준 시각이 매 순간 움직여서 사용자가 다른 곳에서 보는 숫자와 어긋난다.
 *
 * <p>미국 공휴일 캘린더는 없다. 휴장일이면 그날 16:00 ET의 perp 가격이 기준가가 되는데,
 * 실제 체결이 없던 날이라 엄밀한 "종가"는 아니다. 값 자체는 시장가라 크게 어긋나지 않는다.
 */
@Service
public class UsSessionCloseService {

    private static final Logger log = LoggerFactory.getLogger(UsSessionCloseService.class);
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(16, 0);
    private static final Duration RETRY_BACKOFF = Duration.ofMinutes(30);

    private final MarketDataClient marketDataClient;
    private final Clock clock;
    private final Map<String, SessionClose> closeByCode = new ConcurrentHashMap<>();
    private final Map<String, Instant> nextRetryAtByCode = new ConcurrentHashMap<>();

    public UsSessionCloseService(MarketDataClient marketDataClient, Clock clock) {
        this.marketDataClient = marketDataClient;
        this.clock = clock;
    }

    /** 기준일이 바뀐 종목만 다시 계산하고, 나머지는 캐시를 그대로 돌려준다. */
    public Map<String, SessionClose> sessionCloses(List<QuoteProperties.Asset> assets) {
        Instant now = clock.instant();
        ZonedDateTime closedAt = lastRegularClose(now);
        String closeDate = closedAt.toLocalDate().toString();
        assets.stream()
                .filter(asset -> asset.market() == QuoteProperties.Market.US)
                .forEach(asset -> refresh(asset, closedAt.toInstant(), closeDate, now));
        return Map.copyOf(closeByCode);
    }

    /** 직전에 이미 끝난 정규장 마감 시각. 주말은 건너뛴다. */
    private ZonedDateTime lastRegularClose(Instant now) {
        ZonedDateTime et = now.atZone(NEW_YORK);
        ZonedDateTime candidate = et.with(REGULAR_CLOSE);
        if (candidate.isAfter(et)) {
            candidate = candidate.minusDays(1);
        }
        while (candidate.getDayOfWeek() == DayOfWeek.SATURDAY || candidate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    private void refresh(QuoteProperties.Asset asset, Instant closedAt, String closeDate, Instant now) {
        String code = asset.code();
        SessionClose cached = closeByCode.get(code);
        if (cached != null && cached.closeDate().equals(closeDate)) {
            return;
        }
        Instant retryAt = nextRetryAtByCode.get(code);
        if (retryAt != null && now.isBefore(retryAt)) {
            return;
        }
        try {
            marketDataClient.fetchCloseAt(asset.symbol(), closedAt).ifPresentOrElse(
                    closeUsd -> {
                        closeByCode.put(code, new SessionClose(code, closeDate, closeUsd));
                        nextRetryAtByCode.remove(code);
                    },
                    () -> nextRetryAtByCode.put(code, now.plus(RETRY_BACKOFF))
            );
        } catch (RuntimeException ex) {
            nextRetryAtByCode.put(code, now.plus(RETRY_BACKOFF));
            log.warn("Failed to fetch US session close for {}. Keeping cached value.", code, ex);
        }
    }
}
