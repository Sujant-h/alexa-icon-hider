package com.minnallab.alexaiconhider;

import android.app.ActivityManager;
import android.content.Context;

/**
 * RAM tuning that only works through DeviceConfig, which WRITE_SECURE_SETTINGS cannot write.
 * This class only detects the RAM size and renders the exact adb commands; the app cannot
 * apply them itself.
 */
final class Ram {
    static final int MAX_CACHED_SMALL = 32;
    static final int MAX_CACHED_LARGE = 64;
    static final int LARGE_RAM_GB = 12;

    private Ram() {}

    static long totalRamGb(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return Math.round(mi.totalMem / 1024.0 / 1024.0 / 1024.0);
    }

    /** Stock Android keeps ~32 cached processes; the 16 GB model can hold a few more. */
    static int recommendedMaxCached(Context c) {
        return totalRamGb(c) >= LARGE_RAM_GB ? MAX_CACHED_LARGE : MAX_CACHED_SMALL;
    }

    /** The three commands, applied by the shell's WRITE_DEVICE_CONFIG, not by this app. */
    static String commands(Context c) {
        int max = recommendedMaxCached(c);
        return "adb shell device_config put activity_manager max_cached_processes " + max + "\n"
                + "adb shell device_config put activity_manager proactive_kills_enabled true\n"
                + "adb shell device_config put activity_manager "
                + "bg_current_drain_auto_restrict_abusive_apps_enabled true";
    }
}
