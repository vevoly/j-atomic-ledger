package io.github.vevoly.example.wallet.domain;

import io.github.vevoly.ledger.api.BaseLedgerCommand;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesOut;

/**
 * <b>用户业务命令 (User Business Command)</b>
 * <p>
 * 此类是业务逻辑的载体，用于将请求参数从 Controller 传递到核心引擎（Disruptor）。
 * 它继承自 {@link BaseLedgerCommand}，复用了核心的 txId 和 amount 字段。
 * </p>
 *
 * <h3>开发规范与注意事项：</h3>
 * <ol>
 *     <li><b>Lombok 注解：</b> 必须添加 {@code @EqualsAndHashCode(callSuper = true)}。
 *         <br>如果不加，Lombok 生成的 equals 方法将忽略父类中的 {@code txId} 和 {@code amount}，
 *         可能导致完全不同的两笔交易被误判为相等，引发严重 Bug。
 *     </li>
 *     <li><b>路由策略：</b> 必须正确实现 {@code getRoutingKey()}。
 *         <br>引擎根据此 Key 进行 Hash 取模分片。务必保证同一用户（或同一业务聚合根）的 Key 相同，
 *         以确保线程安全。
 *     </li>
 *     <li><b>序列化：</b> 必须实现 {@code writeBizData} 和 {@code readBizData}。
 *         <br>父类已经处理了核心字段，子类只需负责读写自己特有的业务字段（如 userId）。
 *         <b>严禁使用 BigDecimal</b>，请坚持使用 long 类型。
 *     </li>
 * </ol>
 *
 * @author vevoly
 * @see io.github.vevoly.ledger.api.BaseLedgerCommand
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class TradeCommand extends BaseLedgerCommand {

    /**
     * 业务字段：用户ID
     */
    private Long userId;

    // txId 和 amount 字段已在父类定义，此处无需重复定义

    /**
     * 定义分片路由规则
     * @return 返回 userId 的字符串形式，确保同一用户的请求进入同一个 Disruptor 线程
     */
    @Override
    public String getRoutingKey() {
        return String.valueOf(userId);
    }

    /**
     * 序列化扩展：写入业务字段
     * (父类已处理 txId 和 amount)
     */
    @Override
    protected void writeBizData(BytesOut<?> bytes) {
        bytes.writeLong(userId);
    }

    /**
     * 反序列化扩展：读取业务字段
     * (读取顺序必须与写入顺序严格一致！)
     */
    @Override
    protected void readBizData(BytesIn<?> bytes) {
        this.userId = bytes.readLong();
    }

}
