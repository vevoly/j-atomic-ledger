package io.github.vevoly.example.wallet.api;

import io.github.vevoly.example.wallet.domain.TradeCommand;
import io.github.vevoly.example.wallet.domain.WalletState;
import io.github.vevoly.example.wallet.entity.UserWalletEntity;
import io.github.vevoly.ledger.api.RoutingStrategy;
import io.github.vevoly.ledger.api.utils.MoneyUtils;
import io.github.vevoly.ledger.core.LedgerEngine;
import io.github.vevoly.ledger.core.routing.RendezvousHashStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

/**
 * <h3>集群环境性能压测接口 (Benchmark Controller)</h3>
 * @since v1.2.0
 * @author vevoly
 */
@Slf4j
@RestController
public class ClusterBenchController {

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * <h3>集群压测接口 (Cluster Benchmark)</h3>
     * <p>
     * 模拟 API Gateway 的行为，将请求根据 userId 哈希路由到不同的集群实例。
     * </p>
     *
     * @param count     总请求数
     * @param threads   并发线程数
     * @param userCount 模拟用户数
     */
    @GetMapping("/cluster-bench")
    public String clusterBenchmark(
            @RequestParam(value = "count", defaultValue = "10000") int count,
            @RequestParam(value = "threads", defaultValue = "50") int threads,
            @RequestParam(value = "users", defaultValue = "16") int userCount
    ) {
        // 1. 启动时先获取一次实例列表 / Get instance list at start
        List<ServiceInstance> instances = discoveryClient.getInstances("j-atomic-ledger-example");
        if (instances == null || instances.isEmpty()) {
            return "错误：在 Nacos 中找不到任何 'j-atomic-ledger-example' 服务实例！";
        }

        log.info(">>> 集群压测开始 | 发现 {} 个实例 | 总请求: {}", instances.size(), count);

        // 2. 准备压测
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        LongAdder successCount = new LongAdder();
        LongAdder failCount = new LongAdder();
        CountDownLatch latch = new CountDownLatch(count);
        final long amountLong = MoneyUtils.toMem(BigDecimal.ONE); // 每次加 1 元
        long startTime = System.currentTimeMillis();

        // 3. 并发发送请求
        for (int i = 0; i < count; i++) {
            long currentUserId = i % userCount;

            executor.submit(() -> {
                try {
                    // 3.1 构造命令 / Build command
                    TradeCommand cmd = new TradeCommand();
                    cmd.setTxId(UUID.randomUUID().toString());
                    cmd.setUserId(currentUserId);
                    cmd.setAmount(amountLong);

                    // 3.2 计算目标节点 / Calculate target node
                    RoutingStrategy routingStrategy = new RendezvousHashStrategy();
                    int nodeIndex = routingStrategy.getPartition(String.valueOf(currentUserId), instances.size());
                    // 3. 在实例列表中，查找哪个实例的 metadata.node-id 等于 targetNodeId
                    ServiceInstance targetInstance = instances.stream()
                            .filter(inst -> String.valueOf(nodeIndex).equals(inst.getMetadata().get("node-id")))
                            .findFirst()
                            .orElse(null);

                    // 3.3 构建请求 URL / Build request URL
                    String host = targetInstance.getHost();
                    if ("host.docker.internal".equals(host)) {
                        host = "localhost";
                    }
                    String url = String.format("http://%s:%d/internal/trade", host, targetInstance.getPort());

                    // 3.4 发送 HTTP POST 请求 / Send HTTP POST request
                    ResponseEntity<String> response = restTemplate.postForEntity(url, cmd, String.class);
                    if (response.getStatusCode().is2xxSuccessful()) {
                        successCount.increment();
                    } else {
                        failCount.increment();
                    }
                } catch (Exception e) {
                    failCount.increment();
                    log.error("压测请求失败 (UserId={}): {}", currentUserId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 4. 等待并统计结果
        try {
            latch.await();
        } catch (InterruptedException e) { /* ignore */ }

        long cost = System.currentTimeMillis() - startTime;
        long tps = (long) count * 1000 / (cost == 0 ? 1 : cost);
        executor.shutdown();
        String report = String.format("集群压测完成！耗时: %d ms, TPS: %d, 成功: %d, 失败: %d",
                cost, tps, successCount.sum(), failCount.sum());
        log.info(report);
        return report;
    }

    @Autowired
    private LedgerEngine<WalletState, TradeCommand, UserWalletEntity> localEngine;

    /**
     * <h3>内部交易接口 (Internal Trade Endpoint)</h3>
     * <p>
     * 此接口仅供集群内的压测客户端调用，它直接将命令提交给本地引擎。
     * </p>
     */
    @PostMapping("/internal/trade")
    public ResponseEntity<String> internalTrade(@RequestBody TradeCommand cmd) {
        // 为了简单，我们不使用 Future，直接提交 (Fire-and-Forget)
        try {
             localEngine.submit(cmd);
            log.debug("Received internal trade: {}", cmd);
            return ResponseEntity.ok("Accepted");
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}
