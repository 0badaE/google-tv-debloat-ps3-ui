package com.gtvdebloat.crossbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * The cross-shaped menu: categories across the top, the selected category's items in a column
 * underneath. Everything is laid out in a 1920x1080 design space and scaled to the view.
 * Every move animates for 1/6 s with an ease-out, like the PS3.
 */
final class XmbView extends View {
    interface Host {
        void onActivate(Catalog.Item item);
        void onPanelPreview(String panel, int option);
        void onPanelCommit(String panel, int option);
        void onPanelCancel(String panel);
        void onSelectionChanged(String categoryId, String itemKey);
        Sounds sounds();
    }

    static final class Panel {
        final String id;
        final String title;
        final String[] options;
        final int[] dots;
        int saved;
        int sel;

        Panel(String id, String title, String[] options, int[] dots, int saved) {
            this.id = id;
            this.title = title;
            this.options = options;
            this.dots = dots;
            this.saved = saved;
            this.sel = saved;
        }
    }

    private static final int MOVE_MS = 166;
    private static final float DESIGN_W = 1920f;

    private static final float CAT_X = 560, CAT_Y = 300, CAT_SP = 232, CAT_SIZE = 118;
    private static final float SEL_Y = 520, BELOW_GAP = 158, ITEM_SP = 108, ABOVE_Y = 140;
    private static final float H_SMALL = 70, H_BIG = 122;

    private final Host host;
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint catLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Bitmap glow;
    private final Path check = new Path();

    private List<Catalog.Category> cats = new ArrayList<>();
    private int ci;
    private final Anim catAnim = new Anim(0);
    private final List<Anim> selAnims = new ArrayList<>();
    private final List<Anim> colAlpha = new ArrayList<>();
    private final List<Integer> sel = new ArrayList<>();
    private final Anim intro = new Anim(1);
    private final Anim panelAnim = new Anim(0);
    private Panel panel;
    private WaveControls controls;
    private final Anim controlsAnim = new Anim(0);
    private Panel closingPanel;
    private String clock = "";

    XmbView(Context context, Host host) {
        super(context);
        this.host = host;
        setFocusable(true);
        setFocusableInTouchMode(true);

        Typeface light = Typeface.create(Typeface.SANS_SERIF, 300, false);
        Typeface regular = Typeface.create(Typeface.SANS_SERIF, 400, false);
        title.setTypeface(light);
        title.setColor(0xFFFFFFFF);
        title.setTextSize(38);
        title.setShadowLayer(8, 0, 2, 0x8C000000);
        sub.setTypeface(regular);
        sub.setColor(0xFFFFFFFF);
        sub.setTextSize(23);
        sub.setShadowLayer(6, 0, 2, 0x8C000000);
        catLabel.set(sub);
        catLabel.setTextSize(25);
        catLabel.setTextAlign(Paint.Align.CENTER);
        clockPaint.set(sub);
        clockPaint.setTextSize(25);
        clockPaint.setTextAlign(Paint.Align.RIGHT);
        clockPaint.setShadowLayer(4, 0, 1, 0x66000000);
        border.setStyle(Paint.Style.STROKE);
        border.setColor(0xFFFFFFFF);
        border.setStrokeWidth(3);

        glow = makeGlow();
        check.moveTo(-9, 0);
        check.lineTo(-3, 7);
        check.lineTo(10, -8);
    }

    /** A soft white halo baked once and drawn behind the selected banner. */
    private static Bitmap makeGlow() {
        int w = 320, h = 200, pad = 32;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        p.setMaskFilter(new BlurMaskFilter(18, BlurMaskFilter.Blur.NORMAL));
        new Canvas(b).drawRoundRect(new RectF(pad, pad, w - pad, h - pad), 18, 18, p);
        return b;
    }

    // --- state -------------------------------------------------------------------------------

    void setCategories(List<Catalog.Category> list, String catId, java.util.Map<String, String> selectedKeys) {
        String keepCat = cats.isEmpty() ? catId : cats.get(ci).id;
        cats = list;
        selAnims.clear();
        colAlpha.clear();
        sel.clear();
        ci = 0;
        for (int i = 0; i < list.size(); i++) {
            Catalog.Category c = list.get(i);
            if (c.id.equals(keepCat)) ci = i;
            String key = selectedKeys.get(c.id);
            int s = key == null ? 0 : Math.max(0, c.indexOf(key));
            sel.add(s);
            selAnims.add(new Anim(s));
            colAlpha.add(new Anim(0));
        }
        if (!list.isEmpty()) colAlpha.get(ci).jump(1);
        catAnim.jump(ci);
        invalidate();
    }

    private Catalog.Item current() {
        if (cats.isEmpty()) return null;
        List<Catalog.Item> items = cats.get(ci).items;
        return items.isEmpty() ? null : items.get(sel.get(ci));
    }

