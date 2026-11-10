package com.gtvdebloat.crossbar;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every tunable of the wallpaper, with the same names, ranges and defaults as the web version's
 * Spline and Particles panels (linkev/PlayStation-3-XMB, spline-settings.js and particles-settings.js),
 * plus wave and sparkle colors. Saved on the TV; the home screen and the screensaver share one copy.
 */
final class WaveSettings {
    static final String SPLINE = "Spline Controls", PARTICLES = "Particles Controls", COLORS = "Colors";

    /** One slider (or, with {@link #options}, a list to pick from). */
    static final class Param {
        final String key, label, section;
        final float min, max, step, def;
        final int decimals;
        final String[] options;
        volatile float v;

        Param(String key, String section, float min, float max, float step, float def, int decimals, String[] options) {
            this.key = key;
            this.label = humanize(key);
            this.section = section;
            this.min = min;
            this.max = max;
            this.step = step;
            this.def = def;
            this.decimals = decimals;
            this.options = options;
            this.v = def;
        }

        String display() {
            if (options != null) return options[Math.round(v)];
            return String.format(java.util.Locale.ROOT, "%." + decimals + "f", v);
        }
    }

    private static WaveSettings instance;

    private final List<Param> all = new ArrayList<>();
    private SharedPreferences prefs;

