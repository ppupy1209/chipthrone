package dev.yeonwoo.chipthrone.quote.model;

import java.math.BigDecimal;

public record KisStockQuote(
        String code,
        BigDecimal priceKrw,
        BigDecimal changePct,
        BigDecimal previousRegularClose,
        BigDecimal nxtClose
) {
}
