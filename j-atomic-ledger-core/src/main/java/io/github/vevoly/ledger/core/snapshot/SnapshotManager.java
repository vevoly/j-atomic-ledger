package io.github.vevoly.ledger.core.snapshot;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.github.vevoly.ledger.api.IdempotencyStrategy;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * <h3>快照管理器 (Snapshot Manager)</h3>
 *
 * <p>
 * 负责内存状态的序列化与持久化。采用 <b>Kryo</b> 进行高性能序列化，并使用 <b>原子文件操作</b> 保证数据的完整性。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Snapshot Manager.</b><br>
 * Responsible for serialization and persistence of memory state.
 * Uses <b>Kryo</b> for high-performance serialization and <b>Atomic File Operations</b> to ensure data integrity.
 * </span>
 *
 * @param <S> 状态类型 (State Type)
 * @author vevoly
 * @since 1.0.0
 */
@Slf4j
public class SnapshotManager<S extends Serializable> {

    private final File snapshotDir;
    private static final String SNAPSHOT_FILE_NAME = "snapshot.dat";
    private static final String TEMP_FILE_NAME = "snapshot.tmp";

    /**
     * 构造函数 (Constructor).
     *
     * @param dataDir 指定的文件夹路径 (Specified directory path)
     */
    public SnapshotManager(String dataDir) {
        this.snapshotDir = new File(dataDir, "snapshot");
        if (!this.snapshotDir.exists()) {
            this.snapshotDir.mkdirs();
        }
    }

    /**
     * 执行快照保存 (原子写入).
     * <p>先写入临时文件，再执行原子重命名，防止写入过程中断导致文件损坏。</p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Execute Snapshot Saving (Atomic Write).</b><br>
     * Writes to a temp file first, then performs atomic rename to prevent file corruption during write interruption.
     * </span>
     *
     * @param walIndex 当前 WAL 索引 (Current WAL Index)
     * @param state 内存状态 (Memory State)
     * @param strategy 去重策略 (Idempotency Strategy)
     */
    public void save(long walIndex, S state, IdempotencyStrategy strategy) {
        File tempFile = new File(snapshotDir, TEMP_FILE_NAME);
        File finalFile = new File(snapshotDir, SNAPSHOT_FILE_NAME);
        Kryo kryo = createKryo();

        // 1. 写入临时文件 / Write to temporary file
        try (Output output = new Output(new FileOutputStream(tempFile))) {
            SnapshotContainer<S> container = new SnapshotContainer<>(walIndex, state, strategy);
            kryo.writeObject(output, container);
            output.flush();
        } catch (Exception e) {
            log.error("快照写入临时文件失败 / Failed to write snapshot to temp file", e);
            return;
        }

        // 2. 原子重命名 (Atomic Move) / Atomic Rename (Atomic Move)
        try {
            Files.move(tempFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("快照保存成功。Index: {} / Snapshot saved successfully. ", walIndex);
        } catch (Exception e) {
            log.error("快照文件重命名失败 / Failed to rename snapshot file", e);
        }
    }

    /**
     * 加载快照.
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Load Snapshot.</b>
     * </span>
     *
     * @return 快照容器对象，若不存在或损坏则返回 null (Snapshot container, or null if not exists or corrupted)
     */
    @SuppressWarnings("unchecked")
    public SnapshotContainer<S> load() {
        File file = new File(snapshotDir, SNAPSHOT_FILE_NAME);
        if (!file.exists()) {
            log.info("未发现快照文件，将从头开始恢复。/ No snapshot found, starting recovery from scratch.");
            return null;
        }

        Kryo kryo = createKryo();
        try (Input input = new Input(new FileInputStream(file))) {
            log.info("发现快照文件，正在加载... / Snapshot found, loading...");
            // kryo 自动处理泛型 / Kryo handles generics automatically
            return kryo.readObject(input, SnapshotContainer.class);
        } catch (Exception e) {
            log.error("快照文件损坏或版本不兼容，将忽略快照。 / Snapshot file corrupted or incompatible, ignoring.", e);
            return null;
        }
    }

    /**
     * 创建 Kryo 实例 (非线程安全，每次创建新的) / Create Kryo instance (Not thread-safe, create new each time)
     * @return
     */
    private Kryo createKryo() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false); // 允许未注册的类 / Allow unregistered classes
        kryo.setReferences(true); // 允许循环引用 / Allow circular references
        // 为 Guava BloomFilter 注册 JavaSerializer / Register JavaSerializer for Guava BloomFilter
        // 这样 Kryo 就会调用 BloomFilter 自己的 writeObject/readObject / Kryo will use BloomFilter's own writeObject/readObject
        // 而不是去反射它的私有字段（避开 Striped64 访问权限问题） / Instead of reflecting its private fields (Avoiding Striped64 access issues)
        kryo.register(com.google.common.hash.BloomFilter.class, new com.esotericsoftware.kryo.serializers.JavaSerializer());
        return kryo;
    }
}
