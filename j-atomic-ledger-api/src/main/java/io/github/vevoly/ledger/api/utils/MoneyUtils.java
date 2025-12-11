package io.github.vevoly.ledger.api.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 资金转换核心工具类
 *
 * 核心定义：
 * 1. 业务层/数据库层：使用 BigDecimal (单位：元), 保留 4 位小数 (e.g., 100.0000)
 * 2. 内存/Disruptor层：使用 long (单位：万分之一元), 纯整数运算 (e.g., 1000000)
 *
 * 换算公式：Memory = DB * 10000
 */
public class MoneyUtils {

    // 精度：4位小数
    private static final int SCALE = 4;

    // 乘数：10000
    private static final long MULTIPLIER = 10000L;

    // BigDecimal 类型的乘数，避免重复创建对象
    private static final BigDecimal BD_MULTIPLIER = BigDecimal.valueOf(MULTIPLIER);

    /**
     * 【输入】将 BigDecimal (元) 转换为 long (内存整数)
     * 场景：Controller 接收到请求 -> 放入 Disruptor Event
     *
     * @param amount 外部金额 (如 10.50)
     * @return 内存整数 (如 105000)
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
     * 【输出】将 long (内存整数) 转换为 BigDecimal (元)
     * 场景：Disruptor 处理完 -> 异步落库 / 返回给前端
     *
     * @param memAmount 内存整数 (如 105000)
     * @return 数据库金额 (如 10.5000)
     */
    public static BigDecimal toDb(long memAmount) {
        return BigDecimal.valueOf(memAmount)
                .divide(BD_MULTIPLIER, SCALE, RoundingMode.DOWN);
    }

    /**
     * 辅助：格式化打印 (调试用)
     */
    public static String format(long memAmount) {
        return toDb(memAmount).toString();
    }
}
