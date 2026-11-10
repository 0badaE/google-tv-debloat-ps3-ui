package com.gtvdebloat.crossbar;

import android.service.dreams.DreamService;
import android.widget.FrameLayout;

/** The screensaver: the same moving wave as the home screen, with Ambient-style info on top. */
public final class WaveDream extends DreamService {
    private WaveView wave;
    private AmbientView ambient;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        String theme = getSharedPreferences("crossbar", MODE_PRIVATE).getString("theme", Themes.MONTHLY);
        wave = new WaveView(this);
        wave.showTheme(theme, 0);
        ambient = new AmbientView(this);
        FrameLayout root = new FrameLayout(this);
        root.addView(wave);
        root.addView(ambient);
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        wave.onResume();
        wave.setFade(0, 1, 2500);
        ambient.start();
    }

    @Override
    public void onDreamingStopped() {
        ambient.stop();
        wave.onPause();
        super.onDreamingStopped();
    }
}
