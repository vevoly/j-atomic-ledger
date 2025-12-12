package metrics;

public class LedgerMetricConstants {

    // 指标名称前缀
    public static final String PREFIX = "j-atomic-ledger.";

    // 1. RingBuffer 剩余容量 (反映 Disruptor 负载)
    public static final String METRIC_RING_REMAINING = PREFIX + "ring.remaining";

    // 2. 异步落库队列积压数 (反映 DB 压力)
    public static final String METRIC_DB_QUEUE_SIZE = PREFIX + "db.queue.size";

    // 3. 业务处理耗时 (反映内存计算性能)
    public static final String METRIC_PROCESS_TIME = PREFIX + "process.time";

    // 4. 数据库批量写入耗时 (反映数据库性能)
    public static final String METRIC_DB_BATCH_TIME = PREFIX + "db.batch.time";

    // Tags
    public static final String TAG_ENGINE = "engine";
    public static final String TAG_PARTITION = "partition";
}
