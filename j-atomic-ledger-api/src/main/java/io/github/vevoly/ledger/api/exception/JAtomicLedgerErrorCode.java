package io.github.vevoly.ledger.api.exception;

/**
 * <h3>核心引擎错误码 (Core Engine Error Codes)</h3>
 *
 * <p>定义了框架内部可能抛出的所有标准异常代码。</p>
 *
 * <hr>
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Core Engine Error Codes.</b><br>
 * Defines all standard exception codes that can be thrown by the framework.
 * </span>
 *
 * @author vevoly
 * @since 1.0.0
 */
public enum JAtomicLedgerErrorCode {

    // --- 1xxx: 初始化与配置错误 (Initialization & Configuration) ---
    INITIALIZATION_FAILED(1001, "Engine initialization failed"),

    // --- 2xxx: 运行时错误 (Runtime) ---
    DUPLICATE_COMMAND(2001, "Duplicate command detected"),

    // --- 3xxx: 持久化与恢复错误 (Persistence & Recovery) ---
    RECOVERY_FAILED(3001, "Data recovery from WAL/Snapshot failed"),
    SNAPSHOT_SAVE_FAILED(3002, "Failed to save snapshot"),
    SNAPSHOT_LOAD_FAILED(3003, "Failed to load snapshot"),
    WAL_WRITE_FAILED(3004, "Failed to write to WAL"),

    // --- 4xxx: API 调用错误 (API Usage) ---
    INVALID_ARGUMENT(4001, "Invalid argument provided"),

    ;

    private final int code;
    private final String defaultMessage;

    JAtomicLedgerErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
