package io.github.vevoly.example.wallet.mapper;

import io.github.vevoly.example.wallet.entity.UserWalletEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟 MyBatis Mapper
 */
@Slf4j
@Component
public class MockWalletMapper {

    // 模拟数据库存储
    private final ConcurrentHashMap<Long, UserWalletEntity> fakeDb = new ConcurrentHashMap<>();

    public void batchUpdate(UserWalletEntity entity) {
        // 模拟数据库网络 IO 耗时 (随机 5-10ms)
        try {
            Thread.sleep((long) (Math.random() * 5 + 5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 执行“落库”
        fakeDb.put(entity.getUserId(), entity);

        // 偶尔打印一下，证明在工作
        log.debug("【DB层】更新成功: User={}, Balance={}", entity.getUserId(), entity.getBalance());
    }

    // 模拟查询
    public UserWalletEntity selectById(Long userId) {
        return fakeDb.get(userId);
    }
}
