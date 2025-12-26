package io.github.vevoly.ledger.core.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.vevoly.ledger.api.LedgerCommand;
import io.github.vevoly.ledger.api.constants.JAtomicLedgerConstant;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
            log.info("正在读取目录 / Loading dir: " + dir.getAbsolutePath());
            log.info("========================================");
            while (true) {
                try (DocumentContext dc = tailer.readingDocument()) {
                    if (!dc.isPresent()) {
                        break;
                    }

                    String recordJson;
                    try {
                        // 尝试反序列化
                        Object cmd = dc.wire().read(JAtomicLedgerConstant.WAL_KEY_FIELD_NAME).object(commandClass);
                        recordJson = gson.toJson(cmd);

                    } catch (Exception e) {
                        // 如果反序列化失败，降级为文本模式
                        String rawData = dc.wire().toString().replace("\"", "\\\"");
                        recordJson = String.format("{\"error\": \"Deserialization failed at index %d\", \"rawData\": \"%s\"}", dc.index(), rawData);
                        log.error("反序列化失败，Index: {}", dc.index(), e);
                    }
                    results.add(recordJson);
                }
            }
            log.info("========================================");
            log.info("读取完成，共 " + results.size() + " 条记录。");
        } catch (Exception e) {
            log.error("读取 WAL 失败 / Load WAL failed", e);
            throw e;
        }
        return results;
    }
}
