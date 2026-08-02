package dev.yeonwoo.chipthrone.quote.client;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import dev.yeonwoo.chipthrone.quote.service.QuoteMetrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ErApiExchangeRateClient implements ExchangeRateClient {

    private final RestClient restClient;
    private final QuoteMetrics metrics;
    private final String usdLatestUrl;

    public ErApiExchangeRateClient(
            RestClient restClient,
            QuoteMetrics metrics,
            @Value("${chipthrone.source.fx-url:https://open.er-api.com/v6/latest/USD}") String usdLatestUrl
    ) {
        this.restClient = restClient;
        this.metrics = metrics;
        this.usdLatestUrl = usdLatestUrl;
    }

    @Override
    public BigDecimal fetchUsdKrw() {
        metrics.externalCall("fx", "usd_krw");
        JsonNode response = restClient.get()
                .uri(usdLatestUrl)
                .retrieve()
                .body(JsonNode.class);

        JsonNode krw = response == null ? null : response.path("rates").path("KRW");
        if (krw == null || !krw.isNumber()) {
            throw new IllegalStateException("Unexpected exchange rate response");
        }
        return krw.decimalValue();
    }
}
