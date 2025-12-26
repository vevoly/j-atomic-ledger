package io.github.vevoly.ledger.core.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.vevoly.ledger.api.constants.JAtomicLedgerConstant;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <h3>WAL 日志文件查看器 (WAL Viewer)</h3>
 *
 * <p>
 * 一个命令行工具，用于将 Chronicle Queue 的二进制 WAL 文件 dump 为人类可读的格式。
 *
 * <h3>用法 (Usage):</h3>
 * <pre>
 * // 在生产服务器上 / On production server:
 * java -cp "your-app.jar:j-atomic-ledger-core.jar" io.github.vevoly.ledger.core.tools.WalViewer /path/to/wal com.your.CommandClass
 * </pre>
 *
 * <hr>
 * <span style="color: gray; font-size: 0.9em;">
 * <b>A command-line tool to dump binary WAL files into human-readable format.</b><br>
 *
 * @author vevoly
 * @since 1.1.0
 */
public class WalViewer {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) {
        System.out.println("====== j-atomic-ledger WAL Viewer ======");

        // 1. 解析参数
        if (args.length == 0) {
            System.err.println("错误：请输入 WAL 目录路径 / Error: Please input WAL directory path");
            System.err.println("用法 (Usage): java ... WalViewer <path> [optional_class_name]");
            return;
        }
        String path = args[0];
        String className = args.length > 1 ? args[1] : null;

        File dir = new File(path);

        try (ChronicleQueue queue = SingleChronicleQueueBuilder.binary(dir).readOnly(true).build()) {
            ExcerptTailer tailer = queue.createTailer();
            tailer.toStart();

            System.out.println("正在读取目录 / Loading dir: " + dir.getAbsolutePath());
            System.out.println("========================================\n");

            long count = 0;
            while (true) {
                final AtomicBoolean documentWasRead = new AtomicBoolean(false);

                try {
                    boolean found = tailer.readDocument(r -> {
                        documentWasRead.set(true);
                        long index = tailer.index();
                        System.out.println("---------- Index: " + index + " ----------");

                        if (className != null) {
                            try {
                                Class<?> clazz = Class.forName(className);
                                BytesMarshallable instance = (BytesMarshallable) clazz.getDeclaredConstructor().newInstance();
                                Object cmd = r.read(JAtomicLedgerConstant.WAL_KEY_FIELD_NAME).object(clazz);
                                if (cmd != null) {
                                    System.out.println(gson.toJson(cmd));
                                } else {
                                    System.err.println("【警告】解析出的对象为 null / Parsed object is null.");
                                }

                            } catch (Exception e) {
                                System.err.println("【错误】反序列化失败，回退到文本模式... / Deserialization failed, falling back to text mode...");
                                System.out.println(r.toString()); // 降级打印 YAML
                            }
                        } else {
                            System.out.println(r.toString());
                        }
                        System.out.println();
                    });
                    if (!found && !documentWasRead.get()) {
                        break;
                    }

                } catch (Exception e) {
                    System.err.println("读取 WAL 时发生严重 IO 错误，已终止。/ Critical IO error, stopping.");
                    e.printStackTrace();
                    break;
                }
                count++;
            }

            System.out.println("========================================");
            System.out.println("读取完成，共 " + count + " 条记录。");

        } catch (Exception e) {
            System.err.println("读取 WAL 失败 / Load WAL failed: " + e.getMessage());
        }
    }
}
