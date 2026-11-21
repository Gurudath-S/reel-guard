package com.you.reelguard.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import com.you.reelguard.data.Repository;
import com.you.reelguard.detect.Platform;
import com.you.reelguard.detect.ScreenState;
import com.you.reelguard.ui.MainActivity;
import java.util.EnumSet;

final class HealthMonitor {
    public static final String CHANNEL = "selectors";
    private final Context context;
    private final Repository repository;
    private final NotificationManager notifications;
    private final EnumSet<Platform> degraded = EnumSet.noneOf(Platform.class);
    private Platform active;
    private long version, start, last;
    private int samples;

    HealthMonitor(Context context, Repository repository) {
        this.context = context;
        this.repository = repository;
        notifications = context.getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "Selector health",
                NotificationManager.IMPORTANCE_LOW));
    }

    void record(ScreenState state, long packageVersion, long elapsed) {
        if (state.evidence == ScreenState.Evidence.UNAVAILABLE) {
            leave();
            return;
        }
        if (state.evidence != ScreenState.Evidence.UNKNOWN) {
            if (degraded.remove(state.platform)) {
                repository.setHealth(state.platform, null, null);
                notifications.cancel(notificationId(state.platform));
            }
            leave();
            return;
        }
        if (active != state.platform || version != packageVersion) {
            active = state.platform;
            version = packageVersion;
            start = last = elapsed;
            samples = 1;
        } else if (elapsed - last >= 1000) {
            last = elapsed;
            samples++;
        }
        if (samples >= 5 && last - start >= 10000 && degraded.add(state.platform)) {
            Platform platform = state.platform;
            String message = platform.label + " selectors may be outdated.";
            repository.setHealth(platform, message, due -> {
                if (due && degraded.contains(platform)) notify(platform, message);
            });
        }
    }

    void leave() { active = null; }

    private void notify(Platform platform, String message) {
        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Intent dashboard = new Intent(context, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(context, notificationId(platform), dashboard,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification alert = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("ReelGuard")
                .setContentText(message)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        notifications.notify(notificationId(platform), alert);
    }

    private int notificationId(Platform platform) { return 410 + platform.ordinal(); }
}
