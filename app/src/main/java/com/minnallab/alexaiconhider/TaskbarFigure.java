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
 * Alexa button is drawn as a ring when visible and as a dashed outline when hidden.
 */
public final class TaskbarFigure extends View {
    private final float dp;
    private final Paint grid = new Paint();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
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

        // Taskbar, slightly below center to leave room for the marker.
        float barH = 44 * dp;
        float inset = Math.min(32 * dp, w * 0.06f);
        float top = (h - barH) / 2 + 12 * dp;
        rect.set(inset, top, w - inset, top + barH);
        fill.setColor(bg);
        canvas.drawRoundRect(rect, barH / 2, barH / 2, fill);
        stroke.setStrokeWidth(1 * dp);
        stroke.setColor(lineStrong);
        canvas.drawRoundRect(rect, barH / 2, barH / 2, stroke);

        // App tiles.
        float cy = top + barH / 2;
        float tile = 20 * dp;
        float x = rect.left + 18 * dp;
        fill.setColor(line);
        for (int i = 0; i < 4; i++) {
            rect.set(x, cy - tile / 2, x + tile, cy + tile / 2);
            canvas.drawRoundRect(rect, 5 * dp, 5 * dp, fill);
            x += tile + 10 * dp;
        }

        // System area: clock and status dots.
        float right = w - inset - 18 * dp;
        for (int i = 0; i < 3; i++) {
            canvas.drawCircle(right - 3 * dp, cy, 3 * dp, fill);
            right -= 14 * dp;
        }
        rect.set(right - 30 * dp, cy - 3 * dp, right, cy + 3 * dp);
        canvas.drawRoundRect(rect, 3 * dp, 3 * dp, fill);

        // The Alexa button: a ring that fades into a dashed outline.
        float r = 10 * dp;
        float cx = x + 14 * dp + r;
        float t = Math.max(0, hidden);
        stroke.setStrokeWidth(3 * dp);
        stroke.setColor(text);
        stroke.setAlpha(Math.round(255 * (1 - t)));
        canvas.drawCircle(cx, cy, r - (1.5f * dp) * t, stroke);
        dashed.setAlpha(Math.round(255 * t));
        canvas.drawCircle(cx, cy, r + 2 * dp, dashed);

        // Numbered marker, as in the figures on minnallab.com.
        float my = top - 22 * dp;
        stroke.setStrokeWidth(1 * dp);
        stroke.setColor(accent);
        stroke.setAlpha(255);
        canvas.drawLine(cx, my + 9 * dp, cx, cy - r - 5 * dp, stroke);
        fill.setColor(halo);
        canvas.drawCircle(cx, my, 13 * dp, fill);
        fill.setColor(accent);
        canvas.drawCircle(cx, my, 9 * dp, fill);
        canvas.drawText("1", cx, my - (label.descent() + label.ascent()) / 2, label);
    }
}
