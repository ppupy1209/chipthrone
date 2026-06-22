package dev.yeonwoo.chipthrone.quote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.KisStockQuote;
import dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice;
import dev.yeonwoo.chipthrone.quote.model.MarketMode;
import dev.yeonwoo.chipthrone.quote.model.QuoteSnapshot;
import dev.yeonwoo.chipthrone.quote.model.StockQuote;

import org.junit.jupiter.api.Test;

class QuoteSnapshotFactoryTest {

    @Test
    void mapsHyperliquidPricesToConfiguredStocks() {
        Instant fixedTime = Instant.parse("2026-06-22T01:00:00Z");
        QuoteSnapshotFactory factory = new QuoteSnapshotFactory(
                properties(),
                new MarketModeService(),
                Clock.fixed(fixedTime, ZoneOffset.UTC)
        );

        QuoteSnapshot snapshot = factory.create(
                List.of(
                        new MarketAssetPrice("xyz:SMSN", new BigDecimal("241.18"), new BigDecimal("238.70")),
                        new MarketAssetPrice("xyz:SKHX", new BigDecimal("1913.95"), new BigDecimal("1869.28"))
                ),
                new BigDecimal("1476.8")
        );

        assertThat(snapshot.mode()).isEqualTo(MarketMode.REGULAR);
        assertThat(snapshot.at()).isEqualTo(fixedTime);
        assertThat(snapshot.fxRate()).isEqualTo(1476.8);
        assertThat(snapshot.stocks()).hasSize(2);

        StockQuote samsung = snapshot.stocks().getFirst();
        assertThat(samsung.code()).isEqualTo("005930");
        assertThat(samsung.name()).isEqualTo("삼성전자");
        assertThat(samsung.priceUsd()).isEqualTo(241.18);
        assertThat(samsung.priceKrw()).isEqualTo(356174.624);
        assertThat(samsung.changePct()).isCloseTo(1.03896, within(0.00001));
        assertThat(samsung.sharesOutstanding()).isEqualTo(5_919_637_922L);
        assertThat(samsung.marketCap()).isCloseTo(2.1084248110844915E15, within(1.0E6));
        assertThat(samsung.regularClose()).isNull();
        assertThat(samsung.nxtClose()).isNull();
    }

    @Test
    void usesKisCurrentPriceDuringRegularMarketAndMapsNxtClose() {
        QuoteSnapshotFactory factory = new QuoteSnapshotFactory(
                properties(),
                new MarketModeService(),
                Clock.fixed(Instant.parse("2026-06-22T01:00:00Z"), ZoneOffset.UTC)
        );

        QuoteSnapshot snapshot = factory.create(
                List.of(
                        new MarketAssetPrice("xyz:SMSN", new BigDecimal("241.18"), new BigDecimal("238.70")),
                        new MarketAssetPrice("xyz:SKHX", new BigDecimal("1913.95"), new BigDecimal("1869.28"))
                ),
                new BigDecimal("1476.8"),
                Map.of("005930", new KisStockQuote(
                        "005930",
                        new BigDecimal("72000"),
                        new BigDecimal("1.25"),
                        new BigDecimal("71100"),
                        new BigDecimal("71100"),
                        "2026-06-19",
                        new BigDecimal("73000"),
                        new BigDecimal("72500"),
                        "2026-06-19"
                ))
        );

        StockQuote samsung = snapshot.stocks().getFirst();
        assertThat(snapshot.mode()).isEqualTo(MarketMode.REGULAR);
        assertThat(samsung.priceKrw()).isEqualTo(72000.0);
        assertThat(samsung.priceUsd()).isCloseTo(48.75406, within(0.00001));
        assertThat(samsung.changePct()).isEqualTo(1.25);
        assertThat(samsung.regularClose()).isEqualTo(71100.0);
        assertThat(samsung.regularCloseDate()).isEqualTo("2026-06-19");
        assertThat(samsung.high()).isEqualTo(73000.0);
        assertThat(samsung.nxtClose()).isEqualTo(72500.0);
        assertThat(samsung.nxtCloseDate()).isEqualTo("2026-06-19");
    }

