package io.github.vevoly.ledger.core.wal;

import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;

import java.io.Closeable;
import java.io.File;

/**
 * <h3>WAL 日志管理器 (Write-Ahead Log Manager)</h3>
 *
 * <p>
 * 基于 <b>OpenHFT Chronicle Queue</b> 实现的高性能持久化组件。
 * 它利用内存映射文件 (Memory Mapped File) 实现纳秒级的写入延迟，并保证数据在进程崩溃时不丢失。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>WAL (Write-Ahead Log) Manager.</b><br>
 * High-performance persistence component based on <b>OpenHFT Chronicle Queue</b>.<br>
 * It uses Memory Mapped Files to achieve nanosecond-level write latency and ensures zero data loss on process crashes.
 * </span>
 *
 * @author vevoly
 * @since 1.0.0
 */
public class WalManager implements Closeable {

    private final SingleChronicleQueue queue;
    private final ExcerptAppender appender;

    /**
     * 构造函数 (Constructor).
     *
     * @param dataDir 数据存储根目录 (Parent directory for data storage). e.g. /data/ledger/core-p0
     */
    public WalManager(String dataDir) {
        // 创建持久化队列
        File walDir = new File(dataDir, "wal");
        this.queue = SingleChronicleQueueBuilder.binary(walDir).build();
        this.appender = queue.acquireAppender();
    }

    /**
     * 写入命令对象 (Write Command).
     * <p>
     * 显式传入对象的 Class 类型，以消除 Chronicle Wire 的类型匹配警告。
     * 如果对象实现了 {@code BytesMarshallable} 接口，Chronicle 会自动切换到二进制零拷贝模式，性能提升 10 倍。
     * </p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Write Command Object.</b><br>
     * Explicitly passes the object's Class type to eliminate Chronicle Wire warnings.<br>
     * If the object implements {@code BytesMarshallable}, Chronicle automatically switches to binary zero-copy mode, boosting performance by 10x.
     * </span>
     *
     * @param command 实现了 Serializable 或 BytesMarshallable 的命令对象 (Command object)
     * @return 写入后的索引 (Index after writing)
     */
    public long write(Object command) {
        // 使用 Chronicle Wire 格式写入对象，Chronicle 会自动检测是否实现了 BytesMarshallable，如果是，速度提升 10 倍
        // Use Chronicle Wire to write objects, Chronicle automatically detects whether BytesMarshallable is implemented, if so, speed is improved by 10x.
        appender.writeDocument(w -> w.write("data").object(command.getClass(), command));
        return appender.lastIndexAppended();
    }

    /**
     * 创建一个读取器 (Create Tailer).
     * <p>用于系统启动时的灾难恢复 (Data Recovery)。</p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Create a Tailer (Reader).</b><br>
     * Used for disaster recovery during system startup.
     * </span>
     *
     * @return 日志读取器 (ExcerptTailer)
     */
    public ExcerptTailer createTailer() {
        return queue.createTailer();
    }

    /**
     * 关闭资源 (Close Resources).
     */
    @Override
    public void close() {
        if (queue != null && !queue.isClosed()) {
            queue.close();
        }
    }
}
