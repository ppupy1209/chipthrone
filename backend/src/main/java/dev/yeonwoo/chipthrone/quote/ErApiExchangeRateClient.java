package dev.yeonwoo.chipthrone.quote;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ErApiExchangeRateClient implements ExchangeRateClient {

    private static final String USD_LATEST_URL = "https://open.er-api.com/v6/latest/USD";

    private final RestClient restClient;

    public ErApiExchangeRateClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public BigDecimal fetchUsdKrw() {
        JsonNode response = restClient.get()
                .uri(USD_LATEST_URL)
                .retrieve()
                .body(JsonNode.class);

        JsonNode krw = response == null ? null : response.path("rates").path("KRW");
        if (krw == null || !krw.isNumber()) {
            throw new IllegalStateException("Unexpected exchange rate response");
        }
        return krw.decimalValue();
    }
}
