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
        assertThat(service.determine(kstInstant("2026-06-22", "08:59"))).isEqualTo(MarketMode.ESTIMATE);
        assertThat(service.determine(kstInstant("2026-06-22", "15:35"))).isEqualTo(MarketMode.ESTIMATE);
        assertThat(service.determine(kstInstant("2026-06-21", "10:00"))).isEqualTo(MarketMode.ESTIMATE);
    }

    private Instant kstInstant(String date, String time) {
        return LocalDate.parse(date)
                .atTime(LocalTime.parse(time))
                .atZone(kst)
                .toInstant();
    }
}
