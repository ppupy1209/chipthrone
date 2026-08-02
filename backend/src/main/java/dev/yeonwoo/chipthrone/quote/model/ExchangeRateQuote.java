package dev.yeonwoo.chipthrone.quote.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param asOfDate  고시 기준일(yyyy-MM-dd). 설정 기준값이면 null
 * @param fetchedAt 이 값을 실제로 조회한 시각. 설정 기준값이면 null
 */
public record ExchangeRateQuote(BigDecimal rate, String asOfDate, String source, Instant fetchedAt) {

    public ExchangeRateQuote(BigDecimal rate, String asOfDate, String source) {
        this(rate, asOfDate, source, null);
    }
}
