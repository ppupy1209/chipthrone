package dev.yeonwoo.chipthrone.quote.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.yeonwoo.chipthrone.quote.client.KisMarketDataClient;
import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;
import dev.yeonwoo.chipthrone.quote.model.InvestOpinionReport;
import dev.yeonwoo.chipthrone.quote.web.OpinionsResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InvestOpinionService {

    private static final Logger log = LoggerFactory.getLogger(InvestOpinionService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final KisMarketDataClient kisMarketDataClient;
    private final QuoteProperties properties;
    private final Clock clock;
    private final Map<String, List<InvestOpinionReport>> cache = new ConcurrentHashMap<>();
    private final Map<String, LocalDate> cachedOn = new ConcurrentHashMap<>();

    public InvestOpinionService(
            KisMarketDataClient kisMarketDataClient,
            QuoteProperties properties,
            Clock clock
    ) {
        this.kisMarketDataClient = kisMarketDataClient;
        this.properties = properties;
        this.clock = clock;
    }

    public OpinionsResponse currentOpinions() {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        List<OpinionsResponse.Stock> stocks = properties.assets().stream()
                .map(asset -> toStock(asset, today))
                .toList();
        return new OpinionsResponse(today.toString(), stocks);
    }

    private OpinionsResponse.Stock toStock(QuoteProperties.Asset asset, LocalDate today) {
        List<InvestOpinionReport> latest = latestPerBroker(reportsFor(asset.code(), today));

        int buy = 0;
        int hold = 0;
        int sell = 0;
        List<Double> targets = new ArrayList<>();
        double scoreSum = 0;
        int scoreCount = 0;
        for (InvestOpinionReport r : latest) {
            int side = classify(r.opinion());
            if (side > 0) {
                buy++;
            } else if (side < 0) {
                sell++;
            } else {
                hold++;
            }
            if (r.targetPrice() != null && r.targetPrice().signum() > 0) {
                targets.add(r.targetPrice().doubleValue());
            }
            Integer s = score(r.opinion());
            if (s != null) {
                scoreSum += s;
                scoreCount++;
            }
        }

        Double avgTarget = targets.isEmpty()
                ? null
                : (double) Math.round(targets.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        Double consensusScore = scoreCount == 0
                ? null
                : Math.round(scoreSum / scoreCount * 100.0) / 100.0;

        OpinionsResponse.Consensus consensus = new OpinionsResponse.Consensus(
                avgTarget, latest.size(), buy, hold, sell, consensusScore);

        List<OpinionsResponse.Report> reports = latest.stream()
                .map(r -> new OpinionsResponse.Report(
                        r.date(),
                        r.broker(),
                        r.opinion(),
                        r.prevOpinion(),
                        r.targetPrice() == null ? null : r.targetPrice().doubleValue()))
                .toList();

        return new OpinionsResponse.Stock(asset.code(), asset.name(), consensus, reports);
    }

    /** 같은 증권사의 여러 발표 중 최신 1건만 남기고, 발표일 내림차순 정렬. */
    private List<InvestOpinionReport> latestPerBroker(List<InvestOpinionReport> reports) {
        Map<String, InvestOpinionReport> byBroker = new LinkedHashMap<>();
        reports.stream()
                .sorted(Comparator.comparing(InvestOpinionReport::date).reversed())
                .forEach(r -> byBroker.putIfAbsent(broker(r), r));
        return byBroker.values().stream()
                .sorted(Comparator.comparing(InvestOpinionReport::date).reversed())
                .toList();
    }

    private String broker(InvestOpinionReport r) {
        return r.broker() == null ? "" : r.broker();
    }

    private List<InvestOpinionReport> reportsFor(String code, LocalDate today) {
        if (today.equals(cachedOn.get(code)) && cache.containsKey(code)) {
            return cache.get(code);
        }
        try {
            List<InvestOpinionReport> fetched = kisMarketDataClient.fetchInvestOpinions(code);
            if (!fetched.isEmpty()) {
                cache.put(code, fetched);
                cachedOn.put(code, today);
                return fetched;
            }
            return cache.getOrDefault(code, List.of());
        } catch (RuntimeException ex) {
            log.warn("Failed to fetch KIS invest opinions for code {}. Using cached/empty.", code, ex);
            return cache.getOrDefault(code, List.of());
        }
    }

    /** 매수계열 +1 / 매도계열 -1 / 중립 0. */
    private int classify(String opinion) {
        String o = opinion == null ? "" : opinion.toLowerCase();
        if (o.contains("매수") || o.contains("buy")) {
            return 1;
        }
        if (o.contains("매도") || o.contains("sell")) {
            return -1;
        }
        return 0;
    }

    /** 강력매도 1 … 강력매수 5. 분류 불가 시 null. */
    private Integer score(String opinion) {
        String o = opinion == null ? "" : opinion.toLowerCase();
        if (o.contains("강력매수") || o.contains("strong buy")) {
            return 5;
        }
        if (o.contains("강력매도") || o.contains("strong sell")) {
            return 1;
        }
        if (o.contains("매수") || o.contains("buy")) {
            return 4;
        }
        if (o.contains("매도") || o.contains("sell")) {
            return 2;
        }
        if (o.contains("중립") || o.contains("hold") || o.contains("neutral")) {
            return 3;
        }
        return null;
    }
}
