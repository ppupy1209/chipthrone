package dev.yeonwoo.chipthrone.quote.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import dev.yeonwoo.chipthrone.quote.config.DemandProperties;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.MarketMode;
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
    private final MarketModeService marketModeService;
    private final UsMarketHours usMarketHours;
    private final DemandProperties properties;
    private final Clock clock;
    private final AtomicBoolean immediateRefreshRequested = new AtomicBoolean();
    private final AtomicInteger collectionSymbolCount = new AtomicInteger();
    private Instant lastPollAt;

    public QuotePollingWorker(
            QuoteService quoteService,
            SubscriptionRegistry subscriptions,
            AssetCatalog catalog,
            MarketModeService marketModeService,
            UsMarketHours usMarketHours,
            DemandProperties properties,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.quoteService = quoteService;
        this.subscriptions = subscriptions;
        this.catalog = catalog;
        this.marketModeService = marketModeService;
        this.usMarketHours = usMarketHours;
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
        long delayMs = pollDelayMs(now, symbols);
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

    private long pollDelayMs(Instant now, Set<String> symbols) {
        List<QuoteProperties.Asset> assets = catalog.requireAssets(symbols);
        boolean usOpen = assets.stream()
                .anyMatch(asset -> asset.market() == QuoteProperties.Market.US && usMarketHours.isOpen(now));
        MarketMode mode = marketModeService.determine(now);
        boolean krxOpen = assets.stream()
                .anyMatch(asset -> asset.market() == QuoteProperties.Market.KRX)
                && mode != MarketMode.ESTIMATE
                && !marketModeService.isNoTradeBreak(now);
        if (!usOpen && !krxOpen) {
            return properties.closedPollDelayMs();
        }
        return properties.livePollDelayMs();
    }
}
