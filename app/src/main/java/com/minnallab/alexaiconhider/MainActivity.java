package com.minnallab.alexaiconhider;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.text.DecimalFormatSymbols;

/**
 * Status screen with the on/off switch. Opening it once after install also takes the app
 * out of the "stopped" state, which Android requires before boot broadcasts are delivered.
 */
public final class MainActivity extends Activity {
    // Column weights of the history table, shared by header and rows.
    private static final float[] COLUMNS = {1f, 1.1f, 1.9f, 0.6f};
    private static final String STATE_HISTORY_OPEN = "history_open";

    private Switch toggle;
    private TaskbarFigure figure;
    private TextView state, headline, summary, legend, calloutTitle, calloutText, command,
            historyCount;
    private View stateDot, callout, commandRow, historyToggle, historyTable, historyChevron;
    private Button copy;
    private LinearLayout history;
    private boolean shown;
    // The history is for diagnosis, so it starts closed each time the screen is opened.
    private boolean historyOpen;

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
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_main);
        toggle = findViewById(R.id.toggle);
        figure = findViewById(R.id.figure);
        state = findViewById(R.id.state);
        stateDot = findViewById(R.id.state_dot);
        headline = findViewById(R.id.headline);
        summary = findViewById(R.id.summary);
        legend = findViewById(R.id.figure_legend);
        callout = findViewById(R.id.callout);
        calloutTitle = findViewById(R.id.callout_title);
        calloutText = findViewById(R.id.callout_text);
        commandRow = findViewById(R.id.command_row);
        copy = findViewById(R.id.copy);
        history = findViewById(R.id.history);
        historyToggle = findViewById(R.id.history_toggle);
        historyTable = findViewById(R.id.history_table);
        historyChevron = findViewById(R.id.history_chevron);
        historyCount = findViewById(R.id.history_count);

        layoutColumns();
        toggle.setText(toggleLabel());
        // Click, not checked-change: only the user's tap should switch, not showStatus().
        toggle.setOnClickListener(v -> {
            Hider.setEnabled(this, toggle.isChecked());
            showStatus();
        });
        command = findViewById(R.id.command);
        copy.setOnClickListener(v -> copyCommand());
        ((TextView) findViewById(R.id.version)).setText("v" + versionName());
        addHistoryCells((LinearLayout) findViewById(R.id.history_header), R.style.Text_Eyebrow,
                getString(R.string.col_time), getString(R.string.col_uptime),
                getString(R.string.col_reason), getString(R.string.col_value));
        historyOpen = savedState != null && savedState.getBoolean(STATE_HISTORY_OPEN);
        setHistoryOpen(historyOpen, false);
        historyToggle.setOnClickListener(v -> setHistoryOpen(!historyOpen, true));

        // Apps targeting API 35+ are drawn edge-to-edge; keep content clear of the system bars.
        findViewById(R.id.scroll).setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });

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

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean(STATE_HISTORY_OPEN, historyOpen);
    }

    /** Side by side on wide screens (R.bool.wide, w720dp), stacked otherwise. */
    private void layoutColumns() {
        LinearLayout columns = findViewById(R.id.columns);
        View primary = findViewById(R.id.primary);
        View secondary = findViewById(R.id.secondary);
        int gap = getResources().getDimensionPixelSize(R.dimen.column_gap);
        boolean wide = getResources().getBoolean(R.bool.wide);
        columns.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout.LayoutParams p = wide
                ? new LinearLayout.LayoutParams(0, -2, 1f)
                : new LinearLayout.LayoutParams(-1, -2);
        LinearLayout.LayoutParams s = wide
                ? new LinearLayout.LayoutParams(0, -2, 1.15f)
                : new LinearLayout.LayoutParams(-1, -2);
        if (wide) s.setMarginStart(gap); else s.topMargin = gap;
        primary.setLayoutParams(p);
        secondary.setLayoutParams(s);
    }

    private CharSequence toggleLabel() {
        SpannableStringBuilder sb = new SpannableStringBuilder(getString(R.string.toggle_title));
        int start = sb.length();
        sb.append('\n').append(getString(R.string.toggle_summary));
        int end = sb.length();
        sb.setSpan(new AbsoluteSizeSpan(13, true), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(getColor(R.color.subtle)), start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new TypefaceSpan(Typeface.create(Typeface.SANS_SERIF, 400, false)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    private void showStatus() {
        boolean on = Hider.isEnabled(this);
        boolean write = Hider.hasWritePermission(this);
        boolean trigger = Hider.isTriggerScheduled(this);
        boolean exempt = Hider.isExempt(this);
        // Only once it can write; the grant command comes first.
        boolean exemptHint = on && write && !exempt;
        int value = Hider.currentValue(this);
        long pausedAt = Hider.pausedAt(this);
        int accent = getColor(R.color.accent);
        int warn = getColor(R.color.warn);
        int subtle = getColor(R.color.subtle);

        toggle.setChecked(on);
        if (pausedAt != 0) {
            setState(R.string.state_paused, warn, R.string.headline_paused, R.string.summary_paused);
        } else if (on && !write) {
            setState(R.string.state_setup, warn, R.string.headline_setup, R.string.summary_setup);
        } else if (on) {
            setState(R.string.state_active, accent,
                    value == 1 ? R.string.headline_hidden : R.string.headline_visible,
                    R.string.summary_on);
        } else {
            // Off but still 1, e.g. without the permission to write 0: say what the taskbar shows.
            setState(R.string.state_off, subtle,
                    value == 1 ? R.string.headline_hidden : R.string.headline_visible,
                    R.string.summary_off);
        }

        if (pausedAt != 0) {
            calloutTitle.setText(R.string.callout_paused_title);
            calloutText.setText(getString(R.string.callout_paused_text,
                    Hider.LOOP_WRITES, (int) (Hider.LOOP_WINDOW_MS / 1000), pausedAt));
        } else if (!write) {
            calloutTitle.setText(R.string.callout_setup_title);
            calloutText.setText(R.string.callout_setup_text);
            command.setText(Hider.grantCommand());
        } else if (exemptHint) {
            calloutTitle.setText(R.string.callout_exempt_title);
            calloutText.setText(R.string.callout_exempt_text);
            command.setText(Hider.exemptCommand());
        }
        callout.setVisibility(pausedAt != 0 || !write || exemptHint ? View.VISIBLE : View.GONE);
        commandRow.setVisibility(pausedAt == 0 && (!write || exemptHint)
                ? View.VISIBLE : View.GONE);

        String figureState = getString(value == 1
                ? R.string.figure_hidden : R.string.figure_visible);
        figure.setHidden(value == 1, shown);
        figure.setContentDescription(getString(R.string.figure_description, figureState));
        // "1 · state", the number matching the marker in the figure.
        SpannableStringBuilder key = new SpannableStringBuilder("1 · ").append(figureState);
        key.setSpan(new ForegroundColorSpan(accent), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        legend.setText(key);
        shown = true;

        // Red flags only where the switch says the app should be working.
        setRow(R.id.setting_dot, R.id.setting_value, String.valueOf(value),
                value == 1 ? accent : on ? warn : subtle);
        setRow(R.id.permission_dot, R.id.permission_value,
                getString(write ? R.string.granted : R.string.missing), write ? accent : warn);
        setRow(R.id.trigger_dot, R.id.trigger_value,
                getString(trigger ? R.string.active : R.string.inactive),
                trigger ? accent : on ? warn : subtle);
        setRow(R.id.exempt_dot, R.id.exempt_value,
                getString(exempt ? R.string.granted : R.string.missing),
                exempt ? accent : on ? warn : subtle);
        showHistory();
    }

    private void setState(int label, int color, int title, int text) {
        state.setText(label);
        state.setTextColor(color);
        stateDot.setBackgroundTintList(ColorStateList.valueOf(color));
        headline.setText(title);
        summary.setText(text);
    }

    private void setRow(int dotId, int valueId, String value, int color) {
        findViewById(dotId).setBackgroundTintList(ColorStateList.valueOf(color));
        ((TextView) findViewById(valueId)).setText(value);
    }

    private void setHistoryOpen(boolean open, boolean animate) {
        historyOpen = open;
        historyTable.setVisibility(open ? View.VISIBLE : View.GONE);
        historyChevron.animate().cancel();
        if (animate) {
            historyChevron.animate().rotation(open ? 180 : 0).setDuration(200);
        } else {
            historyChevron.setRotation(open ? 180 : 0);
        }
        historyToggle.setStateDescription(
                getString(open ? R.string.history_open : R.string.history_closed));
    }

    private void showHistory() {
        history.removeAllViews();
        String all = Hider.history(this);
        String[] lines = all.isEmpty() ? new String[0] : all.split("\n");
        String count = getResources().getQuantityString(
                R.plurals.history_count, lines.length, lines.length);
        historyCount.setText(String.valueOf(lines.length));
        historyToggle.setContentDescription(getString(R.string.history_title) + ", " + count);
        findViewById(R.id.history_empty).setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
        // Stored with Locale.ROOT so it parses back; shown with the screen's decimal separator.
        char decimal = DecimalFormatSymbols.getInstance(
                getResources().getConfiguration().getLocales().get(0)).getDecimalSeparator();
        for (String line : lines) {
            LinearLayout row = new LinearLayout(this, null, 0, R.style.Row);
            row.setMinimumHeight(0);
            // Hider.log: "time  +uptime s  reason  before[→after][  off|paused]".
            String[] f = line.split(" {2}");
            if (f.length < 4) {
                addHistoryCells(row, R.style.Text_Mono, line);
            } else {
                String reason = reasonLabel(f[2]) + (f.length > 4 ? " · " + flagLabel(f[4]) : "");
                addHistoryCells(row, R.style.Text_Mono, f[0], f[1].replace('.', decimal),
                        reason, f[3]);
                TextView uptime = (TextView) row.getChildAt(1);
                uptime.setTextColor(getColor(R.color.subtle));
                TextView why = (TextView) row.getChildAt(2);
                why.setTypeface(Typeface.SANS_SERIF);
                why.setTextSize(14);
                if (f[3].contains("→")) {
                    ((TextView) row.getChildAt(3)).setTextColor(getColor(R.color.accent));
                }
            }
            history.addView(row);
        }
    }

    private void addHistoryCells(LinearLayout row, int style, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            TextView t = new TextView(this, null, 0, style);
            t.setText(cells[i]);
            if (i == COLUMNS.length - 1) t.setGravity(Gravity.END);
            float weight = cells.length == 1 ? 1 : COLUMNS[i];
            row.addView(t, new LinearLayout.LayoutParams(0, -2, weight));
        }
    }

    private String reasonLabel(String reason) {
        switch (reason) {
            case "activity": return getString(R.string.reason_activity);
            case "on": return getString(R.string.reason_on);
            case "off": return getString(R.string.reason_off);
            case "trigger": return getString(R.string.reason_trigger);
            case "LOCKED_BOOT_COMPLETED": return getString(R.string.reason_locked_boot);
            case "BOOT_COMPLETED": return getString(R.string.reason_boot);
            case "MY_PACKAGE_REPLACED": return getString(R.string.reason_update);
            default: return reason;
        }
    }

    private String flagLabel(String flag) {
        switch (flag) {
            case "off": return getString(R.string.flag_off);
            case "paused": return getString(R.string.flag_paused);
            default: return flag;
        }
    }

    /** Android 13+ confirms the copy itself, so the button only changes its label. */
    private void copyCommand() {
        getSystemService(ClipboardManager.class)
                .setPrimaryClip(ClipData.newPlainText("adb", command.getText()));
        copy.setText(R.string.copied);
        copy.removeCallbacks(resetCopy);
        copy.postDelayed(resetCopy, 2000);
    }

    private final Runnable resetCopy = () -> copy.setText(R.string.copy);

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }
}
