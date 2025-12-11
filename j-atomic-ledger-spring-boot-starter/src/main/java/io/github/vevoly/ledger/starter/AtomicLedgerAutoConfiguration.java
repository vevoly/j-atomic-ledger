package io.github.vevoly.ledger.starter;

import io.github.vevoly.ledger.api.*;
import io.github.vevoly.ledger.core.LedgerEngine;
import io.github.vevoly.ledger.core.idempotency.GuavaIdempotencyStrategy;
import io.github.vevoly.ledger.core.idempotency.IdempotencyType;
import io.github.vevoly.ledger.core.idempotency.LruIdempotencyStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.Serializable;

/**
 * 自动装配类
 *
 * @author vevoly
 */
@Configuration
@EnableConfigurationProperties(AtomicLedgerProperties.class)
public class AtomicLedgerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyStrategy idempotencyStrategy(AtomicLedgerProperties props) {
        if (props.getIdempotency() == IdempotencyType.LRU) {
            // 这里简单硬编码容量，实际也可以做成配置项
            return new LruIdempotencyStrategy(500_000);
        } else {
            return new GuavaIdempotencyStrategy(10_000_000, 0.001);
        }
    }

    /**
     * 核心引擎 Bean
     * initMethod = "start": Spring 容器启动时自动启动引擎 (恢复数据)
     * destroyMethod = "shutdown": Spring 容器销毁时自动优雅停机 (强制快照)
     */
    @Bean(initMethod = "start", destroyMethod = "shutdown")
    @ConditionalOnBean({BusinessProcessor.class, BatchWriter.class, LedgerBootstrap.class})
    @ConditionalOnProperty(prefix = "j-atomic-ledger", name = "base-dir")
    public <S extends Serializable, C extends LedgerCommand, E extends Serializable> LedgerEngine<S, C, E> ledgerEngine(
            AtomicLedgerProperties props,
            BusinessProcessor<S, C, E> processor,
            BatchWriter<E> syncer,
            IdempotencyStrategy idempotencyStrategy,
            LedgerBootstrap<S, C> bootstrap // 注入用户配置
    ) {
        return new LedgerEngine.Builder<S, C, E>()
                .baseDir(props.getBaseDir())
                .name(props.getEngineName())
                .queueSize(props.getQueueSize())
                .batchSize(props.getBatchSize())
                .snapshotInterval(props.getSnapshotInterval())
                .processor(processor)
                .syncer(syncer)
                .idempotency(idempotencyStrategy)
                .initialState(bootstrap.getInitialState())
                .commandClass((bootstrap.getCommandClass()))
                .build();
    }
}
