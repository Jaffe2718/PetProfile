package io.github.jaffe2718.petprofile.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.jaffe2718.petprofile.util.RoutineNotifier;

/**
 * Re-reconciles the routine notifications when one of them is actually swiped away. Each posted
 * notification carries a delete intent that targets this receiver, so a reminder that the user
 * dismisses (on OEMs that allow dismissing these ongoing notifications) is re-posted immediately
 * from the resulting single re-sync — instead of polling every minute in the background.
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
