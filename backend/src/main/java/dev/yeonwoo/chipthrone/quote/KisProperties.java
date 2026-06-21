package dev.yeonwoo.chipthrone.quote;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kis")
public record KisProperties(
        String baseUrl,
        String appKey,
        String appSecret
) {
    public KisProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://openapi.koreainvestment.com:9443";
        }
        if (appKey == null) {
            appKey = "";
        }
        if (appSecret == null) {
            appSecret = "";
        }
    }

    public boolean enabled() {
        return !appKey.isBlank();
    }
}
