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
    // URL: RL: http://localhost:8080/bench?count=100000&threads=50&users=4
    @GetMapping("/bench")
    public String benchmark(@RequestParam(value = "count", defaultValue = "10000") int count,
                            @RequestParam(value = "threads", defaultValue = "10") int threads,
                            @RequestParam(value = "users", defaultValue = "4") int userCount) {

        // 1. 定义计数器和开始时间
        AtomicInteger completedCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        // 2. 创建发送线程池
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        System.out.println(String.format(">>> 压测开始，总请求: %d, 并发线程: %d, 模拟用户数: %d ...", count, threads, userCount));

        for (int i = 0; i < count; i++) {
            // 通过取模，让请求均匀分布在 0, 1, 2, 3 ... userCount-1 这些用户上
            // 这样不同的用户会被路由到不同的 Partition (Disruptor线程)
            long currentUserId = i % userCount;

            executor.submit(() -> {
                try {
                    TradeCommand cmd = new TradeCommand();
                    cmd.setTxId(UUID.randomUUID().toString());
                    cmd.setUserId(currentUserId);
                    cmd.setAmount(BigDecimal.ONE); // 每次加1元

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

                            log.warn("=========================================");
                            log.warn("🚀 压测完成！(收到所有结果)");
                            log.warn("总耗时: {} ms", cost);
                            log.warn("总请求: {}", count);
                            log.warn("用户数: {}", userCount);
                            log.warn("真实 TPS: {}", tps);

                            // 5. 验证总金额 (遍历所有测试用户)
                            long totalMemBalance = 0;
                            for (long uid = 0; uid < userCount; uid++) {
                                // 根据路由键找到对应的分片状态
                                WalletState state = engine.getStateBy(String.valueOf(uid));
                                // 累加余额
                                totalMemBalance += state.getBalances().getOrDefault(uid, 0L);
                            }

                            log.warn("所有用户总余额 (内存值): {}", totalMemBalance);
                            log.warn("所有用户总余额 (数据库值): {} ", MoneyUtils.toDb(totalMemBalance));
                            log.warn("预期总余额: {}", (long) count * 10000L); // 请求数 * 单次金额

                            if (totalMemBalance == (long) count * 10000L) {
                                log.warn("✅ 资金对账成功！");
                            } else {
                                log.error("❌ 资金对账失败！差额: {}", totalMemBalance - ((long) count * 10000L));
                            }
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
