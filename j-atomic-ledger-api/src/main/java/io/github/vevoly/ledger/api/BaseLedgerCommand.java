package io.github.vevoly.ledger.api;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

import java.io.Serial;
import java.util.concurrent.CompletableFuture;

/**
 * <b>核心命令基类 (Core Command Base)</b>
 * <p>
 * 这是高性能账本引擎的基石。为了确保<b>百万级 TPS</b> 和 <b>Zero GC (零垃圾回收)</b>，
 * 本类强制规定了金额的数据类型和序列化方式。
 * </p>
 *
 * <h3>核心设计规范：</h3>
 * <ol>
 *     <li><b>强制使用 long 金额：</b> 严禁在核心链路使用 BigDecimal。请使用最小货币单位（如：厘）进行存储。</li>
 *     <li><b>二进制序列化：</b> 实现了 BytesMarshallable，直接操作内存字节，跳过反射，性能提升 10 倍。</li>
 * </ol>
 *
 * @author vevoly
 */
@Data
public abstract class BaseLedgerCommand implements LedgerCommand, BytesMarshallable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * <b>业务唯一ID (Transaction ID)</b>
     * <p>用于幂等去重。建议使用 UUID 或 "业务类型 + 单号"。</p>
     */
    protected String txId;

    /**
     * <b>变动金额 (Amount)</b>
     * <p>
     * <b>强制规范：</b>
     * 必须使用 long 类型，单位建议为系统的最小货币单位（例如：厘/分）。
     * <br>
     * <b>为什么不用 BigDecimal？</b>
     * BigDecimal 是对象，计算慢且产生大量 GC。long 是 CPU 原生类型，计算只需纳秒级，且零 GC。
     * </p>
     */
    protected long amount;

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

    /**
     * <b>二进制序列化 (写)</b>
     * <p>
     * 用户只需实现 {@link #writeBizData(BytesOut)} 来写入额外的业务字段。
     * </p>
     */
    @Override
    public final void writeMarshallable(BytesOut<?> bytes) {
        // 1. 先写核心字段
        bytes.writeUtf8(txId);
        bytes.writeLong(amount);
        // 2. 再写用户的扩展字段
        writeBizData(bytes);
    }

    /**
     * <b>二进制反序列化 (读)</b>
     * <p>逻辑同上，保证读取顺序与写入顺序严格一致。</p>
     */
    @Override
    public final void readMarshallable(BytesIn<?> bytes) {
        // 1. 先读核心字段
        this.txId = bytes.readUtf8();
        this.amount = bytes.readLong();
        // 2. 再读用户的扩展字段
        readBizData(bytes);
    }

    /**
     * <b>写入业务自定义字段</b>
     * <p>
     * 除了 txId 和 amount 还有其他字段（如 userId, gameId），
     * 请重写此方法并按顺序写入。如果没别的字段，留空即可。
     * </p>
     *
     * @param bytes 输出字节流
     */
    protected abstract void writeBizData(BytesOut<?> bytes);

    /**
     * <b>读取业务自定义字段</b>
     * <p>必须与 writeBizData 的顺序完全一致！</p>
     *
     * @param bytes 输入字节流
     */
    protected abstract void readBizData(BytesIn<?> bytes);
}
