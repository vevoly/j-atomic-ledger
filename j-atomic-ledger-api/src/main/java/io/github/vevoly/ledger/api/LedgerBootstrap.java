package io.github.vevoly.ledger.api;

import java.io.Serializable;

/**
 * 辅助配置接口，用户实现此接口返回泛型类型
 *
 * @author vevoly
 */
public interface LedgerBootstrap<S extends Serializable, C extends LedgerCommand> {
    S getInitialState();
    Class<C> getCommandClass();
}
