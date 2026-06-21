package dev.yeonwoo.chipthrone.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class QuoteServiceTest {

    @Test
    void refreshStoresSnapshotAndKeepsItWhenMarketFetchFails() {
        StubMarketDataClient marketDataClient = new StubMarketDataClient();
        StubExchangeRateClient exchangeRateClient = new StubExchangeRateClient();
        QuoteService service = newService(marketDataClient, exchangeRateClient);

        Optional<QuoteSnapshot> first = service.refresh();
        marketDataClient.fail = true;
        Optional<QuoteSnapshot> second = service.refresh();

        assertThat(first).isPresent();
        assertThat(second).containsSame(first.orElseThrow());
        assertThat(marketDataClient.calls).isEqualTo(2);
    }

    @Test
    void refreshUsesLastFxRateWhenExchangeRateFetchFails() {
        StubMarketDataClient marketDataClient = new StubMarketDataClient();
        StubExchangeRateClient exchangeRateClient = new StubExchangeRateClient();
        QuoteService service = newService(marketDataClient, exchangeRateClient);

        QuoteSnapshot first = service.refresh().orElseThrow();
        exchangeRateClient.fail = true;
        QuoteSnapshot second = service.refresh().orElseThrow();

        assertThat(first.fxRate()).isEqualTo(1476.8);
        assertThat(second.fxRate()).isEqualTo(1476.8);
    }

    private QuoteService newService(StubMarketDataClient marketDataClient, StubExchangeRateClient exchangeRateClient) {
        QuoteProperties properties = properties();
        QuoteSnapshotFactory factory = new QuoteSnapshotFactory(
                properties,
                new MarketModeService(),
                Clock.fixed(Instant.parse("2026-06-22T01:00:00Z"), ZoneOffset.UTC)
        );
        return new QuoteService(marketDataClient, exchangeRateClient, properties, factory, new QuoteBroadcaster());
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

    private static class StubMarketDataClient implements MarketDataClient {
        private int calls;
        private boolean fail;

        @Override
        public List<MarketAssetPrice> fetchAssetPrices(String dex) {
            calls++;
            if (fail) {
                throw new IllegalStateException("market unavailable");
            }
            return List.of(
                    new MarketAssetPrice("xyz:SMSN", new BigDecimal("241.18"), new BigDecimal("238.70")),
                    new MarketAssetPrice("xyz:SKHX", new BigDecimal("1913.95"), new BigDecimal("1869.28"))
            );
        }
    }

    private static class StubExchangeRateClient implements ExchangeRateClient {
        private boolean fail;

        @Override
        public BigDecimal fetchUsdKrw() {
            if (fail) {
                throw new IllegalStateException("fx unavailable");
            }
            return new BigDecimal("1476.8");
        }
    }
}