    @Test
    void usesKisCurrentPriceAndRegularCloseChangeDuringPremarket() {
        QuoteSnapshotFactory factory = new QuoteSnapshotFactory(
                properties(),
                new MarketModeService(),
                Clock.fixed(Instant.parse("2026-06-21T23:30:00Z"), ZoneOffset.UTC)
        );

        QuoteSnapshot snapshot = factory.create(
                List.of(
                        new MarketAssetPrice("xyz:SMSN", new BigDecimal("241.18"), new BigDecimal("238.70")),
                        new MarketAssetPrice("xyz:SKHX", new BigDecimal("1913.95"), new BigDecimal("1869.28"))
                ),
                new BigDecimal("1476.8"),
                Map.of("005930", new KisStockQuote(
                        "005930",
                        new BigDecimal("72000"),
                        new BigDecimal("1.25"),
                        new BigDecimal("71100"),
                        new BigDecimal("71100"),
                        "2026-06-19",
                        new BigDecimal("73000"),
                        new BigDecimal("72500"),
                        "2026-06-19"
                ))
        );

        StockQuote samsung = snapshot.stocks().getFirst();
        assertThat(snapshot.mode()).isEqualTo(MarketMode.PREMARKET);
        assertThat(samsung.priceKrw()).isEqualTo(72000.0);
        assertThat(samsung.priceUsd()).isCloseTo(48.75406, within(0.00001));
        assertThat(samsung.changePct()).isCloseTo(1.26582, within(0.00001));
        assertThat(samsung.regularClose()).isEqualTo(71100.0);
        assertThat(samsung.high()).isEqualTo(73000.0);
        assertThat(samsung.nxtClose()).isEqualTo(72500.0);
    }

    @Test
    void fallsBackToHyperliquidPriceDuringPremarketWhenKisCurrentPriceIsMissing() {
        QuoteSnapshotFactory factory = new QuoteSnapshotFactory(
                properties(),
                new MarketModeService(),
                Clock.fixed(Instant.parse("2026-06-21T23:30:00Z"), ZoneOffset.UTC)
        );

        QuoteSnapshot snapshot = factory.create(
                List.of(
                        new MarketAssetPrice("xyz:SMSN", new BigDecimal("241.18"), new BigDecimal("238.70")),
                        new MarketAssetPrice("xyz:SKHX", new BigDecimal("1913.95"), new BigDecimal("1869.28"))
                ),
                new BigDecimal("1476.8"),
                Map.of("005930", new KisStockQuote(
                        "005930",
                        null,
                        new BigDecimal("1.25"),
                        new BigDecimal("71100"),
                        new BigDecimal("71100"),
                        "2026-06-19",
                        new BigDecimal("73000"),
                        new BigDecimal("72500"),
                        "2026-06-19"
                ))
        );

        StockQuote samsung = snapshot.stocks().getFirst();
        assertThat(snapshot.mode()).isEqualTo(MarketMode.PREMARKET);
        assertThat(samsung.priceKrw()).isEqualTo(356174.624);
        assertThat(samsung.priceUsd()).isEqualTo(241.18);
        assertThat(samsung.changePct()).isCloseTo(400.94884, within(0.00001));
        assertThat(samsung.regularClose()).isEqualTo(71100.0);
        assertThat(samsung.high()).isEqualTo(73000.0);
    }

    @Test
    void fallsBackToHyperliquidChangeDuringPremarketWhenRegularCloseIsMissingOrZero() {
        QuoteSnapshotFactory factory = new QuoteSnapshotFactory(
                properties(),
                new MarketModeService(),
                Clock.fixed(Instant.parse("2026-06-21T23:30:00Z"), ZoneOffset.UTC)
        );

        QuoteSnapshot snapshot = factory.create(
                List.of(
                        new MarketAssetPrice("xyz:SMSN", new BigDecimal("241.18"), new BigDecimal("238.70")),
                        new MarketAssetPrice("xyz:SKHX", new BigDecimal("1913.95"), new BigDecimal("1869.28"))
                ),
                new BigDecimal("1476.8"),
                Map.of("005930", new KisStockQuote(
                        "005930",
                        new BigDecimal("72000"),
                        new BigDecimal("1.25"),
                        new BigDecimal("71100"),
                        BigDecimal.ZERO,
                        "2026-06-19",
                        new BigDecimal("73000"),
                        new BigDecimal("72500"),
                        "2026-06-19"
                ))
        );

        StockQuote samsung = snapshot.stocks().getFirst();
        assertThat(snapshot.mode()).isEqualTo(MarketMode.PREMARKET);
        assertThat(samsung.priceKrw()).isEqualTo(72000.0);
        assertThat(samsung.priceUsd()).isCloseTo(48.75406, within(0.00001));
        assertThat(samsung.changePct()).isCloseTo(1.03896, within(0.00001));
        assertThat(samsung.regularClose()).isEqualTo(0.0);

        StockQuote skHynix = snapshot.stocks().get(1);
        assertThat(skHynix.priceKrw()).isEqualTo(2826521.36);
        assertThat(skHynix.priceUsd()).isEqualTo(1913.95);
        assertThat(skHynix.changePct()).isCloseTo(2.38969, within(0.00001));
        assertThat(skHynix.regularClose()).isNull();
    }

