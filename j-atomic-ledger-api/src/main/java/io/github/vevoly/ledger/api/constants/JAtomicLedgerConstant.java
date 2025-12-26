package io.github.vevoly.ledger.api.constants;

import java.time.Duration;

/**
 * 系统常量 / System constant
 *
 * @since 1.2.2
 * @author vevoly
 */
public class JAtomicLedgerConstant {

    public static final String J_ATOMIC_LEDGER_ID = "j-atomic-ledger";

    // 配置默认值 / Config default value
    public static final String DEFAULT_BASE_DIR = "./data/";
    public static final String DEFAULT_ENGINE_NAME = "JAtomicLedgerEngine";
    public static final int DEFAULT_PARTITIONS = 1;
    public static final int DEFAULT_BATCH_SIZE = 1000;
    public static final int DEFAULT_QUEUE_SIZE = 65536;
    public static final int DEFAULT_SNAPSHOT_INTERVAL = 50000;
    public static final boolean DEFAULT_ENABLE_TIME_SNAPSHOT = true;
    public static final Duration DEFAULT_SNAPSHOT_TIME_INTERVAL = Duration.ofMinutes(10);
    public static final String DEFAULT_IDEMPOTENCY = IdempotencyType.BLOOM.name();
    public static final String DEFAULT_METRICS_PREFIX = J_ATOMIC_LEDGER_ID + ".";
    public static final String DEFAULT_ROUTING = RoutingType.RENDEZVOUS.name();
    public static final int DEFAULT_CLUSTER_TOTAL_NODES = 1;
    public static final int DEFAULT_CLUSTER_NODE_ID = 0;
    public static final boolean DEFAULT_ADMIN_ENABLED = false;
    public static final String DEFAULT_ACTUATOR_BASE_PATH = "/actuator";


    public static final String BUSINESS_ID = "businessId";
    public static final String CLUSTER_NODE_MARK = "node-";

    public static final String WAL_DIR = "wal";

    public static final String SNAPSHOT_DIR = "snapshot";

    public static final String WAL_KEY_FIELD_NAME = "data";

    public static final String META_ACTUATOR_BASE_PATH = "management.endpoints.web.base-path";

    public static final String META_PARTITIONS = J_ATOMIC_LEDGER_ID + ".partitions";

    public static final String META_ROUTING = J_ATOMIC_LEDGER_ID + ".routing";

    public static final String META_TOTAL_NODES = J_ATOMIC_LEDGER_ID + ".total-nodes";

    public static final String META_NODE_ID = J_ATOMIC_LEDGER_ID + ".node-id";

    public static final String META_ADMIN_ENABLED = J_ATOMIC_LEDGER_ID + ".admin.enabled";


}
