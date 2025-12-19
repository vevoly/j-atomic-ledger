package io.github.vevoly.ledger.api;

import java.io.Serializable;

/**
 * <h3>幂等性去重策略接口 (Idempotency Strategy Interface)</h3>
 *
 * <p>
 * 定义如何判断一个请求（TxId）是否已经处理过。
 * 框架内置了 LRU (精准但占内存) 和 BloomFilter (省内存但有误判) 两种实现，用户也可自定义。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Idempotency Strategy Interface.</b><br>
 * Defines how to determine if a request (TxId) has already been processed.<br>
 * Framework provides built-in LRU and BloomFilter implementations.
 * </span>
 *
 * @author vevoly
 * @since 1.0.0
 */
public interface IdempotencyStrategy extends Serializable {

    /**
     * 检查 Key 是否已存在.
     *
     * <span style="color: gray; font-size: 0.9em;">Check if the key exists.</span>
     *
     * @param key 唯一业务键 (Unique Business Key, e.g. txId)
     * @return true=已存在(重复/Duplicate), false=不存在(New)
     */
    boolean contains(String key);

    /**
     * 记录 Key.
     *
     * <span style="color: gray; font-size: 0.9em;">Record the key.</span>
     *
     * @param key 唯一业务键
     */
    void add(String key);

    /**
     * 清理/重置策略状态 (可选).
     *
     * <span style="color: gray; font-size: 0.9em;">Clear/Reset strategy state (Optional).</span>
     */
    default void clear() {}

    /**
     * 获取策略名 (Get Strategy Name).
     * @return 路由策略名称 (Routing Strategy Name).
     */
    String getName();
}
