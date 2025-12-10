package io.github.vevoly.example.wallet.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 模拟数据库表结构：user_wallet
 *
 * @author vevoly
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserWalletEntity {

    // 用户Id
    private Long userId;

    // 余额
    private BigDecimal balance;

    // 乐观锁版本
    private Long version;
}
