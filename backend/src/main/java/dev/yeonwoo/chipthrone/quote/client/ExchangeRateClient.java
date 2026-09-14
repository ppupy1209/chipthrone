package dev.yeonwoo.chipthrone.quote.client;

import dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote;

public interface ExchangeRateClient {

    boolean enabled();

    ExchangeRateQuote fetchUsdKrw();
}
