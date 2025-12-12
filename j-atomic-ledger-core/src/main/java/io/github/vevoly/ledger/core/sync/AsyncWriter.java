package io.github.vevoly.ledger.core.sync;

import io.github.vevoly.ledger.api.BatchWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import metrics.LedgerMetricConstants;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 通用异步批量写入器
 * 负责削峰填谷，将 Disruptor 处理完的状态异步同步到数据库
 *
 * @param <E> 实体类型
 *
 * @author vevoly
 */
@Slf4j
public class AsyncWriter<E extends Serializable> extends Thread {

    /*
      这里为什么使用 BlockingQueue，而不使用 Disruptor？
    . 1. 瓶颈转移原理 (Bottleneck Shift)
        核心链路：纯内存计算。瓶颈在 CPU。Disruptor 的无锁、伪共享解决、环形数组对 CPU 缓存极度友好，所以能跑 100万 TPS。
        落库链路：写数据库。瓶颈在 IO (网络/磁盘)。 数据库写一次最快也要 1ms - 5ms。哪怕 LinkedBlockingQueue 有锁竞争，消耗了 0.01ms，相比于数据库的 5ms，根本微不足道。
        在这里用 Disruptor，就像是开着法拉利去送外卖，速度的瓶颈在于等红绿灯（数据库），而不是车速（队列性能）。
      2. API 的便利性 (drainTo)
        AsyncWriter 的核心逻辑是 “批量聚合”。BlockingQueue 提供了一个神器方法：drainTo(Collection c, int maxElements)。
        它可以一次性、原子地把队列里现有的所有元素都捞出来放到 List 里。这对于实现 batchInsert 极其方便。
        Disruptor 要实现类似的功能（BatchEventProcessor），代码复杂度会高很多，而且很难像 drainTo 那样灵活控制“有多少拿多少，最多拿N个”。
      3. 背压实现的简单性 (Backpressure)
        当 DB 写不过来时，需要阻塞住 Disruptor 线程。BlockingQueue.put() 天然就是阻塞的。
        Disruptor 虽然也支持阻塞策略，但配置起来相对繁琐。
     */

    private final BlockingQueue<E> queue;

    private final BatchWriter<E> syncer;
    private volatile boolean running = false;
    private final int batchSize;

    private final MeterRegistry registry;
    private final Tags tags;
    private Timer dbBatchTimer;

    public AsyncWriter(int bufferSize, int batchSize, BatchWriter<E> syncer, MeterRegistry registry, Tags tags) {
        this.queue = new LinkedBlockingQueue<>(bufferSize);
        this.batchSize = batchSize;
        this.syncer = syncer;
        this.registry = registry;
        this.tags = tags;
        this.setName("J-Atomic-Ledger-AsyncWriter");
    }

    /**
     * 提交任务 (阻塞模式，实现背压)
     */
    public void submit(E entity) {
        try {
            // 使用 put 而不是 offer，队列满时阻塞生产者(Disruptor)，防止内存溢出
            queue.put(entity);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("AsyncWriter submit interrupted", e);
        }
    }

    @Override
    public synchronized void start() {
        if (running) return;
        // 1. 注册队列积压监控 (Gauge)
        registry.gauge(LedgerMetricConstants.METRIC_DB_QUEUE_SIZE, tags, queue, BlockingQueue::size); // 这是一个动态指标，Micrometer 会定期调用 queue.size()
        // 2. 预创建 DB 耗时 Timer
        this.dbBatchTimer = registry.timer(LedgerMetricConstants.METRIC_DB_BATCH_TIME, tags);

        this.running = true;
        super.start();
        log.info("AsyncWriter 启动成功");
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
        while (running || !queue.isEmpty()) { // 停机时也要处理完剩余数据
            try {
                // 1. 尝试获取第一个元素 (带超时),  如果 1秒内没数据，就 continue 检查 running 状态
                E first = queue.poll(1, TimeUnit.SECONDS);
                if (first == null) {
                    continue;
                }
                batchList.add(first);

                // 2. 贪婪获取后续元素, 尝试把队列里剩下的都捞出来，直到填满 batchSize
                queue.drainTo(batchList, batchSize - 1); // drainTo 是非阻塞的，有多少拿多少

                // 3. 执行批量同步
                doSyncWithRetry(batchList);

                // 4. 清空列表，准备下一轮
                batchList.clear();

            } catch (InterruptedException e) {
                // 忽略中断，由 running 标志控制循环结束
            } catch (Exception e) {
                log.error("AsyncWriter 发生未知异常", e);
            }
        }
        log.info("AsyncWriter 已停止，剩余待处理: {}", queue.size());
    }

    private void doSyncWithRetry(List<E> entities) {
        boolean success = false;
        while (!success) {
            try {
                // 记录数据库写入耗时
                dbBatchTimer.record(() -> {
                    syncer.persist(entities);
                });
                success = true;
            } catch (Exception e) {
                log.error("批量落库失败，条数: {}, 1秒后重试...", entities.size(), e);
                // 强制重试机制：数据库挂了也不能丢数据，死等数据库恢复
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    // 如果在重试等待期间被中断且要求停止，则退出
                    if (!running) return;
                }

                // 再次检查运行状态，如果是停机过程，尽可能坚持重试
                // 但如果用户强制 kill，也没办法
            }
        }
    }
}
