package io.github.jaffe2718.petprofile.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Async {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    /**
     * Long, user-initiated network jobs (a cloud sync, a LAN transfer) get their own thread.
     *
     * <p>They hold their worker for as long as the transfer takes, and {@link #EXECUTOR} is also what
     * every {@code PetRepository} read runs on: putting them there queued every screen's data load
     * behind the transfer, so a profile opened during a sync rendered as if it had no records at all
     * until the transfer finished.
     */
    private static final ExecutorService LONG_TASK_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Async() {
    }

    public static void run(Runnable background) {
        EXECUTOR.execute(background);
    }

    /** Runs a long job off the data-read executor; see {@link #LONG_TASK_EXECUTOR}. */
    public static void runLong(Runnable background) {
        LONG_TASK_EXECUTOR.execute(background);
    }

    public static void ui(Runnable action) {
        MAIN.post(action);
    }

    public static <T> void post(Result<T> callback, T value, Throwable error) {
        ui(() -> {
            if (error != null) {
                callback.onError(error);
            } else {
                callback.onSuccess(value);
            }
        });
    }

    public interface Result<T> {
        void onSuccess(T value);

        void onError(Throwable error);
    }

    public interface EmptyResult {
        void onSuccess();

        void onError(Throwable error);
    }
}
