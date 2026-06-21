package dev.yeonwoo.chipthrone.quote.model;

import java.math.BigDecimal;

public record MarketAssetPrice(
        String symbol,
        BigDecimal markPx,
        BigDecimal prevDayPx
) {
}
