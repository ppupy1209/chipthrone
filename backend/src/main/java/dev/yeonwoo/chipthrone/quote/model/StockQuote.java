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
        Double nxtClose,
        String nxtCloseDate
) {
}
