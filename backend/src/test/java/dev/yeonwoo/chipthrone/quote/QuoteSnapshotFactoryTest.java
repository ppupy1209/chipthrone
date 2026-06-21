package dev.yeonwoo.chipthrone.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

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
