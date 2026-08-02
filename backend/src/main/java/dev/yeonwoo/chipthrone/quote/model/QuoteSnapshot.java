package dev.yeonwoo.chipthrone.quote.model;

import java.time.Instant;
import java.util.List;

public record QuoteSnapshot(
        MarketMode mode,
        Instant at,
        double fxRate,
        String fxAsOfDate,
        String fxSource,
        /** 환율을 마지막으로 조회한 시각. 설정 기준값이면 null */
        Instant fxFetchedAt,
        List<StockQuote> stocks
) {
    public QuoteSnapshot(MarketMode mode, Instant at, double fxRate, List<StockQuote> stocks) {
        this(mode, at, fxRate, null, "CONFIG_FALLBACK", null, stocks);
    }
}
