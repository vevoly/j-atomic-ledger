package io.github.vevoly.ledger.core.sync;

import io.github.vevoly.ledger.api.StateSyncer;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 通用异步批量写入器
 * 负责削峰填谷，将 Disruptor 处理完的状态异步同步到数据库
 *
 * @param <S> 状态类型
 *
 * @author vevoly
 */
@Slf4j
public class AsyncBatchWriter<S extends Serializable> extends Thread {

    /*
      这里为什么使用 BlockingQueue，而不使用 Disruptor？
    . 1. 瓶颈转移原理 (Bottleneck Shift)
        核心链路：纯内存计算。瓶颈在 CPU。Disruptor 的无锁、伪共享解决、环形数组对 CPU 缓存极度友好，所以能跑 100万 TPS。
        落库链路：写数据库。瓶颈在 IO (网络/磁盘)。 数据库写一次最快也要 1ms - 5ms。哪怕 LinkedBlockingQueue 有锁竞争，消耗了 0.01ms，相比于数据库的 5ms，根本微不足道。
        在这里用 Disruptor，就像是开着法拉利去送外卖，速度的瓶颈在于等红绿灯（数据库），而不是车速（队列性能）。
      2. API 的便利性 (drainTo)
        AsyncBatchWriter 的核心逻辑是 “批量聚合”。BlockingQueue 提供了一个神器方法：drainTo(Collection c, int maxElements)。
        它可以一次性、原子地把队列里现有的所有元素都捞出来放到 List 里。这对于实现 batchInsert 极其方便。
        Disruptor 要实现类似的功能（BatchEventProcessor），代码复杂度会高很多，而且很难像 drainTo 那样灵活控制“有多少拿多少，最多拿N个”。
      3. 背压实现的简单性 (Backpressure)
        当 DB 写不过来时，需要阻塞住 Disruptor 线程。BlockingQueue.put() 天然就是阻塞的。
        Disruptor 虽然也支持阻塞策略，但配置起来相对繁琐。
     */

    private final BlockingQueue<S> queue;

    private final StateSyncer<S> syncer;
    private volatile boolean running = false;

    public AsyncBatchWriter(int bufferSize, StateSyncer<S> syncer) {
        this.queue = new LinkedBlockingQueue<>(bufferSize);
        this.syncer = syncer;
        this.setName("Ledger-AsyncWriter");
    }

    /**
     * 提交任务 (阻塞模式，实现背压)
     */
    public void submit(S state) {
        try {
            // 使用 put 而不是 offer，队列满时阻塞生产者(Disruptor)，防止内存溢出
            queue.put(state);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("AsyncBatchWriter submit interrupted", e);
        }
    }

    @Override
    public synchronized void start() {
        if (running) return;
        this.running = true;
        super.start();
        log.info("AsyncBatchWriter 启动成功");
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
        while (running || !queue.isEmpty()) { // 停机时也要处理完剩余数据
            try {
                // 1. 获取数据
                S state = queue.poll(1, TimeUnit.SECONDS);
                if (state == null) {
                    continue;
                }

                // 2. 调用用户实现的同步逻辑 (无限重试直到成功)
                doSyncWithRetry(state);

            } catch (InterruptedException e) {
                // 忽略中断，由 running 标志控制循环结束
            } catch (Exception e) {
                log.error("AsyncBatchWriter 发生未知异常", e);
            }
        }
        log.info("AsyncBatchWriter 已停止，剩余待处理: {}", queue.size());
    }

    private void doSyncWithRetry(S state) {
        boolean success = false;
        while (!success) {
            try {
                syncer.sync(state);
                success = true;
            } catch (Exception e) {
                log.error("状态同步失败，将在 1s 后重试...", e);
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
