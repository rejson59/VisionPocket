package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.view.Surface;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * VisionPocket spatial engine:
 * - stereoscopic rendering with barrel-distortion correction
 * - world backgrounds: camera passthrough, procedural space, horizon grid
 * - Windows-style spatial windows (open, grab, move, depth, scale, minimize)
 * - hand-style touch interaction mapped onto window surfaces
 * - gaze + tap with hover feedback
 */
public class VrRenderer implements GLSurfaceView.Renderer, VpWindows.WmHost,
        CameraPipeline.Listener, HeadTracker.RecenterListener {

    private static final float FOVY_DEG = 78f;
    private static final float PASSTHROUGH_DIST = 8f;
    private static final float OVERSCAN = 1.4f;

    private final MainActivity activity;
    private final SettingsStore settings;
    private final HeadTracker head;
    private final CameraPipeline camera;
    private final VpWindows wm;

    // --- programs ---
    private int pPanel, pCam, pDist, pColor, pEnvSpace, pEnvHorizon;
    private int uPanelMVP, aPanelPos, aPanelUV, uPanelTex, uPanelAlpha;
    private int uCamMVP, aCamPos, aCamUV, uCamTex, uCamAngle, uCamZoom, uCamOverscan;
    private int aDistPos, uDistScene, uDistEyeCenter, uDistHalf, uDistAspect, uDistK1, uDistK2;
    private int uColorMVP, aColorPos, uColorColor;
    private int uSpaceMVP, aSpacePos, aSpaceUV, uSpaceTime;
    private int uHorMVP, aHorPos, aHorUV, uHorTime;

    // --- camera passthrough ---
    private int camTex = -1;
    private SurfaceTexture camST;
    private boolean camFrameEver = false;
    private boolean cameraAllowed = false;

    // --- video ---
    private int vidTex = -1;
    private SurfaceTexture vidST;
    private Surface vidSurface;
    private boolean vidFrame = false;

    // --- FBO ---
    private int fbo = -1, fboTex = -1, fboDepth = -1;
    private int fboW = 8, fboH = 8, eyeW = 8, eyeH = 8, scrW = 8, scrH = 8;
    private float eyeAspect = 1f;
    private final float[] proj = new float[16];

    // --- world-locked background anchor ---
    private float ptxCx = 0f, ptxCy = 0f, ptxCz = -PASSTHROUGH_DIST, ptxYaw = 0f;
    private float ptxW = 12f, ptxH = 8f;

    // --- dock + status bar + reticle ---
    private final Panel dock;
    private final Panel statusBar;
    private final Panel reticle;
    private int reticleMode = 0;      // 0 dot, 1 open hand, 2 grab
    private boolean reticleWorld = false;
    private float reticleScale = 1f;
    private float handWorldX, handWorldY, handWorldZ, handYaw;
    private String statusMsg = null;
    private long statusMsgUntil = 0;
    private boolean gyroWarned = false;

    // --- touch state machine ---
    private static final int TM_IDLE = 0, TM_PRESSED = 1, TM_DRAG = 2, TM_GESTURE = 3;
    private int touchMode = TM_IDLE;
    private VpWindows.Window grabWin;
    private Panel grabPanel;
    private float grabDist = 1.6f;
    private float grabKx = 0.001f, grabKy = 0.001f;
    private final float[] hr = new float[3], hu = new float[3], hf = new float[3];
    private float downX, downY, lastX, lastY;
    private float gestureScale0 = 1f, pinchInit = 1f, gestureLastMidY = 0f;

    // --- timing ---
    private long lastNs = 0;
    private float fps = 60f;
    private long lastStatusMs = 0;

    private final ConcurrentLinkedQueue<Runnable> glQueue = new ConcurrentLinkedQueue<>();

    private final float[] eyeT = new float[16];
    private final float[] eyeViewL = new float[16], eyeViewR = new float[16];
    private final float[] pvL = new float[16], pvR = new float[16];
    private final float[] headPvL = new float[16], headPvR = new float[16];
    private final float[] tmpM = new float[16], tmpM2 = new float[16], tmpM3 = new float[16];

    // shaders
    private static final String VS_TEX =
            "uniform mat4 uMVP;\n" +
            "attribute vec4 aPos;\n" +
            "attribute vec2 aUV;\n" +
            "varying vec2 vUV;\n" +
            "void main(){ gl_Position = uMVP * aPos; vUV = aUV; }\n";

    private static final String FS_PANEL =
            "precision mediump float;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform float uAlpha;\n" +
            "varying vec2 vUV;\n" +
            "void main(){ vec4 c = texture2D(uTex, vUV); gl_FragColor = vec4(c.rgb, c.a * uAlpha); }\n";

    private static final String FS_CAM =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES uCam;\n" +
            "uniform float uAngle;\n" +
            "uniform float uZoom;\n" +
            "uniform float uOverscan;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vec2 c = vUV - vec2(0.5);\n" +
            "  float cs = cos(uAngle);\n" +
            "  float sn = sin(uAngle);\n" +
            "  c = vec2(c.x*cs - c.y*sn, c.x*sn + c.y*cs);\n" +
            "  c = c / (uZoom * uOverscan);\n" +
            "  gl_FragColor = texture2D(uCam, vec2(0.5) + c);\n" +
            "}\n";

    private static final String FS_SPACE =
            "precision mediump float;\n" +
            "uniform float uTime;\n" +
            "varying vec2 vUV;\n" +
            "float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }\n" +
            "void main(){\n" +
            "  vec2 uv = vUV;\n" +
            "  vec3 col = mix(vec3(0.012, 0.016, 0.05), vec3(0.07, 0.05, 0.18), uv.y);\n" +
            "  col += vec3(0.10, 0.05, 0.16) * pow(1.0 - abs(uv.y - 0.35), 3.0) * 0.55;\n" +
            "  vec2 g = uv * vec2(150.0, 75.0);\n" +
            "  vec2 id = floor(g);\n" +
            "  float h = hash(id);\n" +
            "  vec2 f = fract(g) - 0.5;\n" +
            "  vec2 off = vec2(hash(id + 7.3), hash(id + 3.7)) - 0.5;\n" +
            "  float star = smoothstep(0.10, 0.0, length(f + off * 0.6));\n" +
            "  float tw = 0.55 + 0.45 * sin(uTime * (1.0 + h * 3.0) + h * 40.0);\n" +
            "  col += vec3(0.9, 0.95, 1.0) * step(0.94, h) * star * tw;\n" +
            "  gl_FragColor = vec4(col, 1.0);\n" +
            "}\n";

    private static final String FS_HORIZON =
            "precision mediump float;\n" +
            "uniform float uTime;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vec2 uv = vUV;\n" +
            "  float horizon = 0.55;\n" +
            "  vec3 col;\n" +
            "  if (uv.y > horizon) {\n" +
            "    col = mix(vec3(0.10, 0.14, 0.30), vec3(0.03, 0.05, 0.13), smoothstep(horizon, 1.0, uv.y));\n" +
            "  } else {\n" +
            "    col = vec3(0.028, 0.045, 0.075);\n" +
            "  }\n" +
            "  col += vec3(0.10, 0.30, 0.45) * exp(-abs(uv.y - horizon) * 26.0);\n" +
            "  if (uv.y <= horizon) {\n" +
            "    float depth = horizon - uv.y;\n" +
            "    float zx = uv.x * (8.0 + 80.0 * depth);\n" +
            "    float zz = depth * (5.0 + 50.0 * depth);\n" +
            "    float lineX = smoothstep(0.47, 0.5, abs(fract(zx) - 0.5));\n" +
            "    float lineZ = smoothstep(0.47, 0.5, abs(fract(zz) - 0.5));\n" +
            "    float fade = smoothstep(horizon, horizon - 0.10, uv.y) * (1.0 - smoothstep(0.45, 0.55, depth));\n" +
            "    col += vec3(0.10, 0.55, 0.65) * max(lineX, lineZ) * 0.45 * fade;\n" +
            "  }\n" +
            "  gl_FragColor = vec4(col, 1.0);\n" +
            "}\n";

    private static final String VS_DIST =
            "uniform float uHalf;\n" +
            "attribute vec4 aPos;\n" +
            "varying vec2 vPos;\n" +
            "void main(){ vPos = aPos.xy; gl_Position = vec4(aPos.x * 0.5 + uHalf, aPos.y, 0.0, 1.0); }\n";

    private static final String FS_DIST =
            "precision mediump float;\n" +
            "uniform sampler2D uScene;\n" +
            "uniform vec2 uEyeCenter;\n" +
            "uniform float uAspect;\n" +
            "uniform float uK1;\n" +
            "uniform float uK2;\n" +
            "varying vec2 vPos;\n" +
            "void main(){\n" +
            "  vec2 q = vec2(vPos.x * uAspect, vPos.y);\n" +
            "  float r2 = dot(q, q);\n" +
            "  float f = 1.0 + uK1 * r2 + uK2 * r2 * r2;\n" +
            "  vec2 uv = uEyeCenter + (q * f) / vec2(uAspect, 1.0) * 0.5;\n" +
            "  uv.x = clamp(uv.x, uEyeCenter.x - 0.499, uEyeCenter.x + 0.499);\n" +
            "  uv.y = clamp(uv.y, 0.001, 0.999);\n" +
            "  gl_FragColor = texture2D(uScene, uv);\n" +
            "}\n";

    private static final String VS_COLOR =
            "uniform mat4 uMVP;\n" +
            "attribute vec4 aPos;\n" +
            "void main(){ gl_Position = uMVP * aPos; }\n";

    private static final String FS_COLOR =
            "precision mediump float;\n" +
            "uniform vec4 uColor;\n" +
            "void main(){ gl_FragColor = uColor; }\n";

    public VrRenderer(MainActivity activity) {
        this.activity = activity;
        this.settings = new SettingsStore(activity);
        this.head = new HeadTracker(activity);
        this.camera = new CameraPipeline(activity, this);
        this.wm = new VpWindows(this);
        head.setRecenterListener(this);

        dock = new Panel(0f, -0.45f, -1.6f, 0f, 1.0f, 0.17f, 1000f);
        dock.add("recenter", 25, 30, 125, 130);
        dock.add("cinema", 145, 30, 245, 130);
        dock.add("photos", 265, 30, 365, 130);
        dock.add("env", 385, 30, 485, 130);
        dock.add("settings", 505, 30, 605, 130);
        dock.hook = this::drawDock;

        statusBar = new Panel(0f, 0.42f, -1.05f, 0f, 0.85f, 0.12f, 1000f);
        statusBar.hook = this::drawStatusBar;

        reticle = new Panel(0f, 0f, -0.7f, 0f, 0.07f, 0.07f, 1400f);
        reticle.hook = this::drawReticle;
    }

    // ================= lifecycle =================

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glClearColor(0f, 0f, 0f, 1f);

        pPanel = GlUtil.createProgram(VS_TEX, FS_PANEL);
        uPanelMVP = GLES20.glGetUniformLocation(pPanel, "uMVP");
        aPanelPos = GLES20.glGetAttribLocation(pPanel, "aPos");
        aPanelUV = GLES20.glGetAttribLocation(pPanel, "aUV");
        uPanelTex = GLES20.glGetUniformLocation(pPanel, "uTex");
        uPanelAlpha = GLES20.glGetUniformLocation(pPanel, "uAlpha");

        pCam = GlUtil.createProgram(VS_TEX, FS_CAM);
        uCamMVP = GLES20.glGetUniformLocation(pCam, "uMVP");
        aCamPos = GLES20.glGetAttribLocation(pCam, "aPos");
        aCamUV = GLES20.glGetAttribLocation(pCam, "aUV");
        uCamTex = GLES20.glGetUniformLocation(pCam, "uCam");
        uCamAngle = GLES20.glGetUniformLocation(pCam, "uAngle");
        uCamZoom = GLES20.glGetUniformLocation(pCam, "uZoom");
        uCamOverscan = GLES20.glGetUniformLocation(pCam, "uOverscan");

        pEnvSpace = GlUtil.createProgram(VS_TEX, FS_SPACE);
        uSpaceMVP = GLES20.glGetUniformLocation(pEnvSpace, "uMVP");
        aSpacePos = GLES20.glGetAttribLocation(pEnvSpace, "aPos");
        aSpaceUV = GLES20.glGetAttribLocation(pEnvSpace, "aUV");
        uSpaceTime = GLES20.glGetUniformLocation(pEnvSpace, "uTime");

        pEnvHorizon = GlUtil.createProgram(VS_TEX, FS_HORIZON);
        uHorMVP = GLES20.glGetUniformLocation(pEnvHorizon, "uMVP");
        aHorPos = GLES20.glGetAttribLocation(pEnvHorizon, "aPos");
        aHorUV = GLES20.glGetAttribLocation(pEnvHorizon, "aUV");
        uHorTime = GLES20.glGetUniformLocation(pEnvHorizon, "uTime");

        pDist = GlUtil.createProgram(VS_DIST, FS_DIST);
        aDistPos = GLES20.glGetAttribLocation(pDist, "aPos");
        uDistScene = GLES20.glGetUniformLocation(pDist, "uScene");
        uDistEyeCenter = GLES20.glGetUniformLocation(pDist, "uEyeCenter");
        uDistHalf = GLES20.glGetUniformLocation(pDist, "uHalf");
        uDistAspect = GLES20.glGetUniformLocation(pDist, "uAspect");
        uDistK1 = GLES20.glGetUniformLocation(pDist, "uK1");
        uDistK2 = GLES20.glGetUniformLocation(pDist, "uK2");

        pColor = GlUtil.createProgram(VS_COLOR, FS_COLOR);
        uColorMVP = GLES20.glGetUniformLocation(pColor, "uMVP");
        aColorPos = GLES20.glGetAttribLocation(pColor, "aPos");
        uColorColor = GLES20.glGetUniformLocation(pColor, "uColor");

        camTex = GlUtil.createOesTexture();
        camST = new SurfaceTexture(camTex);
        camST.setOnFrameAvailableListener(st -> {
            camera.frameAvailable = true;
            camFrameEver = true;
        }, camera.callbackHandler());
        camera.attach(camST);

        vidTex = GlUtil.createOesTexture();

        // GL textures are invalidated with the context
        dock.tex = -1; dock.dirty = true;
        statusBar.tex = -1; statusBar.dirty = true;
        reticle.tex = -1; reticle.dirty = true;
        for (VpWindows.Window w : wm.list) for (VpWindows.Window.Part pt : w.parts) { pt.panel.tex = -1; pt.panel.dirty = true; }

        if (cameraAllowed) camera.start();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        scrW = width;
        scrH = height;
        eyeW = Math.max(8, width / 2);
        eyeH = Math.max(8, height);
        eyeAspect = (float) eyeW / (float) eyeH;
        Matrix.perspectiveM(proj, 0, FOVY_DEG, eyeAspect, 0.05f, 80f);

        float tanHalf = (float) Math.tan(Math.toRadians(FOVY_DEG / 2f));
        float halfH = PASSTHROUGH_DIST * tanHalf * OVERSCAN;
        float halfW = halfH * eyeAspect;
        ptxW = halfW * 2f;
        ptxH = halfH * 2f;

        applyResolution();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        Runnable r;
        while ((r = glQueue.poll()) != null) {
            try { r.run(); } catch (Exception ignored) {}
        }

        long now = System.nanoTime();
        if (lastNs > 0) {
            float dt = (now - lastNs) / 1.0e9f;
            if (dt > 0f && dt < 1f) fps = fps * 0.9f + (1f / dt) * 0.1f;
        }
        lastNs = now;

        float fade = 0f; // reserved for transitions

        long upMs = SystemClock.uptimeMillis();
        if (upMs - lastStatusMs >= 1000) {
            statusBar.dirty = true;
            dock.dirty = true;
            lastStatusMs = upMs;
        }
        wm.update();

        // --- gaze hover (also drives highlights when not touching) ---
        clearAllHover();
        VpWindows.Hit hoverHit = raycastGaze();
        boolean somethingHovered = hoverHit != null && hoverHit.panel.hoveredButton() != null;

        // --- reticle mode from touch state ---
        if (touchMode == TM_IDLE) {
            reticleMode = 0;
            reticleWorld = false;
            reticleScale += ((somethingHovered ? 1.6f : 1f) - reticleScale) * 0.3f;
        } else {
            reticleMode = grabWin != null ? 2 : 1;
            // hand cursor sits on the window surface along the gaze ray
            float[] rot = head.rotation();
            handWorldX = grabDist * -rot[8];
            handWorldY = grabDist * -rot[9];
            handWorldZ = grabDist * -rot[10];
            handWorldY = Math.max(-1.2f, Math.min(1.4f, handWorldY));
            handYaw = (float) Math.atan2(handWorldX, -handWorldZ);
            reticleWorld = grabWin != null || grabPanel == dock;
            reticleScale += (1f - reticleScale) * 0.3f;
        }

        // --- camera frames ---
        if (camera.frameAvailable) {
            camera.frameAvailable = false;
            try { camST.updateTexImage(); } catch (Exception ignored) {}
        }
        if (vidFrame && vidST != null) {
            vidFrame = false;
            try { vidST.updateTexImage(); } catch (Exception ignored) {}
        }

        // --- scene into FBO, once per eye ---
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        for (int eye = 0; eye < 2; eye++) {
            int vx = eye == 0 ? 0 : fboW / 2;
            GLES20.glViewport(vx, 0, fboW / 2, fboH);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            float eyeX = (eye == 0 ? -1f : 1f) * settings.ipd() / 2000f;
            Matrix.setIdentityM(eyeT, 0);
            Matrix.translateM(eyeT, 0, -eyeX, 0f, 0f);
            float[] eyeView = eye == 0 ? eyeViewL : eyeViewR;
            Matrix.multiplyMM(eyeView, 0, eyeT, 0, head.view(), 0);
            float[] pv = eye == 0 ? pvL : pvR;
            Matrix.multiplyMM(pv, 0, proj, 0, eyeView, 0);
            float[] headPv = eye == 0 ? headPvL : headPvR;
            Matrix.multiplyMM(headPv, 0, proj, 0, eyeT, 0);

            // background world
            int envMode = settings.envMode();
            boolean passthroughLive = envMode == 0 && settings.passthrough()
                    && cameraAllowed && camFrameEver;
            if (passthroughLive) {
                drawCamQuad(camTex, ptxCx, ptxCy, ptxCz, ptxYaw, ptxW, ptxH,
                        settings.rotDeg(), settings.zoom(), OVERSCAN, pv);
            } else if (envMode == 2) {
                drawEnvQuad(pEnvHorizon, uHorMVP, aHorPos, aHorUV, uHorTime, pv);
            } else {
                drawEnvQuad(pEnvSpace, uSpaceMVP, aSpacePos, aSpaceUV, uSpaceTime, pv);
            }

            // spatial windows, far -> near
            List<VpWindows.Window> vis = new ArrayList<>();
            for (VpWindows.Window w : wm.list) if (!w.minimized) vis.add(w);
            sortFarToNear(vis, eyeView);
            for (VpWindows.Window w : vis) {
                for (VpWindows.Window.Part pt : w.parts) {
                    Panel p = pt.panel;
                    if (!p.visible) continue;
                    if (p.oes) {
                        drawCamQuad(p.oesTex, p.cx, p.cy, p.cz, p.yaw, p.width, p.height, 0f, 1f, 1f, pv);
                    } else {
                        p.renderIfNeeded();
                        drawTexturedQuad(p.tex, p.cx, p.cy, p.cz, p.yaw, p.width, p.height, 1f, 1f, pv);
                    }
                }
            }

            // dock (world, grabbable) — typically nearest
            dock.renderIfNeeded();
            drawTexturedQuad(dock.tex, dock.cx, dock.cy, dock.cz, dock.yaw,
                    dock.width, dock.height, 1f, 0.96f, pv);

            // head-locked status bar
            statusBar.renderIfNeeded();
            drawTexturedQuad(statusBar.tex, statusBar.cx, statusBar.cy, statusBar.cz, 0f,
                    statusBar.width, statusBar.height, 1f, 0.92f, headPv);

            // reticle / hand cursor
            reticle.renderIfNeeded();
            if (reticleWorld) {
                drawTexturedQuad(reticle.tex, handWorldX, handWorldY, handWorldZ, handYaw,
                        reticle.width, reticle.height, reticleScale, 0.97f, pv);
            } else {
                drawTexturedQuad(reticle.tex, reticle.cx, reticle.cy, reticle.cz, 0f,
                        reticle.width, reticle.height, reticleScale, 0.97f, headPv);
            }
        }

        // --- distortion pass to the display ---
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, scrW, scrH);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(pDist);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex);
        GLES20.glUniform1i(uDistScene, 0);
        GLES20.glUniform1f(uDistAspect, eyeAspect);
        float str = settings.distortionPct() / 100f;
        GLES20.glUniform1f(uDistK1, 0.22f * str);
        GLES20.glUniform1f(uDistK2, 0.02f * str);
        GLES20.glEnableVertexAttribArray(aDistPos);
        GLES20.glVertexAttribPointer(aDistPos, 3, GLES20.GL_FLOAT, false, 12, Panel.quadPos());
        for (int eye = 0; eye < 2; eye++) {
            GLES20.glUniform1f(uDistHalf, eye == 0 ? -0.5f : 0.5f);
            GLES20.glUniform2f(uDistEyeCenter, eye == 0 ? 0.25f : 0.75f, 0.5f);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }

        // --- fade overlay ---
        if (fade > 0.003f) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glUseProgram(pColor);
            Matrix.setIdentityM(tmpM, 0);
            GLES20.glUniformMatrix4fv(uColorMVP, 1, false, tmpM, 0);
            GLES20.glUniform4f(uColorColor, 0f, 0f, 0f, fade);
            GLES20.glEnableVertexAttribArray(aColorPos);
            GLES20.glVertexAttribPointer(aColorPos, 3, GLES20.GL_FLOAT, false, 12, Panel.quadPos());
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisable(GLES20.GL_BLEND);
        }
    }

    // ================= drawing helpers =================

    private void sortFarToNear(List<VpWindows.Window> vis, float[] eyeView) {
        // insertion sort by eye-space z ascending (most negative = farthest, drawn first)
        // so alpha blending composes correctly for overlapping windows
        for (int i = 1; i < vis.size(); i++) {
            VpWindows.Window w = vis.get(i);
            float zw = viewZ(eyeView, w.ax, w.ay, w.az);
            int j = i - 1;
            while (j >= 0 && viewZ(eyeView, vis.get(j).ax, vis.get(j).ay, vis.get(j).az) > zw) {
                vis.set(j + 1, vis.get(j));
                j--;
            }
            vis.set(j + 1, w);
        }
    }

    private float viewZ(float[] m, float x, float y, float z) {
        return m[2] * x + m[6] * y + m[10] * z + m[14];
    }

    private void drawEnvQuad(int program, int mvpLoc, int posLoc, int uvLoc, int timeLoc, float[] pv) {
        Matrix.setIdentityM(tmpM2, 0);
        Matrix.translateM(tmpM2, 0, ptxCx, ptxCy, ptxCz);
        if (ptxYaw != 0f) Matrix.rotateM(tmpM2, 0, (float) Math.toDegrees(ptxYaw), 0f, 1f, 0f);
        Matrix.scaleM(tmpM2, 0, ptxW, ptxH, 1f);
        Matrix.multiplyMM(tmpM3, 0, pv, 0, tmpM2, 0);

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, tmpM3, 0);
        GLES20.glUniform1f(timeLoc, SystemClock.uptimeMillis() / 1000f);
        GLES20.glEnableVertexAttribArray(posLoc);
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 12, Panel.quadPos());
        GLES20.glEnableVertexAttribArray(uvLoc);
        GLES20.glVertexAttribPointer(uvLoc, 2, GLES20.GL_FLOAT, false, 8, Panel.quadUv());
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glEnable(GLES20.GL_BLEND);
    }

    private void drawCamQuad(int tex, float cx, float cy, float cz, float yaw,
                             float w, float h, float angleDeg, float zoom, float overscan, float[] pv) {
        Matrix.setIdentityM(tmpM2, 0);
        Matrix.translateM(tmpM2, 0, cx, cy, cz);
        if (yaw != 0f) Matrix.rotateM(tmpM2, 0, (float) Math.toDegrees(yaw), 0f, 1f, 0f);
        Matrix.scaleM(tmpM2, 0, w, h, 1f);
        Matrix.multiplyMM(tmpM3, 0, pv, 0, tmpM2, 0);

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(pCam);
        GLES20.glUniformMatrix4fv(uCamMVP, 1, false, tmpM3, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
        GLES20.glUniform1i(uCamTex, 0);
        GLES20.glUniform1f(uCamAngle, (float) Math.toRadians(angleDeg));
        GLES20.glUniform1f(uCamZoom, zoom);
        GLES20.glUniform1f(uCamOverscan, overscan);
        GLES20.glEnableVertexAttribArray(aCamPos);
        GLES20.glVertexAttribPointer(aCamPos, 3, GLES20.GL_FLOAT, false, 12, Panel.quadPos());
        GLES20.glEnableVertexAttribArray(aCamUV);
        GLES20.glVertexAttribPointer(aCamUV, 2, GLES20.GL_FLOAT, false, 8, Panel.quadUv());
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glEnable(GLES20.GL_BLEND);
    }

    private void drawTexturedQuad(int tex, float cx, float cy, float cz, float yaw,
                                  float w, float h, float scale, float alpha, float[] pv) {
        Matrix.setIdentityM(tmpM2, 0);
        Matrix.translateM(tmpM2, 0, cx, cy, cz);
        if (yaw != 0f) Matrix.rotateM(tmpM2, 0, (float) Math.toDegrees(yaw), 0f, 1f, 0f);
        Matrix.scaleM(tmpM2, 0, w * scale, h * scale, 1f);
        Matrix.multiplyMM(tmpM3, 0, pv, 0, tmpM2, 0);

        GLES20.glUseProgram(pPanel);
        GLES20.glUniformMatrix4fv(uPanelMVP, 1, false, tmpM3, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glUniform1i(uPanelTex, 0);
        GLES20.glUniform1f(uPanelAlpha, alpha);
        GLES20.glEnableVertexAttribArray(aPanelPos);
        GLES20.glVertexAttribPointer(aPanelPos, 3, GLES20.GL_FLOAT, false, 12, Panel.quadPos());
        GLES20.glEnableVertexAttribArray(aPanelUV);
        GLES20.glVertexAttribPointer(aPanelUV, 2, GLES20.GL_FLOAT, false, 8, Panel.quadUv());
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    // ================= dock / status / reticle =================

    private void drawDock(Canvas c, Panel p) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(dock.hovered ? 0xF01A1E30 : 0xF0141622);
        c.drawRoundRect(0, 0, p.pw, p.ph, 50, 50, bg);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(3f);
        border.setColor(dock.hovered ? 0xFF5B8DEF : 0xFF2A3046);
        c.drawRoundRect(2, 2, p.pw - 2, p.ph - 2, 50, 50, border);

        String[] icons = {"⟲", "🎬", "🖼", "🌍", "⚙"};
        Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
        icon.setColor(0xFFE8ECF5);
        icon.setTextSize(74f);
        icon.setTextAlign(Paint.Align.CENTER);
        Paint btnBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint btnBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        btnBorder.setStyle(Paint.Style.STROKE);
        btnBorder.setStrokeWidth(2.5f);

        for (int i = 0; i < p.buttons.size() && i < icons.length; i++) {
            Panel.Button b = p.buttons.get(i);
            boolean open = false;
            if (!"recenter".equals(b.id)) {
                VpWindows.Window w = wm.find(b.id);
                open = w != null && !w.minimized;
                boolean mini = w != null && w.minimized;
                btnBg.setColor(b.hover ? 0xFF3D4964 : (open ? 0xFF2A3550 : 0xFF20242F));
                icon.setAlpha(mini ? 140 : 255);
            } else {
                btnBg.setColor(b.hover ? 0xFF3D4964 : 0xFF20242F);
            }
            c.drawRoundRect(b.rect, 20, 20, btnBg);
            btnBorder.setColor(b.hover ? 0xFF5B8DEF : 0xFF39415A);
            c.drawRoundRect(b.rect, 20, 20, btnBorder);
            c.drawText(icons[i], (b.rect.left + b.rect.right) / 2f,
                    (b.rect.top + b.rect.bottom) / 2f - (icon.ascent() + icon.descent()) / 2f, icon);
            icon.setAlpha(255);
        }

        Paint brand = new Paint(Paint.ANTI_ALIAS_FLAG);
        brand.setColor(0xFF5B8DEF);
        brand.setTextSize(40f);
        brand.setFakeBoldText(true);
        brand.setTextAlign(Paint.Align.CENTER);
        c.drawText("VisionPocket", (605 + p.pw) / 2f, 96f, brand);

        long now = SystemClock.uptimeMillis();
        if (statusMsg != null && now < statusMsgUntil) {
            Paint msgP = new Paint(Paint.ANTI_ALIAS_FLAG);
            msgP.setColor(0xFF5B8DEF);
            msgP.setTextSize(40f);
            msgP.setFakeBoldText(true);
            msgP.setTextAlign(Paint.Align.CENTER);
            c.drawText(statusMsg, p.pw / 2f, 96f, msgP);
        }
    }

    private void drawStatusBar(Canvas c, Panel p) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xC2101218);
        c.drawRoundRect(0, 0, p.pw, p.ph, 60, 60, bg);
        Paint left = new Paint(Paint.ANTI_ALIAS_FLAG);
        left.setColor(0xFF8FA7E8);
        left.setTextSize(44f);
        left.setFakeBoldText(true);
        left.setTextAlign(Paint.Align.LEFT);
        Paint mid = new Paint(Paint.ANTI_ALIAS_FLAG);
        mid.setColor(0xFFE8ECF5);
        mid.setTextSize(50f);
        mid.setTextAlign(Paint.Align.CENTER);
        Paint right = new Paint(Paint.ANTI_ALIAS_FLAG);
        right.setColor(0xFF7A8298);
        right.setTextSize(38f);
        right.setTextAlign(Paint.Align.RIGHT);

        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        c.drawText("VisionPocket", 36f, 78f, left);
        c.drawText(time, p.pw / 2f, 82f, mid);
        c.drawText(Math.round(fps) + " FPS · " + activity.batteryPct() + "%", p.pw - 36f, 76f, right);

        if (!gyroWarned && !head.hasSensor) {
            gyroWarned = true;
            Paint msgP = new Paint(Paint.ANTI_ALIAS_FLAG);
            msgP.setColor(0xFF5B8DEF);
            msgP.setTextSize(36f);
            msgP.setTextAlign(Paint.Align.CENTER);
            c.drawText(activity.getString(R.string.status_no_gyro), p.pw / 2f, 78f, msgP);
        }
    }

    private void drawReticle(Canvas c, Panel p) {
        if (reticleMode == 0) {
            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setColor(0xF2FFFFFF);
            c.drawCircle(p.pw / 2f, p.ph / 2f, p.pw / 5.5f, dot);
            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
            ring.setColor(0x55FFFFFF);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Math.max(2f, p.pw / 26f));
            c.drawCircle(p.pw / 2f, p.ph / 2f, p.pw / 3.1f, ring);
        } else {
            Paint hand = new Paint(Paint.ANTI_ALIAS_FLAG);
            hand.setTextAlign(Paint.Align.CENTER);
            hand.setTextSize(p.pw * 0.72f);
            String emoji = reticleMode == 2 ? "✊" : "✋";
            c.drawText(emoji, p.pw / 2f, p.ph / 2f - (hand.ascent() + hand.descent()) / 2f, hand);
        }
    }

    private void setMsg(String msg, long ms) {
        statusMsg = msg;
        statusMsgUntil = SystemClock.uptimeMillis() + ms;
        dock.dirty = true;
    }

    // ================= touch handling (hand interaction) =================

    public void postTouch(int type, float[] xs, float[] ys) {
        glQueue.add(() -> handleTouch(type, xs, ys));
    }

    private void clearAllHover() {
        wm.clearHover();
        dock.clearHover();
    }

    private VpWindows.Hit raycastGaze() {
        float[] rot = head.rotation();
        float fx = -rot[8], fy = -rot[9], fz = -rot[10];
        VpWindows.Hit best = wm.raycast(0f, 0f, 0f, fx, fy, fz);
        float[] dh = dock.rayHit(0f, 0f, 0f, fx, fy, fz);
        if (dh != null && (best == null || dh[0] < best.t)) {
            best = new VpWindows.Hit(null, dock, dh[0], dh[1], dh[2]);
        }
        return best;
    }

    private void computeGrabBasis() {
        float[] m = head.rotation();
        hr[0] = m[0]; hr[1] = m[1]; hr[2] = m[2];
        hu[0] = m[4]; hu[1] = m[5]; hu[2] = m[6];
        hf[0] = -m[8]; hf[1] = -m[9]; hf[2] = -m[10];
        float tanHalf = (float) Math.tan(Math.toRadians(FOVY_DEG / 2f));
        grabKx = 2f * grabDist * tanHalf * eyeAspect / Math.max(1, scrW);
        grabKy = 2f * grabDist * tanHalf / Math.max(1, scrH);
    }

    private void handleTouch(int type, float[] xs, float[] ys) {
        switch (type) {
            case 0: { // DOWN
                clearAllHover();
                downX = xs[0]; downY = ys[0]; lastX = downX; lastY = downY;
                VpWindows.Hit h = raycastGaze();
                grabPanel = h != null ? h.panel : null;
                grabWin = (h != null) ? h.win : null;
                grabDist = h != null ? h.t : 1.6f;
                computeGrabBasis();
                if (grabWin != null) wm.focus(grabWin);
                touchMode = TM_PRESSED;
                break;
            }
            case 4: { // second pointer down -> depth + scale gesture
                if (xs.length >= 2 && touchMode != TM_IDLE) {
                    touchMode = TM_GESTURE;
                    pinchInit = (float) Math.hypot(xs[1] - xs[0], ys[1] - ys[0]);
                    gestureLastMidY = (ys[0] + ys[1]) / 2f;
                    gestureScale0 = grabWin != null ? grabWin.scale : 1f;
                }
                break;
            }
            case 1: { // MOVE
                if (touchMode == TM_IDLE || xs.length == 0) break;
                float x = xs[0], y = ys[0];
                if (touchMode == TM_GESTURE) {
                    if (xs.length >= 2) { gestureMove(xs, ys); break; }
                    touchMode = TM_DRAG;
                    lastX = x; lastY = y;
                }
                if (touchMode == TM_PRESSED && Math.abs(x - downX) + Math.abs(y - downY) > 44f) {
                    touchMode = TM_DRAG;
                }
                if (touchMode == TM_DRAG) {
                    float dx = x - lastX, dy = y - lastY;
                    lastX = x; lastY = y;
                    applyDrag(dx, dy);
                }
                break;
            }
            case 5: { // POINTER_UP (one of several)
                if (touchMode == TM_GESTURE && xs.length == 1) {
                    touchMode = TM_DRAG;
                    lastX = xs[0]; lastY = ys[0];
                }
                break;
            }
            case 2:
            case 3: { // UP / CANCEL
                if (touchMode == TM_PRESSED && grabPanel != null) {
                    Panel.Button b = grabPanel.hoveredButton();
                    if (grabPanel == dock) {
                        if (b != null) { dockAction(b.id); activity.playClick(); }
                    } else if (grabWin != null) {
                        if (b != null) {
                            routeButton(grabWin, b.id);
                            activity.playClick();
                        } else if (grabWin.content != null) {
                            grabWin.content.onBodyClick(grabPanel);
                            activity.playClick();
                        }
                    }
                }
                touchMode = TM_IDLE;
                grabWin = null;
                grabPanel = null;
                clearAllHover();
                break;
            }
        }
    }

    private void applyDrag(float dx, float dy) {
        float mx = hr[0] * dx * grabKx + hu[0] * (-dy) * grabKy;
        float my = hr[1] * dx * grabKx + hu[1] * (-dy) * grabKy;
        float mz = hr[2] * dx * grabKx + hu[2] * (-dy) * grabKy;
        if (grabWin != null) {
            grabWin.moveBy(mx, my, mz);
        } else if (grabPanel == dock) {
            dock.cx += mx;
            dock.cy = Math.max(-1.3f, Math.min(0.7f, dock.cy + my));
            dock.cz = Math.max(-6f, Math.min(-0.5f, dock.cz + mz));
        }
    }

    private void gestureMove(float[] xs, float[] ys) {
        float d = (float) Math.hypot(xs[1] - xs[0], ys[1] - ys[0]);
        float midY = (ys[0] + ys[1]) / 2f;
        if (grabWin != null && pinchInit > 20f) {
            grabWin.applyScale(gestureScale0 * d / pinchInit);
        }
        float delta = midY - gestureLastMidY;
        gestureLastMidY = midY;
        float push = -delta * grabKy * 1.6f;
        if (grabWin != null) {
            grabWin.moveBy(hf[0] * push, hf[1] * push, hf[2] * push);
        } else if (grabPanel == dock) {
            dock.cx += hf[0] * push;
            dock.cy = Math.max(-1.3f, Math.min(0.7f, dock.cy + hf[1] * push));
            dock.cz = Math.max(-6f, Math.min(-0.5f, dock.cz + hf[2] * push));
        }
    }

    private void routeButton(VpWindows.Window w, String id) {
        if ("chrome_close".equals(id)) {
            wm.close(w);
            return;
        }
        if ("chrome_min".equals(id)) {
            w.setMinimized(true);
            toast(activity.getString(R.string.toast_min));
            return;
        }
        if (w.content != null) w.content.onClick(id);
    }

    private void dockAction(String id) {
        if ("recenter".equals(id)) {
            recenterView();
            return;
        }
        toggleApp(id);
    }

    private void toggleApp(String kind) {
        VpWindows.Window w = wm.find(kind);
        if (w == null) {
            openWindow(kind);
        } else if (w.minimized || !w.focused) {
            wm.focus(w);
        } else {
            w.setMinimized(true);
        }
    }

    private void openWindow(String kind) {
        switch (kind) {
            case "cinema": new CinemaWindow(wm, this); break;
            case "photos": new PhotosWindow(wm, this); break;
            case "env": new EnvWindow(wm, this); break;
            case "settings": new SettingsWindow(wm, this); break;
            case "start":
                if (wm.find("start") == null) new StartWindow(wm, this);
                break;
        }
    }

    // ================= external entry points (main thread) =================

    public void onVrStarted() {
        glQueue.add(() -> {
            openWindow("start");
            setMsg(activity.getString(R.string.start_toast), 6000);
        });
    }

    public boolean back() {
        final boolean[] consumed = {false};
        // run synchronously-ish: just inspect state on the UI thread (safe reads)
        VpWindows.Window w = wm.focusedWindow();
        if (w != null) {
            consumed[0] = true;
            glQueue.add(() -> wm.close(w));
        }
        return consumed[0];
    }

    public void setCameraAllowed(boolean allowed) {
        cameraAllowed = allowed;
        glQueue.add(() -> { if (cameraAllowed) camera.start(); });
    }

    public void onVideoPicked(Uri u) {
        glQueue.add(() -> {
            CinemaWindow cw = null;
            for (VpWindows.Window w : wm.list) {
                if (w.content instanceof CinemaWindow) { cw = (CinemaWindow) w.content; break; }
            }
            if (cw == null) {
                cw = new CinemaWindow(wm, this);
            } else {
                wm.focus(cw.window);
            }
            cw.video.oesTex = vidTex;
            ensureVideoSurface();
            cw.loadVideo(u, vidST, vidSurface);
        });
    }

    public void onImagePicked(Uri u) {
        glQueue.add(() -> {
            PhotosWindow pw = null;
            for (VpWindows.Window w : wm.list) {
                if (w.content instanceof PhotosWindow) { pw = (PhotosWindow) w.content; break; }
            }
            if (pw == null) pw = new PhotosWindow(wm, this);
            else wm.focus(pw.window);
            pw.loadImage(u);
        });
    }

    private void ensureVideoSurface() {
        if (vidST == null) {
            vidST = new SurfaceTexture(vidTex);
            vidST.setOnFrameAvailableListener(st -> vidFrame = true, camera.callbackHandler());
            vidSurface = new Surface(vidST);
        }
    }

    public void headStart() { head.start(); }
    public void headStop() { head.stop(); }
    public void cameraStop() { camera.stop(); }

    public void onAppResume() {
        glQueue.add(() -> { if (cameraAllowed && camST != null) camera.start(); });
    }

    // ================= WmHost =================

    @Override public SettingsStore settings() { return settings; }
    @Override public MainActivity activity() { return activity; }
    @Override public void toast(String msg) { setMsg(msg, 2600); }
    @Override public void recenterView() { head.recenter(); }
    @Override public float headYaw() {
        float[] f = head.forward(new float[3]);
        return (float) Math.atan2(f[0], -f[2]);
    }

    @Override
    public void applyResolution() {
        float scale = settings.resScale();
        fboW = Math.min(4096, Math.round(eyeW * 2 * scale));
        fboH = Math.max(8, Math.round(eyeH * scale));
        if (fbo >= 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{fbo}, 0);
            GLES20.glDeleteTextures(1, new int[]{fboTex}, 0);
            GLES20.glDeleteRenderbuffers(1, new int[]{fboDepth}, 0);
        }
        int[] fb = new int[1];
        GLES20.glGenFramebuffers(1, fb, 0);
        fbo = fb[0];
        int[] tx = new int[1];
        GLES20.glGenTextures(1, tx, 0);
        fboTex = tx[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, fboW, fboH, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        int[] rb = new int[1];
        GLES20.glGenRenderbuffers(1, rb, 0);
        fboDepth = rb[0];
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, fboDepth);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, fboW, fboH);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex, 0);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, fboDepth);
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            fbo = -1;
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    @Override public void pickVideo() { activity.runOnUiThread(activity::openVideoPicker); }
    @Override public void pickImage() { activity.runOnUiThread(activity::openImagePicker); }

    // ================= CameraPipeline.Listener =================

    @Override public void onCameraFailed(String reason) {
        glQueue.add(() -> setMsg(activity.getString(R.string.err_no_camera), 5000));
    }

    // ================= HeadTracker.RecenterListener =================

    @Override public void onRecenter(float deltaYaw) {
        float c = (float) Math.cos(deltaYaw), s = (float) Math.sin(deltaYaw);
        float nx = ptxCx * c - ptxCz * s;
        float nz = ptxCx * s + ptxCz * c;
        ptxCx = nx;
        ptxCz = nz;
        ptxYaw -= deltaYaw;
        dock.rotateBy(deltaYaw);
        wm.onRecenter(deltaYaw);
        setMsg(activity.getString(R.string.status_recentered), 2000);
    }
}
