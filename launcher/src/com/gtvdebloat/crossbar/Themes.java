package com.gtvdebloat.crossbar;

import java.util.Calendar;

/**
 * Background gradients. A gradient is {startR, startG, startB, endR, endG, endB, angleDeg},
 * colors 0..1, angle in degrees with y pointing down (90 = top to bottom).
 */
final class Themes {
    static final String MONTHLY = "monthly";
    /** The background set in Settings → Wave Controls. */
    static final String CUSTOM = "custom";

    static final String[] IDS = {MONTHLY, "purple", "blue", "black", "pink", CUSTOM};
    static final String[] NAMES = {"Monthly", "Purple", "Classic Blue", "Black", "Pink", "Custom"};

    private static final int[][] FIXED = {
            {0, 0, 0, 0, 0, 0, 0},
            {74, 50, 128, 150, 96, 178, 112},
            {24, 70, 150, 2, 6, 24, 90},
            {44, 44, 52, 0, 0, 0, 90},
            {214, 116, 168, 46, 8, 34, 100},
    };

    // PS3 month colors, extracted by linkev/PlayStation-3-XMB (MIT) from the console's gradient files.
    private static final int[][] DAY = {
            {197, 197, 197, 201, 201, 201, 90},
            {203, 158, 13, 219, 214, 41, 67},
            {142, 190, 40, 104, 168, 22, 106},
            {216, 182, 182, 231, 66, 117, 137},
            {19, 108, 19, 24, 156, 24, 2},
            {198, 120, 238, 103, 77, 161, 149},
            {0, 167, 146, 10, 240, 239, 27},
            {0, 0, 95, 33, 217, 255, 63},
            {146, 44, 155, 217, 98, 236, 149},
            {227, 151, 15, 224, 187, 2, 129},
            {115, 68, 20, 154, 118, 47, 90},
            {236, 68, 45, 214, 63, 43, 171},
    };
    private static final int[][] NIGHT = {
            {181, 181, 181, 0, 0, 0, 90},
            {198, 188, 128, 0, 0, 0, 94},
            {152, 170, 113, 0, 0, 0, 90},
            {212, 174, 182, 10, 8, 8, 90},
            {48, 118, 48, 11, 3, 11, 116},
            {209, 163, 225, 0, 0, 0, 91},
            {16, 129, 124, 17, 0, 0, 110},
            {20, 159, 176, 0, 0, 31, 70},
            {116, 0, 153, 12, 0, 11, 51},
            {216, 142, 0, 0, 0, 0, 90},
            {131, 86, 32, 18, 20, 17, 90},
            {157, 59, 44, 0, 0, 3, 118},
    };

    private Themes() {}

    static int indexOf(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return 0;
    }

    /** Swatch color for a gradient, shown next to the theme name in Settings. */
    static int dotColor(float[] g) {
        float r = (g[0] + g[3]) * 0.5f, gg = (g[1] + g[4]) * 0.5f, b = (g[2] + g[5]) * 0.5f;
        float m = Math.max(r, Math.max(gg, b));
        float lift = m < 0.35f ? 0.35f / Math.max(m, 0.01f) : 1f;
        return 0xFF000000 | ((int) Math.min(255, r * lift * 255) << 16)
                | ((int) Math.min(255, gg * lift * 255) << 8) | (int) Math.min(255, b * lift * 255);
    }

    /** The theme's gradient right now; Custom comes from {@link WaveSettings#customGradient}. */
    static float[] gradient(String id, Calendar now, WaveSettings custom) {
        if (CUSTOM.equals(id)) return custom.customGradient();
        int i = indexOf(id);
        if (i != 0) return toFloats(FIXED[i]);
        int month = now.get(Calendar.MONTH);
        float day = daylight(now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60f);
        float[] d = toFloats(DAY[month]);
        float[] n = toFloats(NIGHT[month]);
        float[] out = new float[7];
        for (int k = 0; k < 7; k++) out[k] = n[k] + (d[k] - n[k]) * day;
        // The day gradients are very bright; keep white text readable on them.
        for (int k = 0; k < 6; k++) out[k] *= 1f - 0.22f * day;
        return out;
    }

    /** A PS3 month color exactly as the console had it (month 0 = January). */
    static float[] monthGradient(int month, boolean day) {
        return toFloats(day ? DAY[month] : NIGHT[month]);
    }

    /** 0 at night, 1 in the middle of the day, with soft dawn and dusk ramps. */
    static float daylight(float hour) {
        if (hour < 6 || hour >= 18.5f) return 0;
        if (hour < 8.5f) return smooth((hour - 6) / 2.5f);
        if (hour < 16) return 1;
        return 1 - smooth((hour - 16) / 2.5f);
    }

    private static float smooth(float t) { return t * t * (3 - 2 * t); }

    private static float[] toFloats(int[] c) {
        return new float[]{c[0] / 255f, c[1] / 255f, c[2] / 255f, c[3] / 255f, c[4] / 255f, c[5] / 255f, c[6]};
    }
}
