package dev.yeonwoo.chipthrone.quote.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote;
import dev.yeonwoo.chipthrone.quote.service.QuoteMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PythExchangeRateClientTest {

    private static final String HERMES = "https://hermes.example.test";
    private static final String FEED = "abc123";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-03T05:30:00Z"), ZoneOffset.UTC);
    // 2026-07-31 15:30 KST == 06:30 UTC == epoch 1785472200
    private static final Instant KRX_CLOSE = Instant.parse("2026-07-31T06:30:00Z");

    private static String body(String price, int expo, long publishTime) {
        return """
                {"binary":{"encoding":"hex","data":["504e4155"]},
                 "parsed":[{"id":"abc123","price":{"price":"%s","conf":"86475","expo":%d,"publish_time":%d}}]}
                """.formatted(price, expo, publishTime);
    }

    private record Fixture(PythExchangeRateClient client, MockRestServiceServer server, SimpleMeterRegistry registry) {}

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        return new Fixture(
                new PythExchangeRateClient(builder.build(), new QuoteMetrics(registry), CLOCK, HERMES, FEED),
                server,
                registry);
    }

    @Test
    void scalesIntegerPriceByExponentAndCountsCall() {
        Fixture f = fixture();
        f.server().expect(requestTo(HERMES + "/v2/updates/price/latest?ids%5B%5D=" + FEED + "&parsed=true&encoding=hex"))
                .andRespond(withSuccess(body("142951017", -5, 1785735160), MediaType.APPLICATION_JSON));

        ExchangeRateQuote quote = f.client().fetchUsdKrw();

        // 142951017 × 10^-5
        assertThat(quote.rate()).isEqualByComparingTo("1429.51017");
        assertThat(quote.source()).isEqualTo("PYTH");
        assertThat(quote.asOfDate()).isEqualTo("2026-08-03");
        assertThat(f.registry().counter(
                "chipthrone.quote.external.api.calls",
                "source", "pyth",
                "operation", "usd_krw").count()).isEqualTo(1);
        f.server().verify(Duration.ofSeconds(1));
    }

    @Test
    void asksForThePriceAtTheExactRequestedInstant() {
        Fixture f = fixture();
        f.server().expect(requestTo(HERMES + "/v2/updates/price/" + KRX_CLOSE.getEpochSecond()
                        + "?ids%5B%5D=" + FEED + "&parsed=true&encoding=hex"))
                .andRespond(withSuccess(body("142615010", -5, KRX_CLOSE.getEpochSecond()), MediaType.APPLICATION_JSON));

        assertThat(f.client().fetchUsdKrw(KRX_CLOSE).rate()).isEqualByComparingTo("1426.15010");
        f.server().verify(Duration.ofSeconds(1));
    }

    @Test
    void walksBackWhenTheForexMarketWasClosedAtThatInstant() {
        Fixture f = fixture();
        // 주말 등으로 그 시각에 시세가 없으면 404다. 한 시간 전으로 되짚어 성공해야 한다.
        f.server().expect(requestTo(HERMES + "/v2/updates/price/" + KRX_CLOSE.getEpochSecond()
                        + "?ids%5B%5D=" + FEED + "&parsed=true&encoding=hex"))
                .andRespond(withResourceNotFound());
        f.server().expect(requestTo(HERMES + "/v2/updates/price/" + KRX_CLOSE.minus(Duration.ofHours(1)).getEpochSecond()
                        + "?ids%5B%5D=" + FEED + "&parsed=true&encoding=hex"))
                .andRespond(withSuccess(body("143000000", -5, 1785468600), MediaType.APPLICATION_JSON));

        assertThat(f.client().fetchUsdKrw(KRX_CLOSE).rate()).isEqualByComparingTo("1430.00000");
        f.server().verify(Duration.ofSeconds(1));
    }

    @Test
    void failsRatherThanGuessWhenNoPriceExistsInTheLookbackWindow() {
        Fixture f = fixture();
        for (int i = 0; i < 5; i++) {
            f.server().expect(requestTo(org.hamcrest.Matchers.startsWith(HERMES + "/v2/updates/price/")))
                    .andRespond(withResourceNotFound());
        }

        assertThatThrownBy(() -> f.client().fetchUsdKrw(KRX_CLOSE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no USD/KRW price");
        f.server().verify(Duration.ofSeconds(1));
    }

    @Test
    void rejectsMalformedResponseInsteadOfReturningZero() {
        Fixture f = fixture();
        f.server().expect(requestTo(org.hamcrest.Matchers.startsWith(HERMES)))
                .andRespond(withSuccess("""
                        {"parsed":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> f.client().fetchUsdKrw()).isInstanceOf(IllegalStateException.class);
        f.server().verify(Duration.ofSeconds(1));
    }

    /** 키가 없는 공개 API라 항상 켜져 있다. 이게 깨지면 환율이 조용히 설정 기준값으로 떨어진다. */
    @Test
    void isAlwaysEnabledBecauseNoKeyIsRequired() {
        assertThat(fixture().client().enabled()).isTrue();
    }
}
