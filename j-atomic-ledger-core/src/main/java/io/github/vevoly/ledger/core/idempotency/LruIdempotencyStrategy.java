package io.github.vevoly.ledger.core.idempotency;

import io.github.vevoly.ledger.api.IdempotencyStrategy;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于 LRU (Least Recently Used) 的精准去重策略
 * 优点：100% 准确，无误判
 * 缺点：内存占用相对较高 (取决于 Key 的长度)
 *
 * @author vevoly
 *
 */
public class LruIdempotencyStrategy implements IdempotencyStrategy {
    @Serial
    private static final long serialVersionUID = 1L;

    // 使用自定义 Map 以支持 Kryo 序列化
    private LruHashMap<String, Boolean> cache;

    // 无参构造函数
    public LruIdempotencyStrategy() {
        this(500_000);
    }

    public LruIdempotencyStrategy(int maxCapacity) {
        this.cache = new LruHashMap<>(maxCapacity);
    }

    @Override
    public boolean contains(String key) {
        return cache.containsKey(key);
    }

    @Override
    public void add(String key) {
        cache.put(key, Boolean.TRUE);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    /**
     * 自定义静态内部类，继承 LinkedHashMap 实现 LRU 逻辑
     * 必须是静态的且有无参构造，否则 Kryo 无法序列化
     */
    @NoArgsConstructor
    public static class LruHashMap<K, V> extends LinkedHashMap<K, V> {
        private int maxCapacity;

        public LruHashMap(int maxCapacity) {
            super(maxCapacity, 0.75f, true);
            this.maxCapacity = maxCapacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxCapacity;
        }
    }
}
