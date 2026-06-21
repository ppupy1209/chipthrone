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
        return create(prices, fxRate, Map.of());
    }

    public QuoteSnapshot create(
            List<MarketAssetPrice> prices,
            BigDecimal fxRate,
            Map<String, KisStockQuote> kisQuoteByCode
    ) {
        Instant at = clock.instant();
        MarketMode mode = marketModeService.determine(at);
        Map<String, MarketAssetPrice> priceBySymbol = prices.stream()
                .collect(Collectors.toMap(MarketAssetPrice::symbol, Function.identity(), (left, right) -> left));

        List<StockQuote> stocks = properties.assets().stream()
                .map(asset -> toStockQuote(
                        asset,
                        requirePrice(priceBySymbol, asset.symbol()),
                        kisQuoteByCode.get(asset.code()),
                        fxRate,
                        mode
                ))
                .toList();

        return new QuoteSnapshot(mode, at, fxRate.doubleValue(), stocks);
    }

    private MarketAssetPrice requirePrice(Map<String, MarketAssetPrice> priceBySymbol, String symbol) {
        MarketAssetPrice price = priceBySymbol.get(symbol);
        if (price == null) {
            throw new IllegalStateException("Missing market price for symbol: " + symbol);
        }
        return price;
    }

    private StockQuote toStockQuote(
            QuoteProperties.Asset asset,
            MarketAssetPrice price,
            KisStockQuote kisQuote,
            BigDecimal fxRate,
            MarketMode mode
    ) {
        BigDecimal hyperliquidPriceKrw = price.markPx().multiply(fxRate);
        BigDecimal hyperliquidChangePct = price.markPx()
                .divide(price.prevDayPx(), 12, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100));
        boolean useKisCurrentPrice = kisQuote != null
                && (mode == MarketMode.REGULAR || mode == MarketMode.NXT)
                && kisQuote.priceKrw() != null;
        BigDecimal priceKrw = useKisCurrentPrice ? kisQuote.priceKrw() : hyperliquidPriceKrw;
        BigDecimal priceUsd = useKisCurrentPrice
                ? kisQuote.priceKrw().divide(fxRate, 12, RoundingMode.HALF_UP)
                : price.markPx();
        BigDecimal changePct = useKisCurrentPrice && kisQuote.changePct() != null
                ? kisQuote.changePct()
                : hyperliquidChangePct;
        BigDecimal regularClose = regularClose(kisQuote, mode);
        BigDecimal marketCap = priceKrw.multiply(BigDecimal.valueOf(asset.sharesOutstanding()));

        // TODO: KIS NXT close is not exposed reliably through this endpoint; keep nxtClose null until a stable source exists.
        return new StockQuote(
                asset.code(),
                asset.name(),
                priceKrw.doubleValue(),
                priceUsd.doubleValue(),
                changePct.doubleValue(),
                asset.sharesOutstanding(),
                marketCap.doubleValue(),
                regularClose == null ? null : regularClose.doubleValue(),
                null
        );
    }

    private BigDecimal regularClose(KisStockQuote kisQuote, MarketMode mode) {
        if (kisQuote == null) {
            return null;
        }
        if (mode == MarketMode.REGULAR && kisQuote.previousRegularClose() != null) {
            return kisQuote.previousRegularClose();
        }
        return kisQuote.priceKrw() != null ? kisQuote.priceKrw() : kisQuote.previousRegularClose();
    }
}
