package dev.yeonwoo.chipthrone.quote.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.yeonwoo.chipthrone.quote.config.DemandProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

@Component
public class SubscriptionRegistry {

    private final Map<String, State> states = new HashMap<>();
    private final Clock clock;
    private final long graceMs;

    public SubscriptionRegistry(
            AssetCatalog catalog,
            DemandProperties properties,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.clock = clock;
        this.graceMs = properties.subscriptionGraceMs();
        Gauge.builder("chipthrone.quote.subscribed.active.symbols", this, SubscriptionRegistry::activeSymbolCount)
                .description("Number of subscribed symbols, including disconnect grace")
                .register(meterRegistry);
        catalog.allCodes().forEach(code -> Gauge.builder(
                        "chipthrone.quote.symbol.subscribers",
                        this,
                        registry -> registry.subscriberCount(code))
                .tag("symbol", code)
                .description("SSE subscribers for a supported symbol")
                .register(meterRegistry));
    }

    public synchronized Subscription subscribe(Set<String> symbols) {
        Set<String> copied = Set.copyOf(symbols);
        copied.forEach(symbol -> {
            State state = states.computeIfAbsent(symbol, ignored -> new State());
            state.subscribers++;
            state.removeAfter = null;
        });
        return new Subscription(this, copied);
    }

    public synchronized Set<String> activeSymbols() {
        reapExpired();
        return new LinkedHashSet<>(states.keySet());
    }

    public synchronized int subscriberCount(String symbol) {
        State state = states.get(symbol);
        return state == null ? 0 : state.subscribers;
    }

    public synchronized int totalSubscribers() {
        return states.values().stream().mapToInt(state -> state.subscribers).sum();
    }

    public synchronized int activeSymbolCount() {
        reapExpired();
        return states.size();
    }

    private synchronized void unsubscribe(Set<String> symbols) {
        Instant now = clock.instant();
        symbols.forEach(symbol -> {
            State state = states.get(symbol);
            if (state == null || state.subscribers == 0) {
                return;
            }
            state.subscribers--;
            if (state.subscribers == 0) {
                state.removeAfter = now.plusMillis(graceMs);
            }
        });
        reapExpired();
    }

    private void reapExpired() {
        Instant now = clock.instant();
        states.entrySet().removeIf(entry -> {
            State state = entry.getValue();
            return state.subscribers == 0
                    && state.removeAfter != null
                    && !now.isBefore(state.removeAfter);
        });
    }

    private static final class State {
        private int subscribers;
        private Instant removeAfter;
    }

    public static final class Subscription implements AutoCloseable {
        private final SubscriptionRegistry registry;
        private final Set<String> symbols;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Subscription(SubscriptionRegistry registry, Set<String> symbols) {
            this.registry = registry;
            this.symbols = symbols;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                registry.unsubscribe(symbols);
            }
        }
    }
}
