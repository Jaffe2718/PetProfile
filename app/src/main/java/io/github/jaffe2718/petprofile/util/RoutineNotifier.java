package io.github.jaffe2718.petprofile.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;

import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.AppDatabase;
import io.github.jaffe2718.petprofile.data.entity.ProfileCustomFieldEntity;

public final class RoutineNotifier {
    public static final String CHANNEL_ID = "routine_work";

    private RoutineNotifier() {
    }

    public static void post(Context context, int notificationId, String title,
                            String nickname, String content) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return;
        }
        createChannel(context, manager);
        Intent intent = new Intent(context, io.github.jaffe2718.petprofile.ui.MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String fallbackTitle = title == null || title.trim().isEmpty()
                ? context.getString(R.string.app_name)
                : title;
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setContentTitle(fallbackTitle)
                .setContentText(content == null ? "" : content)
                .setSubText(nickname == null ? "" : nickname)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build();
        manager.notify(notificationId, notification);
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

    private static void createChannel(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.label_routine),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        manager.createNotificationChannel(channel);
    }
}
