package io.github.vevoly.ledger.core.tools;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.google.common.hash.BloomFilter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.vevoly.ledger.core.snapshot.SnapshotContainer;

import java.io.File;
import java.io.FileInputStream;

/**
 * <h3>快照文件查看器 (Snapshot Viewer)</h3>
 *
 * <p>
 * 一个命令行工具，用于将 Kryo 序列化的快照文件 (.dat) dump 为人类可读的 JSON 格式。
 * </p>
 *
 * <h3>用法 (Usage):</h3>
 * <pre>
 * java -cp ... SnapshotViewer /path/to/snapshot.dat
 * </pre>
 *
 * @author vevoly
 * @since 1.1.0
 */
public class SnapshotViewer {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) {
        System.out.println("====== j-atomic-ledger Snapshot Viewer ======");

        // 1. 解析参数 / Parse arguments
        if (args.length == 0) {
            System.err.println("错误：请输入快照文件路径 / Error: Please enter the snapshot file path");
            System.err.println("用法 (Usage): java ... SnapshotViewer <file_path>");
            return;
        }
        String path = args[0];

        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            System.err.println("文件不存在或不是一个有效文件 / File isn't exist or it's not a valid file: " + path);
            return;
        }

        // 2. 初始化 Kryo / Initialize Kryo
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setReferences(true);
        // 注册 Guava BloomFilter 序列化器 / Register Guava BloomFilter serializer
        kryo.register(BloomFilter.class, new com.esotericsoftware.kryo.serializers.JavaSerializer());

        // 3. 读取并反序列化 / Read and deserialize
        try (Input input = new Input(new FileInputStream(file))) {
            System.out.println("正在读取快照文件: / Loading snapshot file" + file.getAbsolutePath());

            // 使用通用容器类来读取，即使不知道内部 S 的具体类型 / Use a generic container class to read, even if you don't know the specific type of S inside
            SnapshotContainer<?> container = kryo.readObject(input, SnapshotContainer.class);

            System.out.println("\n================ 快照内容 / Snapshot Content ================");
            System.out.println("最后 WAL 索引 (LastWalIndex): " + container.getLastWalIndex());
            System.out.println("\n>>> 内存状态 (State):");
            // 直接用 Gson 打印反序列化后的对象 / Print the deserialized object directly using Gson
            System.out.println(gson.toJson(container.getState()));
            System.out.println("\n>>> 幂等策略 (Idempotency):");
            // 策略对象可能很大，只打印类名和基本信息 / / The strategy object may be large, only print the class name and basic information
            System.out.println("  - Type: " + container.getIdempotencyStrategy().getClass().getName());
            System.out.println("========================================");

        } catch (Exception e) {
            System.err.println("读取快照失败 / Load failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
