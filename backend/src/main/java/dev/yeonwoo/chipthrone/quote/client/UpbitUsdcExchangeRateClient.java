package dev.yeonwoo.chipthrone.quote.client;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import com.fasterxml.jackson.databind.JsonNode;
import dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote;
import dev.yeonwoo.chipthrone.quote.service.QuoteMetrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 업비트 KRW-USDC 최우선 매수 호가와 매도 호가의 중간값을 USD/KRW의 근사값으로 사용하는 내부 환산 클라이언트.
 *
 * <p>인증키가 필요 없는 시세 조회 API를 서버에서만 호출한다. 원본 응답과 환율은 외부 API에
 * 노출하지 않고 Hyperliquid 달러 추정가를 원화로 환산하는 데만 사용한다.
 */
@Component
public class UpbitUsdcExchangeRateClient implements ExchangeRateClient {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration MAX_CURRENT_AGE = Duration.ofMinutes(15);
    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(1);

    private final RestClient restClient;
    private final QuoteMetrics metrics;
    private final Clock clock;
    private final String apiUrl;
    private final String market;

    public UpbitUsdcExchangeRateClient(
            RestClient restClient,
            QuoteMetrics metrics,
            Clock clock,
            @Value("${chipthrone.source.upbit-api-url:https://api.upbit.com}") String apiUrl,
            @Value("${chipthrone.source.upbit-market:KRW-USDC}") String market
    ) {
        this.restClient = restClient;
        this.metrics = metrics;
        this.clock = clock;
        this.apiUrl = apiUrl;
        this.market = market;
    }

    /** 인증키가 필요 없는 공개 시세 조회 API라 항상 켜져 있다. */
    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public ExchangeRateQuote fetchUsdKrw() {
        URI uri = UriComponentsBuilder.fromUriString(apiUrl)
                .path("/v1/orderbook")
                .queryParam("markets", market)
                .queryParam("count", 1)
                .build()
                .encode()
                .toUri();
        JsonNode orderbook = first(request(uri, "usdc_krw_orderbook"), "orderbook");
        requireMarket(orderbook);
        Instant quotedAt = Instant.ofEpochMilli(orderbook.path("timestamp").asLong());
        validateCurrentTimestamp(quotedAt);
        return quote(orderbookMidpoint(orderbook), quotedAt);
    }

    private JsonNode request(URI uri, String operation) {
        metrics.externalCall("upbit", operation);
        return restClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode first(JsonNode response, String kind) {
        JsonNode first = response == null || !response.isArray() ? null : response.path(0);
        if (first == null || first.isMissingNode() || first.isNull()) {
            throw new IllegalStateException("Unexpected Upbit KRW-USDC " + kind + " response");
        }
        return first;
    }

    private void requireMarket(JsonNode node) {
        if (!market.equals(node.path("market").asText())) {
            throw new IllegalStateException("Unexpected Upbit market: " + node.path("market").asText());
        }
    }

    private BigDecimal rate(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            throw new IllegalStateException("Upbit KRW-USDC response has no " + field);
        }
        BigDecimal rate = node.path(field).decimalValue();
        if (rate.signum() <= 0) {
            throw new IllegalStateException("Non-positive Upbit KRW-USDC rate: " + rate);
        }
        return rate;
    }

    private BigDecimal orderbookMidpoint(JsonNode orderbook) {
        JsonNode units = orderbook.path("orderbook_units");
        JsonNode best = units.isArray() ? units.path(0) : null;
        if (best == null || best.isMissingNode() || best.isNull()) {
            throw new IllegalStateException("Unexpected Upbit KRW-USDC orderbook response");
        }
        BigDecimal bid = rate(best, "bid_price");
        BigDecimal ask = rate(best, "ask_price");
        if (bid.compareTo(ask) > 0) {
            throw new IllegalStateException("Crossed Upbit KRW-USDC orderbook");
        }
        return bid.add(ask).divide(BigDecimal.valueOf(2));
    }

    private void validateCurrentTimestamp(Instant tradedAt) {
        Instant now = clock.instant();
        if (tradedAt.isAfter(now.plus(MAX_FUTURE_SKEW))
                || Duration.between(tradedAt, now).compareTo(MAX_CURRENT_AGE) > 0) {
            throw new IllegalStateException("Stale Upbit KRW-USDC orderbook: " + tradedAt);
        }
    }

    private ExchangeRateQuote quote(BigDecimal rate, Instant tradedAt) {
        return new ExchangeRateQuote(
                rate,
                tradedAt.atZone(KST).toLocalDate().toString(),
                "UPBIT_USDC",
                clock.instant()
        );
    }
}
