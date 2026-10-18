package io.github.praneetx.motionfit.utils;

/**
 * Small result object returned by repository write operations. It lets an
 * Activity show a friendly message without ever letting a database exception
 * crash the app.
 */
public final class OperationResult {

    private final boolean success;
    private final String message;

    private OperationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static OperationResult ok() {
        return new OperationResult(true, null);
    }

    public static OperationResult ok(String message) {
        return new OperationResult(true, message);
    }

    public static OperationResult error(String message) {
        return new OperationResult(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    /** Human readable message; may be null on plain success. */
    public String getMessage() {
        return message;
    }
}
