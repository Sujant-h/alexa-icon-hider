package com.minnallab.alexaiconhider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * LOCKED_BOOT_COMPLETED, BOOT_COMPLETED and MY_PACKAGE_REPLACED. The receiver is exported for
 * the system, so explicit broadcasts with other actions are ignored instead of logged.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Hider.apply(context, action);
            Telemetry.apply(context, action);
        }
    }
}
