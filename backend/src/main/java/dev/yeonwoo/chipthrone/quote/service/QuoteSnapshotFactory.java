package dev.yeonwoo.chipthrone.quote.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.EstimateAccuracy;
import dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote;
import dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice;
import dev.yeonwoo.chipthrone.quote.model.MarketMode;
import dev.yeonwoo.chipthrone.quote.model.OfficialStockPrice;
import dev.yeonwoo.chipthrone.quote.model.QuoteSnapshot;
import dev.yeonwoo.chipthrone.quote.model.StockQuote;

import org.springframework.stereotype.Service;

@Service
public class QuoteSnapshotFactory {

    private final QuoteProperties properties;
    private final Clock clock;

    public QuoteSnapshotFactory(QuoteProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public QuoteSnapshot create(List<MarketAssetPrice> prices, ExchangeRateQuote fxRate) {
        return create(prices, fxRate, Map.of(), Map.of(), properties.assets());
    }

    public QuoteSnapshot create(
            List<MarketAssetPrice> prices,
            ExchangeRateQuote fxRate,
            Map<String, OfficialStockPrice> officialByCode,
            Map<String, EstimateAccuracy> accuracyByCode,
            List<QuoteProperties.Asset> assets
    ) {
        Map<String, MarketAssetPrice> priceBySymbol = prices.stream()
                .collect(Collectors.toMap(MarketAssetPrice::symbol, Function.identity(), (left, right) -> left));
        List<StockQuote> stocks = assets.stream()
                .map(asset -> toStockQuote(
                        asset,
                        requirePrice(priceBySymbol, asset.symbol()),
                        officialByCode.get(asset.code()),
                        accuracyByCode.get(asset.code()),
                        fxRate.rate()
                ))
                .toList();
        return new QuoteSnapshot(
                MarketMode.ESTIMATE,
                clock.instant(),
                fxRate.rate().doubleValue(),
                fxRate.asOfDate(),
                fxRate.source(),
                fxRate.fetchedAt(),
                stocks
        );
    }

    private MarketAssetPrice requirePrice(Map<String, MarketAssetPrice> priceBySymbol, String symbol) {
        MarketAssetPrice price = priceBySymbol.get(symbol);
        if (price == null) {
            throw new IllegalStateException("Missing Hyperliquid price for symbol: " + symbol);
        }
        return price;
    }

    private StockQuote toStockQuote(
            QuoteProperties.Asset asset,
            MarketAssetPrice price,
            OfficialStockPrice official,
            EstimateAccuracy accuracy,
            BigDecimal fxRate
    ) {
        BigDecimal priceKrw = price.markPx().multiply(fxRate);
        BigDecimal changePct = price.markPx()
                .divide(price.prevDayPx(), 12, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100));
        long shares = official == null || official.sharesOutstanding() <= 0
                ? asset.sharesOutstanding()
                : official.sharesOutstanding();
        BigDecimal estimatedMarketCap = priceKrw.multiply(BigDecimal.valueOf(shares));

        return new StockQuote(
                asset.code(),
                asset.name(),
                priceKrw.doubleValue(),
                price.markPx().doubleValue(),
                changePct.doubleValue(),
                shares,
                estimatedMarketCap.doubleValue(),
                official == null ? null : official.marketCap().doubleValue(),
                official == null ? null : official.close().doubleValue(),
                official == null ? null : official.closeDate(),
                official == null ? null : official.high().doubleValue(),
                null,
                null,
                asset.market().name(),
                "HYPERLIQUID",
                "ESTIMATE",
                accuracy == null ? null : accuracy.estimateKrw().doubleValue(),
                accuracy == null ? null : accuracy.divergencePct().doubleValue()
        );
    }
}
