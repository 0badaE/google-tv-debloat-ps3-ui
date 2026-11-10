package com.gtvdebloat.crossbar;

/**
 * CPU side of the wave: writes a 256x64 displacement field every frame, which the wave
 * vertex shader samples. Ported from linkev/PlayStation-3-XMB (MIT), ps3xmbwave/spline-reverse.js.
 * Its knobs live in {@link WaveSettings} and can change while it runs.
 */
final class SplineField {
    static final int W = 256;
    static final int H = 64;

    private static final int TABLE = 0x169;
    private static final int ITERS = 8;
    private static final int B380 = 0x2200;
    private static final float[] NORM_A = {0.39584f, -0.0052389996f, -0.58664495f, 0.189007f};
    private static final float[] NORM_B = {-0.003751f, -0.57536095f, 0.161975f, 0.417137f};
    private static final int[] R37 = {0x00, 0x11, 0x22, 0x33};
    private static final int CP = 28;

    final float[] data = new float[W * H];

    private final WaveSettings s;
    private final int[] b380 = new int[B380];
    private int b380Seed = Integer.MIN_VALUE;
    private final float[] b300 = new float[4];
    private final float[] coeffs = new float[TABLE * 16];
    private final float[] table = new float[TABLE * 4];
    private final float[] packed = new float[ITERS * 8 * 4];
    private final float[] packedPrev = new float[ITERS * 8 * 4];
    private final float[] cp = new float[CP];
    private final float[][] v = new float[4][4];
    private final float[][] stores = new float[8][4];
    private final double[] idx = new double[4];

    SplineField(WaveSettings settings) {
        this.s = settings;
    }

    void update(double t) {
        buildDescriptors();
        b300[0] = s.damping.v;
        b300[1] = s.length.v;
        b300[2] = s.tension.v;
        b300[3] = s.spacing.v * 0.001f;
        decodeCoefficients(t);
        buildTable();
        runKernel(t);
        writeField(t);
    }

    /** Synthetic descriptor bytes; like the web version, rebuilt only when the seed changes. */
    private void buildDescriptors() {
        int seed = Math.round(s.reSyntheticDescriptorSeed.v);
        if (seed == b380Seed) return;
        b380Seed = seed;
        float length = s.length.v, tension = s.tension.v, pScale = s.perturbationScale.v;
        float strength = s.reDescriptorStrength.v;
        for (int i = 0; i < B380; i++) {
            double f = (double) i / B380;
            double wave = Math.sin((f * 97.0) * (0.3 + length)) * 0.5
                    + Math.sin((f * 211.0) * (0.15 + tension * 3.0)) * 0.3
                    + Math.sin((f * 17.0) * (0.2 + pScale * 2.0)) * 0.2;
            double noise = hash01(i * 13.37 + seed * 0.01) * 2.0 - 1.0;
            double val = clamp01((wave * strength + noise * 0.35 + 1.0) * 0.5);
            b380[i] = (int) (val * 255);
        }
    }

    private void decodeCoefficients(double t) {
        double phase = t * s.flowSpeed.v * s.timeStep.v * s.reSyntheticDescriptorMotion.v;
        for (int entry = 0; entry < TABLE; entry++) {
            int entryBase = entry * 16;
            int descBase = (entry * 0x130) % B380;
            for (int blk = 0; blk < 4; blk++) {
                for (int lane = 0; lane < 4; lane++) {
                    int byteIdx = (descBase + blk * 0x10 + lane * 4 + (entry % 7)) % B380;
                    double centered = b380[byteIdx] / 255.0 * 2 - 1;
                    double harmonic = Math.sin(entry * 0.07 + blk * 0.91 + lane * 1.37 + phase * 0.23);
                    coeffs[entryBase + blk * 4 + lane] = (float) (centered * 0.75 + harmonic * 0.25);
                }
            }
        }
    }

    private void buildTable() {
        float gain = s.reNormalizeGain.v;
        for (int i = 0; i < TABLE; i++) {
            int c = i * 16;
            for (int lane = 0; lane < 4; lane++) {
                double raw = coeffs[c + lane] * b300[0] + coeffs[c + 4 + lane] * b300[1]
                        + coeffs[c + 8 + lane] * b300[2] + coeffs[c + 12 + lane] * b300[3];
                double denomAbs = Math.max(Math.abs(NORM_B[lane]), 0.05);
                double denom = NORM_B[lane] < 0 ? -denomAbs : denomAbs;
                table[i * 4 + lane] = (float) Math.tanh((raw - NORM_A[lane]) / denom * gain);
            }
        }
    }

