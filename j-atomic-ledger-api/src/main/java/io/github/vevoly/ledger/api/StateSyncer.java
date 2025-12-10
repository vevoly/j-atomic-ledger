package io.github.vevoly.ledger.api;

import java.io.Serializable;

/**
 * 状态同步接口 (异步落库)
 * @param <S> 状态对象类型
 *
 * @author vevoly
 */
@FunctionalInterface
public interface StateSyncer<S extends Serializable> {

    /**
     * 将内存状态同步到持久化存储 (如 MySQL)
     * 框架会异步调用此方法，不会阻塞核心业务
     *
     * @param state 状态对象的快照
     */
    void sync(S state);
}
