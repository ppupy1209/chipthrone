package dev.yeonwoo.chipthrone.quote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
        assertThat(second.fxSource()).isEqualTo("KOREA_EXIMBANK");
    }

    @Test
    void refreshesDailySourcesOnceMoreAfterTheirPublicationWindow() {
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
        assertThat(fx.calls).isEqualTo(2);
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
    void disabledOfficialSourcesUseConfiguredFxAndLeaveDailyPriceEmpty() {
        QuoteService service = newService(
                new StubMarketDataClient(), new StubOfficialClient(false), new StubExchangeRateClient(false));

        QuoteSnapshot snapshot = service.refresh(Set.of("005930")).orElseThrow();

        assertThat(snapshot.fxRate()).isEqualTo(1450.0);
        assertThat(snapshot.fxSource()).isEqualTo("CONFIG_FALLBACK");
        assertThat(snapshot.stocks().getFirst().regularClose()).isNull();
    }

    @Test
    void keepsLastSnapshotWhenHyperliquidFails() {
        StubMarketDataClient market = new StubMarketDataClient();
        QuoteService service = newService(market, new StubOfficialClient(false), new StubExchangeRateClient(false));
        QuoteSnapshot first = service.refresh(Set.of("005930")).orElseThrow();
        market.fail = true;

        Optional<QuoteSnapshot> fallback = service.refresh(Set.of("005930"));

        assertThat(fallback).contains(first);
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
        QuoteProperties properties = properties();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        return new QuoteService(
                market,
                official,
                fx,
                new EstimateAccuracyService(market, fx, clock),
                properties,
                new AssetCatalog(properties),
                new QuoteSnapshotFactory(properties, clock),
                mock(QuoteBroadcaster.class),
                new AlertService(
                        new AlertProperties("", 5, 10),
                        new SlackNotifier(RestClient.builder().build(), new AlertProperties("", 5, 10)),
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
        return new QuoteProperties("xyz", 1450, List.of(
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
            long shares = code.equals("005930") ? 5_919_637_922L : 728_002_365L;
            BigDecimal close = code.equals("005930") ? new BigDecimal("71000") : new BigDecimal("205000");
            return Optional.of(new OfficialStockPrice(
                    code, close, "2026-06-19", close.add(BigDecimal.valueOf(1000)), shares,
                    close.multiply(BigDecimal.valueOf(shares))));
        }
    }

    private static final class StubExchangeRateClient implements ExchangeRateClient {
        private final boolean enabled;
        private int calls;

        private StubExchangeRateClient(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public ExchangeRateQuote fetchUsdKrw() {
            calls++;
            return new ExchangeRateQuote(new BigDecimal("1476.8"), "2026-06-19", "KOREA_EXIMBANK");
        }

        @Override
        public ExchangeRateQuote fetchUsdKrw(LocalDate date) {
            return new ExchangeRateQuote(new BigDecimal("1476.8"), date.toString(), "KOREA_EXIMBANK");
        }
    }
}
