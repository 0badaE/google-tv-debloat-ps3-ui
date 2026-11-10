package com.gtvdebloat.crossbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.session.MediaController;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.view.View;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * The screensaver's overlay, laid out like Google TV's Ambient mode: device name and Wi-Fi top
 * right, now playing bottom left, weather and time bottom right. It drifts a few pixels over time
 * so nothing sits still long enough to mark an OLED panel.
 */
final class AmbientView extends View {
    private static final float W = 1920f, M = 96;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Map<Integer, Bitmap> glyphs = new HashMap<>();
    private final Bitmap wifi;

    private String device = "", network = "", time = "";
    private Weather weather;
    private String songTitle = "", songArtist = "";
    private Bitmap art, roundedArt;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            invalidate();
            handler.postDelayed(this, 2000);
        }
    };

    AmbientView(Context c) {
        super(c);
        text.setTypeface(Typeface.create(Typeface.SANS_SERIF, 400, false));
        text.setColor(0xFFFFFFFF);
        text.setShadowLayer(10, 0, 2, 0x99000000);
        big.setTypeface(Typeface.create(Typeface.SANS_SERIF, 200, false));
        big.setColor(0xFFFFFFFF);
        big.setTextSize(170);
        big.setTextAlign(Paint.Align.RIGHT);
        big.setShadowLayer(16, 0, 3, 0x99000000);
        wifi = Glyphs.render(Glyphs.WIFI, 64);
    }

    void start() {
        device = DeviceStatus.deviceName(getContext());
        weather = Weather.cached(getContext());
        Weather.refresh(getContext(), () -> post(() -> {
            weather = Weather.cached(getContext());
            invalidate();
        }));
        handler.removeCallbacks(tick);
        tick.run();
    }

    void stop() {
        handler.removeCallbacks(tick);
    }

    private void update() {
        Context c = getContext();
        network = DeviceStatus.network(c);
        // Just the digits, as Google's screensaver does; "p.m." at this size is all you'd see.
        time = DateFormat.format(DateFormat.is24HourFormat(c) ? "H:mm" : "h:mm", Calendar.getInstance()).toString();
        MediaController mc = NotifListener.nowPlaying(c);
        if (mc != null && NotifListener.isPlaying(mc)) {
            songTitle = NotifListener.title(mc);
            songArtist = NotifListener.artist(mc);
            Bitmap a = NotifListener.art(mc);
            if (a != art) {
                art = a;
                roundedArt = a == null ? null : rounded(a, 180, 18);
            }
        } else {
            songTitle = "";
            art = roundedArt = null;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = getWidth() / W;
        c.save();
        c.scale(s, s);
        // Slow drift against burn-in.
        double t = SystemClock.uptimeMillis() / 1000.0;
        c.translate((float) (Math.sin(t / 97) * 14), (float) (Math.cos(t / 131) * 10));

        // Top right: device name, then Wi-Fi.
        text.setTextSize(26);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setAlpha(225);
        float x = W - M, y = M + 20;
        c.drawText(network, x, y, text);
        x -= text.measureText(network) + 12;
        drawBitmap(c, wifi, x - 30, y - 24, 30);
        x -= 30 + 26;
        text.setAlpha(150);
        c.drawText("·", x, y, text);
        x -= text.measureText("·") + 26;
        text.setAlpha(225);
        c.drawText(device, x, y, text);

        // Bottom right: weather, then the time.
        float base = 1080 - M;
        c.drawText(time, W - M, base, big);
        if (weather != null) {
            text.setTextSize(34);
            text.setAlpha(240);
            String w = weather.temperature() + "   " + weather.condition();
            float wy = base - 170;
            c.drawText(w, W - M, wy, text);
            Bitmap g = glyphs.get(weather.glyph());
            if (g == null) {
                g = Glyphs.render(weather.glyph(), 96);
                glyphs.put(weather.glyph(), g);
            }
            drawBitmap(c, g, W - M - text.measureText(w) - 16 - 52, wy - 40, 52);
        }

        // Bottom left: now playing.
        if (!songTitle.isEmpty()) {
            float size = 150, top = base - size;
            if (roundedArt != null) {
                r.set(M, top, M + size, base);
                bmp.setAlpha(255);
                c.drawBitmap(roundedArt, null, r, bmp);
            }
            float tx = roundedArt != null ? M + size + 30 : M;
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(18);
            text.setAlpha(170);
            text.setLetterSpacing(0.2f);
            c.drawText("NOW PLAYING", tx, top + 40, text);
            text.setLetterSpacing(0);
            text.setTextSize(36);
            text.setAlpha(255);
            c.drawText(ellipsize(songTitle, 620), tx, top + 92, text);
            text.setTextSize(26);
            text.setAlpha(190);
            c.drawText(ellipsize(songArtist, 620), tx, top + 132, text);
        }
        c.restore();
    }

    private void drawBitmap(Canvas c, Bitmap g, float left, float top, float size) {
        float full = size * (1 + 2 * Glyphs.PAD), pad = size * Glyphs.PAD;
        r.set(left - pad, top - pad, left - pad + full, top - pad + full);
        bmp.setAlpha(230);
        c.drawBitmap(g, null, r, bmp);
    }

    private String ellipsize(String s, float max) {
        if (text.measureText(s) <= max) return s;
        while (s.length() > 1 && text.measureText(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s.trim() + "…";
    }

    static Bitmap rounded(Bitmap src, int size, float radius) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        BitmapShader sh = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        android.graphics.Matrix m = new android.graphics.Matrix();
        float sc = Math.max(size / (float) src.getWidth(), size / (float) src.getHeight());
        m.setScale(sc, sc);
        m.postTranslate((size - src.getWidth() * sc) / 2, (size - src.getHeight() * sc) / 2);
        sh.setLocalMatrix(m);
        p.setShader(sh);
        new Canvas(out).drawRoundRect(new RectF(0, 0, size, size), radius, radius, p);
        return out;
    }
}
