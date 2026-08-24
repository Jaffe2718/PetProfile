package io.github.jaffe2718.petprofile.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.jaffe2718.petprofile.data.AppDatabase;
import io.github.jaffe2718.petprofile.data.entity.RoutineEntity;
import io.github.jaffe2718.petprofile.util.RoutineNotifier;

/** Marks a routine as completed when the user taps the notification's "Done" action. */
public class RoutineCompleteReceiver extends BroadcastReceiver {
    public static final String EXTRA_ROUTINE_ID = "routine_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String routineId = intent.getStringExtra(EXTRA_ROUTINE_ID);
        if (routineId == null) {
            return;
        }
        final PendingResult pendingResult = goAsync();
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                RoutineEntity routine = db.routineDao().getById(routineId);
                if (routine != null) {
                    routine.completed = true;
                    routine.lastInteractionTime = System.currentTimeMillis();
                    db.routineDao().update(routine);
                }
                RoutineNotifier.syncNow(context);
                RoutineNotifier.cancelRoutine(context, routineId);
            } catch (Throwable ignored) {
            } finally {
                pendingResult.finish();
            }
        }).start();
    }
}