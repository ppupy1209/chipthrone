package dev.yeonwoo.chipthrone.quote.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import dev.yeonwoo.chipthrone.quote.config.KisProperties;
import dev.yeonwoo.chipthrone.quote.model.KisClosingPrice;
import dev.yeonwoo.chipthrone.quote.model.KisStockQuote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KisRestMarketDataClient implements KisMarketDataClient {

    private static final Logger log = LoggerFactory.getLogger(KisRestMarketDataClient.class);
    private static final String INQUIRE_PRICE_TR_ID = "FHKST01010100";
    private static final String INQUIRE_DAILY_PRICE_TR_ID = "FHKST01010400";
    private static final DateTimeFormatter KIS_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient;
    private final KisProperties properties;
    private final KisAccessTokenProvider tokenProvider;

    public KisRestMarketDataClient(
            RestClient.Builder builder,
            KisProperties properties,
            KisAccessTokenProvider tokenProvider
    ) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public boolean enabled() {
        return properties.enabled();
    }

    @Override
    public Optional<KisStockQuote> fetchCurrentStockQuote(String code) {
        if (!enabled()) {
            return Optional.empty();
        }

        String accessToken = tokenProvider.accessToken();
        JsonNode output = fetchPriceOutput("J", code, accessToken);

        if (output == null) {
            log.warn("Unexpected KIS price response for code {}", code);
            return Optional.empty();
        }

        return Optional.of(new KisStockQuote(
                code,
                decimal(output, "stck_prpr"),
                decimal(output, "prdy_ctrt"),
                decimal(output, "stck_prdy_clpr"),
                null,
                null,
                null,
                null
        ));
    }

    @Override
    public Optional<KisClosingPrice> fetchClosingPrice(String code) {
        if (!enabled()) {
            return Optional.empty();
        }

        String accessToken = tokenProvider.accessToken();
        DailyClose regularClose = fetchDailyClose("J", code, accessToken);
        if (regularClose == null) {
            log.warn("Unexpected KIS regular close response for code {}", code);
            return Optional.empty();
        }

        DailyClose nxtClose = fetchNxtDailyCloseOrNull(code, accessToken);
        return Optional.of(new KisClosingPrice(
                code,
                regularClose.close(),
                regularClose.date(),
                nxtClose == null ? null : nxtClose.close(),
                nxtClose == null ? regularClose.date() : nxtClose.date()
        ));
    }

    private DailyClose fetchNxtDailyCloseOrNull(String code, String accessToken) {
        try {
            DailyClose close = fetchDailyClose("NX", code, accessToken);
            if (close == null) {
                log.warn("Unexpected KIS NXT close response for code {}", code);
                return null;
            }
            return close;
        } catch (RuntimeException ex) {
            log.warn("Failed to fetch KIS NXT close for code {}. Keeping nxtClose null.", code, ex);
            return null;
        }
    }

    private JsonNode fetchPriceOutput(String marketDivisionCode, String code, String accessToken) {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", marketDivisionCode)
                        .queryParam("FID_INPUT_ISCD", code)
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", properties.appKey())
                .header("appsecret", properties.appSecret())
                .header("tr_id", INQUIRE_PRICE_TR_ID)
                .header("content-type", "application/json; charset=utf-8")
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("output").isMissingNode()) {
            return null;
        }
        return response.path("output");
    }

    private DailyClose fetchDailyClose(String marketDivisionCode, String code, String accessToken) {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", marketDivisionCode)
                        .queryParam("FID_INPUT_ISCD", code)
                        .queryParam("FID_PERIOD_DIV_CODE", "D")
                        .queryParam("FID_ORG_ADJ_PRC", "0")
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", properties.appKey())
                .header("appsecret", properties.appSecret())
                .header("tr_id", INQUIRE_DAILY_PRICE_TR_ID)
                .header("content-type", "application/json; charset=utf-8")
                .retrieve()
                .body(JsonNode.class);

        JsonNode row = firstDailyRow(response);
        if (row == null) {
            return null;
        }
        BigDecimal close = decimal(row, "stck_clpr");
        String date = isoDate(row.path("stck_bsop_date").asText("").trim());
        if (close == null || date == null) {
            return null;
        }
        return new DailyClose(close, date);
    }

    private JsonNode firstDailyRow(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode output = response.path("output");
        if (output.isArray() && !output.isEmpty()) {
            return output.get(0);
        }
        JsonNode output2 = response.path("output2");
        if (output2.isArray() && !output2.isEmpty()) {
            return output2.get(0);
        }
        if (output.isObject()) {
            return output;
        }
        return null;
    }

    private String isoDate(String value) {
        if (value.length() != 8) {
            return null;
        }
        return LocalDate.parse(value, KIS_DATE_FORMAT).toString();
    }

    private BigDecimal decimal(JsonNode output, String field) {
        String value = output.path(field).asText("").trim();
        if (value.isEmpty()) {
            return null;
        }
        return new BigDecimal(value.replace(",", ""));
    }

    private record DailyClose(BigDecimal close, String date) {
    }
}
