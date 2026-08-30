package dev.yeonwoo.chipthrone.quote.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import dev.yeonwoo.chipthrone.quote.config.DemandProperties;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

class QuotePollingWorkerTest {

    @Test
    void skipsExternalCollectionWithoutSubscribersAndPollsUniqueActiveSymbols() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-22T01:00:00Z"));
        QuoteService service = mock(QuoteService.class);
        Fixture fixture = fixture(service, clock, true);

        fixture.worker.poll();
        verify(service, never()).refresh(Set.of("005930"));

        SubscriptionRegistry.Subscription first = fixture.registry.subscribe(Set.of("005930"));
        SubscriptionRegistry.Subscription second = fixture.registry.subscribe(Set.of("005930"));
        fixture.worker.poll();
        verify(service).refresh(Set.of("005930"));

        first.close();
        second.close();
        clock.advance(Duration.ofSeconds(16));
        fixture.worker.poll();
        verify(service).refresh(Set.of("005930"));
    }

    @Test
    void fixedModeStillPollsEverySupportedSymbolWithoutSubscribers() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-22T01:00:00Z"));
        QuoteService service = mock(QuoteService.class);
        Fixture fixture = fixture(service, clock, false);

        fixture.worker.poll();

        verify(service).refresh(Set.of("005930", "000660"));
    }

    @Test
    void estimatesKeepThreeSecondIntervalAroundTheClock() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-22T13:00:00Z"));
        QuoteService service = mock(QuoteService.class);
        Fixture fixture = fixture(service, clock, true);
        fixture.registry.subscribe(Set.of("005930"));

        fixture.worker.poll();
        clock.advance(Duration.ofSeconds(2));
        fixture.worker.poll();
        verify(service).refresh(Set.of("005930"));

        clock.advance(Duration.ofSeconds(1));
        fixture.worker.poll();
        verify(service, org.mockito.Mockito.times(2)).refresh(Set.of("005930"));
    }

    @Test
    void coalescesImmediateRefreshRequestsIntoOneCollection() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-22T01:00:00Z"));
        QuoteService service = mock(QuoteService.class);
        Fixture fixture = fixture(service, clock, true);
        fixture.registry.subscribe(Set.of("005930", "000660"));

        fixture.worker.requestImmediateRefresh();
        fixture.worker.requestImmediateRefresh();
        fixture.worker.requestImmediateRefresh();
        fixture.worker.poll();

        verify(service).refresh(Set.of("005930", "000660"));
    }

    private Fixture fixture(QuoteService service, Clock clock, boolean enabled) {
        QuoteProperties quoteProperties = new QuoteProperties(
                "xyz",
                List.of(
                        new QuoteProperties.Asset("005930", "삼성전자", "xyz:SMSN", 5_919_637_922L, QuoteProperties.Market.KRX),
                        new QuoteProperties.Asset("000660", "SK하이닉스", "xyz:SKHX", 728_002_365L, QuoteProperties.Market.KRX)
                )
        );
        DemandProperties demand = new DemandProperties(enabled, 3000, 1000, 15000, 5000, 4);
        AssetCatalog catalog = new AssetCatalog(quoteProperties);
        SubscriptionRegistry registry = new SubscriptionRegistry(catalog, demand, clock, new SimpleMeterRegistry());
        QuotePollingWorker worker = new QuotePollingWorker(
                service,
                registry,
                catalog,
                demand,
                clock,
                new SimpleMeterRegistry()
        );
        return new Fixture(worker, registry);
    }

    private record Fixture(QuotePollingWorker worker, SubscriptionRegistry registry) {
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
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
