package io.github.vevoly.ledger.core.idempotency;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import io.github.vevoly.ledger.api.IdempotencyStrategy;

import java.io.Serial;
import java.nio.charset.StandardCharsets;

/**
 * <h3>基于 Guava BloomFilter 的去重策略 (BloomFilter Strategy)</h3>
 *
 * <p>
 * 利用位数组和哈希函数实现的概率型去重策略。
 * </p>
 * <ul>
 *     <li><b>优点：</b> 内存占用极低（千万级数据仅需几十MB），查询速度极快。</li>
 *     <li><b>缺点：</b> 存在极低概率的误判（False Positive，即把没处理过的判为处理过），且不支持删除。</li>
 * </ul>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Guava BloomFilter Strategy.</b><br>
 * Probabilistic deduplication based on bit arrays and hash functions.<br>
 * <b>Pros:</b> Extremely low memory usage and fast lookups.<br>
 * <b>Cons:</b> Small probability of false positives; deletion is not supported.
 * </span>
 *
 * @author vevoly
 * @since 1.0.0
 */
public class BloomFilterIdempotencyStrategy implements IdempotencyStrategy {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Guava 提供的布隆过滤器实例。
     * <br>
     * <span style="color: gray;">The underlying Guava BloomFilter instance.</span>
     */
    private BloomFilter<CharSequence> filter;

    /**
     * 无参构造函数.
     * <p>仅供 Kryo 反序列化时反射调用。实际运行时，该对象的数据会被快照文件中的数据覆盖。</p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>No-Arg Constructor.</b><br>
     * Intended for Kryo deserialization via reflection only.
     * Runtime data will be overwritten by snapshot data.
     * </span>
     */
    public BloomFilterIdempotencyStrategy() {
        // 默认参数，实际恢复时会被快照数据覆盖 / default parameters, to be overwritten by snapshot data
        this(10_000_000, 0.00001);
    }

    /**
     * 构造函数 (Constructor).
     *
     * @param expectedInsertions 预计插入数量 (Expected insertions count, e.g. 10,000,000)
     * @param fpp 误判率 (False Positive Probability). 0.001 represents 0.1%.
     */
    public BloomFilterIdempotencyStrategy(int expectedInsertions, double fpp) {
        this.filter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedInsertions,
                fpp
        );
    }

    /**
     * 检查 Key 是否存在 (Check existence).
     *
     * @param key 唯一业务键 (Unique Key)
     * @return true=可能存在(Might contain), false=绝对不存在(Definitely not contain)
     */
    @Override
    public boolean contains(String key) {
        return filter.mightContain(key);
    }

    /**
     * 添加 Key (Add Key).
     *
     * @param key 唯一业务键 (Unique Key)
     */
    @Override
    public void add(String key) {
        filter.put(key);
    }

    @Override
    public String getName() {
        return IdempotencyType.BLOOM.name();
    }

}
