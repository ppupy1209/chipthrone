package dev.yeonwoo.chipthrone.quote.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.EstimateAccuracy;
import dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote;
import dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice;
import dev.yeonwoo.chipthrone.quote.model.OfficialStockPrice;
import dev.yeonwoo.chipthrone.quote.model.StockQuote;

import org.junit.jupiter.api.Test;

class QuoteSnapshotFactoryTest {

    @Test
    void combinesHyperliquidEstimateWithOfficialDailyPrice() {
        QuoteProperties properties = properties();
        QuoteSnapshotFactory factory = new QuoteSnapshotFactory(
                properties,
                Clock.fixed(Instant.parse("2026-06-22T01:00:00Z"), ZoneOffset.UTC)
        );
        OfficialStockPrice official = new OfficialStockPrice(
                "005930", new BigDecimal("71000"), "2026-06-19", new BigDecimal("72500"),
                5_900_000_000L, new BigDecimal("418900000000000")
        );

        EstimateAccuracy accuracy = new EstimateAccuracy(
                "005930", "2026-06-19", new BigDecimal("72420"), new BigDecimal("2.0")
        );

        StockQuote stock = factory.create(
                List.of(new MarketAssetPrice("xyz:SMSN", new BigDecimal("240"), new BigDecimal("235"))),
                new ExchangeRateQuote(new BigDecimal("1450"), "2026-06-19", "UPBIT_USDC"),
                Map.of("005930", official),
                Map.of("005930", accuracy),
                Map.of(),
                properties.assets()
        ).stocks().getFirst();

        assertThat(stock.priceKrw()).isEqualTo(348000.0);
        assertThat(stock.priceUsd()).isEqualTo(240.0);
        assertThat(stock.regularClose()).isEqualTo(71000.0);
        assertThat(stock.regularCloseDate()).isEqualTo("2026-06-19");
        assertThat(stock.officialMarketCap()).isEqualTo(418900000000000.0);
        assertThat(stock.sharesOutstanding()).isEqualTo(5_900_000_000L);
        assertThat(stock.officialCloseEstimate()).isEqualTo(72420.0);
        assertThat(stock.officialDivergencePct()).isEqualTo(2.0);
        assertThat(stock.source()).isEqualTo("HYPERLIQUID");
        assertThat(stock.status()).isEqualTo("ESTIMATE");
    }

    @Test
    void keepsConfiguredSharesWhenOfficialPriceIsUnavailable() {
        QuoteProperties properties = properties();
        StockQuote stock = new QuoteSnapshotFactory(properties, Clock.systemUTC()).create(
                List.of(new MarketAssetPrice("xyz:SMSN", new BigDecimal("240"), new BigDecimal("235"))),
                new ExchangeRateQuote(new BigDecimal("1450"), null, "UPBIT_USDC")
        ).stocks().getFirst();

        assertThat(stock.sharesOutstanding()).isEqualTo(5_919_637_922L);
        assertThat(stock.regularClose()).isNull();
        assertThat(stock.officialMarketCap()).isNull();
    }

    private QuoteProperties properties() {
        return new QuoteProperties("xyz", List.of(
                new QuoteProperties.Asset(
                        "005930", "삼성전자", "xyz:SMSN", 5_919_637_922L, QuoteProperties.Market.KRX)
        ));
    }
}
