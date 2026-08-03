package dev.yeonwoo.chipthrone.quote.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.yeonwoo.chipthrone.quote.client.MarketDataClient;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.MarketAssetPrice;
import dev.yeonwoo.chipthrone.quote.model.SessionClose;

import org.junit.jupiter.api.Test;

class UsSessionCloseServiceTest {

    private static final QuoteProperties.Asset NVIDIA = new QuoteProperties.Asset(
            "NVDA", "엔비디아", "xyz:NVDA", 24_200_000_000L, QuoteProperties.Market.US);
    private static final QuoteProperties.Asset SAMSUNG = new QuoteProperties.Asset(
            "005930", "삼성전자", "xyz:SMSN", 5_919_637_922L, QuoteProperties.Market.KRX);

    private static UsSessionCloseService service(StubMarketDataClient market, String nowIso) {
        return new UsSessionCloseService(market, Clock.fixed(Instant.parse(nowIso), ZoneOffset.UTC));
    }

    @Test
    void usesTodaysCloseOnceTheRegularSessionHasEnded() {
        StubMarketDataClient market = new StubMarketDataClient(new BigDecimal("183.52"));
        // 2026-08-04(화) 18:00 ET == 22:00 UTC. 그날 16:00 ET 마감은 이미 지났다.
        UsSessionCloseService service = service(market, "2026-08-04T22:00:00Z");

        Map<String, SessionClose> closes = service.sessionCloses(List.of(NVIDIA));

        assertThat(closes.get("NVDA").closeDate()).isEqualTo("2026-08-04");
        assertThat(closes.get("NVDA").closeUsd()).isEqualByComparingTo("183.52");
        // 2026-08-04 16:00 EDT == 20:00 UTC
        assertThat(market.requestedAt).isEqualTo(Instant.parse("2026-08-04T20:00:00Z"));
        assertThat(market.requestedSymbol).isEqualTo("xyz:NVDA");
    }

    @Test
    void fallsBackToThePreviousSessionBeforeTodaysCloseHappens() {
        StubMarketDataClient market = new StubMarketDataClient(new BigDecimal("180"));
        // 2026-08-04(화) 10:00 ET == 14:00 UTC. 아직 장중이라 직전 마감은 8/3(월)이다.
        UsSessionCloseService service = service(market, "2026-08-04T14:00:00Z");

        assertThat(service.sessionCloses(List.of(NVIDIA)).get("NVDA").closeDate()).isEqualTo("2026-08-03");
        assertThat(market.requestedAt).isEqualTo(Instant.parse("2026-08-03T20:00:00Z"));
    }

    @Test
    void skipsWeekendsBackToFridaysClose() {
        StubMarketDataClient market = new StubMarketDataClient(new BigDecimal("180"));
        // 2026-08-03(월) 09:00 ET == 13:00 UTC. 직전 마감은 7/31(금)이다.
        UsSessionCloseService service = service(market, "2026-08-03T13:00:00Z");

        assertThat(service.sessionCloses(List.of(NVIDIA)).get("NVDA").closeDate()).isEqualTo("2026-07-31");
        assertThat(market.requestedAt).isEqualTo(Instant.parse("2026-07-31T20:00:00Z"));
    }

    @Test
    void reusesCachedValueWhileTheReferenceCloseIsUnchanged() {
        StubMarketDataClient market = new StubMarketDataClient(new BigDecimal("183.52"));
        UsSessionCloseService service = service(market, "2026-08-04T22:00:00Z");

        service.sessionCloses(List.of(NVIDIA));
        service.sessionCloses(List.of(NVIDIA));

        assertThat(market.calls).isEqualTo(1);
    }

    /** 국내 종목은 금융위 확정 종가를 쓴다. 여기서 건드리면 안 된다. */
    @Test
    void skipsKrxAssets() {
        StubMarketDataClient market = new StubMarketDataClient(new BigDecimal("183.52"));
        UsSessionCloseService service = service(market, "2026-08-04T22:00:00Z");

        assertThat(service.sessionCloses(List.of(SAMSUNG))).isEmpty();
        assertThat(market.calls).isZero();
    }

    @Test
    void yieldsNothingWhenTheCandleIsMissing() {
        StubMarketDataClient market = new StubMarketDataClient(null);
        UsSessionCloseService service = service(market, "2026-08-04T22:00:00Z");

        assertThat(service.sessionCloses(List.of(NVIDIA))).isEmpty();
    }

    private static final class StubMarketDataClient implements MarketDataClient {
        private final BigDecimal close;
        private int calls;
        private String requestedSymbol;
        private Instant requestedAt;

        private StubMarketDataClient(BigDecimal close) {
            this.close = close;
        }

        @Override
        public List<MarketAssetPrice> fetchAssetPrices(String dex) {
            return List.of();
        }

        @Override
        public Optional<BigDecimal> fetchCloseAt(String symbol, Instant at) {
            calls++;
            requestedSymbol = symbol;
            requestedAt = at;
            return Optional.ofNullable(close);
        }
    }
}
