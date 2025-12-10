package io.github.vevoly.ledger.api;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;

/**
 * 所有发送给引擎的命令都建议实现此接口
 *
 * @author vevoly
 */
public interface LedgerCommand extends Serializable {

    /**
     * 获取用于幂等去重的唯一 ID
     * @return 业务唯一 ID (如 txId)
     */
    String getUniqueId();

    /**
     * 获取用于通知结果的 Future
     * @return
     */
    default CompletableFuture<Object> getFuture() {
        return null;
    }

    /**
     * 允许设置 Future（业务层构建命令时调用）
     * @param future
     */
    default void setFuture(CompletableFuture<Object> future) {}
}
