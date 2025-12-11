package io.github.vevoly.example.wallet;

import io.github.vevoly.example.wallet.domain.TradeCommand;
import io.github.vevoly.example.wallet.domain.WalletState;
import io.github.vevoly.example.wallet.entity.UserWalletEntity;
import io.github.vevoly.ledger.api.BusinessProcessor;
import io.github.vevoly.ledger.api.utils.MoneyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 业务处理器
 *
 * @author vevoly
 */
@Slf4j
@Component
public class WalletBusinessProcessor implements BusinessProcessor<WalletState, TradeCommand, UserWalletEntity> {

    @Override
    public UserWalletEntity process(WalletState state, TradeCommand cmd) {
        // 1. 业务逻辑：获取余额
        long currentBalance = state.getBalances().getOrDefault(cmd.getUserId(), 0L);
        // 将 cmd 传过来的值转换成内存值

        long changeAmount = MoneyUtils.toMem(cmd.getAmount());
        // 2. 业务逻辑：检查余额 (仅扣款时)
        if (changeAmount < 0 && currentBalance + changeAmount < 0) {
            // 直接抛异常，框架会捕获并传递给 CompletableFuture
            throw new RuntimeException("余额不足");
        }
        // 3. 修改内存状态
        long newBalance = currentBalance + changeAmount;
        state.getBalances().put(cmd.getUserId(), newBalance);

        // 2. 【关键】返回增量实体
        // 以前这里返回 void，引擎不知道谁变了。现在我们明确告诉引擎：这个 User 变了，去更新他。
        UserWalletEntity entity = new UserWalletEntity();
        entity.setUserId(cmd.getUserId());
        entity.setBalance(MoneyUtils.toDb(newBalance));
        return entity;
        // 尽量不要放日志，比较耗性能
//        log.info("内存计算完成: User={} 新余额={}", cmd.getUserId(), currentBalance + changeAmount);
    }
}
