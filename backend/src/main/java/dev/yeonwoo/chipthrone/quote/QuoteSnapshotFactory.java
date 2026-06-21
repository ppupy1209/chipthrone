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

    private static final String CHANGE_BASIS_PREVIOUS_DAY = "전일 대비";
    private static final String CHANGE_BASIS_NXT_CLOSE = "애프터마켓 대비";
    private static final String CHANGE_BASIS_REGULAR_CLOSE = "정규장 종가 대비";

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
        BigDecimal regularClose = regularClose(kisQuote, mode);
        BigDecimal nxtClose = kisQuote == null ? null : kisQuote.nxtClose();
        Change change = change(
                mode,
                useKisCurrentPrice,
                kisQuote,
                priceKrw,
                hyperliquidChangePct,
                regularClose,
                nxtClose
        );
        BigDecimal marketCap = priceKrw.multiply(BigDecimal.valueOf(asset.sharesOutstanding()));

        return new StockQuote(
                asset.code(),
                asset.name(),
                priceKrw.doubleValue(),
                priceUsd.doubleValue(),
                change.changePct().doubleValue(),
                change.basis(),
                asset.sharesOutstanding(),
                marketCap.doubleValue(),
                regularClose == null ? null : regularClose.doubleValue(),
                nxtClose == null ? null : nxtClose.doubleValue()
        );
    }

    private Change change(
            MarketMode mode,
            boolean useKisCurrentPrice,
            KisStockQuote kisQuote,
            BigDecimal priceKrw,
            BigDecimal hyperliquidChangePct,
            BigDecimal regularClose,
            BigDecimal nxtClose
    ) {
        if (useKisCurrentPrice && kisQuote.changePct() != null) {
            return new Change(kisQuote.changePct(), CHANGE_BASIS_PREVIOUS_DAY);
        }
        if (mode == MarketMode.ESTIMATE) {
            if (isPositive(nxtClose)) {
                return new Change(changeFrom(priceKrw, nxtClose), CHANGE_BASIS_NXT_CLOSE);
            }
            if (isPositive(regularClose)) {
                return new Change(changeFrom(priceKrw, regularClose), CHANGE_BASIS_REGULAR_CLOSE);
            }
        }
        return new Change(hyperliquidChangePct, CHANGE_BASIS_PREVIOUS_DAY);
    }

    private BigDecimal changeFrom(BigDecimal priceKrw, BigDecimal basis) {
        return priceKrw
                .subtract(basis)
                .divide(basis, 12, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
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

    private record Change(BigDecimal changePct, String basis) {
    }
}
