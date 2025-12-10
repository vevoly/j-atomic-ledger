package io.github.vevoly.ledger.core.wal;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;

import java.io.Closeable;
import java.io.File;

/**
 * WAL 日志管理器
 */
public class WalManager implements Closeable {

    private final SingleChronicleQueue queue;
    private final ExcerptAppender appender;

    /**
     * @param dataDir   接收具体文件夹路径
     */
    public WalManager(String dataDir) {
        // 创建持久化队列
        File walDir = new File(dataDir, "wal");
        this.queue = SingleChronicleQueueBuilder.binary(walDir).build();
        this.appender = queue.acquireAppender();
    }

    /**
     * 写入命令对象
     * @param command 实现了 Serializable 的命令
     * @return 写入后的 index
     */
    public long write(Object command) {
        // 使用 Chronicle Wire 格式写入对象
        appender.writeDocument(w -> w.write("data").object(command));
        return appender.lastIndexAppended();
    }

    /**
     * 创建一个读取器 (用于恢复)
     */
    public ExcerptTailer createTailer() {
        return queue.createTailer();
    }

    @Override
    public void close() {
        if (queue != null && !queue.isClosed()) {
            queue.close();
        }
    }
}
