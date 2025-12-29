package io.github.vevoly.ledger.core.tools;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.vevoly.ledger.api.LedgerCommand;
import io.github.vevoly.ledger.api.constants.JAtomicLedgerConstant;
import io.github.vevoly.ledger.core.snapshot.SnapshotContainer;
import io.github.vevoly.ledger.core.snapshot.SnapshotManager;
import io.github.vevoly.ledger.core.wal.WalPageResult;
import io.micrometer.common.lang.Nullable;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.TailerDirection;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.DocumentContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <h3>核心运维工具类 (Ledger Admin Utilities)</h3>
 *
 * <p>
 * 提供了一系列静态方法，用于分析 WAL 和快照文件。
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
    private static final Gson gsonWithExclusions = new GsonBuilder()
            .setPrettyPrinting()
            .setExclusionStrategies(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    // 如果字段的类型是 BloomFilter，跳过！
                    return com.google.common.hash.BloomFilter.class.isAssignableFrom(f.getDeclaredClass());
                }

                @Override
                public boolean shouldSkipClass(Class<?> aClass) {
                    return false;
                }
            })
            .create();

    /**
     * 分页 Dump WAL.
     <p>
     * 采用<b>游标分页 (Cursor Pagination)</b>，从后向前读取，性能极高。
     * </p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Paginated WAL Dump.</b><br>
     * Uses Cursor Pagination and reads backward for high performance.
     * </span>
     *
     * @param walPath       WAL 目录的路径
     * @param commandClass  要解析的 Command 类的 Class 对象
     * @param cursor        起始游标 (Chronicle Index)
     * @param pageSize      每页的大小
     * @param isBackward    是否向前（旧）翻页
     * @param businessId    业务 ID
     * @param txId          事务 ID
     * @return 包含所有记录的 JSON 字符串列表
     * @throws FileNotFoundException 如果目录不存在
     */
    public static <C extends LedgerCommand> WalPageResult<String> dumpWalPage(
            String walPath, Class<C> commandClass,
            @Nullable String cursor, int pageSize, boolean isBackward,
            @Nullable String businessId, @Nullable String txId) throws Exception {
        File dir = new File(walPath);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new FileNotFoundException("WAL directory not found: " + walPath);
        }
        List<String> results = new ArrayList<>();
        String nextCursor = null;
        String prevCursor = null;
        boolean hasMore = false;
        boolean hasPrev = false;

        try (ChronicleQueue queue = SingleChronicleQueueBuilder.binary(dir).readOnly(true).build()) {
            ExcerptTailer tailer = queue.createTailer();
            tailer.direction(isBackward ? TailerDirection.BACKWARD : TailerDirection.FORWARD);

            // 1.定位
            if (cursor != null) {
                // 从指定的 cursor 开始 / Start from the specified cursor
                if (!tailer.moveToIndex(Long.decode(cursor))) {
                    // 如果 cursor 无效，直接返回空 / Return empty if the cursor is invalid
                    return new WalPageResult<>(Collections.emptyList(), null, null, false, false);
                }
            } else {
                if (isBackward) {
                    tailer.toEnd(); // 向前翻页，从末尾开始
                } else {
                    tailer.toStart(); // 向后翻页，从开头开始
                }
            }

            // 记录进入循环前的 index，用于计算 prevCursor/nextCursor
            long startIndex = tailer.index();

            // 2. 循环读取
            while (results.size() < pageSize) {
                try (DocumentContext dc = tailer.readingDocument()) {
                    if (!dc.isPresent()) {
                        break;
                    }
                    try {
                        // 尝试反序列化 / Try to deserialize
                        C cmd = dc.wire().read(JAtomicLedgerConstant.WAL_KEY_FIELD_NAME).object(commandClass);
                        if (null == cmd) {
                            continue;
                        }
                        // 条件过滤 / Condition filter logic
                        boolean match = true;
                        if (businessId != null && !businessId.equals(cmd.getRoutingKey())) {
                            match = false;
                        }
                        if (txId != null && !txId.equals(cmd.getUniqueId())) {
                            match = false;
                        }
                        if (match) {
                            results.add(gson.toJson(cmd));
                        }

                    } catch (Exception e) {
                        // 如果反序列化失败，降级为文本模式
                        String rawData = dc.wire().toString().replace("\"", "\\\"");
                        results.add(String.format("{\"error\": \"Deserialization failed at index %d\", \"rawData\": \"%s\"}", dc.index(), rawData));
                        log.error("反序列化失败，Index: {}", dc.index(), e);
                    }
                }
            }
            // 3. 计算游标和分页状态
            long lastReadIndex = tailer.index();

            if (isBackward) {
                // 我们是向旧的翻，所以“下一页”是更旧的
                // hasNextPage (旧) -> hasPrev
                prevCursor = (results.size() == pageSize && tailer.readingDocument().isPresent()) ? "0x" + Long.toHexString(lastReadIndex) : null;
                hasPrev = prevCursor != null;
                // 只有当不是从文件末尾开始时，才可能有更新
                hasMore = cursor != null;
                if (hasMore) {
                    nextCursor = "0x" + Long.toHexString(startIndex);
                }
                Collections.reverse(results);
            } else {
                // 向新的翻
                // hasNextPage (新) -> hasMore
                nextCursor = (results.size() == pageSize && tailer.readingDocument().isPresent()) ? "0x" + Long.toHexString(lastReadIndex) : null;
                hasMore = nextCursor != null;
                // 检查反方向（旧）是否还有数据
                hasPrev = cursor != null;
                if (hasPrev) prevCursor = "0x" + Long.toHexString(startIndex);
            }

            return new WalPageResult<>(results, nextCursor, prevCursor, hasMore, hasPrev);
        } catch (Exception e) {
            log.error("读取 WAL 失败 / Load WAL failed", e);
            throw e;
        }
    }

    /**
     * <h3>全量加载并 Dump 快照文件 (Load & Dump Snapshot)</h3>
     * <p>
     * 将 Kryo 序列化的快照文件反序列化为对象，并转换为人类可读的 JSON 字符串。
     * <b>注意：</b> 快照是全量加载，不提供分页。
     * </p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Load & Dump Snapshot.</b><br>
     * Deserializes a Kryo snapshot file and converts it to a human-readable JSON string.<br>
     * <b>Note:</b> Snapshots are loaded in full; pagination is not supported.
     * </span>
     *
     * @param snapshotPath 快照文件<b>完整路径</b> / Full path to the snapshot file.
     * @return 快照内容的 JSON 字符串 / JSON string of the snapshot content.
     * @throws Exception 如果文件不存在或反序列化失败.
     */
    public static String dumpSnapshot(String snapshotPath) throws Exception {
        File dir = new File(snapshotPath);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new FileNotFoundException("Snapshot directory not found: " + snapshotPath);
        }
        String dirPath = dir.getParent();
        SnapshotManager<Serializable> tempManager = new SnapshotManager<>(dirPath);
        SnapshotContainer<Serializable> container = tempManager.load();
        if (container == null) {
            return "{\"error\": \"Failed to load snapshot container.\"}";
        }
        return gsonWithExclusions.toJson(container);
    }
}
