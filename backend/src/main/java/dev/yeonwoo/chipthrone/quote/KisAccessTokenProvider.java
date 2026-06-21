package dev.yeonwoo.chipthrone.quote;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

@Component
public class KisAccessTokenProvider {

    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);

    private final KisProperties properties;
    private final KisTokenClient tokenClient;
    private final Clock clock;
    private KisAccessToken cachedToken;

    public KisAccessTokenProvider(KisProperties properties, KisTokenClient tokenClient, Clock clock) {
        this.properties = properties;
        this.tokenClient = tokenClient;
        this.clock = clock;
    }

    public synchronized String accessToken() {
        if (!properties.enabled()) {
            throw new IllegalStateException("KIS is disabled because kis.app-key is empty");
        }

        Instant refreshAfter = clock.instant().plus(REFRESH_MARGIN);
        if (cachedToken == null || !cachedToken.expiresAt().isAfter(refreshAfter)) {
            cachedToken = tokenClient.issueToken();
        }
        return cachedToken.value();
    }
}
