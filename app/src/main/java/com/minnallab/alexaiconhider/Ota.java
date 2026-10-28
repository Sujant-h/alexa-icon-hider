package com.minnallab.alexaiconhider;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Amazon's system update client. Disabling it needs CHANGE_COMPONENT_ENABLED_STATE, which adb
 * cannot grant (it is managed by a role), so the app only shows its state and the command.
 */
final class Ota {
    static final String PACKAGE = "com.amazon.device.software.ota";

    private Ota() {}

    /** Null when the package is missing, e.g. on a device without Amazon's update client. */
    static Boolean isDisabled(Context c) {
        try {
            int state = c.getPackageManager().getApplicationEnabledSetting(PACKAGE);
            return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Per user: the update job runs in the user we run as (10 on the Alexa Tablet 12 Pro). */
    static String command(boolean disable) {
        return "adb shell pm " + (disable ? "disable-user" : "enable")
                + " --user " + Hider.userId() + " " + PACKAGE;
    }
}
