package com.gtvdebloat.crossbar;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Keeps Crossbar in front while Google TV stays the system Home, so Google's own quick-settings
 * panel, YouTube button and recommendations keep working:
 * <ul>
 *   <li>a short press on Home opens Crossbar instead of Google TV;</li>
 *   <li>holding Home opens Google TV's panel (media, Google Home devices, Wi-Fi, notifications);</li>
 *   <li>the YouTube and Netflix buttons open their apps;</li>
 *   <li>if Google TV's home screen comes up on its own (waking up, an app sending you home),
 *       Crossbar replaces it, unless you opened Google TV from Crossbar.</li>
 * </ul>
 * Every other key passes through untouched. If this service is off, the TV is plain Google TV.
 */
public final class KeyService extends AccessibilityService {
    private static final String TAG = "Crossbar";
    private static final String STOCK_HOME = Catalog.LAUNCHER_X + ".home.HomeActivity";
    private static final String YOUTUBE = "com.google.android.youtube.tv";
    private static final String NETFLIX = "com.netflix.ninja";
    private static final long LONG_PRESS_MS = 500;
    /** Overlays that come and go on top of Google TV without the user leaving it. */
    private static final Set<String> OVERLAYS = new HashSet<>(Arrays.asList(
            Catalog.LAUNCHER_X, "com.android.systemui", "com.google.android.katniss",
            "com.google.android.inputmethod.latin"));

    private static volatile boolean stockHomeAllowed;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean homeDown;
    private boolean longPressed;

    // Only the system may open Google TV's panel, so ask it to.
    private final Runnable homeLongPress = () -> {
        longPressed = true;
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    };

    /** Called by Crossbar right before it opens Google TV on purpose. */
    static void allowStockHome() {
        stockHomeAllowed = true;
    }

    @Override
    protected void onServiceConnected() {
        // Back after a pause (see HelperPause): if Home was pressed meanwhile, Google TV is showing.
        if (HelperPause.stockHomeInFront(this) && !stockHomeAllowed) goHome();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_HOME:
                return onHome(e);
            // The remote's app buttons go to Google TV's launcher, which Android no longer lets start
            // apps once it isn't the screen in front. Open them from here instead.
            case KeyEvent.KEYCODE_BUTTON_3:
                if (e.getAction() == KeyEvent.ACTION_UP) openApp(YOUTUBE);
                return true;
            case KeyEvent.KEYCODE_BUTTON_4:
                if (e.getAction() == KeyEvent.ACTION_UP) openApp(NETFLIX);
                return true;
            default:
                return false;
        }
    }

    private void openApp(String pkg) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i != null) start(i);
    }

    private boolean onHome(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                homeDown = true;
                longPressed = false;
                handler.postDelayed(homeLongPress, LONG_PRESS_MS);
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP && homeDown) {
            homeDown = false;
            handler.removeCallbacks(homeLongPress);
            if (!longPressed) goHome();
            // Step aside so Google's smart-home controls work normally (see HelperPause).
            else handler.postDelayed(() -> HelperPause.pause(this), 300);
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName(), cls = event.getClassName();
        if (pkg == null) return;
        if (Catalog.LAUNCHER_X.contentEquals(pkg) && cls != null && STOCK_HOME.contentEquals(cls)) {
            if (!stockHomeAllowed) goHome();
        } else if (!OVERLAYS.contains(pkg.toString())) {
            // Any real app (Crossbar included) coming to the front ends a visit to Google TV.
            stockHomeAllowed = false;
        }
    }

    private void goHome() {
        stockHomeAllowed = false;
        start(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClass(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    private void start(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException ex) {
            Log.w(TAG, "Can't open " + i, ex);
        }
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(homeLongPress);
        homeDown = false;
    }
}
