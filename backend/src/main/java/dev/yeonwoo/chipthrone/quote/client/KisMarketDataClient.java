package dev.yeonwoo.chipthrone.quote.client;

import java.util.Optional;

import dev.yeonwoo.chipthrone.quote.model.KisClosingPrice;
import dev.yeonwoo.chipthrone.quote.model.KisStockQuote;

public interface KisMarketDataClient {

    boolean enabled();

    default Optional<KisStockQuote> fetchCurrentStockQuote(String code) {
        return fetchCurrentStockQuote(code, "J");
    }

    Optional<KisStockQuote> fetchCurrentStockQuote(String code, String marketDivisionCode);

    Optional<KisClosingPrice> fetchClosingPrice(String code);
}
