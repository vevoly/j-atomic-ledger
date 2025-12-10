package io.github.vevoly.ledger.api;

import lombok.Data;

import java.util.concurrent.CompletableFuture;

/**
 * 命令基类 (Command Base Class)
 * <p>
 * 为了减少样板代码，建议您的所有业务命令类都继承此基类。
 * 它封装了引擎运行所需的两个核心基础设施：<b>幂等性ID</b> 和 <b>异步通知句柄</b>。
 * </p>
 *
 * <h3>使用示例 (Usage Example)：</h3>
 * <pre>{@code
 * @Data
 * public class TradeCommand extends BaseCommand {
 *     // 只需定义您的业务字段
 *     private Long userId;
 *     private long amount;
 *     // txId 和 future 已由父类自动处理
 * }
 * }</pre>
 *
 * @author vevoly
 */
@Data
public abstract class BaseCommand implements LedgerCommand {

    /**
     * <b>业务唯一ID (Transaction ID)</b>
     * <ul>
     *     <li>用于 <b>幂等去重 (Idempotency)</b>。</li>
     *     <li>引擎的 BloomFilter 或 LRU 策略将根据此 ID 判断请求是否重复。</li>
     *     <li>建议使用 UUID 或 "业务类型_订单号" 格式。</li>
     * </ul>
     */
    protected String txId;

    /**
     * <b>异步结果通知句柄 (Async Result Handle)</b>
     * <p>
     * 作用：连接 "同步的 Controller" 与 "异步的 Disruptor"。
     * </p>
     * <ul>
     *     <li><b>Transient 关键字说明：</b> 必须标记为 transient！</li>
     *     <li>因为此对象仅用于内存中的线程通信，<b>绝不应该</b>被序列化到 WAL 日志或快照文件中。</li>
     *     <li>如果去掉 transient，会导致序列化失败或产生大量无用垃圾数据。</li>
     * </ul>
     */
    protected transient CompletableFuture<Object> future;

    @Override
    public String getUniqueId() {
        return txId;
    }

    @Override
    public CompletableFuture<Object> getFuture() {
        return future;
    }

    @Override
    public void setFuture(CompletableFuture<Object> future) {
        this.future = future;
    }
}
