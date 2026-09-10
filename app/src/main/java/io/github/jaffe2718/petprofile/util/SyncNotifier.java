package io.github.jaffe2718.petprofile.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import io.github.jaffe2718.petprofile.R;

/**
 * A determinate progress notification for a cloud sync ("syncing x/y files"). It is a plain
 * ongoing notification rather than a foreground service — the sync is short and user-initiated.
 *
 * <p>Because it is not owned by a foreground service, a process that dies mid-sync cannot cancel
 * it. Two safety nets cover that: every re-post carries a fresh {@link #STALE_TIMEOUT_MS} expiry, so
 * a stalled or finished run drops out of the shade on its own, and {@code PetProfileApplication}
 * cancels any leftover notification when the app starts again.
 */
public final class SyncNotifier {
    private static final String CHANNEL_ID = "cloud_sync_progress";
    private static final int NOTIFICATION_ID = 0x7fffff01;
    /**
     * Expiry of one posted state. Each progress update re-posts and therefore restarts the timer, and
     * the value stays above the slowest possible single-file transfer in {@code OneDriveBackupManager}
     * (a 30 s connect plus a 300 s read), so a live sync never expires — only a run whose process died
     * (no further updates) does.
     */
    private static final long STALE_TIMEOUT_MS = 360_000L;

    private SyncNotifier() {
    }

    public static void start(Context context, String title, int total) {
        post(context, title, 0, total);
    }

    public static void progress(Context context, String title, int done, int total) {
        post(context, title, done, total);
    }

    public static void finish(Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
    }

    private static void post(Context context, String title, int done, int total) {
        try {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                return;
            }
            ensureChannel(context);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(context.getString(R.string.sync_progress, done, total))
                    .setProgress(Math.max(total, 1), done, total <= 0)
                    .setOngoing(true)
                    .setSilent(true)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(false)
                    .setTimeoutAfter(STALE_TIMEOUT_MS)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS);
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
        } catch (Throwable ignored) {
        }
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.sync_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setShowBadge(false);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
    }
}
