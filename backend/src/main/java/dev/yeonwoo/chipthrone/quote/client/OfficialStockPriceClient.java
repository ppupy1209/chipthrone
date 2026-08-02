package dev.yeonwoo.chipthrone.quote.client;

import java.util.Optional;

import dev.yeonwoo.chipthrone.quote.model.OfficialStockPrice;

public interface OfficialStockPriceClient {

    boolean enabled();

    Optional<OfficialStockPrice> fetchLatest(String code);
}
