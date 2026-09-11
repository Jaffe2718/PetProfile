package io.github.jaffe2718.petprofile.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import java.io.File;

import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.ui.MiscToolsActivity;

/**
 * Foreground service that carries an update download through while the app is in the background.
 *
 * <p>A 30 MB transfer from GitHub can take minutes, and a plain background thread dies with the
 * process as soon as the app is no longer around, so the transfer runs under a foreground service
 * with a progress notification. Progress is also broadcast inside the package, which is how the
 * settings screen keeps its dialog in step. The service stops itself once the download is verified,
 * or when it fails or is cancelled; either way a partial transfer is left behind to resume from.
 */
public class UpdateService extends Service {
    public static final String ACTION_PROGRESS = "io.github.jaffe2718.petprofile.UPDATE_PROGRESS";
    public static final String ACTION_READY = "io.github.jaffe2718.petprofile.UPDATE_READY";
    public static final String ACTION_FAILED = "io.github.jaffe2718.petprofile.UPDATE_FAILED";
    public static final String EXTRA_DOWNLOADED = "downloaded";
    public static final String EXTRA_TOTAL = "total";
    public static final String EXTRA_VERIFYING = "verifying";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_APK = "apk";
    /** Set on the notification's intent so the screen knows it was opened to install. */
    public static final String EXTRA_INSTALL = "install";
    /** The notification's cancel action. */
    private static final String ACTION_CANCEL = "io.github.jaffe2718.petprofile.UPDATE_CANCEL";

    private static final String TAG = "UpdateService";
    private static final String CHANNEL_ID = "update_download";
    private static final int PROGRESS_NOTIFICATION_ID = 0x7ffffff4;
    private static final int READY_NOTIFICATION_ID = 0x7ffffff5;
    private static final String EXTRA_TAG = "tag";
    private static final String EXTRA_VERSION = "version";
    private static final String EXTRA_URL = "url";
    private static final String EXTRA_SIZE = "size";
    private static final String EXTRA_SHA256 = "sha256";

    /** Guards the transfer so only one can ever be in flight, whatever the entry point. */
    private static final java.util.concurrent.atomic.AtomicBoolean RUNNING =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    /** Last reported progress, so a dialog opened mid-transfer starts where the transfer is. */
    private static volatile long lastDownloaded;
    private static volatile long lastTotal = -1;
    /** The transfer in flight, kept only so it can be cancelled. */
    private static volatile UpdateManager.Download activeDownload;

    public static boolean isDownloading() {
        return RUNNING.get();
    }

    /** {@code [downloaded, total]} of the transfer in flight, or of the last one. */
    public static long[] lastProgress() {
        return new long[]{lastDownloaded, lastTotal};
    }

