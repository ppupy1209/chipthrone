package dev.yeonwoo.chipthrone.quote.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote;
import dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice;
import dev.yeonwoo.chipthrone.quote.model.StockQuote;

import org.junit.jupiter.api.Test;

class QuoteSnapshotFactoryTest {

    @Test
    void estimatesKrwPriceAndMarketCapWithConfiguredShares() {
        QuoteProperties properties = properties();
        QuoteSnapshotFactory factory = new QuoteSnapshotFactory(
                properties,
                Clock.fixed(Instant.parse("2026-06-22T01:00:00Z"), ZoneOffset.UTC)
        );

        StockQuote stock = factory.create(
                List.of(new MarketAssetPrice("xyz:SMSN", new BigDecimal("240"), new BigDecimal("235"))),
                new ExchangeRateQuote(new BigDecimal("1450"), "2026-06-19", "UPBIT_USDC")
        ).stocks().getFirst();

        assertThat(stock.priceKrw()).isEqualTo(348000.0);
        assertThat(stock.priceUsd()).isEqualTo(240.0);
        assertThat(stock.sharesOutstanding()).isEqualTo(5_919_637_922L);
        assertThat(stock.marketCap()).isEqualTo(348000.0 * 5_919_637_922L);
        assertThat(stock.source()).isEqualTo("HYPERLIQUID");
        assertThat(stock.status()).isEqualTo("ESTIMATE");
    }

    private QuoteProperties properties() {
        return new QuoteProperties("xyz", List.of(
                new QuoteProperties.Asset(
                        "005930", "삼성전자", "xyz:SMSN", 5_919_637_922L, QuoteProperties.Market.KRX)
        ));
    }
}
