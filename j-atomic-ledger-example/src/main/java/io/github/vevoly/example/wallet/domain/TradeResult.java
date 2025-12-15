package io.github.vevoly.example.wallet.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 交易执行结果 (Trade Execution Result)
 * <p>用于通过 Future 返回给 Controller</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TradeResult implements Serializable {

    /** 交易是否成功 */
    private boolean success;

    /** 响应码 */
    private String code;

    /** 用户id */
    private Long userId;

    /** 交易单号 */
    private String txId;

    /**
     * 变更后的最新余额
     * (前端拿到这个直接刷新界面，不用再查)
     */
    private BigDecimal currentBalance;

    /** 交易耗时 (ns) */
    private long latencyNs;

    // 静态工厂方法：成功
    public static TradeResult success(Long userId, String txId, BigDecimal balance) {
        return new TradeResult(true, "200", userId, txId, balance, 0);
    }
}
