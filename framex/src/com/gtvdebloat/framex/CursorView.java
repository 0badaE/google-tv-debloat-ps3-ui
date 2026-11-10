package com.gtvdebloat.framex;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * Pointer mode: an on-screen arrow the D-pad steers, for the embedded video players the focus
 * ring can't reach (they're another site's page inside an iframe). OK taps under the arrow.
 * Holding a direction speeds the arrow up; pushing against an edge scrolls the page.
 */
final class CursorView extends View {

    interface Host {
        /** The view to send taps and hovers to: the page, or the fullscreen video. */
        View target();
        /** Scroll whatever is under (x, y) by (dx, dy), all in view pixels. */
        void scrollAt(float x, float y, float dx, float dy);
    }

    private static final float START_SPEED_DP = 2.5f;  // per frame, first moment of a press
    private static final float MAX_SPEED_DP = 16f;     // per frame, after ACCEL_MS of holding
    private static final float ACCEL_MS = 1100f;

    private final Host host;
    private final float dp;
    private final Path arrow = new Path();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean active, left, right, up, down, moving;
    private float x, y;
    private long heldSince;

    private final Runnable tick = this::step;

    CursorView(Context context, Host host) {
        super(context);
        this.host = host;
        dp = getResources().getDisplayMetrics().density;
        float s = dp * 1.4f;
        arrow.moveTo(0, 0);
        arrow.lineTo(0, 17 * s);
        arrow.lineTo(4.5f * s, 13 * s);
        arrow.lineTo(7.5f * s, 20 * s);
        arrow.lineTo(10.5f * s, 18.8f * s);
        arrow.lineTo(7.5f * s, 12 * s);
        arrow.lineTo(13 * s, 12 * s);
        arrow.close();
        fill.setColor(0xFFFFFFFF);
        edge.setColor(0xFF000000);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1.5f * dp);
        edge.setStrokeJoin(Paint.Join.ROUND);
        setWillNotDraw(false);
    }

    boolean isActive() { return active; }
    float pointerX() { return x; }
    float pointerY() { return y; }

    void show(float atX, float atY) {
        active = true;
        x = clamp(atX, 0, getWidth() - 1);
        y = clamp(atY, 0, getHeight() - 1);
        hover();
        invalidate();
    }

    void hide() {
        active = false;
        release();
        invalidate();
    }

    /** Forget held directions, e.g. when a key-up may have gone to another window. */
    void release() {
        left = right = up = down = false;
    }

    void key(int keyCode, boolean pressed) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: left = pressed; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: right = pressed; break;
            case KeyEvent.KEYCODE_DPAD_UP: up = pressed; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: down = pressed; break;
            default: return;
        }
        if (pressed && !moving) {
            moving = true;
            heldSince = SystemClock.uptimeMillis();
            step();
        }
    }

    /** A finger tap under the arrow. */
    void tap() {
        tapAt(x, y);
    }

    /** A finger tap anywhere, arrow or not: a real press, which pages trust more than script. */
    void tapAt(final float tx, final float ty) {
        final View target = host.target();
        final long t = SystemClock.uptimeMillis();
        send(target, MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, tx, ty, 0));
        postDelayed(() -> send(target, MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, tx, ty, 0)), 60);
    }

    private void step() {
        int dx = (right ? 1 : 0) - (left ? 1 : 0);
        int dy = (down ? 1 : 0) - (up ? 1 : 0);
        if (!active || (dx == 0 && dy == 0)) { moving = false; return; }

        float held = Math.min(1f, (SystemClock.uptimeMillis() - heldSince) / ACCEL_MS);
        float speed = dp * (START_SPEED_DP + (MAX_SPEED_DP - START_SPEED_DP) * held * held);
        float nx = clamp(x + dx * speed, 0, getWidth() - 1);
        float ny = clamp(y + dy * speed, 0, getHeight() - 1);
        float pushX = dx != 0 && nx == x ? dx * speed * 1.5f : 0;
        float pushY = dy != 0 && ny == y ? dy * speed * 1.5f : 0;
        if (pushX != 0 || pushY != 0) host.scrollAt(x, y, pushX, pushY);

        x = nx;
        y = ny;
        hover();
        invalidate();
        postOnAnimation(tick);
    }

    private void hover() {
        long t = SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(t, t, MotionEvent.ACTION_HOVER_MOVE, x, y, 0);
        e.setSource(InputDevice.SOURCE_MOUSE);
        host.target().dispatchGenericMotionEvent(e);
        e.recycle();
    }

    private static void send(View target, MotionEvent e) {
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        target.dispatchTouchEvent(e);
        e.recycle();
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!active) return;
        canvas.save();
        canvas.translate(x, y);
        canvas.drawPath(arrow, fill);
        canvas.drawPath(arrow, edge);
        canvas.restore();
    }
}
