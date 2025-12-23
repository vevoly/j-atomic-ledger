package io.github.vevoly.ledger.example.wallet.cluster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 集群部署请先运行 docker-compose
 */
@EnableScheduling
@EnableDiscoveryClient
@SpringBootApplication
public class WalletClusterApplication {
    public static void main(String[] args) {
        // 禁用 Chronicle 的 Google Analytics 上报 / Disable Chronicle's Google Analytics reporting
        System.setProperty("chronicle.analytics.disable", "true");
        SpringApplication.run(WalletClusterApplication.class, args);
    }
}
