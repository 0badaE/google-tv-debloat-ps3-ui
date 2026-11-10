package com.gtvdebloat.framex;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * frameXTV as a TV app: the website, full screen, driven by the remote.
 *
 * D-pad moves a focus ring between links and buttons (assets/tvnav.js), OK clicks; OK on the
 * video player steps onto its controls. Holding OK toggles pointer mode (CursorView), where the
 * D-pad steers an arrow and OK taps, for anything the ring can't reach, such as an embed from
 * another site (selecting one of those switches to the pointer by itself). Back leaves it.
 */
public class MainActivity extends Activity implements CursorView.Host {

    private static final String HOME = "https://framextv.cc/";
    /** Main-frame navigation is allowed only to hosts containing this; everything else is an ad. */
    private static final String SITE = "framextv";
    /** CSS width the page lays out at: 1920px screen / 1440 = text at 1.33x, comfortable from the couch. */
    private static final int VIEWPORT_WIDTH = 1440;
    /** Width of the site's centered content column (it uses 1280 on desktop); the rest is margin. */
    private static final int CONTENT_WIDTH = 1120;
    /** A held D-pad repeats ~20x a second; skip repeats while the last move is still running. */
    private static final long NAV_STALL_MS = 400;
    private static final long LONG_PRESS_MS = 600;
    private static final long EXIT_WINDOW_MS = 2000;
    private static final long AWAKE_POLL_MS = 15000;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private WebView web;
    private CursorView cursor;
    private TextView message;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private String navJs;
    private AudioManager audio;

    private boolean okConsumed, loadFailed, pointerHintShown, navPending, revealed;
    private long lastBack, navSentAt;
    private Toast toast;

    private final Runnable okLongPress = () -> {
        okConsumed = true;
        setPointerMode(!cursor.isActive(), cursor.getWidth() / 2f, cursor.getHeight() / 2f);
    };

    // Video playing (or fullscreen): keep the screensaver away. Otherwise let it run as usual.
    private final Runnable keepAwake = new Runnable() {
        @Override public void run() {
            if (customView != null || audio.isMusicActive()) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            ui.postDelayed(this, AWAKE_POLL_MS);
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        navJs = readAsset("tvnav.js")
                .replace("__VIEWPORT__", String.valueOf(VIEWPORT_WIDTH))
                .replace("__CONTENT__", String.valueOf(CONTENT_WIDTH));

        WebView.setWebContentsDebuggingEnabled(true);
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(9, 9, 11));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        // Paint a margin of page beyond the screen ahead of time, so scrolling never shows blank
        // tiles. Costs memory, which a TV that only runs this app has plenty of.
        s.setOffscreenPreRaster(true);
        web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        // Some embedded players refuse to run in an app WebView; look like Chrome instead.
        s.setUserAgentString(s.getUserAgentString().replace("; wv)", ")").replace(" Version/4.0", ""));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());

        cursor = new CursorView(this, this);

        message = new TextView(this);
        message.setTextColor(Color.WHITE);
        message.setTextSize(22);
        message.setGravity(Gravity.CENTER);
        message.setBackgroundColor(Color.rgb(9, 9, 11));
        message.setText("Couldn't reach frameXTV.\nCheck the TV's internet connection, then press OK to try again.");
        message.setVisibility(View.GONE);

        root = new FrameLayout(this);
        root.addView(web, match());
        root.addView(message, match());
        root.addView(cursor, match());
        setContentView(root);
        web.requestFocus();
        // Hidden until the TV layout is applied, so the site doesn't visibly re-flow on launch.
        web.setAlpha(0f);
        ui.postDelayed(this::reveal, 6000);

        if (state == null || web.restoreState(state) == null) web.loadUrl(HOME);
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    // ---- Remote ----

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        int code = e.getKeyCode();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (down) {
                    if (e.getRepeatCount() == 0) {
                        okConsumed = false;
                        ui.postDelayed(okLongPress, LONG_PRESS_MS);
                    }
                } else {
                    ui.removeCallbacks(okLongPress);
                    if (!okConsumed) select();
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (cursor.isActive()) {
                    cursor.key(code, down);
                } else if (down && !loadFailed) {
                    long now = SystemClock.uptimeMillis();
                    // Dropping a repeat beats queueing it: the ring stops when you let go.
                    if (e.getRepeatCount() > 0 && navPending && now - navSentAt < NAV_STALL_MS) return true;
                    navPending = true;
                    navSentAt = now;
                    js("window.__tv&&__tv.move('" + direction(code) + "')", r -> navPending = false);
                }
                return true;

            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (down && e.getRepeatCount() == 0) back();
                return true;

            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (down) js("window.__tv&&__tv.media('" + mediaCommand(code) + "')", null);
                return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private static String mediaCommand(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_MEDIA_PLAY: return "play";
            case KeyEvent.KEYCODE_MEDIA_PAUSE: return "pause";
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: return "forward";
            case KeyEvent.KEYCODE_MEDIA_REWIND: return "rewind";
            default: return "toggle";
        }
    }

