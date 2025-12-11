package io.github.vevoly.example.wallet;

import io.github.vevoly.example.wallet.entity.UserWalletEntity;
import io.github.vevoly.example.wallet.mapper.MockWalletMapper;
import io.github.vevoly.ledger.api.BatchWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 落库转换
 */
@Slf4j
@Service
public class WalletBatchWriter implements BatchWriter<UserWalletEntity> {

    @Autowired
    private MockWalletMapper walletMapper;

    @Override
    public void persist(List<UserWalletEntity> entities) {
            walletMapper.batchUpdate(entities);
    }
}
