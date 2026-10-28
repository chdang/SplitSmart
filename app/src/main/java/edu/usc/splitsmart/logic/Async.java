package edu.usc.splitsmart.logic;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs handler work off the main thread and delivers the result back on it, which is what
 * {@link Callback} promises its callers.
 *
 * <p>Every handler in this package should use it rather than touching a repository directly:
 * Android kills the app if the database is touched on the main thread. One worker thread, so
 * two writes can never overlap.
 *
 * <p>Anything the work throws reaches {@link Callback#onError}. Throw a {@link
 * SplitSmartException} for failures the user should see a specific message for.
 */
final class Async {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_THREAD = new Handler(Looper.getMainLooper());

    private Async() {
    }

    static <T> void run(Callable<T> work, Callback<T> callback) {
        WORKER.execute(() -> {
            T result;
            try {
                result = work.call();
            } catch (Exception error) {
                MAIN_THREAD.post(() -> callback.onError(error));
                return;
            }
            MAIN_THREAD.post(() -> callback.onSuccess(result));
        });
    }
}
