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
import android.os.Process;
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
 *
 * If something else keeps writing 0 (e.g. an enabled Alexa app), we would fight it forever.
 * After LOOP_WRITES writes within LOOP_WINDOW_MS we switch ourselves off until the user
 * switches us on again in the app.
 */
final class Hider {
    static final String TAG = "AlexaIconHider";
    static final String KEY = "alexa_app_in_foreground";
    static final int LOOP_WRITES = 10;
    static final long LOOP_WINDOW_MS = 60_000;

    private static final int JOB_ID = 1;
    private static final int NOTIFICATION_ID = 1;
    private static final int NOTIFICATION_LOOP_ID = 2;
    private static final String CHANNEL_ID = "permission";
    private static final String CHANNEL_LOOP_ID = "loop";
    private static final String PREF_NOTIFIED = "notified_missing_permission";
    private static final String PREF_HISTORY = "history";
    private static final String PREF_HISTORY_BOOT = "history_boot";
    private static final String PREF_DISABLED = "disabled";
    private static final String PREF_WRITES = "writes";
    private static final String PREF_PAUSED_AT = "paused_at";
    private static final int HISTORY_SIZE = 20;
    // UserHandle.PER_USER_RANGE, hidden API: uid = userId * 100000 + appId.
    private static final int PER_USER_RANGE = 100_000;

    private Hider() {}

    /** The pm grant command for the user we run as (10 on the Alexa Tablet 12 Pro). */
    static String grantCommand() {
        return "adb shell pm grant --user " + Process.myUid() / PER_USER_RANGE
                + " com.minnallab.alexaiconhider android.permission.WRITE_SECURE_SETTINGS";
    }

    static boolean hasWritePermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static int currentValue(Context c) {
        return Settings.Secure.getInt(c.getContentResolver(), KEY, 0);
    }

    /**
     * Idempotent: writes only if the value differs, then (re)arms the change trigger.
     * While switched off it neither writes nor arms the trigger, it only records the run.
     * Opening the app is recorded only when it wrote, so it does not push boot runs out
     * of the history.
     */
    static void apply(Context c, String reason) {
        int before = currentValue(c);
        boolean on = isEnabled(c);
        boolean wrote = false;
        if (before != 1 && on) {
            wrote = write(c, 1);
            if (!wrote) notifyMissingPermission(c);
        }
        if (wrote || (before == 1 && hasWritePermission(c))) clearMissingPermission(c);
        long now = System.currentTimeMillis();
        // Keys, not labels: the status screen translates them when it shows the history.
        String suffix = on ? "" : "  off";
        if (wrote && recordWrite(c, now)) {
            pause(c, now);
            on = false;
            suffix = "  paused";
        }
        if (wrote || !"activity".equals(reason)) {
            log(c, now, reason, before, wrote ? "→1" + suffix : suffix);
        }
        if (on) scheduleTrigger(c);
    }

    static boolean isEnabled(Context c) {
        return !prefs(c).getBoolean(PREF_DISABLED, false);
    }

    /** Wall-clock time the loop guard switched us off, 0 if it did not. */
    static long pausedAt(Context c) {
        return prefs(c).getLong(PREF_PAUSED_AT, 0);
    }

