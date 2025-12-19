package io.github.vevoly.ledger.api.exception;

/**
 * <h3>j-atomic-ledger 引擎异常基类 (Base Engine Exception)</h3>
 *
 * <p>所有由本框架内部抛出的、可预期的运行时异常都应继承此类。</p>
 *
 * <hr>
 * <span style-="color: gray; font-size: 0.9em;">
 * <b>Base exception for the ledger engine.</b><br>
 * All predictable runtime exceptions thrown by the framework should extend this class.
 * </span>
 *
 * @author vevoly
 * @since 1.0.2
 */
public class JAtomicLedgerException extends Exception {

    private final JAtomicLedgerErrorCode errorCode;

    public JAtomicLedgerException(JAtomicLedgerErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public JAtomicLedgerException(JAtomicLedgerErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public JAtomicLedgerException(JAtomicLedgerErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public JAtomicLedgerErrorCode getErrorCode() {
        return errorCode;
    }
}
