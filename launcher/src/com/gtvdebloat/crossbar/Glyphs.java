package com.gtvdebloat.crossbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/** Original solid-white icons, drawn as vector paths in a 100x100 box and baked into bitmaps once. */
final class Glyphs {
    static final int TOOLBOX = 0, TV = 1, FILM = 2, MUSIC = 3, APPS = 4, PALETTE = 5, SPEAKER = 6,
            DISPLAY = 7, WIFI = 8, REMOTE = 9, MOON = 10, CLOCK = 11, GEAR = 12, AMBIENT = 13,
            LIVE = 14, HOME = 15, MIC = 16, DOWNLOAD = 17, SUN = 18, CLOUD = 19, PARTLY = 20, RAIN = 21,
            SNOW = 22, STORM = 23, FOG = 24, SLIDERS = 25;

    /** Shadow padding around the glyph, as a fraction of the glyph size, on each side. */
    static final float PAD = 0.18f;

    private Glyphs() {}

    /** Renders a glyph whose 100-unit box is {@code size} pixels, plus {@link #PAD} on each side. */
    static Bitmap render(int kind, int size) {
        int pad = Math.round(size * PAD);
        Bitmap b = Bitmap.createBitmap(size + pad * 2, size + pad * 2, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, pad, 0, pad + size, 0xFFFFFFFF, 0xFFD6D9E6, Shader.TileMode.CLAMP));
        p.setShadowLayer(size * 0.06f, 0, size * 0.03f, 0x66000000);
        c.translate(pad, pad);
        c.scale(size / 100f, size / 100f);
        c.drawPath(path(kind), p);
        return b;
    }

    static Path path(int kind) {
        Path p = new Path();
        switch (kind) {
            case TOOLBOX: {
                p.addPath(rr(10, 38, 90, 86, 9));
                Path handle = rr(32, 20, 68, 44, 8);
                handle.op(rr(39, 27, 61, 44, 4), Path.Op.DIFFERENCE);
                p.op(handle, Path.Op.UNION);
                p.op(rect(10, 56, 90, 60), Path.Op.DIFFERENCE);
                p.op(rr(43, 51, 57, 65, 3), Path.Op.UNION);
                break;
            }
            case TV: {
                p.addPath(rr(8, 28, 92, 80, 10));
                p.op(rr(16, 36, 84, 72, 4), Path.Op.DIFFERENCE);
                p.op(rr(36, 84, 64, 89, 2.5f), Path.Op.UNION);
                Path ant = new Path();
                ant.moveTo(50, 28);
                ant.lineTo(35, 11);
                ant.moveTo(50, 28);
                ant.lineTo(65, 11);
                p.op(stroke(ant, 5), Path.Op.UNION);
                break;
            }
            case FILM: {
                p.addPath(rr(18, 10, 82, 90, 3));
                p.op(rect(31, 22, 69, 78), Path.Op.DIFFERENCE);
                for (int i = 0; i < 5; i++) {
                    float y = 14 + i * 15.5f;
                    p.op(rr(21.5f, y, 27.5f, y + 7, 1.5f), Path.Op.DIFFERENCE);
                    p.op(rr(72.5f, y, 78.5f, y + 7, 1.5f), Path.Op.DIFFERENCE);
                }
                break;
            }
            case MUSIC: {
                p.addPath(oval(32, 77, 13, 9.5f, -22));
                p.op(oval(72, 69, 13, 9.5f, -22), Path.Op.UNION);
                p.op(rect(39, 22, 45, 76), Path.Op.UNION);
                p.op(rect(79, 14, 85, 68), Path.Op.UNION);
                Path beam = new Path();
                beam.moveTo(39, 22);
                beam.lineTo(85, 13);
                beam.lineTo(85, 27);
                beam.lineTo(39, 36);
                beam.close();
                p.op(beam, Path.Op.UNION);
                break;
            }
            case APPS: {
                p.addPath(rr(14, 14, 45, 45, 9));
                p.op(rr(55, 14, 86, 45, 9), Path.Op.UNION);
                p.op(rr(14, 55, 45, 86, 9), Path.Op.UNION);
                p.op(rr(55, 55, 86, 86, 9), Path.Op.UNION);
                break;
            }
            case PALETTE: {
                p.addCircle(50, 50, 40, Path.Direction.CW);
                p.op(circle(31, 40, 7), Path.Op.DIFFERENCE);
                p.op(circle(47, 26, 7), Path.Op.DIFFERENCE);
                p.op(circle(66, 30, 7), Path.Op.DIFFERENCE);
                p.op(circle(74, 48, 7), Path.Op.DIFFERENCE);
                p.op(circle(42, 67, 10), Path.Op.DIFFERENCE);
                break;
            }
            case SPEAKER: {
                p.moveTo(12, 38);
                p.lineTo(28, 38);
                p.lineTo(50, 18);
                p.lineTo(50, 82);
                p.lineTo(28, 62);
                p.lineTo(12, 62);
                p.close();
                p.op(stroke(arc(54, 50, 16, -48, 96), 7), Path.Op.UNION);
                p.op(stroke(arc(54, 50, 31, -48, 96), 7), Path.Op.UNION);
                break;
            }
            case DISPLAY: {
                p.addPath(rr(8, 16, 92, 70, 8));
                p.op(rr(15, 23, 85, 63, 3), Path.Op.DIFFERENCE);
                p.op(rect(45, 69, 55, 80), Path.Op.UNION);
                p.op(rr(30, 79, 70, 85, 3), Path.Op.UNION);
                break;
            }
            case WIFI: {
                p.addCircle(50, 79, 7, Path.Direction.CW);
                for (int r = 18; r <= 54; r += 18) p.op(stroke(arc(50, 79, r, -135, 90), 9), Path.Op.UNION);
                break;
            }
            case REMOTE: {
                p.addPath(rr(33, 6, 67, 94, 16));
                p.op(circle(50, 31, 11), Path.Op.DIFFERENCE);
                p.op(circle(50, 31, 5), Path.Op.UNION);
                p.op(circle(50, 56, 4), Path.Op.DIFFERENCE);
                p.op(circle(50, 68, 4), Path.Op.DIFFERENCE);
                p.op(rr(43, 78, 57, 82, 2), Path.Op.DIFFERENCE);
                break;
            }
            case MOON: {
                p.addCircle(48, 52, 36, Path.Direction.CW);
                p.op(circle(68, 36, 30), Path.Op.DIFFERENCE);
                break;
            }
            case CLOCK: {
                p.addCircle(50, 50, 40, Path.Direction.CW);
                p.op(circle(50, 50, 33), Path.Op.DIFFERENCE);
                p.op(rr(47, 25, 53, 53, 3), Path.Op.UNION);
                p.op(rr(47, 47, 68, 53, 3), Path.Op.UNION);
                break;
            }
            case GEAR: {
                p.addCircle(50, 50, 29, Path.Direction.CW);
                Matrix m = new Matrix();
                for (int i = 0; i < 8; i++) {
                    Path tooth = rr(43, 8, 57, 26, 3);
                    m.setRotate(i * 45, 50, 50);
                    tooth.transform(m);
                    p.op(tooth, Path.Op.UNION);
                }
                p.op(circle(50, 50, 12), Path.Op.DIFFERENCE);
                break;
            }
            case AMBIENT: {
                p.addPath(rr(8, 18, 92, 82, 8));
                p.op(rr(15, 25, 85, 75, 3), Path.Op.DIFFERENCE);
                Path hills = new Path();
                hills.moveTo(20, 70);
                hills.lineTo(40, 45);
                hills.lineTo(53, 61);
                hills.lineTo(62, 52);
                hills.lineTo(80, 70);
                hills.close();
                p.op(hills, Path.Op.UNION);
                p.op(circle(67, 37, 6), Path.Op.UNION);
                break;
            }
            case LIVE: {
                p.addCircle(50, 50, 9, Path.Direction.CW);
                for (int r = 22; r <= 38; r += 16) {
                    p.op(stroke(arc(50, 50, r, -40, 80), 7), Path.Op.UNION);
                    p.op(stroke(arc(50, 50, r, 140, 80), 7), Path.Op.UNION);
                }
                break;
            }
            case HOME: {
                p.moveTo(50, 12);
                p.lineTo(90, 46);
                p.lineTo(82, 46);
                p.lineTo(82, 88);
                p.lineTo(18, 88);
                p.lineTo(18, 46);
                p.lineTo(10, 46);
                p.close();
                p.op(rr(42, 62, 58, 89, 2), Path.Op.DIFFERENCE);
                break;
            }
            case MIC: {
                p.addPath(rr(37, 8, 63, 56, 13));
                p.op(stroke(arc(50, 42, 23, 0, 180), 6), Path.Op.UNION);
                p.op(rect(47, 64, 53, 80), Path.Op.UNION);
                p.op(rr(35, 79, 65, 85, 3), Path.Op.UNION);
                break;
            }
            case SUN: {
                p.addCircle(50, 50, 17, Path.Direction.CW);
                Matrix m = new Matrix();
                for (int i = 0; i < 8; i++) {
                    Path ray = rr(47, 10, 53, 24, 3);
                    m.setRotate(i * 45, 50, 50);
                    ray.transform(m);
                    p.op(ray, Path.Op.UNION);
                }
                break;
            }
            case CLOUD:
                p.addPath(cloud(0, 0));
                break;
            case PARTLY: {
                Path sun = path(SUN);
                Matrix m = new Matrix();
                m.setScale(0.62f, 0.62f, 0, 0);
                m.postTranslate(10, 6);
                sun.transform(m);
                Path c = cloud(8, 12);
                Path gap = stroke(c, 9);
                gap.op(c, Path.Op.UNION);
                sun.op(gap, Path.Op.DIFFERENCE);
                p.addPath(sun);
                p.op(c, Path.Op.UNION);
                break;
            }
            case RAIN: {
                p.addPath(cloud(0, -14));
                Path drops = new Path();
                for (int i = 0; i < 3; i++) {
                    drops.moveTo(38 + i * 14, 72);
                    drops.lineTo(34 + i * 14, 86);
                }
                p.op(stroke(drops, 6), Path.Op.UNION);
                break;
            }
            case SNOW: {
                p.addPath(cloud(0, -14));
                p.op(circle(36, 78, 4.5f), Path.Op.UNION);
                p.op(circle(50, 86, 4.5f), Path.Op.UNION);
                p.op(circle(64, 78, 4.5f), Path.Op.UNION);
                break;
            }
            case STORM: {
                p.addPath(cloud(0, -14));
                Path bolt = new Path();
                bolt.moveTo(54, 60);
                bolt.lineTo(40, 80);
                bolt.lineTo(50, 80);
                bolt.lineTo(44, 96);
                bolt.lineTo(64, 72);
                bolt.lineTo(53, 72);
                bolt.lineTo(60, 60);
                bolt.close();
                Path gap = stroke(bolt, 7);
                gap.op(bolt, Path.Op.UNION);
                p.op(gap, Path.Op.DIFFERENCE);
                p.op(bolt, Path.Op.UNION);
                break;
            }
            case FOG: {
                p.addPath(rr(16, 32, 84, 41, 4.5f));
                p.op(rr(24, 48, 76, 57, 4.5f), Path.Op.UNION);
                p.op(rr(16, 64, 84, 73, 4.5f), Path.Op.UNION);
                break;
            }
            case SLIDERS: {
                p.addPath(rr(14, 23, 86, 31, 4));
                p.op(circle(34, 27, 10), Path.Op.UNION);
                p.op(rr(14, 46, 86, 54, 4), Path.Op.UNION);
                p.op(circle(66, 50, 10), Path.Op.UNION);
                p.op(rr(14, 69, 86, 77, 4), Path.Op.UNION);
                p.op(circle(46, 73, 10), Path.Op.UNION);
                break;
            }
            case DOWNLOAD:
            default: {
                p.addPath(rect(44, 10, 56, 46));
                Path head = new Path();
                head.moveTo(26, 42);
                head.lineTo(74, 42);
                head.lineTo(50, 68);
                head.close();
                p.op(head, Path.Op.UNION);
                p.op(rr(16, 76, 84, 86, 4), Path.Op.UNION);
                break;
            }
        }
        return p;
    }

    /** A flat-bottomed cloud, offset by (dx, dy). */
    private static Path cloud(float dx, float dy) {
        Path p = circle(36 + dx, 58 + dy, 15);
        p.op(circle(54 + dx, 48 + dy, 20), Path.Op.UNION);
        p.op(circle(70 + dx, 60 + dy, 13), Path.Op.UNION);
        p.op(rr(21 + dx, 58 + dy, 83 + dx, 73 + dy, 7.5f), Path.Op.UNION);
        return p;
    }

    private static Path rr(float l, float t, float r, float b, float rad) {
        Path p = new Path();
        p.addRoundRect(new RectF(l, t, r, b), rad, rad, Path.Direction.CW);
        return p;
    }

    private static Path rect(float l, float t, float r, float b) {
        Path p = new Path();
        p.addRect(l, t, r, b, Path.Direction.CW);
        return p;
    }

    private static Path circle(float x, float y, float r) {
        Path p = new Path();
        p.addCircle(x, y, r, Path.Direction.CW);
        return p;
    }

    private static Path oval(float cx, float cy, float rx, float ry, float deg) {
        Path p = new Path();
        p.addOval(new RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW);
        Matrix m = new Matrix();
        m.setRotate(deg, cx, cy);
        p.transform(m);
        return p;
    }

    private static Path arc(float cx, float cy, float r, float start, float sweep) {
        Path p = new Path();
        p.addArc(new RectF(cx - r, cy - r, cx + r, cy + r), start, sweep);
        return p;
    }

    private static Path stroke(Path src, float width) {
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeWidth(width);
        s.setStrokeCap(Paint.Cap.ROUND);
        s.setStrokeJoin(Paint.Join.ROUND);
        Path out = new Path();
        s.getFillPath(src, out);
        return out;
    }
}
