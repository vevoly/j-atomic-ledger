package io.github.vevoly.ledger.core;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.github.vevoly.ledger.api.BusinessProcessor;
import io.github.vevoly.ledger.api.IdempotencyStrategy;
import io.github.vevoly.ledger.api.LedgerCommand;
import io.github.vevoly.ledger.api.StateSyncer;
import io.github.vevoly.ledger.core.idempotency.GuavaIdempotencyStrategy;
import io.github.vevoly.ledger.core.snapshot.SnapshotContainer;
import io.github.vevoly.ledger.core.snapshot.SnapshotManager;
import io.github.vevoly.ledger.core.sync.AsyncBatchWriter;
import io.github.vevoly.ledger.core.wal.WalManager;
import lombok.extern.slf4j.Slf4j;
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
public class LedgerEngine<S extends Serializable, C extends LedgerCommand> {

    // --- 核心组件 ---
    private final WalManager walManager;
    private final SnapshotManager<S> snapshotManager;
    private final AsyncBatchWriter<S> asyncWriter;
    private final BusinessProcessor<S, C> processor;
    private Disruptor<EventWrapper> disruptor;

    // --- 运行时状态 ---
    private S state; // 内存中的核心状态对象
    private IdempotencyStrategy idempotencyStrategy; // 去重策略
    private final AtomicLong lastWalIndex = new AtomicLong(0); // 当前处理到的 WAL 索引
    private final Class<C> commandClass; // 用于反序列化

    // --- 配置 ---
    private final String engineName;
    private final int snapshotInterval; // 快照间隔 (多少条 Log 做一次快照)

    // 内部事件包装器 (避免让 Disruptor 直接处理泛型)
    private static class EventWrapper {
        Object command;
    }

    /**
     * 私有构造函数，使用 Builder 创建
     */
    private LedgerEngine(Builder<S, C> builder) {
        this.engineName = builder.engineName;
        this.processor = builder.processor;
        this.state = builder.initialState;
        this.snapshotInterval = builder.snapshotInterval;
        this.commandClass = builder.commandClass;

        // 1. 初始化文件路径 baseDir/engineName
        String fullPath = builder.baseDir + File.separator + engineName;

        // 2. 初始化 WAL 和快照管理器
        this.walManager = new WalManager(fullPath);
        this.snapshotManager = new SnapshotManager<>(fullPath);

        // 3. 初始化去重策略 (默认 Guava BloomFilter)
        this.idempotencyStrategy = builder.idempotencyStrategy != null ?
                builder.idempotencyStrategy :
                new GuavaIdempotencyStrategy();

        // 4. 初始化异步写入器
        this.asyncWriter = new AsyncBatchWriter<>(builder.queueSize, builder.syncer);
    }

