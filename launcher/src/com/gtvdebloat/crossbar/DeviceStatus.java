package com.gtvdebloat.crossbar;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;

/** Small read-only facts about the TV for the screensaver. */
final class DeviceStatus {
    private DeviceStatus() {}

    static String deviceName(Context c) {
        String n = Settings.Global.getString(c.getContentResolver(), Settings.Global.DEVICE_NAME);
        return n == null || n.isEmpty() ? Build.MODEL : n;
    }

    /** Wi-Fi network name, "Ethernet", or "Offline". Android hides the name without location access. */
    @SuppressWarnings("deprecation")
    static String network(Context c) {
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (caps == null) return "Offline";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Connected";
        WifiInfo info = c.getSystemService(WifiManager.class).getConnectionInfo();
        String ssid = info == null ? null : info.getSSID();
        if (ssid == null || ssid.equals(WifiManager.UNKNOWN_SSID)) return "Wi-Fi";
        return ssid.replaceAll("^\"|\"$", "");
    }
}
