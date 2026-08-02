package dev.yeonwoo.chipthrone.quote.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice;

public interface MarketDataClient {

    List<MarketAssetPrice> fetchAssetPrices(String dex);

    /** 지정 시각에 마감된 캔들의 종가. 해당 구간 체결이 없으면 비어 있다. */
    Optional<BigDecimal> fetchCloseAt(String symbol, Instant at);
}
