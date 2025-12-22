package io.github.vevoly.example.wallet.adapter;

import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

/**
 * 服务发现适配器
 * <b>架构角色：</b> 连接 Nacos 和 Prometheus 之间的桥梁。
 * 定期从 Nacos 拉取服务实例列表，并将其转换为 Prometheus 支持的 <b>文件服务发现 (File-based Service Discovery)</b> 格式。
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Nacos to Prometheus Service Discovery Adapter.</b><br>
 * <b>Architectural Role:</b> Acts as a "bridge" or "translator" between Nacos and Prometheus.<br>
 * this class periodically fetches service instances from Nacos and converts them into the
 * <b>File-based Service Discovery</b> format that Prometheus understands.
 * </span>
 */
@Component
public class NacosToPrometheusAdapter {

    @Autowired
    private NacosServiceManager nacosServiceManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TARGET_FILE_PATH = "./docker/prometheus/targets/nacos_targets.json";

    @Scheduled(fixedRate = 30000) // 每 30 秒执行一次 / Every 30 seconds
    public void generateTargetFile() {
        System.out.println("正在从 Nacos 生成 Prometheus 目标文件...");

        try {
            NamingService namingService = nacosServiceManager.getNamingService();
            // 1. 从 Nacos 获取所有服务实例 / Get all service instances from Nacos
            List<Instance> instances = namingService.getAllInstances("j-atomic-ledger-example");
            // 2. 构建 Prometheus 格式的 JSON / Build Prometheus format JSON
            ArrayNode targetsArray = objectMapper.createArrayNode();

            for (Instance instance : instances) {
                // 只选择健康的实例 / Only select healthy instances
                if (instance.isEnabled() && instance.isHealthy()) {
                    // "ip:port"
                    String target = instance.getIp() + ":" + instance.getMetadata().get("management.server.port");

                    ObjectNode targetGroup = objectMapper.createObjectNode();
                    ArrayNode targets = targetGroup.putArray("targets");
                    targets.add(target);

                    ObjectNode labels = targetGroup.putObject("labels");
                    labels.put("instance", instance.getInstanceId());
                    labels.put("job", "nacos-discovered-apps");

                    targetsArray.add(targetGroup);
                }
            }

            // 3. 写入文件 / Write to file
            // todo 生产环境是需要一个 LeaderSelector 来确定哪个集群节点可以写文件，演示程序省略
            // todo In the production environment, a LeaderSelector is needed to determine which cluster node can write the file, the demonstration program is omitted
            File file = new File(TARGET_FILE_PATH);
            file.getParentFile().mkdirs();

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(targetsArray.toString());
            }
            System.out.println("Prometheus 目标文件已更新，发现 " + instances.size() + " 个实例。");
        } catch (Exception e) {
            System.err.println("生成 Prometheus 目标文件失败: " + e.getMessage());
        }
    }
}
