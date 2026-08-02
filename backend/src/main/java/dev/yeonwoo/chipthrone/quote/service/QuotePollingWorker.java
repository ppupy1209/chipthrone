package dev.yeonwoo.chipthrone.quote.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import dev.yeonwoo.chipthrone.quote.config.DemandProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "chipthrone.quote", name = "polling-enabled", havingValue = "true", matchIfMissing = true)
public class QuotePollingWorker {

    private final QuoteService quoteService;
    private final SubscriptionRegistry subscriptions;
    private final AssetCatalog catalog;
    private final DemandProperties properties;
    private final Clock clock;
    private final AtomicBoolean immediateRefreshRequested = new AtomicBoolean();
    private final AtomicInteger collectionSymbolCount = new AtomicInteger();
    private Instant lastPollAt;

    public QuotePollingWorker(
            QuoteService quoteService,
            SubscriptionRegistry subscriptions,
            AssetCatalog catalog,
            DemandProperties properties,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.quoteService = quoteService;
        this.subscriptions = subscriptions;
        this.catalog = catalog;
        this.properties = properties;
        this.clock = clock;
        Gauge.builder("chipthrone.quote.active.symbols", collectionSymbolCount, AtomicInteger::get)
                .description("Number of unique symbols in the current collection set")
                .register(meterRegistry);
    }

    public void requestImmediateRefresh() {
        immediateRefreshRequested.set(true);
    }

    @Scheduled(fixedDelayString = "${chipthrone.demand.scheduler-tick-ms:1000}")
    public void poll() {
        Set<String> symbols = properties.enabled() ? subscriptions.activeSymbols() : catalog.allCodes();
        if (symbols.isEmpty()) {
            lastPollAt = null;
            collectionSymbolCount.set(0);
            immediateRefreshRequested.set(false);
            return;
        }
        collectionSymbolCount.set(symbols.size());

        Instant now = clock.instant();
        long delayMs = properties.livePollDelayMs();
        if (immediateRefreshRequested.getAndSet(false)) {
            lastPollAt = now;
            quoteService.refresh(symbols);
            return;
        }
        if (lastPollAt == null && quoteService.hasFresh(symbols, Duration.ofMillis(delayMs))) {
            lastPollAt = now;
            return;
        }
        if (lastPollAt != null && Duration.between(lastPollAt, now).toMillis() < delayMs) {
            return;
        }
        lastPollAt = now;
        quoteService.refresh(symbols);
    }

}
