package io.github.vevoly.ledger.example.wallet.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <h3>模拟数据库表结构 (Simulated Database Entity)</h3>
 *
 * <p>
 * 对应数据库表：{@code user_wallet}。
 * 此类仅用于异步落库，不参与核心内存计算。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Simulated Database Entity.</b><br>
 * Maps to table {@code user_wallet}. Used for async persistence only, not for core memory calculation.
 * </span>
 *
 * @author vevoly
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserWalletEntity implements Serializable {

    // 用户Id
    private Long userId;

    // 余额
    private BigDecimal balance;

    // 乐观锁版本
    private Long version;
}
