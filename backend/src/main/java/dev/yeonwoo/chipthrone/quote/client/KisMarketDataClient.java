package dev.yeonwoo.chipthrone.quote.client;

import java.util.Optional;

import dev.yeonwoo.chipthrone.quote.model.KisStockQuote;

public interface KisMarketDataClient {

    boolean enabled();

    Optional<KisStockQuote> fetchStockQuote(String code);
}
