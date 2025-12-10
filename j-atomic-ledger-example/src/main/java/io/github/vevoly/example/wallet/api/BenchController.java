package io.github.vevoly.example.wallet.api;

import io.github.vevoly.example.wallet.domain.TradeCommand;
import io.github.vevoly.example.wallet.domain.WalletState;
import io.github.vevoly.ledger.core.LedgerEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
public class BenchController {

    @Autowired
    private LedgerEngine<WalletState, TradeCommand> engine;

    // 压测接口：模拟并发请求
    // URL: http://localhost:8080/bench?count=10000&threads=10
    @GetMapping("/bench")
    public String benchmark(@RequestParam(value = "count", defaultValue = "10000") int count,
                            @RequestParam(value = "threads", defaultValue = "10") int threads) {

        long start = System.currentTimeMillis();
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < count; i++) {
            executor.submit(() -> {
                TradeCommand cmd = new TradeCommand();
                cmd.setTxId(UUID.randomUUID().toString());
                cmd.setUserId(1L);
                cmd.setAmount(100);
                // 这里为了单纯测吞吐，可以不 setFuture，或者 set 了但不 get
                engine.submit(cmd);
            });
        }

        executor.shutdown();
        return "压测请求已全部提交，后台处理中... (日志看控制台)";
    }
}
