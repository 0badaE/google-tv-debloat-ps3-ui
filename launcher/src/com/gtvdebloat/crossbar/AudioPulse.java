package com.gtvdebloat.crossbar;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

/**
 * Listens to whatever the TV is playing (the output mix, through Android's Visualizer: a coarse
 * spectrum, no microphone) and turns it into one number the wave can follow: how loud the song is
 * right now compared with its own recent peaks. Without the audio permission it still reports
 * "music is playing", so the wave can at least flow faster.
 */
final class AudioPulse {
    private static final String TAG = "Crossbar";

    private final Context context;
    private final AudioManager audio;
    private HandlerThread thread;
    private Handler handler;
    private Visualizer visualizer;

    private volatile float level;
    private volatile boolean musicActive;
    private float peak;

    AudioPulse(Context context) {
        this.context = context.getApplicationContext();
        this.audio = context.getSystemService(AudioManager.class);
    }

    void start() {
        if (thread != null) return;
        thread = new HandlerThread("audio-pulse");
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(this::open);
        handler.post(pollMusic);
    }

    void stop() {
        if (thread == null) return;
        handler.removeCallbacksAndMessages(null);
        handler.post(() -> {
            if (visualizer != null) {
                visualizer.release();
                visualizer = null;
            }
        });
        thread.quitSafely();
        thread = null;
        handler = null;
        level = 0;
        musicActive = false;
    }

    /** Whether something is playing music right now. */
    boolean musicActive() { return musicActive; }

    /** Whether {@link #level()} is real or just zero. */
    boolean listening() { return visualizer != null; }

    /** Loudness 0..1, relative to the song's recent peaks. Raw: smooth it before showing it. */
    float level() { return level; }

    private final Runnable pollMusic = new Runnable() {
        @Override
        public void run() {
            musicActive = audio.isMusicActive();
            if (!musicActive) level = 0;
            if (handler != null) handler.postDelayed(this, 500);
        }
    };

    private void open() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        try {
            Visualizer v = new Visualizer(0); // 0 = everything the TV plays
            // Visualizers on the same output share one engine; if another (the home screen's, while
            // the screensaver starts) still has it running, it must be stopped before it can be set up.
            if (v.getEnabled()) v.setEnabled(false);
            int[] range = Visualizer.getCaptureSizeRange();
            v.setCaptureSize(Math.max(range[0], Math.min(range[1], 512)));
            v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED);
            v.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer vis, byte[] waveform, int rate) {
                }

                @Override
                public void onFftDataCapture(Visualizer vis, byte[] fft, int rate) {
                    analyze(fft);
                }
            }, Visualizer.getMaxCaptureRate(), false, true);
            v.setEnabled(true);
            visualizer = v;
        } catch (RuntimeException e) {
            Log.w(TAG, "Audio visualizer unavailable; the wave will only flow faster with music", e);
        }
    }

    private void analyze(byte[] fft) {
        if (!musicActive) return;
        // Bins 1..48 cover roughly 90 Hz to 4.5 kHz at a 512-sample capture: the body of the music.
        float sum = 0;
        int bins = 0;
        for (int k = 1; k <= 48 && 2 * k + 1 < fft.length; k++, bins++) {
            sum += (float) Math.hypot(fft[2 * k], fft[2 * k + 1]);
        }
        float raw = sum / Math.max(1, bins);
        // Songs differ a lot in loudness, so measure against this song's recent peak (decays ~10%/s).
        peak = Math.max(raw, peak * 0.995f);
        level = raw / Math.max(peak, 4f);
    }
}
