package io.github.vevoly.ledger.core.snapshot;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import io.github.vevoly.ledger.api.IdempotencyStrategy;


/**
 * 快照数据包装器
 * 包含了恢复系统所需的所有信息
 *
 * @param <S> 业务状态类型
 *
 * @author vevoly
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotContainer<S> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 快照时刻的 WAL 索引 (下次恢复从 index+1 开始)
     */
    private long lastWalIndex;

    /**
     * 业务状态 (用户余额等)
     */
    private S state;

    /**
     * 幂等去重策略的状态 (BloomFilter 或 LRU)
     */
    private IdempotencyStrategy idempotencyStrategy;
}
