package io.github.vevoly.ledger.core.idempotency;

/**
 * 幂等类型枚举
 *
 * @author vevoly
 */
public enum IdempotencyType {
    BLOOM, // 布隆过滤器
    LRU    // LRU Cache
}
