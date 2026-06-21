package dev.yeonwoo.chipthrone.quote;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chipthrone.quote")
public record QuoteProperties(
        @Positive long pollDelayMs,
        boolean pollingEnabled,
        @NotBlank String dex,
        @Positive double initialFxRate,
        @Valid @NotEmpty List<Asset> assets
) {
    public record Asset(
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank String symbol,
            @Positive long sharesOutstanding
    ) {
    }
}
