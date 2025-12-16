package io.github.vevoly.ledger.api.exception;

/**
 * <h3>数据恢复异常</h3>
 *
 * <p>当从 WAL 或快照加载数据失败时抛出。</p>
 *
 * <hr>
 * <span style-="color: gray; font-size: 0.9em;">
 * <b>Data recover exception.</b><br>
 * Throw this exception when failed from WAL or snapshot.
 * </span>
 *
 * @author vevoly
 * @since 1.0.1
 */
public class RecoveryException extends JAtomicLedgerException {
    public RecoveryException(String message) {
        super(JAtomicLedgerErrorCode.RECOVERY_FAILED, message);
    }
    public RecoveryException(String message, Throwable cause) {
        super(JAtomicLedgerErrorCode.RECOVERY_FAILED, message, cause);
    }
}
