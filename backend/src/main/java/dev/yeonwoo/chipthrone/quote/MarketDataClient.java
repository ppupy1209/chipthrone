package dev.yeonwoo.chipthrone.quote;

import java.util.List;

public interface MarketDataClient {

    List<MarketAssetPrice> fetchAssetPrices(String dex);
}
