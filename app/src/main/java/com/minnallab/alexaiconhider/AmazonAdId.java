package com.minnallab.alexaiconhider;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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

    // A service that binds but never answers must not leave the toggle half applied.
    private static final long TIMEOUT_MS = 5000;

    interface Calls {
        void run(IBinder service) throws RemoteException;
    }

    interface Done {
        /** ran is false if the service is missing, timed out or a call failed. */
        void run(boolean ran);
    }

    private AmazonAdId() {}

    /**
     * Binds, runs the calls on the main thread, unbinds and then runs done exactly once.
     * If the service is not there, done(false) runs right away, so the caller falls back to
     * writing the settings itself.
     */
    static void call(Context c, Calls calls, Done done) {
        Connection conn = new Connection(c.getApplicationContext(), calls, done);
        boolean bound;
        try {
            bound = conn.app.bindService(new Intent().setComponent(SERVICE), conn,
                    Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            // Another firmware may not export the service or guard it with a permission.
            Log.w(TAG, "bind rejected", e);
            bound = false;
        }
        if (bound) {
            conn.main.postDelayed(conn.timeout, TIMEOUT_MS);
        } else {
            Log.w(TAG, "service not available");
            conn.finish(false);
        }
    }

    private static final class Connection implements ServiceConnection {
        final Context app;
        final Handler main = new Handler(Looper.getMainLooper());
        final Runnable timeout = () -> {
            Log.w(TAG, "service did not answer");
            finish(false);
        };
        private final Calls calls;
        private final Done done;
        private boolean finished;

        Connection(Context app, Calls calls, Done done) {
            this.app = app;
            this.calls = calls;
            this.done = done;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (finished) return;
            boolean ran = false;
            try {
                calls.run(service);
                ran = true;
            } catch (RemoteException | RuntimeException e) {
                Log.w(TAG, "call failed", e);
            }
            finish(ran);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}

        @Override
        public void onBindingDied(ComponentName name) {
            finish(false);
        }

        @Override
        public void onNullBinding(ComponentName name) {
            finish(false);
        }

        void finish(boolean ran) {
            if (finished) return;
            finished = true;
            main.removeCallbacks(timeout);
            try {
                app.unbindService(this);
            } catch (IllegalArgumentException e) {
                // Not registered, e.g. after bindService threw.
            }
            done.run(ran);
        }
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
