package com.minnallab.alexaiconhider;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Status screen. Opening it once after install also takes the app out of the
 * "stopped" state, which Android requires before boot broadcasts are delivered.
 */
public final class MainActivity extends Activity {
    private TextView text;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        text = new TextView(this);
        text.setTextIsSelectable(true);
        text.setTextSize(16);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        text.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
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
        boolean write = Hider.hasWritePermission(this);
        StringBuilder sb = new StringBuilder()
                .append("Alexa-Symbol: ")
                .append(Hider.currentValue(this) == 1 ? "ausgeblendet" : "sichtbar")
                .append("\n").append(Hider.KEY).append(" = ").append(Hider.currentValue(this))
                .append("\nWRITE_SECURE_SETTINGS: ").append(write ? "erteilt" : "fehlt")
                .append("\nÄnderungs-Trigger aktiv: ")
                .append(Hider.isTriggerScheduled(this) ? "ja" : "nein");
        if (!write) {
            sb.append("\n\nEinmalig am Computer ausführen:\n").append(Hider.GRANT_CMD);
        }
        sb.append("\n\nLetzte Läufe (Uhrzeit, seit Boot, Anlass, Wert vorher):\n")
                .append(Hider.history(this));
        text.setText(sb);
    }
}
