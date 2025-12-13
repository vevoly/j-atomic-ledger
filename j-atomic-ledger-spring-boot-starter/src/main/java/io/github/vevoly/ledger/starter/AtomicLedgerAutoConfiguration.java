package io.github.vevoly.ledger.starter;

import io.github.vevoly.ledger.api.*;
import io.github.vevoly.ledger.core.LedgerEngine;
import io.github.vevoly.ledger.core.idempotency.GuavaIdempotencyStrategy;
import io.github.vevoly.ledger.core.idempotency.IdempotencyType;
import io.github.vevoly.ledger.core.idempotency.LruIdempotencyStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.Serializable;

/**
 * <h3>核心自动装配类 (Core Auto-Configuration)</h3>
 *
 * <p>
 * 利用 Spring Boot 的自动配置机制，将配置文件、核心引擎与用户定义的业务 Bean 组装在一起。
 * 只有当用户实现了必要的接口（BusinessProcessor, BatchWriter, LedgerBootstrap）并配置了存储路径时，引擎才会启动。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Core Auto-Configuration.</b><br>
 * Assembles configuration files, the core engine, and user-defined business beans using Spring Boot's auto-configuration mechanism.<br>
 * The engine starts only when the user implements the required interfaces (BusinessProcessor, BatchWriter, LedgerBootstrap) and configures the storage path.
 * </span>
 *
 * @author vevoly
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(AtomicLedgerProperties.class)
public class AtomicLedgerAutoConfiguration {

    /**
     * 初始化幂等去重策略 (Initialize Idempotency Strategy).
     * <p>
     * 如果用户没有自定义 {@link IdempotencyStrategy} Bean，则根据配置文件创建默认策略。
     * </p>
     * <ul>
     *     <li><b>LRU:</b> 适合小规模精准去重。</li>
     *     <li><b>BLOOM (Default):</b> 适合海量数据去重。</li>
     * </ul>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * Creates a default strategy based on config if the user hasn't defined a custom {@link IdempotencyStrategy} Bean.
     * </span>
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotencyStrategy idempotencyStrategy(AtomicLedgerProperties props) {
        if (props.getIdempotency() == IdempotencyType.LRU) {
            return new LruIdempotencyStrategy();
        } else {
            return new GuavaIdempotencyStrategy();
        }
    }

    /**
     * <h3>核心引擎 Bean (Core Ledger Engine Bean)</h3>
     *
     * <p>
     * 组装并启动 {@link LedgerEngine}。
     * </p>
     *
     * <ul>
     *     <li><b>initMethod = "start":</b> Spring 容器启动时自动执行数据恢复 (WAL Replay) 和 线程启动。</li>
     *     <li><b>destroyMethod = "shutdown":</b> Spring 容器销毁时自动执行优雅停机 (强制快照 + 排空队列)。</li>
     * </ul>
     *
     * <hr>
     *
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Assembles and starts the {@link LedgerEngine}.</b><br>
     * <ul>
     *     <li><b>initMethod = "start":</b> Auto-recovery (WAL Replay) and thread startup on container boot.</li>
     *     <li><b>destroyMethod = "shutdown":</b> Graceful shutdown (Force snapshot + Drain queue) on container destroy.</li>
     * </ul>
     * </span>
     *
     * @param props 配置文件属性 (Properties)
     * @param processor 用户实现的业务逻辑 (User Business Logic)
     * @param syncer 用户实现的落库逻辑 (User Persistence Logic)
     * @param idempotencyStrategy 去重策略 (Deduplication Strategy)
     * @param bootstrap 启动引导配置 (Bootstrap Config)
     * @param meterRegistry Spring Boot 监控注册表 (Metrics Registry)
     * @return 组装好的引擎实例 (Configured Engine Instance)
     */
    @Bean(initMethod = "start", destroyMethod = "shutdown")
    @ConditionalOnBean({BusinessProcessor.class, BatchWriter.class, LedgerBootstrap.class})
    @ConditionalOnProperty(prefix = "j-atomic-ledger", name = "base-dir")
    public <S extends Serializable, C extends LedgerCommand, E extends Serializable> LedgerEngine<S, C, E> ledgerEngine(
            AtomicLedgerProperties props,
            BusinessProcessor<S, C, E> processor,
            BatchWriter<E> syncer,
            IdempotencyStrategy idempotencyStrategy,
            LedgerBootstrap<S, C> bootstrap,
            MeterRegistry meterRegistry
    ) {
        return new LedgerEngine.Builder<S, C, E>()
                .baseDir(props.getBaseDir())
                .name(props.getEngineName())
                .partitions(props.getPartitions())
                .queueSize(props.getQueueSize())
                .batchSize(props.getBatchSize())
                .snapshotInterval(props.getSnapshotInterval())
                .enableTimeSnapshot(props.isEnableTimeSnapshot())
                .snapshotTimeInterval(props.getSnapshotTimeInterval().toMillis())
                .processor(processor)
                .syncer(syncer)
                .idempotency(idempotencyStrategy)
                .initialState(bootstrap.getInitialState())
                .commandClass((bootstrap.getCommandClass()))
                .meterRegistry(meterRegistry)
                .build();
    }
}
