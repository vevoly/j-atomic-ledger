package io.github.vevoly.ledger.api.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * <h3>资金转换工具类 (Currency Conversion Utility)</h3>
 *
 * <p>
 * 本类负责在 <b>业务层 (BigDecimal)</b> 与 <b>核心引擎层 (long)</b> 之间进行金额转换。
 * 核心设计目标是利用 CPU 原生整数运算替代 BigDecimal 对象运算，从而实现 <b>Zero GC</b> 和 <b>纳秒级延迟</b>。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>English Documentation:</b><br>
 * This class is responsible for converting amounts between the <b>Business Layer (BigDecimal)</b>
 * and the <b>Core Engine Layer (long)</b>.
 * The core design goal is to use CPU native integer arithmetic instead of BigDecimal operations
 * to achieve <b>Zero GC</b> and <b>Nanosecond Latency</b>.
 * </span>
 *
 * <h3>精度规约 (Precision Protocol):</h3>
 * <ul>
 *     <li><b>Scale (精度):</b> 4位小数 (1 元 = 10,000 厘)。</li>
 *     <li><b>Rounding (舍入):</b> {@link RoundingMode#DOWN} (直接截断)。</li>
 * </ul>
 *
 * @author vevoly
 * @since 1.0.0
 */
public final class MoneyUtils {

    /**
     * 精度：4位小数 (10^-4)
     * <br>
     * <span style="color: gray;">Precision scale: 4 decimal places.</span>
     */
    private static final int SCALE = 4;

    /**
     * 乘数因子：10,000
     * <br>
     * <span style="color: gray;">Multiplier factor: 10,000.</span>
     */
    private static final long MULTIPLIER = 10000L;

    /**
     * BigDecimal 类型的乘数缓存，避免重复创建对象。
     * <br>
     * <span style="color: gray;">Cached BigDecimal multiplier to avoid object recreation.</span>
     */
    private static final BigDecimal BD_MULTIPLIER = BigDecimal.valueOf(MULTIPLIER);

    private MoneyUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 【入参转换】将业务金额 (BigDecimal) 转换为 内存整数 (long).
     *
     * <p>
     * 应用场景：Controller 接收请求 -> 写入 Disruptor Event。
     * 采用 {@link RoundingMode#DOWN} 模式截断多余小数。
     * </p>
     *
     * <hr>
     *
     * <span style="color: gray; font-size: 0.9em;">
     * <b>[Input Conversion] Convert BigDecimal to memory long.</b><br>
     * Scenario: Controller receives request -> Write to Disruptor Event.<br>
     * Note: Uses {@link RoundingMode#DOWN} to truncate extra decimal places.
     * </span>
     *
     * <h3>示例 (Examples)：</h3>
     * <ul>
     *     <li><code>100.00</code> (100元) -> <code>1,000,000</code></li>
     *     <li><code>0.0001</code> (1厘) -> <code>1</code></li>
     *     <li><code>10.55559</code> (超精) -> <code>105,555</code> (截断第5位)</li>
     * </ul>
     *
     * @param amount 外部传入的金额对象 (External amount object). 允许为 null，null 视为 0。
     * @return 内存中的整数表示 (Integer representation in memory, Unit: 1/10000 CNY)
     */
    public static long toMem(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        // 建议使用 setScale 避免精度丢失警告，RoundingMode.DOWN 截断多余小数
        // 例如：10.55559 -> 10.5555 -> 105555
        return amount.multiply(BD_MULTIPLIER)
                .setScale(0, RoundingMode.DOWN)
                .longValue();
    }

    /**
     * 【出参转换】将 内存整数 (long) 转换为 业务金额 (BigDecimal).
     *
     * <p>
     * 应用场景：异步落库 (Entity) / 查询返回 (VO)。
     * </p>
     *
     * <hr>
     *
     * <span style="color: gray; font-size: 0.9em;">
     * <b>[Output Conversion] Convert memory long to BigDecimal.</b><br>
     * Scenario: Async DB Sync / Query Response.<br>
     * </span>
     *
     * <h3>示例 (Examples)：</h3>
     * <ul>
     *     <li><code>1,000,000</code> -> <code>100.0000</code></li>
     *     <li><code>1</code> -> <code>0.0001</code></li>
     * </ul>
     *
     * @param memAmount 内存中的整数金额 (Integer amount in memory)
     * @return 标准 BigDecimal 金额，保留4位小数 (Standard BigDecimal amount with scale 4)
     */
    public static BigDecimal toDb(long memAmount) {
        return BigDecimal.valueOf(memAmount)
                .divide(BD_MULTIPLIER, SCALE, RoundingMode.DOWN);
    }

    /**
     * 辅助方法：将内存金额格式化为字符串 (仅用于日志/调试).
     * <br>
     * <span style="color: gray;">Helper: Format memory amount to string (For debug purpose).</span>
     *
     * @param memAmount 内存中的整数金额
     * @return 格式化后的金额字符串 (e.g. "100.0000")
     */
    public static String format(long memAmount) {
        return toDb(memAmount).toString();
    }
}
