package io.github.vevoly.example.wallet.api;

import io.github.vevoly.example.wallet.component.PerfMonitor;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@RestController
public class BenchController {

    @Autowired
    private PerfMonitor monitor;

    @Autowired
    private LedgerEngine<WalletState, TradeCommand, UserWalletEntity> engine;

    // 压测接口：模拟并发请求
    // URL: RL: http://localhost:8080/bench?count=100000&threads=50&users=4
    @GetMapping("/bench")
    public String benchmark(@RequestParam(value = "count", defaultValue = "1000000") int count,
                            @RequestParam(value = "threads", defaultValue = "50") int threads, // 建议设为 CPU 核心数 * 2
                            @RequestParam(value = "users", defaultValue = "16") int userCount) {

        // 0. 准备工作：统计期初余额、预计算金额
        long initialTotalBalance = 0;
        for (long uid = 0; uid < userCount; uid++) {
            WalletState state = engine.getStateBy(String.valueOf(uid));
            initialTotalBalance += state.getBalances().getOrDefault(uid, 0L);
        }
        final long startBalanceSnapshot = initialTotalBalance;
        final long amountLong = MoneyUtils.toMem(BigDecimal.ONE); // 移出循环，只算一次

        // 1. 定义计数器 (使用 LongAdder 减少 CAS 竞争)
        LongAdder completedCount = new LongAdder();
        long startTime = System.currentTimeMillis();

        // 2. 计算每个线程需要发送的请求数
        int requestsPerThread = count / threads;
        // 处理除不尽的情况，把余数补给最后一个线程
        int remainder = count % threads;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        System.out.println(String.format(">>> 极致压测开始 | 总量: %d | 线程: %d | 单线程任务量: %d | 用户数: %d",
                count, threads, requestsPerThread, userCount));

        // 3. 提交任务 (按线程分片，而不是按请求分片)
        for (int t = 0; t < threads; t++) {
            final int threadIndex = t;
            final int loopCount = (t == threads - 1) ? requestsPerThread + remainder : requestsPerThread;

            executor.submit(() -> {
                try {
                    // 预先创建 StringBuilder 减少扩容开销 (可选)
                    for (int i = 0; i < loopCount; i++) {
                        // 3.1 极速 ID 生成 (比 UUID 快 10 倍以上)
                        // 格式: T{线程ID}-{序号}，保证全局唯一
                        String txId = "T" + threadIndex + "-" + i + "-" + System.nanoTime();

                        // 3.2 均匀分布用户
                        // 使用全局唯一的逻辑序号来取模，确保分布均匀
                        long globalIndex = (long) threadIndex * requestsPerThread + i;
                        long currentUserId = globalIndex % userCount;

                        TradeCommand cmd = new TradeCommand();
                        cmd.setTxId(txId);
                        cmd.setUserId(currentUserId);
                        cmd.setAmount(amountLong);

                        CompletableFuture<Object> future = new CompletableFuture<>();

                        // 3.3 设置回调
                        future.whenComplete((res, ex) -> {
                            completedCount.increment();

                            // 检查是否全部完成
                            // 注意：LongAdder.sum() 不是实时的，但在最终一致性场景够用了
                            // 为了精准触发结束日志，这里我们可以判断是否达到目标值
                            if (completedCount.sum() == count) {
                                printResult(startTime, count, userCount, startBalanceSnapshot, amountLong);
                            }
                        });

                        cmd.setFuture(future);
                        engine.submit(cmd);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        return "压测请求已后台提交...";
    }

    private void printResult(long startTime, int count, int userCount, long startBalanceSnapshot, long amountPerTrade) {
        long endTime = System.currentTimeMillis();
        long cost = endTime - startTime;
        long safeCost = cost == 0 ? 1 : cost;
        long tps = (long) count * 1000 / safeCost;

        // 统计最终余额
        long finalTotalBalance = 0;
        for (long uid = 0; uid < userCount; uid++) {
            WalletState state = engine.getStateBy(String.valueOf(uid));
            finalTotalBalance += state.getBalances().getOrDefault(uid, 0L);
        }

        long expectedTotalBalance = startBalanceSnapshot + ((long) count * amountPerTrade);
        long roundIncreaseMoney = (long) count * amountPerTrade;

        log.warn("=========================================");
        log.warn("🚀 极致压测完成！");
        log.warn("总耗时: {} ms", cost);
        log.warn("总请求: {}", count);
        log.warn("真实 TPS: {}", tps);
        log.warn("-----------------------------------------");
        log.warn("期初余额: {}", MoneyUtils.toDb(startBalanceSnapshot));
        log.warn("本轮增量: +{}", MoneyUtils.toDb(roundIncreaseMoney));
        log.warn("预期余额: {}", MoneyUtils.toDb(expectedTotalBalance));
        log.warn("实际余额: {}", MoneyUtils.toDb(finalTotalBalance));

        if (finalTotalBalance == expectedTotalBalance) {
            log.warn("✅ 资金对账成功！(金额精确无误)");
        } else {
            log.error("❌ 资金对账失败！差额: {}", MoneyUtils.toDb(finalTotalBalance - expectedTotalBalance));
        }
        log.warn("=========================================");
    }

    @GetMapping("/bench-fast")
    public String benchThroughput(@RequestParam(value = "count", defaultValue = "10000") int count,
                                  @RequestParam(value = "threads", defaultValue = "200") int threads,
                                  @RequestParam(value = "users", defaultValue = "4") int userCount) {
        // 1. 重置计数器
        monitor.start();
        // 2. 异步提交任务 (Fire and Forget)
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < count; i++) {
            long uid = i % userCount;
            executor.submit(() -> {
                try {
                    TradeCommand cmd = new TradeCommand();
                    cmd.setTxId(UUID.randomUUID().toString());
                    cmd.setUserId(uid);
                    cmd.setAmount(MoneyUtils.toMem(BigDecimal.ONE));
                    cmd.setFuture(null);
                    engine.submit(cmd);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        executor.shutdown();

        // 3. 启动一个后台线程来监控进度
        // 这样 HTTP 接口可以先返回，不阻塞浏览器，我们在控制台看结果
        new Thread(() -> {
            System.out.println(">>> 极速压测已启动，正在后台轮询进度...");
            while (true) {
                long current = monitor.getCount();
                // 如果处理完
                if (current >= count) {
                    long endTime = System.currentTimeMillis();
                    long cost = endTime - monitor.getStartTime();
                    long safeCost = cost == 0 ? 1 : cost;
                    long tps = (long) count * 1000 / safeCost;

                    System.out.println("=========================================");
                    System.out.println("🚀 极速模式压测完成！");
                    System.out.println("总耗时: " + cost + " ms");
                    System.out.println("总请求: " + count);
                    System.out.println("真实 TPS: " + tps);
                    System.out.println("=========================================");
                    break;
                }

                // 还没完，睡 10ms 再看
                try { Thread.sleep(10); } catch (InterruptedException e) {}
            }
        }).start();

        return "极速压测请求已全部提交，请关注 IDEA 控制台日志...";
    }
}
