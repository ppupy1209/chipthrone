package dev.yeonwoo.chipthrone.quote.client;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KoreaEximExchangeRateClientTest {

    @Test
    void mapsOfficialUsdDealBaseRate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KoreaEximExchangeRateClient client = new KoreaEximExchangeRateClient(
                builder.build(),
                new QuoteMetrics(registry),
                Clock.fixed(Instant.parse("2026-06-22T01:00:00Z"), ZoneOffset.UTC),
                "https://fx.example.test/exchangeJSON",
                "auth-key"
        );

        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://fx.example.test/exchangeJSON?")))
                .andExpect(queryParam("authkey", "auth-key"))
                .andExpect(queryParam("searchdate", "20260622"))
                .andExpect(queryParam("data", "AP01"))
                .andRespond(withSuccess("""
                        [
                          {"cur_unit":"EUR","deal_bas_r":"1,700.25"},
                          {"cur_unit":"USD","deal_bas_r":"1,476.80"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        ExchangeRateQuote quote = client.fetchUsdKrw();

        assertThat(quote.rate()).isEqualByComparingTo("1476.80");
        assertThat(quote.asOfDate()).isEqualTo("2026-06-22");
        assertThat(quote.source()).isEqualTo("KOREA_EXIMBANK");
        assertThat(registry.counter(
                "chipthrone.quote.external.api.calls",
                "source", "korea_eximbank",
                "operation", "usd_krw").count()).isEqualTo(1);
        server.verify(Duration.ofSeconds(1));
    }
}
