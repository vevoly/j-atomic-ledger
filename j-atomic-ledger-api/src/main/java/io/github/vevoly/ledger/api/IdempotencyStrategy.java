package io.github.vevoly.ledger.api;

import java.io.Serializable;

/**
 * 幂等性去重策略接口
 *
 * @author vevoly
 */
public interface IdempotencyStrategy extends Serializable {

    /**
     * 检查 Key 是否已存在
     * @param key 唯一业务键 (如 txId)
     * @return true=已存在(重复), false=不存在
     */
    boolean contains(String key);

    /**
     * 记录 Key
     * @param key 唯一业务键
     */
    void add(String key);

    /**
     * 清理/重置策略状态 (可选)
     */
    default void clear() {}
}
