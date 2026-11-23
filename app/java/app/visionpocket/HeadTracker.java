package app.visionpocket;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.Matrix;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

/**
 * Head tracking v4 — ported from the Google Cardboard SDK (Apache 2.0).
 *
 * Source of truth: github.com/googlevr/cardboard
 *   sdk/head_tracker.cc        -> GetRotation() composition + the two
 *                                 per-viewport rotation tables (copied
 *                                 VERBATIM below)
 *   sdk/include/cardboard.h    -> World frame definition: right handed,
 *                                 x right, y up, z backwards (OpenGL).
 *   hellocardboard-android     -> pose used directly as the view matrix.
 *
 * The Cardboard chain is:
 *   view = SensorToDisplay[viewport] * sensorRotation * EkfToHeadTracker[viewport]
 * where sensorRotation maps the Z-up sensor world into the device frame.
 * Here sensorRotation = inverse(TYPE_ROTATION_VECTOR quaternion): Android's
 * own fused gyro+accel orientation (device -> world), which replaces the
 * SDK's EKF and is gravity-referenced EVERY FRAME - so no mount calibration
 * can freeze a wrong tilt (the v1.9.5-v3.1 failure class).
 *
 * Thread-safety and smoothing retained from v2.1: single writer lock,
 * immutable snapshot pool, time-constant nlerp.
 */
public class HeadTracker implements SensorEventListener {

    public interface RecenterListener { void onRecenter(float deltaYaw); }

    private final SensorManager sensorManager;
    private final Display display;
    private Sensor rotSensor;
    public boolean hasSensor = false;

    private final Object lock = new Object();

    // quaternions [w, x, y, z]
    private final float[] qSensorInv = unit();  // android world (Z-up) -> device
    private final float[] qRoll = unit();       // user roll steps (viewRot)
    private final float[] qRecenter = unit();   // yaw-only view correction
    private final float[] qFinal = unit();      // smoothed view (world -> gl)

    // --- VERBATIM from cardboard sdk/head_tracker.cc (Apache 2.0) ---
    // Stored [x, y, z, w] in the SDK; converted to [w, x, y, z] here.
    // Index: 0 LandscapeLeft (ROTATION_90), 1 LandscapeRight (ROTATION_270),
    //        2 Portrait (ROTATION_0), 3 PortraitUpsideDown (ROTATION_180).
    private static final float[][] SENSOR_TO_DISPLAY = {
            gf(0.7071067811865476f, 0f, 0f, 0.7071067811865476f),   // LL: +90 deg about Z
            gf(0.7071067811865476f, 0f, 0f, -0.7071067811865476f),  // LR: -90 deg about Z
            gf(1f, 0f, 0f, 0f),                                     // P: identity
            gf(0f, 0f, 0f, 1f)                                      // PUD: 180 deg about Z
    };
    private static final float[][] EKF_TO_HEAD_TRACKER = {
            gf(0.5f, 0.5f, -0.5f, -0.5f),                           // LL: YawPitchRoll(-pi/2, 0, -pi/2)
            gf(0.5f, 0.5f, 0.5f, 0.5f),                             // LR: YawPitchRoll( pi/2, 0,  pi/2)
            gf(0.7071067811865476f, 0.7071067811865476f, 0f, 0f),   // P: YawPitchRoll( pi/2, pi/2,  pi/2)
            gf(0f, 0f, -0.7071067811865476f, -0.7071067811865476f)  // PUD: YawPitchRoll(-pi/2, -pi/2, -pi/2)
    };
    /** [w, x, y, z] convenience builder matching the SDK's QuaternionType(x,y,z,w). */
    private static float[] gf(float w, float x, float y, float z) {
        return new float[]{w, x, y, z};
    }

    private static final float TAU_MS = 55f;   // smoothing time constant

    private long lastEventNs = 0;
    private long lastRotVecMs = 0;
    private int lastViewport = 0;

    // snapshot pool: 16 buffer pairs, round-robin; a writer NEVER overwrites
    // the currently-published slot, so a GL thread holding the reference can
    // never see a torn matrix.
    private static final int POOL = 16;
    private final float[][] poolRot = new float[POOL][16];
    private final float[][] poolView = new float[POOL][16];
    private int poolIdx = 0;
    private volatile float[] publishedRot;
    private volatile float[] publishedView;

    private final float[] target = unit();
    private final float[] tmpA = unit();
    private final float[] tmpB = unit();
    private final float[] liveRot = new float[16];

