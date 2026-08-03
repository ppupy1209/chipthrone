package dev.yeonwoo.chipthrone.quote.client;

import java.time.Instant;

import dev.yeonwoo.chipthrone.quote.model.ExchangeRateQuote;

public interface ExchangeRateClient {

    boolean enabled();

    ExchangeRateQuote fetchUsdKrw();

    /**
     * 지정 "시각"의 환율. 날짜가 아니라 시각인 이유는 괴리율이 같은 순간의 두 값만 비교하기 때문이다.
     * 정규장 마감 캔들과 그날 종일 고정된 환율을 맞대면 그 사이 환율 변동이 오차에 섞인다.
     * 외환시장이 닫혀 그 시각 값이 없으면 직전 값으로 대체된다.
     */
    ExchangeRateQuote fetchUsdKrw(Instant at);
}
