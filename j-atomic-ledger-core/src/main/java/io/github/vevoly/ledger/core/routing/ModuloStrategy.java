package io.github.vevoly.ledger.core.routing;

import io.github.vevoly.ledger.api.RoutingStrategy;
import io.github.vevoly.ledger.api.constants.RoutingType;

/**
 * <h3>简单哈希取模路由策略 (Simple Hash Modulo Strategy)</h3>
 *
 * <p>
 * 算法: {@code hash(key) % N}。
 * <ul>
 *     <li><b>优点:</b> 实现简单，计算速度极快。</li>
 *     <li><b>缺点:</b> 当分片数 (partitionCount) 发生变化时，几乎所有 Key 的路由都会失效，导致数据需要全量迁移。</li>
 * </ul>
 * </p>
 *
 * <hr>
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Simple Hash Modulo Strategy.</b><br>
 * Algorithm: {@code hash(key) % N}.<br>
 * <b>Pros:</b> Simple and fast.<br>
 * <b>Cons:</b> Causes massive data migration when partition count changes.
 * </span>
 *
 * @author vevoly
 * @since 1.1.0
 */
public class ModuloStrategy implements RoutingStrategy {
    @Override
    public int getPartition(String routingKey, int partitionCount) {
        if (routingKey == null) {
            return 0;
        }
        // 使用位运算保证正数，并取模 / Use bitwise op for positive integer and then modulo
        return (routingKey.hashCode() & Integer.MAX_VALUE) % partitionCount;
    }

    @Override
    public String getName() {
        return RoutingType.MODULO.name();
    }
}
