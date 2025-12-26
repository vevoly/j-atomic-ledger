package io.github.vevoly.ledger.starter.endpoint;

import io.github.vevoly.ledger.api.LedgerBootstrap;
import io.github.vevoly.ledger.api.LedgerCommand;
import io.github.vevoly.ledger.core.admin.JAtomicLedgerAdminUtils;
import io.github.vevoly.ledger.api.constants.JAtomicLedgerConstant;
import io.github.vevoly.ledger.starter.JAtomicLedgerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.File;
import java.util.List;

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
     * URL: GET /actuator/atomic-ledger/wal/{partitionIndex}
     */
    @GetMapping("/wal/{partitionIndex}")
    public ResponseEntity<List<String>> viewWal(@PathVariable String partitionIndex) {
        try {
            int index = Integer.parseInt(partitionIndex);

            // 1. 自动构建路径
            String partitionName = String.format("%s-p%d", properties.getEngineName(), index);
            String walPath = properties.getBaseDir() + File.separator + properties.getEngineName() + File.separator +
                    JAtomicLedgerConstant.CLUSTER_NODE_MARK + properties.getCluster().getNodeId() + File.separator +
                    partitionName + File.separator + JAtomicLedgerConstant.WAL_DIR;

            // 2. 调用核心工具类
            @SuppressWarnings("unchecked")
            Class<? extends LedgerCommand> commandClass = (Class<? extends LedgerCommand>) bootstrap.getCommandClass();
            List<String> logs = JAtomicLedgerAdminUtils.dumpWal(walPath, commandClass);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(List.of("Error: " + e.getMessage()));
        }
    }

}
