package io.github.vevoly.example.wallet.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * <h3>【用户自定义】业务状态对象 (Business State Domain)</h3>
 *
 * <p>
 * 这个类相当于传统架构中的 <b>"内存数据库" (In-Memory Database)</b>。
 * 引擎启动时，会将快照加载为这个对象；运行时，所有的业务计算（加减扣除）都直接修改这个对象。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>[User Defined] Business State Object.</b><br>
 * Acts as the "In-Memory Database".<br>
 * Loaded from snapshot on startup; modified directly by business logic during runtime.
 * </span>
 *
 * <h3>设计最佳实践 (Best Practices)：</h3>
 * <ul>
 *     <li>
 *         <b>必须实现 Serializable (Must implement Serializable):</b><br>
 *         引擎依赖 Kryo 对此对象进行快照保存（持久化到磁盘）。<br>
 *         <span style="color: gray;">Required for Kryo snapshot persistence.</span>
 *     </li>
 *     <li>
 *         <b>推荐使用原生类型 (Prefer Primitive Types):</b><br>
 *         为了追求极致的内存效率和计算速度，建议使用 {@code long} 来存储金额，而不是 {@code BigDecimal}。<br>
 *         例如：数据库存 {@code 100.00} 元，这里存 {@code 10000} 分 (或厘)。<br>
 *         这能避免海量 {@code BigDecimal} 对象带来的 GC 压力。<br>
 *         <span style="color: gray;">Use {@code long} for amounts to achieve Zero GC and high performance. Store cents/millis instead of dollars.</span>
 *     </li>
 * </ul>
 *
 * @author vevoly
 */
@Data
public class WalletState implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户余额映射表.
     *
     * <h3>数据结构选择指南 (Data Structure Guide):</h3>
     * <ul>
     *     <li>
     *         <b>方案 A: {@code Map<Long, Long>} (当前方案)</b><br>
     *         适用于极简场景。
     *         <ul>
     *             <li><b>优点：</b> 内存占用最小，序列化最快。</li>
     *             <li><b>缺点：</b> 只能存余额。无法处理冻结金额、账户状态等复杂逻辑。</li>
     *         </ul>
     *     </li>
     *     <li>
     *         <b>方案 B: {@code Map<Long, WalletDomain>} (推荐方案)</b><br>
     *         适用于真实业务场景。Value 是一个包含余额、冻结、状态的复杂对象。
     *         <ul>
     *             <li><b>优点：</b> 业务表达能力强，高内聚。</li>
     *             <li><b>缺点：</b> 内存占用增加（每个对象有 16字节头信息开销）。但对于 16G+ 内存的服务器，存几千万用户依然毫无压力。</li>
     *         </ul>
     *     </li>
     * </ul>
     *
     * <hr>
     *
     * <span style="color: gray; font-size: 0.9em;">
     * <b>User Balance Map.</b><br>
     * <b>Option A (Current): {@code Map<Long, Long>}</b> - Best for memory efficiency. Stores balance only.<br>
     * <b>Option B (Rich Model): {@code Map<Long, WalletDomain>}</b> - Best for real business. Stores balance, frozen amount, status, etc.<br>
     * <i>Note: Option B consumes more heap memory due to object headers, but is acceptable for modern servers.</i>
     * </span>
     */
    private Map<Long, Long> balances = new HashMap<>();
}
