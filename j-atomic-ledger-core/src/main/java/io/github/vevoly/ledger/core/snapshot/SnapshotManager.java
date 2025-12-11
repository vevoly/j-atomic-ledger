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
 * 快照管理器
 * @param <S>
 *
 * @author vevoly
 */
@Slf4j
public class SnapshotManager<S extends Serializable> {

    private final File snapshotDir;
    private static final String SNAPSHOT_FILE_NAME = "snapshot.dat";
    private static final String TEMP_FILE_NAME = "snapshot.tmp";

    /**
     * @param dataDir   指定的文件夹路径
     */
    public SnapshotManager(String dataDir) {
        this.snapshotDir = new File(dataDir, "snapshot");
        if (!this.snapshotDir.exists()) {
            this.snapshotDir.mkdirs();
        }
    }

    /**
     * 执行快照保存
     */
    public void save(long walIndex, S state, IdempotencyStrategy strategy) {
        File tempFile = new File(snapshotDir, TEMP_FILE_NAME);
        File finalFile = new File(snapshotDir, SNAPSHOT_FILE_NAME);

        Kryo kryo = createKryo();

        // 1. 写入临时文件
        try (Output output = new Output(new FileOutputStream(tempFile))) {
            SnapshotContainer<S> container = new SnapshotContainer<>(walIndex, state, strategy);
            kryo.writeObject(output, container);
            output.flush();
        } catch (Exception e) {
            log.error("快照写入临时文件失败", e);
            return;
        }

        // 2. 原子重命名 (Atomic Move)
        try {
            Files.move(tempFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("快照保存成功。Index: {}", walIndex);
        } catch (Exception e) {
            log.error("快照文件重命名失败", e);
        }
    }

    /**
     * 加载快照
     */
    @SuppressWarnings("unchecked")
    public SnapshotContainer<S> load() {
        File file = new File(snapshotDir, SNAPSHOT_FILE_NAME);
        if (!file.exists()) {
            log.info("未发现快照文件，将从头开始恢复。");
            return null;
        }

        Kryo kryo = createKryo();
        try (Input input = new Input(new FileInputStream(file))) {
            // kryo 自动处理泛型
            return kryo.readObject(input, SnapshotContainer.class);
        } catch (Exception e) {
            log.error("快照文件损坏或版本不兼容，将忽略快照。", e);
            return null;
        }
    }

    // 创建 Kryo 实例 (非线程安全，每次创建新的)
    private Kryo createKryo() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false); // 允许未注册的类
        kryo.setReferences(true); // 允许循环引用
        // 为 Guava BloomFilter 注册 JavaSerializer
        // 这样 Kryo 就会调用 BloomFilter 自己的 writeObject/readObject，
        // 而不是去反射它的私有字段（避开 Striped64 访问权限问题）。
        kryo.register(com.google.common.hash.BloomFilter.class, new com.esotericsoftware.kryo.serializers.JavaSerializer());

        return kryo;
    }
}
