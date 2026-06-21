package dev.yeonwoo.chipthrone.quote;

import java.math.BigDecimal;

public record MarketAssetPrice(
        String symbol,
        BigDecimal markPx,
        BigDecimal prevDayPx
) {
}
