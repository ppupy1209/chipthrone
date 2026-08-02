package dev.yeonwoo.chipthrone.quote.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import dev.yeonwoo.chipthrone.quote.config.DemandProperties;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

class SubscriptionRegistryTest {

    @Test
    void sharesSymbolsAndRemovesThemAfterGraceWithoutNegativeCounts() {
        MutableClock clock = new MutableClock();
        SubscriptionRegistry registry = registry(clock);

        SubscriptionRegistry.Subscription first = registry.subscribe(Set.of("005930", "000660"));
        SubscriptionRegistry.Subscription second = registry.subscribe(Set.of("005930"));

        assertThat(registry.activeSymbols()).containsExactlyInAnyOrder("005930", "000660");
        assertThat(registry.subscriberCount("005930")).isEqualTo(2);
        assertThat(registry.subscriberCount("000660")).isEqualTo(1);

        first.close();
        first.close();
        second.close();
        second.close();

        assertThat(registry.subscriberCount("005930")).isZero();
        assertThat(registry.subscriberCount("000660")).isZero();
        assertThat(registry.activeSymbols()).hasSize(2);

        clock.advance(Duration.ofSeconds(14));
        SubscriptionRegistry.Subscription reconnect = registry.subscribe(Set.of("005930"));
        clock.advance(Duration.ofSeconds(2));
        assertThat(registry.activeSymbols()).containsExactly("005930");
        assertThat(registry.subscriberCount("005930")).isOne();

        reconnect.close();
        clock.advance(Duration.ofSeconds(15));
        assertThat(registry.activeSymbols()).isEmpty();
    }

    @Test
    void concurrentSubscribeAndDuplicateCloseLeavesNoPermanentSymbols() {
        MutableClock clock = new MutableClock();
        SubscriptionRegistry registry = registry(clock);

        List<SubscriptionRegistry.Subscription> handles = IntStream.range(0, 500)
                .parallel()
                .mapToObj(index -> registry.subscribe(Set.of(index % 2 == 0 ? "005930" : "000660")))
                .toList();
        assertThat(registry.totalSubscribers()).isEqualTo(500);

        handles.parallelStream().forEach(handle -> {
            handle.close();
            handle.close();
        });
        assertThat(registry.totalSubscribers()).isZero();

        clock.advance(Duration.ofSeconds(15));
        assertThat(registry.activeSymbols()).isEmpty();
    }

    private SubscriptionRegistry registry(Clock clock) {
        QuoteProperties quoteProperties = new QuoteProperties(
                3000,
                false,
                "xyz",
                1450,
                List.of(
                        new QuoteProperties.Asset("005930", "삼성전자", "xyz:SMSN", 5_919_637_922L, QuoteProperties.Market.KRX),
                        new QuoteProperties.Asset("000660", "SK하이닉스", "xyz:SKHX", 728_002_365L, QuoteProperties.Market.KRX)
                )
        );
        DemandProperties demandProperties = new DemandProperties(true, 3000, 60000, 1000, 15000, 5000, 4);
        return new SubscriptionRegistry(
                new AssetCatalog(quoteProperties),
                demandProperties,
                clock,
                new SimpleMeterRegistry()
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-06-22T01:00:00Z");

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
