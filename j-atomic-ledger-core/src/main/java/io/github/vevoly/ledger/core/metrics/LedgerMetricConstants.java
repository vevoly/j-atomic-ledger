package io.github.vevoly.ledger.core.metrics;

/**
 * <h3>监控指标常量定义 (Metric Constants)</h3>
 *
 * <p>定义了核心引擎暴露给 Micrometer (Prometheus/Grafana) 的监控指标名称。</p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Metric Constants Definitions.</b><br>
 * Defines metric names exposed to Micrometer (Prometheus/Grafana).
 * </span>
 *
 * @author vevoly
 * @since 1.0.0
 */
public class LedgerMetricConstants {

    /**
     * 指标名称前缀 / Metric name prefix
     */
    public static final String PREFIX = "j-atomic-ledger.";

    /**
     * RingBuffer 剩余容量 (反映 Disruptor 负载).
     * <br><span style="color: gray;">RingBuffer remaining capacity (Reflects Disruptor load).</span>
     */
    public static final String METRIC_RING_REMAINING = PREFIX + "ring.remaining";

    /**
     * 异步落库队列积压数 (反映 DB 压力).
     * <br><span style="color: gray;">Async DB queue size (Reflects DB pressure).</span>
     */
    public static final String METRIC_DB_QUEUE_SIZE = PREFIX + "db.queue.size";

    /**
     * 业务处理耗时 (反映内存计算性能).
     * <br><span style="color: gray;">Business process time (Reflects in-memory calc performance).</span>
     */
    public static final String METRIC_PROCESS_TIME = PREFIX + "process.time";

    /**
     * 数据库批量写入耗时 (反映数据库性能).
     * <br><span style="color: gray;">DB batch write latency (Reflects database performance).</span>
     */
    public static final String METRIC_DB_BATCH_TIME = PREFIX + "db.batch.time";

    // Tags
    public static final String TAG_ENGINE = "engine";
    public static final String TAG_PARTITION = "partition";
}
