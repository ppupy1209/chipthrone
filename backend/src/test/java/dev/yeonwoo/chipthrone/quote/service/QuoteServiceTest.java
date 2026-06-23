package dev.yeonwoo.chipthrone.quote.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.yeonwoo.chipthrone.quote.client.ExchangeRateClient;
import dev.yeonwoo.chipthrone.quote.client.KisMarketDataClient;
import dev.yeonwoo.chipthrone.quote.client.MarketDataClient;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.KisClosingPrice;
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
        assertThat(kisMarketDataClient.currentCalls).isZero();
        assertThat(kisMarketDataClient.closeCalls).isZero();
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
        assertThat(kisMarketDataClient.currentCalls).isZero();
        assertThat(kisMarketDataClient.closeCalls).isZero();
    }

    @Test
    void refreshFallsBackPerStockWhenKisFetchFails() {
        StubMarketDataClient marketDataClient = new StubMarketDataClient();
        StubKisMarketDataClient kisMarketDataClient = new StubKisMarketDataClient(true);
        kisMarketDataClient.failCurrentCode = "005930";
        StubExchangeRateClient exchangeRateClient = new StubExchangeRateClient();
        QuoteService service = newService(marketDataClient, kisMarketDataClient, exchangeRateClient);

        QuoteSnapshot snapshot = service.refresh().orElseThrow();

        StockQuote samsung = snapshot.stocks().getFirst();
        StockQuote hynix = snapshot.stocks().get(1);
        assertThat(kisMarketDataClient.currentCalls).isEqualTo(2);
        assertThat(kisMarketDataClient.closeCalls).isEqualTo(2);
        assertThat(samsung.priceKrw()).isEqualTo(356174.624);
        assertThat(samsung.regularClose()).isEqualTo(71500.0);
        assertThat(samsung.regularCloseDate()).isEqualTo("2026-06-19");
        assertThat(samsung.high()).isEqualTo(74000.0);
        assertThat(hynix.priceKrw()).isEqualTo(210000.0);
        assertThat(hynix.regularClose()).isEqualTo(205000.0);
        assertThat(hynix.high()).isEqualTo(214000.0);
        assertThat(hynix.nxtClose()).isEqualTo(212000.0);
    }

    @Test
    void refreshFetchesClosingPriceOnceAndReusesCache() {
        StubMarketDataClient marketDataClient = new StubMarketDataClient();
        StubKisMarketDataClient kisMarketDataClient = new StubKisMarketDataClient(true);
        StubExchangeRateClient exchangeRateClient = new StubExchangeRateClient();
        QuoteService service = newService(marketDataClient, kisMarketDataClient, exchangeRateClient);

        QuoteSnapshot first = service.refresh().orElseThrow();
        QuoteSnapshot second = service.refresh().orElseThrow();

        assertThat(kisMarketDataClient.closeCalls).isEqualTo(2);
        assertThat(kisMarketDataClient.currentCalls).isEqualTo(4);
        assertThat(first.stocks().getFirst().regularClose()).isEqualTo(71500.0);
        assertThat(second.stocks().getFirst().regularClose()).isEqualTo(71500.0);
        assertThat(second.stocks().getFirst().regularCloseDate()).isEqualTo("2026-06-19");
    }

    @Test
    void refreshesClosingPriceWhenNewClosedTradingDayExists() {
        StubMarketDataClient marketDataClient = new StubMarketDataClient();
        StubKisMarketDataClient kisMarketDataClient = new StubKisMarketDataClient(true);
        StubExchangeRateClient exchangeRateClient = new StubExchangeRateClient();
        MutableClock clock = new MutableClock(Instant.parse("2026-06-22T01:00:00Z"));
        QuoteService service = newService(marketDataClient, kisMarketDataClient, exchangeRateClient, clock);

        service.refresh();
        kisMarketDataClient.closeByCode = code -> new KisClosingPrice(
                code,
                new BigDecimal("73000"),
                "2026-06-22",
                new BigDecimal("74100"),
                new BigDecimal("73500"),
                "2026-06-22"
        );
        clock.advance(Duration.ofHours(6));
        QuoteSnapshot snapshot = service.refresh().orElseThrow();

        assertThat(kisMarketDataClient.closeCalls).isEqualTo(4);
        assertThat(snapshot.stocks().getFirst().regularClose()).isEqualTo(73000.0);
        assertThat(snapshot.stocks().getFirst().regularCloseDate()).isEqualTo("2026-06-22");
    }

    @Test
    void keepsCachedClosingPriceOnFailureAndRetriesAfterBackoff() {
        StubMarketDataClient marketDataClient = new StubMarketDataClient();
        StubKisMarketDataClient kisMarketDataClient = new StubKisMarketDataClient(true);
        StubExchangeRateClient exchangeRateClient = new StubExchangeRateClient();
        MutableClock clock = new MutableClock(Instant.parse("2026-06-22T01:00:00Z"));
        QuoteService service = newService(marketDataClient, kisMarketDataClient, exchangeRateClient, clock);

        service.refresh();
        clock.advance(Duration.ofHours(6));
        kisMarketDataClient.failCloseCode = "005930";
        kisMarketDataClient.closeByCode = code -> new KisClosingPrice(
                code,
                new BigDecimal("73000"),
                "2026-06-22",
                new BigDecimal("74100"),
                new BigDecimal("73500"),
                "2026-06-22"
        );
        QuoteSnapshot failed = service.refresh().orElseThrow();
        int callsAfterFailure = kisMarketDataClient.closeCalls;
        QuoteSnapshot backoff = service.refresh().orElseThrow();
        int callsAfterBackoff = kisMarketDataClient.closeCalls;
        clock.advance(Duration.ofSeconds(31));
        kisMarketDataClient.failCloseCode = null;
        QuoteSnapshot retried = service.refresh().orElseThrow();

        assertThat(failed.stocks().getFirst().regularClose()).isEqualTo(71500.0);
        assertThat(backoff.stocks().getFirst().regularClose()).isEqualTo(71500.0);
        assertThat(retried.stocks().getFirst().regularClose()).isEqualTo(73000.0);
        assertThat(callsAfterBackoff).isEqualTo(callsAfterFailure);
        assertThat(kisMarketDataClient.closeCalls).isEqualTo(callsAfterBackoff + 1);
    }

    @Test
    void estimateModeDoesNotFetchKisCurrentPrice() {
        StubMarketDataClient marketDataClient = new StubMarketDataClient();
        StubKisMarketDataClient kisMarketDataClient = new StubKisMarketDataClient(true);
        StubExchangeRateClient exchangeRateClient = new StubExchangeRateClient();
        QuoteService service = newService(
                marketDataClient,
                kisMarketDataClient,
                exchangeRateClient,
                new MutableClock(Instant.parse("2026-06-22T13:00:00Z"))
        );

        QuoteSnapshot snapshot = service.refresh().orElseThrow();

        assertThat(kisMarketDataClient.currentCalls).isZero();
        assertThat(kisMarketDataClient.closeCalls).isEqualTo(2);
        assertThat(snapshot.stocks().getFirst().priceKrw()).isEqualTo(356174.624);
        assertThat(snapshot.stocks().getFirst().regularCloseDate()).isEqualTo("2026-06-19");
    }

    @Test
    void regularModeFetchesKisCurrentPriceFromKrxMarket() {
        StubMarketDataClient marketDataClient = new StubMarketDataClient();
        StubKisMarketDataClient kisMarketDataClient = new StubKisMarketDataClient(true);
        StubExchangeRateClient exchangeRateClient = new StubExchangeRateClient();
        QuoteService service = newService(
                marketDataClient,
                kisMarketDataClient,
                exchangeRateClient,
                new MutableClock(Instant.parse("2026-06-22T01:00:00Z"))
        );

        service.refresh();

        assertThat(kisMarketDataClient.currentMarketDivisionCodes).containsExactly("J", "J");
    }

    @Test
    void premarketAndNxtFetchKisCurrentPriceFromNxtMarket() {
        StubMarketDataClient premarketDataClient = new StubMarketDataClient();
        StubKisMarketDataClient premarketKisClient = new StubKisMarketDataClient(true);
        StubExchangeRateClient premarketExchangeRateClient = new StubExchangeRateClient();
        QuoteService premarketService = newService(
                premarketDataClient,
                premarketKisClient,
                premarketExchangeRateClient,
                new MutableClock(Instant.parse("2026-06-21T23:30:00Z"))
        );

        premarketService.refresh();

        StubMarketDataClient nxtMarketDataClient = new StubMarketDataClient();
        StubKisMarketDataClient nxtKisClient = new StubKisMarketDataClient(true);
        StubExchangeRateClient nxtExchangeRateClient = new StubExchangeRateClient();
        QuoteService nxtService = newService(
                nxtMarketDataClient,
                nxtKisClient,
                nxtExchangeRateClient,
                new MutableClock(Instant.parse("2026-06-22T07:00:00Z"))
        );

        nxtService.refresh();

        assertThat(premarketKisClient.currentMarketDivisionCodes).containsExactly("NX", "NX");
        assertThat(nxtKisClient.currentMarketDivisionCodes).containsExactly("NX", "NX");
    }

    @Test
    void premarketZeroKisCurrentPriceFallsBackToHyperliquidEstimate() {
        StubMarketDataClient marketDataClient = new StubMarketDataClient();
        StubKisMarketDataClient kisMarketDataClient = new StubKisMarketDataClient(true);
        kisMarketDataClient.zeroCurrentCode = "000660";
        StubExchangeRateClient exchangeRateClient = new StubExchangeRateClient();
        QuoteService service = newService(
                marketDataClient,
                kisMarketDataClient,
                exchangeRateClient,
                new MutableClock(Instant.parse("2026-06-21T23:30:00Z"))
        );

        QuoteSnapshot snapshot = service.refresh().orElseThrow();

        assertThat(kisMarketDataClient.currentMarketDivisionCodes).containsExactly("NX", "NX");
        assertThat(snapshot.stocks().get(1).priceKrw()).isEqualTo(2826521.36);
        assertThat(snapshot.stocks().get(1).priceUsd()).isEqualTo(1913.95);
    }

    @Test
    void freezesPriceDuringNoTradeBreak() {
        StubMarketDataClient marketDataClient = new StubMarketDataClient();
        StubKisMarketDataClient kisMarketDataClient = new StubKisMarketDataClient(true);
        StubExchangeRateClient exchangeRateClient = new StubExchangeRateClient();
        MutableClock clock = new MutableClock(Instant.parse("2026-06-22T01:00:00Z")); // 10:00 KST 정규장
        QuoteService service = newService(marketDataClient, kisMarketDataClient, exchangeRateClient, clock);

        QuoteSnapshot live = service.refresh().orElseThrow();
        int marketCallsBefore = marketDataClient.calls;
        int currentCallsBefore = kisMarketDataClient.currentCalls;

        clock.advance(Duration.ofMinutes(325)); // → 06:25:00Z = 15:25 KST (거래 공백)
        QuoteSnapshot frozen = service.refresh().orElseThrow();

        // 가격·종목은 마지막 값으로 고정, 시각만 갱신, 추가 외부 호출 없음
        assertThat(frozen.stocks()).isSameAs(live.stocks());
        assertThat(frozen.stocks().getFirst().priceKrw())
                .isEqualTo(live.stocks().getFirst().priceKrw());
        assertThat(frozen.at()).isEqualTo(clock.instant());
        assertThat(marketDataClient.calls).isEqualTo(marketCallsBefore);
        assertThat(kisMarketDataClient.currentCalls).isEqualTo(currentCallsBefore);
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
        return newService(marketDataClient, kisMarketDataClient, exchangeRateClient,
                Clock.fixed(Instant.parse("2026-06-22T01:00:00Z"), ZoneOffset.UTC), factory);
    }

    private QuoteService newService(
            StubMarketDataClient marketDataClient,
            StubKisMarketDataClient kisMarketDataClient,
            StubExchangeRateClient exchangeRateClient,
            Clock clock
    ) {
        QuoteProperties properties = properties();
        QuoteSnapshotFactory factory = new QuoteSnapshotFactory(
                properties,
                new MarketModeService(),
                clock
        );
        return newService(marketDataClient, kisMarketDataClient, exchangeRateClient, clock, factory);
    }

    private QuoteService newService(
            StubMarketDataClient marketDataClient,
            StubKisMarketDataClient kisMarketDataClient,
            StubExchangeRateClient exchangeRateClient,
            Clock clock,
            QuoteSnapshotFactory factory
    ) {
        QuoteProperties properties = properties();
        return new QuoteService(
                marketDataClient,
                kisMarketDataClient,
                exchangeRateClient,
                properties,
                factory,
                new QuoteBroadcaster(),
                new MarketModeService(),
                clock
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
        private int currentCalls;
        private int closeCalls;
        private final List<String> currentMarketDivisionCodes = new ArrayList<>();
        private String failCurrentCode;
        private String zeroCurrentCode;
        private String failCloseCode;
        private CloseFactory closeByCode = code -> {
            if ("000660".equals(code)) {
                return new KisClosingPrice(
                        code,
                        new BigDecimal("205000"),
                        "2026-06-19",
                        new BigDecimal("214000"),
                        new BigDecimal("212000"),
                        "2026-06-19"
                );
            }
            return new KisClosingPrice(
                    code,
                    new BigDecimal("71500"),
                    "2026-06-19",
                    new BigDecimal("74000"),
                    new BigDecimal("72500"),
                    "2026-06-19"
            );
        };

        private StubKisMarketDataClient(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public Optional<KisStockQuote> fetchCurrentStockQuote(String code, String marketDivisionCode) {
            currentCalls++;
            currentMarketDivisionCodes.add(marketDivisionCode);
            if (code.equals(failCurrentCode)) {
                throw new IllegalStateException("kis unavailable");
            }
            if (code.equals(zeroCurrentCode)) {
                return Optional.of(new KisStockQuote(
                        code,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            }
            if ("000660".equals(code)) {
                return Optional.of(new KisStockQuote(
                        code,
                        new BigDecimal("210000"),
                        new BigDecimal("2.40"),
                        new BigDecimal("205000"),
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            }
            return Optional.empty();
        }

        @Override
        public Optional<KisClosingPrice> fetchClosingPrice(String code) {
            closeCalls++;
            if (code.equals(failCloseCode)) {
                throw new IllegalStateException("kis close unavailable");
            }
            return Optional.of(closeByCode.create(code));
        }
    }

    private interface CloseFactory {
        KisClosingPrice create(String code);
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
