package io.github.vevoly.ledger.starter;

import io.github.vevoly.ledger.core.idempotency.IdempotencyType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置属性类
 *
 * @author vevoly
 */
@Data
@ConfigurationProperties(prefix = "j-atomic-ledger")
public class AtomicLedgerProperties {

    /**
     * 数据存储根目录 (必填)
     * 例如: /data/ledger-data
     */
    private String baseDir;

    /**
     * 引擎名称 (用于目录隔离)
     * 默认为 default
     */
    private String engineName = "default";

    /**
     * 数据库批量写入大小
     * 默认 500
     */
    private int batchSize = 500;

    /**
     * 异步落库队列大小
     * 默认为 10万
     */
    private int queueSize = 100000;

    /**
     * 自动快照间隔 (处理多少条 Command 后触发一次)
     * 默认为 5万
     */
    private int snapshotInterval = 50000;

    /**
     * 幂等去重策略类型
     */
    private IdempotencyType idempotency = IdempotencyType.BLOOM;

}
