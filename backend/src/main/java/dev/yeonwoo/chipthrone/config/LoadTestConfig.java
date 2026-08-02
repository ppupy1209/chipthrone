package dev.yeonwoo.chipthrone.config;

import java.time.Instant;

import dev.yeonwoo.chipthrone.quote.model.MarketMode;
import dev.yeonwoo.chipthrone.quote.service.MarketModeService;
import dev.yeonwoo.chipthrone.quote.service.UsMarketHours;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("loadtest")
public class LoadTestConfig {

    @Bean
    @Primary
    MarketModeService loadTestMarketModeService(
            @Value("${chipthrone.loadtest.market-mode:REGULAR}") MarketMode mode
    ) {
        return new MarketModeService() {
            @Override
            public MarketMode determine(Instant at) {
                return mode;
            }

            @Override
            public boolean isNoTradeBreak(Instant at) {
                return false;
            }
        };
    }

    @Bean
    @Primary
    UsMarketHours loadTestUsMarketHours(
            @Value("${chipthrone.loadtest.us-market-open:false}") boolean open
    ) {
        return new UsMarketHours() {
            @Override
            public boolean isOpen(Instant at) {
                return open;
            }
        };
    }
}
