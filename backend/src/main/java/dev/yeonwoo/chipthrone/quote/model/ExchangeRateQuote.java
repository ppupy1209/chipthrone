package dev.yeonwoo.chipthrone.quote.model;

import java.math.BigDecimal;

public record ExchangeRateQuote(BigDecimal rate, String asOfDate, String source) {
}
