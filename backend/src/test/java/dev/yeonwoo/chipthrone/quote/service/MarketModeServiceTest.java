package dev.yeonwoo.chipthrone.quote.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import dev.yeonwoo.chipthrone.quote.model.MarketMode;

import org.junit.jupiter.api.Test;

class MarketModeServiceTest {

    private final MarketModeService service = new MarketModeService();
    private final ZoneId kst = ZoneId.of("Asia/Seoul");

    @Test
    void determinesPremarketDuringWeekdayPremarketSession() {
        assertThat(service.determine(kstInstant("2026-06-22", "08:00"))).isEqualTo(MarketMode.PREMARKET);
        assertThat(service.determine(kstInstant("2026-06-22", "08:30"))).isEqualTo(MarketMode.PREMARKET);
    }

    @Test
    void determinesRegularDuringWeekdayRegularSession() {
        assertThat(service.determine(kstInstant("2026-06-22", "09:00"))).isEqualTo(MarketMode.REGULAR);
        assertThat(service.determine(kstInstant("2026-06-22", "15:30"))).isEqualTo(MarketMode.REGULAR);
    }

    @Test
    void determinesNxtDuringWeekdayNxtSession() {
        assertThat(service.determine(kstInstant("2026-06-22", "15:40"))).isEqualTo(MarketMode.NXT);
        assertThat(service.determine(kstInstant("2026-06-22", "20:00"))).isEqualTo(MarketMode.NXT);
    }

    @Test
    void determinesEstimateOutsideSessionsAndOnWeekend() {
        assertThat(service.determine(kstInstant("2026-06-22", "07:59"))).isEqualTo(MarketMode.ESTIMATE);
        assertThat(service.determine(kstInstant("2026-06-22", "15:35"))).isEqualTo(MarketMode.ESTIMATE);
        assertThat(service.determine(kstInstant("2026-06-21", "10:00"))).isEqualTo(MarketMode.ESTIMATE);
    }

    @Test
    void determinesEstimateOnWeekdayPublicHolidays() {
        // 평일 공휴일은 정규장 시간이라도 휴장 → ESTIMATE
        assertThat(service.determine(kstInstant("2026-01-01", "10:00"))).isEqualTo(MarketMode.ESTIMATE); // 신정(목)
        assertThat(service.determine(kstInstant("2026-12-25", "10:00"))).isEqualTo(MarketMode.ESTIMATE); // 성탄절(금)
        assertThat(service.determine(kstInstant("2026-09-28", "10:00"))).isEqualTo(MarketMode.ESTIMATE); // 추석 대체(월)
        assertThat(service.determine(kstInstant("2026-12-31", "10:00"))).isEqualTo(MarketMode.ESTIMATE); // 연말 폐장(목)
    }

    @Test
    void keepsRegularOnNormalTradingDay() {
        assertThat(service.determine(kstInstant("2026-06-22", "10:00"))).isEqualTo(MarketMode.REGULAR); // 일반 거래일(월)
    }

    @Test
    void detectsNoTradeBreakWindows() {
        // 거래 공백: 08:50~09:00, 15:20~15:40
        assertThat(service.isNoTradeBreak(kstInstant("2026-06-22", "08:55"))).isTrue();
        assertThat(service.isNoTradeBreak(kstInstant("2026-06-22", "15:25"))).isTrue();
        assertThat(service.isNoTradeBreak(kstInstant("2026-06-22", "15:35"))).isTrue();
        // 경계 밖
        assertThat(service.isNoTradeBreak(kstInstant("2026-06-22", "08:49"))).isFalse();
        assertThat(service.isNoTradeBreak(kstInstant("2026-06-22", "09:00"))).isFalse();
        assertThat(service.isNoTradeBreak(kstInstant("2026-06-22", "15:40"))).isFalse();
        // 주말·공휴일엔 공백 개념 없음
        assertThat(service.isNoTradeBreak(kstInstant("2026-06-21", "08:55"))).isFalse();
        assertThat(service.isNoTradeBreak(kstInstant("2026-01-01", "15:25"))).isFalse();
    }

    private Instant kstInstant(String date, String time) {
        return LocalDate.parse(date)
                .atTime(LocalTime.parse(time))
                .atZone(kst)
                .toInstant();
    }
}
