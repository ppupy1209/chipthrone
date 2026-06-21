package dev.yeonwoo.chipthrone.quote;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

@Service
public class MarketModeService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime REGULAR_OPEN = LocalTime.of(9, 0);
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(15, 30);
    private static final LocalTime NXT_OPEN = LocalTime.of(15, 40);
    private static final LocalTime NXT_CLOSE = LocalTime.of(20, 0);

    public MarketMode determine(Instant at) {
        ZonedDateTime kst = at.atZone(KST);
        if (isWeekend(kst.getDayOfWeek())) {
            return MarketMode.ESTIMATE;
        }

        LocalTime time = kst.toLocalTime();
        if (!time.isBefore(REGULAR_OPEN) && !time.isAfter(REGULAR_CLOSE)) {
            return MarketMode.REGULAR;
        }
        if (!time.isBefore(NXT_OPEN) && !time.isAfter(NXT_CLOSE)) {
            return MarketMode.NXT;
        }
        return MarketMode.ESTIMATE;
    }

    private boolean isWeekend(DayOfWeek dayOfWeek) {
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }
}
