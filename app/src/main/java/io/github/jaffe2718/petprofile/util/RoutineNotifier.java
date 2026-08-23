package io.github.jaffe2718.petprofile.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.AppDatabase;
import io.github.jaffe2718.petprofile.data.entity.ProfileCustomFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;
import io.github.jaffe2718.petprofile.data.entity.RoutineEntity;

public final class RoutineNotifier {
    public static final String CHANNEL_ID = "routine_work_silent";
    public static final String GROUP_KEY = "routine_work_group";
    public static final int SUMMARY_ID = 0x7ffffff0;

    private static final String PREF_NAME = "routine_notif_state";
    private static final String PREF_ACTIVE_IDS = "active_ids";

    private RoutineNotifier() {
    }

    /** Reconciles notifications on a worker thread. */
    public static void sync(Context context) {
        Async.run(() -> syncNow(context));
    }

    /** Reconciles notifications on the calling thread (use on a worker thread). */
    public static void syncNow(Context context) {
        try {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                cancelAll(context);
                return;
            }
            List<RoutineEntity> pending = computePending(context);
            ensureChannel(context);
            if (pending.isEmpty()) {
                cancelSummary(context);
                RoutineScheduler.cancelRepost(context);
            } else {
                postChildren(context, pending);
                NotificationManagerCompat.from(context).notify(SUMMARY_ID,
                        buildSummary(context, pending));
                RoutineScheduler.scheduleRepost(context);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Computes the routines that currently need a persistent notification, rolls today's
     * occurrences, cancels any card that is no longer pending (completed/skipped/disabled/
     * archived/deleted), and persists the active set. Returns the pending list.
     */
    public static List<RoutineEntity> computePending(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        List<RoutineEntity> routines = db.routineDao().getAllRoutines();
        long now = System.currentTimeMillis();
        long todayStart = RoutineTodoMath.startOfToday();

        Set<String> activeIds = new HashSet<>();
        List<RoutineEntity> pending = new ArrayList<>();
        for (RoutineEntity routine : routines) {
            if (routine == null || !routine.enabled) {
                continue;
            }
            ProfileEntity profile = db.profileDao().getById(routine.profileId);
            if (profile == null || profile.isArchived()) {
                continue;
            }
            Long dueToday = RoutineTodoMath.computeDueToday(routine, now);
            if (dueToday != null && routine.lastInteractionTime < todayStart) {
                routine.lastInteractionTime = dueToday;
                routine.completed = false;
                db.routineDao().update(routine);
            }
            if (routine.completed) {
                continue;
            }
            if (!RoutineTodoMath.isRelevant(routine, dueToday, now, todayStart)) {
                continue;
            }
            long dueTime = dueToday != null ? dueToday : routine.lastInteractionTime;
            // A pending todo notifies once its due time has been reached/passed (both SKIP and
            // CARRY); it ends only when completed.
            if (now < dueTime) {
                continue;
            }
            activeIds.add(routine.id);
            pending.add(routine);
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> previous = new HashSet<>(prefs.getStringSet(PREF_ACTIVE_IDS, Collections.emptySet()));
        for (String id : previous) {
            if (!activeIds.contains(id)) {
                cancel(context, id.hashCode());
            }
        }
        prefs.edit().putStringSet(PREF_ACTIVE_IDS, new HashSet<>(activeIds)).apply();
        return pending;
    }

    /** Posts the per-routine child notifications (grouped, ongoing). */
    public static void postChildren(Context context, List<RoutineEntity> pending) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return;
        }
        ensureChannel(context);
        for (RoutineEntity routine : pending) {
            postChild(context, routine);
        }
    }

    /** Builds the group summary notification (used as the foreground notification). */
    public static Notification buildSummary(Context context, List<RoutineEntity> pending) {
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        for (RoutineEntity routine : pending) {
            style.addLine(routine.title == null || routine.title.trim().isEmpty()
                    ? context.getString(R.string.label_routine)
                    : routine.title.trim());
        }
        String summaryText = context.getString(R.string.routine_pending_summary, pending.size());
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setContentTitle(context.getString(R.string.action_daily_todo))
                .setContentText(summaryText)
                .setOnlyAlertOnce(true)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(style)
                .setContentIntent(contentIntent(context, SUMMARY_ID));
        return builder.build();
    }

    private static void postChild(Context context, RoutineEntity routine) {
        int id = routine.id.hashCode();
        String nickname = findNickname(context, routine.profileId);
        String title = routine.title == null || routine.title.trim().isEmpty()
                ? context.getString(R.string.label_routine)
                : routine.title.trim();
        String body = routine.details == null ? "" : routine.details.trim();
        String content = body.isEmpty() ? nickname : body;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setContentTitle(title)
                .setContentText(content)
                .setSubText(nickname)
                .setOnlyAlertOnce(true)
                .setGroup(GROUP_KEY)
                .setGroupSummary(false)
                .setOngoing(true)
                .setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(contentIntent(context, id));
        if (!body.isEmpty()) {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(body));
        }
        NotificationManagerCompat.from(context).notify(id, builder.build());
    }

    private static PendingIntent contentIntent(Context context, int requestCode) {
        Intent intent = new Intent(context, io.github.jaffe2718.petprofile.ui.DailyTodoActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void cancel(Context context, int notificationId) {
        NotificationManagerCompat.from(context).cancel(notificationId);
    }

    public static void cancelRoutine(Context context, String routineId) {
        cancel(context, routineId.hashCode());
    }

    public static void cancelSummary(Context context) {
        cancel(context, SUMMARY_ID);
    }

    private static void cancelAll(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        for (String id : prefs.getStringSet(PREF_ACTIVE_IDS, Collections.emptySet())) {
            cancel(context, id.hashCode());
        }
        cancel(context, SUMMARY_ID);
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        final int targetImportance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel existing = manager.getNotificationChannel(CHANNEL_ID);
        // Downgrade an existing sound channel to silent. (Android keeps a channel the user has
        // customized, so a user's own choice is preserved if they changed it.)
        if (existing != null && existing.getImportance() != targetImportance) {
            manager.deleteNotificationChannel(CHANNEL_ID);
            existing = manager.getNotificationChannel(CHANNEL_ID);
        }
        if (existing == null) {
            // Clean up the previous (sound/old importance) channel if it still exists.
            try {
                manager.deleteNotificationChannel("routine_work");
            } catch (Throwable ignored) {
            }
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.label_routine),
                    targetImportance
            );
            channel.setShowBadge(true);
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
        }
    }

    public static String findNickname(Context context, String profileId) {
        try {
            List<ProfileCustomFieldEntity> fields =
                    AppDatabase.getInstance(context).profileDao().getCustomFields(profileId);
            for (ProfileCustomFieldEntity field : fields) {
                if (isNicknameField(field)) {
                    return field.textValue == null ? "" : field.textValue;
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static boolean isNicknameField(ProfileCustomFieldEntity field) {
        return "nickname".equalsIgnoreCase(field.fieldKey)
                || "nickname".equalsIgnoreCase(field.fieldName)
                || "昵称".equals(field.fieldName)
                || "暱稱".equals(field.fieldName);
    }
}
