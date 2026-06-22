package dev.yeonwoo.chipthrone.quote.client;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import dev.yeonwoo.chipthrone.quote.config.KisProperties;
import dev.yeonwoo.chipthrone.quote.model.InvestOpinionReport;
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
    private static final String INVEST_OPINION_TR_ID = "FHKST663300C0";
    private static final DateTimeFormatter KIS_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(15, 30);
    private static final LocalTime NXT_CLOSE = LocalTime.of(20, 0);
    private static final String NXT_MARKET_DIVISION_CODE = "NX";

    private final RestClient restClient;
    private final KisProperties properties;
    private final KisAccessTokenProvider tokenProvider;
    private final Clock clock;

    public KisRestMarketDataClient(
            RestClient.Builder builder,
            KisProperties properties,
            KisAccessTokenProvider tokenProvider,
            Clock clock
    ) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
        this.tokenProvider = tokenProvider;
        this.clock = clock;
    }

    @Override
    public boolean enabled() {
        return properties.enabled();
    }

    @Override
    public Optional<KisStockQuote> fetchCurrentStockQuote(String code) {
        return fetchCurrentStockQuote(code, "J");
    }

    @Override
    public Optional<KisStockQuote> fetchCurrentStockQuote(String code, String marketDivisionCode) {
        if (!enabled()) {
            return Optional.empty();
        }

        String accessToken = tokenProvider.accessToken();
        JsonNode output = fetchPriceOutput(marketDivisionCode, code, accessToken);

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
                regularClose.high(),
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

    @Override
    public List<InvestOpinionReport> fetchInvestOpinions(String code) {
        if (!enabled()) {
            return List.of();
        }

        String accessToken = tokenProvider.accessToken();
        LocalDate today = clock.instant().atZone(KST).toLocalDate();
        String startDate = today.minusDays(180).format(KIS_DATE_FORMAT);
        String endDate = today.format(KIS_DATE_FORMAT);

        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/invest-opinion")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_COND_SCR_DIV_CODE", "16633")
                        .queryParam("FID_INPUT_ISCD", code)
                        .queryParam("FID_INPUT_DATE_1", startDate)
                        .queryParam("FID_INPUT_DATE_2", endDate)
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", properties.appKey())
                .header("appsecret", properties.appSecret())
                .header("tr_id", INVEST_OPINION_TR_ID)
                .header("content-type", "application/json; charset=utf-8")
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.path("output").isArray()) {
            log.warn("Unexpected KIS invest-opinion response for code {}", code);
            return List.of();
        }

        List<InvestOpinionReport> reports = new ArrayList<>();
        for (JsonNode row : response.path("output")) {
            String date = isoDate(row.path("stck_bsop_date").asText("").trim());
            if (date == null) {
                continue;
            }
            reports.add(new InvestOpinionReport(
                    code,
                    date,
                    text(row, "mbcr_name"),
                    text(row, "invt_opnn"),
                    text(row, "invt_opnn_cls_code"),
                    text(row, "rgbf_invt_opnn"),
                    decimal(row, "hts_goal_prc")
            ));
        }
        return reports;
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

        JsonNode row = firstDailyRow(response, latestClosedTradingDate(marketDivisionCode));
        if (row == null) {
            return null;
        }
        BigDecimal close = decimal(row, "stck_clpr");
        BigDecimal high = decimal(row, "stck_hgpr");
        String date = isoDate(row.path("stck_bsop_date").asText("").trim());
        if (close == null || date == null) {
            return null;
        }
        return new DailyClose(close, date, high);
    }

    private JsonNode firstDailyRow(JsonNode response, LocalDate latestClosedDate) {
        if (response == null) {
            return null;
        }
        JsonNode output = response.path("output");
        if (output.isArray() && !output.isEmpty()) {
            return firstClosedRow(output, latestClosedDate);
        }
        JsonNode output2 = response.path("output2");
        if (output2.isArray() && !output2.isEmpty()) {
            return firstClosedRow(output2, latestClosedDate);
        }
        if (output.isObject()) {
            return isClosedRow(output, latestClosedDate) ? output : null;
        }
        return null;
    }

    private JsonNode firstClosedRow(JsonNode rows, LocalDate latestClosedDate) {
        for (JsonNode row : rows) {
            if (isClosedRow(row, latestClosedDate)) {
                return row;
            }
        }
        return null;
    }

    private boolean isClosedRow(JsonNode row, LocalDate latestClosedDate) {
        String dateText = row.path("stck_bsop_date").asText("").trim();
        if (dateText.length() != 8) {
            return false;
        }
        LocalDate rowDate = LocalDate.parse(dateText, KIS_DATE_FORMAT);
        return !rowDate.isAfter(latestClosedDate);
    }

    private LocalDate latestClosedTradingDate(String marketDivisionCode) {
        ZonedDateTime now = clock.instant().atZone(KST);
        LocalDate today = now.toLocalDate();
        LocalTime closeTime = NXT_MARKET_DIVISION_CODE.equals(marketDivisionCode) ? NXT_CLOSE : REGULAR_CLOSE;
        if (isWeekday(today) && !now.toLocalTime().isBefore(closeTime)) {
            return today;
        }
        return previousWeekday(today.minusDays(1));
    }

    private LocalDate previousWeekday(LocalDate date) {
        LocalDate candidate = date;
        while (!isWeekday(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    private boolean isWeekday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
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

    private String text(JsonNode output, String field) {
        return output.path(field).asText("").trim();
    }

    private record DailyClose(BigDecimal close, String date, BigDecimal high) {
    }
}
