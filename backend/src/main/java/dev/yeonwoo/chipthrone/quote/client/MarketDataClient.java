package dev.yeonwoo.chipthrone.quote.client;

import java.util.List;

import dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice;

public interface MarketDataClient {

    List<MarketAssetPrice> fetchAssetPrices(String dex);
}
