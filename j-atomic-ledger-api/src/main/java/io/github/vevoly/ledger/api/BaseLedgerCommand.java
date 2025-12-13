package io.github.vevoly.ledger.api;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

import java.io.Serial;
import java.util.concurrent.CompletableFuture;

/**
 * <h3>核心命令基类 (Core Command Base)</h3>
 *
 * <p>
 * 这是高性能账本引擎的基石。为了确保 <b>百万级 TPS</b> 和 <b>Zero GC (零垃圾回收)</b>，
 * 本类强制规定了金额的数据类型和序列化方式。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>English Documentation:</b><br>
 * The cornerstone of the high-performance ledger engine.
 * To ensure <b>Million-Level TPS</b> and <b>Zero GC</b>, this class enforces the data type
 * and serialization mechanism for the transaction amount.
 * </span>
 *
 * <h3>核心设计规范 (Design Specification):</h3>
 * <ul>
 *     <li>
 *         <b>强制使用 long 金额 (Force long Amount):</b><br>
 *         严禁在核心链路使用 BigDecimal。请使用最小货币单位（如：厘）进行存储。<br>
 *         <span style="color: gray;">BigDecimal is strictly forbidden in the core path to avoid GC overhead. Use atomic units (e.g., cents).</span>
 *     </li>
 *     <li>
 *         <b>二进制序列化 (Binary Serialization):</b><br>
 *         实现了 {@link BytesMarshallable}，直接操作内存字节，跳过反射，性能提升 10 倍。<br>
 *         <span style="color: gray;">Implements BytesMarshallable to operate directly on memory bytes, skipping reflection for 10x performance.</span>
 *     </li>
 * </ul>
 *
 * @author vevoly
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode
public abstract class BaseLedgerCommand implements LedgerCommand, BytesMarshallable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务唯一ID (Transaction ID).
     * <p>
     * 用于 <b>幂等去重 (Idempotency)</b>。引擎的 BloomFilter 或 LRU 策略将根据此 ID 判断请求是否重复。
     * 建议使用 UUID 或 "业务类型_单号" 格式。
     * </p>
     * <span style="color: gray;">Unique ID for idempotency deduplication. Suggest using UUID or "Type_OrderNo".</span>
     */
    protected String txId;

    /**
     * 变动金额 (Transaction Amount).
     * <p>
     * <b>强制规范：</b>必须使用 long 类型，单位建议为系统的最小货币单位（例如：厘/分）。
     * </p>
     * <span style="color: gray;">Must use long type. Recommended unit: the smallest currency unit (e.g., cent/milli).</span>
     */
    protected long amount;

    /**
     * 异步结果通知句柄 (Async Result Handle).
     * <p>
     * 作用：连接 "同步的 Controller" 与 "异步的 Disruptor"。
     * 必须标记为 <b>transient</b>，因为它仅用于内存通信，<b>绝不应该</b>被序列化到 WAL 或快照中。
     * </p>
     * <span style="color: gray;">
     * Bridges synchronous Controller and asynchronous Disruptor.
     * Must be transient; never persisted to WAL or Snapshots.
     * </span>
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
     * <h3>二进制序列化 (Write to Bytes)</h3>
     * <p>
     * 采用<b>模板方法模式</b>：强制先写入核心字段 (txId, amount)，然后调用 {@link #writeBizData(BytesOut)} 写入用户字段。
     * 这里的 final 关键字保证了序列化顺序的统一性。
     * </p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Template Method Pattern:</b> Enforces writing core fields first, then calls abstract method for user fields.
     * The 'final' keyword ensures consistent serialization order.
     * </span>
     *
     * @param bytes 输出字节流 (Output byte stream)
     */
    @Override
    public final void writeMarshallable(BytesOut<?> bytes) {
        // 1. 先写核心字段 / write core fields first
        bytes.writeUtf8(txId);
        bytes.writeLong(amount);
        // 2. 再写用户的扩展字段 / write user fields next
        writeBizData(bytes);
    }

    /**
     * <h3>二进制反序列化 (Read from Bytes)</h3>
     * <p>
     * 逻辑同写入方法，保证读取顺序与写入顺序严格一致。
     * </p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * Logic matches the write method to ensure strict order consistency.
     * </span>
     *
     * @param bytes 输入字节流 (Input byte stream)
     */
    @Override
    public final void readMarshallable(BytesIn<?> bytes) {
        // 1. 先读核心字段 / read core fields first
        this.txId = bytes.readUtf8();
        this.amount = bytes.readLong();
        // 2. 再读用户的扩展字段 / read user fields next
        readBizData(bytes);
    }

    /**
     * <b>写入业务自定义字段 (Write Custom Fields).</b>
     * <p>
     * 如果您的 Command 除了 txId 和 amount 还有其他字段（如 userId, gameId），
     * 请重写此方法并按顺序写入。如果没别的字段，留空即可。
     * </p>
     * <span style="color: gray;">Override to write extra fields (e.g., userId). Leave empty if none.</span>
     *
     * @param bytes 输出字节流 (Output byte stream)
     */
    protected abstract void writeBizData(BytesOut<?> bytes);

    /**
     * <b>读取业务自定义字段 (Read Custom Fields).</b>
     * <p>
     * 必须与 writeBizData 的顺序完全一致！
     * </p>
     * <span style="color: gray;">Must match the order in writeBizData exactly!</span>
     *
     * @param bytes 输入字节流 (Input byte stream)
     */
    protected abstract void readBizData(BytesIn<?> bytes);
}
