package io.github.vevoly.ledger.api;

/**
 * <h3>路由策略接口 (Routing Strategy Interface)</h3>
 *
 * <p>
 * 定义了如何将一个命令（基于其路由键）映射到具体的分片索引 (Partition Index)。
 * 这是实现 <b>节点内数据分片 (Internal Sharding)</b> 的核心机制。
 * </p>
 * <p>
 * <b>实现要求 (Implementation Requirements):</b>
 * <ul>
 *     <li><b>无状态 (Stateless):</b> 实现类不应持有任何运行时状态，所有计算都应基于输入参数。</li>
 *     <li><b>确定性 (Deterministic):</b> 对于相同的 {@code routingKey} 和 {@code partitionCount}，必须永远返回相同的索引。</li>
 * </ul>
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Routing Strategy Interface.</b><br>
 * Defines how to map a command (based on its routing key) to a specific Partition Index.
 * This is the core mechanism for <b>Internal Data Sharding</b>.<br>
 * <br>
 * <b>Implementation Requirements:</b><br>
 * 1. <b>Stateless:</b> Implementations should not hold any runtime state.<br>
 * 2. <b>Deterministic:</b> Must always return the same index for the same inputs.
 * </span>
 *
 * @author vevoly
 * @since 1.1.0
 */
public interface RoutingStrategy {

    /**
     * 根据路由键计算分片索引 (Calculate Partition Index).
     *
     * @param routingKey   路由键 (Routing Key). 通常是 userId, accountId 等业务聚合根的标识符。
     * @param partitionCount 总分片数 (Total number of partitions).
     * @return 计算出的分片索引 (Calculated partition index), 范围应在 {@code [0, partitionCount - 1]}。
     */
    int getPartition(String routingKey, int partitionCount);

    /**
     * 获取策略名 (Get Strategy Name).
     * @return 路由策略名称 (Routing Strategy Name).
     */
    String getName();
}
