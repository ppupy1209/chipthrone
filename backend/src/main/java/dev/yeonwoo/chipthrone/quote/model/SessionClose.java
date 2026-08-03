package dev.yeonwoo.chipthrone.quote.model;

import java.math.BigDecimal;

/**
 * 직전 미국 정규장 마감(16:00 ET) 시점의 Hyperliquid 추정가.
 *
 * <p>미국 종목은 금융위원회 같은 확정 종가 소스가 없다. 대신 정규장 마감 시각의 perp 가격을 기준가로 쓴다.
 * 그 시간대 perp은 실제 시세를 0.03~0.09%로 따라가므로(2026-08-03 실측) 등락률 기준으로 쓰기에 충분하다.
 *
 * @param closeDate 마감 기준일(ET 기준 yyyy-MM-dd)
 * @param closeUsd  그 시점의 추정가(USD)
 */
public record SessionClose(
        String code,
        String closeDate,
        BigDecimal closeUsd
) {
}
