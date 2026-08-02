package dev.yeonwoo.chipthrone.quote.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import dev.yeonwoo.chipthrone.quote.model.MarketMode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.stereotype.Component;

@Component
public class QuoteMetrics {

    private final MeterRegistry registry;
    private final Timer collectionDuration;
    private final Timer deliveryLatency;

    public QuoteMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.collectionDuration = Timer.builder("chipthrone.quote.collection.duration")
                .description("Quote collection duration")
                .publishPercentileHistogram()
                .register(registry);
        this.deliveryLatency = Timer.builder("chipthrone.quote.delivery.latency")
                .description("Delay from snapshot creation to SSE send")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void externalCall(String source, String operation) {
        Counter.builder("chipthrone.quote.external.api.calls")
                .tag("source", source)
                .tag("operation", operation)
                .register(registry)
                .increment();
    }

    public void poll(MarketMode mode, boolean success, long durationNanos) {
        Counter.builder("chipthrone.quote.polls")
                .tag("mode", mode.name())
                .tag("result", success ? "success" : "failure")
                .register(registry)
                .increment();
        collectionDuration.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordDelivery(Instant snapshotAt, Instant deliveredAt) {
        Duration delay = Duration.between(snapshotAt, deliveredAt);
        deliveryLatency.record(delay.isNegative() ? Duration.ZERO : delay);
    }
}
