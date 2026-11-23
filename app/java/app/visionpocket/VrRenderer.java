package app.visionpocket;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Environment;
import android.os.SystemClock;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.FloatBuffer;
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
 * - backgrounds: passthrough (back/front camera), procedural space/horizon,
 *   360° equirectangular panoramas (photo/video) with projection modes
 * - Windows-style spatial windows: open, grab, move, depth, scale, minimize,
 *   inertia physics, snap-to-face
 * - hand-style touch interaction + gamepad (stick cursor/laser, grab, depth)
 * - curved cinema screen, screen mirroring, app launcher, 3D keyboard,
 *   notes, music visualizer, VR screenshots
 * - adaptive eye resolution to hold frame rate
 */
public class VrRenderer implements GLSurfaceView.Renderer, VpWindows.WmHost,
        CameraPipeline.Listener, HeadTracker.RecenterListener {

    private static final float FOVY_DEG = 78f;
    private static final float PASSTHROUGH_DIST = 8f;
    private static final float OVERSCAN = 1.4f;
    private static final float PANO_RADIUS = 30f;

    private final MainActivity activity;
    private final SettingsStore settings;
    private final HeadTracker head;
    private final CameraPipeline camera;
    private final VpWindows wm;

    // --- programs ---
    private int pPanel, pCam, pDist, pColor, pEnvSpace, pEnvHorizon;
    private int uPanelMVP, aPanelPos, aPanelUV, uPanelTex, uPanelAlpha, uPanelMode;
    private int uCamMVP, aCamPos, aCamUV, uCamTex, uCamAngle, uCamZoom, uCamOverscan, uCamMode, uCamSide, uCamBright;
    private int aDistPos, uDistScene, uDistEyeCenter, uDistHalf, uDistAspect, uDistK1, uDistK2;
    private int uColorMVP, aColorPos, uColorColor;
    private int uSpaceMVP, aSpacePos, aSpaceUV, uSpaceTime;
    private int uHorMVP, aHorPos, aHorUV, uHorTime;

    // --- camera passthrough ---
    private int camTex = -1;
    private SurfaceTexture camST;
    private boolean camFrameEver = false;
    private boolean cameraAllowed = false;

    // --- cinema video ---
    private int vidTex = -1;
    private SurfaceTexture vidST;
    private Surface vidSurface;
    private boolean vidFrame = false;

    // --- panorama 360 ---
    public static final int PANO_OFF = 0, PANO_IMAGE = 1, PANO_VIDEO = 2;
    private int panoMode = PANO_OFF;
    private int panoTex = -1;
    private Mesh panoMesh;
    private int panoVidTex = -1;
    private SurfaceTexture panoST;
    private Surface panoSurface;
    private MediaPlayer panoMp;
    private boolean panoFrame = false;
    private boolean panoVideoReady = false;

    // --- screen mirror ---
    private int mirrorTex = -1;
    private SurfaceTexture mirrorST;
    private Surface mirrorSurface;
    private boolean mirrorFrame = false;
    private int mirrorState = 0;
    private MirrorWindow mirrorWin;

    // --- FBO + adaptive resolution ---
    private int fbo = -1, fboTex = -1, fboDepth = -1;
    private int fboW = 8, fboH = 8, eyeW = 8, eyeH = 8, scrW = 8, scrH = 8;
    private float eyeAspect = 1f;
    private final float[] proj = new float[16];
    private static final float[] ADAPT_STEPS = {0.55f, 0.65f, 0.75f, 0.85f, 1.0f};
    private int adaptIdx = ADAPT_STEPS.length - 1;
    private float lowTimer = 0f, highTimer = 0f, runTime = 0f;

    // --- world-locked background anchor ---
    private float ptxCx = 0f, ptxCy = 0f, ptxCz = -PASSTHROUGH_DIST, ptxYaw = 0f;
    private float ptxW = 12f, ptxH = 8f;

    // --- dock + status bar + reticle/hand ---
    private final Panel dock;
    private final Panel statusBar;
    private final Panel reticle;
    private int reticleMode = 0;      // 0 dot, 1 open hand, 2 grab hand
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
    private float velPx = 0f, velPy = 0f;

    // --- game overlay ---
    private GameWindow gameWin;
    private Game2048Window game2048Win;

    // --- shared room (Wi-Fi multiplayer) ---
    private NetService net;
    private NetWindow netWin;
    private boolean netApplying = false;
    private final java.util.HashSet<String> netClosedKeys = new java.util.HashSet<>();
    private final java.util.concurrent.ConcurrentHashMap<String, float[]> avatars
            = new java.util.concurrent.ConcurrentHashMap<>();
    private String lastNetStateSent = "";
    private final float[] netM = new float[16], netM2 = new float[16];
    private java.nio.FloatBuffer peerBuf;

    // --- window inertia ---
    private VpWindows.Window inertWin;
    private float inertVX, inertVY, inertVZ;

    // --- gamepad ---
    private boolean gpActive = false;
    private float gpYaw = 0f, gpPitch = 0f;      // ray offsets (radians)
    private float gpStickX = 0f, gpStickY = 0f;
    private boolean gpGrabbing = false;
    private float gpDepth = 0f;                  // right trigger 0..1
    private FloatBuffer laserBuf;

    // --- screenshot ---
    private volatile boolean shotRequested = false;

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

    // shader sources live in Shaders.java

    public VrRenderer(MainActivity activity) {
        this.activity = activity;
        this.settings = new SettingsStore(activity);
        settings.migrateDistortion();
        this.head = new HeadTracker(activity);
        this.head.attach(this.settings);
        this.camera = new CameraPipeline(activity, this);
        this.wm = new VpWindows(this);
        head.setRecenterListener(this);

        peerBuf = java.nio.ByteBuffer.allocateDirect(12 * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
        net = new NetService();
        String selfName = android.os.Build.MODEL == null ? "VP" : android.os.Build.MODEL;
        if (selfName.length() > 24) selfName = selfName.substring(0, 24);
        net.setSelfName(selfName);
        net.setListener(new NetService.Listener() {
            @Override public void onPeersChanged() {
                glQueue.add(() -> { if (netWin != null) netWin.refresh(); });
            }
            @Override public void onMessage(org.json.JSONObject m) {
                glQueue.add(() -> handleNetMessage(m));
            }
            @Override public void onStatus(String st) {
                glQueue.add(() -> {
                    if ("fail".equals(st)) toast(activity.getString(R.string.net_fail));
                    else if (st != null && st.startsWith("connected")) toast(activity.getString(R.string.net_joined));
                    if (netWin != null) netWin.refresh();
                });
            }
        });

        String[] ids = {"recenter", "cinema", "photos", "pano", "mirror", "notes", "apps",
                "viz", "clock", "game", "g2048", "net", "env", "settings"};
        dock = new Panel(0f, -0.45f, -1.6f, 0f, 1.6f, 0.17f, 1000f);
        for (int i = 0; i < ids.length; i++) {
            int x0 = 40 + i * 112;
            dock.add(ids[i], x0, 30, x0 + 88, 130);
        }
        dock.hook = this::drawDock;

        statusBar = new Panel(0f, 0.42f, -1.05f, 0f, 0.85f, 0.12f, 1000f);
        statusBar.hook = this::drawStatusBar;

        reticle = new Panel(0f, 0f, -0.7f, 0f, 0.075f, 0.075f, 1400f);
        reticle.hook = this::drawReticle;

        laserBuf = GlUtil.fb(new float[6]);

        MirrorService.stopListener = () -> glQueue.add(() -> {
            mirrorState = 0;
            if (mirrorWin != null) mirrorWin.screen.visible = false;
            toast(activity.getString(R.string.toast_mirror_stopped));
        });
    }

    // ================= lifecycle =================

    private boolean glFail = false;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        try {
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glClearColor(0f, 0f, 0f, 1f);

        pPanel = GlUtil.createProgram(Shaders.VS_TEX, Shaders.FS_PANEL);
        uPanelMVP = GLES20.glGetUniformLocation(pPanel, "uMVP");
        aPanelPos = GLES20.glGetAttribLocation(pPanel, "aPos");
        aPanelUV = GLES20.glGetAttribLocation(pPanel, "aUV");
        uPanelTex = GLES20.glGetUniformLocation(pPanel, "uTex");
        uPanelAlpha = GLES20.glGetUniformLocation(pPanel, "uAlpha");
        uPanelMode = GLES20.glGetUniformLocation(pPanel, "uMode");

        pCam = GlUtil.createProgram(Shaders.VS_TEX, Shaders.FS_CAM);
        uCamMVP = GLES20.glGetUniformLocation(pCam, "uMVP");
        aCamPos = GLES20.glGetAttribLocation(pCam, "aPos");
        aCamUV = GLES20.glGetAttribLocation(pCam, "aUV");
        uCamTex = GLES20.glGetUniformLocation(pCam, "uCam");
        uCamAngle = GLES20.glGetUniformLocation(pCam, "uAngle");
        uCamZoom = GLES20.glGetUniformLocation(pCam, "uZoom");
        uCamOverscan = GLES20.glGetUniformLocation(pCam, "uOverscan");
        uCamMode = GLES20.glGetUniformLocation(pCam, "uMode");
        uCamSide = GLES20.glGetUniformLocation(pCam, "uSide");
        uCamBright = GLES20.glGetUniformLocation(pCam, "uBright");

        pEnvSpace = GlUtil.createProgram(Shaders.VS_TEX, Shaders.FS_SPACE);
        uSpaceMVP = GLES20.glGetUniformLocation(pEnvSpace, "uMVP");
        aSpacePos = GLES20.glGetAttribLocation(pEnvSpace, "aPos");
        aSpaceUV = GLES20.glGetAttribLocation(pEnvSpace, "aUV");
        uSpaceTime = GLES20.glGetUniformLocation(pEnvSpace, "uTime");

        pEnvHorizon = GlUtil.createProgram(Shaders.VS_TEX, Shaders.FS_HORIZON);
        uHorMVP = GLES20.glGetUniformLocation(pEnvHorizon, "uMVP");
        aHorPos = GLES20.glGetAttribLocation(pEnvHorizon, "aPos");
        aHorUV = GLES20.glGetAttribLocation(pEnvHorizon, "aUV");
        uHorTime = GLES20.glGetUniformLocation(pEnvHorizon, "uTime");

        pDist = GlUtil.createProgram(Shaders.VS_DIST, Shaders.FS_DIST);
        aDistPos = GLES20.glGetAttribLocation(pDist, "aPos");
        uDistScene = GLES20.glGetUniformLocation(pDist, "uScene");
        uDistEyeCenter = GLES20.glGetUniformLocation(pDist, "uEyeCenter");
        uDistHalf = GLES20.glGetUniformLocation(pDist, "uHalf");
        uDistAspect = GLES20.glGetUniformLocation(pDist, "uAspect");
        uDistK1 = GLES20.glGetUniformLocation(pDist, "uK1");
        uDistK2 = GLES20.glGetUniformLocation(pDist, "uK2");

        pColor = GlUtil.createProgram(Shaders.VS_COLOR, Shaders.FS_COLOR);
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
        panoVidTex = GlUtil.createOesTexture();
        panoMesh = Mesh.sphere(PANO_RADIUS, 48, 32);
        mirrorTex = GlUtil.createOesTexture();

        // GL textures are invalidated with the context
        dock.tex = -1; dock.dirty = true;
        statusBar.tex = -1; statusBar.dirty = true;
        reticle.tex = -1; reticle.dirty = true;
        panoTex = -1;
        panoST = null; panoSurface = null; panoMp = null; panoVideoReady = false;
        mirrorST = null; mirrorSurface = null;
        for (VpWindows.Window w : wm.list) {
            for (VpWindows.Window.Part pt : w.parts) { pt.panel.tex = -1; pt.panel.dirty = true; }
        }

        if (cameraAllowed) camera.start();
        } catch (Throwable t) {
            glFail = true;
        }
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

    private int frameCount = 0;
    private int frameErrors = 0;

    @Override
    public void onDrawFrame(GL10 gl) {
        if (glFail) {
            GLES20.glClearColor(0f, 0f, 0f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            return;
        }
        Runnable r;
        while ((r = glQueue.poll()) != null) {
            try { r.run(); } catch (Exception ignored) {}
        }
        if (++frameCount % 120 == 0) saveSession();

        try {
            drawFrame(gl);
        } catch (Exception e) {
            frameErrors++;
            if (frameErrors == 1 || frameErrors % 900 == 0) {
                activity.runOnUiThread(() -> toast(activity.getString(R.string.err_render)));
            }
        }
    }

    private void drawFrame(GL10 gl) {
        long now = System.nanoTime();

        // shared room: send gaze + full window state periodically
        if (net.active && !netApplying && net.snapshot().size() > 0) {
            if (frameCount % 10 == 0) netSendGaze();
            if (frameCount % 45 == 0) netSyncState();
        }
        float dt = 0.016f;
        if (lastNs > 0) {
            dt = (now - lastNs) / 1.0e9f;
            if (dt > 0f && dt < 1f) fps = fps * 0.9f + (1f / dt) * 0.1f;
        }
        lastNs = now;
        runTime += dt;
        adaptResolution(dt);

        long upMs = SystemClock.uptimeMillis();
        if (upMs - lastStatusMs >= 1000) {
            statusBar.dirty = true;
            dock.dirty = true;
            lastStatusMs = upMs;
        }
        wm.update();
        updateInertia(dt);
        applyGamepadFrame(dt);

        // --- gaze / gamepad hover ---
        clearAllHover();
        float[] rayDir = currentRay();
        VpWindows.Hit hoverHit = raycastAlong(rayDir);
        boolean somethingHovered = hoverHit != null && hoverHit.panel.hoveredButton() != null;

        // --- reticle / hand mode ---
        if (touchMode == TM_IDLE && !gpGrabbing) {
            reticleMode = gpActive ? 1 : 0;
            reticleWorld = false;
            reticleScale += ((somethingHovered ? 1.6f : 1f) - reticleScale) * 0.3f;
        } else {
            reticleMode = (grabWin != null || gpGrabbing) ? 2 : 1;
            float[] rot = head.rotation();
            handWorldX = grabDist * -rot[8];
            handWorldY = grabDist * -rot[9];
            handWorldZ = grabDist * -rot[10];
            handWorldY = Math.max(-1.2f, Math.min(1.4f, handWorldY));
            handYaw = (float) Math.atan2(handWorldX, -handWorldZ);
            reticleWorld = grabWin != null || grabPanel == dock;
            reticleScale += (1f - reticleScale) * 0.3f;
        }

        // --- external frames ---
        if (camera.frameAvailable) {
            camera.frameAvailable = false;
            try { camST.updateTexImage(); } catch (Exception ignored) {}
        }
        if (vidFrame && vidST != null) {
            vidFrame = false;
            try { vidST.updateTexImage(); } catch (Exception ignored) {}
        }
        if (panoFrame && panoST != null && panoMode == PANO_VIDEO) {
            panoFrame = false;
            try { panoST.updateTexImage(); } catch (Exception ignored) {}
        }
        if (mirrorFrame && mirrorST != null && mirrorState == 1) {
            mirrorFrame = false;
            try { mirrorST.updateTexImage(); } catch (Exception ignored) {}
        }

        // --- scene into FBO, once per eye ---
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        int panoProj = settings.panoProj();

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

            // background world (panorama replaces environment)
            VpWindows.Window panoW = wm.find("pano");
            boolean panoVisible = panoMode != PANO_OFF && panoW != null && !panoW.minimized;
            if (panoVisible) {
                drawPano(pv, panoProj);
            } else {
                int envMode = settings.envMode();
                boolean passthroughLive = envMode == 0 && settings.passthrough()
                        && cameraAllowed && camFrameEver;
                if (passthroughLive) {
                    drawCamBright(camTex, ptxCx, ptxCy, ptxCz, ptxYaw, ptxW, ptxH,
                            settings.rotDeg(), settings.zoom(), OVERSCAN, pv,
                            settings.brightness());
                } else if (envMode == 2) {
                    drawEnvQuad(pEnvHorizon, uHorMVP, aHorPos, aHorUV, uHorTime, pv);
                } else {
                    drawEnvQuad(pEnvSpace, uSpaceMVP, aSpacePos, aSpaceUV, uSpaceTime, pv);
                }
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
                        float side = p.sbsSide == 0 ? 0f : (eye == 0 ? -1f : 1f);
                        if (p.mesh != null) {
                            drawOesMesh(p.oesTex, p, pv, side);
                        } else {
                            drawCamQuad(p.oesTex, p.cx, p.cy, p.cz, p.yaw, p.width, p.height,
                                    0f, 1f, 1f, pv, 0f, side);
                        }
                    } else {
                        p.renderIfNeeded();
                        drawTexturedQuad(p.tex, p.cx, p.cy, p.cz, p.yaw, p.width, p.height, 1f, 1f, pv, 0f);
                    }
                }
            }

            // game targets overlay
            for (Panel tp : gamePanels()) {
                if (!tp.visible) continue;
                tp.renderIfNeeded();
                drawTexturedQuad(tp.tex, tp.cx, tp.cy, tp.cz, tp.yaw,
                        tp.width, tp.height, 1f, 1f, pv, 0f);
            }

            // peer avatars (fresh gaze markers)
            long nowMs = System.currentTimeMillis();
            for (java.util.Map.Entry<String, float[]> e : avatars.entrySet()) {
                float[] v = e.getValue();
                if (nowMs - v[2] > 3000) { avatars.remove(e.getKey()); continue; }
                float dist = 2.6f;
                float px = (float) Math.sin(v[0]) * (float) Math.cos(v[1]) * dist;
                float py = (float) Math.sin(v[1]) * dist + 0.05f;
                float pz = -(float) Math.cos(v[0]) * (float) Math.cos(v[1]) * dist;
                drawDiamond(px, py, pz, pv);
            }

            // dock
            dock.renderIfNeeded();
            drawTexturedQuad(dock.tex, dock.cx, dock.cy, dock.cz, dock.yaw,
                    dock.width, dock.height, 1f, 0.96f, pv, 0f);

            // status bar
            statusBar.renderIfNeeded();
            drawTexturedQuad(statusBar.tex, statusBar.cx, statusBar.cy, statusBar.cz, 0f,
                    statusBar.width, statusBar.height, 1f, 0.92f, headPv, 0f);

            // gamepad laser
            if (gpActive) {
                float dist = hoverHit != null ? Math.min(hoverHit.t, 4f) : 1.6f;
                drawLaser(rayDir, dist, pv);
            }

            // reticle / hand cursor
            reticle.renderIfNeeded();
            if (reticleWorld) {
                drawTexturedQuad(reticle.tex, handWorldX, handWorldY, handWorldZ, handYaw,
                        reticle.width, reticle.height, reticleScale, 0.97f, pv, 0f);
            } else if (gpActive) {
                drawTexturedQuad(reticle.tex, rayDir[0] * 0.8f, rayDir[1] * 0.8f, rayDir[2] * 0.8f, 0f,
                        reticle.width, reticle.height, reticleScale, 0.97f, headPv, 0f);
            } else {
                drawTexturedQuad(reticle.tex, reticle.cx, reticle.cy, reticle.cz, 0f,
                        reticle.width, reticle.height, reticleScale, 0.97f, headPv, 0f);
            }

            // VR screenshot (before leaving the FBO)
            if (shotRequested && eye == 1) {
                shotRequested = false;
                captureShot(fboW / 2, fboH);
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
    }

    // ================= adaptive resolution =================

    private void adaptResolution(float dt) {
        int pm = settings.perfMode();
        if (pm == 2) { // performance: always full res
            if (adaptIdx < ADAPT_STEPS.length - 1) {
                adaptIdx = ADAPT_STEPS.length - 1;
                applyResolution();
            }
            return;
        }
        int minIdx = pm == 0 ? 2 : 0;              // battery never goes below 75 %
        int maxIdx = pm == 0 ? 2 : ADAPT_STEPS.length - 1;
        if (adaptIdx < minIdx) {
            adaptIdx = minIdx;
            applyResolution();
            return;
        }
        if (runTime < 2.5f) return;
        if (fps < 52f) {
            lowTimer += dt;
            highTimer = 0f;
            if (lowTimer > 1.5f && adaptIdx > minIdx) {
                adaptIdx--;
                applyResolution();
                lowTimer = 0f;
                toast(activity.getString(R.string.toast_res,
                        Math.round(ADAPT_STEPS[adaptIdx] * 100f)));
            }
        } else if (fps > 58f) {
            highTimer += dt;
            lowTimer = 0f;
            if (highTimer > 5f && adaptIdx < maxIdx) {
                adaptIdx++;
                applyResolution();
                highTimer = 0f;
                toast(activity.getString(R.string.toast_res,
                        Math.round(ADAPT_STEPS[adaptIdx] * 100f)));
            }
        } else {
            lowTimer = 0f;
            highTimer = 0f;
        }
    }

    // ================= rays / gamepad =================

    /** Gaze ray or gamepad-offset ray (head-relative). */
    private float[] currentRay() {
        float[] rot = head.rotation();
        float fx = -rot[8], fy = -rot[9], fz = -rot[10];
        if (!gpActive) return new float[]{fx, fy, fz};
        float yaw0 = (float) Math.atan2(fx, -fz);
        float pitch0 = (float) Math.asin(Math.max(-1f, Math.min(1f, fy)));
        float yaw = yaw0 + gpYaw;
        float pitch = Math.max(-1.3f, Math.min(1.3f, pitch0 + gpPitch));
        float cp = (float) Math.cos(pitch);
        return new float[]{(float) Math.sin(yaw) * cp, (float) Math.sin(pitch), -(float) Math.cos(yaw) * cp};
    }

    private List<Panel> gamePanels() {
        if (gameWin == null || gameWin.window.minimized) return java.util.Collections.emptyList();
        return gameWin.targets;
    }

    private VpWindows.Hit raycastAlong(float[] dir) {
        float fx = dir[0], fy = dir[1], fz = dir[2];
        VpWindows.Hit best = wm.raycast(0f, 0f, 0f, fx, fy, fz);
        float[] dh = dock.rayHit(0f, 0f, 0f, fx, fy, fz);
        if (dh != null && (best == null || dh[0] < best.t)) {
            best = new VpWindows.Hit(null, dock, dh[0], dh[1], dh[2]);
        }
        for (Panel tp : gamePanels()) {
            if (!tp.visible) continue;
            float[] th = tp.rayHit(0f, 0f, 0f, fx, fy, fz);
            if (th != null && (best == null || th[0] < best.t)) {
                best = new VpWindows.Hit(gameWin.window, tp, th[0], th[1], th[2]);
            }
        }
        return best;
    }

    public void postGamepadStick(float x, float y) {
        glQueue.add(() -> {
            if (Math.abs(x) < 0.16f && Math.abs(y) < 0.16f) return;
            gpActive = true;
            if (!gpGrabbing && grabWin == null) {
                gpYaw = Math.max(-0.6f, Math.min(0.6f, gpYaw + x * 0.045f));
                gpPitch = Math.max(-0.5f, Math.min(0.5f, gpPitch - y * 0.032f));
            }
            gpStickX = x;
            gpStickY = y;
        });
    }

    public void postGamepadTrigger(float v) {
        glQueue.add(() -> gpDepth = Math.max(0f, Math.min(1f, v)));
    }

    public void postGamepadButton(int code, boolean down) {
        glQueue.add(() -> {
            switch (code) {
                case 96: // A
                    if (down) doSelectCurrent();
                    break;
                case 97: // B
                    if (down) back();
                    break;
                case 102: // R1
                    if (down) gamepadGrabStart();
                    else gamepadGrabEnd();
                    break;
                case 108: // START / menu
                    if (down) recenterView();
                    break;
                default:
                    break;
            }
        });
    }

    private void gamepadGrabStart() {
        VpWindows.Hit h = raycastAlong(currentRay());
        if (h == null) return;
        gpGrabbing = true;
        grabPanel = h.panel;
        grabWin = h.win;
        grabDist = h.t;
        computeGrabBasis();
        if (grabWin != null) wm.focus(grabWin);
    }

    private void gamepadGrabEnd() {
        gpGrabbing = false;
        if (grabWin != null) releaseWithPhysics();
        grabWin = null;
        grabPanel = null;
        clearAllHover();
    }

    private void applyGamepadFrame(float dt) {
        if (!gpGrabbing) return;
        // constant linear speed (m/s), frame-rate independent
        float step = 1.15f * dt;
        float dx = gpStickX * step;
        float dy = -gpStickY * step;
        float df = gpDepth * step;
        float mx = hr[0] * dx + hu[0] * dy + hf[0] * df;
        float my = hr[1] * dx + hu[1] * dy + hf[1] * df;
        float mz = hr[2] * dx + hu[2] * dy + hf[2] * df;
        if (grabWin != null) {
            grabWin.moveBy(mx, my, mz);
        } else if (grabPanel == dock) {
            dock.cx += mx;
            dock.cy = Math.max(-1.3f, Math.min(0.7f, dock.cy + my));
            dock.cz = Math.max(-6f, Math.min(-0.5f, dock.cz + mz));
        }
    }

    /** Selection shared by touch tap and gamepad A. */
    private void doSelectCurrent() {
        VpWindows.Hit h = raycastAlong(currentRay());
        if (h == null) return;
        Panel.Button b = h.panel.hoveredButton();
        activity.playClick();
        if (h.panel == dock) {
            if (b != null) dockAction(b.id);
            return;
        }
        if (h.win != null) {
            wm.focus(h.win);
            if (b != null) {
                routeButton(h.win, b.id);
            } else if (h.win.content != null) {
                h.win.content.onBodyClick(h.panel);
            }
        }
    }

    // ================= shared room =================

    private void netSendGaze() {
        try {
            float[] rot = head.rotation();
            net.send(new org.json.JSONObject()
                    .put("t", "G")
                    .put("n", net.selfName())
                    .put("y", (double) headYaw())
                    .put("p", (double) Math.asin(Math.max(-1f, Math.min(1f, -rot[9])))));
        } catch (Exception ignored) {}
    }

    private void netSyncState() {
        String st = netStateJson();
        if (st.isEmpty() || st.equals(lastNetStateSent)) return;
        lastNetStateSent = st;
        try { net.send(new org.json.JSONObject(st)); } catch (Exception ignored) {}
    }

    private String netStateJson() {
        try {
            org.json.JSONObject root = new org.json.JSONObject();
            root.put("t", "S");
            org.json.JSONArray arr = new org.json.JSONArray();
            for (VpWindows.Window w : wm.list) {
                if ("net".equals(w.kind) || "start".equals(w.kind) || "kb".equals(w.kind)
                        || "calib".equals(w.kind)) continue;
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("k", w.kind);
                o.put("x", (double) w.ax); o.put("y", (double) w.ay); o.put("z", (double) w.az);
                o.put("a", (double) w.ayaw); o.put("s", (double) w.scale); o.put("m", w.minimized);
                arr.put(o);
            }
            root.put("w", arr);
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("x", (double) dock.cx); d.put("y", (double) dock.cy);
            d.put("z", (double) dock.cz); d.put("a", (double) dock.yaw);
            root.put("d", d);
            return root.toString();
        } catch (Exception e) { return ""; }
    }

    private void netSendClose(String kind) {
        if (!net.active || net.snapshot().size() == 0) return;
        netClosedKeys.add(kind);
        lastNetStateSent = "";
        try { net.send(new org.json.JSONObject().put("t", "C").put("k", kind)); }
        catch (Exception ignored) {}
    }

    private void handleNetMessage(org.json.JSONObject m) {
        try {
            String t = m.optString("t");
            if ("G".equals(t)) {
                avatars.put(m.optString("n", "?"), new float[]{
                        (float) m.optDouble("y", 0), (float) m.optDouble("p", 0),
                        System.currentTimeMillis()});
                return;
            }
            if ("O".equals(t)) {
                String k = m.optString("k");
                netClosedKeys.remove(k);   // explicit open overrides an earlier close
                if (wm.find(k) == null) {
                    netApplying = true;
                    openWindow(k);
                    netApplying = false;
                }
                return;
            }
            if ("C".equals(t)) {
                String k = m.optString("k");
                netClosedKeys.add(k);
                lastNetStateSent = "";
                VpWindows.Window w = wm.find(k);
                if (w != null) wm.close(w);
                return;
            }
            if ("S".equals(t)) {
                netApplying = true;
                org.json.JSONArray arr = m.optJSONArray("w");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject o = arr.getJSONObject(i);
                        String k = o.optString("k");
                        if (netClosedKeys.contains(k)) continue;
                        VpWindows.Window w = wm.find(k);
                        if (w == null) { openWindow(k); w = wm.find(k); }
                        if (w == null) continue;
                        w.ax = (float) o.optDouble("x", w.ax);
                        w.ay = (float) o.optDouble("y", w.ay);
                        w.az = (float) o.optDouble("z", w.az);
                        w.ayaw = (float) o.optDouble("a", w.ayaw);
                        w.applyScale((float) o.optDouble("s", w.scale));
                        w.layout();
                        w.setMinimized(o.optBoolean("m", w.minimized));
                    }
                }
                org.json.JSONObject d = m.optJSONObject("d");
                if (d != null) {
                    dock.cx = (float) d.optDouble("x", dock.cx);
                    dock.cy = (float) d.optDouble("y", dock.cy);
                    dock.cz = (float) d.optDouble("z", dock.cz);
                    dock.yaw = (float) d.optDouble("a", dock.yaw);
                }
                netApplying = false;
            }
        } catch (Exception ignored) {
            netApplying = false;
        }
    }

    public void netHost() {
        glQueue.add(() -> {
            netClosedKeys.clear();
            net.startHost();
            toast(activity.getString(R.string.net_hosting_toast));
            if (netWin != null) netWin.refresh();
        });
    }

    public void netJoin(String ip) {
        if (ip == null || ip.trim().isEmpty()) return;
        netClosedKeys.clear();
        net.join(ip);
    }

    public void netLeave() {
        glQueue.add(() -> {
            net.stop();
            avatars.clear();
            toast(activity.getString(R.string.net_left));
            if (netWin != null) netWin.refresh();
        });
    }

    public NetService net() { return net; }

    private void drawDiamond(float x, float y, float z, float[] pv) {
        float s = 0.05f;
        peerBuf.position(0);
        peerBuf.put(x).put(y + s).put(z);
        peerBuf.put(x + s).put(y).put(z);
        peerBuf.put(x).put(y - s).put(z);
        peerBuf.put(x - s).put(y).put(z);
        peerBuf.position(0);
        Matrix.setIdentityM(netM, 0);
        Matrix.rotateM(netM, 0, (float) Math.toDegrees(Math.atan2(x, -z)), 0f, 1f, 0f);
        Matrix.multiplyMM(netM2, 0, pv, 0, netM, 0);
        GLES20.glUseProgram(pColor);
        GLES20.glUniformMatrix4fv(uColorMVP, 1, false, netM2, 0);
        GLES20.glUniform4f(uColorColor, 0.85f, 0.87f, 0.92f, 0.85f);
        GLES20.glEnableVertexAttribArray(aColorPos);
        GLES20.glVertexAttribPointer(aColorPos, 3, GLES20.GL_FLOAT, false, 12, peerBuf);
        GLES20.glLineWidth(6f);
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, 4);
        GLES20.glLineWidth(1f);
    }

    private void drawLaser(float[] dir, float len, float[] pv) {
        laserBuf.position(0);
        laserBuf.put(0f).put(0f).put(0f);
        laserBuf.put(dir[0] * len).put(dir[1] * len).put(dir[2] * len);
        laserBuf.position(0);
        GLES20.glUseProgram(pColor);
        Matrix.setIdentityM(tmpM, 0);
        GLES20.glUniformMatrix4fv(uColorMVP, 1, false, tmpM, 0);
        GLES20.glUniform4f(uColorColor, 0.62f, 0.68f, 0.80f, 0.55f);
        GLES20.glEnableVertexAttribArray(aColorPos);
        GLES20.glVertexAttribPointer(aColorPos, 3, GLES20.GL_FLOAT, false, 12, laserBuf);
        GLES20.glLineWidth(4f);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2);
        GLES20.glLineWidth(1f);
    }

    // ================= drawing helpers =================

    private void sortFarToNear(List<VpWindows.Window> vis, float[] eyeView) {
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
                             float w, float h, float angleDeg, float zoom, float overscan,
                             float[] pv, float mode) {
        drawCamQuad(tex, cx, cy, cz, yaw, w, h, angleDeg, zoom, overscan, pv, mode, 0f);
    }

    private void drawCamQuad(int tex, float cx, float cy, float cz, float yaw,
                             float w, float h, float angleDeg, float zoom, float overscan,
                             float[] pv, float mode, float side) {
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
        GLES20.glUniform1f(uCamMode, mode);
        GLES20.glUniform1f(uCamSide, side);
        GLES20.glUniform1f(uCamBright, 1f);
        GLES20.glEnableVertexAttribArray(aCamPos);
        GLES20.glVertexAttribPointer(aCamPos, 3, GLES20.GL_FLOAT, false, 12, Panel.quadPos());
        GLES20.glEnableVertexAttribArray(aCamUV);
        GLES20.glVertexAttribPointer(aCamUV, 2, GLES20.GL_FLOAT, false, 8, Panel.quadUv());
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glEnable(GLES20.GL_BLEND);
    }

    private void drawCamBright(int tex, float cx, float cy, float cz, float yaw,
                             float w, float h, float angleDeg, float zoom, float overscan,
                             float[] pv, float bright) {
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
        GLES20.glUniform1f(uCamMode, 0f);
        GLES20.glUniform1f(uCamSide, 0f);
        GLES20.glUniform1f(uCamBright, bright);
        GLES20.glEnableVertexAttribArray(aCamPos);
        GLES20.glVertexAttribPointer(aCamPos, 3, GLES20.GL_FLOAT, false, 12, Panel.quadPos());
        GLES20.glEnableVertexAttribArray(aCamUV);
        GLES20.glVertexAttribPointer(aCamUV, 2, GLES20.GL_FLOAT, false, 8, Panel.quadUv());
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glEnable(GLES20.GL_BLEND);
    }

    private void drawOesMesh(int tex, Panel p, float[] pv, float side) {
        float sx = p.width / Math.max(0.01f, p.mesh.baseW);
        Matrix.setIdentityM(tmpM2, 0);
        Matrix.translateM(tmpM2, 0, p.cx, p.cy, p.cz);
        if (p.yaw != 0f) Matrix.rotateM(tmpM2, 0, (float) Math.toDegrees(p.yaw), 0f, 1f, 0f);
        Matrix.scaleM(tmpM2, 0, sx, sx, sx);
        Matrix.multiplyMM(tmpM3, 0, pv, 0, tmpM2, 0);

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(pCam);
        GLES20.glUniformMatrix4fv(uCamMVP, 1, false, tmpM3, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
        GLES20.glUniform1i(uCamTex, 0);
        GLES20.glUniform1f(uCamAngle, 0f);
        GLES20.glUniform1f(uCamZoom, 1f);
        GLES20.glUniform1f(uCamOverscan, 1f);
        GLES20.glUniform1f(uCamMode, 0f);
        GLES20.glUniform1f(uCamSide, side);
        GLES20.glUniform1f(uCamBright, 1f);
        GLES20.glEnableVertexAttribArray(aCamPos);
        GLES20.glVertexAttribPointer(aCamPos, 3, GLES20.GL_FLOAT, false, 12, p.mesh.pos);
        GLES20.glEnableVertexAttribArray(aCamUV);
        GLES20.glVertexAttribPointer(aCamUV, 2, GLES20.GL_FLOAT, false, 8, p.mesh.uv);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, p.mesh.count);
        GLES20.glEnable(GLES20.GL_BLEND);
    }

    private void drawPano(float[] pv, float projMode) {
        GLES20.glDisable(GLES20.GL_BLEND);
        Matrix.setIdentityM(tmpM2, 0);
        Matrix.multiplyMM(tmpM3, 0, pv, 0, tmpM2, 0);
        if (panoMode == PANO_IMAGE && panoTex >= 0) {
            GLES20.glUseProgram(pPanel);
            GLES20.glUniformMatrix4fv(uPanelMVP, 1, false, tmpM3, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, panoTex);
            GLES20.glUniform1i(uPanelTex, 0);
            GLES20.glUniform1f(uPanelAlpha, 1f);
            GLES20.glUniform1f(uPanelMode, projMode);
            GLES20.glEnableVertexAttribArray(aPanelPos);
            GLES20.glVertexAttribPointer(aPanelPos, 3, GLES20.GL_FLOAT, false, 12, panoMesh.pos);
            GLES20.glEnableVertexAttribArray(aPanelUV);
            GLES20.glVertexAttribPointer(aPanelUV, 2, GLES20.GL_FLOAT, false, 8, panoMesh.uv);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, panoMesh.count);
        } else if (panoMode == PANO_VIDEO && panoST != null) {
            GLES20.glUseProgram(pCam);
            GLES20.glUniformMatrix4fv(uCamMVP, 1, false, tmpM3, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, panoVidTex);
            GLES20.glUniform1i(uCamTex, 0);
            GLES20.glUniform1f(uCamAngle, 0f);
            GLES20.glUniform1f(uCamZoom, 1f);
            GLES20.glUniform1f(uCamOverscan, 1f);
            GLES20.glUniform1f(uCamMode, projMode);
            GLES20.glUniform1f(uCamSide, 0f);
            GLES20.glEnableVertexAttribArray(aCamPos);
            GLES20.glVertexAttribPointer(aCamPos, 3, GLES20.GL_FLOAT, false, 12, panoMesh.pos);
            GLES20.glEnableVertexAttribArray(aCamUV);
            GLES20.glVertexAttribPointer(aCamUV, 2, GLES20.GL_FLOAT, false, 8, panoMesh.uv);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, panoMesh.count);
        }
        GLES20.glEnable(GLES20.GL_BLEND);
    }

    private void drawTexturedQuad(int tex, float cx, float cy, float cz, float yaw,
                                  float w, float h, float scale, float alpha, float[] pv, float mode) {
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
        GLES20.glUniform1f(uPanelMode, mode);
        GLES20.glEnableVertexAttribArray(aPanelPos);
        GLES20.glVertexAttribPointer(aPanelPos, 3, GLES20.GL_FLOAT, false, 12, Panel.quadPos());
        GLES20.glEnableVertexAttribArray(aPanelUV);
        GLES20.glVertexAttribPointer(aPanelUV, 2, GLES20.GL_FLOAT, false, 8, Panel.quadUv());
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    // ================= dock / status / hand =================

    private void drawDock(Canvas c, Panel p) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(dock.hovered ? 0xF01A1B1E : 0xF0131315);
        c.drawRoundRect(0, 0, p.pw, p.ph, 50, 50, bg);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(3f);
        border.setColor(dock.hovered ? 0xFF8FA3C8 : 0xFF2A3046);
        c.drawRoundRect(2, 2, p.pw - 2, p.ph - 2, 50, 50, border);

        String[] icons = {"⟲", "🎬", "🖼", "360°", "▣", "📝", "🚀", "🎵", "🕒", "🎯", "▦", "🌐", "🌍", "⚙"};
        Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
        icon.setColor(0xFFE8ECF5);
        icon.setTextSize(56f);
        icon.setTextAlign(Paint.Align.CENTER);
        Paint btnBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint btnBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        btnBorder.setStyle(Paint.Style.STROKE);
        btnBorder.setStrokeWidth(2.5f);

        for (int i = 0; i < p.buttons.size() && i < icons.length; i++) {
            Panel.Button b = p.buttons.get(i);
            if (!"recenter".equals(b.id)) {
                VpWindows.Window w = wm.find(b.id);
                boolean open = w != null && !w.minimized;
                boolean mini = w != null && w.minimized;
                btnBg.setColor(b.hover ? 0xFF2E323A : (open ? 0xFF2A2D34 : 0xFF1D1F24));
                icon.setAlpha(mini ? 140 : 255);
            } else {
                btnBg.setColor(b.hover ? 0xFF2E323A : 0xFF1D1F24);
            }
            c.drawRoundRect(b.rect, 20, 20, btnBg);
            btnBorder.setColor(b.hover ? 0xFF8FA3C8 : 0xFF2E3138);
            c.drawRoundRect(b.rect, 20, 20, btnBorder);
            c.drawText(icons[i], (b.rect.left + b.rect.right) / 2f,
                    (b.rect.top + b.rect.bottom) / 2f - (icon.ascent() + icon.descent()) / 2f, icon);
            icon.setAlpha(255);
        }
    }

    private void drawStatusBar(Canvas c, Panel p) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xC2101114);
        c.drawRoundRect(0, 0, p.pw, p.ph, 60, 60, bg);
        Paint left = new Paint(Paint.ANTI_ALIAS_FLAG);
        left.setColor(0xFFA9B2C4);
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
            msgP.setColor(0xFF8FA3C8);
            msgP.setTextSize(36f);
            msgP.setTextAlign(Paint.Align.CENTER);
            c.drawText(activity.getString(R.string.status_no_gyro), p.pw / 2f, 78f, msgP);
        }
    }

    /** Procedural hand sprite: palm + 5 fingers, open or closed pose. */
    private void drawReticle(Canvas c, Panel p) {
        float s = p.pw;
        float cx = s / 2f, cy = s / 2f;
        if (reticleMode == 0) {
            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setColor(0xF2FFFFFF);
            c.drawCircle(cx, cy, s / 5.5f, dot);
            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
            ring.setColor(0x55FFFFFF);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Math.max(2f, s / 26f));
            c.drawCircle(cx, cy, s / 3.1f, ring);
            return;
        }
        boolean grab = reticleMode == 2;
        Paint skin = new Paint(Paint.ANTI_ALIAS_FLAG);
        skin.setColor(0xF2EAEFF7);
        Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        shade.setColor(0x668FA7E8);
        shade.setStyle(Paint.Style.STROKE);
        shade.setStrokeWidth(s * 0.035f);

        // palm
        float palmW = s * (grab ? 0.40f : 0.36f);
        float palmH = s * 0.40f;
        RectF palm = new RectF(cx - palmW / 2f, cy + s * 0.02f, cx + palmW / 2f, cy + s * 0.02f + palmH);
        c.drawRoundRect(palm, palmW / 2.2f, palmW / 2.2f, skin);
        c.drawRoundRect(palm, palmW / 2.2f, palmW / 2.2f, shade);

        // fingers
        float[] angles = {-30f, -10f, 10f, 30f};
        float fl = grab ? s * 0.24f : s * 0.46f;
        float fw = s * 0.115f;
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setColor(0xF2EAEFF7);
        f.setStrokeWidth(fw);
        f.setStrokeCap(Paint.Cap.ROUND);
        for (float a : angles) {
            float rad = (float) Math.toRadians(a);
            float bx = cx + (float) Math.sin(rad) * palmW * 0.34f;
            float by = cy + s * 0.10f;
            float ex = bx + (float) Math.sin(rad) * (grab ? fw * 0.6f : fl * 0.32f);
            float ey = by - fl;
            c.drawLine(bx, by, ex, ey, f);
        }
        // thumb
        float tbx = cx - palmW * 0.52f;
        float tby = cy + s * 0.24f;
        float tex = tbx - (grab ? s * 0.06f : s * 0.20f);
        float tey = tby - (grab ? s * 0.06f : s * 0.16f);
        c.drawLine(tbx, tby, tex, tey, f);
    }

    private void setMsg(String msg, long ms) {
        statusMsg = msg;
        statusMsgUntil = SystemClock.uptimeMillis() + ms;
        dock.dirty = true;
    }

    // ================= touch handling =================

    public void postTouch(int type, float[] xs, float[] ys) {
        glQueue.add(() -> handleTouch(type, xs, ys));
    }

    private void clearAllHover() {
        wm.clearHover();
        dock.clearHover();
        for (Panel tp : gamePanels()) tp.clearHover();
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
            case 0: {
                clearAllHover();
                gpActive = false;
                gpYaw = 0f; gpPitch = 0f;
                downX = xs[0]; downY = ys[0]; lastX = downX; lastY = downY;
                velPx = 0f; velPy = 0f;
                VpWindows.Hit h = raycastAlong(currentRay());
                grabPanel = h != null ? h.panel : null;
                grabWin = (h != null) ? h.win : null;
                grabDist = h != null ? h.t : 1.6f;
                computeGrabBasis();
                if (grabWin != null) wm.focus(grabWin);
                touchMode = TM_PRESSED;
                break;
            }
            case 4: {
                if (xs.length >= 2 && touchMode != TM_IDLE) {
                    touchMode = TM_GESTURE;
                    pinchInit = (float) Math.hypot(xs[1] - xs[0], ys[1] - ys[0]);
                    gestureLastMidY = (ys[0] + ys[1]) / 2f;
                    gestureScale0 = grabWin != null ? grabWin.scale : 1f;
                }
                break;
            }
            case 1: {
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
                    velPx = velPx * 0.55f + dx * 0.45f;
                    velPy = velPy * 0.55f + dy * 0.45f;
                    applyDrag(dx, dy);
                }
                break;
            }
            case 5: {
                if (touchMode == TM_GESTURE && xs.length == 1) {
                    touchMode = TM_DRAG;
                    lastX = xs[0]; lastY = ys[0];
                }
                break;
            }
            case 2:
            case 3: {
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
                } else if (touchMode == TM_DRAG && grabWin != null) {
                    releaseWithPhysics();
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

    private void releaseWithPhysics() {
        if (grabWin == null) return;
        float frameDt = fps > 20f ? 1f / fps : 0.016f;
        float vx = hr[0] * velPx * grabKx / frameDt + hu[0] * (-velPy) * grabKy / frameDt;
        float vy = hr[1] * velPx * grabKx / frameDt + hu[1] * (-velPy) * grabKy / frameDt;
        float vz = hr[2] * velPx * grabKx / frameDt + hu[2] * (-velPy) * grabKy / frameDt;
        float speed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (speed > 0.35f) {
            float cap = 1.6f;
            if (speed > cap) {
                float f = cap / speed;
                vx *= f; vy *= f; vz *= f;
            }
            inertWin = grabWin;
            inertVX = vx; inertVY = vy; inertVZ = vz;
        } else {
            faceUser(grabWin);
        }
    }

    private void faceUser(VpWindows.Window w) {
        float desired = (float) Math.atan2(-w.ax, -w.az);
        float cur = w.ayaw;
        float diff = desired - cur;
        while (diff > (float) Math.PI) diff -= 2f * (float) Math.PI;
        while (diff < -(float) Math.PI) diff += 2f * (float) Math.PI;
        if (Math.abs(diff) < (float) Math.toRadians(10)) {
            w.ayaw = desired;
            w.layout();
        }
    }

    private void updateInertia(float dt) {
        if (inertWin == null) return;
        inertWin.moveBy(inertVX * dt, inertVY * dt, inertVZ * dt);
        float decay = (float) Math.exp(-dt / 0.28f);
        inertVX *= decay; inertVY *= decay; inertVZ *= decay;
        float speed = (float) Math.sqrt(inertVX * inertVX + inertVY * inertVY + inertVZ * inertVZ);
        if (speed < 0.06f) {
            faceUser(inertWin);
            inertWin = null;
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

    /** Hardware volume buttons: cinema volume when the cinema is focused. */
    public void postVolume(boolean up) {
        glQueue.add(() -> {
            VpWindows.Window w = wm.focusedWindow();
            if (w != null && w.content instanceof CinemaWindow) {
                ((CinemaWindow) w.content).nudgeVolume(up ? 0.1f : -0.1f);
            } else {
                activity.runOnUiThread(() -> activity.systemVolume(up));
            }
        });
    }

    private void routeButton(VpWindows.Window w, String id) {
        if ("chrome_close".equals(id)) {
            wm.close(w);
            netSendClose(w.kind);
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
            case "pano": new PanoramaWindow(wm, this); break;
            case "mirror": mirrorWin = new MirrorWindow(wm, this); break;
            case "notes": new NotesWindow(wm, this); break;
            case "apps": new LauncherWindow(wm, this); break;
            case "viz":
                activity.ensureRecordAudio();
                new VisualizerWindow(wm, this);
                break;
            case "clock":
                new ClockWindow(wm, this);
                break;
            case "net":
                netWin = new NetWindow(wm, this);
                break;
            case "game":
                gameWin = new GameWindow(wm, this);
                break;
            case "g2048":
                game2048Win = new Game2048Window(wm, this);
                break;
            case "env": new EnvWindow(wm, this); break;
            case "settings": new SettingsWindow(wm, this); break;
            case "calib": new CalibrationWindow(wm, this); break;
            case "diag": new DiagWindow(wm, this); break;
            case "start":
                if (wm.find("start") == null) new StartWindow(wm, this);
                break;
        }
    }

    // ================= external entry points (main thread) =================

    public void onVrStarted() {
        glQueue.add(() -> {
            head.refreshMount(true);   // gravity-calibrate for how the phone sits now
            loadSession();
            if (settings.autoRecenter()) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> glQueue.add(() -> head.recenter()), 600);
            }
            openWindow("start");
            setMsg(activity.getString(R.string.start_toast), 6000);
            if (System.currentTimeMillis() - settings.lastCrash() < 60000) {
                setMsg(activity.getString(R.string.err_recovered), 6000);
            }
        });
    }

    /** Persists window placement + dock so the desktop survives app restarts. */
    private void saveSession() {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (VpWindows.Window w : wm.list) {
                if ("start".equals(w.kind) || "kb".equals(w.kind) || "calib".equals(w.kind)
                        || "net".equals(w.kind)) continue;
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("k", w.kind);
                o.put("x", (double) w.ax); o.put("y", (double) w.ay); o.put("z", (double) w.az);
                o.put("a", (double) w.ayaw); o.put("s", (double) w.scale); o.put("m", w.minimized);
                arr.put(o);
            }
            org.json.JSONObject root = new org.json.JSONObject();
            root.put("w", arr);
            String lv = settings.lastVideo();
            if (lv != null && !lv.isEmpty()) root.put("v", lv);
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("x", (double) dock.cx); d.put("y", (double) dock.cy);
            d.put("z", (double) dock.cz); d.put("a", (double) dock.yaw);
            root.put("d", d);
            settings.setSession(root.toString());
        } catch (Exception ignored) {}
    }

    private void loadSession() {
        try {
            String str = settings.session();
            if (str == null || str.isEmpty()) return;
            org.json.JSONObject root = new org.json.JSONObject(str);
            org.json.JSONArray arr = root.optJSONArray("w");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.getJSONObject(i);
                    String k = o.optString("k");
                    if (wm.find(k) != null) continue;
                    openWindow(k);
                    VpWindows.Window w = wm.find(k);
                    if (w == null) continue;
                    w.ax = (float) o.optDouble("x", 0);
                    w.ay = (float) o.optDouble("y", 0.18);
                    w.az = (float) o.optDouble("z", -2.3);
                    w.ayaw = (float) o.optDouble("a", 0);
                    w.applyScale((float) o.optDouble("s", 1));
                    w.layout();
                    wm.focus(w);
                    if (o.optBoolean("m")) w.setMinimized(true);
                }
            }
            String lv = root.optString("v", "");
            if (!lv.isEmpty() && wm.find("cinema") != null) {
                try {
                    Uri vu = Uri.parse(lv);
                    VpWindows.Window cw = wm.find("cinema");
                    wm.focus(cw);
                    if (cw.content instanceof CinemaWindow) {
                        CinemaWindow cwi = (CinemaWindow) cw.content;
                        cwi.video.oesTex = vidTex;
                        ensureVideoSurface();
                        cwi.loadVideo(vu, vidST, vidSurface);
                    }
                } catch (Exception ignored) {}
            }
            org.json.JSONObject d = root.optJSONObject("d");
            if (d != null) {
                dock.cx = (float) d.optDouble("x", dock.cx);
                dock.cy = (float) d.optDouble("y", dock.cy);
                dock.cz = (float) d.optDouble("z", dock.cz);
                dock.yaw = (float) d.optDouble("a", dock.yaw);
            }
        } catch (Exception ignored) {}
    }

    public boolean back() {
        VpWindows.Window w = wm.focusedWindow();
        if (w != null) {
            glQueue.add(() -> { wm.close(w); netSendClose(w.kind); });
            return true;
        }
        return false;
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
            if (cw == null) cw = new CinemaWindow(wm, this);
            else wm.focus(cw.window);
            cw.video.oesTex = vidTex;
            ensureVideoSurface();
            cw.loadVideo(u, vidST, vidSurface);
            settings.setLastVideo(u.toString());
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

    // ---- panorama 360 ----

    public int panoMode() { return panoMode; }

    public void requestPanoImage() {
        activity.runOnUiThread(() -> activity.pickImage(true));
    }

    public void requestPanoVideo() {
        activity.runOnUiThread(() -> activity.pickVideo(true));
    }

    public void onPanoImagePicked(Uri u) {
        glQueue.add(() -> {
            PanoramaWindow pw = null;
            for (VpWindows.Window w : wm.list) {
                if (w.content instanceof PanoramaWindow) { pw = (PanoramaWindow) w.content; break; }
            }
            if (pw == null) pw = new PanoramaWindow(wm, this);
            else wm.focus(pw.window);
            pw.loadImage(u);
        });
    }

    public void onPanoVideoPicked(Uri u) {
        glQueue.add(() -> {
            PanoramaWindow pw = null;
            for (VpWindows.Window w : wm.list) {
                if (w.content instanceof PanoramaWindow) { pw = (PanoramaWindow) w.content; break; }
            }
            if (pw == null) pw = new PanoramaWindow(wm, this);
            else wm.focus(pw.window);
            panoStartVideo(u);
        });
    }

    public void setPanoImage(Bitmap b) {
        glQueue.add(() -> {
            if (b == null) {
                toast(activity.getString(R.string.pano_error));
                return;
            }
            if (panoTex >= 0) GLES20.glDeleteTextures(1, new int[]{panoTex}, 0);
            panoTex = GlUtil.createTexture2D();
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, panoTex);
            try {
                android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0);
                panoMode = PANO_IMAGE;
            } catch (Exception e) {
                panoMode = PANO_OFF;
                toast(activity.getString(R.string.pano_error));
            }
            b.recycle();
        });
    }

    private void panoStartVideo(Uri u) {
        panoStopVideo();
        if (panoST == null) {
            panoST = new SurfaceTexture(panoVidTex);
            panoST.setOnFrameAvailableListener(st -> panoFrame = true, camera.callbackHandler());
            panoSurface = new Surface(panoST);
        }
        try {
            panoMp = new MediaPlayer();
            panoMp.setDataSource(activity.getApplicationContext(), u);
            panoMp.setSurface(panoSurface);
            panoMp.setLooping(true);
            panoMp.setOnPreparedListener(p -> {
                panoVideoReady = true;
                panoMode = PANO_VIDEO;
                try { p.start(); } catch (Exception ignored) {}
            });
            panoMp.setOnErrorListener((p, what, extra) -> {
                toast(activity.getString(R.string.pano_error));
                panoMode = PANO_OFF;
                return true;
            });
            panoMp.prepareAsync();
        } catch (Exception e) {
            toast(activity.getString(R.string.pano_error));
        }
    }

    private void panoStopVideo() {
        if (panoMp != null) {
            try { panoMp.release(); } catch (Exception ignored) {}
            panoMp = null;
        }
        panoVideoReady = false;
    }

    public void panoClear() {
        glQueue.add(() -> {
            panoStopVideo();
            panoMode = PANO_OFF;
        });
    }

    public void setPanoProj(int mode) {
        settings.setPanoProj(mode);
        String[] names = {
                activity.getString(R.string.proj_name_equal),
                activity.getString(R.string.proj_name_dome),
                activity.getString(R.string.proj_name_planet)};
        toast(names[Math.max(0, Math.min(2, mode))]);
    }

    // ---- screen mirroring ----

    public int mirrorMode() { return mirrorState; }

    public void requestMirror() {
        activity.runOnUiThread(activity::startMirrorFlow);
    }

    public void stopMirror() {
        glQueue.add(() -> {
            mirrorState = 0;
            if (mirrorWin != null) mirrorWin.screen.visible = false;
            MirrorService.surface = null;
        });
        activity.runOnUiThread(() -> MirrorService.requestStop(activity));
    }

    public void onProjectionResult(int resultCode, Intent data) {
        glQueue.add(() -> {
            if (mirrorST == null) {
                mirrorST = new SurfaceTexture(mirrorTex);
                mirrorST.setOnFrameAvailableListener(st -> mirrorFrame = true, camera.callbackHandler());
                mirrorSurface = new Surface(mirrorST);
            }
            if (mirrorWin == null) mirrorWin = new MirrorWindow(wm, this);
            wm.focus(mirrorWin.window);
            MirrorService.surface = mirrorSurface;
            Intent svc = new Intent(activity, MirrorService.class);
            svc.putExtra("code", resultCode);
            svc.putExtra("data", data);
            activity.startMirrorService(svc);
            android.util.DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            float aspect = (float) dm.widthPixels / (float) dm.heightPixels;
            if (aspect > 1f) aspect = 1f / aspect;
            mirrorWin.setScreenAspect(aspect);
            mirrorState = 1;
            toast(activity.getString(R.string.mirror_live));
        });
    }

    // ---- misc host services ----

    @Override
    public void openStreamDialog() {
        new KeyboardWindow(wm, this,
                activity.getString(R.string.kb_title_url), "https://",
                new KeyboardWindow.Callback() {
                    @Override public void onOk(String t) {
                        CinemaWindow cw = null;
                        for (VpWindows.Window w : wm.list) {
                            if (w.content instanceof CinemaWindow) { cw = (CinemaWindow) w.content; break; }
                        }
                        if (cw == null) cw = new CinemaWindow(wm, VrRenderer.this);
                        else wm.focus(cw.window);
                        cw.video.oesTex = vidTex;
                        ensureVideoSurface();
                        cw.loadVideo(Uri.parse(t.trim()), vidST, vidSurface);
                        toast(activity.getString(R.string.stream_started));
                    }
                    @Override public void onCancel() {}
                });
    }

    @Override
    public void openCalibration() {
        glQueue.add(() -> {
            if (wm.find("calib") == null) new CalibrationWindow(wm, this);
        });
    }

    @Override
    public void openDiagnostics() {
        glQueue.add(() -> {
            if (wm.find("diag") == null) new DiagWindow(wm, this);
        });
    }

    public void applyFrontCam() {
        glQueue.add(() -> {
            camera.front = settings.frontCam();
            camera.stop();
            if (cameraAllowed) camera.start();
        });
    }

    public void vrScreenshot() {
        glQueue.add(() -> shotRequested = true);
    }

    private void captureShot(int w, int h) {
        int n = w * h * 4;
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocateDirect(n);
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
        buf.rewind();
        final Bitmap eye = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] px = new int[n / 4];
        buf.asIntBuffer().get(px);
        for (int y = 0; y < h; y++) {
            int srcRow = h - 1 - y;
            for (int x = 0; x < w; x++) {
                int argb = px[srcRow * w + x];
                eye.setPixel(x, y, (argb & 0xFF000000) | ((argb & 0x00FF0000) >> 8)
                        | ((argb & 0x0000FF00) << 8) | (argb & 0x000000FF));
            }
        }
        new Thread(() -> {
            boolean okSet = false;
            try {
                String name = "VisionPocket_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                        .format(new Date()) + ".png";
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    android.content.ContentValues cv = new android.content.ContentValues();
                    cv.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name);
                    cv.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
                    cv.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/VisionPocket");
                    Uri uri = activity.getContentResolver().insert(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                    if (uri != null) {
                        OutputStream os = activity.getContentResolver().openOutputStream(uri);
                        eye.compress(Bitmap.CompressFormat.PNG, 100, os);
                        if (os != null) os.close();
                        okSet = true;
                    }
                } else {
                    File dir = new File(activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "VisionPocket");
                    if (dir.mkdirs() || dir.isDirectory()) {
                        File f = new File(dir, name);
                        FileOutputStream fos = new FileOutputStream(f);
                        eye.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.close();
                        okSet = true;
                    }
                }
            } catch (Exception ignored) {}
            eye.recycle();
            final boolean ok = okSet;
            glQueue.add(() -> toast(activity.getString(ok
                    ? R.string.shot_saved : R.string.shot_failed)));
        }, "vp-shot").start();
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
    @Override public float fps() { return fps; }
    @Override public void recenterView() {
        head.refreshMount(false);   // refresh user roll steps only — keep gravity cal
        head.recenter();
    }
    @Override public float headYaw() {
        float[] f = head.forward(new float[3]);
        return (float) Math.atan2(f[0], -f[2]);
    }

    @Override
    public void applyResolution() {
        float scale = settings.resScale() * ADAPT_STEPS[adaptIdx];
        fboW = Math.max(8, Math.min(4096, Math.round(eyeW * 2 * scale)));
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

    @Override public void pickVideo() { activity.runOnUiThread(() -> activity.pickVideo(false)); }
    @Override public void pickImage() { activity.runOnUiThread(() -> activity.pickImage(false)); }

    @Override public void exportSettings() { activity.runOnUiThread(() -> activity.exportSettings()); }

    @Override public void importSettings() { activity.runOnUiThread(() -> activity.importSettings()); }

    @Override public void openApp(String kind) { glQueue.add(() -> toggleApp(kind)); }

    @Override public void applyScene(String id) { glQueue.add(() -> applySceneNow(id)); }

    @Override public void pickSrt() { activity.runOnUiThread(() -> activity.pickSrt()); }

    @Override public String pipelineInfo() {
        String ver = "";
        try {
            ver = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        return "v" + ver + " · fbo " + fboW + "x" + fboH
                + " · dist " + settings.distortionPct() + "%"
                + " · IPD " + settings.ipd() + "mm"
                + " · viewRot " + settings.viewRot()
                + " · mount " + (head.mountCalibrated() ? "ok" : "raw")
                + " · " + Math.round(fps) + " fps";
    }

    /** One-tap desktop layouts (Work / Cinema / Game scenes). */
    private void applySceneNow(String id) {
        netApplying = true;
        java.util.List<VpWindows.Window> all = new java.util.ArrayList<>(wm.list);
        for (VpWindows.Window w : all) {
            if ("start".equals(w.kind) || "kb".equals(w.kind) || "calib".equals(w.kind)
                    || "net".equals(w.kind)) continue;
            boolean keep = "work".equals(id)
                    && ("notes".equals(w.kind) || "clock".equals(w.kind) || "mirror".equals(w.kind));
            keep |= "cinema".equals(id) && "cinema".equals(w.kind);
            keep |= "game".equals(id) && "game".equals(w.kind);
            if (!keep) wm.close(w);
        }
        if ("work".equals(id)) {
            placeScene("notes", -1.35f, 0.30f, -2.05f, 1f);
            placeScene("clock", 1.35f, 0.30f, -2.05f, 1f);
            placeScene("mirror", 0f, 0.12f, -2.7f, 1.25f);
            toast(activity.getString(R.string.env_scene_work));
        } else if ("cinema".equals(id)) {
            placeScene("cinema", 0f, 0.22f, -2.6f, 1.35f);
            toast(activity.getString(R.string.env_scene_cinema));
        } else if ("game".equals(id)) {
            placeScene("game", 0f, 0.15f, -2.2f, 1.1f);
            toast(activity.getString(R.string.env_scene_game));
        }
        netApplying = false;
        lastNetStateSent = "";
    }

    private void placeScene(String kind, float x, float y, float z, float s) {
        VpWindows.Window w = wm.find(kind);
        if (w == null) { openWindow(kind); w = wm.find(kind); }
        if (w == null) return;
        w.ax = x; w.ay = y; w.az = z;
        w.ayaw = (float) Math.atan2(-x, -z);
        w.applyScale(s);
        w.layout();
        w.setMinimized(false);
        wm.focus(w);
    }

    public void onSrtPicked(Uri u) {
        if (u == null) return;
        glQueue.add(() -> {
            CinemaWindow cw = null;
            for (VpWindows.Window w : wm.list) {
                if (w.content instanceof CinemaWindow) { cw = (CinemaWindow) w.content; break; }
            }
            if (cw == null) {
                openWindow("cinema");
                VpWindows.Window w = wm.find("cinema");
                if (w != null && w.content instanceof CinemaWindow) cw = (CinemaWindow) w.content;
            }
            if (cw != null) cw.loadSubtitles(u);
        });
    }

    // ================= CameraPipeline.Listener =================

    @Override public void onCameraFailed(String reason) {
        glQueue.add(() -> setMsg(activity.getString(R.string.err_no_camera), 5000));
    }

    // ================= HeadTracker.RecenterListener =================

    @Override public void onRecenter(float deltaYaw) {
        gpYaw = 0f;
        gpPitch = 0f;
        // HeadTracker rotates the VIEW by R(+deltaYaw) (premultiplied into the
        // device->gl rotation). World objects must follow with the SAME world
        // rotation. Panel.rotateBy(d) applies R(-d), so pass -deltaYaw — the
        // old sign sent the whole desktop behind the user's head on every
        // recenter (startup auto-recenter included).
        float d = -deltaYaw;
        float c = (float) Math.cos(d), s = (float) Math.sin(d);
        float nx = ptxCx * c - ptxCz * s;
        float nz = ptxCx * s + ptxCz * c;
        ptxCx = nx;
        ptxCz = nz;
        ptxYaw -= d;
        dock.rotateBy(d);
        wm.onRecenter(d);
        if (gameWin != null) gameWin.rotateOverlay(d);
        setMsg(activity.getString(R.string.status_recentered), 2000);
    }
}
