package dev.yeonwoo.chipthrone.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chipthrone.alert")
public record AlertProperties(
        String slackWebhookUrl,
        int consecutiveFailureThreshold,
        long cooldownMinutes
) {

    public AlertProperties {
        if (slackWebhookUrl == null) {
            slackWebhookUrl = "";
        }
        if (consecutiveFailureThreshold <= 0) {
            consecutiveFailureThreshold = 5;
        }
        if (cooldownMinutes <= 0) {
            cooldownMinutes = 10;
        }
    }
}
