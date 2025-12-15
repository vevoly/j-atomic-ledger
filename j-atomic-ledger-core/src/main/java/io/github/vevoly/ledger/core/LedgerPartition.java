package io.github.vevoly.ledger.core;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.github.vevoly.ledger.api.LedgerCommand;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.github.vevoly.ledger.api.BusinessProcessor;
import io.github.vevoly.ledger.api.IdempotencyStrategy;
import io.github.vevoly.ledger.core.idempotency.GuavaIdempotencyStrategy;
import io.github.vevoly.ledger.core.snapshot.SnapshotContainer;
import io.github.vevoly.ledger.core.snapshot.SnapshotManager;
import io.github.vevoly.ledger.core.sync.AsyncWriter;
import io.github.vevoly.ledger.core.wal.WalManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import io.github.vevoly.ledger.core.metrics.LedgerMetricConstants;
import net.openhft.chronicle.queue.ExcerptTailer;

import java.io.File;
import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <h3>核心账本分片引擎 (Core Ledger Partition)</h3>
 *
 * <p>
 * 这是引擎的最小工作单元。每个 Partition 拥有独立的 Disruptor 线程、独立的 WAL 目录和独立的内存状态。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Core Ledger Partition.</b><br>
 * The smallest unit of the engine. Each partition owns an independent Disruptor thread, WAL directory, and memory state.
 * </span>
 *
 * @param <S> 状态类型 (State Type)
 * @param <C> 命令类型 (Command Type)
 * @param <E> 实体类型 (Entity Type)
 * @author vevoly
 * @since 1.0.0
 */
@Slf4j
class LedgerPartition<S extends Serializable, C extends LedgerCommand, E extends Serializable> {

    // --- 核心组件 (Core Components) ---
    private final WalManager walManager;
    private final SnapshotManager<S> snapshotManager;
    private final AsyncWriter<E> asyncWriter;
    private final BusinessProcessor<S, C, E> processor;
    private Disruptor<EventWrapper> disruptor;

    // --- 运行时状态 (Runtime State) ---
    @Getter
    private S state; // 内存中的核心状态对象 / Core state object in memory
    private IdempotencyStrategy idempotencyStrategy;
    private final AtomicLong lastWalIndex = new AtomicLong(0); // 当前处理到的 WAL 索引 / Current WAL index being processed
    private final Class<C> commandClass; // 用于反序列化 / Used for deserialization

    // --- 配置 (Configuration) ---
    private final int partitionIndex; // 分片编号 / Partition index
    @Getter
    private final String partitionName; // 分片名 / Partition name
    private final int snapshotInterval; // 快照间隔 (多少条 Log 做一次快照) / Snapshot interval (how many logs to do a snapshot)
    private final boolean enableTimeSnapshot; // 是否开启时间触发 / Whether to enable time-triggered
    private final long snapshotTimeIntervalMs; // 时间间隔(毫秒) / Time interval (milliseconds)

    // --- 快照运行时状态 (Snapshot Runtime State) ---
    private long lastSnapshotIndex = 0; // 上次快照时的 Sequence / Sequence of the last snapshot
    private long lastSnapshotTime = System.currentTimeMillis(); // 上次快照的时间 / Time of the last snapshot

    // --- 监控 (Metrics) ---
    private final MeterRegistry registry;
    private final Tags tags;

    // --- 心跳 (Heartbeat) ---
    private ScheduledExecutorService heartbeatScheduler; // 心跳调度器 / Heartbeat scheduler
    private static final Object HEARTBEAT_EVENT = new Object(); // 心跳信号对象 / Heartbeat signal object

    // 内部事件包装器 (避免让 Disruptor 直接处理泛型) / internal event wrapper (to avoid Disruptor directly handling generics)
    private static class EventWrapper {
        Object command;
    }

