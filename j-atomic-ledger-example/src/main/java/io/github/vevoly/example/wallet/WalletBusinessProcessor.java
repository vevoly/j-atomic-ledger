package io.github.vevoly.example.wallet;

import io.github.vevoly.example.wallet.domain.TradeCommand;
import io.github.vevoly.example.wallet.domain.WalletState;
import io.github.vevoly.ledger.api.BusinessProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 业务处理器
 *
 * @author vevoly
 */
@Slf4j
@Component
public class WalletBusinessProcessor implements BusinessProcessor<WalletState, TradeCommand> {

    @Override
    public void process(WalletState state, TradeCommand cmd) {
        // 1. 业务逻辑：获取余额
        long currentBalance = state.getBalances().getOrDefault(cmd.getUserId(), 0L);
        long changeAmount = cmd.getAmount();
        // 2. 业务逻辑：检查余额 (仅扣款时)
        if (changeAmount < 0 && currentBalance + changeAmount < 0) {
            // 直接抛异常，框架会捕获并传递给 CompletableFuture
            throw new RuntimeException("余额不足");
        }
        // 3. 修改内存状态
        state.getBalances().put(cmd.getUserId(), currentBalance + changeAmount);
        // 可以在这里做一些简单的日志，但不要太重
        log.info("内存计算完成: User={} 新余额={}", cmd.getUserId(), currentBalance + changeAmount);
    }
}
