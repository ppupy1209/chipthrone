package dev.yeonwoo.chipthrone.quote.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import dev.yeonwoo.chipthrone.quote.model.OfficialStockPrice;
import dev.yeonwoo.chipthrone.quote.service.QuoteMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FinancialServicesCommissionStockPriceClientTest {

    @Test
    void mapsLatestExactStockAndCountsCall() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FinancialServicesCommissionStockPriceClient client = new FinancialServicesCommissionStockPriceClient(
                builder.build(),
                new QuoteMetrics(registry),
                Clock.fixed(Instant.parse("2026-06-22T01:00:00Z"), ZoneOffset.UTC),
                "https://data.example.test/getStockPriceInfo",
                "decoded-key"
        );

        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://data.example.test/getStockPriceInfo?")))
                .andExpect(queryParam("serviceKey", "decoded-key"))
                .andExpect(queryParam("resultType", "json"))
                .andExpect(queryParam("srtnCd", "005930"))
                .andRespond(withSuccess("""
                        {"response":{"body":{"items":{"item":[
                          {"basDt":"20260618","srtnCd":"005930","clpr":"70000","hipr":"71000","lstgStCnt":"5919637922","mrktTotAmt":"414374454540000"},
                          {"basDt":"20260619","srtnCd":"005930","clpr":"72000","hipr":"73000","lstgStCnt":"5919637922","mrktTotAmt":"426213930384000"}
                        ]}}}}
                        """, MediaType.APPLICATION_JSON));

        OfficialStockPrice price = client.fetchLatest("005930").orElseThrow();

        assertThat(price.close()).isEqualByComparingTo("72000");
        assertThat(price.closeDate()).isEqualTo("2026-06-19");
        assertThat(price.sharesOutstanding()).isEqualTo(5_919_637_922L);
        assertThat(registry.counter(
                "chipthrone.quote.external.api.calls",
                "source", "financial_services_commission",
                "operation", "daily_stock_price").count()).isEqualTo(1);
        server.verify(Duration.ofSeconds(1));
    }
}
