package io.github.vevoly.ledger.example.wallet.standalone.config;

import io.github.vevoly.ledger.api.RoutingStrategy;
import io.github.vevoly.ledger.example.wallet.standalone.strategy.OddEvenRoutingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
//@Configuration
public class CustomStrategyConfig {

    @Bean
    public RoutingStrategy customRoutingStrategy() {
        log.warn("🔥🔥🔥 J-ATOMIC-LEDGER: Using custom strategy: Odd-Even Strategy！");
        return new OddEvenRoutingStrategy();
    }
}
