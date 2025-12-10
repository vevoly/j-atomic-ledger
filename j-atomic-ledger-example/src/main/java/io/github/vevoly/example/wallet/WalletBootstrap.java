package io.github.vevoly.example.wallet;

import io.github.vevoly.example.wallet.domain.TradeCommand;
import io.github.vevoly.example.wallet.domain.WalletState;
import io.github.vevoly.ledger.api.LedgerBootstrap;
import org.springframework.stereotype.Component;

/**
 * 启动引导
 */
@Component
public class WalletBootstrap implements LedgerBootstrap<WalletState, TradeCommand> {

    @Override
    public WalletState getInitialState() {
        return new WalletState(); // 初始为空状态
    }

    @Override
    public Class<TradeCommand> getCommandClass() {
        return TradeCommand.class; // 告诉引擎反序列化用这个类
    }
}
