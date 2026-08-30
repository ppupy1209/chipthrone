package dev.yeonwoo.chipthrone.quote.web;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.yeonwoo.chipthrone.quote.model.QuoteSnapshot;
import dev.yeonwoo.chipthrone.quote.service.QuoteMetrics;
import dev.yeonwoo.chipthrone.quote.service.SubscriptionRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class QuoteBroadcaster {

    private final List<Connection> connections = new CopyOnWriteArrayList<>();
    private final SubscriptionRegistry subscriptions;
    private final QuoteMetrics metrics;
    private final Clock clock;
    private final long emitterTimeoutMs;

    public QuoteBroadcaster(
            SubscriptionRegistry subscriptions,
            QuoteMetrics metrics,
            Clock clock,
            MeterRegistry meterRegistry,
            @Value("${chipthrone.sse.emitter-timeout-ms:300000}") long emitterTimeoutMs
    ) {
        this.subscriptions = subscriptions;
        this.metrics = metrics;
        this.clock = clock;
        this.emitterTimeoutMs = emitterTimeoutMs;
        Gauge.builder("chipthrone.sse.connections", connections, List::size)
                .description("Current SSE connections")
                .register(meterRegistry);
        Gauge.builder("chipthrone.sse.orphan.emitters", this, QuoteBroadcaster::orphanEmitterCount)
                .description("Closed SSE emitters still retained by the broadcaster")
                .register(meterRegistry);
    }

    public SseEmitter subscribe(Set<String> symbols, QuoteSnapshot initialSnapshot) {
        SseEmitter emitter = new SseEmitter(emitterTimeoutMs);
        Connection connection = new Connection(emitter, symbols, subscriptions.subscribe(symbols));
        connections.add(connection);
        emitter.onCompletion(() -> cleanup(connection));
        emitter.onTimeout(() -> cleanup(connection));
        emitter.onError(error -> cleanup(connection));

        if (initialSnapshot != null) {
            send(connection, initialSnapshot);
        }
        return emitter;
    }

    public void publish(QuoteSnapshot snapshot) {
        for (Connection connection : connections) {
            send(connection, snapshot);
        }
    }

    @Scheduled(fixedDelayString = "${chipthrone.sse.heartbeat-ms:10000}")
    public void heartbeat() {
        for (Connection connection : connections) {
            try {
                connection.emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException ex) {
                cleanup(connection);
            }
        }
    }

    public int connectionCount() {
        return connections.size();
    }

    public int orphanEmitterCount() {
        return (int) connections.stream().filter(connection -> connection.closed.get()).count();
    }

    private void send(Connection connection, QuoteSnapshot snapshot) {
        List<dev.yeonwoo.chipthrone.quote.model.StockQuote> stocks = snapshot.stocks().stream()
                .filter(stock -> connection.symbols.contains(stock.code()))
                .toList();
        if (stocks.isEmpty()) {
            return;
        }
        QuoteSnapshot filtered = new QuoteSnapshot(snapshot.mode(), snapshot.at(), stocks);
        try {
            connection.emitter.send(SseEmitter.event().name("quotes").data(filtered));
            metrics.recordDelivery(snapshot.at(), clock.instant());
        } catch (IOException | IllegalStateException ex) {
            cleanup(connection);
        }
    }

    private void cleanup(Connection connection) {
        if (connection.closed.compareAndSet(false, true)) {
            connection.subscription.close();
            connections.remove(connection);
        }
    }

    private static final class Connection {
        private final SseEmitter emitter;
        private final Set<String> symbols;
        private final SubscriptionRegistry.Subscription subscription;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Connection(
                SseEmitter emitter,
                Set<String> symbols,
                SubscriptionRegistry.Subscription subscription
        ) {
            this.emitter = emitter;
            this.symbols = Set.copyOf(symbols);
            this.subscription = subscription;
        }
    }
}
