package dev.yeonwoo.chipthrone.quote.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class UsMarketHoursTest {

    private final UsMarketHours hours = new UsMarketHours();

    @Test
    void handlesNewYorkSessionAndDaylightSavingTime() {
        assertThat(hours.isOpen(Instant.parse("2026-01-05T15:00:00Z"))).isTrue();
        assertThat(hours.isOpen(Instant.parse("2026-06-22T14:00:00Z"))).isTrue();
        assertThat(hours.isOpen(Instant.parse("2026-06-22T20:00:00Z"))).isFalse();
        assertThat(hours.isOpen(Instant.parse("2026-06-21T14:00:00Z"))).isFalse();
    }
}
