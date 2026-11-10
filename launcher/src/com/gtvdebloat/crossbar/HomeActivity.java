package com.gtvdebloat.crossbar;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The home screen: a wave wallpaper on a GL surface with the cross-shaped menu drawn on top. */
public final class HomeActivity extends Activity implements XmbView.Host {
    private static final String TAG = "Crossbar";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private WaveView wave;
    private XmbView xmb;
    private Sounds sounds;
    private boolean resumed;
    private boolean pendingIntro = true;
    private String themeId;

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            if (Themes.MONTHLY.equals(themeId)) wave.showTheme(themeId, 8000);
            long ms = System.currentTimeMillis();
            handler.postDelayed(this, 60_000 - ms % 60_000 + 50);
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            loadCatalog();
        }
    };

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) {
                pendingIntro = true;
                if (resumed) intro();
            } else if (Intent.ACTION_TIME_CHANGED.equals(i.getAction())
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(i.getAction())) {
                updateClock();
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("crossbar", MODE_PRIVATE);
        themeId = prefs.getString("theme", Themes.MONTHLY);
        // Older versions saved just on/off; "on" now means the default Zen set.
        String style = prefs.getString("sound_style", prefs.getBoolean("sounds", true) ? Sounds.ZEN : Sounds.OFF);
        sounds = new Sounds(this, style);

        wave = new WaveView(this);
        wave.showTheme(themeId, 0);
        xmb = new XmbView(this, this);
        FrameLayout root = new FrameLayout(this);
        root.addView(wave);
        root.addView(xmb);
        setContentView(root);
        xmb.requestFocus();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf, RECEIVER_EXPORTED);
        IntentFilter sf = new IntentFilter(Intent.ACTION_SCREEN_ON);
        sf.addAction(Intent.ACTION_TIME_CHANGED);
        sf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(screen, sf, RECEIVER_EXPORTED);

        loadCatalog();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        unregisterReceiver(screen);
        super.onDestroy();
    }

    // The wave keeps moving while anything sits on top without covering it (Google's panel, the
    // Assistant), and only stops once Crossbar is fully off screen.
    @Override
    protected void onStart() {
        super.onStart();
        wave.onResume();
        handler.removeCallbacks(minuteTick);
        minuteTick.run();
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(minuteTick);
        wave.onPause();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (pendingIntro) intro();
        HelperPause.resume(this);
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (resumed && intent.hasCategory(Intent.CATEGORY_HOME)) xmb.goHome();
    }

    private void intro() {
        pendingIntro = false;
        wave.setFade(0, 1, 2200);
        xmb.playIntro();
        handler.postDelayed(() -> sounds.play(Sounds.SWELL), 120);
    }

    private void loadCatalog() {
        new Thread(() -> {
            List<Catalog.Category> cats = new Catalog(this).build();
            Map<String, String> keys = new HashMap<>();
            for (Catalog.Category c : cats) {
                String k = prefs.getString("sel_" + c.id, null);
                if (k != null) keys.put(c.id, k);
            }
            runOnUiThread(() -> xmb.setCategories(cats, prefs.getString("cat", Catalog.VIDEO), keys));
        }, "catalog").start();
    }

    private void updateClock() {
        Locale l = Locale.getDefault();
        String date = DateFormat.format(DateFormat.getBestDateTimePattern(l, "EEEdMMM"), Calendar.getInstance()).toString();
        String time = DateFormat.format(DateFormat.getBestDateTimePattern(l,
                DateFormat.is24HourFormat(this) ? "Hmm" : "hmm"), Calendar.getInstance()).toString();
        xmb.setClock(date + "     " + time);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_SEARCH || code == KeyEvent.KEYCODE_ASSIST || code == KeyEvent.KEYCODE_VOICE_ASSIST) {
            if (e.getAction() == KeyEvent.ACTION_UP) startVoiceSearch();
            return true;
        }
        if (xmb.handleKey(e)) return true;
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onBackPressed() {
        // The home screen never closes.
    }

    private void startVoiceSearch() {
        try {
            startActivity(new Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "No assistant", e);
        }
    }

    // --- XmbView.Host ----------------------------------------------------------------------------

    @Override
    public Sounds sounds() {
        return sounds;
    }

    @Override
    public void onActivate(Catalog.Item item) {
        if ("theme".equals(item.panel)) {
            Calendar now = Calendar.getInstance();
            WaveSettings custom = WaveSettings.get(this);
            int[] dots = new int[Themes.IDS.length];
            for (int i = 0; i < dots.length; i++) dots[i] = Themes.dotColor(Themes.gradient(Themes.IDS[i], now, custom));
            xmb.openPanel(new XmbView.Panel("theme", "Theme", Themes.NAMES, dots, Themes.indexOf(themeId)));
            return;
        }
        if ("wave".equals(item.panel)) {
            // Background sliders only show on the Custom theme, so moving one switches to it.
            xmb.openControls(new WaveControls(WaveSettings.get(this), sounds, () -> {
                if (Themes.CUSTOM.equals(themeId)) return;
                themeId = Themes.CUSTOM;
                prefs.edit().putString("theme", themeId).apply();
                wave.showTheme(themeId, 600);
            }));
            return;
        }
        if ("sounds".equals(item.panel)) {
            xmb.openPanel(new XmbView.Panel("sounds", "Sounds", Sounds.STYLE_NAMES, null,
                    java.util.Arrays.asList(Sounds.STYLES).indexOf(sounds.style())));
            return;
        }
        if (item.intent == null) return;
        if (Catalog.LAUNCHER_X.equals(item.intent.getComponent() == null ? null
                : item.intent.getComponent().getPackageName())) {
            KeyService.allowStockHome();
        }
        Intent launch = new Intent(item.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Shortcuts (settings screens, store pages) start fresh so Back returns straight home; apps resume.
        if (item.glyph) launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            startActivity(launch);
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "Can't open " + item.key, e);
            Toast.makeText(this, item.title + " isn't available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPanelPreview(String panel, int option) {
        if ("theme".equals(panel)) wave.showTheme(Themes.IDS[option], 500);
        // Moving through the sound sets plays each one, since the next tick uses it.
        else if ("sounds".equals(panel)) sounds.setStyle(Sounds.STYLES[option]);
    }

    @Override
    public void onPanelCommit(String panel, int option) {
        if ("theme".equals(panel)) {
            themeId = Themes.IDS[option];
            prefs.edit().putString("theme", themeId).apply();
            wave.showTheme(themeId, 500);
        } else if ("sounds".equals(panel)) {
            sounds.setStyle(Sounds.STYLES[option]);
            prefs.edit().putString("sound_style", Sounds.STYLES[option]).apply();
        }
    }

    @Override
    public void onPanelCancel(String panel) {
        if ("theme".equals(panel)) wave.showTheme(themeId, 500);
        else if ("sounds".equals(panel)) {
            sounds.setStyle(prefs.getString("sound_style", prefs.getBoolean("sounds", true) ? Sounds.ZEN : Sounds.OFF));
        }
    }

    @Override
    public void onSelectionChanged(String categoryId, String itemKey) {
        SharedPreferences.Editor ed = prefs.edit().putString("cat", categoryId);
        if (itemKey != null) ed.putString("sel_" + categoryId, itemKey);
        ed.apply();
    }
}
