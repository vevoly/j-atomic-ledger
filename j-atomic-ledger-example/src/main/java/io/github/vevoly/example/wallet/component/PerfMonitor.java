package io.github.vevoly.example.wallet.component;

import lombok.Getter;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.LongAdder;

/**
 * 监控组建
 * 用于极速模式下 (不使用Future)监控任务结束
 *
 * @author vevoly
 */
@Component
public class PerfMonitor {

    // 高性能计数器
    private final LongAdder counter = new LongAdder();
    @Getter
    private long startTime;

    // 开始压测时调用
    public void start() {
        counter.reset();
        startTime = System.currentTimeMillis();
    }

    // 业务处理时调用 (极速)
    public void increment() {
        counter.increment();
    }

    // 获取当前处理总数
    public long getCount() {
        return counter.sum();
    }

}
