package dev.yeonwoo.chipthrone.quote;

import java.math.BigDecimal;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KisRestMarketDataClient implements KisMarketDataClient {

    private static final Logger log = LoggerFactory.getLogger(KisRestMarketDataClient.class);
    private static final String INQUIRE_PRICE_TR_ID = "FHKST01010100";

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
    public Optional<KisStockQuote> fetchStockQuote(String code) {
        if (!enabled()) {
            return Optional.empty();
        }

        JsonNode output = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", code)
                        .build())
                .header("authorization", "Bearer " + tokenProvider.accessToken())
                .header("appkey", properties.appKey())
                .header("appsecret", properties.appSecret())
                .header("tr_id", INQUIRE_PRICE_TR_ID)
                .header("content-type", "application/json; charset=utf-8")
                .retrieve()
                .body(JsonNode.class);

        if (output == null || output.path("output").isMissingNode()) {
            log.warn("Unexpected KIS price response for code {}", code);
            return Optional.empty();
        }

        JsonNode price = output.path("output");
        return Optional.of(new KisStockQuote(
                code,
                decimal(price, "stck_prpr"),
                decimal(price, "prdy_ctrt"),
                decimal(price, "stck_prdy_clpr")
        ));
    }

    private BigDecimal decimal(JsonNode output, String field) {
        String value = output.path(field).asText("").trim();
        if (value.isEmpty()) {
            return null;
        }
        return new BigDecimal(value.replace(",", ""));
    }
}
