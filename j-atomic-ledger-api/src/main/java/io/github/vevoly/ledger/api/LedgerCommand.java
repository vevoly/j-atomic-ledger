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
     * 路由键 (用于分片路由)
     * 必须保证同一个业务实体的请求返回相同的 Key。
     * 例如: userId, accountId。千万不要用随机生成的 ID (如 txId)，否则同一个用户的余额会被分散到不同线程，导致并发扣款事故。
     */
    String getRoutingKey();

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
