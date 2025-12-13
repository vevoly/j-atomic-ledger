package io.github.vevoly.ledger.core;

import io.github.vevoly.ledger.api.BatchWriter;
import io.github.vevoly.ledger.api.BusinessProcessor;
import io.github.vevoly.ledger.api.IdempotencyStrategy;
import io.github.vevoly.ledger.api.LedgerCommand;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * <h3>核心账本引擎 (Core Ledger Engine)</h3>
 *
 * <p>
 * 这是对外暴露的统一入口，采用 <b>路由器模式 (Router Pattern)</b>。
 * 它不直接处理业务，而是负责管理多个 {@link LedgerPartition} (分片)，并根据命令的 {@code RoutingKey} 将请求分发到具体的分片中执行。
 * </p>
 *
 * <h3>架构视图 (Architecture):</h3>
 * <pre>
 * Request -> LedgerEngine (Router) --hash--> LedgerPartition (Worker) -> Disruptor -> Memory
 * </pre>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Core Ledger Engine (Router Mode).</b><br>
 * The unified entry point exposed to the outside. It manages multiple {@link LedgerPartition}s and routes requests
 * based on {@code RoutingKey}.<br>
 * It enables <b>Internal Sharding</b> to fully utilize multi-core CPUs.
 * </span>
 *
 * @param <S> 状态类型 (State Type)
 * @param <C> 命令类型 (Command Type)
 * @param <E> 增量实体类型 (Entity Type)
 * @author vevoly
 * @since 1.0.0
 */
@Slf4j
public class LedgerEngine<S extends Serializable, C extends LedgerCommand, E extends Serializable> {

    /**
     * 分片容器列表.
     * <br><span style="color: gray;">List of partitions.</span>
     */
    private final List<LedgerPartition<S, C, E>> partitions;

    /**
     * 分片数量 (通常建议设置为 CPU 核心数).
     * <br><span style="color: gray;">Partition count (Recommended: CPU core count).</span>
     */
    private final int partitionCount;

    /**
     * 引擎总名称 (用于区分不同业务线).
     * <br><span style="color: gray;">Engine name (Distinguish between business lines).</span>
     */
    private final String engineName;

    /**
     * 私有构造函数 (Private Constructor).
     * <p>强制使用 Builder 创建，在此处初始化所有分片。</p>
     */
    private LedgerEngine(Builder<S, C, E> builder) {
        this.engineName = builder.engineName;
        this.partitionCount = builder.partitionCount;

        // 校验分片数 / Validate partition count
        if (this.partitionCount <= 0) {
            throw new IllegalArgumentException("Partition count must be > 0");
        }
        this.partitions = new ArrayList<>(partitionCount);
        log.info("正在初始化引擎 [{}], 分片数: {}", engineName, partitionCount);

        // 初始化所有分片 / Initialize all partitions
        for (int i = 0; i < partitionCount; i++) {
            // 1. 构建分片名称 / Build partition name
            String partitionName = String.format("%s-p%d", engineName, i);
            // 2. 创建分片实例 / Build partition instance
            // 自动在 baseDir 下创建子目录 /p0, /p1 来隔离 WAL 和 Snapshot / Auto create subdirectories /p0, /p1 to isolate WAL and Snapshot
            LedgerPartition<S, C, E> partition = new LedgerPartition<>(i, partitionName, builder);
            // 3. 放入容器 / Put in container
            partitions.add(partition);
        }
    }

    /**
     * 启动所有分片 (Start All Partitions).
     * <p>依次启动内部的每个分片，执行恢复逻辑和线程初始化。</p>
     */
    public synchronized void start() {
        log.info(">>> 正在启动引擎 [{}] 的所有分片...", engineName);
        for (LedgerPartition<S, C, E> partition : partitions) {
            partition.start();
        }
        log.info("<<< 引擎 [{}] 启动完成，所有分片已就绪。", engineName);
    }

    /**
     * 路由提交 (Route & Submit).
     * <p>
     * 核心方法。根据 Command 的路由键计算哈希，将任务转发给特定的 Disruptor 线程。
     * </p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Core Method.</b> Calculates hash based on RoutingKey and dispatches the command to the specific Disruptor thread.
     * </span>
     *
     * @param command 业务命令 (Business Command)
     */
    public void submit(C command) {
        // 1. 获取路由键 (例如 userId) / Get routing key (e.g. userId)
        String routingKey = command.getRoutingKey();
        // 2. 计算哈希槽 / Calculate hash slot
        int index = getPartitionIndex(routingKey);;
        // 3. 转发给具体的分片 / Dispatch to specific partition
        partitions.get(index).submit(command);
    }

    /**
     * 优雅停机 (Graceful Shutdown).
     * <p>依次关闭所有分片，确保数据安全落盘。</p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Graceful Shutdown.</b> Closes partitions sequentially to ensure data safety.
     * </span>
     */
    public synchronized void shutdown() {
        log.info("正在停止引擎 [{}] ...", engineName);
        // 为了数据安全，串行关闭最稳妥，避免日志交错混乱 / For data safety, sequential shutdown is the safest approach, avoiding log interleaving.
        for (LedgerPartition<S, C, E> partition : partitions) {
            try {
                partition.shutdown();
            } catch (Exception e) {
                log.error("分片 [{}] 关闭失败", partition.getPartitionName(), e);
            }
        }
        log.info("引擎 [{}] 已全部停止。", engineName);
    }

