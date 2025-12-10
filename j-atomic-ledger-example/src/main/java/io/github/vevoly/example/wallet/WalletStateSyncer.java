package io.github.vevoly.example.wallet;

import io.github.vevoly.example.wallet.domain.WalletState;
import io.github.vevoly.example.wallet.entity.UserWalletEntity;
import io.github.vevoly.example.wallet.mapper.MockWalletMapper;
import io.github.vevoly.ledger.api.StateSyncer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 落库转换
 */
@Slf4j
@Service
public class WalletStateSyncer implements StateSyncer<WalletState> {

    @Autowired
    private MockWalletMapper walletMapper;

    @Override
    public void sync(WalletState state) {
        // 策略：全量同步还是增量？
        // 简单起见，我们遍历 State 中的所有用户进行更新 (实际场景通常有 DirtyFlag 优化)

        for (Map.Entry<Long, Long> entry : state.getBalances().entrySet()) {
            Long userId = entry.getKey();
            Long memBalance = entry.getValue();

            // --- 数据转换：State (内存 Long) -> Entity (DB BigDecimal) ---
            UserWalletEntity entity = new UserWalletEntity();
            entity.setUserId(userId);
            // 假设 10000 厘 = 1 元
            entity.setBalance(BigDecimal.valueOf(memBalance).divide(BigDecimal.valueOf(10000)));

            // 调用 Mapper
            walletMapper.batchUpdate(entity);
        }
    }
}
