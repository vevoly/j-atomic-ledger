package io.github.vevoly.ledger.starter;

import io.github.vevoly.ledger.core.idempotency.IdempotencyType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * <h3>核心引擎配置属性 (Engine Configuration Properties)</h3>
 *
 * <p>
 * 对应 {@code application.yml} 中的配置项。前缀为 <b>j-atomic-ledger</b>。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Engine Configuration Properties.</b><br>
 * Maps to configuration items in {@code application.yml}. Prefix: <b>j-atomic-ledger</b>.
 * </span>
 *
 * @author vevoly
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "j-atomic-ledger")
public class AtomicLedgerProperties {

    /**
     * 数据存储根目录 (Base Directory).
     * <p><b>必填项。</b> WAL 日志和快照文件将存储在此目录下。</p>
     * <span style="color: gray;">Mandatory. Root directory for WAL logs and snapshots. e.g., /data/ledger-data</span>
     */
    private String baseDir;

    /**
     * 引擎名称 (Engine Name).
     * <p>用于目录隔离。当部署多个引擎实例时，需确保名称唯一。</p>
     * <span style="color: gray;">Used for directory isolation. Default: "JAtomicLedgerEngine".</span>
     */
    private String engineName = "JAtomicLedgerEngine";

    /**
     * 分片数量 (Partition Count).
     * <p>
     * 决定了启动多少个 Disruptor 线程。
     * <b>建议设置为服务器的 CPU 物理核心数。</b>
     * </p>
     * <span style="color: gray;">Determines the number of Disruptor threads. Recommended to match CPU core count. Default: 1.</span>
     */
    private int partitions = 1;

    /**
     * 数据库批量写入大小 (DB Batch Size).
     * <p>每攒够多少条数据执行一次批量落库。</p>
     * <span style="color: gray;">Number of records to accumulate before triggering a batch DB insert. Default: 1000.</span>
     */
    private int batchSize = 1000;

    /**
     * 异步落库队列大小 (Async Queue Size).
     * <p>用于缓冲 Disruptor 处理完但尚未落库的数据。队列满时会阻塞核心业务（背压机制）。</p>
     * <span style="color: gray;">Buffer size for async persistence. Blocks core logic when full (Backpressure). Default: 65536.</span>
     */
    private int queueSize = 65536;

    /**
     * 自动快照间隔 (Snapshot Interval).
     * <p>处理多少条 Command 后触发一次自动快照保存。</p>
     * <span style="color: gray;">Triggers an automatic snapshot after processing this many commands. Default: 50,000.</span>
     */
    private int snapshotInterval = 50000;

    /**
     * 是否开启基于时间的自动快照 (Enable Time-based Auto Snapshot).
     * <p>默认开启。</p>
     * <span style="color: gray;">Default: true.</span>
     */
    private boolean enableTimeSnapshot = true;

    /**
     * 自动快照时间间隔 (Time-based Auto Snapshot Interval).
     * <p>默认 10分钟。支持格式: 10m, 1h, 30s</p>
     * <span style="color: gray;">Default: 10m. Supports formats: 10m, 1h, 30s</span>
     */
    private Duration snapshotTimeInterval = Duration.ofMinutes(10);

    /**
     * 幂等去重策略类型 (Idempotency Strategy Type).
     * <p>默认为布隆过滤器 (BLOOM)。</p>
     * <span style="color: gray;">Deduplication strategy. Default: BLOOM.</span>
     */
    private IdempotencyType idempotency = IdempotencyType.BLOOM;

    /**
     * 监控指标的前缀 (Metrics Prefix).
     * <p>默认为 "j-atomic-ledger."</p>
     * <span style="color: gray;">Metrics prefix. Default: "j-atomic-ledger.".</span>
     */
    private String metricsPrefix = "j-atomic-ledger.";

}
