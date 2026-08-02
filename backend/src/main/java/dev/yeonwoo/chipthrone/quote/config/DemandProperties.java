package dev.yeonwoo.chipthrone.quote.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chipthrone.demand")
public record DemandProperties(
        boolean enabled,
        @Positive long livePollDelayMs,
        @Positive long closedPollDelayMs,
        @Positive long schedulerTickMs,
        @Min(0) long subscriptionGraceMs,
        @Positive long staleAfterMs,
        @Positive int maxSymbolsPerConnection
) {
}
