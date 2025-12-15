package io.github.vevoly.example.wallet.api;

import io.github.vevoly.example.wallet.component.PerfMonitor;
import io.github.vevoly.example.wallet.domain.TradeCommand;
import io.github.vevoly.example.wallet.domain.TradeResult;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

/**
 * <h3>性能压测接口 (Benchmark Controller)</h3>
 *
 * <p>
 * 提供两种压测模式，用于验证引擎的 <b>吞吐量 (Throughput)</b> 和 <b>数据准确性 (Correctness)</b>。
 * </p>
 *
 * <ul>
 *     <li><b>/bench (标准模式):</b> 使用 Future 回调统计。适用于验证数据一致性和端到端延迟。</li>
 *     <li><b>/bench-fast (极速模式):</b> Fire-and-Forget。适用于探测引擎的物理极限 TPS (无 Future 开销)。</li>
 * </ul>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Benchmark Controller.</b><br>
 * Provides two modes to verify throughput and correctness.<br>
 * 1. <b>/bench (Standard):</b> Uses Future callbacks. Validates consistency and E2E latency.<br>
 * 2. <b>/bench-fast (Extreme):</b> Fire-and-Forget. Proves the physical limit of the engine (No Future overhead).
 * </span>
 *
 * @author vevoly
 */
@Slf4j
@RestController
public class BenchController {

    @Autowired
    private PerfMonitor monitor;

    @Autowired
    private LedgerEngine<WalletState, TradeCommand, UserWalletEntity> engine;