    // Spline Controls, in the web panel's order.
    final Param gradientPreset = select("gradientPreset", SPLINE, backgroundOptions(), 0);
    final Param colorR = p("colorR", SPLINE, 0, 255, 1, 37, 0);
    final Param colorG = p("colorG", SPLINE, 0, 255, 1, 89, 0);
    final Param colorB = p("colorB", SPLINE, 0, 255, 1, 179, 0);
    final Param gradientTopMul = p("gradientTopMul", SPLINE, 0, 0.3f, 0.005f, 0.09f, 3);
    final Param gradientBotMul = p("gradientBotMul", SPLINE, 0.2f, 1.2f, 0.005f, 0.62f, 3);
    final Param flowSpeed = p("flowSpeed", SPLINE, 0, 1.2f, 0.005f, 0.18f, 3);
    final Param tension = p("tension", SPLINE, 0, 0.5f, 0.005f, 0.12f, 3);
    final Param damping = p("damping", SPLINE, 0, 0.002f, 0.00001f, 0.0001f, 5);
    final Param length = p("length", SPLINE, 0.05f, 1.2f, 0.001f, 0.306001f, 3);
    final Param spacing = p("spacing", SPLINE, 10, 800, 1, 407.658f, 0);
    final Param timeStep = p("timeStep", SPLINE, 0.1f, 4, 0.05f, 1.0f, 2);
    final Param bandAmplitude = p("bandAmplitude", SPLINE, 0, 0.6f, 0.002f, 0.2f, 3);
    final Param bandSecondaryFreq = p("bandSecondaryFreq", SPLINE, 0.5f, 16, 0.1f, 7.0f, 1);
    final Param bandSecondaryAmp = p("bandSecondaryAmp", SPLINE, 0, 0.12f, 0.002f, 0.025f, 3);
    final Param travelSpeed1 = p("travelSpeed1", SPLINE, 0, 1.5f, 0.01f, 0.25f, 2);
    final Param travelAmp1 = p("travelAmp1", SPLINE, 0, 0.08f, 0.001f, 0.014f, 3);
    final Param travelSpeed2 = p("travelSpeed2", SPLINE, 0, 1.5f, 0.01f, 0.15f, 2);
    final Param travelAmp2 = p("travelAmp2", SPLINE, 0, 0.08f, 0.001f, 0.008f, 3);
    final Param perturbation = p("perturbation", SPLINE, 0, 0.3f, 0.001f, 0.0998587f, 3);
    final Param perturbationScale = p("perturbationScale", SPLINE, 0, 0.3f, 0.001f, 0.07f, 3);
    final Param waveCosAmp = p("waveCosAmp", SPLINE, 0, 0.3f, 0.001f, 0.09f, 3);
    final Param waveBias = p("waveBias", SPLINE, -0.3f, 0.3f, 0.001f, -0.1f, 3);
    final Param waveHeightScale = p("waveHeightScale", SPLINE, 0, 1, 0.005f, 0.5f, 3);
    final Param waveSoftClip = p("waveSoftClip", SPLINE, 0.05f, 0.5f, 0.005f, 0.22f, 3);
    final Param rePipelineBlend = p("rePipelineBlend", SPLINE, 0, 1, 0.01f, 0.45f, 2);
    final Param reDescriptorStrength = p("reDescriptorStrength", SPLINE, 0, 2, 0.01f, 0.7f, 2);
    final Param reSyntheticDescriptorSeed = p("reSyntheticDescriptorSeed", SPLINE, 0, 100000, 1, 1337, 0);
    final Param reSyntheticDescriptorMotion = p("reSyntheticDescriptorMotion", SPLINE, 0, 5, 0.05f, 0.65f, 2);
    final Param reKernelGain = p("reKernelGain", SPLINE, 0, 1, 0.005f, 0.04f, 3);
    final Param reNormalizeGain = p("reNormalizeGain", SPLINE, 0, 2, 0.01f, 0.08f, 2);
    final Param reKernelPhaseStep = p("reKernelPhaseStep", SPLINE, 0, 8, 0.05f, 0.45f, 2);
    final Param reIndexJitter = p("reIndexJitter", SPLINE, 0, 0.5f, 0.001f, 0.006f, 3);
    final Param reTemporalSmooth = p("reTemporalSmooth", SPLINE, 0, 0.98f, 0.01f, 0.84f, 2);
    final Param fresnelPower = p("fresnelPower", SPLINE, 0.2f, 8, 0.05f, 4.0f, 2);
    final Param fresnelScale = p("fresnelScale", SPLINE, 0, 2, 0.01f, 0.5f, 2);
    final Param opacity = p("opacity", SPLINE, 0, 1, 0.005f, 0.7f, 3);
    final Param brightness = p("brightness", SPLINE, 0, 2, 0.01f, 0.98f, 2);
    final Param zDetailScale = p("zDetailScale", SPLINE, 0, 0.25f, 0.001f, 0.08f, 3);
    final Param ffdScale1X = p("ffdScale1X", SPLINE, 0, 8, 0.01f, 5.67726f, 2);
    final Param ffdScale1Y = p("ffdScale1Y", SPLINE, 0, 3, 0.01f, 1.00077f, 2);
    final Param ffdScale1Z = p("ffdScale1Z", SPLINE, 0, 3, 0.01f, 1.0f, 2);
    final Param ffdScale2X = p("ffdScale2X", SPLINE, 0, 8, 0.01f, 2.82755f, 2);
    final Param ffdScale2Y = p("ffdScale2Y", SPLINE, 0, 3, 0.01f, 1.27579f, 2);
    final Param ffdScale2Z = p("ffdScale2Z", SPLINE, 0, 6, 0.01f, 2.88782f, 2);
    final Param ffdOffsetX = p("ffdOffsetX", SPLINE, -2, 2, 0.01f, 0.0f, 2);
    final Param ffdOffsetY = p("ffdOffsetY", SPLINE, -2, 2, 0.01f, -0.469999f, 2);
    final Param ffdOffsetZ = p("ffdOffsetZ", SPLINE, -2, 2, 0.01f, 0.0f, 2);
    final Param ffdYAmp = p("ffdYAmp", SPLINE, 0, 0.3f, 0.001f, 0.05f, 3);
    final Param ffdZAmp = p("ffdZAmp", SPLINE, 0, 0.3f, 0.001f, 0.06f, 3);

    // Particles Controls.
    final Param count = p("count", PARTICLES, 10, WaveView.MAX_PARTICLES, 10, 2000, 0);
    final Param particleOpacity = p("opacity", PARTICLES, 0, 1, 0.01f, 0.75f, 2);
    final Param sizeBase = p("sizeBase", PARTICLES, 1, 40, 0.1f, 2.6f, 1);
    final Param sizeVar = p("sizeVar", PARTICLES, 0, 50, 0.1f, 1.5f, 1);
    final Param particleFlowSpeed = p("flowSpeed", PARTICLES, 0, 3, 0.01f, 0.18f, 2);

