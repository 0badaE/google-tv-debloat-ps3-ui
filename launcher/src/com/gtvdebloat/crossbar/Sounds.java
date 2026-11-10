package com.gtvdebloat.crossbar;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Navigation sounds, played with SoundPool, in one of two sets:
 * <ul>
 *   <li><b>Zen</b> (default): the "zen" pack from uisfx (CC0), bundled in res/raw;</li>
 *   <li><b>Original</b>: Crossbar's own, synthesized on first run into small WAV files.</li>
 * </ul>
 */
final class Sounds {
    static final int TICK = 0, TOCK = 1, OK = 2, BACK = 3, SWELL = 4;
    static final String ZEN = "zen", ORIGINAL = "original", OFF = "off";
    static final String[] STYLES = {ZEN, ORIGINAL, OFF};
    static final String[] STYLE_NAMES = {"Zen", "Original", "Off"};

    private static final int RATE = 44100;
    private static final int VERSION = 1;
    // uisfx zen cues: hover (up/down), swipe (left/right), open, back, wake (startup).
    private static final int[] ZEN_RAW = {R.raw.zen_hover, R.raw.zen_swipe, R.raw.zen_open, R.raw.zen_back, R.raw.zen_wake};
    // uisfx's suggested levels, made for web pages, raised together so they carry across a living room.
    private static final float[] ZEN_VOLUME = {0.67f, 0.79f, 1.0f, 0.95f, 0.95f};
    private static final float[] ORIGINAL_VOLUME = {0.45f, 0.5f, 0.55f, 0.5f, 0.6f};

    private final Context context;
    private final SoundPool pool;
    private final int[][] ids = new int[2][5];
    private final boolean[] loading = new boolean[2];
    private final java.util.Set<Integer> readyIds = new java.util.HashSet<>();
    private volatile String style;

    Sounds(Context context, String style) {
        this.context = context.getApplicationContext();
        pool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        // Load completion can arrive before ids[] is filled in, so track ready sample ids instead.
        pool.setOnLoadCompleteListener((p, sampleId, status) -> {
            if (status == 0) synchronized (readyIds) { readyIds.add(sampleId); }
            else Log.w("Crossbar", "Sound failed to load: " + sampleId + " status " + status);
        });
        setStyle(style);
    }

    /** Switches set; each set loads the first time it's used. */
    void setStyle(String s) {
        style = s;
        if (ZEN.equals(s) && !loading[0]) {
            loading[0] = true;
            for (int i = 0; i < ZEN_RAW.length; i++) ids[0][i] = pool.load(context, ZEN_RAW[i], 1);
        } else if (ORIGINAL.equals(s) && !loading[1]) {
            loading[1] = true;
            new Thread(this::loadOriginal, "sounds").start();
        }
    }

    String style() { return style; }

    void play(int sound) {
        String s = style;
        int set = ZEN.equals(s) ? 0 : ORIGINAL.equals(s) ? 1 : -1;
        if (set < 0) return;
        int id = ids[set][sound];
        synchronized (readyIds) {
            if (!readyIds.contains(id)) return;
        }
        float v = set == 0 ? ZEN_VOLUME[sound] : ORIGINAL_VOLUME[sound];
        pool.play(id, v, v, 1, 0, 1f);
    }

    private void loadOriginal() {
        File dir = new File(context.getCacheDir(), "sounds-v" + VERSION);
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        for (int i = 0; i < ids[1].length; i++) {
            File f = new File(dir, i + ".wav");
            try {
                if (!f.exists()) writeWav(f, synth(i));
                ids[1][i] = pool.load(f.getPath(), 1);
            } catch (IOException e) {
                Log.w("Crossbar", "Could not create sound " + i, e);
            }
        }
    }

    private static float[] synth(int sound) {
        switch (sound) {
            case TICK: return tone(0.07f, new float[]{2350, 3520}, new float[]{1f, 0.25f}, 0.0012f, 0.011f, 0);
            case TOCK: return tone(0.11f, new float[]{1180, 1770}, new float[]{1f, 0.2f}, 0.0015f, 0.022f, -0.25f);
            case OK: return mix(
                    tone(0.5f, new float[]{1046.5f, 2093f}, new float[]{1f, 0.18f}, 0.002f, 0.11f, 0),
                    tone(0.55f, new float[]{1568f, 3136f}, new float[]{0.85f, 0.12f}, 0.002f, 0.16f, 0), 0.065f);
            case BACK: return mix(
                    tone(0.35f, new float[]{1318.5f}, new float[]{0.8f}, 0.002f, 0.08f, 0),
                    tone(0.45f, new float[]{880f}, new float[]{0.9f}, 0.002f, 0.12f, 0), 0.06f);
            case SWELL:
            default: return swell();
        }
    }

    /** Bell-like partials with an exponential decay; {@code glide} bends the pitch over the sound. */
    private static float[] tone(float seconds, float[] freqs, float[] amps, float attack, float decay, float glide) {
        int n = (int) (seconds * RATE);
        float[] out = new float[n];
        double[] phase = new double[freqs.length];
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            float env = Math.min(1f, t / attack) * (float) Math.exp(-t / decay);
            float bend = 1f + glide * (t / seconds);
            float s = 0;
            for (int k = 0; k < freqs.length; k++) {
                phase[k] += 2 * Math.PI * freqs[k] * bend / RATE;
                s += Math.sin(phase[k]) * amps[k];
            }
            out[i] = s * env;
        }
        return out;
    }

    private static float[] mix(float[] a, float[] b, float bOffsetSeconds) {
        int off = (int) (bOffsetSeconds * RATE);
        float[] out = new float[Math.max(a.length, off + b.length)];
        for (int i = 0; i < a.length; i++) out[i] += a[i];
        for (int i = 0; i < b.length; i++) out[off + i] += b[i];
        return out;
    }

    /** A slow, warm chord that blooms in and fades out, for waking up. */
    private static float[] swell() {
        float seconds = 4.2f;
        float[] notes = {130.81f, 196.00f, 261.63f, 329.63f, 392.00f, 493.88f, 587.33f};
        float[] amps = {0.9f, 0.7f, 0.65f, 0.5f, 0.45f, 0.3f, 0.25f};
        int n = (int) (seconds * RATE);
        float[] out = new float[n];
        for (int k = 0; k < notes.length; k++) {
            for (int voice = -1; voice <= 1; voice += 2) {
                double f = notes[k] * (1 + voice * 0.0025);
                double ph = k * 1.3 + voice;
                double step = 2 * Math.PI * f / RATE;
                for (int i = 0; i < n; i++) {
                    float t = (float) i / RATE;
                    float a = smooth(Math.min(1f, t / (1.4f + k * 0.12f)));
                    float r = t < 2.3f ? 1f : (float) Math.exp(-(t - 2.3f) / 0.6f);
                    ph += step;
                    out[i] += Math.sin(ph) * amps[k] * a * r;
                }
            }
        }
        return out;
    }

    private static float smooth(float t) { return t * t * (3 - 2 * t); }

    private static void writeWav(File f, float[] samples) throws IOException {
        float peak = 1e-6f;
        for (float s : samples) peak = Math.max(peak, Math.abs(s));
        float gain = 0.8f / peak;
        int dataLen = samples.length * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + dataLen).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes()).putInt(dataLen);
        for (float s : samples) b.putShort((short) Math.max(-32767, Math.min(32767, s * gain * 32767)));
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            os.write(b.array());
        }
        if (!tmp.renameTo(f)) throw new IOException("rename failed: " + f);
    }
}
