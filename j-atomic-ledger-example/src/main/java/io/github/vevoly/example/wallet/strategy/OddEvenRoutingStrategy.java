package io.github.vevoly.example.wallet.strategy;

import io.github.vevoly.ledger.api.RoutingStrategy;
import lombok.extern.slf4j.Slf4j;

/**
 * <h3>[用户自定义] 奇偶哈希路由策略</h3>
 * <p>
 * 仅用于演示如何实现 {@link RoutingStrategy} 接口。
 * <ul>
 *     <li>偶数 UserID -> 分片 0</li>
 *     <li>奇数 UserID -> 分片 1</li>
 * </ul>
 * </p>
 */
@Slf4j
public class OddEvenRoutingStrategy implements RoutingStrategy {

    @Override
    public int getPartition(String routingKey, int partitionCount) {
        if (partitionCount < 2) {
            return 0;
        }

        try {
            long userId = Long.parseLong(routingKey);
            // 偶数 -> 0, 奇数 -> 1
            int targetPartition = (int) (userId % 2);

            log.trace("Routing key '{}' to partition {}", routingKey, targetPartition);
            return targetPartition;

        } catch (NumberFormatException e) {
            log.warn("Routing key '{}' is not a number, falling back to default hash.", routingKey);
            return (routingKey.hashCode() & Integer.MAX_VALUE) % partitionCount;
        }
    }

    @Override
    public String getName() {
        return "OddEvenRoutingStrategy";
    }
}