    // Colors (not in the web version).
    final Param waveR = p("waveRed", COLORS, 0, 255, 1, 255, 0);
    final Param waveG = p("waveGreen", COLORS, 0, 255, 1, 255, 0);
    final Param waveB = p("waveBlue", COLORS, 0, 255, 1, 255, 0);
    final Param sparkleR = p("sparkleRed", COLORS, 0, 255, 1, 255, 0);
    final Param sparkleG = p("sparkleGreen", COLORS, 0, 255, 1, 255, 0);
    final Param sparkleB = p("sparkleBlue", COLORS, 0, 255, 1, 255, 0);

    private WaveSettings() {}

    static synchronized WaveSettings get(Context c) {
        if (instance == null) {
            instance = new WaveSettings();
            instance.prefs = c.getApplicationContext().getSharedPreferences("crossbar_wave", Context.MODE_PRIVATE);
            for (Param p : instance.all) {
                p.v = Math.max(p.min, Math.min(p.max, instance.prefs.getFloat(prefKey(p), p.def)));
            }
        }
        return instance;
    }

    List<Param> all() {
        return Collections.unmodifiableList(all);
    }

    void set(Param p, float value) {
        float v = Math.max(p.min, Math.min(p.max, value));
        if (p.options == null) v = Math.round(v / p.step) * p.step;
        p.v = Math.max(p.min, Math.min(p.max, v));
        prefs.edit().putFloat(prefKey(p), p.v).apply();
    }

    void resetAll() {
        SharedPreferences.Editor e = prefs.edit();
        for (Param p : all) {
            p.v = p.def;
            e.remove(prefKey(p));
        }
        e.apply();
    }

    /** The Custom theme's background (Settings → Theme → Custom). Same format as {@link Themes#gradient}. */
    float[] customGradient() {
        int preset = Math.round(gradientPreset.v);
        if (preset == 0) {
            // The web version's "Original (RGB Sliders)": darker at the top, lighter at the bottom.
            float r = colorR.v / 255f, g = colorG.v / 255f, b = colorB.v / 255f;
            float top = gradientTopMul.v, bot = gradientBotMul.v;
            return new float[]{r * top, g * top, Math.min(1, b * top * 1.2f), r * bot, g * bot, b * bot, 90};
        }
        int month = (preset - 1) / 2;
        return Themes.monthGradient(month, (preset - 1) % 2 == 0);
    }

    /** Whether this setting shapes the background, which only the Custom theme shows. */
    boolean isBackground(Param p) {
        return p == gradientPreset || p == colorR || p == colorG || p == colorB
                || p == gradientTopMul || p == gradientBotMul;
    }

    private static String[] backgroundOptions() {
        String[] names = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        String[] out = new String[1 + 24];
        out[0] = "Original (RGB Sliders)";
        for (int m = 0; m < 12; m++) {
            out[1 + m * 2] = names[m] + " (Day)";
            out[2 + m * 2] = names[m] + " (Night)";
        }
        return out;
    }

    private Param p(String key, String section, float min, float max, float step, float def, int decimals) {
        Param p = new Param(key, section, min, max, step, def, decimals, null);
        all.add(p);
        return p;
    }

    private Param select(String key, String section, String[] options, int def) {
        Param p = new Param(key, section, 0, options.length - 1, 1, def, 0, options);
        all.add(p);
        return p;
    }

    private static String prefKey(Param p) {
        return p.section + "/" + p.key;
    }

    /** "ffdScale1X" → "Ffd Scale1 X", the way the web panel labels its sliders. */
    private static String humanize(String key) {
        return Character.toUpperCase(key.charAt(0)) + key.substring(1).replaceAll("([A-Z])", " $1");
    }
}
