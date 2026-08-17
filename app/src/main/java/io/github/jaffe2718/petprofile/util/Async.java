package io.github.jaffe2718.petprofile.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Async {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Async() {
    }

    public static void run(Runnable background) {
        EXECUTOR.execute(background);
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
