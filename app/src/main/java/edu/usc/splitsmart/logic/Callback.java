package edu.usc.splitsmart.logic;

/**
 * Receives the result of a handler call. Database and network work can't run on Android's main
 * thread, so handlers return results through a callback instead of a return value.
 *
 * <p>Implementations must call exactly one of these methods, on the main thread.
 */
public interface Callback<T> {
    void onSuccess(T result);

    /**
     * Called when the request fails. Expected failures, such as a wrong password, arrive as a
     * {@link SplitSmartException} with a code the screens can show a message for.
     */
    void onError(Exception error);
}
