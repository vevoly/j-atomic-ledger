package io.github.vevoly.ledger.core;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.github.vevoly.ledger.api.BusinessProcessor;
import io.github.vevoly.ledger.api.IdempotencyStrategy;
import io.github.vevoly.ledger.api.LedgerCommand;
import io.github.vevoly.ledger.core.idempotency.GuavaIdempotencyStrategy;
import io.github.vevoly.ledger.core.snapshot.SnapshotContainer;
import io.github.vevoly.ledger.core.snapshot.SnapshotManager;
import io.github.vevoly.ledger.core.sync.AsyncWriter;
import io.github.vevoly.ledger.core.wal.WalManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import metrics.LedgerMetricConstants;
import net.openhft.chronicle.queue.ExcerptTailer;

import java.io.File;
import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * j-atomic-ledger 核心账本引擎
 * @param <S> 状态类型 (State)
 * @param <C> 命令类型 (Command)
 *
 * @author vevoly
 */
@Slf4j
class LedgerPartition<S extends Serializable, C extends LedgerCommand, E extends Serializable> {

    // --- 核心组件 ---
    private final WalManager walManager;
    private final SnapshotManager<S> snapshotManager;
    private final AsyncWriter<E> asyncWriter;
    private final BusinessProcessor<S, C, E> processor;
    private Disruptor<EventWrapper> disruptor;

    /**
     * -- GETTER --
     *  获取当前内存状态 (只读)
     */
    // --- 运行时状态 ---
    @Getter
    private S state; // 内存中的核心状态对象
    private IdempotencyStrategy idempotencyStrategy; // 去重策略
    private final AtomicLong lastWalIndex = new AtomicLong(0); // 当前处理到的 WAL 索引
    private final Class<C> commandClass; // 用于反序列化

    // --- 配置 ---
    private final int partitionIndex; // 分片编号
    @Getter
    private final String partitionName; // 分片名
    private final String engineName; // 引擎名
    private final int snapshotInterval; // 快照间隔 (多少条 Log 做一次快照)

    // --- Metrics ---
    private final MeterRegistry registry;
    private final Tags tags;

    // 内部事件包装器 (避免让 Disruptor 直接处理泛型)
    private static class EventWrapper {
        Object command;
    }

    /**
     * 私有构造函数，使用 Builder 创建
     */
    LedgerPartition(int partitionIndex, String partitionName, LedgerEngine.Builder<S, C, E> builder) {
        this.partitionIndex = partitionIndex;
        this.partitionName = partitionName;
        this.engineName = builder.getEngineName();
        this.processor = builder.getProcessor();
        this.state = builder.getInitialState();
        this.snapshotInterval = builder.getSnapshotInterval();
        this.commandClass = builder.getCommandClass();

        // 1. 初始化文件路径 格式: baseDir/engineName/partitionName/wal
        String fullPath = builder.getBaseDir() + File.separator + engineName + File.separator + partitionName;

        // 2. 初始化 WAL 和快照管理器
        this.walManager = new WalManager(fullPath);
        this.snapshotManager = new SnapshotManager<>(fullPath);

        // 3. 初始化去重策略 (默认 Guava BloomFilter)
        this.idempotencyStrategy = builder.getIdempotencyStrategy() != null ?
                builder.getIdempotencyStrategy() :
                new GuavaIdempotencyStrategy();

        // 4. 初始化 Metrics
        this.registry = builder.getRegistry();
        this.tags = Tags.of(
                LedgerMetricConstants.TAG_ENGINE, builder.getEngineName(),
                LedgerMetricConstants.TAG_PARTITION, partitionName
        );

        // 5. 初始化异步写入器
        this.asyncWriter = new AsyncWriter<>(builder.getQueueSize(), builder.getBatchSize(), builder.getSyncer(), registry, tags);
    }

