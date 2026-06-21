package dev.yeonwoo.chipthrone.quote;

import java.time.Instant;

record KisAccessToken(String value, Instant expiresAt) {
}
