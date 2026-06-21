package dev.yeonwoo.chipthrone.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class QuoteSnapshotFactory {

    private final QuoteProperties properties;
    private final MarketModeService marketModeService;
    private final Clock clock;

    public QuoteSnapshotFactory(QuoteProperties properties, MarketModeService marketModeService, Clock clock) {
        this.properties = properties;
        this.marketModeService = marketModeService;
        this.clock = clock;
    }

    public QuoteSnapshot create(List<MarketAssetPrice> prices, BigDecimal fxRate) {
        Instant at = clock.instant();
        Map<String, MarketAssetPrice> priceBySymbol = prices.stream()
                .collect(Collectors.toMap(MarketAssetPrice::symbol, Function.identity(), (left, right) -> left));

        List<StockQuote> stocks = properties.assets().stream()
                .map(asset -> toStockQuote(asset, requirePrice(priceBySymbol, asset.symbol()), fxRate))
                .toList();

        return new QuoteSnapshot(marketModeService.determine(at), at, fxRate.doubleValue(), stocks);
    }

    private MarketAssetPrice requirePrice(Map<String, MarketAssetPrice> priceBySymbol, String symbol) {
        MarketAssetPrice price = priceBySymbol.get(symbol);
        if (price == null) {
            throw new IllegalStateException("Missing market price for symbol: " + symbol);
        }
        return price;
    }

    private StockQuote toStockQuote(QuoteProperties.Asset asset, MarketAssetPrice price, BigDecimal fxRate) {
        BigDecimal priceKrw = price.markPx().multiply(fxRate);
        BigDecimal changePct = price.markPx()
                .divide(price.prevDayPx(), 12, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100));
        BigDecimal marketCap = priceKrw.multiply(BigDecimal.valueOf(asset.sharesOutstanding()));

        // TODO: Wire KIS regular/NXT close data here. Until then every mode uses Hyperliquid estimate prices.
        return new StockQuote(
                asset.code(),
                asset.name(),
                priceKrw.doubleValue(),
                price.markPx().doubleValue(),
                changePct.doubleValue(),
                asset.sharesOutstanding(),
                marketCap.doubleValue(),
                null,
                null
        );
    }
}