    /**
     * 构造函数 (Constructor).
     * <p>仅供 LedgerEngine 调用。</p>
     */
    LedgerPartition(int partitionIndex, String partitionName, LedgerEngine.Builder<S, C, E> builder) {
        this.partitionIndex = partitionIndex;
        this.partitionName = partitionName;
        this.processor = builder.getProcessor();
        this.state = builder.getInitialState();
        this.snapshotInterval = builder.getSnapshotInterval();
        this.enableTimeSnapshot = builder.isEnableTimeSnapshot();
        this.snapshotTimeIntervalMs = builder.getSnapshotTimeIntervalMs();
        this.commandClass = builder.getCommandClass();

        // 1. 初始化文件路径 格式 / initialize file path : baseDir/engineName/partitionName/wal
        String fullPath = builder.getBaseDir() + File.separator + builder.getEngineName() + File.separator + partitionName;
        // 2. 初始化 WAL 和快照管理器 / initialize WAL and snapshot manager
        this.walManager = new WalManager(fullPath);
        this.snapshotManager = new SnapshotManager<>(fullPath);
        // 3. 初始化去重策略 (默认 Guava BloomFilter) / initialize idempotency strategy (default Guava BloomFilter)
        this.idempotencyStrategy = builder.getIdempotencyStrategy() != null ?
                builder.getIdempotencyStrategy() :
                new GuavaIdempotencyStrategy();
        // 4. 初始化 Metrics / initialize Metrics
        this.registry = builder.getRegistry();
        this.tags = Tags.of(
                LedgerMetricConstants.TAG_ENGINE, builder.getEngineName(),
                LedgerMetricConstants.TAG_PARTITION, partitionName
        );
        // 5. 初始化异步写入器 / initialize async writer
        this.asyncWriter = new AsyncWriter<>(builder.getQueueSize(), builder.getBatchSize(), builder.getSyncer(), registry, tags);
    }

