package io.github.jaffe2718.petprofile.mcp;

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

import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.ui.MainActivity;

/**
 * Foreground service that keeps the LAN MCP server alive while the app is backgrounded or
 * swiped away. It is started/stopped only from the MCP dialog switch; the server is ready once
 * {@code isRunning()} reports true.
 */
public class McpService extends Service {
    private static final String CHANNEL_ID = "mcp_service";
    private static final int NOTIF_ID = 0x7ffffff2;

    public static void start(Context context) {
        McpServer.get(context).start();
        ContextCompat.startForegroundService(context, new Intent(context, McpService.class));
    }

    public static void stop(Context context) {
        McpServer.get(context).stop();
        context.stopService(new Intent(context, McpService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        McpServer.get(this).start();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, NOTIF_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        McpServer.get(this).stop();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        McpServer server = McpServer.get(this);
        Intent tap = new Intent(this, MainActivity.class);
        tap.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 0, tap,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String url = server.isRunning() ? server.getUrl() : getString(R.string.mcp_disabled);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mcp)
                .setContentTitle(getString(R.string.mcp_title))
                .setContentText(getString(R.string.mcp_service_running, url))
                .setOngoing(true)
                .setContentIntent(content)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
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
                getString(R.string.mcp_title),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setShowBadge(false);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }
}
