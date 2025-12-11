package io.github.vevoly.ledger.api;

import java.io.Serializable;
import java.util.List;

/**
 * 状态同步接口 (异步落库)
 * @param <E> 实体对象类型
 *
 * @author vevoly
 */
@FunctionalInterface
public interface BatchWriter<E extends Serializable> {

    /**
     * 批量落库
     * 将内存状态同步到持久化存储 (如 MySQL)
     * 框架会异步调用此方法，不会阻塞核心业务
     * @param entities 增量数据列表
     */
    void persist(List<E> entities);
}