    private void runKernel(double t) {
        float phaseStep = s.reKernelPhaseStep.v, jitter = s.reIndexJitter.v;
        for (int iter = 0; iter < ITERS; iter++) {
            double phase = t * phaseStep + iter * 0.37;
            for (int lane = 0; lane < 4; lane++) {
                int word = (R37[lane] + iter * 0x13) & 0xff;
                int baseIdx = (19 * ((word >> 4) & 0xff) + (word & 0xf)) % TABLE;
                double offset = Math.sin(phase + lane * 0.77) * jitter * TABLE;
                double w = (baseIdx + offset) % TABLE;
                idx[lane] = w < 0 ? w + TABLE : w;
            }
            for (int k = 0; k < 4; k++) sample(idx[k], v[k]);

            float mixA = (float) (0.5 + 0.5 * Math.sin(phase * 0.7));
            float mixB = (float) (0.5 + 0.5 * Math.cos(phase * 0.9));
            for (int lane = 0; lane < 4; lane++) {
                float v0 = v[0][lane], v1 = v[1][lane], v2 = v[2][lane], v3 = v[3][lane];
                stores[0][lane] = v0 + (v1 - v0) * mixA;
                stores[1][lane] = v1 + (v2 - v1) * mixB;
                stores[2][lane] = v2 + (v3 - v2) * mixA;
                stores[3][lane] = v3 + (v0 - v3) * mixB;
                stores[4][lane] = v1 - v0;
                stores[5][lane] = v2 - v1;
                stores[6][lane] = v3 - v2;
                stores[7][lane] = v0 - v3;
            }
            for (int st = 0; st < 8; st++) {
                System.arraycopy(stores[st], 0, packed, (iter * 8 + st) * 4, 4);
            }
        }
        float smooth = Math.max(0, Math.min(0.999f, s.reTemporalSmooth.v));
        for (int i = 0; i < packed.length; i++) {
            float smoothed = packedPrev[i] * smooth + packed[i] * (1 - smooth);
            packed[i] = smoothed;
            packedPrev[i] = smoothed;
        }
    }

    private void sample(double idxF, float[] out) {
        int i0 = ((int) Math.floor(idxF)) % TABLE;
        int i1 = (i0 + 1) % TABLE;
        float t = (float) (idxF - Math.floor(idxF));
        for (int k = 0; k < 4; k++) out[k] = table[i0 * 4 + k] * (1 - t) + table[i1 * 4 + k] * t;
    }

    private void writeField(double t) {
        int vecCount = ITERS * 8;
        double flow = t * s.flowSpeed.v * s.timeStep.v;
        float kernelGain = s.reKernelGain.v, bandAmp = s.bandAmplitude.v, band2Freq = s.bandSecondaryFreq.v;
        float band2Amp = s.bandSecondaryAmp.v, speed1 = s.travelSpeed1.v, amp1 = s.travelAmp1.v;
        float speed2 = s.travelSpeed2.v, amp2 = s.travelAmp2.v, tension = s.tension.v;
        float pert = s.perturbation.v * s.perturbationScale.v, length = s.length.v, spacing = s.spacing.v;
        float blend = s.rePipelineBlend.v;
        for (int row = 0; row < H; row++) {
            double z = (double) row / (H - 1) * 2 - 1;
            double rowPhase = flow * 0.25 + z * 1.7;
            for (int i = 0; i < CP; i++) {
                double x = (double) i / (CP - 1);
                double kvf = row * 0.93 + i * 0.61 + flow * 0.35;
                int kv0 = Math.floorMod((int) Math.floor(kvf), vecCount);
                int kv1 = (kv0 + 1) % vecCount;
                float kt = (float) (kvf - Math.floor(kvf));
                int k0 = kv0 * 4, k1 = kv1 * 4;
                float kx = packed[k0] * (1 - kt) + packed[k1] * kt;
                float ky = packed[k0 + 1] * (1 - kt) + packed[k1 + 1] * kt;
                float kz = packed[k0 + 2] * (1 - kt) + packed[k1 + 2] * kt;
                float kw = packed[k0 + 3] * (1 - kt) + packed[k1 + 3] * kt;

                double reCore = (kx * 0.45 + ky * 0.25 + kz * 0.2 + kw * 0.1) * kernelGain
                        + Math.sin(rowPhase + x * 6.2) * bandAmp
                        + Math.cos(z * band2Freq + x * 4.8 + flow * 0.09) * band2Amp;
                double legacy = Math.sin((x * Math.PI * 1.3 + z * 0.8) - flow * speed1) * amp1 * tension
                        + Math.sin((x * Math.PI * 2.8 - z * 1.2) + flow * speed2) * amp2
                        + pert * Math.sin((x * (4.0 + length * 2.0) + z * 4.0 - flow * 0.6) * (spacing * 0.01));
                cp[i] = (float) (reCore * blend + legacy * (1.0 - blend));
            }
            int base = row * W;
            for (int xi = 0; xi < W; xi++) data[base + xi] = evalSpline((float) xi / (W - 1));
        }
    }

    // Uniform cubic B-spline through the control points.
    private float evalSpline(float u) {
        int n = CP - 3;
        float sp = Math.max(0, Math.min(u * n, n - 1e-6f));
        int seg = (int) sp;
        float t = sp - seg, t2 = t * t, t3 = t2 * t;
        float b0 = (1 - 3 * t + 3 * t2 - t3) / 6;
        float b1 = (4 - 6 * t2 + 3 * t3) / 6;
        float b2 = (1 + 3 * t + 3 * t2 - 3 * t3) / 6;
        float b3 = t3 / 6;
        return b0 * cp[seg] + b1 * cp[seg + 1] + b2 * cp[seg + 2] + b3 * cp[seg + 3];
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    private static double hash01(double x) {
        double h = Math.sin(x) * 43758.5453123;
        return h - Math.floor(h);
    }
}
