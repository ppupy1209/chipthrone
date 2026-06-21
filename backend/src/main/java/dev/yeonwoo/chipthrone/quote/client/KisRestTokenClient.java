package dev.yeonwoo.chipthrone.quote.client;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import dev.yeonwoo.chipthrone.quote.config.KisProperties;
import dev.yeonwoo.chipthrone.quote.model.KisAccessToken;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KisRestTokenClient implements KisTokenClient {

    private final RestClient restClient;
    private final KisProperties properties;
    private final Clock clock;

    public KisRestTokenClient(RestClient.Builder builder, KisProperties properties, Clock clock) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public KisAccessToken issueToken() {
        JsonNode response = restClient.post()
                .uri("/oauth2/tokenP")
                .body(Map.of(
                        "grant_type", "client_credentials",
                        "appkey", properties.appKey(),
                        "appsecret", properties.appSecret()
                ))
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("access_token").asText("").isBlank()) {
            throw new IllegalStateException("Unexpected KIS token response");
        }

        long expiresIn = response.path("expires_in").asLong(86400);
        Instant expiresAt = clock.instant().plusSeconds(expiresIn);
        return new KisAccessToken(response.path("access_token").asText(), expiresAt);
    }
}
