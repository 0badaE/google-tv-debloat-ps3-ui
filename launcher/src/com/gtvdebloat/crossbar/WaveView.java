package com.gtvdebloat.crossbar;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The animated wallpaper: background gradient, the translucent wave mesh and additive sparkles,
 * drawn with OpenGL ES 3 on its own surface behind the menu. Shaders are ported from
 * linkev/PlayStation-3-XMB (MIT), ps3xmbwave/spline.js and particles.js.
 */
final class WaveView extends GLSurfaceView implements GLSurfaceView.Renderer {
    private static final String TAG = "Crossbar";
    private static final int GRID = 100;
    /** Sparkles allocated; the Count setting shows a share of them. Same maximum as the web panel. */
    static final int MAX_PARTICLES = 4000;

    private static final String BG_VS = "#version 300 es\n"
            + "in vec2 aPos; out vec2 vUv;\n"
            + "void main(){ vUv = vec2(aPos.x*0.5+0.5, 1.0-(aPos.y*0.5+0.5)); gl_Position = vec4(aPos,0.0,1.0); }";
    private static final String BG_FS = "#version 300 es\n"
            + "precision highp float;\n"
            + "in vec2 vUv; out vec4 oColor;\n"
            + "uniform vec3 uStart; uniform vec3 uEnd; uniform vec2 uDir; uniform float uTMin; uniform float uTSpan; uniform float uFade;\n"
            + "void main(){ float u = clamp((dot(vUv,uDir)-uTMin)/max(uTSpan,1e-6),0.0,1.0);\n"
            + "  float g = u*u*(3.0-2.0*u); oColor = vec4(mix(uStart,uEnd,g)*uFade,1.0); }";

    private static final String WAVE_VS = "#version 300 es\n"
            + "precision highp float;\n"
            + "in vec2 aPos;\n"
            + "uniform sampler2D uSplineTex; uniform float uTime; uniform float flowSpeed; uniform float tension;\n"
            + "uniform float damping; uniform float uLength; uniform float spacing; uniform float perturbation;\n"
            + "uniform float perturbationScale; uniform float timeStep; uniform float waveCosAmp; uniform float waveBias;\n"
            + "uniform float waveHeightScale; uniform float waveSoftClip; uniform float ffdYAmp; uniform float ffdZAmp;\n"
            + "uniform float zDetailScale; uniform vec3 ffdScale1; uniform vec3 ffdScale2; uniform vec3 ffdOffset;\n"
            + "out vec3 vPos;\n"
            + "void main(){\n"
            + "  vec3 p = vec3(aPos.x, 0.0, aPos.y);\n"
            + "  vec2 uv = (aPos + 1.0) * 0.5;\n"
            + "  p.y = texture(uSplineTex, uv).r;\n"
            + "  vec3 ffd1 = p * ffdScale1 + ffdOffset; vec3 ffd2 = p * ffdScale2 + ffdOffset;\n"
            + "  p.y += sin(ffd1.x + uTime * flowSpeed) * ffdYAmp;\n"
            + "  p.z += cos(ffd2.z + uTime * flowSpeed) * ffdZAmp;\n"
            + "  float baseWave = cos(p.x * 2.0 - uTime * 0.5 * timeStep) * waveCosAmp + waveBias;\n"
            + "  baseWave *= (1.0 - damping);\n"
            + "  baseWave += tension * sin(p.x * uLength + uTime * flowSpeed * timeStep * 0.25);\n"
            + "  float structured = perturbation * perturbationScale * (\n"
            + "    sin((p.x * uLength * 6.0 + p.z * 0.5) * spacing * 0.01 + uTime * flowSpeed * timeStep * 0.7) * 0.5 +\n"
            + "    sin((p.x * uLength * 10.0 - p.z * 0.8) * spacing * 0.005 - uTime * flowSpeed * timeStep * 0.35) * 0.25);\n"
            + "  float totalWave = (baseWave + structured) * waveHeightScale;\n"
            + "  totalWave = waveSoftClip * tanh(totalWave / max(waveSoftClip, 1e-4));\n"
            + "  p.y -= totalWave;\n"
            + "  vec2 uv2 = uv; uv2.x = fract(uv2.x - uTime * flowSpeed * 0.04 * timeStep);\n"
            + "  p.z -= texture(uSplineTex, uv2).r * zDetailScale;\n"
            + "  gl_Position = vec4(p, 1.0); vPos = p;\n"
            + "}";
    private static final String WAVE_FS = "#version 300 es\n"
            + "precision highp float;\n"
            + "in vec3 vPos; out vec4 oColor;\n"
            + "uniform float opacity; uniform float brightness; uniform float fresnelPower; uniform float fresnelScale; uniform float uFade;\n"
            + "uniform vec3 uColor;\n"
            + "void main(){\n"
            + "  vec3 N = normalize(cross(dFdx(vPos), dFdy(vPos)));\n"
            + "  float F = fresnelScale * pow(max(1.0 + dot(vec3(0.0,0.0,-1.0), N), 0.0), fresnelPower);\n"
            + "  oColor = vec4(uColor, clamp(F * opacity * brightness * uFade, 0.0, 1.0));\n"
            + "}";

