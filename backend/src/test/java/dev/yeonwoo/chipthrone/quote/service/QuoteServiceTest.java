package dev.yeonwoo.chipthrone.quote.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import dev.yeonwoo.chipthrone.quote.client.ExchangeRateClient;
import dev.yeonwoo.chipthrone.quote.client.KisMarketDataClient;
import dev.yeonwoo.chipthrone.quote.client.MarketDataClient;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.KisStockQuote;
import dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice;
import dev.yeonwoo.chipthrone.quote.model.QuoteSnapshot;
import dev.yeonwoo.chipthrone.quote.model.StockQuote;
import dev.yeonwoo.chipthrone.quote.web.QuoteBroadcaster;

import org.junit.jupiter.api.Test;

class QuoteServiceTest {

    @Test
    void refreshStoresSnapshotAndKeepsItWhenMarketFetchFails() {
        StubMarketDataClient marketDataClient = new StubMarketDataClient();
        StubKisMarketDataClient kisMarketDataClient = new StubKisMarketDataClient(false);
        StubExchangeRateClient exchangeRateClient = new StubExchangeRateClient();
        QuoteService service = newService(marketDataClient, kisMarketDataClient, exchangeRateClient);

        Optional<QuoteSnapshot> first = service.refresh();
        marketDataClient.fail = true;
        Optional<QuoteSnapshot> second = service.refresh();

        assertThat(first).isPresent();
        assertThat(second).containsSame(first.orElseThrow());
        assertThat(marketDataClient.calls).isEqualTo(2);
        assertThat(kisMarketDataClient.calls).isZero();
    }

    @Test
    void refreshUsesLastFxRateWhenExchangeRateFetchFails() {
        StubMarketDataClient marketDataClient = new StubMarketDataClient();
        StubKisMarketDataClient kisMarketDataClient = new StubKisMarketDataClient(false);
        StubExchangeRateClient exchangeRateClient = new StubExchangeRateClient();
        QuoteService service = newService(marketDataClient, kisMarketDataClient, exchangeRateClient);

        QuoteSnapshot first = service.refresh().orElseThrow();
        exchangeRateClient.fail = true;
        QuoteSnapshot second = service.refresh().orElseThrow();

        assertThat(first.fxRate()).isEqualTo(1476.8);
        assertThat(second.fxRate()).isEqualTo(1476.8);
        assertThat(kisMarketDataClient.calls).isZero();
    }

    @Test
    void refreshFallsBackPerStockWhenKisFetchFails() {
        StubMarketDataClient marketDataClient = new StubMarketDataClient();
        StubKisMarketDataClient kisMarketDataClient = new StubKisMarketDataClient(true);
        kisMarketDataClient.failCode = "005930";
        StubExchangeRateClient exchangeRateClient = new StubExchangeRateClient();
        QuoteService service = newService(marketDataClient, kisMarketDataClient, exchangeRateClient);

        QuoteSnapshot snapshot = service.refresh().orElseThrow();

        StockQuote samsung = snapshot.stocks().getFirst();
        StockQuote hynix = snapshot.stocks().get(1);
        assertThat(kisMarketDataClient.calls).isEqualTo(2);
        assertThat(samsung.priceKrw()).isEqualTo(356174.624);
        assertThat(samsung.regularClose()).isNull();
        assertThat(hynix.priceKrw()).isEqualTo(210000.0);
        assertThat(hynix.regularClose()).isEqualTo(205000.0);
        assertThat(hynix.nxtClose()).isEqualTo(212000.0);
    }

    private QuoteService newService(
            StubMarketDataClient marketDataClient,
            StubKisMarketDataClient kisMarketDataClient,
            StubExchangeRateClient exchangeRateClient
    ) {
        QuoteProperties properties = properties();
        QuoteSnapshotFactory factory = new QuoteSnapshotFactory(
                properties,
                new MarketModeService(),
                Clock.fixed(Instant.parse("2026-06-22T01:00:00Z"), ZoneOffset.UTC)
        );
        return new QuoteService(
                marketDataClient,
                kisMarketDataClient,
                exchangeRateClient,
                properties,
                factory,
                new QuoteBroadcaster()
        );
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

    private static class StubKisMarketDataClient implements KisMarketDataClient {
        private final boolean enabled;
        private int calls;
        private String failCode;

        private StubKisMarketDataClient(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public Optional<KisStockQuote> fetchStockQuote(String code) {
            calls++;
            if (code.equals(failCode)) {
                throw new IllegalStateException("kis unavailable");
            }
            if ("000660".equals(code)) {
                return Optional.of(new KisStockQuote(
                        code,
                        new BigDecimal("210000"),
                        new BigDecimal("2.40"),
                        new BigDecimal("205000"),
                        new BigDecimal("212000")
                ));
            }
            return Optional.empty();
        }
    }
}
