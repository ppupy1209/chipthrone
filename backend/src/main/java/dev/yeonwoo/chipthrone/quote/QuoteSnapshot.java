package dev.yeonwoo.chipthrone.quote;

import java.time.Instant;
import java.util.List;

public record QuoteSnapshot(
        MarketMode mode,
        Instant at,
        double fxRate,
        List<StockQuote> stocks
) {
}