    /**
     * 启动引擎 (Start Engine).
     * <ol>
     *     <li>恢复数据 (Recover data from Snapshot & WAL).</li>
     *     <li>启动异步落库线程 (Start AsyncWriter).</li>
     *     <li>启动 Disruptor (Start Disruptor).</li>
     * </ol>
     */
    public synchronized void start() {
        log.info(">>> 分片 [{}] 正在启动...", partitionName);

        // 1. 执行恢复 (加载快照 + 重放 WAL) / Recover (load snapshot + replay WAL)
        recover();

        // 2. 启动异步落库线程 / Start AsyncWriter
        this.asyncWriter.start();

        // 3. 配置并启动 Disruptor / Configure and start Disruptor
        this.disruptor = new Disruptor<>(
                EventWrapper::new,
                1024 * 1024, // RingBuffer size
                r -> {
                    Thread t = new Thread(r);
                    t.setName(String.format("JAtomicLedger-%s-%d", partitionName, partitionIndex));
                    return t;
                },
                ProducerType.MULTI,
                new BlockingWaitStrategy()
        );

        // 注册 RingBuffer 监控，监控剩余容量，越小越危险 / register RingBuffer monitoring, the smaller the more dangerous
        registry.gauge(LedgerMetricConstants.METRIC_RING_REMAINING, tags,
                disruptor, d -> d.getRingBuffer().remainingCapacity());

        // 绑定内部处理器 / bind internal handler
        this.disruptor.handleEventsWith(new CoreEventHandler());
        this.disruptor.start();

        // 启动心跳定时器
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "JAtomicLedger-Heartbeat-" + partitionName);
            t.setDaemon(true); // 设置为守护线程
            return t;
        });
        this.heartbeatScheduler.scheduleAtFixedRate(
                this::sendHeartbeat,
                10, 10, TimeUnit.SECONDS
        );
        log.info("<<< 分片 [{}] 启动成功！Disruptor 线程已就绪。", partitionName);
    }

    /**
     * 灾难恢复逻辑 (Disaster Recovery).
     * <p>加载快照 -> 定位 WAL -> 重放增量日志。</p>
     */
    @SuppressWarnings("unchecked")
    private void recover() {
        log.info("分片 [{}] 开始执行数据恢复...", partitionName);
        // 1. 加载快照 / load snapshot
        SnapshotContainer<S> snapshot = snapshotManager.load();
        if (snapshot != null) {
            this.state = snapshot.getState();
            this.idempotencyStrategy = snapshot.getIdempotencyStrategy();
            this.lastWalIndex.set(snapshot.getLastWalIndex());
            log.info("分片 [{}] 已加载快照，Snapshot Index: {}", partitionName, lastWalIndex.get());
        } else {
            log.info("分片 [{}] 未发现快照，将从头开始重放 WAL。", partitionName);
        }
        // 2. 准备 WAL 读取器 / prepare WAL reader
        ExcerptTailer tailer = walManager.createTailer();
        // 只要有快照，就尝试定位到快照点 / try to locate to snapshot point
        if (snapshot != null) {
            boolean found = tailer.moveToIndex(snapshot.getLastWalIndex());
            if (found) {
                // 读取并跳过快照点那一条日志 / read and skip snapshot point log
                tailer.readDocument(r -> {});
            } else {
                log.warn("分片 [{}] 警告：快照记录的 Index {} 在 WAL 中未找到，可能日志已被清理。尝试从头读取...", partitionName, snapshot.getLastWalIndex());
                tailer.toStart();
            }
        } else {
            tailer.toStart();
        }
        // 3. 重放增量日志 / replay WAL
        long count = 0;
        long startTime = System.currentTimeMillis();
        while (true) {
            // 读取日志中的 Command 对象 / read Command object from log
            boolean read = tailer.readDocument(r -> {
                // 这里读取 "data" 字段，反序列化为对象 / read "data" field and deserialize to object
                C cmd = r.read("data").object(commandClass);
                if (cmd != null) {
                    processCommand(cmd, true);
                }
            });
            if (!read) break;
            count++;
        }
        // 更新内存中的 index 记录，确保新来的请求接着写 / update memory index record to ensure new requests continue to write
        if (tailer.index() > this.lastWalIndex.get()) {
            this.lastWalIndex.set(tailer.index());
        }
        long cost = System.currentTimeMillis() - startTime;
        log.info("分片 [{}] 增量恢复完成。耗时: {}ms, 重放条数: {}", partitionName, cost, count);
    }

    /**
     * 内部 Disruptor 处理器 (Internal Disruptor Handler).
     */
    private class CoreEventHandler implements EventHandler<EventWrapper> {
        @Override
        public void onEvent(EventWrapper event, long sequence, boolean endOfBatch) {
            // 1. 心跳检测 / Heartbeat Check
            if (event.command == HEARTBEAT_EVENT) {
                try {
                    // 只检查快照 (心跳本身就是一个 batch 的结束，或者是空闲时的唯一事件)
                    // Only check snapshot (heartbeat itself is the end of a batch or the only event during idle time)
                    checkAndSnapshot(sequence);
                } finally {
                    event.command = null; // Help GC
                }
                return; // 直接返回，不走后面流程
            }
            C command = (C) event.command;
            try {
                // 2. 写 WAL (这是唯一会产生 IO 的地方，但因为是顺序写，极快)
                // Write WAL (This is the only place that will cause IO, but because it is sequential write, it is very fast)
                long index = walManager.write(command);
                lastWalIndex.set(index);
                // 3. 执行业务逻辑 / Execute business logic
                processCommand(command, false);
                // 4. 触发快照检查 (仅在非恢复模式下) / Trigger snapshot check (only in non-recovery mode)
                if (endOfBatch) {
                    checkAndSnapshot(sequence);
                }
            } catch (Throwable t) {
                // 捕获 Throwable，防止 Disruptor 线程因异常而终止 / Catch Throwable to prevent Disruptor thread from terminating due to exception
                log.error("分片 [{}] 处理事件时发生严重错误，Event: {}", partitionName, command, t);
                // 在这里通知 Command 里的 Future 异常 / Notify Command's Future exception here
                if (command != null && command.getFuture() != null) {
                    try {
                        command.getFuture().completeExceptionally(t);
                    } catch (Exception ex) {
                        // ignore
                    }
                }
            } finally {
                // 5. 清理引用，帮助 GC / Clean up references to help GC
                event.command = null;
            }
        }
    }

    /**
     * <h3>通用业务处理流程 (Common Business Processing Workflow)</h3>
     *
     * <p>
     * 这是 Disruptor 消费者线程的核心逻辑，负责协调幂等去重、业务计算、状态更新和异步落库。
     * 支持两种调用模式：
     * </p>
     * <ul>
     *     <li><b>同步模式 (Sync Mode):</b> Controller 创建 Future 并传入。处理完成后，本方法调用 {@code future.complete()} 通知等待的线程。常用于 HTTP 请求。</li>
     *     <li><b>极速模式 (Fire-and-Forget):</b> Controller 传入 null Future。本方法跳过通知逻辑，以此获得极致吞吐量。常用于日志收集或极限压测。</li>
     * </ul>
     *
     * <hr>
     *
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Core Business Logic Workflow.</b><br>
     * Coordinates idempotency checks, business calculations, state updates, and async persistence.<br>
     * Supports two modes:<br>
     * 1. <b>Sync Mode:</b> Future is provided. Notifies the caller upon completion. Used for HTTP requests.<br>
     * 2. <b>Fire-and-Forget:</b> Future is null. Skips notification for maximum throughput. Used for logging or benchmarking.
     * </span>
     *
     * @param command    待处理的命令 (Command to process)
     * @param isRecovery 是否处于恢复模式 (Recovery Mode Flag). <br>
     *                   true = 仅重放内存状态，跳过数据库落库 (Replay memory state only, skip DB sync).<br>
     *                   false = 正常处理 (Normal processing).
     */
    private void processCommand(C command, boolean isRecovery) {
        // 这是一个防御性检查，防止路由层把 User-2 的请求发到了 Partition-1
        // if (!isRecovery && (command.getRoutingKey().hashCode() % totalPartitions != partitionIndex)) {
        //     log.error("路由错误！我是分片 {}, 但收到了 key={} 的请求", partitionIndex, command.getRoutingKey());
        //     return;
        // }

        String txId = command.getUniqueId();  // 获取去重 id / Get deduplication id
        CompletableFuture<Object> future = command.getFuture(); // 获取 future / Get future

        // 1. 幂等去重检查 / Idempotency check
        if (idempotencyStrategy.contains(txId)) {
            // 如果已处理，直接跳过 / if already processed, skip
            log.debug("Command already processed: {}", txId);
            if (future != null) {
                future.completeExceptionally(new RuntimeException("Duplicate request: " + txId));
            }
            return;
        }

        try {
            // 2. 执行业务，获取增量实体 / Execute business logic, get incremental entity
            E entity = processor.process(state, command);
            // 3. 记录幂等性 / Record idempotency
            idempotencyStrategy.add(txId);
            // 4. 触发异步落库 (恢复模式下不需要，因为数据库里应该已经有了，或者依靠最终快照)
            // Trigger asynchronous database write (no need in recovery mode, as it should already be in the database or rely on the final snapshot)
            // 恢复模式只恢复内存 / Recovery mode only restores memory
            if (!isRecovery && entity != null) {
                asyncWriter.submit(entity);
            }
            // 如果业务层(Processor)里已经调用了 future.complete(result)，这里 isDone() 就是 true，跳过。
            // If the business layer (Processor) has already called future.complete(result), here isDone() is true, skip.
            if (future != null && !future.isDone()) {
                // 如果业务层忘了调，这里给个兜底的 null，防止 Controller 死等。
                // If the business layer forgot to call, here is a bottom line null to prevent the Controller from waiting indefinitely.
                future.complete(null);
            }
        } catch (Exception e) {
            log.error("业务逻辑执行异常", e);
            //  通知业务方：失败了 / Notify the business party: failed
            if (future != null) {
                future.completeExceptionally(e);
            }
        }
    }

    /**
     * <h3>提交业务命令 (Submit Command)</h3>
     *
     * <p>
     * 将命令发布到 Disruptor 的 RingBuffer 中。
     * 采用<b>零拷贝 (Zero-Copy)</b> 模式：先领号，再获取预分配的对象进行填充，最后发布。
     * </p>
     *
     * <hr>
     *
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Submit Command to RingBuffer.</b><br>
     * Uses Zero-Copy pattern: Claim sequence -> Get pre-allocated event -> Fill data -> Publish.
     * </span>
     *
     * @param command 业务命令对象 (The command to be processed)
     */
    public void submit(C command) {
        RingBuffer<EventWrapper> ringBuffer = disruptor.getRingBuffer();
        // 1. 领号：拿到第 N 个槽位的序号 / Claim sequence: get the sequence number of the Nth slot
        long sequence = ringBuffer.next();
        try {
            // 2. 取货：拿出第 N 个槽位里的那个对象（这是复用的旧对象）/ Get the object in the Nth slot (this is the reused old object)
            EventWrapper event = ringBuffer.get(sequence);
            // 3. 装货：把业务数据填进去 / Fill the business data into it
            event.command = command;
        } finally {
            // 4. 发货：告诉 Disruptor "第 N 个位置的数据准备好了" / Tell Disruptor "the data in the Nth position is ready"
            ringBuffer.publish(sequence);
        }
    }

    /**
     * <h3>优雅停机 (Graceful Shutdown)</h3>
     *
     * <p>
     * 按严格顺序关闭组件，确保数据零丢失：
     * <ol>
     *     <li><b>Stop Disruptor:</b> 停止接收新请求，并处理完 RingBuffer 中剩余的积压。</li>
     *     <li><b>Force Snapshot:</b> 强制保存当前内存状态到磁盘（作为下次启动的基准）。</li>
     *     <li><b>Stop AsyncWriter:</b> 等待异步落库队列排空，确保所有数据写入数据库。</li>
     *     <li><b>Close WAL:</b> 关闭日志文件句柄。</li>
     * </ol>
     * </p>
     *
     * <hr>
     *
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Graceful Shutdown.</b><br>
     * Strict shutdown sequence to ensure Zero Data Loss:<br>
     * 1. Stop Disruptor (Process remaining events).<br>
     * 2. Force Snapshot (Save final state).<br>
     * 3. Stop AsyncWriter (Drain DB queue).<br>
     * 4. Close WAL resources.
     * </span>
     */
    public synchronized void shutdown() {
        log.info(">>> 分片 [{}] 正在停止...", partitionName);
        // 1. 停止心跳 / Stop heartbeat.
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdownNow();
        }
        // 2. 停止 Disruptor (不再接收新请求，并处理完 RingBuffer 中剩余的) / Stop Disruptor (No new requests, process remaining events).
        if (disruptor != null) {
            disruptor.shutdown();
        }
        // 3. 强制保存最后一次快照 (非常重要！) / Force Snapshot (Very important!).
        String logContext = String.format("[%s][Shutdown]", partitionName);
        log.info("{} 执行停机快照...", logContext);
        try {
            snapshotManager.save(lastWalIndex.get(), state, idempotencyStrategy, logContext);
        } catch (Exception e) {
            log.error("分片 [{}] 停机快照保存失败", partitionName, e);
        }
        // 4. 停止异步写入器 (等待队列排空) / Stop AsyncWriter (Wait for queue drain).
        if (asyncWriter != null) {
            asyncWriter.shutdown();
        }
        // 5. 关闭 WAL 资源 / Close WAL resources.
        if (walManager != null) {
            walManager.close();
        }
        log.info("<<< 分片 [{}] 已安全停止。", partitionName);
    }

    /**
     * 发送心跳事件到 RingBuffer / Send heartbeat event to RingBuffer
     */
    private void sendHeartbeat() {
        RingBuffer<EventWrapper> ringBuffer = disruptor.getRingBuffer();
        long sequence = ringBuffer.next();
        try {
            EventWrapper event = ringBuffer.get(sequence);
            // 装入特殊的心跳对象 / Load a special heartbeat object
            event.command = HEARTBEAT_EVENT;
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    /**
     * 检查并执行快照 (Check and execute snapshot).
     */
    private void checkAndSnapshot(long currentSequence) {
        boolean trigger = false;
        String reason = ""; // 触发原因 / Trigger reason

        // 1. 数量触发逻辑 (解决跳跃问题：使用差值判断，而不是取模) / Quantity trigger logic (solve the skipping problem: use the difference value judgment, rather than modulo).
        // 只要当前进度超过了上次快照点 N 条，就触发 / Trigger as long as the current progress exceeds the last snapshot point N items.
        if (currentSequence - lastSnapshotIndex >= snapshotInterval) {
            trigger = true;
            reason = "CountTrigger"; // 数量触发 / Count trigger
            log.debug("分片 [{}] 触发快照: 数量阈值已达 ({} >= {})",
                    partitionName, currentSequence - lastSnapshotIndex, snapshotInterval);
        }

        // 2. 时间触发逻辑 / Time trigger logic.
        if (!trigger && enableTimeSnapshot) {
            long now = System.currentTimeMillis();
            if (now - lastSnapshotTime >= snapshotTimeIntervalMs) {
                trigger = true;
                reason = "TimeTrigger"; // 时间触发 / Time trigger
                log.debug("分片 [{}] 触发快照: 时间阈值已达 ({}ms >= {}ms)",
                        partitionName, now - lastSnapshotTime, snapshotTimeIntervalMs);
            }
        }
        if (trigger) {
            doSnapshot(currentSequence, reason);
        }
    }

    /**
     * 执行自动快照 (Execute save snapshot).
     */
    private void doSnapshot(long sequence, String reason) {
        // 组装日志上下文：[分片名][原因] / Build log context: [partition name][reason]
        String logContext = String.format("[%s][%s]", partitionName, reason);
        if (log.isDebugEnabled()) {
            log.debug("{} 正在执行自动快照...", logContext);
        }
        // 保存快照 / Save snapshot
        snapshotManager.save(lastWalIndex.get(), state, idempotencyStrategy, logContext);
        // 更新状态 / Update snapshot state
        this.lastSnapshotIndex = sequence;
        this.lastSnapshotTime = System.currentTimeMillis();
    }

}

