package dev.yeonwoo.chipthrone.quote.config;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chipthrone.quote")
public record QuoteProperties(
        @NotBlank String dex,
        @Valid @NotEmpty List<Asset> assets
) {
    public record Asset(
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank String symbol,
            @Positive long sharesOutstanding,
            Market market,
            String exchange
    ) {
        public Asset(String code, String name, String symbol, long sharesOutstanding, Market market) {
            this(code, name, symbol, sharesOutstanding, market, "");
        }

        @ConstructorBinding
        public Asset {
            market = market == null ? Market.KRX : market;
            exchange = exchange == null ? "" : exchange;
        }

    }

    public enum Market {
        KRX,
        US
    }
}
