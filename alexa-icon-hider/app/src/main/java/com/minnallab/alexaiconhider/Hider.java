package com.minnallab.alexaiconhider;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.util.Locale;

/**
 * Keeps the Fire Launcher's Alexa taskbar button hidden.
 *
 * The launcher (TabletAlexaIngressVisibilityController) shows the button only while
 * alexa_window_is_open == 0 && alexa_app_in_foreground == 0, and observes both keys live.
 * SystemUI (AlexaFloatingWindowModel.onInit) writes both back to 0 when it starts, so we
 * set alexa_app_in_foreground = 1 after boot and whenever it changes.
 */
final class Hider {
    static final String TAG = "AlexaIconHider";
    static final String KEY = "alexa_app_in_foreground";
    static final String GRANT_CMD = "adb shell pm grant --user 10 com.minnallab.alexaiconhider "
            + "android.permission.WRITE_SECURE_SETTINGS";

    private static final int JOB_ID = 1;
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "permission";
    private static final String PREF_NOTIFIED = "notified_missing_permission";
    private static final String PREF_HISTORY = "history";
    private static final int HISTORY_SIZE = 20;

    private Hider() {}

    static boolean hasWritePermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static int currentValue(Context c) {
        return Settings.Secure.getInt(c.getContentResolver(), KEY, 0);
    }

    /** Idempotent: writes only if the value differs, then (re)arms the change trigger. */
    static void apply(Context c, String reason) {
        int before = currentValue(c);
        boolean wrote = false;
        if (before != 1) {
            if (hasWritePermission(c)) {
                try {
                    wrote = Settings.Secure.putInt(c.getContentResolver(), KEY, 1);
                } catch (SecurityException e) {
                    Log.w(TAG, "write rejected", e);
                }
            }
            if (!wrote) notifyMissingPermission(c);
        }
        if (wrote || (before == 1 && hasWritePermission(c))) clearMissingPermission(c);
        long uptime = SystemClock.elapsedRealtime();
        Log.i(TAG, reason + " uptime=" + uptime + "ms before=" + before + " wrote=" + wrote);
        record(c, String.format(Locale.ROOT, "%tT  +%.1f s  %s  %d%s", System.currentTimeMillis(),
                uptime / 1000.0, shortReason(reason), before, wrote ? "→1" : ""));
        scheduleTrigger(c);
    }

    /** Last runs, newest first; logcat drops app logs early in boot on this device. */
    static String history(Context c) {
        return prefs(c).getString(PREF_HISTORY, "");
    }

    private static void record(Context c, String line) {
        SharedPreferences prefs = prefs(c);
        String[] old = prefs.getString(PREF_HISTORY, "").split("\n");
        StringBuilder sb = new StringBuilder(line);
        for (int i = 0; i < old.length && i < HISTORY_SIZE - 1; i++) {
            if (!old[i].isEmpty()) sb.append('\n').append(old[i]);
        }
        prefs.edit().putString(PREF_HISTORY, sb.toString()).commit();
    }

    private static String shortReason(String reason) {
        int dot = reason.lastIndexOf('.');
        return dot < 0 ? reason : reason.substring(dot + 1);
    }

    /**
     * One-shot job that fires when the setting changes. Content-trigger jobs cannot be
     * persisted, so the boot receiver arms it again after every boot.
     */
    static void scheduleTrigger(Context c) {
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, ResetJob.class))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(
                        Settings.Secure.getUriFor(KEY), 0))
                // SystemUI resets with a single write; no need for the ~10 s default debounce.
                .setTriggerContentUpdateDelay(0)
                .build();
        c.getSystemService(JobScheduler.class).schedule(job);
    }

    static boolean isTriggerScheduled(Context c) {
        return c.getSystemService(JobScheduler.class).getPendingJob(JOB_ID) != null;
    }

    /** Shown once until the permission works again. */
    private static void notifyMissingPermission(Context c) {
        SharedPreferences prefs = prefs(c);
        if (prefs.getBoolean(PREF_NOTIFIED, false)) return;
        Log.w(TAG, "WRITE_SECURE_SETTINGS missing, grant with: " + GRANT_CMD);

        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "Berechtigung", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(c, 0,
                new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Alexa Icon Hider: Berechtigung fehlt")
                .setContentText("Einmalig per ADB ausführen:")
                .setStyle(new Notification.BigTextStyle().bigText(
                        "Einmalig per ADB ausführen:\n" + GRANT_CMD))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        if (nm.areNotificationsEnabled()) nm.notify(NOTIFICATION_ID, n);
        prefs.edit().putBoolean(PREF_NOTIFIED, true).apply();
    }

    private static void clearMissingPermission(Context c) {
        SharedPreferences prefs = prefs(c);
        if (!prefs.getBoolean(PREF_NOTIFIED, false)) return;
        c.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
        prefs.edit().remove(PREF_NOTIFIED).apply();
    }

    // Device-protected storage, readable before the user unlocks (LOCKED_BOOT_COMPLETED).
    private static SharedPreferences prefs(Context c) {
        return c.createDeviceProtectedStorageContext()
                .getSharedPreferences("state", Context.MODE_PRIVATE);
    }
}
