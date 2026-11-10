package com.gtvdebloat.crossbar;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;

import java.util.List;

/**
 * Notification access. Android requires it to read what's playing, which the screensaver shows.
 * Nothing is stored or sent anywhere.
 */
public final class NotifListener extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        // Android keeps this service running, so after a restart it picks up a paused Home helper.
        HelperPause.recover(this);
    }

    /** The media session to show: the one playing, else the most recent one with a title. */
    static MediaController nowPlaying(Context c) {
        try {
            List<MediaController> list = c.getSystemService(MediaSessionManager.class)
                    .getActiveSessions(new ComponentName(c, NotifListener.class));
            MediaController fallback = null;
            for (MediaController mc : list) {
                if (title(mc).isEmpty()) continue;
                PlaybackState s = mc.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) return mc;
                if (fallback == null) fallback = mc;
            }
            return fallback;
        } catch (SecurityException e) {
            return null; // notification access not granted
        }
    }

    static String title(MediaController mc) {
        MediaMetadata m = mc.getMetadata();
        CharSequence t = m == null ? null : m.getText(MediaMetadata.METADATA_KEY_TITLE);
        return t == null ? "" : t.toString();
    }

    static String artist(MediaController mc) {
        MediaMetadata m = mc.getMetadata();
        if (m == null) return "";
        CharSequence a = m.getText(MediaMetadata.METADATA_KEY_ARTIST);
        if (a == null) a = m.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        return a == null ? "" : a.toString();
    }

    static android.graphics.Bitmap art(MediaController mc) {
        MediaMetadata m = mc.getMetadata();
        if (m == null) return null;
        android.graphics.Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        return b;
    }

    static boolean isPlaying(MediaController mc) {
        PlaybackState s = mc.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }
}
