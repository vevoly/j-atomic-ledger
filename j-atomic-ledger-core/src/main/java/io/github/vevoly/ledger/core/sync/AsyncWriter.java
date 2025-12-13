package io.github.vevoly.ledger.core.sync;

import io.github.vevoly.ledger.api.BatchWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import io.github.vevoly.ledger.core.metrics.LedgerMetricConstants;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * <h3>通用异步批量写入器 (Generic Async Batch Writer)</h3>
 *
 * <p>
 * 负责削峰填谷，将 Disruptor 处理完的状态增量实体异步同步到数据库。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Generic Async Batch Writer.</b><br>
 * Responsible for peak shaving and valley filling, asynchronously synchronizing the state incremental entities processed by Disruptor to the database.
 * </span>
 *
 * <h3>架构设计说明：为什么使用 BlockingQueue 而不是 Disruptor？</h3>
 * <h3>Architecture Design Note: Why BlockingQueue instead of Disruptor?</h3>
 * <ul>
 *     <li>
 *         <b>1. 瓶颈转移原理 (Bottleneck Shift):</b><br>
 *         核心链路是纯内存计算，瓶颈在 CPU；落库链路瓶颈在 IO (网络/磁盘)。
 *         数据库写一次最快也要 1ms-5ms。哪怕 LinkedBlockingQueue 有锁竞争消耗了 0.01ms，相比于数据库的 5ms 根本微不足道。
 *         在这里用 Disruptor 就像开着法拉利送外卖，速度瓶颈在于等红绿灯（数据库），而不是车速。
 *         <br>
 *         <span style="color: gray; font-size: 0.8em;">
 *         Core path is CPU-bound; Persistence path is IO-bound. The lock contention of BlockingQueue (0.01ms) is negligible compared to DB latency (5ms).
 *         Using Disruptor here is like driving a Ferrari for food delivery; the bottleneck is the traffic light (DB), not the car speed.
 *         </span>
 *     </li>
 *     <li>
 *         <b>2. API 的便利性 (API Convenience - drainTo):</b><br>
 *         AsyncWriter 的核心逻辑是“批量聚合”。BlockingQueue 提供了神器方法 {@code drainTo}，可以一次性原子地捞出所有元素。
 *         Disruptor 要实现类似功能代码复杂度极高，且难以灵活控制“有多少拿多少，最多拿N个”。
 *         <br>
 *         <span style="color: gray; font-size: 0.8em;">
 *         BlockingQueue provides {@code drainTo}, which atomically retrieves all elements. Implementing this in Disruptor is complex.
 *         </span>
 *     </li>
 *     <li>
 *         <b>3. 背压实现的简单性 (Backpressure Simplicity):</b><br>
 *         当 DB 写不过来时，需要阻塞住 Disruptor 线程。{@code BlockingQueue.put()} 天然就是阻塞的。
 *         <br>
 *         <span style="color: gray; font-size: 0.8em;">
 *         {@code BlockingQueue.put()} is naturally blocking, providing simple backpressure when the DB is slow.
 *         </span>
 *     </li>
 * </ul>
 *
 * @param <E> 实体类型 (Entity Type)
 * @author vevoly
 * @since 1.0.0
 */
@Slf4j
public class AsyncWriter<E extends Serializable> extends Thread {

    private final BlockingQueue<E> queue;
    private final BatchWriter<E> syncer;
    private volatile boolean running = false;
    private final int batchSize;

    private final MeterRegistry registry;
    private final Tags tags;
    private Timer dbBatchTimer;

    /**
     * 构造函数 (Constructor).
     *
     * @param bufferSize 队列大小 / Queue size
     * @param batchSize 批量落库大小 / Batch size for persistence
     * @param syncer 同步器接口 / Syncer interface
     * @param registry 监控注册表 / Metric registry
     * @param tags 监控标签 / Metric tags
     */
    public AsyncWriter(int bufferSize, int batchSize, BatchWriter<E> syncer, MeterRegistry registry, Tags tags) {
        this.queue = new LinkedBlockingQueue<>(bufferSize);
        this.batchSize = batchSize;
        this.syncer = syncer;
        this.registry = registry;
        this.tags = tags;
        this.setName("J-Atomic-Ledger-AsyncWriter");
    }

