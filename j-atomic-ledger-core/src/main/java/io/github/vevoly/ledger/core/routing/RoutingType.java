package io.github.vevoly.ledger.core.routing;

/**
 * <h3>路由类型枚举 (Routing Strategy Type)</h3>
 *
 * <p>用于 Spring Boot 配置文件中选择路由策略。</p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Routing Strategy Type Enum.</b><br>
 * Used in configuration files (application.yml) to select the routing strategy.
 * </span>
 *
 * @author vevoly
 * @since 1.1.0
 */
public enum RoutingType {

    /**
     * 哈希取模路由策略 / Module Hashing Strategy
     */
    MODULO,

    /**
     * 集合点哈希路由策略 / Rendezvous Hashing Strategy
     */
    RENDEZVOUS
}
