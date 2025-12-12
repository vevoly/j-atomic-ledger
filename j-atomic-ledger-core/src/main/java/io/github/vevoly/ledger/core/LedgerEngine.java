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
 * 核心账本引擎 (Router 模式)
 * 作用：负责管理多个分片 (Partition)，并将请求根据 RoutingKey 路由到具体的分片中。
 *
 * 架构：
 * Request -> LedgerEngine (Router) --hash--> LedgerPartition (Worker) -> Disruptor -> Memory
 *
 * @param <S> 状态类型
 * @param <C> 命令类型
 * @param <E> 增量实体类型
 *
 * @author vevoly
 */
@Slf4j
public class LedgerEngine<S extends Serializable, C extends LedgerCommand, E extends Serializable> {

    // 分片容器
    private final List<LedgerPartition<S, C, E>> partitions;
    // 分片数量 (通常建议设置为 CPU 核心数)
    private final int partitionCount;
    // 引擎总名称
    private final String engineName;

    /**
     * 构造函数 (由 Builder 调用)
     */
    private LedgerEngine(Builder<S, C, E> builder) {
        this.engineName = builder.engineName;
        this.partitionCount = builder.partitionCount;

        // 校验分片数
        if (this.partitionCount <= 0) {
            throw new IllegalArgumentException("Partition count must be > 0");
        }
        this.partitions = new ArrayList<>(partitionCount);
        log.info("正在初始化引擎 [{}], 分片数: {}", engineName, partitionCount);

        // 初始化所有分片
        for (int i = 0; i < partitionCount; i++) {
            // 1. 构建分片名称
            String partitionName = String.format("%s-p%d", engineName, i);
            // 2. 创建分片实例
            // 自动在 baseDir 下创建子目录 /p0, /p1 来隔离 WAL 和 Snapshot
            LedgerPartition<S, C, E> partition = new LedgerPartition<>(i, partitionName, builder);
            // 3. 放入容器
            partitions.add(partition);
        }
    }

    /**
     * 启动所有分片
     */
    public synchronized void start() {
        log.info(">>> 正在启动引擎 [{}] 的所有分片...", engineName);
        for (LedgerPartition<S, C, E> partition : partitions) {
            partition.start();
        }
        log.info("<<< 引擎 [{}] 启动完成，所有分片已就绪。", engineName);
    }

    /**
     * 路由提交 (核心方法)
     * 将命令分发到特定的 Disruptor 线程
     */
    public void submit(C command) {
        // 1. 获取路由键 (例如 userId)
        String routingKey = command.getRoutingKey();
        // 2. 计算哈希槽
        int index = getPartitionIndex(routingKey);;
        // 3. 转发给具体的分片
        partitions.get(index).submit(command);
    }

    /**
     * 优雅停机
     * 依次关闭所有分片
     */
    public synchronized void shutdown() {
        log.info("正在停止引擎 [{}] ...", engineName);
        // 为了数据安全，串行关闭最稳妥，避免日志交错混乱
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
     * 根据路由键获取对应的分片状态
     * @param routingKey 路由键 (如 userId)
     * @return 该分片持有的 State 对象
     */
    public S getStateBy(String routingKey) {
        if (routingKey == null) return null;

        // 1. 复用路由算法
        int index = getPartitionIndex(routingKey);
        // 2. 返回对应分片的状态
        return partitions.get(index).getState();
    }

    private int getPartitionIndex(String routingKey) {
        if (routingKey == null) {
            throw new IllegalArgumentException("Routing key cannot be null");
        }
        // 使用位运算保证正数，效率比 Math.abs 高，且能处理 Integer.MIN_VALUE 的边界情况
        int hash = routingKey.hashCode();
        return (hash & Integer.MAX_VALUE) % partitionCount;
    }

    @Data
    public static class Builder<S extends Serializable, C extends LedgerCommand, E extends Serializable> {
        // 基础配置
        private String baseDir = "/tmp/atomic-ledger";
        private String engineName = "default";
        // 性能配置
        private int batchSize = 500;
        private int queueSize = 100000;
        private int snapshotInterval = 50000;
        // 分片数量配置，默认为 1
        int partitionCount = 1;
        // Metrics
        private MeterRegistry meterRegistry;
        // 用户扩展点
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

        // Getter (给 Partition 用)
        public MeterRegistry getRegistry() {
            if (this.meterRegistry == null) {
                // 兜底，防止用户没传报错
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
