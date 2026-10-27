package com.minnallab.alexaiconhider;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Insets;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

/**
 * Status screen with the on/off switch. Opening it once after install also takes the app
 * out of the "stopped" state, which Android requires before boot broadcasts are delivered.
 */
public final class MainActivity extends Activity {
    private Switch toggle;
    private TextView text;

    // Keeps the screen current while it is open, e.g. when the loop guard switches off.
    // ResetJob also runs on the main thread, so its run is recorded before this is called.
    private final ContentObserver observer = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            showStatus();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        toggle = new Switch(this);
        toggle.setText("Alexa-Symbol ausblenden");
        toggle.setTextSize(18);
        // Click, not checked-change: only the user's tap should switch, not showStatus().
        toggle.setOnClickListener(v -> {
            Hider.setEnabled(this, toggle.isChecked());
            showStatus();
        });
        text = new TextView(this);
        text.setTextIsSelectable(true);
        text.setTextSize(16);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        text.setPadding(0, pad, 0, 0);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, pad, pad, pad);
        column.addView(toggle);
        column.addView(text);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        // Apps targeting API 35+ are drawn edge-to-edge; keep text clear of the system bars.
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });
        setContentView(scroll);

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Hider.apply(this, "activity");
        showStatus();
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Hider.KEY), false, observer);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(observer);
    }

    private void showStatus() {
        boolean on = Hider.isEnabled(this);
        boolean write = Hider.hasWritePermission(this);
        toggle.setChecked(on);
        StringBuilder sb = new StringBuilder()
                .append("Alexa-Symbol: ")
                .append(Hider.currentValue(this) == 1 ? "ausgeblendet" : "sichtbar")
                .append("\n").append(Hider.KEY).append(" = ").append(Hider.currentValue(this))
                .append("\nWRITE_SECURE_SETTINGS: ").append(write ? "erteilt" : "fehlt")
                .append("\nÄnderungs-Trigger aktiv: ")
                .append(Hider.isTriggerScheduled(this) ? "ja" : "nein");
        long pausedAt = Hider.pausedAt(this);
        if (pausedAt != 0) {
            sb.append(String.format(Locale.ROOT,
                    "\n\nAutomatisch ausgeschaltet um %tT: Der Wert wurde %d-mal in %d s "
                            + "zurückgesetzt, zum Beispiel durch die aktivierte Alexa-App. "
                            + "Zum Fortsetzen den Schalter wieder einschalten.",
                    pausedAt, Hider.LOOP_WRITES, Hider.LOOP_WINDOW_MS / 1000));
        } else if (!on) {
            sb.append("\n\nAusgeschaltet: Die App schreibt nichts, das Symbol bleibt sichtbar.");
        }
        if (!write) {
            sb.append("\n\nEinmalig am Computer ausführen:\n").append(Hider.GRANT_CMD);
        }
        sb.append("\n\nLetzte Läufe (Uhrzeit, seit Boot, Anlass, Wert vorher):\n")
                .append(Hider.history(this));
        text.setText(sb);
    }
}
