package dev.yeonwoo.chipthrone.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KisRestMarketDataClientTest {

    @Test
    void mapsKisPriceResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisProperties properties = new KisProperties("https://example.test", "app-key", "app-secret");
        KisAccessTokenProvider tokenProvider = new KisAccessTokenProvider(
                properties,
                () -> new KisAccessToken("access-token", Instant.parse("2026-06-23T00:00:00Z")),
                Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneId.of("UTC"))
        );
        KisRestMarketDataClient client = new KisRestMarketDataClient(builder, properties, tokenProvider);

        server.expect(requestTo("https://example.test/uapi/domestic-stock/v1/quotations/inquire-price"
                        + "?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=005930"))
                .andExpect(queryParam("FID_COND_MRKT_DIV_CODE", "J"))
                .andExpect(queryParam("FID_INPUT_ISCD", "005930"))
                .andExpect(header("authorization", "Bearer access-token"))
                .andExpect(header("appkey", "app-key"))
                .andExpect(header("appsecret", "app-secret"))
                .andExpect(header("tr_id", "FHKST01010100"))
                .andRespond(withSuccess("""
                        {
                          "output": {
                            "stck_prpr": "72000",
                            "prdy_ctrt": "1.25",
                            "stck_prdy_clpr": "71100"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<KisStockQuote> quote = client.fetchStockQuote("005930");

        assertThat(quote).isPresent();
        assertThat(quote.orElseThrow().priceKrw()).isEqualByComparingTo(new BigDecimal("72000"));
        assertThat(quote.orElseThrow().changePct()).isEqualByComparingTo(new BigDecimal("1.25"));
        assertThat(quote.orElseThrow().previousRegularClose()).isEqualByComparingTo(new BigDecimal("71100"));
        server.verify(Duration.ofSeconds(1));
    }
}
