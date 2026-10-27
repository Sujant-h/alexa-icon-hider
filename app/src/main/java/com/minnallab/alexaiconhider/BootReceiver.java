package com.minnallab.alexaiconhider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** LOCKED_BOOT_COMPLETED, BOOT_COMPLETED and MY_PACKAGE_REPLACED. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Hider.apply(context, String.valueOf(intent.getAction()));
    }
}
