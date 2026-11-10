package com.gtvdebloat.crossbar;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings → Wave Controls: every wallpaper setting as a slider, like the web version's
 * Spline and Particles panels. Up/Down picks a row, Left/Right moves it (hold to go faster),
 * OK resets that row, Back closes. Changes show on the wave immediately and are saved.
 */
final class WaveControls {
    private static final float W = 1920f, PANEL = 700, TOP = 170, BOTTOM = 1050;
    private static final float ROW = 74, HEADER = 58;

    /** A list row: a section title, a setting, or the "reset everything" action. */
    private static final class Row {
        final String header;
        final WaveSettings.Param param;
        final boolean resetAll;
        float y;

        Row(String header, WaveSettings.Param param, boolean resetAll) {
            this.header = header;
            this.param = param;
            this.resetAll = resetAll;
        }

        boolean selectable() { return header == null; }

        float height() { return header != null ? HEADER : ROW; }
    }

    private final WaveSettings settings;
    private final Sounds sounds;
    private final Runnable onBackgroundEdit;
    private final List<Row> rows = new ArrayList<>();
    private int sel;
    private final XmbView.Anim scroll = new XmbView.Anim(0);
    private final XmbView.Anim selY = new XmbView.Anim(0);
    private boolean closing;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    /**
     * @param onBackgroundEdit runs when a background setting changes, so the home screen can switch
     *     to the Custom theme (the only one that shows these settings).
     */
    WaveControls(WaveSettings settings, Sounds sounds, Runnable onBackgroundEdit) {
        this.settings = settings;
        this.sounds = sounds;
        this.onBackgroundEdit = onBackgroundEdit;
        String section = null;
        for (WaveSettings.Param p : settings.all()) {
            if (!p.section.equals(section)) {
                section = p.section;
                rows.add(new Row(section, null, false));
            }
            rows.add(new Row(null, p, false));
        }
        rows.add(new Row("", null, false));
        rows.add(new Row(null, null, true));
        float y = 0;
        for (Row row : rows) {
            row.y = y;
            y += row.height();
        }
        sel = 1;
        selY.jump(rows.get(sel).y);
        scroll.jump(targetScroll());

        Typeface light = Typeface.create(Typeface.SANS_SERIF, 300, false);
        Typeface regular = Typeface.create(Typeface.SANS_SERIF, 400, false);
        label.setTypeface(light);
        label.setColor(0xFFFFFFFF);
        label.setTextSize(27);
        value.setTypeface(regular);
        value.setColor(0xFFFFFFFF);
        value.setTextSize(23);
        value.setTextAlign(Paint.Align.RIGHT);
        small.setTypeface(regular);
        small.setColor(0xFFFFFFFF);
        small.setTextSize(19);
        small.setLetterSpacing(0.18f);
    }

    boolean closing() { return closing; }

