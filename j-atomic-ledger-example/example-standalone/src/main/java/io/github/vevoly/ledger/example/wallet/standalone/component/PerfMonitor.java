package io.github.vevoly.ledger.example.wallet.standalone.component;

import lombok.Getter;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.LongAdder;

/**
 * <h3>压测性能监控组件 (Benchmark Performance Monitor)</h3>
 *
 * <p>
 * 专为 <b>极速压测模式 (Fire-and-Forget Mode)</b> 设计的监控工具。
 * </p>
 * <p>
 * 在极速模式下，为了消除 {@code CompletableFuture} 回调和线程唤醒带来的性能损耗，Controller 不会阻塞等待结果。
 * 因此，我们需要通过这个“旁路计数器”来统计后端 Disruptor 实际处理完成了多少请求，从而判断压测是否结束。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Benchmark Performance Monitor.</b><br>
 * Designed for <b>Fire-and-Forget Mode</b> benchmarking.<br>
 * In this mode, to eliminate the overhead of {@code CompletableFuture} callbacks and thread wake-ups, the Controller does not wait for results.
 * This side-channel counter tracks how many requests the Disruptor has actually processed to determine when the benchmark is finished.
 * </span>
 *
 * @author vevoly
 */
@Component
public class PerfMonitor {

    /**
     * 高性能计数器。
     * <p>
     * 使用 {@link LongAdder} 而不是 {@code AtomicLong}。
     * 在高并发写（大量线程同时 increment）的场景下，LongAdder 通过空间换时间（Cell 数组分片）避免了 CAS 自旋竞争，性能极高。
     * </p>
     * <span style="color: gray;">High-performance counter using {@link LongAdder} to avoid CAS contention in high-concurrency scenarios.</span>
     */
    private final LongAdder counter = new LongAdder();

    /**
     * 压测开始时间戳。
     * <br><span style="color: gray;">Benchmark start timestamp.</span>
     */
    @Getter
    private long startTime;

    /**
     * 开始压测时调用 (Start Benchmark).
     * <p>重置计数器并记录当前时间。</p>
     * <span style="color: gray;">Resets counter and records start time.</span>
     */
    public void start() {
        counter.reset();
        startTime = System.currentTimeMillis();
    }

    /**
     * 业务处理完成时调用 (Increment).
     * <p>
     * <b>极速操作：</b> 仅涉及内存计数，对 TPS 几乎零影响。
     * </p>
     * <span style="color: gray;">Called upon business completion. Extremely fast, negligible impact on TPS.</span>
     */
    public void increment() {
        counter.increment();
    }

    /**
     * 获取当前处理总数 (Get Current Count).
     * <p>用于主线程轮询检查压测进度。</p>
     * <span style="color: gray;">Used by the main thread to poll benchmark progress.</span>
     *
     * @return 当前已处理的请求数 (Current processed count)
     */
    public long getCount() {
        return counter.sum();
    }

}