    @Test
    void usesNxtCloseForEstimateChangeWhenAvailable() {
        QuoteSnapshotFactory factory = new QuoteSnapshotFactory(
                properties(),
                new MarketModeService(),
                Clock.fixed(Instant.parse("2026-06-22T13:00:00Z"), ZoneOffset.UTC)
        );

        QuoteSnapshot snapshot = factory.create(
                List.of(
                        new MarketAssetPrice("xyz:SMSN", new BigDecimal("241.18"), new BigDecimal("238.70")),
                        new MarketAssetPrice("xyz:SKHX", new BigDecimal("1913.95"), new BigDecimal("1869.28"))
                ),
                new BigDecimal("1476.8"),
                Map.of("005930", new KisStockQuote(
                        "005930",
                        new BigDecimal("72000"),
                        new BigDecimal("1.25"),
                        new BigDecimal("71100"),
                        new BigDecimal("71100"),
                        "2026-06-19",
                        new BigDecimal("73000"),
                        new BigDecimal("72500"),
                        "2026-06-19"
                ))
        );

        StockQuote samsung = snapshot.stocks().getFirst();
        assertThat(snapshot.mode()).isEqualTo(MarketMode.ESTIMATE);
        assertThat(samsung.priceKrw()).isEqualTo(356174.624);
        assertThat(samsung.priceUsd()).isEqualTo(241.18);
        assertThat(samsung.changePct()).isCloseTo(391.27534, within(0.00001));
        assertThat(samsung.regularClose()).isEqualTo(71100.0);
        assertThat(samsung.high()).isEqualTo(73000.0);
        assertThat(samsung.nxtClose()).isEqualTo(72500.0);
    }

    @Test
    void fallsBackToRegularCloseForEstimateChangeWhenNxtCloseIsMissingOrZero() {
        QuoteSnapshotFactory factory = new QuoteSnapshotFactory(
                properties(),
                new MarketModeService(),
                Clock.fixed(Instant.parse("2026-06-22T13:00:00Z"), ZoneOffset.UTC)
        );

        QuoteSnapshot snapshot = factory.create(
                List.of(
                        new MarketAssetPrice("xyz:SMSN", new BigDecimal("241.18"), new BigDecimal("238.70")),
                        new MarketAssetPrice("xyz:SKHX", new BigDecimal("1913.95"), new BigDecimal("1869.28"))
                ),
                new BigDecimal("1476.8"),
                Map.of("005930", new KisStockQuote(
                        "005930",
                        new BigDecimal("72000"),
                        new BigDecimal("1.25"),
                        new BigDecimal("71100"),
                        new BigDecimal("71100"),
                        "2026-06-19",
                        new BigDecimal("73000"),
                        BigDecimal.ZERO,
                        "2026-06-19"
                ))
        );

        StockQuote samsung = snapshot.stocks().getFirst();
        assertThat(snapshot.mode()).isEqualTo(MarketMode.ESTIMATE);
        assertThat(samsung.changePct()).isCloseTo(400.9488, within(0.0001));
        assertThat(samsung.regularClose()).isEqualTo(71100.0);
        assertThat(samsung.high()).isEqualTo(73000.0);
        assertThat(samsung.nxtClose()).isEqualTo(0.0);
    }

    private QuoteProperties properties() {
        return new QuoteProperties(
                3000,
                false,
                "xyz",
                1450,
                List.of(
                        new QuoteProperties.Asset("005930", "삼성전자", "xyz:SMSN", 5_919_637_922L),
                        new QuoteProperties.Asset("000660", "SK하이닉스", "xyz:SKHX", 728_002_365L)
                )
        );
    }
}
