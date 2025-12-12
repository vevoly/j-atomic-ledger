package io.github.vevoly.example.wallet;

import io.github.vevoly.example.wallet.domain.TradeCommand;
import io.github.vevoly.example.wallet.domain.WalletState;
import io.github.vevoly.ledger.api.LedgerBootstrap;
import org.springframework.stereotype.Component;

/**
 * 启动引导
 * 作用：
 *  1. 解决泛型擦除问题：Java 在运行时无法感知具体的泛型类型（S, C），
 *     通过此接口显式告诉框架：处理哪个状态类，反序列化哪个命令类。
 *  2. 初始化内存账本：定义系统在初始时（即没有任何快照和日志时）的初始模样。
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
