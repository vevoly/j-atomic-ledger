package io.github.vevoly.ledger.core.idempotency;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import io.github.vevoly.ledger.api.IdempotencyStrategy;

import java.io.Serial;
import java.nio.charset.StandardCharsets;

/**
 * 基于 Guava BloomFilter 的去重策略
 * 优点：极度节省内存
 * 缺点：存在极低概率的误判（False Positive），不支持删除
 *
 * @author vevoly
 *
 */
public class GuavaIdempotencyStrategy implements IdempotencyStrategy {
    @Serial
    private static final long serialVersionUID = 1L;

    private BloomFilter<CharSequence> filter;

    // 无参构造函数 (供 Kryo 反序列化使用)
    public GuavaIdempotencyStrategy() {
        // 默认参数，实际恢复时会被快照数据覆盖
        this(1_000_000, 0.001);
    }

    /**
     * @param expectedInsertions 预计插入数量
     * @param fpp 误判率 (0.001 = 0.1%)
     */
    public GuavaIdempotencyStrategy(int expectedInsertions, double fpp) {
        this.filter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedInsertions,
                fpp
        );
    }

    @Override
    public boolean contains(String key) {
        return filter.mightContain(key);
    }

    @Override
    public void add(String key) {
        filter.put(key);
    }

}
