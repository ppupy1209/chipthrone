package dev.yeonwoo.chipthrone.quote.model;

import java.math.BigDecimal;

public record OfficialStockPrice(
        String code,
        BigDecimal close,
        String closeDate,
        BigDecimal high,
        long sharesOutstanding,
        BigDecimal marketCap
) {
}
