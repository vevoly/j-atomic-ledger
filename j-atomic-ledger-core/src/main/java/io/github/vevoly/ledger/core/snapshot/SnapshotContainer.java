package io.github.vevoly.ledger.core.snapshot;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import io.github.vevoly.ledger.api.IdempotencyStrategy;


/**
 * <h3>快照数据包装器 (Snapshot Data Wrapper)</h3>
 *
 * <p>
 * 包含了恢复系统所需的所有信息：WAL 索引进度、业务状态、去重策略状态。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Snapshot Data Wrapper.</b><br>
 * Contains all information needed for system recovery: WAL index progress, business state, and idempotency strategy state.
 * </span>
 *
 * @param <S> 业务状态类型 (Business State Type)
 * @author vevoly
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotContainer<S> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 快照时刻的 WAL 索引 (下次恢复从 index+1 开始).
     * <br>
     * <span style="color: gray;">WAL index at the time of snapshot (Recovery starts from index+1).</span>
     */
    private long lastWalIndex;

    /**
     * 业务状态 (用户余额等).
     * <br>
     * <span style="color: gray;">Business state (e.g., User balances).</span>
     */
    private S state;

    /**
     * 幂等去重策略的状态 (BloomFilter 或 LRU).
     * <br>
     * <span style="color: gray;">State of idempotency strategy (BloomFilter or LRU).</span>
     */
    private IdempotencyStrategy idempotencyStrategy;
}
