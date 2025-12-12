package io.github.vevoly.example.wallet.api;

import io.github.vevoly.example.wallet.domain.TradeCommand;
import io.github.vevoly.example.wallet.domain.WalletState;
import io.github.vevoly.example.wallet.entity.UserWalletEntity;
import io.github.vevoly.ledger.core.LedgerEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
public class WalletController {

    // 直接注入引擎！
    @Autowired
    private LedgerEngine<WalletState, TradeCommand, UserWalletEntity> ledgerEngine;

    @GetMapping("/trade")
    public String trade(@RequestParam("uid") Long uid, @RequestParam("amount") BigDecimal amount) {
        // 1. 构造命令
        TradeCommand cmd = new TradeCommand();
        cmd.setTxId(UUID.randomUUID().toString());
        cmd.setUserId(uid);
        cmd.setAmount(amount);

        // 2. 设置 Future
        CompletableFuture<Object> future = new CompletableFuture<>();
        cmd.setFuture(future);

        // 3. 提交
        ledgerEngine.submit(cmd);

        // 4. 等待结果
        try {
            // 阻塞等待，直到 Disruptor 处理完成
            Object result = future.get(3, TimeUnit.SECONDS);
            return "SUCCESS: " + result;
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // 查询当前内存状态 (仅用于调试)
    @GetMapping("/balance")
    public Long balance(@RequestParam("uid") Long uid) {
        return ledgerEngine.getStateBy(String.valueOf(uid)).getBalances().getOrDefault(uid, 0L);
    }
}
