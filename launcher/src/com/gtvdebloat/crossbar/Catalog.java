package com.gtvdebloat.crossbar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the categories and their items from what is installed on the TV. */
final class Catalog {
    static final int BANNER_W = 256, BANNER_H = 144, GLYPH_PX = 140, CAT_GLYPH_PX = 150;

    static final String SETTINGS = "settings", TV = "tv", VIDEO = "video", MUSIC = "music", APPS = "apps";

    static final String LAUNCHER_X = "com.google.android.apps.tv.launcherx";
    private static final String TV_SETTINGS = "com.android.tv.settings";
    private static final String PLAY_STORE = "com.android.vending";

    // Apps placed by package name, in the order they appear in their column.
    private static final String[] KNOWN_TV = {
            "tv.pluto.android", "com.google.android.youtube.tvunplugged", "com.sling", "com.fubo.firetv.screen",
            "com.philo.philo", "com.xumo.xumo.tv", "com.plexapp.android",
    };
    private static final String[] KNOWN_VIDEO = {
            "com.netflix.ninja", "com.google.android.youtube.tv", "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom", "com.wbd.stream", "com.hulu.livingroomplus",
            "com.apple.atve.androidtv.appletv", "com.peacocktv.peacockandroid", "com.cbs.ott", "com.tubitv",
            "org.xbmc.kodi",
    };
    private static final String[] KNOWN_MUSIC = {
            "com.spotify.tv.android", "tunein.player", "com.google.android.youtube.tvmusic",
            "com.amazon.music.tv", "com.apple.android.music", "com.pandora.android.atv",
            "com.aspiro.tidal", "deezer.android.tv", "com.soundcloud.android",
    };
    // Never shown as apps: they appear as their own items, or aren't useful from Home.
    private static final Set<String> HIDDEN = new HashSet<>(Arrays.asList(
            TV_SETTINGS, LAUNCHER_X, "com.google.android.katniss", "com.google.android.tvlauncher"));

    static final class Item {
        final String key;
        final String title;
        final String subtitle;
        final Bitmap image;
        final boolean glyph;
        final Intent intent;
        final String panel;

        Item(String key, String title, String subtitle, Bitmap image, boolean glyph, Intent intent, String panel) {
            this.key = key;
            this.title = title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.image = image;
            this.glyph = glyph;
            this.intent = intent;
            this.panel = panel;
        }
    }

    static final class Category {
        final String id;
        final String label;
        final Bitmap glyph;
        final List<Item> items = new ArrayList<>();

        Category(String id, String label, Bitmap glyph) {
            this.id = id;
            this.label = label;
            this.glyph = glyph;
        }

        int indexOf(String key) {
            for (int i = 0; i < items.size(); i++) if (items.get(i).key.equals(key)) return i;
            return -1;
        }
    }

    private final Context context;
    private final PackageManager pm;
    private final Map<Integer, Bitmap> glyphCache = new HashMap<>();

    Catalog(Context context) {
        this.context = context;
        this.pm = context.getPackageManager();
    }

    List<Category> build() {
        Category settings = new Category(SETTINGS, "Settings", glyph(Glyphs.TOOLBOX, CAT_GLYPH_PX));
        Category tv = new Category(TV, "TV", glyph(Glyphs.TV, CAT_GLYPH_PX));
        Category video = new Category(VIDEO, "Video", glyph(Glyphs.FILM, CAT_GLYPH_PX));
        Category music = new Category(MUSIC, "Music", glyph(Glyphs.MUSIC, CAT_GLYPH_PX));
        Category apps = new Category(APPS, "Apps", glyph(Glyphs.APPS, CAT_GLYPH_PX));

        addSettings(settings);

        Map<String, ResolveInfo> launchable = new HashMap<>();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (!pkg.equals(context.getPackageName()) && !HIDDEN.contains(pkg) && !launchable.containsKey(pkg)) {
                launchable.put(pkg, ri);
            }
        }

        addKnown(tv, KNOWN_TV, launchable);
        if (!launchable.containsKey("tv.pluto.android") && !installed("tv.pluto.android")) {
            tv.items.add(getItem("tv.pluto.android", "Get Pluto TV", "Hundreds of free live channels"));
        }
        if (installed(LAUNCHER_X)) {
            tv.items.add(glyphItem("tv:googletv", "Google TV", "Recommendations, Continue watching, profiles", Glyphs.HOME,
                    new Intent(Intent.ACTION_MAIN).setComponent(
                            new ComponentName(LAUNCHER_X, LAUNCHER_X + ".home.HomeActivity"))));
        }
        tv.items.add(glyphItem("tv:search", "Search", "Or hold the mic button on your remote", Glyphs.MIC,
                new Intent(Intent.ACTION_ASSIST)));

        addKnown(video, KNOWN_VIDEO, launchable);
        addKnown(music, KNOWN_MUSIC, launchable);
        if (!launchable.containsKey("tunein.player") && !installed("tunein.player")) {
            music.items.add(getItem("tunein.player", "Get TuneIn", "Live radio, news and sports"));
        }

        // Everything else is placed by the category the app declares.
        List<ResolveInfo> rest = new ArrayList<>(launchable.values());
        rest.sort((a, b) -> label(a).compareToIgnoreCase(label(b)));
        Item store = null;
        for (ResolveInfo ri : rest) {
            Item item = appItem(ri);
            if (ri.activityInfo.packageName.equals(PLAY_STORE)) {
                store = item;
                continue;
            }
            int cat = ri.activityInfo.applicationInfo.category;
            if (cat == ApplicationInfo.CATEGORY_VIDEO || cat == ApplicationInfo.CATEGORY_IMAGE) video.items.add(item);
            else if (cat == ApplicationInfo.CATEGORY_AUDIO) music.items.add(item);
            else apps.items.add(item);
        }
        if (store != null) apps.items.add(0, store);

