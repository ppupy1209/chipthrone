package dev.yeonwoo.chipthrone.quote.web;

import java.util.List;

/** GET /api/opinions 응답 — 종목별 증권사 투자의견 컨센서스 + 리포트 목록. */
public record OpinionsResponse(String asOf, List<Stock> stocks) {

    public record Stock(String code, String name, Consensus consensus, List<Report> reports) {
    }

    public record Consensus(
            Double avgTargetPrice,
            int institutionCount,
            int buy,
            int hold,
            int sell,
            Double score
    ) {
    }

    public record Report(
            String date,
            String broker,
            String opinion,
            String prevOpinion,
            Double targetPrice
    ) {
    }
}
