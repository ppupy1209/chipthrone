package dev.yeonwoo.chipthrone.quote;

public record StockQuote(
        String code,
        String name,
        double priceKrw,
        double priceUsd,
        double changePct,
        String changeBasis,
        long sharesOutstanding,
        double marketCap,
        Double regularClose,
        Double nxtClose
) {
}
