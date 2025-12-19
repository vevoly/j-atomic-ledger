package io.github.vevoly.example.wallet.config;

import io.github.vevoly.example.wallet.strategy.OddEvenRoutingStrategy;
import io.github.vevoly.ledger.api.RoutingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
//@Configuration
public class CustomStrategyConfig {

    @Bean
    public RoutingStrategy customRoutingStrategy() {
        log.warn("🔥🔥🔥 J-ATOMIC-LEDGER: 正在使用用户自定义的 [奇偶路由] 策略！");
        return new OddEvenRoutingStrategy();
    }
}
