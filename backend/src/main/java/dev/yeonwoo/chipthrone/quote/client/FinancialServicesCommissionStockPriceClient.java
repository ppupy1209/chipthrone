package dev.yeonwoo.chipthrone.quote.client;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import dev.yeonwoo.chipthrone.quote.model.OfficialStockPrice;
import dev.yeonwoo.chipthrone.quote.service.QuoteMetrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class FinancialServicesCommissionStockPriceClient implements OfficialStockPriceClient {

    private static final Logger log = LoggerFactory.getLogger(FinancialServicesCommissionStockPriceClient.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient;
    private final QuoteMetrics metrics;
    private final Clock clock;
    private final String url;
    private final String serviceKey;

    public FinancialServicesCommissionStockPriceClient(
            RestClient restClient,
            QuoteMetrics metrics,
            Clock clock,
            @Value("${chipthrone.source.fsc-stock-url:https://apis.data.go.kr/1160100/service/GetStockSecuritiesInfoService/getStockPriceInfo}") String url,
            @Value("${chipthrone.source.public-data-service-key:}") String serviceKey
    ) {
        this.restClient = restClient;
        this.metrics = metrics;
        this.clock = clock;
        this.url = url;
        this.serviceKey = serviceKey;
    }

    @Override
    public boolean enabled() {
        return !serviceKey.isBlank();
    }

    @Override
    public Optional<OfficialStockPrice> fetchLatest(String code) {
        if (!enabled()) {
            return Optional.empty();
        }
        metrics.externalCall("financial_services_commission", "daily_stock_price");
        String beginDate = LocalDate.now(clock.withZone(KST)).minusDays(30).format(BASIC_DATE);
        JsonNode response = restClient.get()
                .uri(UriComponentsBuilder.fromUriString(url)
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("resultType", "json")
                        .queryParam("pageNo", 1)
                        .queryParam("numOfRows", 20)
                        .queryParam("beginBasDt", beginDate)
                        // srtnCd는 이 엔드포인트에서 조용히 무시된다(전 종목이 그대로 돌아옴).
                        // likeSrtnCd만 실제로 필터링하므로 이걸 쓰고, 정확 일치는 아래에서 다시 거른다.
                        .queryParam("likeSrtnCd", code)
                        .build()
                        .encode()
                        .toUri())
                .retrieve()
                .body(JsonNode.class);

        JsonNode items = response == null
                ? null
                : response.path("response").path("body").path("items").path("item");
        if (items == null || !items.isArray()) {
            throw new IllegalStateException("Unexpected Financial Services Commission stock response");
        }
        Optional<OfficialStockPrice> latest = StreamSupport.stream(items.spliterator(), false)
                .filter(item -> code.equals(item.path("srtnCd").asText()))
                .max(Comparator.comparing(item -> item.path("basDt").asText()))
                .map(item -> new OfficialStockPrice(
                        code,
                        decimal(item, "clpr"),
                        isoDate(item.path("basDt").asText()),
                        decimal(item, "hipr"),
                        decimal(item, "lstgStCnt").longValueExact(),
                        decimal(item, "mrktTotAmt")
                ));
        if (latest.isEmpty()) {
            // 응답은 정상인데 해당 종목이 없는 경우. 조용히 비면 원인을 못 찾으니 남긴다.
            log.warn("No Financial Services Commission row matched {} in {} items.", code, items.size());
        }
        return latest;
    }

    private BigDecimal decimal(JsonNode item, String field) {
        String value = item.path(field).asText().replace(",", "");
        if (value.isBlank()) {
            throw new IllegalStateException("Missing Financial Services Commission field: " + field);
        }
        return new BigDecimal(value);
    }

    private String isoDate(String value) {
        return LocalDate.parse(value, BASIC_DATE).toString();
    }
}
