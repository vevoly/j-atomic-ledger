package io.github.vevoly.ledger.api;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;

/**
 * <h3>核心命令接口 (Core Command Interface)</h3>
 *
 * <p>
 * 所有发送给引擎的业务命令都必须实现此接口。它定义了引擎处理请求所需的三个核心要素：
 * <b>去重 (Idempotency)</b>、<b>路由 (Routing)</b> 和 <b>结果通知 (Result Notification)</b>。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>English Documentation:</b><br>
 * All business commands sent to the engine must implement this interface.
 * It defines three core elements required for engine processing:
 * <b>Idempotency</b>, <b>Routing</b>, and <b>Result Notification</b>.
 * </span>
 *
 * @author vevoly
 * @since 1.0.0
 */
public interface LedgerCommand extends Serializable {

    /**
     * 获取用于幂等去重的唯一 ID.
     * <p>
     * 引擎会根据此 ID 判断该请求是否已经处理过。通常对应业务流水号或订单号。
     * </p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Get Unique ID for Idempotency.</b><br>
     * The engine uses this ID to check if the request has already been processed.
     * Usually corresponds to a business transaction ID or order ID.
     * </span>
     *
     * @return 业务唯一 ID (Business Unique ID, e.g., txId)
     */
    String getUniqueId();

    /**
     * 获取路由键 (用于分片路由).
     * <p>
     * <b>重要规则：</b>必须保证同一个业务实体（如同一个用户）的请求返回相同的 Key。
     * <br>
     * 错误示例：使用随机生成的 txId 作为路由键（会导致同一用户的请求分散在不同线程，引发并发扣款事故）。
     * 正确示例：使用 userId 或 accountId。
     * </p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Get Routing Key (For Sharding).</b><br>
     * <b>Critical Rule:</b> Must ensure requests for the same business entity return the same Key.<br>
     * <i>Bad Example:</i> Using random txId (Causes concurrency issues).<br>
     * <i>Good Example:</i> Using userId or accountId.
     * </span>
     *
     * @return 路由键 (Routing Key)
     */
    String getRoutingKey();

    /**
     * 获取用于通知结果的 Future.
     * <p>
     * 引擎处理完业务逻辑后（或发生异常时），会调用此 Future 通知调用方。
     * </p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Get Future for Result Notification.</b><br>
     * The engine calls this Future to notify the caller after processing logic (or upon exception).
     * </span>
     *
     * @return 异步结果句柄 (Async Result Handle)
     */
    default CompletableFuture<Object> getFuture() {
        return null; // 默认返回 null，表示不关心结果 (Fire & Forget) / Default null (Fire & Forget)
    }

    /**
     * 设置 Future (业务层构建命令时调用).
     * <p>
     * 用于将 Controller 创建的 Future 注入到命令中。
     * </p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Set Future.</b><br>
     * Called by the business layer to inject the Future created in the Controller.
     * </span>
     *
     * @param future 异步结果句柄 (Async Result Handle)
     */
    default void setFuture(CompletableFuture<Object> future) {}
}
