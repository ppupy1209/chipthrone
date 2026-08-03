package dev.yeonwoo.chipthrone.quote.model;

public record StockQuote(
        String code,
        String name,
        double priceKrw,
        double priceUsd,
        double changePct,
        long sharesOutstanding,
        double marketCap,
        Double officialMarketCap,
        Double regularClose,
        String regularCloseDate,
        Double high,
        Double nxtClose,
        String nxtCloseDate,
        String market,
        String source,
        String status,
        /** 확정 종가와 같은 시점의 추정가(원). 산출 불가 시 null */
        Double officialCloseEstimate,
        /** 확정 종가 대비 추정가 괴리율(%). 산출 불가 시 null */
        Double officialDivergencePct,
        /** 미국 종목 전용. 직전 정규장 마감(16:00 ET) 시점 추정가(USD). 국내 종목·산출 불가 시 null */
        Double sessionCloseUsd,
        /** 미국 종목 전용. 그 마감 기준일(ET 기준 yyyy-MM-dd) */
        String sessionCloseDate
) {
    public StockQuote(
            String code,
            String name,
            double priceKrw,
            double priceUsd,
            double changePct,
            long sharesOutstanding,
            double marketCap,
            Double regularClose,
            String regularCloseDate,
            Double high,
            Double nxtClose,
            String nxtCloseDate
    ) {
        this(code, name, priceKrw, priceUsd, changePct, sharesOutstanding, marketCap, null,
                regularClose, regularCloseDate, high, nxtClose, nxtCloseDate,
                "KRX", "HYPERLIQUID", "ESTIMATE", null, null, null, null);
    }
}
