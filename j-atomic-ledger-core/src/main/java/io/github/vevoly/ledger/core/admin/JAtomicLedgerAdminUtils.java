package io.github.vevoly.ledger.core.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.vevoly.ledger.api.LedgerCommand;
import lombok.NoArgsConstructor;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.DocumentContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * <h3>核心运维工具类 (Ledger Admin Utilities)</h3>
 *
 * <p>
 * 提供了一系列静态方法，用于离线分析 WAL 和快照文件。
 * 可被 Actuator 端点或自定义的工具调用。
 * </p>
 *
 * @author vevoly
 * @since 1.2.1
 */
@NoArgsConstructor
public class JAtomicLedgerAdminUtils {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 将 WAL 目录的内容 Dump 为 JSON 字符串列表.
     *
     * @param walPath       WAL 目录的路径
     * @param commandClass  要解析的 Command 类的 Class 对象
     * @param <C>           Command 的类型
     * @return 包含所有记录的 JSON 字符串列表
     * @throws FileNotFoundException 如果目录不存在
     */
    public static <C extends LedgerCommand> List<String> dumpWal(String walPath, Class<C> commandClass) throws Exception {
        File dir = new File(walPath);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new FileNotFoundException("WAL directory not found: " + walPath);
        }

        List<String> results = new ArrayList<>();

        try (ChronicleQueue queue = SingleChronicleQueueBuilder.binary(dir).readOnly(true).build()) {
            ExcerptTailer tailer = queue.createTailer();
            tailer.toStart();

            while (true) {
                try (DocumentContext dc = tailer.readingDocument()) {
                    if (!dc.isPresent()) break;

                    String record;
                    try {
                        C cmd = dc.wire().read("data").object(commandClass);
                        if (cmd != null) {
                            record = gson.toJson(cmd);
                        } else {
                            record = "{\"error\": \"Parsed command is null at index " + dc.index() + "\"}";
                        }
                    } catch (Exception e) {
                        // 降级为文本模式
                        record = "{\"error\": \"Deserialization failed at index " + dc.index() + "\", \"rawData\": \"" + dc.wire().toString().replace("\"", "\\\"") + "\"}";
                    }
                    results.add(record);
                }
            }
        }
        return results;
    }
}