    /**
     * 启动引擎
     */
    public synchronized void start() {
        log.info("正在启动引擎 [{}] ...", engineName);

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
                    t.setName("Ledger-Core-" + engineName);
                    return t;
                },
                ProducerType.MULTI,
                new BlockingWaitStrategy()
        );

        // 绑定内部处理器
        this.disruptor.handleEventsWith(new CoreEventHandler());
        this.disruptor.start();

        log.info("引擎 [{}] 启动成功！", engineName);
    }

    /**
     * 灾难恢复逻辑
     */
    @SuppressWarnings("unchecked")
    private void recover() {
        log.info("开始执行数据恢复...");

        // 1. 加载快照
        SnapshotContainer<S> snapshot = snapshotManager.load();
        long replayStartIndex = 0;

        if (snapshot != null) {
            this.state = snapshot.getState();
            this.idempotencyStrategy = snapshot.getIdempotencyStrategy();
            this.lastWalIndex.set(snapshot.getLastWalIndex());
            replayStartIndex = snapshot.getLastWalIndex();

            log.info("已加载快照，Snapshot Index: {}", replayStartIndex);
        } else {
            log.info("未发现快照，将从头开始重放 WAL。");
        }

        // 2. 准备 WAL 读取器
        ExcerptTailer tailer = walManager.createTailer();
        boolean found;

        if (replayStartIndex > 0) {
            // 尝试移动到快照记录的位置
            found = tailer.moveToIndex(replayStartIndex);
            if (found) {
                // 如果找到了快照点，读取并跳过它 (因为快照已经包含这一条的状态了)
                // 这样下面的 while 循环就会从 index + 1 开始
                tailer.readDocument(r -> {});
            }
        } else {
            tailer.toStart();
        }

        // 3. 重放增量日志
        long count = 0;
        while (true) {
            // 读取日志中的 Command 对象
            boolean read = tailer.readDocument(r -> {
                // 这里读取 "data" 字段，反序列化为对象
                C cmd = r.read("data").object(commandClass);

                // --- 核心重放逻辑 ---
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

        log.info("增量恢复完成，共重放 {} 条日志。", count);
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
                if (sequence % snapshotInterval == 0 && sequence > 0) {
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
        String txId = command.getUniqueId();

        // 获取 future
        CompletableFuture<Object> future = command.getFuture();

        // 1. 幂等去重检查
        if (idempotencyStrategy.contains(txId)) {
            // 如果已处理，直接跳过
            // 生产环境可以考虑打印 debug 日志
            log.warn("Command already processed: {}", txId);
            if (future != null) {
                future.completeExceptionally(new RuntimeException("Duplicate request: " + txId));
            }
            return;
        }

        try {
            // 2. 执行用户定义的业务逻辑 (纯内存操作)
            processor.process(state, command);
            // 3. 记录幂等性
            idempotencyStrategy.add(txId);
            // 4. 触发异步落库 (恢复模式下不需要，因为数据库里应该已经有了，或者依靠最终快照)
            // 注意：如果你希望恢复时也强制刷一遍 DB 以防 DB 数据丢失，可以去掉 !isRecovery
            // 但通常建议恢复模式只恢复内存
            if (!isRecovery) {
                asyncWriter.submit(state);
                // 优化点：为了极致性能，我们传递引用。
                // 但因为 Disruptor 是单线程写，AsyncWriter 是单线程读，
                // 只要 AsyncWriter 在落库时(Serializer/Insert)不修改 State 对象，就是安全的。
                // 如果 State 是可变的，最安全的做法是这里 clone 一份，但这会影响性能。
                // 既然是高性能中间件，我们约定：StateSyncer 里读取 State 时，State 此时是"快照"语义。
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
        long sequence = ringBuffer.next();
        try {
            EventWrapper event = ringBuffer.get(sequence);
            event.command = command;
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    /**
     * 获取当前内存状态 (只读)
     */
    public S getState() {
        return state;
    }

    /**
     * 优雅停机
     */
    public synchronized void shutdown() {
        log.info("正在停止引擎 [{}] ...", engineName);
        // 1. 停止 Disruptor (不再接收新请求，并处理完 RingBuffer 中剩余的)
        if (disruptor != null) {
            disruptor.shutdown();
        }
        // 2. 强制保存最后一次快照 (非常重要！)
        log.info("正在执行停机快照...");
        snapshotManager.save(lastWalIndex.get(), state, idempotencyStrategy);
        // 3. 停止异步写入器 (等待队列排空)
        if (asyncWriter != null) {
            asyncWriter.shutdown();
        }
        // 4. 关闭 WAL 资源
        if (walManager != null) {
            walManager.close();
        }
        log.info("引擎 [{}] 已安全停止。", engineName);
    }

    private void doSnapshot() {
        // 打印日志放在 debug 级别，避免刷屏
        log.debug("正在执行自动快照...");
        snapshotManager.save(lastWalIndex.get(), state, idempotencyStrategy);
    }

    public static class Builder<S extends Serializable, C extends LedgerCommand> {
        private String baseDir = "/tmp/atomic-ledger"; // 默认路径
        private String engineName = "default";
        private int queueSize = 100000;
        private int snapshotInterval = 50000;
        private S initialState;
        private BusinessProcessor<S, C> processor;
        private StateSyncer<S> syncer;
        private IdempotencyStrategy idempotencyStrategy;
        private Class<C> commandClass;

        public Builder<S, C> baseDir(String dir) { this.baseDir = dir; return this; }
        public Builder<S, C> name(String name) { this.engineName = name; return this; }
        public Builder<S, C> queueSize(int size) { this.queueSize = size; return this; }
        public Builder<S, C> snapshotInterval(int interval) { this.snapshotInterval = interval; return this; }
        public Builder<S, C> initialState(S state) { this.initialState = state; return this; }
        public Builder<S, C> processor(BusinessProcessor<S, C> p) { this.processor = p; return this; }
        public Builder<S, C> syncer(StateSyncer<S> s) { this.syncer = s; return this; }
        public Builder<S, C> idempotency(IdempotencyStrategy s) { this.idempotencyStrategy = s; return this; }
        public Builder<S, C> commandClass(Class<C> commandClass) { this.commandClass = commandClass; return this; }

        public LedgerEngine<S, C> build() {
            if (processor == null || syncer == null || initialState == null) {
                throw new IllegalArgumentException("Processor, Syncer, and InitialState are required.");
            }
            return new LedgerEngine<>(this);
        }
    }

}

