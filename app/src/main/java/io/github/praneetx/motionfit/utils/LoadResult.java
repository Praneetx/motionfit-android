package io.github.praneetx.motionfit.utils;

/**
 * A small success/data/error wrapper for read operations.
 *
 * <p>Screens use this to distinguish a real empty result from a failed load.
 * It deliberately carries no Android types so it can be unit-tested on the
 * JVM.</p>
 *
 * @param <T> loaded data type
 */
public final class LoadResult<T> {

    private final boolean success;
    private final T data;
    private final String errorMessage;

    private LoadResult(boolean success, T data, String errorMessage) {
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
    }

    public static <T> LoadResult<T> success(T data) {
        return new LoadResult<>(true, data, null);
    }

    public static <T> LoadResult<T> error(String message) {
        return new LoadResult<>(false, null, message);
    }

    public boolean isSuccess() {
        return success;
    }

    /** Loaded data on success, otherwise {@code null}. */
    public T getData() {
        return data;
    }

    /** Human-readable failure message on error, otherwise {@code null}. */
    public String getErrorMessage() {
        return errorMessage;
    }
}
