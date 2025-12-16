package io.github.vevoly.ledger.core.metrics;

/**
 * <h3>监控指标管理器 (Metric Manager)</h3>
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
public class LedgerMetricManager {

    // 指标名称 / Metric names
    public final String ringRemaining;
    public final String dbQueueSize;
    public final String dbBatchTime;

    // 标签名称 / Label names
    public static final String TAG_ENGINE = "engine";
    public static final String TAG_PARTITION = "partition";

    public LedgerMetricManager(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            prefix = "j-atomic-ledger.";
        }
        if (!prefix.endsWith(".")) {
            prefix += ".";
        }
        this.ringRemaining = prefix + "ring.remaining";
        this.dbQueueSize = prefix + "db.queue.size";
        this.dbBatchTime = prefix + "db.batch.time";
    }
}
