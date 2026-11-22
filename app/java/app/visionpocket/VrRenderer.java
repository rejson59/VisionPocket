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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Stereoscopic MR renderer: camera passthrough + world-locked panels,
 * barrel-distortion pass per eye, gaze & tap interaction.
 */
public class VrRenderer implements GLSurfaceView.Renderer, SceneHost, CameraPipeline.Listener, HeadTracker.RecenterListener {

    private static final float FOVY_DEG = 78f;
    private static final float PASSTHROUGH_DIST = 8f;
    private static final float OVERSCAN = 1.4f;

    private final MainActivity activity;
    private final SettingsStore settings;
    private final HeadTracker head;
    private final CameraPipeline camera;
    private final SceneManager scenes;

    // --- programs ---
    private int pPanel, pCam, pDist, pColor;
    private int uPanelMVP, aPanelPos, aPanelUV, uPanelTex, uPanelAlpha;
    private int uCamMVP, aCamPos, aCamUV, uCamTex, uCamAngle, uCamZoom, uCamOverscan;
    private int aDistPos, uDistScene, uDistEyeCenter, uDistHalf, uDistAspect, uDistK1, uDistK2;
    private int uColorMVP, aColorPos, uColorColor;

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
    private final float[] proj = new float[16];

    // --- passthrough world anchor ---
    private float ptxCx = 0f, ptxCy = 0f, ptxCz = -PASSTHROUGH_DIST, ptxYaw = 0f;
    private float ptxW = 12f, ptxH = 8f;

    // --- head-locked panels ---
    private final Panel statusBar;
    private final Panel reticle;
    private String statusMsg = null;
    private long statusMsgUntil = 0;
    private boolean gyroWarned = false;

    // --- gaze ---
    private Panel.Button focused = null;
    private float reticleScale = 1f;

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
        this.scenes = new SceneManager(this);
        head.setRecenterListener(this);

        statusBar = new Panel(0f, 0.42f, -1.05f, 0f, 0.85f, 0.12f, 1000f);
        statusBar.hook = this::drawStatusBar;
        reticle = new Panel(0f, 0f, -0.7f, 0f, 0.055f, 0.055f, 1000f);
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

        // GL textures of panels are invalidated with the context
        statusBar.tex = -1; statusBar.dirty = true;
        reticle.tex = -1; reticle.dirty = true;
        resetSceneTextures();