    /** Handles a key; every key is consumed while the panel is open. */
    boolean key(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        long now = SystemClock.uptimeMillis();
        Row row = rows.get(sel);
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                int d = e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                int n = sel + d;
                while (n >= 0 && n < rows.size() && !rows.get(n).selectable()) n += d;
                if (n >= 0 && n < rows.size()) {
                    sel = n;
                    selY.set(rows.get(n).y, now, 140);
                    scroll.set(targetScroll(), now, 200);
                    if (e.getRepeatCount() % 3 == 0) sounds.play(Sounds.TICK);
                }
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                if (row.param == null) return true;
                int d = e.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                WaveSettings.Param p = row.param;
                if (p.options != null) {
                    int n = Math.round(p.v) + d;
                    if (n < 0) n = p.options.length - 1;
                    if (n >= p.options.length) n = 0;
                    settings.set(p, n);
                } else {
                    // Holding the button speeds up, so a 0..255 or 10..800 range doesn't take forever.
                    int rep = e.getRepeatCount();
                    float mul = rep > 30 ? 25 : rep > 12 ? 5 : 1;
                    float range = (p.max - p.min) / p.step;
                    if (range > 2000 && rep > 30) mul = range / 40;
                    settings.set(p, p.v + d * p.step * mul);
                }
                if (settings.isBackground(p)) onBackgroundEdit.run();
                if (e.getRepeatCount() == 0) sounds.play(Sounds.TOCK);
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (row.resetAll) {
                    settings.resetAll();
                } else if (row.param != null) {
                    settings.set(row.param, row.param.def);
                    if (settings.isBackground(row.param)) onBackgroundEdit.run();
                }
                sounds.play(Sounds.OK);
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BUTTON_B:
                closing = true;
                sounds.play(Sounds.BACK);
                return true;
            default:
                return true;
        }
    }

    /** Keeps the selected row a little above the middle of the list. */
    private float targetScroll() {
        float total = rows.get(rows.size() - 1).y + ROW;
        float view = BOTTOM - TOP;
        float t = rows.get(sel).y - view * 0.4f;
        return Math.max(0, Math.min(Math.max(0, total - view), t));
    }

    boolean animating(long now) {
        return scroll.running(now) || selY.running(now);
    }

    void draw(Canvas c, float a, long now) {
        int alpha = (int) (255 * a);
        float left = W - PANEL * a;
        fill.setShader(new LinearGradient(left - 160, 0, left + 40, 0, 0x00060610, 0xC8060610, Shader.TileMode.CLAMP));
        fill.setAlpha(255);
        c.drawRect(left - 160, 0, W, 1080, fill);
        fill.setShader(null);

        float x = left + 70, right = W - 70;
        small.setAlpha((int) (alpha * 0.85f));
        c.drawText("WAVE CONTROLS", x, 96, small);
        small.setLetterSpacing(0);
        small.setAlpha((int) (alpha * 0.5f));
        c.drawText("◀ ▶ adjust (hold to speed up)  ·  OK reset row  ·  Back done", x, 130, small);
        small.setLetterSpacing(0.18f);

        float off = TOP - scroll.value(now);
        c.save();
        c.clipRect(left, TOP - 10, W, BOTTOM);

        // Selection highlight, gliding between rows.
        float hy = off + selY.value(now);
        r.set(x - 30, hy + 4, right + 20, hy + ROW - 4);
        fill.setColor(0xFFFFFFFF);
        fill.setAlpha((int) (36 * a));
        c.drawRoundRect(r, 14, 14, fill);
        fill.setAlpha((int) (230 * a));
        c.drawRoundRect(x - 30, hy + 22, x - 26, hy + ROW - 22, 2, 2, fill);

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float y = off + row.y;
            if (y + row.height() < TOP - 10 || y > BOTTOM) continue;
            boolean on = i == sel;
            if (row.header != null) {
                if (row.header.isEmpty()) continue;
                small.setAlpha((int) (alpha * 0.6f));
                c.drawText(row.header.toUpperCase(java.util.Locale.ROOT), x, y + 40, small);
                continue;
            }
            if (row.resetAll) {
                label.setAlpha(on ? alpha : (int) (alpha * 0.7f));
                c.drawText("Reset everything to defaults", x, y + 46, label);
                continue;
            }
            WaveSettings.Param p = row.param;
            label.setAlpha(on ? alpha : (int) (alpha * 0.72f));
            label.setTextSize(on ? 27 : 25);
            c.drawText(p.label, x, y + 32, label);
            value.setAlpha(on ? alpha : (int) (alpha * 0.6f));
            boolean changed = Math.abs(p.v - p.def) > p.step * 0.5f;
            String text = p.options != null ? "‹  " + p.display() + "  ›" : p.display() + (changed ? "  •" : "");
            c.drawText(text, right, y + 32, value);
            if (p.options == null) drawSlider(c, p, x, right, y + 54, on, alpha);
        }
        c.restore();
        label.setTextSize(27);
    }

    private void drawSlider(Canvas c, WaveSettings.Param p, float x1, float x2, float y, boolean on, int alpha) {
        float f = (p.v - p.min) / (p.max - p.min);
        float kx = x1 + (x2 - x1) * f;
        fill.setColor(0xFFFFFFFF);
        fill.setAlpha((int) (alpha * 0.18f));
        c.drawRoundRect(x1, y - 2, x2, y + 2, 2, 2, fill);
        fill.setAlpha((int) (alpha * (on ? 0.9f : 0.5f)));
        c.drawRoundRect(x1, y - 2, kx, y + 2, 2, 2, fill);
        // Default position, as a faint tick.
        float dx = x1 + (x2 - x1) * (p.def - p.min) / (p.max - p.min);
        fill.setAlpha((int) (alpha * 0.35f));
        c.drawRect(dx - 1, y - 7, dx + 1, y + 7, fill);
        fill.setAlpha(on ? alpha : (int) (alpha * 0.6f));
        c.drawCircle(kx, y, on ? 10 : 6, fill);
    }
}
