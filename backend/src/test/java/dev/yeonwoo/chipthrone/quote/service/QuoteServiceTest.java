package dev.yeonwoo.chipthrone.quote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.yeonwoo.chipthrone.alert.AlertProperties;
import dev.yeonwoo.chipthrone.alert.AlertEvent;
import dev.yeonwoo.chipthrone.alert.AlertService;
import dev.yeonwoo.chipthrone.alert.SlackNotifier;
import dev.yeonwoo.chipthrone.quote.client.ExchangeRateClient;
import dev.yeonwoo.chipthrone.quote.client.MarketDataClient;
import dev.yeonwoo.chipthrone.quote.client.OfficialStockPriceClient;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote;
import dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice;
import dev.yeonwoo.chipthrone.quote.model.OfficialStockPrice;
import dev.yeonwoo.chipthrone.quote.model.QuoteSnapshot;
import dev.yeonwoo.chipthrone.quote.web.QuoteBroadcaster;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class QuoteServiceTest {

    @Test
    void sharesOneHyperliquidBatchAndCachesDailyOfficialSources() {
        StubMarketDataClient market = new StubMarketDataClient();
        StubOfficialClient official = new StubOfficialClient(true);
        StubExchangeRateClient fx = new StubExchangeRateClient(true);
        QuoteService service = newService(market, official, fx);

        QuoteSnapshot first = service.refresh(Set.of("005930", "000660")).orElseThrow();
        QuoteSnapshot second = service.refresh(Set.of("005930", "000660")).orElseThrow();

        assertThat(market.calls).isEqualTo(2);
        assertThat(official.calls).isEqualTo(2);
        assertThat(fx.calls).isOne();
        assertThat(first.stocks()).allMatch(stock -> "HYPERLIQUID".equals(stock.source()));
        assertThat(first.stocks()).allMatch(stock -> stock.officialMarketCap() != null);
        assertThat(second.stocks()).hasSize(2);
    }

    @Test
    void refreshesOfficialDailyPriceOnceMoreAfterItsPublicationWindow() {
        StubMarketDataClient market = new StubMarketDataClient();
        StubOfficialClient official = new StubOfficialClient(true);
        StubExchangeRateClient fx = new StubExchangeRateClient(true);
        MutableClock clock = new MutableClock(Instant.parse("2026-06-22T01:00:00Z"));
        QuoteService service = newService(market, official, fx, clock);

        service.refresh(Set.of("005930", "000660"));
        service.refresh(Set.of("005930", "000660"));
        clock.advance(Duration.ofHours(4));
        service.refresh(Set.of("005930", "000660"));
        service.refresh(Set.of("005930", "000660"));

        assertThat(official.calls).isEqualTo(4);
    }

    @Test
    void refetchesExchangeRateOncePerRefreshInterval() {
        StubMarketDataClient market = new StubMarketDataClient();
        StubOfficialClient official = new StubOfficialClient(false);
        MutableClock clock = new MutableClock(Instant.parse("2026-06-22T01:00:00Z"));
        StubExchangeRateClient fx = new StubExchangeRateClient(true, clock);
        QuoteService service = newService(market, official, fx, clock);

        service.refresh(Set.of("005930"));
        clock.advance(Duration.ofMinutes(4));
        service.refresh(Set.of("005930"));
        assertThat(fx.calls).isOne();

        clock.advance(Duration.ofMinutes(2));
        service.refresh(Set.of("005930")).orElseThrow();

        assertThat(fx.calls).isEqualTo(2);
    }

    @Test
    void stopsRefreshingWithAnExchangeRateOlderThanThirtyMinutes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-22T01:00:00Z"));
        StubExchangeRateClient fx = new StubExchangeRateClient(true, clock);
        QuoteService service = newService(new StubMarketDataClient(), new StubOfficialClient(false), fx, clock);

        QuoteSnapshot first = service.refresh(Set.of("005930")).orElseThrow();
        clock.advance(Duration.ofMinutes(6));
        fx.fail = true;
        QuoteSnapshot recentFallback = service.refresh(Set.of("005930")).orElseThrow();
        assertThat(recentFallback.at()).isAfter(first.at());

        clock.advance(Duration.ofMinutes(25));
        QuoteSnapshot staleFallback = service.refresh(Set.of("005930")).orElseThrow();

        assertThat(staleFallback.at()).isEqualTo(recentFallback.at());
        assertThat(fx.calls).isEqualTo(3);
    }

    @Test
    void usOnlySubscriptionDoesNotCallOfficialKrxSource() {
        StubMarketDataClient market = new StubMarketDataClient();
        StubOfficialClient official = new StubOfficialClient(true);
        StubExchangeRateClient fx = new StubExchangeRateClient(true);
        QuoteService service = newService(market, official, fx);

        QuoteSnapshot snapshot = service.refresh(Set.of("NVDA")).orElseThrow();

        assertThat(market.calls).isOne();
        assertThat(official.calls).isZero();
        assertThat(snapshot.stocks()).extracting("code").containsExactly("NVDA");
    }

    @Test
    void disabledExchangeRateSourceDoesNotPublishAConfiguredEstimate() {
        QuoteService service = newService(
                new StubMarketDataClient(), new StubOfficialClient(false), new StubExchangeRateClient(false));

        Optional<QuoteSnapshot> snapshot = service.refresh(Set.of("005930"));

        assertThat(snapshot).isEmpty();
    }

    @Test
    void keepsLastSnapshotWhenHyperliquidFails() {
        StubMarketDataClient market = new StubMarketDataClient();
        QuoteService service = newService(market, new StubOfficialClient(false), new StubExchangeRateClient(true));
        QuoteSnapshot first = service.refresh(Set.of("005930")).orElseThrow();
        market.fail = true;

        Optional<QuoteSnapshot> fallback = service.refresh(Set.of("005930"));

        assertThat(fallback).contains(first);
    }

    @Test
    void reportsOfficialCloseFailureAndRecovery() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-22T01:00:00Z"));
        StubOfficialClient official = new StubOfficialClient(true);
        official.empty = true;
        AlertService alerts = mock(AlertService.class);
        QuoteService service = newService(
                new StubMarketDataClient(), official, new StubExchangeRateClient(true), clock, alerts);

        service.refresh(Set.of("005930", "000660"));
        verify(alerts).recordFailure(AlertEvent.OFFICIAL_CLOSE);

        clock.advance(Duration.ofMinutes(10));
        official.empty = false;
        service.refresh(Set.of("005930", "000660"));
        verify(alerts).recordSuccess(AlertEvent.OFFICIAL_CLOSE);
    }

    private QuoteService newService(
            MarketDataClient market,
            OfficialStockPriceClient official,
            ExchangeRateClient fx
    ) {
        return newService(
                market,
                official,
                fx,
                Clock.fixed(Instant.parse("2026-06-22T01:00:00Z"), ZoneOffset.UTC)
        );
    }

    private QuoteService newService(
            MarketDataClient market,
            OfficialStockPriceClient official,
            ExchangeRateClient fx,
            Clock clock
    ) {
        AlertProperties alertProperties = new AlertProperties("", 5, 10);
        return newService(
                market,
                official,
                fx,
                clock,
                new AlertService(
                        alertProperties,
                        new SlackNotifier(RestClient.builder().build(), alertProperties),
                        clock
                )
        );
    }

    private QuoteService newService(
            MarketDataClient market,
            OfficialStockPriceClient official,
            ExchangeRateClient fx,
            Clock clock,
            AlertService alertService
    ) {
        QuoteProperties properties = properties();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        return new QuoteService(
                market,
                official,
                fx,
                new EstimateAccuracyService(market, fx, clock),
                new UsSessionCloseService(market, clock),
                properties,
                new AssetCatalog(properties),
                new QuoteSnapshotFactory(properties, clock),
                mock(QuoteBroadcaster.class),
                alertService,
                new QuoteMetrics(registry),
                clock
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant) {
            this(instant, ZoneOffset.UTC);
        }

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private QuoteProperties properties() {
        return new QuoteProperties("xyz", List.of(
                new QuoteProperties.Asset(
                        "005930", "삼성전자", "xyz:SMSN", 5_919_637_922L, QuoteProperties.Market.KRX),
                new QuoteProperties.Asset(
                        "000660", "SK하이닉스", "xyz:SKHX", 728_002_365L, QuoteProperties.Market.KRX),
                new QuoteProperties.Asset(
                        "NVDA", "엔비디아", "xyz:NVDA", 24_200_000_000L, QuoteProperties.Market.US)
        ));
    }

    private static final class StubMarketDataClient implements MarketDataClient {
        private int calls;
        private boolean fail;

        @Override
        public List<MarketAssetPrice> fetchAssetPrices(String dex) {
            calls++;
            if (fail) throw new IllegalStateException("market failure");
            return List.of(
                    new MarketAssetPrice("xyz:SMSN", new BigDecimal("240"), new BigDecimal("235")),
                    new MarketAssetPrice("xyz:SKHX", new BigDecimal("1900"), new BigDecimal("1850")),
                    new MarketAssetPrice("xyz:NVDA", new BigDecimal("180"), new BigDecimal("175"))
            );
        }

        @Override
        public Optional<BigDecimal> fetchCloseAt(String symbol, Instant at) {
            return Optional.of(new BigDecimal("48"));
        }
    }

    private static final class StubOfficialClient implements OfficialStockPriceClient {
        private final boolean enabled;
        private int calls;
        private boolean empty;

        private StubOfficialClient(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public Optional<OfficialStockPrice> fetchLatest(String code) {
            calls++;
            if (empty) {
                return Optional.empty();
            }
            long shares = code.equals("005930") ? 5_919_637_922L : 728_002_365L;
            BigDecimal close = code.equals("005930") ? new BigDecimal("71000") : new BigDecimal("205000");
            return Optional.of(new OfficialStockPrice(
                    code, close, "2026-06-19", close.add(BigDecimal.valueOf(1000)), shares,
                    close.multiply(BigDecimal.valueOf(shares))));
        }
    }

    private static final class StubExchangeRateClient implements ExchangeRateClient {
        private final boolean enabled;
        private final Clock clock;
        private int calls;
        private boolean fail;

        private StubExchangeRateClient(boolean enabled) {
            this(enabled, null);
        }

        private StubExchangeRateClient(boolean enabled, Clock clock) {
            this.enabled = enabled;
            this.clock = clock;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public ExchangeRateQuote fetchUsdKrw() {
            calls++;
            if (fail) {
                throw new IllegalStateException("exchange failure");
            }
            return new ExchangeRateQuote(new BigDecimal("1476.8"), "2026-06-19", "UPBIT_USDC", fetchedAt());
        }

        @Override
        public ExchangeRateQuote fetchUsdKrw(Instant at) {
            return new ExchangeRateQuote(new BigDecimal("1476.8"), "2026-06-19", "UPBIT_USDC", fetchedAt());
        }

        private Instant fetchedAt() {
            return clock == null ? null : clock.instant();
        }
    }
}
