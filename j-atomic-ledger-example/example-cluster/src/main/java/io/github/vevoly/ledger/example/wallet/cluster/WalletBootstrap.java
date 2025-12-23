package io.github.vevoly.ledger.example.wallet.cluster;

import io.github.vevoly.ledger.api.LedgerBootstrap;
import io.github.vevoly.ledger.example.wallet.domain.TradeCommand;
import io.github.vevoly.ledger.example.wallet.domain.WalletState;
import org.springframework.stereotype.Component;

/**
 * <h3>钱包启动引导类 (Wallet Bootstrap Configuration)</h3>
 *
 * <p>
 * 此类是连接 <b>业务层</b> 与 <b>核心引擎层</b> 的关键桥梁。
 * 它负责在系统启动时为引擎提供必要的 <b>类型信息</b> 和 <b>初始数据</b>。
 * </p>
 *
 * <hr>
 *
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Wallet Bootstrap Configuration.</b><br>
 * Acts as the bridge between the <b>Business Layer</b> and the <b>Core Engine</b>.<br>
 * It provides necessary <b>Type Information</b> and <b>Initial Data</b> to the engine during startup.
 * </span>
 *
 * @author vevoly
 */
@Component
public class WalletBootstrap implements LedgerBootstrap<WalletState, TradeCommand> {

    /**
     * 获取初始内存状态 (Get Initial Memory State).
     *
     * <p>
     * <b>执行时机：</b> 仅当系统 <b>冷启动</b>（磁盘上没有任何快照文件和 WAL 日志）时调用。<br>
     * <b>业务应用：</b>
     * <ul>
     *     <li><b>Demo 场景：</b> 直接返回 {@code new WalletState()} (空账本)。</li>
     *     <li><b>生产场景：</b> 通常在此处调用数据库 DAO，加载所有用户的余额到内存中（数据预热）。</li>
     * </ul>
     * </p>
     *
     * <hr>
     *
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Execution Timing:</b> Called only on <b>Cold Start</b> (No snapshot/WAL exists on disk).<br>
     * <b>Usage:</b><br>
     * - <i>Demo:</i> Return an empty state object.<br>
     * - <i>Production:</i> Load full user balances from DB into memory (Data Pre-warming).
     * </span>
     *
     * @return 初始状态对象 (Initial State Object)
     */
    @Override
    public WalletState getInitialState() {
        return new WalletState();
    }

    /**
     * 获取命令类的 Class 对象 (Get Command Class Type).
     *
     * <p>
     * <b>作用：解决 Java 泛型擦除问题。</b><br>
     * 框架在读取磁盘上的二进制 WAL 日志时，无法通过泛型 {@code <C>} 知道具体是哪个类。
     * 必须通过此方法显式返回 {@code TradeCommand.class}，底层序列化工具（如 Kryo/Chronicle）才能利用反射创建对象实例。
     * </p>
     *
     * <hr>
     *
     * <span style="color: gray; font-size: 0.9em;">
     * <b>Purpose: Solves Java Type Erasure.</b><br>
     * The framework cannot determine the generic type {@code <C>} at runtime when reading binary WAL logs.<br>
     * This method explicitly provides {@code TradeCommand.class} for reflection-based deserialization.
     * </span>
     *
     * @return 命令类的 Class 对象 (Class object of the Command)
     */
    @Override
    public Class<TradeCommand> getCommandClass() {
        return TradeCommand.class; // 告诉引擎反序列化用这个类 / Tell the engine to deserialize with this class
    }
}
