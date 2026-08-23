package io.github.jaffe2718.petprofile.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.jaffe2718.petprofile.util.RoutineNotifier;

/**
 * Periodically re-runs the routine notification reconciliation so that a notification which was
 * swiped away (on OEMs that allow it) is re-posted within about a minute.
 */
public class RoutineRepostReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();
        new Thread(() -> {
            try {
                RoutineNotifier.syncNow(context);
            } catch (Throwable ignored) {
            } finally {
                pendingResult.finish();
            }
        }).start();
    }
}
