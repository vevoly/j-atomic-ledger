package io.github.vevoly.ledger.api.exception;

/**
 * <h3>初始化异常</h3>
 *
 * <p>当 Builder 参数校验失败或必要组件无法创建时抛出。</p>
 *
 * <hr>
 * <span style-="color: gray; font-size: 0.9em;">
 * <b>Initialization exception.</b><br>
 * Throw this exception when when a Builder’s parameter validation fails or a required component cannot be created.
 * </span>
 *
 * @author vevoly
 * @since 1.0.1
 */
public class InitializationException extends JAtomicLedgerException {
    public InitializationException(String message) {
        super(JAtomicLedgerErrorCode.INITIALIZATION_FAILED, message);
    }
    public InitializationException(String message, Throwable cause) {
        super(JAtomicLedgerErrorCode.INITIALIZATION_FAILED, message, cause);
    }
}
