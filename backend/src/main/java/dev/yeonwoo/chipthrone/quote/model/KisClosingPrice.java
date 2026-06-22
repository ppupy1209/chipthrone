package dev.yeonwoo.chipthrone.quote.model;

import java.math.BigDecimal;

public record KisClosingPrice(
        String code,
        BigDecimal regularClose,
        String regularCloseDate,
        BigDecimal regularHigh,
        BigDecimal nxtClose,
        String nxtCloseDate
) {
}
