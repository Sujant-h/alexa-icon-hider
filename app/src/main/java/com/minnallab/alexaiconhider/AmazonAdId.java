package com.minnallab.alexaiconhider;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

/**
 * Talks to Amazon's own advertising id service, the one the system Settings app uses.
 *
 * Writing limit_ad_tracking and advertising_id ourselves only changes the settings. The
 * service also tells Amazon's ad backend (MAUI) about the change, and its reset renews the
 * per-app and per-vendor ad ids it keeps in its own storage, not only advertising_id.
 * It is exported and only checks WRITE_SECURE_SETTINGS, which we already hold.
 *
 * Interface from AmazonAdIdService.apk (Alexa Tablet UI 16.1.10), a stable AIDL:
 * 1 isAdvertisingTrackingEnabled, 2 fetchAdvertisingId, 3 setAdvertisingTracking(boolean),
 * 4 resetAllAdvertisingIds. setAdvertisingTracking only notifies the backend when
 * limit_ad_tracking actually changes, so it must run before we write the key ourselves.
 */
final class AmazonAdId {
    static final String TAG = "AmazonAdId";
    static final ComponentName SERVICE = new ComponentName("com.amazon.adidmanager",
            "com.amazon.adidmanager.service.AmazonAdIdService");
    static final String DESCRIPTOR = "com.amazon.adidmanager.IAmazonAdIdService";
    private static final int TX_SET_ADVERTISING_TRACKING = 3;
    private static final int TX_RESET_ALL_ADVERTISING_IDS = 4;

    interface Calls {
        void run(IBinder service) throws RemoteException;
    }

    private AmazonAdId() {}

    /**
     * Binds, runs the calls on the main thread, unbinds and then runs done, also when a call
     * fails. Returns false without running anything if the service is not there, so the
     * caller falls back to writing the settings itself.
     */
    static boolean call(Context c, Calls calls, Runnable done) {
        Context app = c.getApplicationContext();
        ServiceConnection conn = new ServiceConnection() {
            private boolean finished;

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                try {
                    calls.run(service);
                } catch (RemoteException | RuntimeException e) {
                    Log.w(TAG, "call failed", e);
                }
                finish();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {}

            @Override
            public void onBindingDied(ComponentName name) {
                finish();
            }

            @Override
            public void onNullBinding(ComponentName name) {
                finish();
            }

            private void finish() {
                if (finished) return;
                finished = true;
                app.unbindService(this);
                done.run();
            }
        };
        if (app.bindService(new Intent().setComponent(SERVICE), conn, Context.BIND_AUTO_CREATE)) {
            return true;
        }
        app.unbindService(conn);
        Log.w(TAG, "service not available");
        return false;
    }

    static void setAdvertisingTracking(IBinder service, boolean enabled)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(DESCRIPTOR);
        data.writeBoolean(enabled);
        transact(service, TX_SET_ADVERTISING_TRACKING, data);
        Log.i(TAG, "setAdvertisingTracking " + enabled);
    }

    static void resetAllAdvertisingIds(IBinder service) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(DESCRIPTOR);
        transact(service, TX_RESET_ALL_ADVERTISING_IDS, data);
        Log.i(TAG, "resetAllAdvertisingIds");
    }

    private static void transact(IBinder service, int code, Parcel data) throws RemoteException {
        Parcel reply = Parcel.obtain();
        try {
            service.transact(code, data, reply, 0);
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