    private static final String PT_VS = "#version 300 es\n"
            + "precision highp float;\n"
            + "in vec3 aSeed;\n"
            + "uniform float uTime; uniform float flowSpeed; uniform float ratio; uniform float ptSizeBase; uniform float ptSizeVar;\n"
            + "uniform float uDensity;\n"
            + "out float vAlpha;\n"
            + "void main(){\n"
            + "  gl_PointSize = aSeed.z * ptSizeVar + ptSizeBase;\n"
            + "  float time = uTime * flowSpeed;\n"
            + "  float x = fract(time * (aSeed.x - 0.5) / 15.0 + aSeed.y * 50.0) * 2.0 - 1.0;\n"
            + "  float y = sin(sign(aSeed.y) * time * (aSeed.y + 1.5) / 4.0 + aSeed.x * 100.0) / ((6.0 - aSeed.x * 4.0 * aSeed.y) / ratio);\n"
            + "  float opVar = mix(sin(time * (aSeed.x + 0.5) * 12.0 + aSeed.y * 10.0),\n"
            + "                    sin(time * (aSeed.y + 1.5) * 6.0 + aSeed.x * 4.0), y * 0.5 + 0.5) * aSeed.x + aSeed.y;\n"
            + "  vAlpha = opVar * opVar * (1.0 - fract(aSeed.x + time * 0.00285));\n"
            // Each sparkle has a rank; uDensity reveals ranks below it, fading in rather than popping.
            + "  float rank = fract(aSeed.x * 7.13 + aSeed.y * 3.71);\n"
            + "  vAlpha *= clamp((uDensity - rank) * 8.0, 0.0, 1.0);\n"
            + "  gl_Position = vec4(x, y, 0.0, 1.0);\n"
            + "}";
    private static final String PT_FS = "#version 300 es\n"
            + "precision highp float;\n"
            + "in float vAlpha; out vec4 oColor; uniform float particleOpacity; uniform vec3 uColor;\n"
            + "void main(){ vec2 c = gl_PointCoord * 2.0 - 1.0; float d = dot(c,c); if (d > 1.0) discard;\n"
            + "  float s = (1.0-d)*(1.0-d); float a = vAlpha * particleOpacity * s; oColor = vec4(uColor * a,1.0); }";

