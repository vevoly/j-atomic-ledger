package io.github.vevoly.example.wallet.mapper;

import io.github.vevoly.example.wallet.entity.UserWalletEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟 MyBatis Mapper
 */
@Slf4j
@Component
public class MockWalletMapper {


    public void batchUpdate(List<UserWalletEntity> entities) {
        // 模拟数据库网络 IO 耗时 (随机 5-10ms)
        try {
            Thread.sleep((long) (Math.random() * 5 + 5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }

}
