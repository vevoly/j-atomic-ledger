package io.github.vevoly.ledger.core.routing;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.github.vevoly.ledger.api.RoutingStrategy;
import io.github.vevoly.ledger.api.constants.RoutingType;

import java.nio.charset.StandardCharsets;

/**
 * <h3>集合点哈希路由策略 (Rendezvous Hashing Strategy)</h3>
 *
 * <p>
 * 也称为最高随机权重 (HRW) 算法。
 * 它通过为每个 (Key, Partition) 组合计算哈希，并选择哈希值最大的分片，来实现路由。
 * </p>
 * <ul>
 *     <li><b>优点:</b> 负载均衡性极佳，且在扩容/缩容时，<b>只需迁移极少量数据</b> (1/N+1)，对运维非常友好。</li>
 *     <li><b>缺点:</b> 计算量比取模略大（但依然是微秒级）。</li>
 * </ul>
 *
 * <hr>
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Rendezvous Hashing Strategy (Highest Random Weight).</b><br>
 * Selects the partition that yields the highest hash value for a given key.<br>
 * <b>Pros:</b> Excellent load balancing and minimal data migration on resizing.<br>
 * <b>Cons:</b> Slightly more computation than simple modulo.
 * </span>
 *
 * @author vevoly
 * @since 1.1.0
 */
public class RendezvousHashStrategy implements RoutingStrategy {

    // 使用 Guava 的 Murmur3 哈希函数，分布性好 / Guava Murmur3 hash function for good distribution
    private static final HashFunction HASH_FUNCTION = Hashing.murmur3_128();

    @Override
    public int getPartition(String routingKey, int partitionCount) {
        if (routingKey == null) {
            return 0;
        }

        long maxHash = Long.MIN_VALUE;
        int selectedPartition = -1;

        // 遍历所有分片 / Iterate over all partitions
        for (int i = 0; i < partitionCount; i++) {
            // 1. 为 (Key, Partition) 组合计算哈希 / Compute hash for (Key, Partition) combination
            long hash = HASH_FUNCTION.newHasher()
                    .putString(routingKey, StandardCharsets.UTF_8)
                    .putInt(i) // 把分片ID也加入计算 / Include partition ID in the hash calculation
                    .hash()
                    .asLong();
            // 2. 选择哈希值最大的那个 / Select the one with the largest hash
            if (hash > maxHash) {
                maxHash = hash;
                selectedPartition = i;
            }
        }
        return selectedPartition;
    }

    @Override
    public String getName() {
        return RoutingType.RENDEZVOUS.name();
    }

}