    private final WaveSettings settings;
    private final SplineField field;
    private final FloatBuffer fieldBuf = ByteBuffer.allocateDirect(SplineField.W * SplineField.H * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final float particleOffset = new Random().nextFloat() * 1000f;
    private final AudioPulse pulse;
    private final android.util.SparseArray<java.util.HashMap<String, Integer>> uniforms = new android.util.SparseArray<>();

    // Wave time runs faster while music plays, so it's integrated frame by frame.
    private long lastFrameNanos;
    private float waveTime;
    private float musicMix, fast, slow, dyn = 0.5f, speed = 1;
    private float dynMin = 1, dynMax = 0;

    private int bgProg, waveProg, ptProg;
    private int bgVao, waveVao, ptVao, waveIndexCount;
    private int tex;
    private float aspect = 16f / 9f;
    private boolean ready;

    // Theme and fade transitions, set from the UI thread.
    private final float[] gradFrom = new float[7];
    private final float[] gradTo = new float[7];
    private long gradStart;
    private int gradDur;
    private boolean followCustom;
    private float fadeFrom = 1, fadeTo = 1;
    private long fadeStart;
    private int fadeDur;

    WaveView(Context context) {
        super(context);
        settings = WaveSettings.get(context);
        field = new SplineField(settings);
        pulse = new AudioPulse(context);
        setEGLContextClientVersion(3);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    @Override
    public void onResume() {
        super.onResume();
        pulse.start();
    }

    @Override
    public void onPause() {
        pulse.stop();
        super.onPause();
    }

    /**
     * Fades the background to a theme. Custom keeps following Settings → Wave Controls live,
     * so moving its sliders shows straight away.
     */
    synchronized void showTheme(String id, int durationMs) {
        long now = System.nanoTime();
        float[] cur = displayed(now);
        followCustom = Themes.CUSTOM.equals(id);
        if (!followCustom) {
            System.arraycopy(Themes.gradient(id, java.util.Calendar.getInstance(), settings), 0, gradTo, 0, 7);
        }
        System.arraycopy(durationMs > 0 ? cur : followCustom ? settings.customGradient() : gradTo, 0, gradFrom, 0, 7);
        gradStart = now;
        gradDur = durationMs;
    }

    synchronized void setFade(float from, float to, int durationMs) {
        fadeFrom = from;
        fadeTo = to;
        fadeStart = System.nanoTime();
        fadeDur = durationMs;
    }

    /** The gradient on screen at {@code now}, partway through any fade. */
    private float[] displayed(long now) {
        float[] to = followCustom ? settings.customGradient() : gradTo;
        float t = gradDur <= 0 ? 1 : Math.min(1, (now - gradStart) / 1e6f / gradDur);
        t = t * t * (3 - 2 * t);
        float[] out = new float[7];
        for (int k = 0; k < 7; k++) out[k] = gradFrom[k] + (to[k] - gradFrom[k]) * t;
        return out;
    }

    private synchronized float currentFade(long now) {
        float t = fadeDur <= 0 ? 1 : Math.min(1, (now - fadeStart) / 1e6f / fadeDur);
        t = t * t * (3 - 2 * t);
        return fadeFrom + (fadeTo - fadeFrom) * t;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        uniforms.clear();
        try {
            bgProg = link(BG_VS, BG_FS);
            waveProg = link(WAVE_VS, WAVE_FS);
            ptProg = link(PT_VS, PT_FS);
            bgVao = makeQuad();
            makeGrid();
            makeParticles();
            makeTexture();
            ready = true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Wave setup failed", e);
            ready = false;
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES30.glViewport(0, 0, width, height);
        aspect = (float) width / Math.max(1, height);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        long now = System.nanoTime();
        logFps(now);
        if (!ready) {
            GLES30.glClearColor(0, 0, 0, 1);
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
            return;
        }
        float dt = lastFrameNanos == 0 ? 0 : Math.min(0.1f, (now - lastFrameNanos) / 1e9f);
        lastFrameNanos = now;
        react(dt);
        // Music: the wave flows faster, its glow and the sparkles rise and fall with the song.
        // swell is -0.5 (quieter than the song around it) .. +0.5 (louder).
        float swell = dyn - 0.5f;
        waveTime += dt * speed;
        float brightness = 1 + musicMix * (0.25f + 1.1f * swell);
        float density = settings.count.v / MAX_PARTICLES + musicMix * (0.15f + 0.7f * swell);
        float sparkle = Math.max(0.2f, 1 + musicMix * (0.4f + 2.0f * swell));
        float t = waveTime;
        float fade = currentFade(now);
        float[] g;
        synchronized (this) { g = displayed(now); }

        field.update(t);
        fieldBuf.position(0);
        fieldBuf.put(field.data).position(0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex);
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, SplineField.W, SplineField.H,
                GLES30.GL_RED, GLES30.GL_FLOAT, fieldBuf);

        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_BLEND);

        // Background gradient.
        double rad = Math.toRadians(g[6]);
        float dx = (float) Math.cos(rad), dy = (float) Math.sin(rad);
        float tMin = Math.min(Math.min(0, dx), Math.min(dy, dx + dy));
        float tMax = Math.max(Math.max(0, dx), Math.max(dy, dx + dy));
        GLES30.glUseProgram(bgProg);
        GLES30.glUniform3f(u(bgProg, "uStart"), g[0], g[1], g[2]);
        GLES30.glUniform3f(u(bgProg, "uEnd"), g[3], g[4], g[5]);
        GLES30.glUniform2f(u(bgProg, "uDir"), dx, dy);
        GLES30.glUniform1f(u(bgProg, "uTMin"), tMin);
        GLES30.glUniform1f(u(bgProg, "uTSpan"), Math.max(1e-6f, tMax - tMin));
        GLES30.glUniform1f(u(bgProg, "uFade"), fade);
        GLES30.glBindVertexArray(bgVao);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        // Wave.
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glUseProgram(waveProg);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex);
        int p = waveProg;
        WaveSettings s = settings;
        GLES30.glUniform1i(u(p, "uSplineTex"), 0);
        GLES30.glUniform1f(u(p, "uTime"), t);
        GLES30.glUniform1f(u(p, "flowSpeed"), s.flowSpeed.v);
        GLES30.glUniform1f(u(p, "tension"), s.tension.v);
        GLES30.glUniform1f(u(p, "damping"), s.damping.v);
        GLES30.glUniform1f(u(p, "uLength"), s.length.v);
        GLES30.glUniform1f(u(p, "spacing"), s.spacing.v);
        GLES30.glUniform1f(u(p, "perturbation"), s.perturbation.v);
        GLES30.glUniform1f(u(p, "perturbationScale"), s.perturbationScale.v);
        GLES30.glUniform1f(u(p, "timeStep"), s.timeStep.v);
        GLES30.glUniform1f(u(p, "waveCosAmp"), s.waveCosAmp.v);
        GLES30.glUniform1f(u(p, "waveBias"), s.waveBias.v);
        GLES30.glUniform1f(u(p, "waveHeightScale"), s.waveHeightScale.v);
        GLES30.glUniform1f(u(p, "waveSoftClip"), s.waveSoftClip.v);
        GLES30.glUniform3f(u(p, "ffdScale1"), s.ffdScale1X.v, s.ffdScale1Y.v, s.ffdScale1Z.v);
        GLES30.glUniform3f(u(p, "ffdScale2"), s.ffdScale2X.v, s.ffdScale2Y.v, s.ffdScale2Z.v);
        GLES30.glUniform3f(u(p, "ffdOffset"), s.ffdOffsetX.v, s.ffdOffsetY.v, s.ffdOffsetZ.v);
        GLES30.glUniform1f(u(p, "ffdYAmp"), s.ffdYAmp.v);
        GLES30.glUniform1f(u(p, "ffdZAmp"), s.ffdZAmp.v);
        GLES30.glUniform1f(u(p, "zDetailScale"), s.zDetailScale.v);
        GLES30.glUniform1f(u(p, "opacity"), s.opacity.v * brightness);
        GLES30.glUniform1f(u(p, "brightness"), s.brightness.v);
        GLES30.glUniform1f(u(p, "fresnelPower"), s.fresnelPower.v);
        GLES30.glUniform1f(u(p, "fresnelScale"), s.fresnelScale.v);
        GLES30.glUniform3f(u(p, "uColor"), s.waveR.v / 255f, s.waveG.v / 255f, s.waveB.v / 255f);
        GLES30.glUniform1f(u(p, "uFade"), fade);
        GLES30.glBindVertexArray(waveVao);
        GLES30.glDrawElements(GLES30.GL_TRIANGLE_STRIP, waveIndexCount, GLES30.GL_UNSIGNED_SHORT, 0);

        // Sparkles.
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE);
        GLES30.glUseProgram(ptProg);
        GLES30.glUniform1f(u(ptProg, "uTime"), t + particleOffset);
        GLES30.glUniform1f(u(ptProg, "flowSpeed"), s.particleFlowSpeed.v);
        GLES30.glUniform1f(u(ptProg, "ratio"), Math.max(1f, Math.min(aspect, 2f)) * 0.375f);
        GLES30.glUniform1f(u(ptProg, "particleOpacity"), s.particleOpacity.v * fade * sparkle);
        GLES30.glUniform1f(u(ptProg, "ptSizeBase"), s.sizeBase.v);
        GLES30.glUniform1f(u(ptProg, "uDensity"), density);
        GLES30.glUniform1f(u(ptProg, "ptSizeVar"), s.sizeVar.v);
        GLES30.glUniform3f(u(ptProg, "uColor"), s.sparkleR.v / 255f, s.sparkleG.v / 255f, s.sparkleB.v / 255f);
        GLES30.glBindVertexArray(ptVao);
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, MAX_PARTICLES);
        GLES30.glBindVertexArray(0);
    }

    private long fpsStart;
    private int fpsFrames;

    /** Enable with: adb shell setprop log.tag.Crossbar DEBUG */
    private void logFps(long now) {
        if (fpsStart == 0) fpsStart = now;
        fpsFrames++;
        if (now - fpsStart >= 5_000_000_000L) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, String.format(java.util.Locale.ROOT,
                        "wave %.1f fps, music=%b listening=%b mix=%.2f speed=%.2f swell %.2f..%.2f",
                        fpsFrames / ((now - fpsStart) / 1e9), pulse.musicActive(), pulse.listening(), musicMix,
                        speed, dynMin, dynMax));
                dynMin = 1;
                dynMax = 0;
            }
            fpsStart = now;
            fpsFrames = 0;
        }
    }

    /**
     * Turns raw loudness into a smooth "swell": how much louder or quieter this moment is than the
     * last few seconds of the song. Comparing with the song itself keeps it lively on any track.
     */
    private void react(float dt) {
        boolean music = pulse.musicActive();
        // Music starting or stopping blends over a couple of seconds.
        musicMix += ((music ? 1 : 0) - musicMix) * Math.min(1, dt * 0.8f);
        float level = music && pulse.listening() ? pulse.level() : 0;
        // Envelope: rises quickly, falls gently; then compared with a slow running average.
        fast += (level - fast) * Math.min(1, dt * (level > fast ? 7f : 2.5f));
        slow += (level - slow) * Math.min(1, dt * 0.35f);
        float target = music && pulse.listening() ? clamp01(0.5f + (fast - slow) * 3f) : 0.5f;
        dyn += (target - dyn) * Math.min(1, dt * 4f);
        dynMin = Math.min(dynMin, dyn);
        dynMax = Math.max(dynMax, dyn);
        // Speed eases separately so the flow never jerks.
        float speedTarget = 1 + musicMix * (1.6f + 1.0f * dyn);
        speed += (speedTarget - speed) * Math.min(1, dt * 1.2f);
    }

    private static float clamp01(float v) {
        return Math.max(0, Math.min(1, v));
    }

    private int u(int prog, String name) {
        java.util.HashMap<String, Integer> m = uniforms.get(prog);
        if (m == null) {
            m = new java.util.HashMap<>();
            uniforms.put(prog, m);
        }
        Integer loc = m.get(name);
        if (loc == null) {
            loc = GLES30.glGetUniformLocation(prog, name);
            m.put(name, loc);
        }
        return loc;
    }

    private static int compile(int type, String src) {
        int s = GLES30.glCreateShader(type);
        GLES30.glShaderSource(s, src);
        GLES30.glCompileShader(s);
        int[] ok = new int[1];
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(s);
            GLES30.glDeleteShader(s);
            throw new RuntimeException("Shader compile failed: " + log);
        }
        return s;
    }

    private static int link(String vs, String fs) {
        int p = GLES30.glCreateProgram();
        int v = compile(GLES30.GL_VERTEX_SHADER, vs);
        int f = compile(GLES30.GL_FRAGMENT_SHADER, fs);
        GLES30.glAttachShader(p, v);
        GLES30.glAttachShader(p, f);
        GLES30.glLinkProgram(p);
        GLES30.glDeleteShader(v);
        GLES30.glDeleteShader(f);
        int[] ok = new int[1];
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("Program link failed: " + GLES30.glGetProgramInfoLog(p));
        return p;
    }

    private static FloatBuffer floats(float[] a) {
        FloatBuffer b = ByteBuffer.allocateDirect(a.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.put(a).position(0);
        return b;
    }

    private static int genVao() {
        int[] id = new int[1];
        GLES30.glGenVertexArrays(1, id, 0);
        GLES30.glBindVertexArray(id[0]);
        return id[0];
    }

    private static int genBuffer(int target) {
        int[] id = new int[1];
        GLES30.glGenBuffers(1, id, 0);
        GLES30.glBindBuffer(target, id[0]);
        return id[0];
    }

    private int makeQuad() {
        int vao = genVao();
        genBuffer(GLES30.GL_ARRAY_BUFFER);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 32, floats(new float[]{-1, -1, 1, -1, -1, 1, 1, 1}), GLES30.GL_STATIC_DRAW);
        int loc = GLES30.glGetAttribLocation(bgProg, "aPos");
        GLES30.glEnableVertexAttribArray(loc);
        GLES30.glVertexAttribPointer(loc, 2, GLES30.GL_FLOAT, false, 0, 0);
        GLES30.glBindVertexArray(0);
        return vao;
    }

    private void makeGrid() {
        int strips = GRID - 1, perStrip = GRID * 2;
        float[] verts = new float[strips * perStrip * 2];
        int vi = 0;
        for (int y = 0; y < strips; y++) {
            for (int x = 0; x < GRID; x++) {
                float fx = (float) x / (GRID - 1) * 2 - 1;
                verts[vi++] = fx;
                verts[vi++] = (float) (y + 1) / (GRID - 1) * 2 - 1;
                verts[vi++] = fx;
                verts[vi++] = (float) y / (GRID - 1) * 2 - 1;
            }
        }
        short[] idx = new short[strips * (perStrip + 2) - 2];
        int ii = 0, base = 0;
        for (int s = 0; s < strips; s++) {
            if (s > 0) {
                idx[ii++] = (short) (base - 1);
                idx[ii++] = (short) base;
            }
            for (int i = 0; i < perStrip; i++) idx[ii++] = (short) (base + i);
            base += perStrip;
        }
        waveIndexCount = ii;

        waveVao = genVao();
        genBuffer(GLES30.GL_ARRAY_BUFFER);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.length * 4, floats(verts), GLES30.GL_STATIC_DRAW);
        int loc = GLES30.glGetAttribLocation(waveProg, "aPos");
        GLES30.glEnableVertexAttribArray(loc);
        GLES30.glVertexAttribPointer(loc, 2, GLES30.GL_FLOAT, false, 8, 0);
        genBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER);
        ShortBuffer ib = ByteBuffer.allocateDirect(ii * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        ib.put(idx, 0, ii).position(0);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, ii * 2, ib, GLES30.GL_STATIC_DRAW);
        GLES30.glBindVertexArray(0);
    }

    private void makeParticles() {
        Random r = new Random();
        float[] seeds = new float[MAX_PARTICLES * 3];
        for (int i = 0; i < MAX_PARTICLES; i++) {
            seeds[i * 3] = r.nextFloat();
            seeds[i * 3 + 1] = r.nextFloat();
            seeds[i * 3 + 2] = (float) Math.pow(r.nextFloat(), 8) + 0.1f;
        }
        ptVao = genVao();
        genBuffer(GLES30.GL_ARRAY_BUFFER);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, seeds.length * 4, floats(seeds), GLES30.GL_STATIC_DRAW);
        int loc = GLES30.glGetAttribLocation(ptProg, "aSeed");
        GLES30.glEnableVertexAttribArray(loc);
        GLES30.glVertexAttribPointer(loc, 3, GLES30.GL_FLOAT, false, 0, 0);
        GLES30.glBindVertexArray(0);
    }

    private void makeTexture() {
        int[] id = new int[1];
        GLES30.glGenTextures(1, id, 0);
        tex = id[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        // R16F is filterable on every GLES 3 device; R32F (used by the web version) often isn't.
        fieldBuf.position(0);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R16F, SplineField.W, SplineField.H, 0,
                GLES30.GL_RED, GLES30.GL_FLOAT, fieldBuf);
    }
}
