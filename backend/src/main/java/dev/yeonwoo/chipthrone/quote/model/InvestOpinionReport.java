package dev.yeonwoo.chipthrone.quote.model;

import java.math.BigDecimal;

/** KIS 종목투자의견 1건(증권사 1개의 발표). */
public record InvestOpinionReport(
        String code,
        String date,
        String broker,
        String opinion,
        String opinionCode,
        String prevOpinion,
        BigDecimal targetPrice
) {
}
