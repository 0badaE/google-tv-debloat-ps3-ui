package com.gtvdebloat.crossbar;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Current weather for where the TV is, from Open-Meteo (free, no account). The TV's network
 * location is rounded to about 1 km before it leaves the device; nothing else is sent.
 */
final class Weather {
    private static final String TAG = "Crossbar";
    private static final long MAX_AGE_MS = 30 * 60_000L;

    final float temp;
    final int code;
    final boolean day;
    final boolean fahrenheit;

    private Weather(float temp, int code, boolean day, boolean fahrenheit) {
        this.temp = temp;
        this.code = code;
        this.day = day;
        this.fahrenheit = fahrenheit;
    }

    String temperature() {
        return Math.round(temp) + "°";
    }

    String condition() {
        if (code == 0) return day ? "Sunny" : "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67 || code >= 80 && code <= 82) return "Rain";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "Snow";
        if (code >= 95) return "Thunderstorms";
        return "";
    }

    int glyph() {
        if (code == 0) return day ? Glyphs.SUN : Glyphs.MOON;
        if (code <= 2) return day ? Glyphs.PARTLY : Glyphs.CLOUD;
        if (code == 3) return Glyphs.CLOUD;
        if (code == 45 || code == 48) return Glyphs.FOG;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return Glyphs.SNOW;
        if (code >= 95) return Glyphs.STORM;
        return Glyphs.RAIN;
    }

    /** The last fetched weather, or null if there's none yet. */
    static Weather cached(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.contains("w_time")) return null;
        return new Weather(p.getFloat("w_temp", 0), p.getInt("w_code", 0), p.getBoolean("w_day", true),
                p.getBoolean("w_f", false));
    }

    /** Fetches fresh weather on a background thread if the cache is old, then runs {@code done}. */
    static void refresh(Context c, Runnable done) {
        Context app = c.getApplicationContext();
        if (System.currentTimeMillis() - prefs(app).getLong("w_time", 0) < MAX_AGE_MS) return;
        new Thread(() -> {
            if (fetch(app)) done.run();
        }, "weather").start();
    }

    private static boolean fetch(Context c) {
        Location loc = location(c);
        if (loc == null) return false;
        boolean f = "US".equals(Locale.getDefault().getCountry());
        String url = String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                        + "&current=temperature_2m,weather_code,is_day&temperature_unit=%s",
                loc.getLatitude(), loc.getLongitude(), f ? "fahrenheit" : "celsius");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[4096];
                for (int n; (n = in.read(buf)) > 0; ) body.write(buf, 0, n);
            }
            JSONObject cur = new JSONObject(body.toString("UTF-8")).getJSONObject("current");
            prefs(c).edit()
                    .putFloat("w_temp", (float) cur.getDouble("temperature_2m"))
                    .putInt("w_code", cur.getInt("weather_code"))
                    .putBoolean("w_day", cur.getInt("is_day") == 1)
                    .putBoolean("w_f", f)
                    .putLong("w_time", System.currentTimeMillis())
                    .apply();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Weather update failed", e);
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static Location location(Context c) {
        if (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        LocationManager lm = c.getSystemService(LocationManager.class);
        for (String provider : new String[]{LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null) return l;
            } catch (IllegalArgumentException | SecurityException ignored) {
                // Provider missing on this device.
            }
        }
        return null;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("crossbar", Context.MODE_PRIVATE);
    }
}
