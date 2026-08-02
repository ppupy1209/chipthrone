package dev.yeonwoo.chipthrone.quote.model;

import java.time.Instant;
import java.util.List;

public record QuoteSnapshot(
        MarketMode mode,
        Instant at,
        double fxRate,
        String fxAsOfDate,
        String fxSource,
        List<StockQuote> stocks
) {
    public QuoteSnapshot(MarketMode mode, Instant at, double fxRate, List<StockQuote> stocks) {
        this(mode, at, fxRate, null, "CONFIG_FALLBACK", stocks);
    }
}
