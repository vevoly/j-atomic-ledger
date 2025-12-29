package io.github.vevoly.ledger.starter.endpoint;

import io.github.vevoly.ledger.api.LedgerBootstrap;
import io.github.vevoly.ledger.api.LedgerCommand;
import io.github.vevoly.ledger.core.tools.JAtomicLedgerAdminUtils;
import io.github.vevoly.ledger.api.constants.JAtomicLedgerConstant;
import io.github.vevoly.ledger.core.wal.WalPageResult;
import io.github.vevoly.ledger.starter.JAtomicLedgerProperties;
import io.micrometer.common.lang.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.File;

/**
 * <h3>Admin Endpoint 的 Web 扩展</h3>
 * <p>
 * 使用 @RestControllerEndpoint 可以让我们像写普通 Controller 一样
 * 自由地定义 Actuator 端点的 URL 路径和 HTTP 方法。
 * </p>
 *
 * @since 1.2.2
 * @author vevoly
 */
@RestControllerEndpoint(id = JAtomicLedgerConstant.J_ATOMIC_LEDGER_ID) // 挂在 /actuator/j-atomic-ledger 之下
@ConditionalOnProperty(prefix = "j-atomic-ledger.admin", name = "enabled", havingValue = "true")
public class JAtomicLedgerAdminEndpoint {

    @Autowired
    private JAtomicLedgerProperties properties;
    @Autowired
    private LedgerBootstrap<?, ?> bootstrap;

    /**
     * 在线查看 WAL
     * URL: GET /actuator/j-atomic-ledger/wal/{partitionIndex}
     */
    @GetMapping("/wal/{partitionIndex}")
    public ResponseEntity<WalPageResult<String>> viewWal(
            @PathVariable String partitionIndex,
            @RequestParam(required = false) String businessId,
            @RequestParam(required = false) String txId,
            @Nullable String cursor,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "true") boolean isBackward
    ) {
        try {
            // 1. 自动构建路径
            String walPath = getDirPath(JAtomicLedgerConstant.WAL_DIR, partitionIndex);
            // 2. 调用核心工具类
            @SuppressWarnings("unchecked")
            Class<? extends LedgerCommand> commandClass = (Class<? extends LedgerCommand>) bootstrap.getCommandClass();
            WalPageResult<String> walPageResult = JAtomicLedgerAdminUtils.dumpWalPage(walPath, commandClass, cursor, pageSize, true, businessId, txId);
            return ResponseEntity.ok(walPageResult);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new WalPageResult<>());
        }
    }

    /**
     * 在线查看快照
     * URL: GET /actuator/j-atomic-ledger/snapshot/{partitionIndex}
     * @param partitionIndex    分片号
     * @return
     */
    @GetMapping("/snapshot/{partitionIndex}")
    public ResponseEntity<String> viewSnapshot(@PathVariable String partitionIndex) {
        try {

            // 1. 构建快照文件路径
            String snapshotPath = getDirPath(JAtomicLedgerConstant.SNAPSHOT_DIR, partitionIndex);
            // 2. 调用新的工具类
            // Snapshot 不需要 CommandClass，因为它依赖 Kryo 自身的类型信息
            String snapshotJson = JAtomicLedgerAdminUtils.dumpSnapshot(snapshotPath);
            return ResponseEntity.ok(snapshotJson);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    /**
     * 获取文件夹路径
     * @param dirType           文件夹类型
     * @param partitionIndex    分片索引号
     * @return
     */
    private String getDirPath(String dirType, String partitionIndex) {
        String partitionName = String.format("%s-p%s", properties.getEngineName(), partitionIndex);
        String dirName = dirType.equalsIgnoreCase(JAtomicLedgerConstant.WAL_DIR) ? JAtomicLedgerConstant.WAL_DIR : JAtomicLedgerConstant.SNAPSHOT_DIR;
        return properties.getBaseDir() + File.separator + properties.getEngineName() + File.separator +
                JAtomicLedgerConstant.CLUSTER_NODE_MARK + properties.getCluster().getNodeId() + File.separator +
                partitionName + File.separator + dirName;
    }
}
