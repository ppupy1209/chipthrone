package dev.yeonwoo.chipthrone.quote.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote;
import dev.yeonwoo.chipthrone.quote.service.QuoteMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class UpbitUsdcExchangeRateClientTest {

    private static final String API = "https://api.upbit.example.test";
    private static final String MARKET = "KRW-USDC";
    private static final Instant NOW = Instant.parse("2026-08-30T14:20:59Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant KRX_CLOSE = Instant.parse("2026-07-31T06:30:00Z");

    private record Fixture(
            UpbitUsdcExchangeRateClient client,
            MockRestServiceServer server,
            SimpleMeterRegistry registry
    ) {
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        return new Fixture(
                new UpbitUsdcExchangeRateClient(
                        builder.build(), new QuoteMetrics(registry), CLOCK, API, MARKET),
                server,
                registry
        );
    }

    @Test
    void readsFreshOrderbookMidpointAndCountsOneServerSideCall() {
        Fixture f = fixture();
        f.server().expect(requestTo(API + "/v1/orderbook?markets=KRW-USDC&count=1"))
                .andExpect(headerDoesNotExist(HttpHeaders.ORIGIN))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("""
                        [{"market":"KRW-USDC","timestamp":1788099593809,
                          "orderbook_units":[{"bid_price":1387.0,"ask_price":1388.0}]}]
                        """, MediaType.APPLICATION_JSON));

        ExchangeRateQuote quote = f.client().fetchUsdKrw();

        assertThat(quote.rate()).isEqualByComparingTo("1387.5");
        assertThat(quote.source()).isEqualTo("UPBIT_USDC");
        assertThat(quote.asOfDate()).isEqualTo("2026-08-30");
        assertThat(quote.fetchedAt()).isEqualTo(NOW);
        assertThat(f.registry().counter(
                "chipthrone.quote.external.api.calls",
                "source", "upbit",
                "operation", "usdc_krw_orderbook").count()).isEqualTo(1);
        f.server().verify(Duration.ofSeconds(1));
    }

    @Test
    void rejectsOrderbookOlderThanFifteenMinutes() {
        Fixture f = fixture();
        f.server().expect(requestTo(API + "/v1/orderbook?markets=KRW-USDC&count=1"))
                .andRespond(withSuccess("""
                        [{"market":"KRW-USDC","timestamp":1788098400000,
                          "orderbook_units":[{"bid_price":1387.0,"ask_price":1388.0}]}]
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> f.client().fetchUsdKrw())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stale Upbit");
    }

    @Test
    void readsTheLastMinuteCandleBeforeTheRequestedInstantAndCachesIt() {
        Fixture f = fixture();
        f.server().expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/v1/candles/minutes/1")))
                .andExpect(queryParam("market", MARKET))
                .andExpect(queryParam("to", KRX_CLOSE.toString()))
                .andExpect(queryParam("count", "1"))
                .andRespond(withSuccess("""
                        [{"market":"KRW-USDC","candle_date_time_utc":"2026-07-31T06:23:00",
                          "trade_price":1390.0,"timestamp":1785479016000}]
                        """, MediaType.APPLICATION_JSON));

        ExchangeRateQuote first = f.client().fetchUsdKrw(KRX_CLOSE);
        ExchangeRateQuote second = f.client().fetchUsdKrw(KRX_CLOSE);

        assertThat(first.rate()).isEqualByComparingTo("1390.0");
        assertThat(second).isEqualTo(first);
        assertThat(f.registry().counter(
                "chipthrone.quote.external.api.calls",
                "source", "upbit",
                "operation", "usdc_krw_minute_candle").count()).isEqualTo(1);
        f.server().verify(Duration.ofSeconds(1));
    }

    @Test
    void rejectsHistoricalCandleMoreThanThirtyMinutesAway() {
        Fixture f = fixture();
        f.server().expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/v1/candles/minutes/1")))
                .andRespond(withSuccess("""
                        [{"market":"KRW-USDC","candle_date_time_utc":"2026-07-31T05:59:00",
                          "trade_price":1390.0,"timestamp":1785477540000}]
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> f.client().fetchUsdKrw(KRX_CLOSE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not close");
    }

    @Test
    void isAlwaysEnabledBecauseQuotationDoesNotRequireAnApiKey() {
        assertThat(fixture().client().enabled()).isTrue();
    }
}