        if (cameraAllowed) camera.start();
    }

    private void resetSceneTextures() {
        List<Panel> all = scenes.home.panels();
        addAll(all, scenes.settingsScene.panels());
        addAll(all, scenes.cinema.panels());
        for (Panel p : all) { p.tex = -1; p.dirty = true; }
    }

    private void addAll(List<Panel> dst, List<Panel> src) { for (Panel p : src) dst.add(p); }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        scrW = width;
        scrH = height;
        eyeW = Math.max(8, width / 2);
        eyeH = Math.max(8, height);
        Matrix.perspectiveM(proj, 0, FOVY_DEG, (float) eyeW / (float) eyeH, 0.05f, 80f);

        // passthrough quad geometry: covers FOV * OVERSCAN at PASSTHROUGH_DIST
        float tanHalf = (float) Math.tan(Math.toRadians(FOVY_DEG / 2f));
        float halfH = PASSTHROUGH_DIST * tanHalf * OVERSCAN;
        float halfW = halfH * ((float) eyeW / (float) eyeH);
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

        scenes.tick();
        float fade = scenes.fade();

        long upMs = SystemClock.uptimeMillis();
        if (upMs - lastStatusMs >= 1000) {
            updateStatus();
            lastStatusMs = upMs;
        }
        scenes.update();

        // --- gaze raycast (head center) ---
        float[] rot = head.rotation();
        float fx = -rot[8], fy = -rot[9], fz = -rot[10];
        focused = null;
        List<Panel> panels = scenes.panels();
        for (Panel p : panels) {
            Panel.Button b = p.hitTest(0f, 0f, 0f, fx, fy, fz);
            if (b != null) focused = b;
        }
        float targetScale = focused != null ? 1.7f : 1f;
        reticleScale += (targetScale - reticleScale) * 0.35f;

        // --- camera frames ---
        if (camera.frameAvailable) {
            camera.frameAvailable = false;
            try { camST.updateTexImage(); } catch (Exception ignored) {}
        }
        if (vidFrame) {
            vidFrame = false;
            if (scenes.current() == SceneManager.SceneId.CINEMA && vidST != null) {
                try { vidST.updateTexImage(); } catch (Exception ignored) {}
            }
        }

        // --- scene into FBO, once per eye ---
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        CinemaScene cinema = scenes.cinema();
        boolean drawVideo = scenes.current() == SceneManager.SceneId.CINEMA && cinema.videoReady;

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

            if (settings.passthrough() && cameraAllowed && camFrameEver) {
                drawCamQuad(camTex, ptxCx, ptxCy, ptxCz, ptxYaw, ptxW, ptxH,
                        settings.rotDeg(), settings.zoom(), OVERSCAN, pv);
            } else {
                drawBackdropNdc();
            }

            if (drawVideo) {
                drawCamQuad(vidTex, cinema.videoCx, cinema.videoCy, cinema.videoCz, cinema.videoYaw,
                        cinema.videoW, cinema.videoH, 0f, 1f, 1f, pv);
            }

            for (Panel p : panels) {
                p.renderIfNeeded();
                drawTexturedQuad(p.tex, p.cx, p.cy, p.cz, p.yaw, p.width, p.height, 1f, 1f, pv);
            }

            statusBar.renderIfNeeded();
            drawTexturedQuad(statusBar.tex, statusBar.cx, statusBar.cy, statusBar.cz, 0f,
                    statusBar.width, statusBar.height, 1f, 0.92f, headPv);
            reticle.renderIfNeeded();
            drawTexturedQuad(reticle.tex, reticle.cx, reticle.cy, reticle.cz, 0f,
                    reticle.width, reticle.height, reticleScale, 0.95f, headPv);
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
        GLES20.glUniform1f(uDistAspect, (float) eyeW / (float) eyeH);
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

    private void drawBackdropNdc() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(pColor);
        Matrix.setIdentityM(tmpM, 0);
        GLES20.glUniformMatrix4fv(uColorMVP, 1, false, tmpM, 0);
        GLES20.glUniform4f(uColorColor, 0.03f, 0.035f, 0.055f, 1f);
        GLES20.glEnableVertexAttribArray(aColorPos);
        GLES20.glVertexAttribPointer(aColorPos, 3, GLES20.GL_FLOAT, false, 12, Panel.quadPos());
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
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

    // ================= status bar / reticle =================

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
        Paint msgP = new Paint(Paint.ANTI_ALIAS_FLAG);
        msgP.setColor(0xFF5B8DEF);
        msgP.setTextSize(40f);
        msgP.setTextAlign(Paint.Align.CENTER);
        msgP.setFakeBoldText(true);

        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        c.drawText("VisionPocket", 36f, 78f, left);
        c.drawText(time, p.pw / 2f, 82f, mid);
        c.drawText(Math.round(fps) + " FPS · " + activity.batteryPct() + "%", p.pw - 36f, 76f, right);

        long now = SystemClock.uptimeMillis();
        if (statusMsg != null && now < statusMsgUntil) {
            c.drawText(statusMsg, p.pw / 2f, 82f, msgP);
        } else if (!gyroWarned && !head.hasSensor) {
            gyroWarned = true;
            c.drawText(activity.getString(R.string.status_no_gyro), p.pw / 2f, 82f, msgP);
        }
    }

    private void drawReticle(Canvas c, Panel p) {
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(0xF2FFFFFF);
        float r = p.pw / 4f;
        c.drawCircle(p.pw / 2f, p.ph / 2f, r, dot);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setColor(0x66FFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Math.max(2f, p.pw / 22f));
        c.drawCircle(p.pw / 2f, p.ph / 2f, p.pw / 2.6f, ring);
    }

    private void setMsg(String msg, long ms) {
        statusMsg = msg;
        statusMsgUntil = SystemClock.uptimeMillis() + ms;
    }

    private void updateStatus() {
        statusBar.dirty = true;
    }

    // ================= external entry points (main thread) =================

    public void onTap() { glQueue.add(this::doTap); }

    private void doTap() {
        if (focused != null) {
            scenes.onClick(focused);
        } else if (scenes.current() == SceneManager.SceneId.CINEMA && scenes.cinema().videoReady) {
            scenes.cinema().togglePlay();
        }
    }

    /** @return true if the back press was consumed (navigated home). */
    public boolean back() {
        if (scenes.current() != SceneManager.SceneId.HOME) {
            glQueue.add(() -> scenes.show(SceneManager.SceneId.HOME));
            return true;
        }
        return false;
    }

    public void setCameraAllowed(boolean allowed) {
        cameraAllowed = allowed;
        glQueue.add(() -> { if (cameraAllowed) camera.start(); });
    }

    public void loadVideo(Uri u) {
        glQueue.add(() -> {
            ensureVideoSurface();
            scenes.cinemaLoadVideo(u, vidST, vidSurface);
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

    /** Re-starts the camera after app resume (called from the main thread). */
    public void onAppResume() {
        glQueue.add(() -> { if (cameraAllowed && camST != null) camera.start(); });
    }

    // ================= SceneHost =================

    @Override public SettingsStore settings() { return settings; }
    @Override public MainActivity activity() { return activity; }

    @Override public void recenterView() { head.recenter(); }

    @Override public void applyResolution() {
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

    @Override public void showScene(SceneManager.SceneId s) { scenes.show(s); }

    @Override public void requestVideoPicker() {
        activity.runOnUiThread(activity::openVideoPicker);
    }

    // ================= CameraPipeline.Listener =================

    @Override public void onCameraFailed(String reason) {
        glQueue.add(() -> {
            setMsg(activity.getString(R.string.err_no_camera), 5000);
        });
    }

    // ================= HeadTracker.RecenterListener =================

    @Override public void onRecenter(float deltaYaw) {
        // world anchors rotate by -deltaYaw
        float c = (float) Math.cos(deltaYaw), s = (float) Math.sin(deltaYaw);
        float nx = ptxCx * c - ptxCz * s;
        float nz = ptxCx * s + ptxCz * c;
        ptxCx = nx;
        ptxCz = nz;
        ptxYaw -= deltaYaw;
        scenes.onRecenter(deltaYaw);
        setMsg(activity.getString(R.string.status_recentered), 2000);
    }
}
