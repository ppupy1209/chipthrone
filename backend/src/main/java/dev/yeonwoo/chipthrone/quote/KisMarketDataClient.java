package dev.yeonwoo.chipthrone.quote;

import java.util.Optional;

public interface KisMarketDataClient {

    boolean enabled();

    Optional<KisStockQuote> fetchStockQuote(String code);
}
