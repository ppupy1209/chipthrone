package dev.yeonwoo.chipthrone.quote.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.yeonwoo.chipthrone.quote.model.MarketMode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

class QuoteMetricsTest {

    @Test
    void startsFreshnessAfterTheFirstSuccessfulPoll() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        QuoteMetrics metrics = new QuoteMetrics(registry);

        metrics.poll(MarketMode.ESTIMATE, false, 1);
        assertThat(registry.get("chipthrone.quote.freshness.age").gauge().value()).isNaN();

        metrics.poll(MarketMode.ESTIMATE, true, 1);
        assertThat(registry.get("chipthrone.quote.freshness.age").gauge().value())
                .isBetween(0.0, 1.0);
    }
}