        List<Category> out = new ArrayList<>();
        out.add(settings);
        out.add(tv);
        out.add(video);
        out.add(music);
        if (!apps.items.isEmpty()) out.add(apps);
        return out;
    }

    private void addSettings(Category c) {
        c.items.add(glyphItem("set:theme", "Theme", "Wallpaper colors", Glyphs.PALETTE, null, "theme"));
        c.items.add(glyphItem("set:wave", "Wave Controls", "Every wallpaper setting, as sliders", Glyphs.SLIDERS,
                null, "wave"));
        c.items.add(glyphItem("set:sounds", "Sounds", "Navigation and startup sounds", Glyphs.SPEAKER, null, "sounds"));
        c.items.addAll(settingsShortcuts());
    }

    /** Shortcuts into the TV's own settings that this TV can open. */
    private List<Item> settingsShortcuts() {
        List<Item> out = new ArrayList<>();
        out.add(glyphItem("set:display", "Display & Sound", "Picture, resolution, audio output", Glyphs.DISPLAY,
                new Intent("android.settings.SOUND_SETTINGS")));
        out.add(glyphItem("set:network", "Network & Internet", "Wi-Fi and Ethernet", Glyphs.WIFI,
                new Intent("android.settings.WIFI_SETTINGS")));
        out.add(glyphItem("set:remote", "Pair Remote or Accessory", "Bluetooth remotes, headphones, gamepads",
                Glyphs.REMOTE, new Intent("com.google.android.intent.action.CONNECT_INPUT")));
        out.add(glyphItem("set:ambient", "Ambient Mode", "Screensaver", Glyphs.AMBIENT,
                new Intent("com.google.android.tv.settings.ambient")));
        out.add(glyphItem("set:date", "Date & Time", "", Glyphs.CLOCK, new Intent("android.settings.DATE_SETTINGS")));
        out.add(glyphItem("set:sleep", "Screensaver", "Start it now", Glyphs.MOON,
                new Intent("com.google.android.pano.action.SLEEP")));
        out.add(glyphItem("set:all", "All Settings", "", Glyphs.GEAR, new Intent("android.settings.SETTINGS")));
        for (int i = out.size() - 1; i >= 0; i--) {
            if (pm.resolveActivity(out.get(i).intent, 0) == null) out.remove(i);
        }
        return out;
    }

    private void addKnown(Category c, String[] known, Map<String, ResolveInfo> launchable) {
        for (String pkg : known) {
            ResolveInfo ri = launchable.remove(pkg);
            if (ri != null) c.items.add(appItem(ri));
        }
    }

    private boolean installed(String pkg) {
        try {
            return pm.getApplicationInfo(pkg, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String label(ResolveInfo ri) {
        CharSequence l = ri.loadLabel(pm);
        return l == null ? ri.activityInfo.packageName : l.toString();
    }

    private Item appItem(ResolveInfo ri) {
        String pkg = ri.activityInfo.packageName;
        Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(new ComponentName(pkg, ri.activityInfo.name));
        return new Item("app:" + pkg, label(ri), "", banner(ri), false, launch, null);
    }

    private Item getItem(String pkg, String title, String subtitle) {
        Intent store = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)).setPackage(PLAY_STORE);
        return glyphItem("get:" + pkg, title, subtitle, Glyphs.DOWNLOAD, store);
    }

    private Item glyphItem(String key, String title, String subtitle, int kind, Intent intent) {
        return glyphItem(key, title, subtitle, kind, intent, null);
    }

    private Item glyphItem(String key, String title, String subtitle, int kind, Intent intent, String panel) {
        return new Item(key, title, subtitle, glyph(kind, GLYPH_PX), true, intent, panel);
    }

    private Bitmap glyph(int kind, int px) {
        int k = kind * 1000 + px;
        Bitmap b = glyphCache.get(k);
        if (b == null) {
            b = Glyphs.render(kind, px);
            glyphCache.put(k, b);
        }
        return b;
    }

    /** The app's TV banner with rounded corners, or its icon on a frosted card when it has no banner. */
    private Bitmap banner(ResolveInfo ri) {
        Drawable d = ri.activityInfo.loadBanner(pm);
        if (d == null) d = ri.activityInfo.applicationInfo.loadBanner(pm);
        Bitmap flat = Bitmap.createBitmap(BANNER_W, BANNER_H, Bitmap.Config.ARGB_8888);
        Canvas fc = new Canvas(flat);
        if (d != null) {
            d.setBounds(0, 0, BANNER_W, BANNER_H);
            d.draw(fc);
        } else {
            fc.drawColor(0x40FFFFFF);
            Drawable icon = ri.loadIcon(pm);
            int s = 96;
            icon.setBounds((BANNER_W - s) / 2, (BANNER_H - s) / 2, (BANNER_W + s) / 2, (BANNER_H + s) / 2);
            icon.draw(fc);
        }
        Bitmap out = Bitmap.createBitmap(BANNER_W, BANNER_H, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(flat, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(out).drawRoundRect(new RectF(0, 0, BANNER_W, BANNER_H), 14, 14, p);
        flat.recycle();
        return out;
    }
}
