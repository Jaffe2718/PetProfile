package io.github.jaffe2718.petprofile.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.jaffe2718.petprofile.util.RoutineNotifier;
import io.github.jaffe2718.petprofile.util.RoutineScheduler;

/**
 * Re-runs the routine notification reconciliation shortly after midnight so any persistent
 * notification that should have been closed (e.g. a SKIP routine whose day passed) is cleaned up.
 */
public class RoutineDailyResetReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();
        new Thread(() -> {
            try {
                RoutineNotifier.syncNow(context);
                RoutineScheduler.scheduleAll(context);
            } catch (Throwable ignored) {
            } finally {
                pendingResult.finish();
            }
        }).start();
    }
}
