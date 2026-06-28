package dev.yeonwoo.chipthrone.alert;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class SlackNotifier {

    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);

    private final RestClient restClient;
    private final AlertProperties properties;

    public SlackNotifier(RestClient restClient, AlertProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public void send(String text) {
        if (!StringUtils.hasText(properties.slackWebhookUrl())) {
            return;
        }
        try {
            restClient.post()
                    .uri(properties.slackWebhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            log.warn("Failed to send Slack alert.", ex);
        }
    }
}
