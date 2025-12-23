package io.github.vevoly.ledger.example.wallet.cluster.mapper;

import io.github.vevoly.ledger.example.wallet.entity.UserWalletEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * <h3>模拟 MyBatis Mapper (Mock Mapper)</h3>
 *
 * <p>
 * 模拟真实的数据库操作，包含随机的网络/IO 延迟。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Mock MyBatis Mapper.</b><br>
 * Simulates real database operations with random Network/IO latency.
 * </span>
 */
@Slf4j
@Component
public class MockWalletMapper {


    public void batchUpdate(List<UserWalletEntity> entities) {
        // 模拟数据库网络 IO 耗时 (随机 5-10ms) / Simulate DB Network I/O latency (Random 5-10ms)
        try {
            Thread.sleep((long) (Math.random() * 5 + 5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 实际场景这里会执行 SQL update / In real scenarios, this would execute SQL update
        // log.debug("Batch update {} records", entities.size());
    }

}