    public static void start(Context context, UpdateManager.Release release) {
        if (isDownloading()) {
            return;
        }
        Intent intent = new Intent(context, UpdateService.class)
                .putExtra(EXTRA_TAG, release.tag)
                .putExtra(EXTRA_VERSION, release.version)
                .putExtra(EXTRA_URL, release.apkUrl)
                .putExtra(EXTRA_SIZE, release.size)
                .putExtra(EXTRA_SHA256, release.sha256);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void cancel(Context context) {
        UpdateManager.Download download = activeDownload;
        if (download != null) {
            download.cancel();
        }
    }

    /** Drops the "update ready" notification once its install has been handled. */
    public static void clearReadyNotification(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(READY_NOTIFICATION_ID);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            // The notification's own cancel action.
            cancel(this);
            return START_NOT_STICKY;
        }
        if (intent == null || intent.getStringExtra(EXTRA_URL) == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            // Already transferring: this start was a duplicate, let the running one continue.
            return START_NOT_STICKY;
        }
        UpdateManager.Release release = new UpdateManager.Release(
                intent.getStringExtra(EXTRA_TAG),
                intent.getStringExtra(EXTRA_VERSION),
                intent.getStringExtra(EXTRA_URL),
                intent.getLongExtra(EXTRA_SIZE, -1),
                intent.getStringExtra(EXTRA_SHA256));
        long total = release.size > 0 ? release.size : -1;
        lastDownloaded = 0;
        lastTotal = total;

        startForegroundCompat(progressNotification(0, total, false));
        activeDownload = UpdateManager.download(this, release, new UpdateManager.DownloadCallback() {
            @Override
            public void onProgress(long downloaded, long total, boolean resumed) {
                lastDownloaded = downloaded;
                lastTotal = total;
                post(progressNotification(downloaded, total, false));
                send(new Intent(ACTION_PROGRESS)
                        .putExtra(EXTRA_DOWNLOADED, downloaded)
                        .putExtra(EXTRA_TOTAL, total));
            }

            @Override
            public void onVerifying() {
                post(progressNotification(total, total, true));
                send(new Intent(ACTION_PROGRESS).putExtra(EXTRA_VERIFYING, true));
            }

            @Override
            public void onReady(File apk) {
                finish();
                ServiceCompat.stopForeground(UpdateService.this, ServiceCompat.STOP_FOREGROUND_REMOVE);
                notifyReadyNotification(apk);
                send(new Intent(ACTION_READY).putExtra(EXTRA_APK, apk.getAbsolutePath()));
                stopSelf();
            }

            @Override
            public void onError(String message) {
                finish();
                ServiceCompat.stopForeground(UpdateService.this, ServiceCompat.STOP_FOREGROUND_REMOVE);
                send(new Intent(ACTION_FAILED).putExtra(EXTRA_MESSAGE, message == null ? "" : message));
                stopSelf();
            }
        });
        return START_NOT_STICKY;
    }

    private static void finish() {
        activeDownload = null;
        RUNNING.set(false);
    }

    @Override
    public void onDestroy() {
        // Leaving the service (for instance the user swiped the task away) stops the transfer, but
        // the partial file and its ETag stay in the cache, so the next attempt resumes it.
        UpdateManager.Download download = activeDownload;
        if (download != null) {
            download.cancel();
        }
        finish();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, PROGRESS_NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(PROGRESS_NOTIFICATION_ID, notification);
        }
    }

    private Notification progressNotification(long downloaded, long total, boolean verifying) {
        int percent = total > 0 ? (int) Math.min(100, downloaded * 100 / total) : 0;
        String text;
        if (verifying) {
            text = getString(R.string.update_verifying);
        } else if (total > 0) {
            text = getString(R.string.update_download_progress, percent,
                    UpdateManager.formatBytes(downloaded), UpdateManager.formatBytes(total));
        } else {
            text = getString(R.string.update_downloading);
        }
        return baseNotification()
                .setContentTitle(getString(R.string.update_downloading))
                .setContentText(text)
                .setProgress(100, percent, total <= 0)
                .setOngoing(true)
                .addAction(0, getString(R.string.action_cancel), cancelPendingIntent())
                .build();
    }

    /** The notification's cancel button: stops the transfer but keeps the partial file. */
    private PendingIntent cancelPendingIntent() {
        Intent cancel = new Intent(this, UpdateService.class).setAction(ACTION_CANCEL);
        return PendingIntent.getService(this, 2, cancel,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void notifyReadyNotification(File apk) {
        Intent tap = new Intent(this, MiscToolsActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_INSTALL, true);
        PendingIntent content = PendingIntent.getActivity(this, 1, tap,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = baseNotification()
                .setContentTitle(getString(R.string.update_downloading))
                .setContentText(getString(R.string.update_notification_ready))
                .setContentIntent(content)
                .setAutoCancel(true)
                .setOngoing(false)
                .build();
        post(notification, READY_NOTIFICATION_ID);
        clearProgressNotification();
    }

    private NotificationCompat.Builder baseNotification() {
        Intent tap = new Intent(this, MiscToolsActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 0, tap,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cloud_down)
                .setContentIntent(content)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW);
    }

    private void post(Notification notification) {
        post(notification, PROGRESS_NOTIFICATION_ID);
    }

    private void post(Notification notification, int id) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(id, notification);
        }
    }

    private void clearProgressNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(PROGRESS_NOTIFICATION_ID);
        }
    }

    private void send(Intent intent) {
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.update_notification_channel),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setShowBadge(false);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }
}
