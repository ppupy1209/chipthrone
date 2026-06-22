package dev.yeonwoo.chipthrone.quote.client;

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

import dev.yeonwoo.chipthrone.quote.config.KisProperties;
import dev.yeonwoo.chipthrone.quote.model.KisAccessToken;
import dev.yeonwoo.chipthrone.quote.model.KisClosingPrice;
import dev.yeonwoo.chipthrone.quote.model.KisStockQuote;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KisRestMarketDataClientTest {

    @Test
    void mapsKisCurrentPriceResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisProperties properties = new KisProperties("https://example.test", "app-key", "app-secret");
        KisAccessTokenProvider tokenProvider = new KisAccessTokenProvider(
                properties,
                () -> new KisAccessToken("access-token", Instant.parse("2026-06-23T00:00:00Z")),
                Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneId.of("UTC"))
        );
        KisRestMarketDataClient client = new KisRestMarketDataClient(
                builder,
                properties,
                tokenProvider,
                Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneId.of("UTC"))
        );

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
        Optional<KisStockQuote> quote = client.fetchCurrentStockQuote("005930");

        assertThat(quote).isPresent();
        assertThat(quote.orElseThrow().priceKrw()).isEqualByComparingTo(new BigDecimal("72000"));
        assertThat(quote.orElseThrow().changePct()).isEqualByComparingTo(new BigDecimal("1.25"));
        assertThat(quote.orElseThrow().previousRegularClose()).isEqualByComparingTo(new BigDecimal("71100"));
        assertThat(quote.orElseThrow().regularClose()).isNull();
        assertThat(quote.orElseThrow().nxtClose()).isNull();
        server.verify(Duration.ofSeconds(1));
    }

    @Test
    void mapsKisCurrentPriceResponseForRequestedMarketDivisionCode() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisProperties properties = new KisProperties("https://example.test", "app-key", "app-secret");
        KisAccessTokenProvider tokenProvider = new KisAccessTokenProvider(
                properties,
                () -> new KisAccessToken("access-token", Instant.parse("2026-06-23T00:00:00Z")),
                Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneId.of("UTC"))
        );
        KisRestMarketDataClient client = new KisRestMarketDataClient(
                builder,
                properties,
                tokenProvider,
                Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneId.of("UTC"))
        );

        server.expect(requestTo("https://example.test/uapi/domestic-stock/v1/quotations/inquire-price"
                        + "?FID_COND_MRKT_DIV_CODE=NX&FID_INPUT_ISCD=005930"))
                .andExpect(queryParam("FID_COND_MRKT_DIV_CODE", "NX"))
                .andExpect(queryParam("FID_INPUT_ISCD", "005930"))
                .andExpect(header("authorization", "Bearer access-token"))
                .andExpect(header("tr_id", "FHKST01010100"))
                .andRespond(withSuccess("""
                        {
                          "output": {
                            "stck_prpr": "72100",
                            "prdy_ctrt": "1.41",
                            "stck_prdy_clpr": "71100"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        Optional<KisStockQuote> quote = client.fetchCurrentStockQuote("005930", "NX");

        assertThat(quote).isPresent();
        assertThat(quote.orElseThrow().priceKrw()).isEqualByComparingTo(new BigDecimal("72100"));
        assertThat(quote.orElseThrow().changePct()).isEqualByComparingTo(new BigDecimal("1.41"));
        server.verify(Duration.ofSeconds(1));
    }

    @Test
    void mapsKisDailyCloseResponseDates() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisProperties properties = new KisProperties("https://example.test", "app-key", "app-secret");
        KisAccessTokenProvider tokenProvider = new KisAccessTokenProvider(
                properties,
                () -> new KisAccessToken("access-token", Instant.parse("2026-06-23T00:00:00Z")),
                Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneId.of("UTC"))
        );
        KisRestMarketDataClient client = new KisRestMarketDataClient(
                builder,
                properties,
                tokenProvider,
                Clock.fixed(Instant.parse("2026-06-22T11:00:00Z"), ZoneId.of("UTC"))
        );

        server.expect(requestTo("https://example.test/uapi/domestic-stock/v1/quotations/inquire-daily-price"
                        + "?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=005930&FID_PERIOD_DIV_CODE=D&FID_ORG_ADJ_PRC=0"))
                .andExpect(queryParam("FID_COND_MRKT_DIV_CODE", "J"))
                .andExpect(queryParam("FID_INPUT_ISCD", "005930"))
                .andExpect(queryParam("FID_PERIOD_DIV_CODE", "D"))
                .andExpect(queryParam("FID_ORG_ADJ_PRC", "0"))
                .andExpect(header("authorization", "Bearer access-token"))
                .andExpect(header("tr_id", "FHKST01010400"))
                .andRespond(withSuccess("""
                        {
                          "output": [
                            {
                              "stck_bsop_date": "20260619",
                              "stck_clpr": "71100",
                              "stck_hgpr": "73000"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/uapi/domestic-stock/v1/quotations/inquire-daily-price"
                        + "?FID_COND_MRKT_DIV_CODE=NX&FID_INPUT_ISCD=005930&FID_PERIOD_DIV_CODE=D&FID_ORG_ADJ_PRC=0"))
                .andExpect(queryParam("FID_COND_MRKT_DIV_CODE", "NX"))
                .andExpect(header("tr_id", "FHKST01010400"))
                .andRespond(withSuccess("""
                        {
                          "output": [
                            {
                              "stck_bsop_date": "20260619",
                              "stck_clpr": "72500"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<KisClosingPrice> quote = client.fetchClosingPrice("005930");

        assertThat(quote).isPresent();
        assertThat(quote.orElseThrow().regularClose()).isEqualByComparingTo(new BigDecimal("71100"));
        assertThat(quote.orElseThrow().regularCloseDate()).isEqualTo("2026-06-19");
        assertThat(quote.orElseThrow().regularHigh()).isEqualByComparingTo(new BigDecimal("73000"));
        assertThat(quote.orElseThrow().nxtClose()).isEqualByComparingTo(new BigDecimal("72500"));
        assertThat(quote.orElseThrow().nxtCloseDate()).isEqualTo("2026-06-19");
        server.verify(Duration.ofSeconds(1));
    }

    @Test
    void skipsRunningTodayDailyPriceRowBeforeRegularCloseAndMapsRegularHigh() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisProperties properties = new KisProperties("https://example.test", "app-key", "app-secret");
        KisAccessTokenProvider tokenProvider = new KisAccessTokenProvider(
                properties,
                () -> new KisAccessToken("access-token", Instant.parse("2026-06-23T00:00:00Z")),
                Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneId.of("UTC"))
        );
        KisRestMarketDataClient client = new KisRestMarketDataClient(
                builder,
                properties,
                tokenProvider,
                Clock.fixed(Instant.parse("2026-06-22T01:00:00Z"), ZoneId.of("UTC"))
        );

        server.expect(requestTo("https://example.test/uapi/domestic-stock/v1/quotations/inquire-daily-price"
                        + "?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=005930&FID_PERIOD_DIV_CODE=D&FID_ORG_ADJ_PRC=0"))
                .andExpect(queryParam("FID_COND_MRKT_DIV_CODE", "J"))
                .andExpect(header("tr_id", "FHKST01010400"))
                .andRespond(withSuccess("""
                        {
                          "output": [
                            {
                              "stck_bsop_date": "20260622",
                              "stck_clpr": "72000",
                              "stck_hgpr": "72500"
                            },
                            {
                              "stck_bsop_date": "20260619",
                              "stck_clpr": "71100",
                              "stck_hgpr": "73000"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/uapi/domestic-stock/v1/quotations/inquire-daily-price"
                        + "?FID_COND_MRKT_DIV_CODE=NX&FID_INPUT_ISCD=005930&FID_PERIOD_DIV_CODE=D&FID_ORG_ADJ_PRC=0"))
                .andExpect(queryParam("FID_COND_MRKT_DIV_CODE", "NX"))
                .andRespond(withSuccess("""
                        {
                          "output": [
                            {
                              "stck_bsop_date": "20260622",
                              "stck_clpr": "72600",
                              "stck_hgpr": "72700"
                            },
                            {
                              "stck_bsop_date": "20260619",
                              "stck_clpr": "72500",
                              "stck_hgpr": "72800"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<KisClosingPrice> quote = client.fetchClosingPrice("005930");

        assertThat(quote).isPresent();
        assertThat(quote.orElseThrow().regularClose()).isEqualByComparingTo(new BigDecimal("71100"));
        assertThat(quote.orElseThrow().regularCloseDate()).isEqualTo("2026-06-19");
        assertThat(quote.orElseThrow().regularHigh()).isEqualByComparingTo(new BigDecimal("73000"));
        assertThat(quote.orElseThrow().nxtClose()).isEqualByComparingTo(new BigDecimal("72500"));
        assertThat(quote.orElseThrow().nxtCloseDate()).isEqualTo("2026-06-19");
        server.verify(Duration.ofSeconds(1));
    }
}