    /**
     * 根据路由键获取对应的分片状态 (Get State By Routing Key).
     * <p>用于查询特定用户的内存状态。</p>
     *
     * @param routingKey 路由键 (如 userId)
     * @return 该分片持有的 State 对象 (State object held by the partition)
     */
    public S getStateBy(String routingKey) {
        if (routingKey == null) return null;
        // 1. 计算哈希槽 / Calculate hash slot
        int index = getPartitionIndex(routingKey);
        // 2. 返回对应分片的状态 / Return the state of the corresponding partition
        return partitions.get(index).getState();
    }

    /**
     * 内部路由算法 (Internal Routing Algorithm).
     *
     * @param routingKey 路由键
     * @return 分片索引
     */
    private int getPartitionIndex(String routingKey) {
        if (routingKey == null) {
            throw new IllegalArgumentException("Routing key cannot be null");
        }
        // 使用位运算保证正数，效率比 Math.abs 高，且能处理 Integer.MIN_VALUE 的边界情况
        // Use bitwise operation to ensure positive number, efficiency is higher than Math.abs, and can handle the boundary case of Integer.MIN_VALUE
        int hash = routingKey.hashCode();
        return (hash & Integer.MAX_VALUE) % partitionCount;
    }

    /**
     * 引擎构建器 (Engine Builder).
     * <p>用于配置和组装 LedgerEngine。</p>
     */
    @Data
    public static class Builder<S extends Serializable, C extends LedgerCommand, E extends Serializable> {
        // 基础配置 / Base Configuration
        private String baseDir = "/tmp/atomic-ledger";
        private String engineName = "default";
        // 性能配置 / Performance Configuration
        private int batchSize = 1000;
        private int queueSize = 65536;
        private int snapshotInterval = 50000;
        private boolean enableTimeSnapshot = true;
        private long snapshotTimeIntervalMs = 600 * 1000; // 默认 10分钟 / Default 10 minutes
        // 分片数量配置，默认为 1 / Number of partitions configuration, default is 1
        int partitionCount = 1;
        // Metrics
        private MeterRegistry meterRegistry;
        // 用户扩展点 / User Extensions
        private S initialState;
        private BusinessProcessor<S, C, E> processor;
        private BatchWriter<E> syncer;
        private IdempotencyStrategy idempotencyStrategy;
        private Class<C> commandClass;

        public Builder<S, C, E> baseDir(String dir) {
            this.baseDir = dir;
            return this;
        }
        public Builder<S, C, E> name(String name) {
            this.engineName = name;
            return this;
        }
        public Builder<S, C, E> batchSize(int size) {
            this.batchSize = size;
            return this;
        }
        public Builder<S, C, E> queueSize(int size) {
            this.queueSize = size;
            return this;
        }
        public Builder<S, C, E> snapshotInterval(int interval) {
            this.snapshotInterval = interval;
            return this;
        }
        public Builder<S, C, E> enableTimeSnapshot(boolean enable) {
            this.enableTimeSnapshot = enable;
            return this;
        }
        public Builder<S, C, E> snapshotTimeInterval(long millis) {
            this.snapshotTimeIntervalMs = millis;
            return this;
        }
        public Builder<S, C, E> partitions(int count) {
            this.partitionCount = count;
            return this;
        }
        public Builder<S, C, E> meterRegistry(MeterRegistry registry) {
            this.meterRegistry = registry;
            return this;
        }
        public Builder<S, C, E> initialState(S state) {
            this.initialState = state;
            return this;
        }
        public Builder<S, C, E> processor(BusinessProcessor<S, C, E> p) {
            this.processor = p;
            return this;
        }
        public Builder<S, C, E> syncer(BatchWriter<E> s) {
            this.syncer = s;
            return this;
        }
        public Builder<S, C, E> idempotency(IdempotencyStrategy s) {
            this.idempotencyStrategy = s;
            return this;
        }
        public Builder<S, C, E> commandClass(Class<C> commandClass) {
            this.commandClass = commandClass;
            return this;
        }

        /**
         * 获取监控注册表 (Get Registry).
         * <p>兜底逻辑：如果用户没传，返回 SimpleMeterRegistry 防止空指针。</p>
         */
        public MeterRegistry getRegistry() {
            if (this.meterRegistry == null) {
                // 兜底，防止用户没传报错 / Fallback to prevent errors if user doesn't pass.
                this.meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
            }
            return this.meterRegistry;
        }
        public LedgerEngine<S, C, E> build() {
            if (processor == null || syncer == null || initialState == null) {
                throw new IllegalArgumentException("Processor, Syncer, and InitialState are required.");
            }
            return new LedgerEngine<>(this);
        }
    }

}
