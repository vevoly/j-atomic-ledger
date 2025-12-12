package io.github.vevoly.ledger.api;

import java.io.Serializable;

/**
 * 启动引导，辅助配置接口，用户实现此接口返回泛型类型
 * 1. 解决泛型擦除问题：Java 在运行时无法感知具体的泛型类型（S, C），
 *    通过此接口显式告诉框架：处理哪个状态类，反序列化哪个命令类。
 * 2. 初始化内存账本：定义系统在初始时（即没有任何快照和日志时）的初始模样。
 *
 * @author vevoly
 */
public interface LedgerBootstrap<S extends Serializable, C extends LedgerCommand> {

    /**
     * 定义初始状态
     *
     * 执行时机：当系统第一次启动，且在 /snapshot 目录下找不到任何快照文件时调用。
     * 业务应用：
     *   - 简单场景：直接返回 new WalletState()，代表一个空账本。
     *   - 真实场景：可以在此处调用数据库，加载所有用户的初始余额进内存。
     */
    S getInitialState();

    /**
     * 提供命令类的 Class 对象
     *
     * 作用：
     *   框架在读取本地磁盘日志（WAL）时，需要知道要把二进制数据还原（反序列化）成哪个具体的 Java 类。
     *   这里返回 TradeCommand.class，底层序列化工具（如 Kryo）就知道该如何实例化对象。
     */
    Class<C> getCommandClass();
}
