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
 */
final class Telemetry {
    static final String TAG = "Telemetry";

    static final String KEY_LIMIT_AD_TRACKING = "limit_ad_tracking"; // Secure
    static final String KEY_ADVERTISING_ID = "advertising_id";       // Secure
    static final String KEY_DTCP = "IS_DTCP_ENABLED";                // Global

    private static final String PREF_DISABLED = "telemetry_disabled";

    private Telemetry() {}

    static boolean isEnabled(Context c) {
        return prefs(c).getBoolean(PREF_DISABLED, false);
    }

    /** Off reverts to the defaults the Fire OS settings screen would write. */
    static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(PREF_DISABLED, on).commit();
        apply(c, on ? "on" : "off");
    }

    /** Idempotent: writes the blocked values when on, the defaults when off. */
    static void apply(Context c, String reason) {
        boolean on = isEnabled(c);
        int trackBefore = currentTrack(c);
        int dtcpBefore = currentDtcp(c);
        if (hasWritePermission(c)) {
            writeTrack(c, on ? 1 : 0);
            writeDtcp(c, on ? 0 : 1);
        }
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
