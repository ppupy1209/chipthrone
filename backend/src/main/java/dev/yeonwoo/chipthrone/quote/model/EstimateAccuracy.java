package dev.yeonwoo.chipthrone.quote.model;

import java.math.BigDecimal;

/**
 * 확정 종가와 같은 시점의 Hyperliquid 추정가를 맞대어 계산한 실제 괴리율.
 *
 * @param closeDate   금융위원회 확정 종가 기준일(yyyy-MM-dd)
 * @param estimateKrw 그날 정규장 마감 시각의 Hyperliquid 추정가(원)
 * @param divergencePct 확정 종가 대비 추정가 괴리율(%)
 */
public record EstimateAccuracy(
        String code,
        String closeDate,
        BigDecimal estimateKrw,
        BigDecimal divergencePct
) {
}
