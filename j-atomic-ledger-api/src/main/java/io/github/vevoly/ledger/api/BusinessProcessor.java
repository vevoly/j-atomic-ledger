package io.github.vevoly.ledger.api;

import java.io.Serializable;

/**
 * <h3>业务逻辑处理器接口 (Business Logic Processor)</h3>
 *
 * <p>
 * 这是核心引擎的“大脑”。用户需实现此接口来定义如何根据命令修改内存状态。
 * </p>
 *
 * <p style="color: red">
 * <b>⚠️ 严禁操作：</b><br>
 * 1. 禁止任何 IO 操作（读写数据库、Redis、文件、网络调用）。<br>
 * 2. 禁止使用任何锁（synchronized, Lock）或阻塞操作。<br>
 * 3. 禁止 Thread.sleep。<br>
 * <b>违背上述规则将直接导致系统吞吐量崩塌。</b>
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Business Logic Processor Interface.</b><br>
 * The "Brain" of the engine. Defines how to mutate memory state based on commands.<br>
 * <b>⚠️ STRICT PROHIBITIONS:</b> No IO (DB/Network), No Locks, No Sleep.<br>
 * Violation will cause throughput collapse.
 * </span>
 *
 * @param <S> 状态对象类型 (State) - 必须可序列化
 * @param <C> 命令对象类型 (Command) - 必须可序列化
 * @param <E> 增量实体类型 (Entity) - 用于异步落库
 * @author vevoly
 * @since 1.0.0
 */
@FunctionalInterface
public interface BusinessProcessor<S extends Serializable, C extends Serializable, E> {

    /**
     * 执行业务逻辑.
     *
     * <p>
     * 在这里你可以进行：
     * 1. <b>前置校验：</b> 检查余额、检查账户状态（冻结/黑名单）、检查交易限额。
     * 2. <b>状态更新：</b> 修改内存中的余额。
     * 3. <b>构建增量：</b> 返回需要持久化到数据库的实体对象。
     * </p>
     *
     * <hr>
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Execute Business Logic.</b><br>
     * Here you can perform:<br>
     * 1. <b>Pre-validation:</b> Check balance, account status (frozen/blacklist), transaction limits.<br>
     * 2. <b>State Update:</b> Mutate balance in memory.<br>
     * 3. <b>Build Increment:</b> Return the entity object for DB persistence.
     * </span>
     *
     * @param state   当前内存状态 (Current Memory State)
     * @param command 接收到的命令 (Received Command)
     * @return 需要异步落库的增量实体 (Incremental Entity for Async Persistence)
     */
    E process(S state, C command);
}
