package dev.yeonwoo.chipthrone.quote.client;

import java.math.BigDecimal;

public interface ExchangeRateClient {

    BigDecimal fetchUsdKrw();
}
