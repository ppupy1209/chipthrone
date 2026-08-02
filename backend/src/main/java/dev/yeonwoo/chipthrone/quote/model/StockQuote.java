package dev.yeonwoo.chipthrone.quote.model;

public record StockQuote(
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
        String nxtCloseDate,
        String market,
        String source,
        String status
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
        this(code, name, priceKrw, priceUsd, changePct, sharesOutstanding, marketCap,
                regularClose, regularCloseDate, high, nxtClose, nxtCloseDate,
                "KRX", "HYPERLIQUID", "ESTIMATE");
    }
}
