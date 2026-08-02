package dev.yeonwoo.chipthrone.quote.client;

import java.time.LocalDate;

import dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote;

public interface ExchangeRateClient {

    boolean enabled();

    ExchangeRateQuote fetchUsdKrw();

    /** 지정일 기준 고시환율. 휴일이면 직전 고시일 값으로 대체된다. */
    ExchangeRateQuote fetchUsdKrw(LocalDate date);
}
