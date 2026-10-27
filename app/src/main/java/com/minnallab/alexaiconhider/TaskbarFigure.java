package com.minnallab.alexaiconhider;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * Fig. 01 on the status screen: a sketch of the Fire Launcher taskbar on a lab grid. The
 * Alexa widget sits at the right edge as a dark pill when visible and is left as a dashed
 * outline when hidden.
 */
public final class TaskbarFigure extends View {
    private final float dp;
    private final Paint grid = new Paint();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint word = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int bg, line, lineStrong, gridColor, text, subtle, accent, onAccent, halo;

    // 0 = button shown, 1 = hidden; animated between the two.
    private float hidden = -1;
    private ValueAnimator animator;

    public TaskbarFigure(Context c, AttributeSet attrs) {
        super(c, attrs);
        dp = getResources().getDisplayMetrics().density;
        bg = c.getColor(R.color.bg);
        line = c.getColor(R.color.line);
        lineStrong = c.getColor(R.color.line_strong);
        gridColor = c.getColor(R.color.grid);
        text = c.getColor(R.color.text);
        subtle = c.getColor(R.color.subtle);
        accent = c.getColor(R.color.accent);
        onAccent = c.getColor(R.color.on_accent);
        halo = c.getColor(R.color.accent_halo);
        grid.setStrokeWidth(1);
        grid.setColor(gridColor);
        stroke.setStyle(Paint.Style.STROKE);
        dashed.setStyle(Paint.Style.STROKE);
        dashed.setStrokeWidth(1.5f * dp);
        dashed.setColor(subtle);
        dashed.setPathEffect(new DashPathEffect(new float[] {3 * dp, 3 * dp}, 0));
        label.setTypeface(Typeface.create(Typeface.MONOSPACE, 700, false));
        label.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 10, getResources().getDisplayMetrics()));
        label.setTextAlign(Paint.Align.CENTER);
        label.setColor(onAccent);
        word.setTypeface(Typeface.create(Typeface.DEFAULT, 600, false));
        word.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 12, getResources().getDisplayMetrics()));
        word.setColor(bg);
    }

    void setHidden(boolean h, boolean animate) {
        float target = h ? 1 : 0;
        if (animator != null) animator.cancel();
        if (!animate || hidden < 0) {
            hidden = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(hidden, target).setDuration(260);
        animator.setInterpolator(new PathInterpolator(0.2f, 0, 0, 1));
        animator.addUpdateListener(a -> {
            hidden = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float step = 16 * dp;
        for (float x = step; x < w; x += step) canvas.drawLine(x, 0, x, h, grid);
        for (float y = step; y < h; y += step) canvas.drawLine(0, y, w, y, grid);

        // Bottom row, slightly below center to leave room for the marker.
        float barH = 40 * dp;
        float inset = Math.min(24 * dp, w * 0.05f);
        float top = (h - barH) / 2 + 12 * dp;
        float cy = top + barH / 2;

        // The Alexa widget sits at the right edge: logo ring, wordmark and mic in a dark pill.
        float wordW = word.measureText("Alexa");
        float widgetW = 34 * dp + wordW + 30 * dp;
        float wRight = w - inset;
        float wLeft = wRight - widgetW;

        // Floating dock with the app tiles, centered unless the widget is in the way.
        float tile = 20 * dp;
        int tiles = 3;
        float dockW = 2 * 14 * dp + tiles * tile + (tiles - 1) * 10 * dp;
        float dockLeft = Math.max(inset, Math.min(w / 2, wLeft - 16 * dp - dockW / 2) - dockW / 2);
        rect.set(dockLeft, top, dockLeft + dockW, top + barH);
        fill.setColor(bg);
        canvas.drawRoundRect(rect, 14 * dp, 14 * dp, fill);
        stroke.setStrokeWidth(1 * dp);
        stroke.setColor(lineStrong);
        stroke.setAlpha(255);
        canvas.drawRoundRect(rect, 14 * dp, 14 * dp, stroke);
        float x = dockLeft + 14 * dp;
        fill.setColor(line);
        for (int i = 0; i < tiles; i++) {
            rect.set(x, cy - tile / 2, x + tile, cy + tile / 2);
            canvas.drawRoundRect(rect, 5 * dp, 5 * dp, fill);
            x += tile + 10 * dp;
        }

        // The widget fades out and leaves a dashed outline where it was.
        float t = Math.max(0, hidden);
        rect.set(wLeft, top, wRight, top + barH);
        dashed.setAlpha(Math.round(255 * t));
        canvas.drawRoundRect(rect, barH / 2, barH / 2, dashed);
        if (t < 1) {
            int save = canvas.saveLayerAlpha(rect, Math.round(255 * (1 - t)));
            fill.setColor(text);
            canvas.drawRoundRect(rect, barH / 2, barH / 2, fill);
            stroke.setColor(bg);
            stroke.setStrokeWidth(2.5f * dp);
            float lx = wLeft + 20 * dp;
            canvas.drawCircle(lx, cy, 7 * dp, stroke);
            canvas.drawText("Alexa", wLeft + 34 * dp, cy - (word.descent() + word.ascent()) / 2, word);
            // Mic: capsule, cradle and stem.
            float mx = wRight - 17 * dp;
            stroke.setStrokeWidth(1.5f * dp);
            rect.set(mx - 2.5f * dp, cy - 8 * dp, mx + 2.5f * dp, cy + 1 * dp);
            canvas.drawRoundRect(rect, 2.5f * dp, 2.5f * dp, stroke);
            rect.set(mx - 5 * dp, cy - 6 * dp, mx + 5 * dp, cy + 4 * dp);
            canvas.drawArc(rect, 0, 180, false, stroke);
            canvas.drawLine(mx, cy + 4 * dp, mx, cy + 7 * dp, stroke);
            canvas.restoreToCount(save);
        }

        // Numbered marker, as in the figures on minnallab.com.
        float cx = (wLeft + wRight) / 2;
        float my = top - 22 * dp;
        stroke.setStrokeWidth(1 * dp);
        stroke.setColor(accent);
        stroke.setAlpha(255);
        canvas.drawLine(cx, my + 9 * dp, cx, top - 5 * dp, stroke);
        fill.setColor(halo);
        canvas.drawCircle(cx, my, 13 * dp, fill);
        fill.setColor(accent);
        canvas.drawCircle(cx, my, 9 * dp, fill);
        canvas.drawText("1", cx, my - (label.descent() + label.ascent()) / 2, label);
    }
}
