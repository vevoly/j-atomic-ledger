package io.github.vevoly.ledger.api;

import java.io.Serializable;

/**
 * 业务逻辑处理器接口
 * 用户需实现此接口来定义核心内存计算逻辑
 *
 * @param <S> 状态对象类型 (State) - 必须可序列化
 * @param <C> 命令对象类型 (Command) - 必须可序列化
 * @author vevoly
 */
@FunctionalInterface
public interface BusinessProcessor<S extends Serializable, C extends Serializable, E> {

    /**
     * 处理业务逻辑 (严禁包含 IO 操作、锁、或 Thread.sleep)
     *
     * @param state   当前内存状态
     * @param command 接收到的命令
     * @return 需要异步落库的增量实体 (例如 WalletEntity)，如果没有则返回 null
     */
    E process(S state, C command);
}
