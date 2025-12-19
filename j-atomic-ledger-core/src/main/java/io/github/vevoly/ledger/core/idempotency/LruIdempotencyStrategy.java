package io.github.vevoly.ledger.core.idempotency;

import io.github.vevoly.ledger.api.IdempotencyStrategy;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <h3>基于 LRU 的精准去重策略 (LRU Exact Strategy)</h3>
 *
 * <p>
 * 利用 {@link LinkedHashMap} 实现的最近最少使用 (Least Recently Used) 淘汰策略。
 * </p>
 * <ul>
 *     <li><b>优点：</b> 100% 准确，无误判。</li>
 *     <li><b>缺点：</b> 内存占用相对较高（存储完整的 String Key），容量受限。</li>
 * </ul>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>LRU Exact Strategy.</b><br>
 * Implemented using {@link LinkedHashMap}. Evicts the least recently used entries when full.<br>
 * <b>Pros:</b> 100% accuracy, no false positives.<br>
 * <b>Cons:</b> Higher memory usage (stores full keys); limited capacity.
 * </span>
 *
 * @author vevoly
 * @since 1.0.0
 */
public class LruIdempotencyStrategy implements IdempotencyStrategy {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 使用自定义静态内部类 Map，以确保 Kryo 能正确序列化/反序列化。
     * <br>
     * <span style="color: gray;">Custom static inner Map class to ensure Kryo compatibility.</span>
     */
    private LruHashMap<String, Boolean> cache;

    /**
     * 无参构造函数 (No-Arg Constructor).
     * <p>默认容量 500,000。</p>
     * <span style="color: gray;">Defaults to 500,000 capacity.</span>
     */
    public LruIdempotencyStrategy() {
        this(500_000);
    }

    /**
     * 构造函数 (Constructor).
     *
     * @param maxCapacity 最大容量 (Max Capacity). 超过此数量将淘汰最旧的数据 (Evict oldest when exceeded).
     */
    public LruIdempotencyStrategy(int maxCapacity) {
        this.cache = new LruHashMap<>(maxCapacity);
    }

    /**
     * 检查 Key 是否存在 (Check existence).
     *
     * @param key 唯一业务键 (Unique Key)
     * @return true=已存在(Exists), false=不存在(Not Found)
     */
    @Override
    public boolean contains(String key) {
        return cache.containsKey(key);
    }

    /**
     * 添加 Key (Add Key).
     *
     * @param key 唯一业务键 (Unique Key)
     */
    @Override
    public void add(String key) {
        cache.put(key, Boolean.TRUE);
    }

    /**
     * 清空缓存 (Clear Cache).
     */
    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public String getName() {
        return IdempotencyType.LRU.name();
    }

    /**
     * <h3>自定义 LRU Map 实现 (Custom LRU Map Implementation)</h3>
     * <p>
     * 必须是 <b>静态内部类 (static nested class)</b> 且拥有 <b>无参构造函数</b>，否则 Kryo 无法反序列化。
     * </p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * Must be a <b>static nested class</b> with a <b>no-arg constructor</b> to support Kryo deserialization.
     * </span>
     */
    @NoArgsConstructor
    public static class LruHashMap<K, V> extends LinkedHashMap<K, V> {
        private int maxCapacity;

        /**
         * 构造函数 (Constructor).
         *
         * @param maxCapacity 最大容量 (Max capacity)
         */
        public LruHashMap(int maxCapacity) {
            // accessOrder = true: 按访问顺序排序 (Get/Put 都会将元素移到队尾) / accessOrder = true: Order by access (LRU mode)
            super(maxCapacity, 0.75f, true);
            this.maxCapacity = maxCapacity;
        }

        /**
         * 判断是否移除最老的条目 (Eviction Logic).
         */
        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxCapacity;
        }
    }
}
