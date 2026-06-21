package dev.yeonwoo.chipthrone.quote.model;

import java.time.Instant;

public record KisAccessToken(String value, Instant expiresAt) {
}
