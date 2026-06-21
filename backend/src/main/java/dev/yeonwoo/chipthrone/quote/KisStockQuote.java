package dev.yeonwoo.chipthrone.quote;

import java.math.BigDecimal;

public record KisStockQuote(
        String code,
        BigDecimal priceKrw,
        BigDecimal changePct,
        BigDecimal previousRegularClose
) {
}
