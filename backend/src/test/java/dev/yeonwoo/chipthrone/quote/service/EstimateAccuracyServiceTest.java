package dev.yeonwoo.chipthrone.quote.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.yeonwoo.chipthrone.quote.client.ExchangeRateClient;
import dev.yeonwoo.chipthrone.quote.client.MarketDataClient;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.EstimateAccuracy;
import dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice;
import dev.yeonwoo.chipthrone.quote.model.OfficialStockPrice;

import org.junit.jupiter.api.Test;

class EstimateAccuracyServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-22T04:00:00Z"), ZoneOffset.UTC);
    private static final QuoteProperties.Asset SAMSUNG = new QuoteProperties.Asset(
            "005930", "삼성전자", "xyz:SMSN", 5_919_637_922L, QuoteProperties.Market.KRX);
    private static final QuoteProperties.Asset NVIDIA = new QuoteProperties.Asset(
            "NVDA", "엔비디아", "xyz:NVDA", 24_200_000_000L, QuoteProperties.Market.US);

    @Test
    void comparesCloseTimeEstimateAgainstOfficialClose() {
        // 15:30 KST 추정 50 USD × 1450 = 72,500원, 확정 종가 71,000원 → +2.11%
        StubMarketDataClient market = new StubMarketDataClient(new BigDecimal("50"));
        EstimateAccuracyService service = new EstimateAccuracyService(market, new StubFxClient(true), CLOCK);

        Map<String, EstimateAccuracy> accuracies =
                service.accuracies(List.of(SAMSUNG), Map.of("005930", official("71000", "2026-06-19")));

        EstimateAccuracy accuracy = accuracies.get("005930");
        assertThat(accuracy.closeDate()).isEqualTo("2026-06-19");
        assertThat(accuracy.estimateKrw()).isEqualByComparingTo("72500");
        assertThat(accuracy.divergencePct()).isEqualByComparingTo("2.1126760563");
        // 2026-06-19 15:30 KST == 06:30 UTC
        assertThat(market.requestedAt).isEqualTo(Instant.parse("2026-06-19T06:30:00Z"));
        assertThat(market.requestedSymbol).isEqualTo("xyz:SMSN");
    }

    @Test
    void reusesCachedValueWhileCloseDateIsUnchanged() {
        StubMarketDataClient market = new StubMarketDataClient(new BigDecimal("50"));
        EstimateAccuracyService service = new EstimateAccuracyService(market, new StubFxClient(true), CLOCK);
        Map<String, OfficialStockPrice> official = Map.of("005930", official("71000", "2026-06-19"));

        service.accuracies(List.of(SAMSUNG), official);
        service.accuracies(List.of(SAMSUNG), official);

        assertThat(market.calls).isEqualTo(1);
    }

    @Test
    void skipsUsAssetsAndMissingOfficialPrices() {
        StubMarketDataClient market = new StubMarketDataClient(new BigDecimal("50"));
        EstimateAccuracyService service = new EstimateAccuracyService(market, new StubFxClient(true), CLOCK);

        Map<String, EstimateAccuracy> accuracies = service.accuracies(List.of(SAMSUNG, NVIDIA), Map.of());

        assertThat(accuracies).isEmpty();
        assertThat(market.calls).isZero();
    }

    @Test
    void yieldsNothingWhenExchangeRateSourceIsDisabled() {
        StubMarketDataClient market = new StubMarketDataClient(new BigDecimal("50"));
        EstimateAccuracyService service = new EstimateAccuracyService(market, new StubFxClient(false), CLOCK);

        Map<String, EstimateAccuracy> accuracies =
                service.accuracies(List.of(SAMSUNG), Map.of("005930", official("71000", "2026-06-19")));

        assertThat(accuracies).isEmpty();
        assertThat(market.calls).isZero();
    }

    @Test
    void yieldsNothingWhenCandleIsMissing() {
        StubMarketDataClient market = new StubMarketDataClient(null);
        EstimateAccuracyService service = new EstimateAccuracyService(market, new StubFxClient(true), CLOCK);

        Map<String, EstimateAccuracy> accuracies =
                service.accuracies(List.of(SAMSUNG), Map.of("005930", official("71000", "2026-06-19")));

        assertThat(accuracies).isEmpty();
    }

    private OfficialStockPrice official(String close, String closeDate) {
        BigDecimal value = new BigDecimal(close);
        return new OfficialStockPrice("005930", value, closeDate, value, 5_919_637_922L, value);
    }

    private static final class StubMarketDataClient implements MarketDataClient {
        private final BigDecimal close;
        private int calls;
        private String requestedSymbol;
        private Instant requestedAt;

        private StubMarketDataClient(BigDecimal close) {
            this.close = close;
        }

        @Override
        public List<MarketAssetPrice> fetchAssetPrices(String dex) {
            return List.of();
        }

        @Override
        public Optional<BigDecimal> fetchCloseAt(String symbol, Instant at) {
            calls++;
            requestedSymbol = symbol;
            requestedAt = at;
            return Optional.ofNullable(close);
        }
    }

    private static final class StubFxClient implements ExchangeRateClient {
        private final boolean enabled;

        private StubFxClient(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote fetchUsdKrw() {
            return fetchUsdKrw(LocalDate.of(2026, 6, 19));
        }

        @Override
        public dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote fetchUsdKrw(LocalDate date) {
            return new dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote(
                    new BigDecimal("1450"), date.toString(), "KOREA_EXIMBANK");
        }
    }
}
