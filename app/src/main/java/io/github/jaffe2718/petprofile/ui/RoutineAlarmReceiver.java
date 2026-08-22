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
                        String nickname = RoutineNotifier.findNickname(context, routine.profileId);
                        RoutineNotifier.post(context, routineId.hashCode(), routine.title, nickname, routine.details);
                        routine.lastFiredAt = System.currentTimeMillis();
                        if (RoutineEntity.TYPE_ONCE.equals(routine.type)) {
                            routine.enabled = false;
                            db.routineDao().update(routine);
                            RoutineScheduler.cancel(context, routineId);
                        } else {
                            db.routineDao().update(routine);
                            RoutineScheduler.reschedule(context, routine, profile);
                        }
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                pendingResult.finish();
            }
        }).start();
    }
}
