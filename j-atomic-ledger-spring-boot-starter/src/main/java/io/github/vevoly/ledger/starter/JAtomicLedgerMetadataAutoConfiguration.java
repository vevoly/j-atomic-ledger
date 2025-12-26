package io.github.vevoly.ledger.starter;

import io.github.vevoly.ledger.api.constants.JAtomicLedgerConstant;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.Map;


/**
 * 元数据上报 Nacos
 * 自动向 Nacos 注册中心注入 j-atomic-ledger 的元数据
 * 仅在集群模式下生效
 *
 * @since 1.2.2
 * @author vevoly
 */
@Slf4j
@Configuration
@AllArgsConstructor
@ConditionalOnClass(name = "com.alibaba.cloud.nacos.registry.NacosRegistration")
@AutoConfigureAfter(name = "com.alibaba.cloud.nacos.ribbon.NacosRibbonClientConfiguration")
@ConditionalOnProperty(prefix = "j-atomic-ledger.admin", name = "enabled", havingValue = "true")
public class JAtomicLedgerMetadataAutoConfiguration implements ApplicationListener<WebServerInitializedEvent> {

    private ApplicationContext applicationContext;

    /**
     * 在 Bean 初始化完成后，上报元数据
     */
    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        log.info("检测到 Web 服务器已启动，开始尝试上报 j-atomic-ledger 元数据...");
        try {
            Object registration = applicationContext.getBean("nacosRegistration");
            // 使用反射调用 getMetadata()
            Method getMetadata = registration.getClass().getMethod("getMetadata");
            Map<String, String> metadata = (Map<String, String>) getMetadata.invoke(registration);
            // 将引擎的核心配置，动态添加到元数据中
            JAtomicLedgerProperties properties = applicationContext.getBean(JAtomicLedgerProperties.class);
            metadata.put(JAtomicLedgerConstant.META_PARTITIONS, String.valueOf(properties.getPartitions()));
            metadata.put(JAtomicLedgerConstant.META_ROUTING, properties.getRouting());
            metadata.put(JAtomicLedgerConstant.META_TOTAL_NODES, String.valueOf(properties.getCluster().getTotalNodes()));
            metadata.put(JAtomicLedgerConstant.META_NODE_ID, String.valueOf(properties.getCluster().getNodeId()));
            metadata.put(JAtomicLedgerConstant.META_ADMIN_ENABLED, String.valueOf(properties.getAdmin().isEnabled()));
            log.info("✅ j-atomic-ledger 元数据自动上报成功！");

            // 上报后重新注册
            Object serviceRegistry = applicationContext.getBean(Class.forName("org.springframework.cloud.client.serviceregistry.ServiceRegistry"));
            Class<?> registrationInterface = Class.forName("org.springframework.cloud.client.serviceregistry.Registration");
            Method reRegister = serviceRegistry.getClass().getMethod("register", registrationInterface);

            reRegister.invoke(serviceRegistry, registration);
            log.info("已通知 Nacos 更新元数据。");
        } catch (NoSuchBeanDefinitionException e) {
            log.warn("未发现 Nacos Registration Bean，跳过元数据自动上报。");
        } catch (Exception e) {
            log.error("自动上报元数据失败", e);
        }
    }
}
