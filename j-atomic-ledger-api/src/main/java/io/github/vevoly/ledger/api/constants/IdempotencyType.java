package io.github.vevoly.ledger.api.constants;

/**
 * <h3>幂等策略类型枚举 (Idempotency Strategy Type)</h3>
 *
 * <p>用于 Spring Boot 配置文件中选择去重策略。</p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Idempotency Strategy Type Enum.</b><br>
 * Used in configuration files (application.yml) to select the deduplication strategy.
 * </span>
 *
 * @author vevoly
 * @since 1.0.0
 */
public enum IdempotencyType {

    /**
     * <b>布隆过滤器 (Bloom Filter)</b>
     * <br>
     * 省内存，高性能，但有微小误判率。适合亿级数据量。
     * <br>
     * <span style="color: gray;">Low memory, high perf, slight false positive rate. Good for billion-scale data.</span>
     */
    BLOOM,

    /**
     * <b>LRU 缓存 (LRU Cache)</b>
     * <br>
     * 精准去重，但在数据量极大时内存消耗大。适合千万级以下数据量或只需近期去重的场景。
     * <br>
     * <span style="color: gray;">Exact deduplication, high memory usage. Good for recent-transaction deduplication.</span>
     */
    LRU
}
