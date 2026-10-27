package com.minnallab.alexaiconhider;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

import java.util.UUID;

/**
 * Disables Amazon's ad tracking and device-targeted content.
 *
 * The Fire Launcher's ads stack (AdsContextProvider) reads limit_ad_tracking (Secure),
 * advertising_id (Secure) and IS_DTCP_ENABLED (Global) live via ContentObserver. Unlike the
 * Alexa key, SystemUI does not reset these on boot, so a one-shot write on toggle/boot/open
 * is enough: no change-trigger job and no loop guard.
 *
 * While off we never write, so boot and updates leave the user's own Fire OS choice alone.
 * Switching off restores the values that were there when the user switched on.
 *
 * Switching on also resets the advertising id once. Amazon's policy forbids apps to carry
 * earlier data over to a new id, so this cuts the profile collected so far. We do not rotate
 * it periodically: with limit_ad_tracking set, the id is only left for frequency capping
 * and attribution, and that would need a recurring job.
 */
final class Telemetry {
    static final String TAG = "Telemetry";

    static final String KEY_LIMIT_AD_TRACKING = "limit_ad_tracking"; // Secure
    static final String KEY_ADVERTISING_ID = "advertising_id";       // Secure
    static final String KEY_DTCP = "IS_DTCP_ENABLED";                // Global

    private static final String PREF_DISABLED = "telemetry_disabled";
    private static final String PREF_SAVED_TRACK = "telemetry_saved_track";
    private static final String PREF_SAVED_DTCP = "telemetry_saved_dtcp";

    private Telemetry() {}

    static boolean isEnabled(Context c) {
        return prefs(c).getBoolean(PREF_DISABLED, false);
    }

    /**
     * On saves the current values first and resets the advertising id; off writes the saved
     * values back and forgets them. Only the user's tap gets here, never boot or update.
     */
    static void setEnabled(Context c, boolean on) {
        SharedPreferences p = prefs(c);
        if (on) {
            if (!p.contains(PREF_SAVED_TRACK)) {
                p.edit().putInt(PREF_SAVED_TRACK, currentTrack(c))
                        .putInt(PREF_SAVED_DTCP, currentDtcp(c)).commit();
            }
            p.edit().putBoolean(PREF_DISABLED, true).commit();
            apply(c, "on");
            if (hasWritePermission(c)) {
                resetAdvertisingId(c);
                Log.i(TAG, "on advertising_id reset");
            }
            return;
        }
        p.edit().putBoolean(PREF_DISABLED, false).commit();
        int trackBefore = currentTrack(c);
        int dtcpBefore = currentDtcp(c);
        if (p.contains(PREF_SAVED_TRACK) && hasWritePermission(c)) {
            writeTrack(c, p.getInt(PREF_SAVED_TRACK, 0));
            writeDtcp(c, p.getInt(PREF_SAVED_DTCP, 1));
        }
        p.edit().remove(PREF_SAVED_TRACK).remove(PREF_SAVED_DTCP).commit();
        log("off", false, trackBefore, dtcpBefore, c);
    }

    /** Idempotent: writes the blocked values when on, nothing when off. */
    static void apply(Context c, String reason) {
        boolean on = isEnabled(c);
        int trackBefore = currentTrack(c);
        int dtcpBefore = currentDtcp(c);
        if (on && hasWritePermission(c)) {
            writeTrack(c, 1);
            writeDtcp(c, 0);
        }
        log(reason, on, trackBefore, dtcpBefore, c);
    }

    private static void log(String reason, boolean on, int trackBefore, int dtcpBefore,
            Context c) {
        Log.i(TAG, reason + " on=" + on
                + " limit_ad_tracking=" + trackBefore + "->" + currentTrack(c)
                + " IS_DTCP_ENABLED=" + dtcpBefore + "->" + currentDtcp(c));
    }

    /** A fresh advertising id de-links this device from Amazon's ad profile. */
    static String resetAdvertisingId(Context c) {
        String id = UUID.randomUUID().toString();
        if (hasWritePermission(c)) {
            Settings.Secure.putString(c.getContentResolver(), KEY_ADVERTISING_ID, id);
        }
        return id;
    }

    static boolean hasWritePermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static int currentTrack(Context c) {
        return Settings.Secure.getInt(c.getContentResolver(), KEY_LIMIT_AD_TRACKING, 0);
    }

    static int currentDtcp(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), KEY_DTCP, 1);
    }

    static String currentAdId(Context c) {
        return Settings.Secure.getString(c.getContentResolver(), KEY_ADVERTISING_ID);
    }

    private static boolean writeTrack(Context c, int value) {
        try {
            return Settings.Secure.putInt(c.getContentResolver(), KEY_LIMIT_AD_TRACKING, value);
        } catch (SecurityException e) {
            Log.w(TAG, "write limit_ad_tracking rejected", e);
            return false;
        }
    }

    private static boolean writeDtcp(Context c, int value) {
        try {
            return Settings.Global.putInt(c.getContentResolver(), KEY_DTCP, value);
        } catch (SecurityException e) {
            Log.w(TAG, "write IS_DTCP_ENABLED rejected", e);
            return false;
        }
    }

    // Same file Hider uses; device-protected so it is readable before the first unlock.
    private static SharedPreferences prefs(Context c) {
        return c.createDeviceProtectedStorageContext()
                .getSharedPreferences("state", Context.MODE_PRIVATE);
    }
}
