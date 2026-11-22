package app.visionpocket;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Size;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;

/**
 * Camera2 passthrough pipeline. The SurfaceTexture is created on the GL thread
 * (renderer owns it); this class opens the camera and streams frames into it.
 */
public class CameraPipeline {

    public interface Listener {
        void onCameraFailed(String reason);
    }

    private final Context ctx;
    private final Listener listener;
    private HandlerThreadHolder threads;

    private CameraDevice camera;
    private CameraCaptureSession session;
    private SurfaceTexture surfaceTexture;
    private Surface previewSurface;
    private volatile boolean started = false;

    /** false = back camera (MR), true = front camera (selfie AR). */
    public volatile boolean front = false;

    public int sensorOrientation = 90;
    public int streamWidth = 1280, streamHeight = 720;
    public volatile boolean frameAvailable = false;
    public volatile boolean hasFrames = false;

    private static class HandlerThreadHolder {
        final android.os.HandlerThread thread = new android.os.HandlerThread("vp-camera");
        android.os.Handler handler;
        void ensure() {
            if (!thread.isAlive()) thread.start();
            handler = new android.os.Handler(thread.getLooper());
        }
    }

    public CameraPipeline(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    public android.os.Handler callbackHandler() {
        if (threads == null) threads = new HandlerThreadHolder();
        threads.ensure();
        return threads.handler;
    }

    /** Called on the GL thread after the SurfaceTexture exists. */
    public void attach(SurfaceTexture st) {
        surfaceTexture = st;
        surfaceTexture.setDefaultBufferSize(streamWidth, streamHeight);
    }

    /** Called on the GL thread once camera permission is granted. */
    @SuppressLint("MissingPermission")
    public void start() {
        if (started) return;
        if (ctx.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (listener != null) listener.onCameraFailed("permission");
            return;
        }
        if (surfaceTexture == null) return;
        if (threads == null) threads = new HandlerThreadHolder();
        threads.ensure();
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            String id = findCamera(cm, front);
            if (id == null) { fail("no " + (front ? "front" : "back") + " camera"); return; }
            CameraCharacteristics ch = cm.getCameraCharacteristics(id);
            Integer orient = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = orient == null ? 90 : orient;
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                Size best = pickSize(map.getOutputSizes(SurfaceTexture.class), streamWidth, streamHeight);
                if (best != null) {
                    streamWidth = best.getWidth();
                    streamHeight = best.getHeight();
                    surfaceTexture.setDefaultBufferSize(streamWidth, streamHeight);
                }
            }
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice d) { camera = d; createSession(); }
                @Override public void onDisconnected(CameraDevice d) { d.close(); if (camera == d) camera = null; }
                @Override public void onError(CameraDevice d, int error) { d.close(); if (camera == d) camera = null; fail("camera error " + error); }
            }, threads.handler);
            started = true;
        } catch (Exception e) {
            fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void fail(String msg) { started = false; if (listener != null) listener.onCameraFailed(msg); }

    private String findCamera(CameraManager cm, boolean front) {
        int want = front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
        try {
            for (String id : cm.getCameraIdList()) {
                Integer f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if (f != null && f == want) return id;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Size pickSize(Size[] sizes, int wantW, int wantH) {
        if (sizes == null || sizes.length == 0) return null;
        float wantAspect = (float) wantW / wantH;
        Size best = null; float bestScore = Float.MAX_VALUE;
        for (Size s : sizes) {
            if (s.getWidth() * s.getHeight() > 1920 * 1440) continue; // keep it cheap
            float aspect = (float) s.getWidth() / s.getHeight();
            float score = Math.abs(aspect - wantAspect) * 10f + Math.abs(s.getWidth() * s.getHeight() - wantW * wantH) / (wantW * wantH * 10f);
            if (score < bestScore) { bestScore = score; best = s; }
        }
        return best;
    }

    private void createSession() {
        try {
            previewSurface = new Surface(surfaceTexture);
            List<Surface> outs = new ArrayList<>(1);
            outs.add(previewSurface);
            camera.createCaptureSession(outs, new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession s) {
                    session = s;
                    try {
                        CaptureRequest.Builder b = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        b.addTarget(previewSurface);
                        b.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                        session.setRepeatingRequest(b.build(), null, threads != null ? threads.handler : null);
                        hasFrames = false;
                    } catch (Exception e) { fail("repeating request: " + e.getMessage()); }
                }
                @Override public void onConfigureFailed(CameraCaptureSession s) { fail("session config failed"); }
            }, threads.handler);
        } catch (Exception e) {
            fail("createSession: " + e.getMessage());
        }
    }

    public void stop() {
        started = false;
        try { if (session != null) { session.close(); session = null; } } catch (Exception ignored) {}
        try { if (camera != null) { camera.close(); camera = null; } } catch (Exception ignored) {}
        try { if (previewSurface != null) { previewSurface.release(); previewSurface = null; } } catch (Exception ignored) {}
    }
}