    /**
     * 提交任务 (阻塞模式，实现背压).
     * <br>
     * <span style="color: gray;">Submit task (Blocking mode, implements backpressure).</span>
     *
     * @param entity 增量实体 (Incremental Entity)
     */
    public void submit(E entity) {
        try {
            // 使用 put 而不是 offer，队列满时阻塞生产者(Disruptor)，防止内存溢出 / Use 'put' instead of 'offer'. Block producer (Disruptor) when full to prevent OOM
            queue.put(entity);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("AsyncWriter submit interrupted", e);
        }
    }

    @Override
    public synchronized void start() {
        if (running) return;
        // 1. 注册队列积压监控 (Gauge) / Register queue size monitoring (Gauge)
        // 这是一个动态指标，Micrometer 会定期调用 queue.size() / This is dynamic, Micrometer polls queue.size() periodically
        registry.gauge(LedgerMetricConstants.METRIC_DB_QUEUE_SIZE, tags, queue, BlockingQueue::size);
        // 2. 预创建 DB 耗时 Timer / Pre-create DB latency Timer
        this.dbBatchTimer = registry.timer(LedgerMetricConstants.METRIC_DB_BATCH_TIME, tags);
        this.running = true;
        super.start();
        log.info("AsyncWriter 启动成功 / AsyncWriter started successfully");
    }

    public void shutdown() {
        this.running = false;
        this.interrupt();
    }

    public boolean isQueueEmpty() {
        return queue.isEmpty();
    }

    @Override
    public void run() {
        List<E> batchList = new ArrayList<>(batchSize);
        while (running || !queue.isEmpty()) { // 停机时也要处理完剩余数据 / Process remaining data even during shutdown
            try {
                // 1. 尝试获取第一个元素 (带超时) / Try to get the first element (with timeout)
                // 如果 1秒内没数据，就 continue 检查 running 状态 / If no data within 1s, continue to check running status
                E first = queue.poll(1, TimeUnit.SECONDS);
                if (first == null) {
                    continue;
                }
                batchList.add(first);

                // 2. 贪婪获取后续元素 / Greedily retrieve subsequent elements
                // 尝试把队列里剩下的都捞出来，直到填满 batchSize / Try to drain the rest of the queue up to batchSize
                // drainTo 是非阻塞的，有多少拿多少 / drainTo is non-blocking, takes whatever is available
                queue.drainTo(batchList, batchSize - 1);

                // 3. 执行批量同步 / Execute batch synchronization
                doSyncWithRetry(batchList);

                // 4. 清空列表，准备下一轮 / Clear list for next round
                batchList.clear();

            } catch (InterruptedException e) {
                // 忽略中断，由 running 标志控制循环结束 / Ignore interruption, running flag controls loop termination
            } catch (Exception e) {
                log.error("AsyncWriter 发生未知异常 / AsyncWriter unknown exception", e);
            }
        }
        log.info("AsyncWriter 已停止，剩余待处理: {} / AsyncWriter stopped, remaining: {}", queue.size());
    }

    private void doSyncWithRetry(List<E> entities) {
        boolean success = false;
        while (!success) {
            try {
                // 记录数据库写入耗时 / Record DB write latency
                dbBatchTimer.record(() -> {
                    syncer.persist(entities);
                });
                success = true;
            } catch (Exception e) {
                log.error("批量落库失败，条数: {}, 1秒后重试... / Batch persist failed, count: {}, retry in 1s...", entities.size(), e);
                // 强制重试机制：数据库挂了也不能丢数据，死等数据库恢复 / Mandatory retry: data cannot be lost even if DB is down, wait indefinitely
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    // 如果在重试等待期间被中断且要求停止，则退出 / If interrupted during retry wait and shutdown requested, exit
                    if (!running) return;
                }
                // 再次检查运行状态，如果是停机过程，尽可能坚持重试 / Check running status again. If shutting down, try to persist
                // 但如果用户强制 kill，也没办法 / But if user force kills, nothing we can do
            }
        }
    }
}
