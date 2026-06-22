package dev.yeonwoo.chipthrone.quote.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.yeonwoo.chipthrone.quote.client.KisMarketDataClient;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.InvestOpinionReport;
import dev.yeonwoo.chipthrone.quote.model.KisClosingPrice;
import dev.yeonwoo.chipthrone.quote.model.KisStockQuote;
import dev.yeonwoo.chipthrone.quote.web.OpinionsResponse;

import org.junit.jupiter.api.Test;

class InvestOpinionServiceTest {

    @Test
    void aggregatesLatestPerBrokerIntoConsensus() {
        Map<String, List<InvestOpinionReport>> data = Map.of(
                "005930", List.of(
                        report("2026-06-18", "하나증권", "매수", "480000"),
                        report("2026-05-01", "하나증권", "중립", "400000"),
                        report("2026-06-17", "유진투자", "강력매수", "560000"),
                        report("2026-06-10", "메리츠", "매도", "300000")
                ),
                "000660", List.of()
        );
        InvestOpinionService service = new InvestOpinionService(
                new StubKisClient(data),
                properties(),
                Clock.fixed(Instant.parse("2026-06-22T01:00:00Z"), ZoneOffset.UTC)
        );

        OpinionsResponse res = service.currentOpinions();

        assertThat(res.asOf()).isEqualTo("2026-06-22");
        assertThat(res.stocks()).hasSize(2);

        OpinionsResponse.Stock samsung = res.stocks().getFirst();
        assertThat(samsung.code()).isEqualTo("005930");
        assertThat(samsung.name()).isEqualTo("삼성전자");

        OpinionsResponse.Consensus c = samsung.consensus();
        assertThat(c.institutionCount()).isEqualTo(3);
        assertThat(c.buy()).isEqualTo(2);
        assertThat(c.hold()).isZero();
        assertThat(c.sell()).isEqualTo(1);
        assertThat(c.avgTargetPrice()).isEqualTo(446667.0);
        assertThat(c.score()).isEqualTo(3.67);

        assertThat(samsung.reports()).hasSize(3);
        assertThat(samsung.reports().getFirst().date()).isEqualTo("2026-06-18");
        assertThat(samsung.reports().getFirst().broker()).isEqualTo("하나증권");

        OpinionsResponse.Stock hynix = res.stocks().get(1);
        assertThat(hynix.consensus().institutionCount()).isZero();
        assertThat(hynix.reports()).isEmpty();
    }

    private InvestOpinionReport report(String date, String broker, String opinion, String target) {
        return new InvestOpinionReport("005930", date, broker, opinion, "", opinion, new BigDecimal(target));
    }

    private QuoteProperties properties() {
        return new QuoteProperties(
                3000,
                false,
                "xyz",
                1450,
                List.of(
                        new QuoteProperties.Asset("005930", "삼성전자", "xyz:SMSN", 5_919_637_922L),
                        new QuoteProperties.Asset("000660", "SK하이닉스", "xyz:SKHX", 728_002_365L)
                )
        );
    }

    private record StubKisClient(Map<String, List<InvestOpinionReport>> data) implements KisMarketDataClient {
        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Optional<KisStockQuote> fetchCurrentStockQuote(String code, String marketDivisionCode) {
            return Optional.empty();
        }

        @Override
        public Optional<KisClosingPrice> fetchClosingPrice(String code) {
            return Optional.empty();
        }

        @Override
        public List<InvestOpinionReport> fetchInvestOpinions(String code) {
            return data.getOrDefault(code, List.of());
        }
    }
}
