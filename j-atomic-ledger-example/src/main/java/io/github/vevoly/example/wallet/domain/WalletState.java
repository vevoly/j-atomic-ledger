package io.github.vevoly.example.wallet.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 【用户自定义】业务状态对象 (Business State Domain)
 * <p>
 * 这个类相当于传统架构中的 <b>"内存数据库"</b>。
 * 引擎启动时，会将快照加载为这个对象；运行时，所有的业务计算（加减扣除）都直接修改这个对象。
 * </p>
 *
 * <h3>设计最佳实践 (Best Practices)：</h3>
 * <ul>
 *     <li><strong>必须实现 Serializable：</strong> 引擎依赖 Kryo 对此对象进行快照保存（持久化到磁盘）。</li>
 *     <li><strong>推荐使用原生类型 (Primitive Types)：</strong>
 *         <p>为了追求极致的内存效率和计算速度，建议使用 {@code long} 来存储金额，而不是 {@code BigDecimal}。</p>
 *         <p>例如：数据库存 {@code 100.00} 元，这里存 {@code 10000} 分 (或厘)。</p>
 *         <p>这能避免海量 {@code BigDecimal} 对象带来的 GC 压力。</p>
 *     </li>
 *     <li><strong>数据结构：</strong> 通常包含一个或多个 {@code Map} (如余额表、库存表、订单簿)。</li>
 * </ul>
 *
 * @author vevoly
 */
@Data
public class WalletState implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户余额映射表
     * <ul>
     *     <li>Key: UserId (用户ID)</li>
     *     <li>Value: Balance (余额) - <b>注意：单位为"厘" (元 * 10000)</b>，使用 long 类型避免精度丢失且提升性能。</li>
     * </ul>
     */
    private Map<Long, Long> balances = new HashMap<>();
}
