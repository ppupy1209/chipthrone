package dev.yeonwoo.chipthrone.quote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
import dev.yeonwoo.chipthrone.alert.AlertService;
import dev.yeonwoo.chipthrone.alert.SlackNotifier;
import dev.yeonwoo.chipthrone.quote.client.ExchangeRateClient;
import dev.yeonwoo.chipthrone.quote.client.MarketDataClient;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote;
import dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice;
import dev.yeonwoo.chipthrone.quote.model.QuoteSnapshot;
import dev.yeonwoo.chipthrone.quote.web.QuoteBroadcaster;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class QuoteServiceTest {

    @Test
    void sharesOneHyperliquidBatchAndCachesExchangeRate() {
        StubMarketDataClient market = new StubMarketDataClient();
        StubExchangeRateClient fx = new StubExchangeRateClient(true);
        QuoteService service = newService(market, fx);

        QuoteSnapshot first = service.refresh(Set.of("005930", "000660")).orElseThrow();
        QuoteSnapshot second = service.refresh(Set.of("005930", "000660")).orElseThrow();

        assertThat(market.calls).isEqualTo(2);
        assertThat(fx.calls).isOne();
        assertThat(first.stocks()).allMatch(stock -> "HYPERLIQUID".equals(stock.source()));
        assertThat(second.stocks()).hasSize(2);
    }

    @Test
    void refetchesExchangeRateOncePerRefreshInterval() {
        StubMarketDataClient market = new StubMarketDataClient();
        MutableClock clock = new MutableClock(Instant.parse("2026-06-22T01:00:00Z"));
        StubExchangeRateClient fx = new StubExchangeRateClient(true, clock);
        QuoteService service = newService(market, fx, clock);

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
        QuoteService service = newService(new StubMarketDataClient(), fx, clock);

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
    void usOnlySubscriptionReturnsOnlyRequestedSymbol() {
        StubMarketDataClient market = new StubMarketDataClient();
        StubExchangeRateClient fx = new StubExchangeRateClient(true);
        QuoteService service = newService(market, fx);

        QuoteSnapshot snapshot = service.refresh(Set.of("NVDA")).orElseThrow();

        assertThat(market.calls).isOne();
        assertThat(snapshot.stocks()).extracting("code").containsExactly("NVDA");
    }

    @Test
    void disabledExchangeRateSourceDoesNotPublishAConfiguredEstimate() {
        QuoteService service = newService(new StubMarketDataClient(), new StubExchangeRateClient(false));

        Optional<QuoteSnapshot> snapshot = service.refresh(Set.of("005930"));

        assertThat(snapshot).isEmpty();
    }

    @Test
    void keepsLastSnapshotWhenHyperliquidFails() {
        StubMarketDataClient market = new StubMarketDataClient();
        QuoteService service = newService(market, new StubExchangeRateClient(true));
        QuoteSnapshot first = service.refresh(Set.of("005930")).orElseThrow();
        market.fail = true;

        Optional<QuoteSnapshot> fallback = service.refresh(Set.of("005930"));

        assertThat(fallback).contains(first);
    }

    private QuoteService newService(MarketDataClient market, ExchangeRateClient fx) {
        return newService(
                market,
                fx,
                Clock.fixed(Instant.parse("2026-06-22T01:00:00Z"), ZoneOffset.UTC)
        );
    }

    private QuoteService newService(MarketDataClient market, ExchangeRateClient fx, Clock clock) {
        QuoteProperties properties = properties();
        AlertProperties alertProperties = new AlertProperties("", 5, 10);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        return new QuoteService(
                market,
                fx,
                new UsSessionCloseService(market, clock),
                properties,
                new AssetCatalog(properties),
                new QuoteSnapshotFactory(properties, clock),
                mock(QuoteBroadcaster.class),
                new AlertService(
                        alertProperties,
                        new SlackNotifier(RestClient.builder().build(), alertProperties),
                        clock
                ),
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

        private Instant fetchedAt() {
            return clock == null ? null : clock.instant();
        }
    }
}
