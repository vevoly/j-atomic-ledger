package io.github.vevoly.ledger.api.support;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 默认的简单状态实现 (仅包含一个 Map)
 * 适用于简单的计数、库存扣减场景
 */
public class SimpleLongState implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public Map<String, Long> values = new HashMap<>();

    public long get(String key) {
        return values.getOrDefault(key, 0L);
    }

    public void add(String key, long delta) {
        values.put(key, get(key) + delta);
    }
}
