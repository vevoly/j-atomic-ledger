package io.github.vevoly.example.wallet.api;

import io.github.vevoly.example.wallet.domain.TradeCommand;
import io.github.vevoly.example.wallet.domain.WalletState;
import io.github.vevoly.example.wallet.entity.UserWalletEntity;
import io.github.vevoly.ledger.api.utils.MoneyUtils;
import io.github.vevoly.ledger.core.LedgerEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
public class BenchController {

    @Autowired
    private LedgerEngine<WalletState, TradeCommand, UserWalletEntity> engine;

    // 压测接口：模拟并发请求
    // URL: http://localhost:8080/bench?count=10000&threads=10
    @GetMapping("/bench")
    public String benchmark(@RequestParam(value = "count", defaultValue = "10000") int count,
                            @RequestParam(value = "threads", defaultValue = "10") int threads) {

        // 1. 定义计数器和开始时间
        AtomicInteger completedCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        // 2. 创建发送线程池
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        System.out.println(">>> 压测开始，计划发送 " + count + " 条请求...");

        for (int i = 0; i < count; i++) {
            executor.submit(() -> {
                try {
                    TradeCommand cmd = new TradeCommand();
                    cmd.setTxId(UUID.randomUUID().toString());
                    cmd.setUserId(1L); // 依然只压测同一个用户，测试热点性能
                    cmd.setAmount(BigDecimal.ONE);

                    // 3. 创建 Future 并设置回调
                    CompletableFuture<Object> future = new CompletableFuture<>();

                    // 注册回调：当 Disruptor 处理完这个 future 后，自动执行这里
                    // thenAccept (成功) / exceptionally (失败) 都会触发计数
                    future.whenComplete((res, ex) -> {
                        // 计数器 +1
                        int current = completedCount.incrementAndGet();

                        // 4. 判断是否是最后一条
                        if (current == count) {
                            long endTime = System.currentTimeMillis();
                            long cost = endTime - startTime;
                            // 防止除以0
                            long safeCost = cost == 0 ? 1 : cost;
                            long tps = (long) count * 1000 / safeCost;

                            // 获取内存状态对象
                            WalletState state = engine.getState();
                            // 获取用户 1 的余额 (默认为0，防止空指针)
                            long finalMemBalance = state.getBalances().getOrDefault(1L, 0L);

                            log.warn("=========================================");
                            log.warn("🚀 压测完成！(收到所有结果)");
                            log.warn("总耗时: {} ms", cost);
                            log.warn("总请求: {}", count);
                            log.warn("真实 TPS: {}", tps);
                            log.warn("最终余额: " + MoneyUtils.toDb(finalMemBalance));
                            log.warn("=========================================");
                        }
                    });

                    cmd.setFuture(future);
                    engine.submit(cmd);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();

        // HTTP 接口立刻返回，不阻塞浏览器，结果看控制台日志
        return "压测请求已全部后台提交，请关注控制台的【🚀 压测完成】日志...";
    }
}
