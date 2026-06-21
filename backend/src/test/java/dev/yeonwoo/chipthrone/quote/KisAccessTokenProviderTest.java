package dev.yeonwoo.chipthrone.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class KisAccessTokenProviderTest {

    @Test
    void reusesCachedTokenUntilRefreshMargin() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-22T00:00:00Z"));
        StubTokenClient tokenClient = new StubTokenClient(clock);
        KisAccessTokenProvider provider = new KisAccessTokenProvider(enabledProperties(), tokenClient, clock);

        assertThat(provider.accessToken()).isEqualTo("token-1");
        clock.advance(Duration.ofHours(23));
        assertThat(provider.accessToken()).isEqualTo("token-1");
        clock.advance(Duration.ofMinutes(56));
        assertThat(provider.accessToken()).isEqualTo("token-2");
        assertThat(tokenClient.calls).isEqualTo(2);
    }

    @Test
    void rejectsTokenAccessWhenKisIsDisabled() {
        KisAccessTokenProvider provider = new KisAccessTokenProvider(
                new KisProperties("https://example.test", "", "secret"),
                new StubTokenClient(new MutableClock(Instant.parse("2026-06-22T00:00:00Z"))),
                Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneId.of("UTC"))
        );

        assertThatThrownBy(provider::accessToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KIS is disabled");
    }

    private KisProperties enabledProperties() {
        return new KisProperties("https://example.test", "key", "secret");
    }

    private static class StubTokenClient implements KisTokenClient {
        private final Clock clock;
        private int calls;

        private StubTokenClient(Clock clock) {
            this.clock = clock;
        }

        @Override
        public KisAccessToken issueToken() {
            calls++;
            return new KisAccessToken("token-" + calls, clock.instant().plus(Duration.ofHours(24)));
        }
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