    /**
     * The in-app switch. Off cancels the trigger and writes 0, the value SystemUI uses when
     * Alexa is not in the foreground, so the button shows again right away.
     */
    static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(PREF_DISABLED, !on)
                .remove(PREF_PAUSED_AT).remove(PREF_WRITES).commit();
        c.getSystemService(NotificationManager.class).cancel(NOTIFICATION_LOOP_ID);
        if (on) {
            apply(c, "on");
            return;
        }
        c.getSystemService(JobScheduler.class).cancel(JOB_ID);
        int before = currentValue(c);
        boolean wrote = before != 0 && write(c, 0);
        log(c, System.currentTimeMillis(), "off", before, wrote ? "→0" : "");
    }

    /**
     * Runs since the last boot, newest first; logcat drops app logs early in boot on this
     * device. Older runs are dropped by comparing the boot counter, not cleared by
     * BootReceiver, because the change trigger can run before the boot broadcast arrives.
     */
    static String history(Context c) {
        SharedPreferences prefs = prefs(c);
        return prefs.getInt(PREF_HISTORY_BOOT, -1) == bootCount(c)
                ? prefs.getString(PREF_HISTORY, "") : "";
    }

    private static int bootCount(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    private static boolean write(Context c, int value) {
        if (!hasWritePermission(c)) return false;
        try {
            return Settings.Secure.putInt(c.getContentResolver(), KEY, value);
        } catch (SecurityException e) {
            Log.w(TAG, "write rejected", e);
            return false;
        }
    }

    private static void log(Context c, long now, String reason, int before, String change) {
        long uptime = SystemClock.elapsedRealtime();
        Log.i(TAG, reason + " uptime=" + uptime + "ms before=" + before + change);
        record(c, String.format(Locale.ROOT, "%tT  +%.1f s  %s  %d%s", now,
                uptime / 1000.0, shortReason(reason), before, change));
    }

    private static void record(Context c, String line) {
        String[] old = history(c).split("\n");
        StringBuilder sb = new StringBuilder(line);
        for (int i = 0; i < old.length && i < HISTORY_SIZE - 1; i++) {
            if (!old[i].isEmpty()) sb.append('\n').append(old[i]);
        }
        prefs(c).edit().putString(PREF_HISTORY, sb.toString())
                .putInt(PREF_HISTORY_BOOT, bootCount(c)).commit();
    }

    private static String shortReason(String reason) {
        int dot = reason.lastIndexOf('.');
        return dot < 0 ? reason : reason.substring(dot + 1);
    }

    /** Remembers this write; true once LOOP_WRITES of them fall within LOOP_WINDOW_MS. */
    private static boolean recordWrite(Context c, long now) {
        SharedPreferences prefs = prefs(c);
        // Wall-clock, newest first; elapsedRealtime would mix up runs from different boots.
        StringBuilder sb = new StringBuilder().append(now);
        int count = 1;
        long oldest = now;
        for (String s : prefs.getString(PREF_WRITES, "").split(",")) {
            if (count == LOOP_WRITES) break;
            if (s.isEmpty()) continue;
            long t = Long.parseLong(s);
            if (t > now) continue; // clock was set back
            sb.append(',').append(t);
            oldest = t;
            count++;
        }
        prefs.edit().putString(PREF_WRITES, sb.toString()).commit();
        return count == LOOP_WRITES && now - oldest < LOOP_WINDOW_MS;
    }

    /** Switches off without writing 0: whoever keeps resetting the value gets to keep it. */
    private static void pause(Context c, long now) {
        prefs(c).edit().putBoolean(PREF_DISABLED, true).putLong(PREF_PAUSED_AT, now).commit();
        c.getSystemService(JobScheduler.class).cancel(JOB_ID);
        Log.w(TAG, "value reset " + LOOP_WRITES + " times within " + LOOP_WINDOW_MS + " ms, paused");
        post(c, NOTIFICATION_LOOP_ID, CHANNEL_LOOP_ID, c.getString(R.string.channel_loop),
                c.getString(R.string.notify_paused_title),
                c.getString(R.string.notify_paused_text),
                c.getString(R.string.notify_paused_big, LOOP_WRITES, LOOP_WINDOW_MS / 1000));
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

    /**
     * Shown once until the permission works again. While notifications are off nothing is
     * remembered, so the hint still appears once they are allowed.
     */
    private static void notifyMissingPermission(Context c) {
        SharedPreferences prefs = prefs(c);
        if (prefs.getBoolean(PREF_NOTIFIED, false)) return;
        String cmd = grantCommand();
        Log.w(TAG, "WRITE_SECURE_SETTINGS missing, grant with: " + cmd);
        String text = c.getString(R.string.notify_permission_text);
        if (post(c, NOTIFICATION_ID, CHANNEL_ID, c.getString(R.string.channel_permission),
                c.getString(R.string.notify_permission_title), text, text + "\n" + cmd)) {
            prefs.edit().putBoolean(PREF_NOTIFIED, true).apply();
        }
    }

    private static void clearMissingPermission(Context c) {
        SharedPreferences prefs = prefs(c);
        if (!prefs.getBoolean(PREF_NOTIFIED, false)) return;
        c.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
        prefs.edit().remove(PREF_NOTIFIED).apply();
    }

    /** Tapping it opens the status screen. False if notifications are off. */
    private static boolean post(Context c, int id, String channelId, String channelName,
            String title, String text, String bigText) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(c, 0,
                new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(bigText))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        if (!nm.areNotificationsEnabled()) return false;
        nm.notify(id, n);
        return true;
    }

    // Device-protected storage, readable before the user unlocks (LOCKED_BOOT_COMPLETED).
    private static SharedPreferences prefs(Context c) {
        return c.createDeviceProtectedStorageContext()
                .getSharedPreferences("state", Context.MODE_PRIVATE);
    }
}
