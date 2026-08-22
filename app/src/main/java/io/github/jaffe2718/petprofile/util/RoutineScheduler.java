package io.github.jaffe2718.petprofile.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.jaffe2718.petprofile.data.AppDatabase;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;
import io.github.jaffe2718.petprofile.data.entity.RoutineEntity;
import io.github.jaffe2718.petprofile.ui.RoutineAlarmReceiver;

public final class RoutineScheduler {
    private RoutineScheduler() {
    }

    public static void scheduleAll(Context context) {
        Async.run(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                List<RoutineEntity> routines = db.routineDao().getEnabledRoutines();
                for (RoutineEntity routine : routines) {
                    ProfileEntity profile = db.profileDao().getById(routine.profileId);
                    if (profile == null || profile.isArchived()) {
                        cancel(context, routine.id);
                        continue;
                    }
                    reschedule(context, routine, profile);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    public static void reschedule(Context context, RoutineEntity routine, ProfileEntity profile) {
        cancel(context, routine.id);
        if (routine == null || profile == null || !routine.enabled || profile.isArchived()) {
            return;
        }
        long now = System.currentTimeMillis();
        long triggerAt;
        if (RoutineEntity.TYPE_ONCE.equals(routine.type)) {
            if (routine.onceAt == null || routine.onceAt <= now) {
                return;
            }
            triggerAt = routine.onceAt;
        } else {
            triggerAt = nextWeeklyOccurrence(routine, now);
            if (triggerAt == Long.MAX_VALUE) {
                return;
            }
        }
        schedule(context, routine.id, triggerAt);
    }

    public static void cancel(Context context, String routineId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent pending = pendingIntent(context, routineId);
        alarmManager.cancel(pending);
        pending.cancel();
    }

    private static void schedule(Context context, String routineId, long triggerAt) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent pending = pendingIntent(context, routineId);
        if (canScheduleExact(alarmManager)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        } else {
            // Fallback that fires exactly even in Doze without the alarm permission.
            Intent show = new Intent(context, io.github.jaffe2718.petprofile.ui.MainActivity.class);
            PendingIntent showIntent = PendingIntent.getActivity(context, routineId.hashCode(), show,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(triggerAt, showIntent);
            alarmManager.setAlarmClock(info, pending);
        }
    }

    private static boolean canScheduleExact(AlarmManager alarmManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        return alarmManager.canScheduleExactAlarms();
    }

    private static PendingIntent pendingIntent(Context context, String routineId) {
        Intent intent = new Intent(context, RoutineAlarmReceiver.class);
        intent.putExtra(RoutineAlarmReceiver.EXTRA_ROUTINE_ID, routineId);
        int requestCode = routineId.hashCode();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    private static long nextWeeklyOccurrence(RoutineEntity routine, long now) {
        Set<Integer> selected = parseWeekdays(routine.weekdays);
        if (selected.isEmpty()) {
            for (int i = 0; i < 7; i++) {
                selected.add(i);
            }
        }
        long best = Long.MAX_VALUE;
        for (int offset = 0; offset < 7; offset++) {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, routine.hour);
            calendar.set(Calendar.MINUTE, routine.minute);
            calendar.set(Calendar.SECOND, routine.second);
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.add(Calendar.DAY_OF_YEAR, offset);
            int index = calendar.get(Calendar.DAY_OF_WEEK) - 1;
            long millis = calendar.getTimeInMillis();
            if (selected.contains(index) && millis > now) {
                best = Math.min(best, millis);
            }
        }
        return best;
    }

    private static Set<Integer> parseWeekdays(String value) {
        Set<Integer> result = new LinkedHashSet<>();
        if (value == null || value.trim().isEmpty()) {
            return result;
        }
        for (String part : value.split(",")) {
            try {
                result.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }
}
