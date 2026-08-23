package io.github.jaffe2718.petprofile.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.jaffe2718.petprofile.data.AppDatabase;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;
import io.github.jaffe2718.petprofile.data.entity.RoutineEntity;
import io.github.jaffe2718.petprofile.util.RoutineNotifier;
import io.github.jaffe2718.petprofile.util.RoutineScheduler;

public class RoutineAlarmReceiver extends BroadcastReceiver {
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
                if (routine != null && routine.enabled) {
                    ProfileEntity profile = db.profileDao().getById(routine.profileId);
                    if (profile == null || profile.isArchived()) {
                        RoutineScheduler.cancel(context, routineId);
                    } else {
                        routine.lastFiredAt = System.currentTimeMillis();
                        db.routineDao().update(routine);
                        // Keep the reminder enabled: once its time has passed it will never be
                        // re-notified (the scheduler skips onceAt <= now), so it should still
                        // appear on the Daily Todo screen until the user completes it.
                        RoutineScheduler.reschedule(context, routine, profile);
                        RoutineNotifier.syncNow(context);
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                pendingResult.finish();
            }
        }).start();
    }
}