    void setClock(String text) {
        clock = text;
        invalidate();
    }

    void playIntro() {
        intro.jump(0);
        intro.set(1, now(), 1300);
        invalidate();
    }

    /** Home pressed while already home: close any panel and go back to the top of the column. */
    void goHome() {
        if (panel != null) cancelPanel();
        if (controls != null) controlsAnim.set(0, now(), MOVE_MS + 60);
        if (!cats.isEmpty() && sel.get(ci) != 0) moveItem(-sel.get(ci));
    }

    /** Wave Controls: the menu fades away so the whole wave is visible while you tune it. */
    void openControls(WaveControls c) {
        controls = c;
        controlsAnim.set(1, now(), MOVE_MS + 120);
        invalidate();
    }

    void openPanel(Panel p) {
        panel = p;
        panelAnim.set(1, now(), MOVE_MS + 60);
        invalidate();
    }

    private void closePanel() {
        closingPanel = panel;
        panel = null;
        panelAnim.set(0, now(), MOVE_MS + 60);
        invalidate();
    }

    private void cancelPanel() {
        Panel p = panel;
        closePanel();
        host.onPanelCancel(p.id);
    }

    // --- input -------------------------------------------------------------------------------

    boolean handleKey(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN || cats.isEmpty()) return false;
        if (controls != null && controlsAnim.to == 1) {
            controls.key(e);
            if (controls.closing()) controlsAnim.set(0, now(), MOVE_MS + 60);
            invalidate();
            return true;
        }
        int code = e.getKeyCode();
        Sounds s = host.sounds();
        if (panel != null) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN: {
                    int d = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                    int n = panel.sel + d;
                    if (n >= 0 && n < panel.options.length) {
                        panel.sel = n;
                        s.play(Sounds.TICK);
                        host.onPanelPreview(panel.id, n);
                        invalidate();
                    }
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_BUTTON_A: {
                    Panel p = panel;
                    p.saved = p.sel;
                    host.onPanelCommit(p.id, p.sel);
                    s.play(Sounds.OK);
                    closePanel();
                    return true;
                }
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                case KeyEvent.KEYCODE_BUTTON_B:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    s.play(Sounds.BACK);
                    cancelPanel();
                    return true;
                default:
                    return code == KeyEvent.KEYCODE_DPAD_RIGHT;
            }
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (ci > 0) {
                    moveCat(-1);
                    s.play(Sounds.TOCK);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (ci < cats.size() - 1) {
                    moveCat(1);
                    s.play(Sounds.TOCK);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (sel.get(ci) > 0) {
                    moveItem(-1);
                    s.play(Sounds.TICK);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (sel.get(ci) < cats.get(ci).items.size() - 1) {
                    moveItem(1);
                    s.play(Sounds.TICK);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A: {
                List<Catalog.Item> items = cats.get(ci).items;
                if (items.isEmpty()) return true;
                s.play(Sounds.OK);
                host.onActivate(items.get(sel.get(ci)));
                return true;
            }
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BUTTON_B:
                if (sel.get(ci) > 0) {
                    s.play(Sounds.BACK);
                    moveItem(-sel.get(ci));
                }
                return true;
            default:
                return false;
        }
    }

    private void moveCat(int d) {
        long now = now();
        colAlpha.get(ci).set(0, now, MOVE_MS);
        ci += d;
        colAlpha.get(ci).set(1, now, MOVE_MS);
        catAnim.set(ci, now, MOVE_MS);
        notifySelection();
        invalidate();
    }

    private void moveItem(int d) {
        int n = sel.get(ci) + d;
        sel.set(ci, n);
        selAnims.get(ci).set(n, now(), MOVE_MS);
        notifySelection();
        invalidate();
    }

    private void notifySelection() {
        Catalog.Item item = current();
        host.onSelectionChanged(cats.get(ci).id, item == null ? null : item.key);
    }

    // --- drawing -----------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        long now = now();
        float scale = getWidth() / DESIGN_W;
        canvas.save();
        canvas.scale(scale, scale);

        float in = intro.value(now);
        float ca = controlsAnim.value(now);
        if (controls != null && controlsAnim.to == 0 && ca < 0.001f) controls = null;
        float ui = smooth(in) * (1 - smooth(ca));
        float pa = panelAnim.value(now);
        float dim = 1f - 0.65f * pa;
        float rowShift = (1 - smooth(in)) * 90;

        // Text shadows ignore alpha, so a fully faded menu still leaves ghosts unless it's skipped.
        if (ui < 0.01f) {
            if (controls != null && ca > 0.001f) controls.draw(canvas, smooth(ca), now);
            canvas.restore();
            if (isAnimating(now)) postInvalidateOnAnimation();
            return;
        }

        drawClock(canvas, ui);

        float cat = catAnim.value(now);
        for (int i = 0; i < cats.size(); i++) {
            float a = colAlpha.get(i).value(now);
            if (a < 0.01f) continue;
            float x = CAT_X + (i - cat) * CAT_SP + rowShift;
            drawColumn(canvas, cats.get(i), selAnims.get(i).value(now), sel.get(i), x, a * ui * dim, now);
        }

        for (int i = 0; i < cats.size(); i++) {
            float dx = i - cat;
            float x = CAT_X + dx * CAT_SP + rowShift;
            if (x < -200 || x > DESIGN_W + 200) continue;
            float f = clamp01(1 - Math.abs(dx));
            float size = CAT_SIZE * lerp(0.66f, 1f, f);
            float alpha = lerp(0.62f, 1f, f) * ui * lerp(dim, 1f, 0.4f);
            if (x > DESIGN_W - 720) alpha *= 1 - pa; // hidden behind the side panel
            drawGlyph(canvas, cats.get(i).glyph, x, CAT_Y, size, alpha);
            if (f > 0.01f) {
                catLabel.setAlpha((int) (255 * f * ui * dim));
                canvas.drawText(cats.get(i).label, x, CAT_Y + size / 2 + 40, catLabel);
            }
        }

        if (pa > 0.001f) drawPanel(canvas, panel != null ? panel : closingPanel, pa);
        if (controls != null && ca > 0.001f) controls.draw(canvas, smooth(ca), now);

        canvas.restore();
        if (isAnimating(now) || colAnimating(now)) postInvalidateOnAnimation();
        else if (!cats.isEmpty()) postInvalidateDelayed(50); // selected-item pulse
    }

    private void drawColumn(Canvas c, Catalog.Category cat, float selPos, int selIdx, float x, float alpha, long now) {
        float pulse = 0.72f + 0.28f * (float) Math.sin(now / 1000.0 * 2 * Math.PI / 2.6);
        // Titles line up after the widest image in the column: a banner, or just the icon.
        boolean banners = false;
        for (Catalog.Item it : cat.items) banners |= !it.glyph;
        for (int j = 0; j < cat.items.size(); j++) {
            float d = j - selPos;
            if (d < -3 || d > 6) continue;
            float y = itemY(d);
            float f = clamp01(1 - Math.abs(d));
            float a = alpha * (d < -0.5f ? lerp(0.55f, 0.42f, clamp01(-d - 0.5f)) : lerp(0.78f, 1f, f));
            drawItem(c, cat.items.get(j), x, y, f, a, j == selIdx ? pulse : 0, banners);
        }
    }

    /** Vertical center of an item that is {@code d} places from the selected one. */
    private static float itemY(float d) {
        if (d >= 1) return SEL_Y + BELOW_GAP + (d - 1) * ITEM_SP;
        if (d >= 0) return lerp(SEL_Y, SEL_Y + BELOW_GAP, d);
        if (d > -1) return lerp(SEL_Y, ABOVE_Y, -d);
        return ABOVE_Y - (-d - 1) * ITEM_SP;
    }

    private void drawItem(Canvas c, Catalog.Item it, float cx, float cy, float f, float alpha, float pulse,
                          boolean bannerColumn) {
        float h = lerp(H_SMALL, H_BIG, f);
        float w = h * 16f / 9f;
        if (it.glyph) {
            drawGlyph(c, it.image, cx, cy, h * 0.82f, alpha);
        } else {
            if (f > 0.01f && pulse > 0) {
                float gw = w * 320f / 256f, gh = h * 200f / 136f;
                r.set(cx - gw / 2, cy - gh / 2, cx + gw / 2, cy + gh / 2);
                bmp.setAlpha((int) (255 * alpha * f * 0.38f * pulse));
                c.drawBitmap(glow, null, r, bmp);
            }
            r.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
            bmp.setAlpha((int) (255 * alpha));
            c.drawBitmap(it.image, null, r, bmp);
            if (f > 0.01f) {
                border.setAlpha((int) (255 * alpha * f * (0.55f + 0.35f * pulse)));
                float rad = 14f * h / Catalog.BANNER_H;
                c.drawRoundRect(r, rad, rad, border);
            }
        }

        float tx = bannerColumn ? cx + w / 2 + 34 : cx + h * 0.41f + 40;
        float ts = lerp(27, 38, f) / 38f;
        boolean hasSub = !it.subtitle.isEmpty() && f > 0.01f;
        float baseline = hasSub ? cy - 4 * f + 13 * (1 - f) : cy + 13;
        title.setAlpha((int) (255 * alpha));
        c.save();
        c.translate(tx, baseline);
        c.scale(ts, ts);
        c.drawText(it.title, 0, 0, title);
        c.restore();
        if (hasSub) {
            sub.setAlpha((int) (255 * alpha * f * 0.78f));
            c.drawText(it.subtitle, tx, cy + 32, sub);
        }
    }

    private void drawGlyph(Canvas c, Bitmap g, float cx, float cy, float size, float alpha) {
        float full = size * (1 + 2 * Glyphs.PAD);
        r.set(cx - full / 2, cy - full / 2, cx + full / 2, cy + full / 2);
        bmp.setAlpha((int) (255 * clamp01(alpha)));
        c.drawBitmap(g, null, r, bmp);
    }

    private void drawClock(Canvas c, float a) {
        if (clock.isEmpty()) return;
        float right = DESIGN_W - 80, top = 58, h = 50;
        float w = clockPaint.measureText(clock) + 64;
        r.set(right - w, top, right + 26, top + h);
        fill.setShader(new LinearGradient(r.left, 0, r.right, 0, 0x00FFFFFF, 0x2EFFFFFF, Shader.TileMode.CLAMP));
        fill.setAlpha((int) (255 * a));
        c.drawRoundRect(r, h / 2, h / 2, fill);
        fill.setShader(null);
        clockPaint.setAlpha((int) (255 * a));
        c.drawText(clock, right - 8, top + h / 2 + 9, clockPaint);
    }

    private void drawPanel(Canvas c, Panel p, float a) {
        if (p == null) return;
        float pw = 600;
        float left = DESIGN_W - pw * smooth(a);
        fill.setShader(new LinearGradient(left, 0, left + 140, 0, 0x00060610, 0xB3060610, Shader.TileMode.CLAMP));
        fill.setAlpha(255);
        c.drawRect(left, 0, DESIGN_W, 1080, fill);
        fill.setShader(null);

        float x = left + 120;
        sub.setAlpha((int) (200 * a));
        c.drawText(p.title.toUpperCase(java.util.Locale.ROOT), x, 250, letterSpaced(sub));
        sub.setLetterSpacing(0);

        float rowH = 78, y0 = 330;
        for (int i = 0; i < p.options.length; i++) {
            float y = y0 + i * rowH;
            boolean on = i == p.sel;
            if (on) {
                r.set(x - 40, y - rowH / 2 + 6, DESIGN_W - 60, y + rowH / 2 - 6);
                fill.setColor(0xFFFFFFFF);
                fill.setAlpha((int) (40 * a));
                c.drawRoundRect(r, 14, 14, fill);
                fill.setAlpha((int) (230 * a));
                c.drawRoundRect(x - 40, y - 16, x - 36, y + 16, 2, 2, fill);
            }
            float tx = x;
            if (p.dots != null) {
                fill.setColor(p.dots[i]);
                fill.setAlpha((int) (255 * a));
                c.drawCircle(x + 10, y, 11, fill);
                border.setStrokeWidth(2);
                border.setAlpha((int) (140 * a));
                c.drawCircle(x + 10, y, 11, border);
                border.setStrokeWidth(3);
                tx = x + 42;
            }
            title.setAlpha((int) ((on ? 255 : 170) * a));
            c.save();
            c.translate(tx, y + 11);
            c.scale(30f / 38f, 30f / 38f);
            c.drawText(p.options[i], 0, 0, title);
            c.restore();
            if (i == p.saved) {
                c.save();
                c.translate(DESIGN_W - 110, y);
                border.setAlpha((int) (220 * a));
                c.drawPath(check, border);
                c.restore();
            }
        }
    }

    private static Paint letterSpaced(Paint p) {
        p.setLetterSpacing(0.18f);
        return p;
    }

    private boolean isAnimating(long now) {
        return catAnim.running(now) || intro.running(now) || panelAnim.running(now) || controlsAnim.running(now)
                || (controls != null && controls.animating(now));
    }

    private boolean colAnimating(long now) {
        for (int i = 0; i < cats.size(); i++) {
            if (selAnims.get(i).running(now) || colAlpha.get(i).running(now)) return true;
        }
        return false;
    }

    private static long now() { return SystemClock.uptimeMillis(); }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static float clamp01(float v) { return Math.max(0, Math.min(1, v)); }

    private static float smooth(float t) { return t * t * (3 - 2 * t); }

    /** A value that eases out towards its target over a fixed time. */
    static final class Anim {
        float from, to;
        long start;
        int dur;

        Anim(float v) {
            from = to = v;
        }

        void set(float target, long now, int durMs) {
            from = value(now);
            to = target;
            start = now;
            dur = durMs;
        }

        void jump(float v) {
            from = to = v;
            dur = 0;
        }

        float value(long now) {
            if (dur <= 0) return to;
            float t = (now - start) / (float) dur;
            if (t >= 1) return to;
            float e = 1 - (1 - t) * (1 - t) * (1 - t);
            return from + (to - from) * e;
        }

        boolean running(long now) {
            return dur > 0 && now - start < dur;
        }
    }
}
