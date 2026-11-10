package com.gtvdebloat.crossbar;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Steps the Home button helper aside while Google TV's own panel and smart-home screens are open.
 *
 * Any running accessibility service puts Google's smart-home controls into an accessibility mode
 * where Left/Right no longer change a lamp's brightness. So when you hold Home, the helper opens
 * Google's panel and switches itself off; once you leave Google's screens, this switches it back on.
 * It only re-enables what it paused, never a helper you turned off yourself.
 *
 * Needs WRITE_SECURE_SETTINGS (to switch the helper) and usage access (to see which screen is in
 * front), both granted over ADB. Without them, the helper simply stays on.
 */
final class HelperPause {
    private static final String TAG = "Crossbar";
    private static final String PREF = "helper_paused";
    private static final long POLL_MS = 700;
    /** Google screens to stay paused on: its panel, smart-home controls, and what floats over them. */
    private static final Set<String> GOOGLE_SCREENS = new HashSet<>(Arrays.asList(
            Catalog.LAUNCHER_X, "com.google.android.apps.tv.dreamx", "com.android.systemui",
            "com.google.android.katniss", "com.google.android.inputmethod.latin"));
    private static final String STOCK_HOME = Catalog.LAUNCHER_X + ".home.HomeActivity";

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable watcher;

    private HelperPause() {}

    static boolean available(Context c) {
        if (c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                == AppOpsManager.MODE_ALLOWED;
    }

    /** Switches the helper off and watches for the user leaving Google's screens. */
    static void pause(Context context) {
        Context c = context.getApplicationContext();
        if (!available(c)) return;
        prefs(c).edit().putBoolean(PREF, true).commit();
        setHelperEnabled(c, false);
        watch(c);
    }

    /** Switches the helper back on if this paused it. Safe to call any time. */
    static void resume(Context context) {
        Context c = context.getApplicationContext();
        stopWatching();
        if (!prefs(c).getBoolean(PREF, false)) return;
        prefs(c).edit().putBoolean(PREF, false).commit();
        if (available(c)) setHelperEnabled(c, true);
    }

    /** After a restart: keep watching if the helper was left paused. */
    static void recover(Context context) {
        Context c = context.getApplicationContext();
        if (prefs(c).getBoolean(PREF, false)) watch(c);
    }

    /** Whether Google TV's own home screen is the thing in front. */
    static boolean stockHomeInFront(Context c) {
        String[] fg = foreground(c);
        return fg != null && Catalog.LAUNCHER_X.equals(fg[0]) && STOCK_HOME.equals(fg[1]);
    }

    private static void watch(Context c) {
        stopWatching();
        watcher = new Runnable() {
            @Override
            public void run() {
                String[] fg = foreground(c);
                boolean onGoogle = fg != null && GOOGLE_SCREENS.contains(fg[0]) && !STOCK_HOME.equals(fg[1]);
                if (onGoogle) {
                    handler.postDelayed(this, POLL_MS);
                } else {
                    // Left Google's screens. If that was a Home press, the helper sends it to
                    // Crossbar as soon as it's back on (KeyService.onServiceConnected).
                    resume(c);
                }
            }
        };
        // Give Google's panel a moment to come to the front before the first check.
        handler.postDelayed(watcher, 1500);
    }

    private static void stopWatching() {
        if (watcher != null) handler.removeCallbacks(watcher);
        watcher = null;
    }

    /** {package, activity class} of the screen most recently brought to the front. */
    private static String[] foreground(Context c) {
        try {
            UsageStatsManager usage = c.getSystemService(UsageStatsManager.class);
            long now = System.currentTimeMillis();
            UsageEvents events = usage.queryEvents(now - 10 * 60_000L, now);
            UsageEvents.Event e = new UsageEvents.Event();
            String[] last = null;
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    last = new String[]{e.getPackageName(), e.getClassName()};
                }
            }
            return last;
        } catch (RuntimeException ex) {
            Log.w(TAG, "Can't read the foreground app", ex);
            return null;
        }
    }

    private static void setHelperEnabled(Context c, boolean on) {
        String me = new ComponentName(c, KeyService.class).flattenToString();
        String current = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        List<String> services = new ArrayList<>();
        if (current != null) {
            for (String s : current.split(":")) {
                if (!s.isEmpty() && !s.equalsIgnoreCase(me)) services.add(s);
            }
        }
        if (on) services.add(me);
        try {
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    String.join(":", services));
            if (on) Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (SecurityException e) {
            Log.w(TAG, "Can't switch the Home button helper", e);
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("crossbar", Context.MODE_PRIVATE);
    }
}
