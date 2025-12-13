package io.github.vevoly.ledger.api;

import java.io.Serializable;

/**
 * <h3>引擎启动引导接口 (Engine Bootstrap Interface)</h3>
 *
 * <p>
 * 用于配置引擎的<b>初始状态</b>和<b>类型映射</b>。
 * <br>
 * 1. <b>解决泛型擦除：</b> 显式告知框架 Command 的具体 Class 类型，用于 WAL 反序列化。<br>
 * 2. <b>冷启动初始化：</b> 定义当磁盘上没有任何快照时的初始内存状态（如从数据库全量加载）。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Engine Bootstrap Interface.</b><br>
 * Configures initial state and type mapping.<br>
 * 1. <b>Type Erasure:</b> Explicitly provides the Command Class for WAL deserialization.<br>
 * 2. <b>Cold Start:</b> Defines the initial memory state when no snapshot exists on disk.
 * </span>
 *
 * @author vevoly
 * @since 1.0.0
 */
public interface LedgerBootstrap<S extends Serializable, C extends LedgerCommand> {

    /**
     * 获取初始状态.
     * <p>
     * <b>执行时机：</b> 仅当系统第一次启动，且 snapshot 目录下找不到任何快照文件时调用。<br>
     * <b>场景：</b> 返回 new 空对象，或者从数据库 select 全量数据加载到内存。
     * </p>
     *
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Get Initial State.</b><br>
     * Executed only on first startup when no snapshot file is found.<br>
     * Usage: Return a new empty object, or load full data from DB into memory.
     * </span>
     *
     * @return 初始内存状态对象 (Initial Memory State)
     */
    S getInitialState();

    /**
     * 获取命令类的 Class 对象.
     * <p>
     * 用于底层序列化工具（如 Kryo/Chronicle Wire）反射创建对象实例。
     * </p>
     *
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Get Command Class.</b><br>
     * Used by underlying serializers to create object instances via reflection.
     * </span>
     *
     * @return 命令类 Class (e.g. TradeCommand.class)
     */
    Class<C> getCommandClass();
}
