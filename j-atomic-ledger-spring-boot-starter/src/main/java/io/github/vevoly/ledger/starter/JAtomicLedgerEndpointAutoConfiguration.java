package io.github.vevoly.ledger.starter;

import io.github.vevoly.ledger.starter.endpoint.JAtomicLedgerAdminEndpoint;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 负责注册 Admin Endpoint 的自动配置类
 * Recharge for registering Admin Endpoint.
 *
 * @since 1.2.2
 * @author vevoly
 */
@Configuration
@ConditionalOnClass(JAtomicLedgerAdminEndpoint.class)
@ConditionalOnProperty(prefix = "j-atomic-ledger.admin", name = "enabled", havingValue = "true")
public class JAtomicLedgerEndpointAutoConfiguration {

    @Bean
    @ConditionalOnAvailableEndpoint
    public JAtomicLedgerAdminEndpoint jAtomicLedgerAdminEndpoint() {
        return new JAtomicLedgerAdminEndpoint();
    }
}
