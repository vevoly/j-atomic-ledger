package endpoint;

import io.github.vevoly.ledger.api.LedgerBootstrap;
import io.github.vevoly.ledger.api.LedgerCommand;
import io.github.vevoly.ledger.core.admin.JAtomicLedgerAdminUtils;
import io.github.vevoly.ledger.starter.JAtomicLedgerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * <h3>j-atomic-ledger 管理端点</h3>
 * <p>
 * 暴露运维操作接口，如在线查看 WAL。
 * 默认关闭，需通过配置显式开启。
 * </p>
 * @since 1.2.1
 * @author vevoly
 */
@Component
@Endpoint(id = "j-atomic-ledger") // 端点 ID: /actuator/j-atomic-ledger
@ConditionalOnClass(JAtomicLedgerAdminUtils.class)
@ConditionalOnProperty(prefix = "j-atomic-ledger.admin", name = "enabled", havingValue = "true")
public class JAtomicLedgerAdminEndpoint {

    @Autowired
    private JAtomicLedgerProperties properties;

    // 注入用户定义的 Bootstrap，以获取 Command Class
    @Autowired
    private LedgerBootstrap<?, ?> bootstrap;

    /**
     * 在线查看指定分片的 WAL 日志.
     *
     * @param partitionIndex 分片编号 (e.g., 0, 1, 2...)
     * @return WAL 记录的 JSON 列表
     */
    @ReadOperation // 映射到 GET /actuator/j-atomic-ledger/wal/{partitionIndex}
    public List<String> viewWal(@Selector String partitionIndex) {
        try {
            int index = Integer.parseInt(partitionIndex);

            // 1. 自动构建路径
            String partitionName = String.format("%s-p%d", properties.getEngineName(), index);
            String walPath = properties.getBaseDir() + File.separator +
                    properties.getEngineName() + File.separator +
                    partitionName + File.separator + "wal";

            // 2. 调用核心工具类
            @SuppressWarnings("unchecked")
            Class<? extends LedgerCommand> commandClass = (Class<? extends LedgerCommand>) bootstrap.getCommandClass();

            return JAtomicLedgerAdminUtils.dumpWal(walPath, commandClass);

        } catch (NumberFormatException e) {
            return Collections.singletonList("Error: Partition index must be an integer.");
        } catch (Exception e) {
            return Collections.singletonList("Error: " + e.getMessage());
        }
    }
}
