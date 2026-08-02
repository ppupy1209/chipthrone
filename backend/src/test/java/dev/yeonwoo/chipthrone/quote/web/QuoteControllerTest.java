package dev.yeonwoo.chipthrone.quote.web;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.yeonwoo.chipthrone.quote.model.MarketMode;
import dev.yeonwoo.chipthrone.quote.model.QuoteSnapshot;
import dev.yeonwoo.chipthrone.quote.model.StockQuote;
import dev.yeonwoo.chipthrone.quote.service.QuoteService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "chipthrone.quote.polling-enabled=false")
@AutoConfigureMockMvc
class QuoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuoteService quoteService;

    @Test
    void quotesReturnsLatestSnapshotShape() throws Exception {
        when(quoteService.currentSnapshot()).thenReturn(Optional.of(snapshot()));

        mockMvc.perform(get("/api/quotes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("ESTIMATE"))
                .andExpect(jsonPath("$.at").value("2026-06-21T05:00:00Z"))
                .andExpect(jsonPath("$.fxRate").value(1476.8))
                .andExpect(jsonPath("$.fxSource").value("CONFIG_FALLBACK"))
                .andExpect(jsonPath("$.stocks[0].code").value("005930"))
                .andExpect(jsonPath("$.stocks[0].name").value("삼성전자"))
                .andExpect(jsonPath("$.stocks[0].priceKrw").value(356208.0))
                .andExpect(jsonPath("$.stocks[0].priceUsd").value(241.18))
                .andExpect(jsonPath("$.stocks[0].changePct").value(1.04))
                .andExpect(jsonPath("$.stocks[0].changeBasis").doesNotExist())
                .andExpect(jsonPath("$.stocks[0].sharesOutstanding").value(5_919_637_922L))
                .andExpect(jsonPath("$.stocks[0].marketCap").value(2.108E15))
                .andExpect(jsonPath("$.stocks[0].officialMarketCap").value(nullValue()))
                .andExpect(jsonPath("$.stocks[0].regularClose").value(nullValue()))
                .andExpect(jsonPath("$.stocks[0].regularCloseDate").value(nullValue()))
                .andExpect(jsonPath("$.stocks[0].high").value(nullValue()))
                .andExpect(jsonPath("$.stocks[0].nxtClose").value(nullValue()))
                .andExpect(jsonPath("$.stocks[0].nxtCloseDate").value(nullValue()));
    }

    @Test
    void quotesReturnsServiceUnavailableBeforeFirstSnapshot() throws Exception {
        when(quoteService.currentSnapshot()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/quotes"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void assetsReturnsSupportedSymbols() throws Exception {
        mockMvc.perform(get("/api/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(16)))
                .andExpect(jsonPath("$[0].code").value("005930"))
                .andExpect(jsonPath("$[0].name").value("삼성전자"))
                .andExpect(jsonPath("$[1].code").value("000660"))
                .andExpect(jsonPath("$[5].code").value("AMD"))
                .andExpect(jsonPath("$[6].code").value("ASML"))
                .andExpect(jsonPath("$[14].code").value("TSM"))
                .andExpect(jsonPath("$[15].code").value("SKHY"));
    }

    @Test
    void streamRejectsMissingAndUnsupportedSymbols() throws Exception {
        mockMvc.perform(get("/api/stream"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/stream").param("symbols", "999999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void streamAcceptsEightSymbolsAndRejectsNine() throws Exception {
        String eightSymbols = "005930,000660,SNDK,MU,AVGO,AAPL,MSFT,GOOGL";

        mockMvc.perform(get("/api/stream").param("symbols", eightSymbols))
                .andExpect(request().asyncStarted());
        mockMvc.perform(get("/api/stream").param("symbols", eightSymbols + ",AMZN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void corsAllowsConfiguredApiOrigins() throws Exception {
        assertCorsAllowsOrigin("https://chipthrone.com");
        assertCorsAllowsOrigin("https://www.chipthrone.com");
        assertCorsAllowsOrigin("https://preview.vercel.app");
    }

    private void assertCorsAllowsOrigin(String origin) throws Exception {
        mockMvc.perform(options("/api/quotes")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
    }

    private QuoteSnapshot snapshot() {
        return new QuoteSnapshot(
                MarketMode.ESTIMATE,
                Instant.parse("2026-06-21T05:00:00Z"),
                1476.8,
                List.of(new StockQuote(
                        "005930",
                        "삼성전자",
                        356208.0,
                        241.18,
                        1.04,
                        5_919_637_922L,
                        2.108E15,
                        null,
                        null,
                        null,
                        null,
                        null
                ))
        );
    }
}