    // 压测接口：模拟并发请求
    // URL: http://localhost:8080/bench?count=100000&threads=50&users=4
    /**
     * <h3>标准压测接口 (Standard Benchmark)</h3>
     * <p>
     * 流程：发送请求 -> 等待 Future 回调 -> 统计耗时 -> <b>严格核对资金</b>。
     * 证明系统在高速运转下，数据依然由 ACID 级别的准确性。
     * </p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Standard Benchmark.</b><br>
     * Flow: Send Request -> Wait Future -> Calc Time -> <b>Strict Balance Check</b>.<br>
     * Proves data accuracy under high load.
     * </span>
     *
     * @param count 总请求数 (Total Requests)
     * @param threads 并发线程数 (Concurrent Threads)
     * @param userCount 模拟用户数 (Simulated Users - for sharding distribution)
     */
    @GetMapping("/bench")
    public String benchmark(@RequestParam(value = "count", defaultValue = "1000000") int count,
                            @RequestParam(value = "threads", defaultValue = "50") int threads,
                            @RequestParam(value = "users", defaultValue = "16") int userCount) {

        // 0. 准备工作：统计期初余额、预计算金额 / Preparation: Calc initial balance & amount
        final long startBalanceSnapshot = calculateTotalBalance(userCount);
        final long amountLong = MoneyUtils.toMem(BigDecimal.ONE); // 每次加 1 元

        // 1. 定义计数器 (使用 LongAdder 减少 CAS 竞争) / Define Counter (Use LongAdder to reduce CAS contention)
        LongAdder completedCount = new LongAdder();
        long startTime = System.currentTimeMillis();

        // 2. 计算每个线程的任务量 (避免向线程池提交百万个 Task，减少调度开销) / Calc tasks per thread
        int requestsPerThread = count / threads;
        int remainder = count % threads;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        log.info(">>> 标准压测开始 | 总量: {} | 线程: {} | 用户数: {} / Standard Bench Started...", count, threads, userCount);

        // 3. 提交任务 (按线程分片) / Submit tasks (Thread Sharding)
        for (int t = 0; t < threads; t++) {
            final int threadIndex = t;
            final int loopCount = (t == threads - 1) ? requestsPerThread + remainder : requestsPerThread;

            executor.submit(() -> {
                try {
                    for (int i = 0; i < loopCount; i++) {
                        // 3.1 极速 ID 生成 (T{线程}-{序号}-{时间}) / Fast ID Generation
                        String txId = "S-" + threadIndex + "-" + i + "-" + System.nanoTime();

                        // 3.2 均匀分布用户 (确保利用所有分片) / Distribute users evenly
                        long globalIndex = (long) threadIndex * requestsPerThread + i;
                        long currentUserId = globalIndex % userCount;

                        TradeCommand cmd = new TradeCommand();
                        cmd.setTxId(txId);
                        cmd.setUserId(currentUserId);
                        cmd.setAmount(amountLong);

                        CompletableFuture<Object> future = new CompletableFuture<>();

                        // 3.3 设置回调 (Standard Mode) / Set Callback
                        future.whenComplete((res, ex) -> {
                            if (ex != null) {
                                log.error("交易失败 / Trade failed", ex);
                            } else {
                                if (res instanceof TradeResult) {
                                    TradeResult result = (TradeResult) res;
                                    Long userId = result.getUserId();
                                    String transactionId = result.getTxId();
                                    BigDecimal balance = result.getCurrentBalance();
                                    long latency = result.getLatencyNs();
//                                    log.info("请求结果：userId={}, TxId={}, 最新余额={}, 耗时={}ns", userId, transactionId, balance, latency);
                                }
                            }
                            completedCount.increment();
                            // 检查是否全部完成 / Check if finished
                            if (completedCount.sum() == count) {
                                printResult("标准模式 / Standard mode", startTime, count, userCount, startBalanceSnapshot, amountLong);
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
        return "标准压测请求已后台提交，结果将打印在控制台... / Standard benchmark submitted, check logs...";
    }

    /**
     * <h3>极速压测接口 (Extreme Throughput Benchmark)</h3>
     * <p>
     * 流程：发送请求 -> <b>不创建 Future</b> -> 旁路计数器统计 -> 估算 TPS。
     * 去除了 Future 的创建和通知开销，测算引擎的纯吞吐极限。
     * </p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Extreme Throughput Benchmark.</b><br>
     * Flow: Send Request -> <b>No Future</b> -> Side-channel Counter -> Estimate TPS.<br>
     * Eliminates Future overhead to probe physical limits.
     * </span>
     */
    @GetMapping("/bench-fast")
    public String benchThroughput(@RequestParam(value = "count", defaultValue = "1000000") int count,
                                  @RequestParam(value = "threads", defaultValue = "50") int threads,
                                  @RequestParam(value = "users", defaultValue = "16") int userCount) {

        // 0. 准备工作 / Preparation
        final long startBalanceSnapshot = calculateTotalBalance(userCount);
        final long amountLong = MoneyUtils.toMem(BigDecimal.ONE);

        // 1. 重置监控器 / Reset Monitor
        monitor.start();

        // 2. 任务分片计算 / Task Sharding Calculation
        int requestsPerThread = count / threads;
        int remainder = count % threads;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        log.info(">>> 极速压测开始 | 总量: {} | 线程: {} | 模式: Fire-and-Forget / Extreme Bench Started...", count, threads);

        // 3. 异步提交任务 / Async Submit
        for (int t = 0; t < threads; t++) {
            final int threadIndex = t;
            final int loopCount = (t == threads - 1) ? requestsPerThread + remainder : requestsPerThread;

            executor.submit(() -> {
                try {
                    for (int i = 0; i < loopCount; i++) {
                        // 快速 ID / Fast ID
                        String txId = "F-" + threadIndex + "-" + i + "-" + System.nanoTime();
                        long globalIndex = (long) threadIndex * requestsPerThread + i;
                        long currentUserId = globalIndex % userCount;

                        TradeCommand cmd = new TradeCommand();
                        cmd.setTxId(txId);
                        cmd.setUserId(currentUserId);
                        cmd.setAmount(amountLong);

                        // 【核心区别】不设置 Future，引擎跳过回调逻辑 / Core Diff: No Future, skip callback logic
                        cmd.setFuture(null);

                        engine.submit(cmd);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        executor.shutdown();

        // 4. 启动后台监控线程 (Polling) / Start background monitoring thread
        new Thread(() -> {
            while (true) {
                long current = monitor.getCount();
                if (current >= count) {
                    // 打印结果并校验资金 (即使是极速模式，钱也不能错) / Print result and verify balance
                    printResult("极速模式 / Fire-and-Forget", monitor.getStartTime(), count, userCount, startBalanceSnapshot, amountLong);
                    break;
                }
                try { Thread.sleep(10); } catch (InterruptedException e) {}
            }
        }).start();

        return "极速压测请求已后台提交，正在后台轮询进度... / Extreme benchmark submitted, polling progress...";
    }

    /**
     * 辅助方法：统计所有用户的总余额.
     * <br><span style="color: gray;">Helper: Calculate total balance of all users.</span>
     */
    private long calculateTotalBalance(int userCount) {
        long total = 0;
        for (long uid = 0; uid < userCount; uid++) {
            // 直接读取内存状态，无 IO 损耗 / Direct memory access, no I/O cost
            WalletState state = engine.getStateBy(String.valueOf(uid));
            total += state.getBalances().getOrDefault(uid, 0L);
        }
        return total;
    }

    /**
     * 辅助方法：打印压测报告与资金对账.
     * <br><span style="color: gray;">Helper: Print report and verify funds.</span>
     */
    private void printResult(String mode, long startTime, int count, int userCount, long startBalance, long amountPerTrade) {
        long endTime = System.currentTimeMillis();
        long cost = endTime - startTime;
        long safeCost = cost == 0 ? 1 : cost;
        long tps = (long) count * 1000 / safeCost;

        // 计算期末余额 / Calculate Final Balance
        long finalTotalBalance = calculateTotalBalance(userCount);

        // 理论预期 / Theoretical Expectation
        long totalIncrease = (long) count * amountPerTrade;
        long expectedTotalBalance = startBalance + totalIncrease;

        log.warn("=========================================");
        log.warn("🚀 {} 压测完成！/ Benchmark Finished!", mode);
        log.warn("总耗时: {} ms / Total Time", cost);
        log.warn("总请求: {} / Total Requests", count);
        log.warn("真实 TPS: {} / Real TPS", tps);
        log.warn("-----------------------------------------");
        log.warn("期初余额: {} / Initial Balance", MoneyUtils.toDb(startBalance));
        log.warn("本轮增量: +{} / Increment", MoneyUtils.toDb(totalIncrease));
        log.warn("预期余额: {} / Expected Balance", MoneyUtils.toDb(expectedTotalBalance));
        log.warn("实际余额: {} / Actual Balance", MoneyUtils.toDb(finalTotalBalance));

        if (finalTotalBalance == expectedTotalBalance) {
            log.warn("✅ 资金对账成功！(金额精确无误) / Balance Matched!");
        } else {
            log.error("❌ 资金对账失败！差额: {} / Balance Mismatch!", MoneyUtils.toDb(finalTotalBalance - expectedTotalBalance));
        }
        log.warn("=========================================");
    }
}
