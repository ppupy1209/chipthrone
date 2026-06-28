package dev.yeonwoo.chipthrone.alert;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class AlertService {

    private final AlertProperties properties;
    private final SlackNotifier slackNotifier;
    private final Clock clock;
    private final Map<AlertEvent, FailureState> states = new EnumMap<>(AlertEvent.class);
    private final Map<AlertNotification, Instant> lastSentAt = new EnumMap<>(AlertNotification.class);

    public AlertService(AlertProperties properties, SlackNotifier slackNotifier, Clock clock) {
        this.properties = properties;
        this.slackNotifier = slackNotifier;
        this.clock = clock;
    }

    public synchronized void notifyStartup(String version) {
        send(AlertNotification.DEPLOYMENT, ":white_check_mark: chipthrone-api v" + version + " 기동");
    }

    public synchronized void recordFailure(AlertEvent event) {
        FailureState state = state(event);
        state.consecutiveFailures++;
        if (!state.failing && state.consecutiveFailures >= properties.consecutiveFailureThreshold()) {
            state.failing = true;
            send(failureNotification(event), failureMessage(event));
        }
    }

    public synchronized void recordSuccess(AlertEvent event) {
        FailureState state = state(event);
        state.consecutiveFailures = 0;
        if (state.failing) {
            state.failing = false;
            send(recoveryNotification(event), recoveryMessage(event));
        }
    }

    private FailureState state(AlertEvent event) {
        return states.computeIfAbsent(event, ignored -> new FailureState());
    }

    private void send(AlertNotification notification, String text) {
        Instant now = clock.instant();
        Instant lastSent = lastSentAt.get(notification);
        if (lastSent != null && now.isBefore(lastSent.plus(cooldown()))) {
            return;
        }
        slackNotifier.send(text);
        lastSentAt.put(notification, now);
    }

    private Duration cooldown() {
        return Duration.ofMinutes(properties.cooldownMinutes());
    }

    private AlertNotification failureNotification(AlertEvent event) {
        return switch (event) {
            case QUOTE_SOURCE -> AlertNotification.QUOTE_SOURCE_FAILURE;
            case KIS_PERSISTENT -> AlertNotification.KIS_PERSISTENT_FAILURE;
        };
    }

    private AlertNotification recoveryNotification(AlertEvent event) {
        return switch (event) {
            case QUOTE_SOURCE -> AlertNotification.QUOTE_SOURCE_RECOVERY;
            case KIS_PERSISTENT -> AlertNotification.KIS_PERSISTENT_RECOVERY;
        };
    }

    private String failureMessage(AlertEvent event) {
        return switch (event) {
            case QUOTE_SOURCE -> ":warning: chipthrone-api 시세 소스 장애: 연속 실패 "
                    + properties.consecutiveFailureThreshold() + "회";
            case KIS_PERSISTENT -> ":warning: chipthrone-api KIS 지속 실패: 정규장 추정치 폴백 "
                    + properties.consecutiveFailureThreshold() + "회";
        };
    }

    private String recoveryMessage(AlertEvent event) {
        return switch (event) {
            case QUOTE_SOURCE -> ":white_check_mark: chipthrone-api 시세 소스 복구";
            case KIS_PERSISTENT -> ":white_check_mark: chipthrone-api KIS 복구";
        };
    }

    private enum AlertNotification {
        DEPLOYMENT,
        QUOTE_SOURCE_FAILURE,
        QUOTE_SOURCE_RECOVERY,
        KIS_PERSISTENT_FAILURE,
        KIS_PERSISTENT_RECOVERY
    }

    private static final class FailureState {
        private int consecutiveFailures;
        private boolean failing;
    }
}
