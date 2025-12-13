package io.github.vevoly.ledger.api;

import java.io.Serializable;
import java.util.List;

/**
 * <h3>批量写入接口 (Batch Persistence Interface)</h3>
 *
 * <p>
 * 定义如何将内存中产生的增量实体同步到持久化存储（如 MySQL/Oracle）。
 * 框架会将多个业务处理产生的实体聚合为一个列表，调用此接口进行批量插入/更新，从而极大地降低数据库 IO 压力。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Batch Persistence Interface.</b><br>
 * Defines how to sync incremental entities generated in memory to persistent storage (e.g., MySQL).
 * The framework aggregates entities into a list and calls this interface for batch execution,
 * significantly reducing database I/O pressure.
 * </span>
 *
 * @param <E> 实体对象类型 (Entity Type)
 * @author vevoly
 * @since 1.0.0
 */
@FunctionalInterface
public interface BatchWriter<E extends Serializable> {

    /**
     * 执行批量落库.
     * <p>
     * <b>注意：</b> 此方法在独立的异步线程中被调用，不会阻塞核心业务链路。<br>
     * 如果抛出异常，框架会自动进行无限重试，直到成功为止（保证 At-least-once）。
     * </p>
     *
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Execute batch persistence.</b><br>
     * Note: Called in a separate async thread. Does not block core business logic.<br>
     * If an exception is thrown, the framework will retry indefinitely (At-least-once guarantee).
     * </span>
     *
     * @param entities 增量数据列表 (List of incremental data entities)
     */
    void persist(List<E> entities);
}
