package io.github.vevoly.example.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WalletApplication {
    public static void main(String[] args) {
        // 禁用 Chronicle 的 Google Analytics 上报 / Disable Chronicle's Google Analytics reporting
        System.setProperty("chronicle.analytics.disable", "true");
        SpringApplication.run(WalletApplication.class, args);
    }
}