    /**
     * 启动引擎
     */
    public synchronized void start() {
        log.info(">>> 分片 [{}] 正在启动...", partitionName);

        // 1. 执行恢复 (加载快照 + 重放 WAL)
        recover();

        // 2. 启动异步落库线程
        this.asyncWriter.start();

        // 3. 配置并启动 Disruptor
        this.disruptor = new Disruptor<>(
                EventWrapper::new,
                1024 * 1024, // RingBuffer 大小
                r -> {
                    Thread t = new Thread(r);
                    t.setName(String.format("JAtomicLedger-%s-%d", partitionName, partitionIndex));
                    return t;
                },
                ProducerType.MULTI,
                new BlockingWaitStrategy()
        );

        // 注册 RingBuffer 监控，监控剩余容量，越小越危险
        registry.gauge(LedgerMetricConstants.METRIC_RING_REMAINING, tags,
                disruptor, d -> d.getRingBuffer().remainingCapacity());

        // 绑定内部处理器
        this.disruptor.handleEventsWith(new CoreEventHandler());
        this.disruptor.start();
        log.info("<<< 分片 [{}] 启动成功！Disruptor 线程已就绪。", partitionName);
    }

    /**
     * 灾难恢复逻辑
     */
    @SuppressWarnings("unchecked")
    private void recover() {
        log.info("分片 [{}] 开始执行数据恢复...", partitionName);
        // 1. 加载快照
        SnapshotContainer<S> snapshot = snapshotManager.load();
        long replayStartIndex = 0;
        if (snapshot != null) {
            this.state = snapshot.getState();
            this.idempotencyStrategy = snapshot.getIdempotencyStrategy();
            this.lastWalIndex.set(snapshot.getLastWalIndex());
            log.info("分片 [{}] 已加载快照，Snapshot Index: {}", partitionName, lastWalIndex.get());
        } else {
            log.info("分片 [{}] 未发现快照，将从头开始重放 WAL。", partitionName);
        }
        // 2. 准备 WAL 读取器
        ExcerptTailer tailer = walManager.createTailer();
        // 只要有快照，就尝试定位到快照点
        if (snapshot != null) {
            boolean found = tailer.moveToIndex(snapshot.getLastWalIndex());
            if (found) {
                // 读取并跳过快照点那一条日志
                tailer.readDocument(r -> {});
            } else {
                log.warn("分片 [{}] 警告：快照记录的 Index {} 在 WAL 中未找到，可能日志已被清理。尝试从头读取...", partitionName, snapshot.getLastWalIndex());
                tailer.toStart();
            }
        } else {
            tailer.toStart();
        }
        // 3. 重放增量日志
        long count = 0;
        long startTime = System.currentTimeMillis();
        while (true) {
            // 读取日志中的 Command 对象
            boolean read = tailer.readDocument(r -> {
                // 这里读取 "data" 字段，反序列化为对象
                C cmd = r.read("data").object(commandClass);
                if (cmd != null) {
                    processCommand(cmd, true); // true 表示是恢复模式
                }
            });
            if (!read) break;
            count++;
        }
        // 更新内存中的 index 记录，确保新来的请求接着写
        if (tailer.index() > this.lastWalIndex.get()) {
            this.lastWalIndex.set(tailer.index());
        }
        long cost = System.currentTimeMillis() - startTime;
        log.info("分片 [{}] 增量恢复完成。耗时: {}ms, 重放条数: {}", partitionName, cost, count);
    }

    /**
     * 内部 Disruptor 处理器
     */
    private class CoreEventHandler implements EventHandler<EventWrapper> {
        @Override
        public void onEvent(EventWrapper event, long sequence, boolean endOfBatch) {
            C command = (C) event.command;
            try {
                // 1. 写 WAL (这是唯一会产生 IO 的地方，但因为是顺序写，极快)
                long index = walManager.write(command);
                lastWalIndex.set(index);
                // 2. 执行业务逻辑
                processCommand(command, false);
                // 3. 触发快照检查 (仅在非恢复模式下)
                if (sequence % snapshotInterval == 0 && endOfBatch) {
                    doSnapshot();
                }
            } finally {
                // 4. 清理引用，帮助 GC
                event.command = null;
            }
        }
    }

