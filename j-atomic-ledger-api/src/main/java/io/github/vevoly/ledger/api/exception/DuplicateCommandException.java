package io.github.vevoly.ledger.api.exception;

/**
 * <h3>重复命令异常</h3>
 *
 * <p>当幂等性策略检测到重复的 UniqueId 时抛出。</p>
 *
 * <hr>
 * <span style-="color: gray; font-size: 0.9em;">
 * <b>Duplicate command exception.</b><br>
 * Throw this exception when the idempotency strategy detects a duplicate UniqueId.
 * </span>
 *
 * @author vevoly
 * @since 1.0.2
 */
public class DuplicateCommandException extends JAtomicLedgerException {
    public DuplicateCommandException(String message) {
        super(JAtomicLedgerErrorCode.DUPLICATE_COMMAND, message);
    }
    public DuplicateCommandException(String message, Throwable cause) {
        super(JAtomicLedgerErrorCode.DUPLICATE_COMMAND, message, cause);
    }
}
