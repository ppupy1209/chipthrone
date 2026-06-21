package dev.yeonwoo.chipthrone.quote;

import java.math.BigDecimal;

public interface ExchangeRateClient {

    BigDecimal fetchUsdKrw();
}
