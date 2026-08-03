package dev.yeonwoo.chipthrone.quote.client;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote;
import dev.yeonwoo.chipthrone.quote.service.QuoteMetrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Pyth Network Hermes의 FX.USD/KRW 피드.
 *
 * <p>Hyperliquid HIP-3 오라클이 원화 환산에 쓰는 것과 같은 피드다. perp 가격을 만들 때 쓰인 환율과
 * 우리가 되돌릴 때 쓰는 환율을 일치시켜야 환산에서 오차가 새로 생기지 않는다.
 *
 * <p>키가 필요 없는 공개 REST다. 응답의 {@code price × 10^expo}가 실제 값이다.
 */
@Component
public class PythExchangeRateClient implements ExchangeRateClient {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 외환시장이 닫힌 시각을 요청하면 404다. 주말·연휴를 건너뛰도록 점점 크게 되짚는다. */
    private static final List<Duration> LOOKBACK_STEPS = List.of(
            Duration.ZERO,
            Duration.ofHours(1),
            Duration.ofHours(24),
            Duration.ofHours(48),
            Duration.ofHours(72)
    );

    private final RestClient restClient;
    private final QuoteMetrics metrics;
    private final Clock clock;
    private final String hermesUrl;
    private final String feedId;

    public PythExchangeRateClient(
            RestClient restClient,
            QuoteMetrics metrics,
            Clock clock,
            @Value("${chipthrone.source.pyth-hermes-url:https://hermes.pyth.network}") String hermesUrl,
            @Value("${chipthrone.source.pyth-usd-krw-feed-id:"
                    + "e539120487c29b4defdf9a53d337316ea022a2688978a468f9efd847201be7e3}") String feedId
    ) {
        this.restClient = restClient;
        this.metrics = metrics;
        this.clock = clock;
        this.hermesUrl = hermesUrl;
        this.feedId = feedId;
    }

    /** 키가 없는 공개 API라 항상 켜져 있다. 실패 시 폴백은 호출자가 처리한다. */
    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public ExchangeRateQuote fetchUsdKrw() {
        return parse(request(hermesUrl + "/v2/updates/price/latest?ids[]=" + feedId + "&parsed=true&encoding=hex"));
    }

    @Override
    public ExchangeRateQuote fetchUsdKrw(Instant at) {
        RestClientResponseException lastMiss = null;
        for (Duration step : LOOKBACK_STEPS) {
            long epochSecond = at.minus(step).getEpochSecond();
            try {
                return parse(request(hermesUrl + "/v2/updates/price/" + epochSecond
                        + "?ids[]=" + feedId + "&parsed=true&encoding=hex"));
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode().value() != 404) {
                    throw ex;
                }
                lastMiss = ex; // 그 시각에 시세가 없다. 더 뒤로 되짚는다.
            }
        }
        throw new IllegalStateException("Pyth has no USD/KRW price near " + at, lastMiss);
    }

    private JsonNode request(String uri) {
        metrics.externalCall("pyth", "usd_krw");
        return restClient.get().uri(uri).retrieve().body(JsonNode.class);
    }

    private ExchangeRateQuote parse(JsonNode response) {
        JsonNode price = response == null ? null : response.path("parsed").path(0).path("price");
        if (price == null || price.isMissingNode() || !price.hasNonNull("price")) {
            throw new IllegalStateException("Unexpected Pyth USD/KRW response");
        }
        // price는 정수 문자열, expo는 음수 지수. 1429.51017 = 142951017 × 10^-5
        BigDecimal rate = new BigDecimal(price.path("price").asText()).scaleByPowerOfTen(price.path("expo").asInt());
        if (rate.signum() <= 0) {
            throw new IllegalStateException("Non-positive Pyth USD/KRW rate: " + rate);
        }
        Instant publishedAt = Instant.ofEpochSecond(price.path("publish_time").asLong());
        return new ExchangeRateQuote(
                rate,
                publishedAt.atZone(KST).toLocalDate().toString(),
                "PYTH",
                clock.instant()
        );
    }
}