    private RecenterListener listener;
    private SettingsStore settings;

    private static float[] unit() { return new float[]{1f, 0f, 0f, 0f}; }

    public HeadTracker(Context ctx) {
        sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        display = wm.getDefaultDisplay();
        rotSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rotSensor == null) {
            rotSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        }
        hasSensor = rotSensor != null;
        Matrix.setIdentityM(liveRot, 0);
        Matrix.setIdentityM(poolRot[0], 0);
        Matrix.setIdentityM(poolView[0], 0);
        publishedRot = poolRot[0];
        publishedView = poolView[0];
    }

    public void setRecenterListener(RecenterListener l) { listener = l; }

    public void attach(SettingsStore st) {
        settings = st;
        refreshMount(true);
    }

    public void start() {
        if (rotSensor != null) {
            sensorManager.registerListener(this, rotSensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    // ================= quaternion math (pure) =================

    private static void qMul(float[] a, float[] b, float[] out) {
        float w = a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3];
        float x = a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2];
        float y = a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1];
        float z = a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0];
        out[0] = w; out[1] = x; out[2] = y; out[3] = z;
    }

    private static void qAxis(float[] out, float ax, float ay, float az, float rad) {
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-8f) { out[0] = 1; out[1] = out[2] = out[3] = 0; return; }
        float s = (float) Math.sin(rad / 2f);
        out[0] = (float) Math.cos(rad / 2f);
        out[1] = ax / len * s;
        out[2] = ay / len * s;
        out[3] = az / len * s;
    }

    private static void qNormalize(float[] q) {
        float l = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        if (l < 1e-8f) { q[0] = 1; q[1] = q[2] = q[3] = 0; return; }
        q[0] /= l; q[1] /= l; q[2] /= l; q[3] /= l;
    }

    private static void qToMatrix(float[] q, float[] m) {
        float x = q[1], y = q[2], z = q[3], w = q[0];
        float x2 = x + x, y2 = y + y, z2 = z + z;
        float xx = x * x2, xy = x * y2, xz = x * z2;
        float yy = y * y2, yz = y * z2, zz = z * z2;
        float wx = w * x2, wy = w * y2, wz = w * z2;
        m[0] = 1f - (yy + zz); m[1] = xy + wz;       m[2] = xz - wy;
        m[4] = xy - wz;        m[5] = 1f - (xx + zz); m[6] = yz + wx;
        m[8] = xz + wy;        m[9] = yz - wx;        m[10] = 1f - (xx + yy);
        m[3] = m[7] = m[11] = m[12] = m[13] = m[14] = 0f;
        m[15] = 1f;
    }

    // ================= the Cardboard chain =================

    /** Map android display rotation to the SDK's viewport index. */
    private int viewportFromDisplay() {
        int r = Surface.ROTATION_0;
        try { r = display.getRotation(); } catch (Exception ignored) {}
        switch (r) {
            case Surface.ROTATION_90: return 0;      // kLandscapeLeft
            case Surface.ROTATION_270: return 1;     // kLandscapeRight
            case Surface.ROTATION_180: return 3;     // kPortraitUpsideDown
            default: return 2;                       // kPortrait
        }
    }

    /** Compose the view quaternion: SensorToDisplay * inv(rv) * EkfToHeadTracker. */
    private void composeView() {
        int vp = viewportFromDisplay();
        lastViewport = vp;
        qMul(SENSOR_TO_DISPLAY[vp], qSensorInv, tmpA);   // device -> display
        qMul(tmpA, EKF_TO_HEAD_TRACKER[vp], tmpB);       // -> OpenGL world
        qMul(qRoll, tmpB, tmpA);                         // user roll steps
        qMul(qRecenter, tmpA, target);                   // yaw correction
    }

    /** Writes q into the next free pool slot and publishes it. Call under lock. */
    private void publish(float[] q) {
        for (int i = 0; i < POOL; i++) {
            poolIdx = (poolIdx + 1) % POOL;
            if (poolRot[poolIdx] != publishedRot) break;   // never touch the live slot
        }
        qToMatrix(q, poolView[poolIdx]);                   // world -> camera
        Matrix.transposeM(poolRot[poolIdx], 0, poolView[poolIdx], 0); // head -> world
        publishedRot = poolRot[poolIdx];
        publishedView = poolView[poolIdx];
    }

    private void composeAndPublish() {
        composeView();
        publish(target);
    }

    // ================= recenter (self-verifying, lock-safe) =================

    public float recenter() {
        synchronized (lock) {
            float fx = -publishedRot[8], fy = -publishedRot[9], fz = -publishedRot[10];
            float a = (float) Math.atan2(fx, -fz);
            float[] c1 = unit(), c2 = unit();
            qAxis(c1, 0f, 1f, 0f, a);
            qAxis(c2, 0f, 1f, 0f, -a);
            float t1x = 2f * (c1[2] * fz - c1[3] * fy);
            float t1y = 2f * (c1[3] * fx - c1[1] * fz);
            float t1z = 2f * (c1[1] * fy - c1[2] * fx);
            float v1z = fz + c1[0] * t1z + c1[1] * t1y - c1[2] * t1x;
            float t2x = 2f * (c2[2] * fz - c2[3] * fy);
            float t2y = 2f * (c2[3] * fx - c2[1] * fz);
            float t2z = 2f * (c2[1] * fy - c2[2] * fx);
            float v2z = fz + c2[0] * t2z + c2[1] * t2y - c2[2] * t2x;
            float chosen = (-v1z) >= (-v2z) ? a : -a;
            qAxis(qRecenter, 0f, 1f, 0f, chosen);
            composeAndPublish();
            if (listener != null) listener.onRecenter(chosen);
            return chosen;
        }
    }

    // ================= GL-thread queries (lock-free, snapshot reads) =================

    public float[] forward(float[] out3) {
        float[] m = publishedRot;
        out3[0] = -m[8];
        out3[1] = -m[9];
        out3[2] = -m[10];
        return out3;
    }

    /** head -> gl rotation matrix (published snapshot; treat as immutable). */
    public float[] rotation() { return publishedRot; }

    /** view matrix (published snapshot; treat as immutable). */
    public float[] view() { return publishedView; }

    public boolean mountCalibrated() { return hasSensor; }

    public String angles() {
        float[] m = publishedRot;
        float fx = -m[8], fy = -m[9], fz = -m[10];
        float yaw = (float) Math.toDegrees(Math.atan2(fx, -fz));
        float pitch = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, fy))));
        float roll = (float) Math.toDegrees(Math.atan2(m[1], m[5]));
        long age = lastRotVecMs == 0 ? -1 : System.currentTimeMillis() - lastRotVecMs;
        String vp;
        switch (lastViewport) {
            case 0: vp = "LL"; break;
            case 1: vp = "LR"; break;
            case 3: vp = "PUD"; break;
            default: vp = "P";
        }
        return "yaw " + Math.round(yaw) + "° pitch " + Math.round(pitch)
                + "° roll " + Math.round(roll) + "° · vp " + vp
                + " · rv " + (age < 0 ? "—" : age + "ms");
    }

    public void refreshMount(boolean force) {
        synchronized (lock) {
            int ex = settings == null ? 0 : settings.viewRot();
            qAxis(qRoll, 0f, 0f, 1f, (float) Math.toRadians(ex * 90f));
            composeAndPublish();
        }
    }

    // ================= sensor events (single writer under lock) =================

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values.length < 3) return;
        long now = System.nanoTime();
        float dtMs = lastEventNs == 0 ? TAU_MS : (now - lastEventNs) / 1e6f;
        lastEventNs = now;
        lastRotVecMs = System.currentTimeMillis();

        synchronized (lock) {
            // rotation vector: device -> android world (Z-up); invert to get
            // the SDK's sensorRotation slot (world -> device)
            SensorManager.getQuaternionFromVector(tmpA, event.values);
            qSensorInv[0] = tmpA[0];
            qSensorInv[1] = -tmpA[1];
            qSensorInv[2] = -tmpA[2];
            qSensorInv[3] = -tmpA[3];
            qNormalize(qSensorInv);

            composeView();

            // time-constant smoothing: same behavior at any sampling rate
            float alpha = 1f - (float) Math.exp(-dtMs / TAU_MS);
            if (alpha > 1f) alpha = 1f;
            for (int i = 0; i < 4; i++) qFinal[i] += (target[i] - qFinal[i]) * alpha;
            qNormalize(qFinal);
            if (qFinal[0] < 0) { qFinal[0] = -qFinal[0]; qFinal[1] = -qFinal[1];
                qFinal[2] = -qFinal[2]; qFinal[3] = -qFinal[3]; }

            publish(qFinal);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
