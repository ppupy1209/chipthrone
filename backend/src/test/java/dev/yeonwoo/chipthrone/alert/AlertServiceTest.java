package dev.yeonwoo.chipthrone.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AlertServiceTest {

    @Test
    void sendsFailureOnlyAfterConsecutiveThreshold() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-22T01:00:00Z"));
        CapturingSlackNotifier notifier = new CapturingSlackNotifier();
        AlertService service = newService(notifier, clock, 3, 10);

        service.recordFailure(AlertEvent.QUOTE_SOURCE);
        service.recordFailure(AlertEvent.QUOTE_SOURCE);
        service.recordFailure(AlertEvent.QUOTE_SOURCE);
        service.recordFailure(AlertEvent.QUOTE_SOURCE);

        assertThat(notifier.messages).hasSize(1);
        assertThat(notifier.messages.getFirst()).contains("시세 소스 장애");
    }

    @Test
    void sendsRecoveryOnlyOnFailureToNormalTransition() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-22T01:00:00Z"));
        CapturingSlackNotifier notifier = new CapturingSlackNotifier();
        AlertService service = newService(notifier, clock, 2, 10);

        service.recordFailure(AlertEvent.KIS_PERSISTENT);
        service.recordFailure(AlertEvent.KIS_PERSISTENT);
        service.recordSuccess(AlertEvent.KIS_PERSISTENT);
        service.recordSuccess(AlertEvent.KIS_PERSISTENT);

        assertThat(notifier.messages).hasSize(2);
        assertThat(notifier.messages.get(0)).contains("KIS 지속 실패");
        assertThat(notifier.messages.get(1)).contains("KIS 복구");
    }

    @Test
    void suppressesSameNotificationWithinCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-22T01:00:00Z"));
        CapturingSlackNotifier notifier = new CapturingSlackNotifier();
        AlertService service = newService(notifier, clock, 2, 10);

        service.recordFailure(AlertEvent.QUOTE_SOURCE);
        service.recordFailure(AlertEvent.QUOTE_SOURCE);
        service.recordSuccess(AlertEvent.QUOTE_SOURCE);
        clock.advance(Duration.ofMinutes(5));
        service.recordFailure(AlertEvent.QUOTE_SOURCE);
        service.recordFailure(AlertEvent.QUOTE_SOURCE);
        service.recordSuccess(AlertEvent.QUOTE_SOURCE);
        clock.advance(Duration.ofMinutes(6));
        service.recordFailure(AlertEvent.QUOTE_SOURCE);
        service.recordFailure(AlertEvent.QUOTE_SOURCE);

        assertThat(notifier.messages).hasSize(3);
        assertThat(notifier.messages.get(0)).contains("시세 소스 장애");
        assertThat(notifier.messages.get(1)).contains("시세 소스 복구");
        assertThat(notifier.messages.get(2)).contains("시세 소스 장애");
    }

    private AlertService newService(
            CapturingSlackNotifier notifier,
            Clock clock,
            int threshold,
            long cooldownMinutes
    ) {
        return new AlertService(
                new AlertProperties("https://hooks.slack.test/services/test", threshold, cooldownMinutes),
                notifier,
                clock
        );
    }

    private static class CapturingSlackNotifier extends SlackNotifier {
        private final List<String> messages = new ArrayList<>();

        private CapturingSlackNotifier() {
            super(RestClient.builder().build(), new AlertProperties("", 5, 10));
        }

        @Override
        public void send(String text) {
            messages.add(text);
        }
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
