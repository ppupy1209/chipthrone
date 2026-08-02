package dev.yeonwoo.chipthrone.quote.client;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote;
import dev.yeonwoo.chipthrone.quote.service.QuoteMetrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class KoreaEximExchangeRateClient implements ExchangeRateClient {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient;
    private final QuoteMetrics metrics;
    private final Clock clock;
    private final String url;
    private final String authKey;

    public KoreaEximExchangeRateClient(
            RestClient restClient,
            QuoteMetrics metrics,
            Clock clock,
            @Value("${chipthrone.source.korea-exim-url:https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON}") String url,
            @Value("${chipthrone.source.korea-exim-auth-key:}") String authKey
    ) {
        this.restClient = restClient;
        this.metrics = metrics;
        this.clock = clock;
        this.url = url;
        this.authKey = authKey;
    }

    @Override
    public boolean enabled() {
        return !authKey.isBlank();
    }

    @Override
    public ExchangeRateQuote fetchUsdKrw() {
        return fetchUsdKrw(LocalDate.now(clock.withZone(KST)));
    }

    @Override
    public ExchangeRateQuote fetchUsdKrw(LocalDate date) {
        if (!enabled()) {
            throw new IllegalStateException("Korea Eximbank API key is not configured");
        }
        for (int day = 0; day < 7; day++) {
            ExchangeRateQuote quote = fetch(date.minusDays(day));
            if (quote != null) {
                return quote;
            }
        }
        throw new IllegalStateException("Korea Eximbank returned no USD rate for the 7 days up to " + date);
    }

    private ExchangeRateQuote fetch(LocalDate date) {
        metrics.externalCall("korea_eximbank", "usd_krw");
        JsonNode response = restClient.get()
                .uri(UriComponentsBuilder.fromUriString(url)
                        .queryParam("authkey", authKey)
                        .queryParam("searchdate", date.format(BASIC_DATE))
                        .queryParam("data", "AP01")
                        .build()
                        .encode()
                        .toUri())
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !response.isArray()) {
            throw new IllegalStateException("Unexpected Korea Eximbank exchange-rate response");
        }
        return StreamSupport.stream(response.spliterator(), false)
                .filter(item -> "USD".equals(item.path("cur_unit").asText()))
                .findFirst()
                .map(item -> new ExchangeRateQuote(
                        new BigDecimal(item.path("deal_bas_r").asText().replace(",", "")),
                        date.toString(),
                        "KOREA_EXIMBANK",
                        clock.instant()
                ))
                .orElse(null);
    }
}
