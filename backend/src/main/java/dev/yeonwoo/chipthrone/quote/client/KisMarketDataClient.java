package dev.yeonwoo.chipthrone.quote.client;

import java.util.List;
import java.util.Optional;

import dev.yeonwoo.chipthrone.quote.model.InvestOpinionReport;
import dev.yeonwoo.chipthrone.quote.model.KisClosingPrice;
import dev.yeonwoo.chipthrone.quote.model.KisStockQuote;

public interface KisMarketDataClient {

    boolean enabled();

    default Optional<KisStockQuote> fetchCurrentStockQuote(String code) {
        return fetchCurrentStockQuote(code, "J");
    }

    Optional<KisStockQuote> fetchCurrentStockQuote(String code, String marketDivisionCode);

    Optional<KisClosingPrice> fetchClosingPrice(String code);

    /** KIS 종목투자의견(증권사별 의견·목표가) 목록. 미연동/실패 시 빈 리스트. */
    default List<InvestOpinionReport> fetchInvestOpinions(String code) {
        return List.of();
    }
}
