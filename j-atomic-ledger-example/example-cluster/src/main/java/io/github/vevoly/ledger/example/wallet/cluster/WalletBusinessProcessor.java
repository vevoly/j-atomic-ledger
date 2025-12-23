package io.github.vevoly.ledger.example.wallet.cluster;

import io.github.vevoly.ledger.api.BusinessProcessor;
import io.github.vevoly.ledger.api.utils.MoneyUtils;
import io.github.vevoly.ledger.example.wallet.domain.TradeCommand;
import io.github.vevoly.ledger.example.wallet.domain.TradeResult;
import io.github.vevoly.ledger.example.wallet.domain.WalletState;
import io.github.vevoly.ledger.example.wallet.entity.UserWalletEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <h3>钱包业务处理器 (Wallet Business Processor)</h3>
 *
 * <p>
 * 核心业务逻辑的实现类。负责执行具体的资金扣减、增加以及<b>业务规则校验</b>（如余额不足、账户冻结）。
 * 此类运行在 Disruptor 的单线程消费者中，<b>天然线程安全</b>，无需任何锁。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Wallet Business Processor.</b><br>
 * Implementation of core business logic. Responsible for fund deduction, addition, and <b>business rule validation</b> (e.g., insufficient balance, account frozen).<br>
 * Runs inside the Disruptor single-threaded consumer, ensuring <b>Thread-Safety</b> without locks.
 * </span>
 *
 * @author vevoly
 */
@Slf4j
@Component
public class WalletBusinessProcessor implements BusinessProcessor<WalletState, TradeCommand, UserWalletEntity> {

    /**
     * 执行业务逻辑.
     *
     * <p>
     * 在这里你可以进行：
     * 1. <b>前置校验：</b> 检查余额、检查账户状态（冻结/黑名单）、检查交易限额。
     * 2. <b>状态更新：</b> 修改内存中的余额。
     * 3. <b>构建增量：</b> 返回需要持久化到数据库的实体对象。
     * </p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Execute Business Logic.</b><br>
     * Here you can perform:<br>
     * 1. <b>Pre-validation:</b> Check balance, account status (frozen/blacklist), transaction limits.<br>
     * 2. <b>State Update:</b> Mutate balance in memory.<br>
     * 3. <b>Build Increment:</b> Return the entity object for DB persistence.
     * </span>
     *
     * @param state   当前内存状态 (Current Memory State)
     * @param cmd     接收到的命令 (Received Command)
     * @return 需要异步落库的增量实体 (Incremental Entity for Async Persistence)
     */
    @Override
    public UserWalletEntity process(WalletState state, TradeCommand cmd) {
        long start = System.nanoTime();
        // 1. 业务逻辑：获取余额 / Business logic: Get balance
        long currentBalance = state.getBalances().getOrDefault(cmd.getUserId(), 0L);

        // Q: 可以判断用户钱包状态吗？
        // A: 可以！假设 WalletState 里存的是 WalletModel 对象而不是简单的 Long，
        //    你可以这样写：
        //    WalletModel model = state.getModels().get(userId);
        //    if (model.isFrozen()) throw new RuntimeException("Account Frozen");

        // 2. 业务逻辑：检查余额 (仅扣款时) / Business Logic: Check balance (Only for debit)
        if (cmd.getAmount() < 0 && currentBalance + cmd.getAmount() < 0) {
            // 直接抛异常，框架会捕获并传递给 CompletableFuture
            // Throw exception directly, framework will catch and pass to CompletableFuture
            throw new RuntimeException("余额不足 / Insufficient Balance");
        }
        // 3. 修改内存状态 / Mutate Memory State
        long newBalance = currentBalance + cmd.getAmount();
        state.getBalances().put(cmd.getUserId(), newBalance);

        // 2. 返回增量实体 / Return Incremental Entity
        UserWalletEntity entity = new UserWalletEntity();
        entity.setUserId(cmd.getUserId());
        entity.setBalance(MoneyUtils.toDb(newBalance));

        // 4. 主动通知 Future，返回结果对象 / Notify Future actively, return result object
        if (cmd.getFuture() != null) {
            TradeResult result = TradeResult.success(cmd.getUserId(), cmd.getTxId(), MoneyUtils.toDb(newBalance));
            result.setLatencyNs(System.nanoTime() - start);

            // 填入结果 / Fill in the result
            cmd.getFuture().complete(result);
        }
        // LongAdder 的性能极其恐怖，对 TPS 几乎零影响
//        perfMonitor.increment();
        return entity;
    }
}
