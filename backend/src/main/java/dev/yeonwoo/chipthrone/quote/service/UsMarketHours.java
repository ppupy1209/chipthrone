package dev.yeonwoo.chipthrone.quote.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

@Component
public class UsMarketHours {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalTime OPEN = LocalTime.of(9, 30);
    private static final LocalTime CLOSE = LocalTime.of(16, 0);

    public boolean isOpen(Instant instant) {
        ZonedDateTime now = instant.atZone(NEW_YORK);
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        // ponytail: 미국 휴장일은 정적 추정. 운영 정확도가 필요하면 거래소 캘린더로 교체한다.
        return !time.isBefore(OPEN) && time.isBefore(CLOSE);
    }
}
