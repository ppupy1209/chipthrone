package dev.yeonwoo.chipthrone.quote.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

import dev.yeonwoo.chipthrone.quote.model.MarketMode;

import org.springframework.stereotype.Service;

@Service
public class MarketModeService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime PREMARKET_OPEN = LocalTime.of(8, 0);
    private static final LocalTime REGULAR_OPEN = LocalTime.of(9, 0);
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(15, 30);
    private static final LocalTime NXT_OPEN = LocalTime.of(15, 40);
    private static final LocalTime NXT_CLOSE = LocalTime.of(20, 0);

    // 휴장일(주말 외). 대체공휴일·연말 폐장일 포함. 음력 공휴일이 매년 바뀌므로 연도별로 갱신 필요.
    private static final Set<LocalDate> HOLIDAYS = Set.of(
            // 2026
            LocalDate.of(2026, 1, 1),    // 신정
            LocalDate.of(2026, 2, 16),   // 설날 연휴
            LocalDate.of(2026, 2, 17),   // 설날
            LocalDate.of(2026, 2, 18),   // 설날 연휴
            LocalDate.of(2026, 3, 1),    // 삼일절
            LocalDate.of(2026, 3, 2),    // 삼일절 대체공휴일
            LocalDate.of(2026, 5, 5),    // 어린이날
            LocalDate.of(2026, 5, 24),   // 부처님오신날
            LocalDate.of(2026, 5, 25),   // 부처님오신날 대체공휴일
            LocalDate.of(2026, 6, 6),    // 현충일
            LocalDate.of(2026, 8, 15),   // 광복절
            LocalDate.of(2026, 8, 17),   // 광복절 대체공휴일
            LocalDate.of(2026, 9, 24),   // 추석 연휴
            LocalDate.of(2026, 9, 25),   // 추석
            LocalDate.of(2026, 9, 26),   // 추석 연휴
            LocalDate.of(2026, 9, 28),   // 추석 대체공휴일
            LocalDate.of(2026, 10, 3),   // 개천절
            LocalDate.of(2026, 10, 5),   // 개천절 대체공휴일
            LocalDate.of(2026, 10, 9),   // 한글날
            LocalDate.of(2026, 12, 25),  // 성탄절
            LocalDate.of(2026, 12, 31)   // 연말 폐장일(KRX)
    );

    public MarketMode determine(Instant at) {
        ZonedDateTime kst = at.atZone(KST);
        if (isWeekend(kst.getDayOfWeek()) || isHoliday(kst.toLocalDate())) {
            return MarketMode.ESTIMATE;
        }

        LocalTime time = kst.toLocalTime();
        if (!time.isBefore(PREMARKET_OPEN) && time.isBefore(REGULAR_OPEN)) {
            return MarketMode.PREMARKET;
        }
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

    private boolean isHoliday(LocalDate date) {
        return HOLIDAYS.contains(date);
    }
}