    /**
     * 通用业务处理流程
     * @param isRecovery 是否处于恢复模式 (恢复模式下不执行落库)
     */
    private void processCommand(C command, boolean isRecovery) {
        // 这是一个防御性检查，防止路由层把 User-2 的请求发到了 Partition-1
        // if (!isRecovery && (command.getRoutingKey().hashCode() % totalPartitions != partitionIndex)) {
        //     log.error("路由错误！我是分片 {}, 但收到了 key={} 的请求", partitionIndex, command.getRoutingKey());
        //     return;
        // }

        String txId = command.getUniqueId();  // 获取去重 id
        CompletableFuture<Object> future = command.getFuture(); // 获取 future

        // 1. 幂等去重检查
        if (idempotencyStrategy.contains(txId)) {
            // 如果已处理，直接跳过
            log.debug("Command already processed: {}", txId);
            if (future != null) {
                future.completeExceptionally(new RuntimeException("Duplicate request: " + txId));
            }
            return;
        }

        try {
            // 2. 执行业务，获取增量实体
            E entity = processor.process(state, command);
            // 3. 记录幂等性
            idempotencyStrategy.add(txId);
            // 4. 触发异步落库 (恢复模式下不需要，因为数据库里应该已经有了，或者依靠最终快照)
            // 恢复模式只恢复内存
            if (!isRecovery && entity != null) {
                asyncWriter.submit(entity);
            }
            if (future != null) {
                future.complete("SUCCESS");
            }
        } catch (Exception e) {
            log.error("业务逻辑执行异常", e);
            //  通知业务方：失败了
            if (future != null) {
                future.completeExceptionally(e);
            }
        }
    }

    /**
     * 提交业务命令
     */
    public void submit(C command) {
        RingBuffer<EventWrapper> ringBuffer = disruptor.getRingBuffer();
        // 1. 领号：拿到第 N 个槽位的序号
        long sequence = ringBuffer.next();
        try {
            // 2. 取货：拿出第 N 个槽位里的那个对象（这是复用的旧对象）
            EventWrapper event = ringBuffer.get(sequence);
            // 3. 装货：把业务数据填进去
            event.command = command;
        } finally {
            // 4. 发货：告诉 Disruptor "第 N 个位置的数据准备好了"
            ringBuffer.publish(sequence);
        }
    }

    /**
     * 优雅停机
     */
    public synchronized void shutdown() {
        log.info(">>> 分片 [{}] 正在停止...", partitionName);
        // 1. 停止 Disruptor (不再接收新请求，并处理完 RingBuffer 中剩余的)
        if (disruptor != null) {
            disruptor.shutdown();
        }
        // 2. 强制保存最后一次快照 (非常重要！)
        log.info("分片 [{}] 执行停机快照...", partitionName);
        try {
            snapshotManager.save(lastWalIndex.get(), state, idempotencyStrategy);
        } catch (Exception e) {
            log.error("分片 [{}] 停机快照保存失败", partitionName, e);
        }
        // 3. 停止异步写入器 (等待队列排空)
        if (asyncWriter != null) {
            asyncWriter.shutdown();
        }
        // 4. 关闭 WAL 资源
        if (walManager != null) {
            walManager.close();
        }
        log.info("<<< 分片 [{}] 已安全停止。", partitionName);
    }

    private void doSnapshot() {
        if (log.isDebugEnabled()) {
            log.debug("分片 [{}] 正在执行自动快照, Index: {}", partitionName, lastWalIndex.get());
        }
        snapshotManager.save(lastWalIndex.get(), state, idempotencyStrategy);
    }

}

