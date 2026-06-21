package dev.yeonwoo.chipthrone.quote.client;

import java.math.BigDecimal;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import dev.yeonwoo.chipthrone.quote.config.KisProperties;
import dev.yeonwoo.chipthrone.quote.model.KisStockQuote;

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
                fetchNxtCloseOrNull(code, accessToken)
        ));
    }

    private BigDecimal fetchNxtCloseOrNull(String code, String accessToken) {
        try {
            JsonNode output = fetchPriceOutput("NX", code, accessToken);
            if (output == null) {
                log.warn("Unexpected KIS NXT price response for code {}", code);
                return null;
            }
            return decimal(output, "stck_prpr");
        } catch (RuntimeException ex) {
            log.warn("Failed to fetch KIS NXT quote for code {}. Keeping nxtClose null.", code, ex);
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

    private BigDecimal decimal(JsonNode output, String field) {
        String value = output.path(field).asText("").trim();
        if (value.isEmpty()) {
            return null;
        }
        return new BigDecimal(value.replace(",", ""));
    }
}