    private static String direction(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "down";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "left";
            default: return "right";
        }
    }

    private void select() {
        if (loadFailed) {
            message.setVisibility(View.GONE);
            loadFailed = false;
            web.reload();
            return;
        }
        if (cursor.isActive()) {
            cursor.tap();
            return;
        }
        js("window.__tv?__tv.select():null", result -> {
            if (result == null || !result.startsWith("{")) return;
            try {
                JSONObject r = new JSONObject(result);
                String type = r.optString("t");
                if ("frame".equals(type)) {
                    setPointerMode(true, (float) r.getDouble("x"), (float) r.getDouble("y"));
                } else if ("tap".equals(type)) {
                    cursor.tapAt((float) r.getDouble("x"), (float) r.getDouble("y"));
                } else if ("input".equals(type)) {
                    ui.postDelayed(() -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                            .showSoftInput(web, InputMethodManager.SHOW_IMPLICIT), 150);
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void back() {
        if (customView != null) {
            // The page goes first (Back cancels a seek, or leaves fullscreen itself); if it
            // doesn't take it, leave fullscreen from here.
            js("window.__tv?__tv.back():false", handled -> {
                if (!"true".equals(handled) && customCallback != null) customCallback.onCustomViewHidden();
            });
            return;
        }
        if (cursor.isActive()) {
            setPointerMode(false, 0, 0);
            return;
        }
        if (loadFailed) {
            finish();
            return;
        }
        js("window.__tv?__tv.back():false", handled -> {
            if ("true".equals(handled)) return;
            if (web.canGoBack()) {
                web.goBack();
            } else if (System.currentTimeMillis() - lastBack < EXIT_WINDOW_MS) {
                finish();
            } else {
                lastBack = System.currentTimeMillis();
                say("Press Back again to exit");
            }
        });
    }

    private void setPointerMode(boolean on, float x, float y) {
        if (on) {
            cursor.show(x, y);
            if (!pointerHintShown) {
                pointerHintShown = true;
                say("Pointer: arrows move, OK clicks, Back returns to the remote");
            }
        } else {
            float px = cursor.pointerX(), py = cursor.pointerY();
            cursor.hide();
            js("window.__tv&&__tv.focusAt(" + px + "," + py + ")", null);
        }
    }

    @Override
    public View target() {
        return customView != null ? customView : web;
    }

    @Override
    public void scrollAt(float x, float y, float dx, float dy) {
        if (customView == null) js("window.__tv&&__tv.scrollAt(" + x + "," + y + "," + dx + "," + dy + ")", null);
    }

    // ---- Web ----

    private void js(String code, android.webkit.ValueCallback<String> then) {
        web.evaluateJavascript(code, then);
    }

    private static boolean onSite(Uri uri) {
        String host = uri.getHost();
        return host != null && host.contains(SITE);
    }

    private final class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) return false;
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();
            if (("http".equals(scheme) || "https".equals(scheme)) && onSite(uri)) return false;
            // Players love to redirect the whole page to ads, or to app-store intents.
            if (uri.getHost() != null) say("Blocked a redirect to " + uri.getHost());
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            loadFailed = false;
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            // Give the new viewport width one frame or so to lay out before showing it.
            view.evaluateJavascript(navJs, r -> ui.postDelayed(MainActivity.this::reveal, 150));
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            view.evaluateJavascript(navJs, null);
            message.setVisibility(loadFailed ? View.VISIBLE : View.GONE);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) loadFailed = true;
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            recreate();
            return true;
        }
    }

    private final class Chrome extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) { callback.onCustomViewHidden(); return; }
            customView = view;
            customCallback = callback;
            root.addView(view, root.indexOfChild(cursor), match());
            web.setVisibility(View.INVISIBLE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        @Override
        public void onHideCustomView() {
            if (customView == null) return;
            root.removeView(customView);
            customView = null;
            customCallback = null;
            web.setVisibility(View.VISIBLE);
        }

        // Pop-ups: follow ones that stay on frameXTV in this window, drop the rest (ads).
        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
            if (!isUserGesture) return false;
            final WebView probe = new WebView(MainActivity.this);
            probe.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                    if (onSite(r.getUrl())) web.loadUrl(r.getUrl().toString());
                    ui.post(probe::destroy);
                    return true;
                }
            });
            ((WebView.WebViewTransport) resultMsg.obj).setWebView(probe);
            resultMsg.sendToTarget();
            return true;
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            request.deny();
        }

        // Hides WebView's grey play-button placeholder before a video starts.
        @Override
        public Bitmap getDefaultVideoPoster() {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }
    }

    // ---- Lifecycle ----

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        ui.post(keepAwake);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(keepAwake);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // A key-up can get lost while a dialog or the keyboard has focus; don't let the arrow drift.
        if (!hasFocus) cursor.release();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        root.removeAllViews();
        web.destroy();
        super.onDestroy();
    }

    // ---- Helpers ----

    private void reveal() {
        if (revealed) return;
        revealed = true;
        web.animate().alpha(1f).setDuration(250).start();
    }

    private void say(String text) {
        if (toast != null) toast.cancel();
        toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.show();
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
