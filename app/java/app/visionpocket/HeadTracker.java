package app.visionpocket;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.Matrix;

/**
 * Head tracking v3 — thread-safe, noise-limited.
 *
 * The v2.0 design had two real-device defects, both fixed here:
 *  1. DATA RACES: the sensor thread and the GL thread read/wrote the same
 *     scratch quaternions (target/tmpA/tmpB) and matrices concurrently —
 *     torn quaternions render as violent shaking. Fixed with a single lock
 *     and immutable publication: the sensor thread composes into a BACK
 *     buffer and publishes its reference; the GL thread only ever reads a
 *     published, never-mutated snapshot.
 *  2. NOISE PASS-THROUGH: per-event lerp factor 0.45 at 100-500 Hz events
 *     means effectively no smoothing (raw gyro noise reached the screen).
 *     Fixed with a TIME-CONSTANT filter: alpha = 1 - exp(-dt/tau), tau 55 ms,
 *     so the response is identical at any sampling rate. The accelerometer
 *     is low-passed (EMA) before the mount calibration uses it.
 *
 * Everything else from v2.0 stays: quaternion chain, self-verifying recenter,
 * gravity mount basis with residual self-check, first-sample calibration.
 */
public class HeadTracker implements SensorEventListener {

    public interface RecenterListener { void onRecenter(float deltaYaw); }

    private final SensorManager sensorManager;
    private Sensor rotSensor;
    public boolean hasSensor = false;

    private final Object lock = new Object();

    // quaternions [w, x, y, z]
    private final float[] qSensor = unit();    // device -> android world
    private final float[] qMount = unit();     // head -> device (gravity basis)
    private final float[] qRoll = unit();      // user roll steps (viewRot)
    private final float[] qRecenter = unit();  // yaw-only view correction
    private final float[] qFinal = unit();     // smoothed head -> gl

    private static final float[] Q_G = gf();   // android world -> gl (fixed)
    private static float[] gf() { return new float[]{0.70710678f, -0.70710678f, 0f, 0f}; }

    private static final float TAU_MS = 55f;   // smoothing time constant
    private static final float ACC_EMA = 0.25f;

    private final float[] accF = new float[3]; // low-passed accelerometer
    private boolean accValid = false;
    private boolean calibrated = false;
    private float mountResidualDeg = 999f;
    private long lastRotVecMs = 0;
    private long lastEventNs = 0;

    // snapshot pool: 16 buffer pairs, round-robin; a writer NEVER overwrites
    // the currently-published slot, and a full cycle takes >> one frame, so a
    // GL thread holding the published reference can never see a torn matrix.
    private static final int POOL = 16;
    private final float[][] poolRot = new float[POOL][16];
    private final float[][] poolView = new float[POOL][16];
    private int poolIdx = 0;
    private volatile float[] publishedRot;
    private volatile float[] publishedView;
    private final float[] liveRot = new float[16];

    private final float[] target = unit();
    private final float[] tmpA = unit();
    private final float[] tmpB = unit();

    private RecenterListener listener;
    private SettingsStore settings;

    private static float[] unit() { return new float[]{1f, 0f, 0f, 0f}; }

    public HeadTracker(Context ctx) {
        sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
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

    /** Writes q into the next free pool slot and publishes it. Call under lock. */
    private void publish(float[] q) {
        for (int i = 0; i < POOL; i++) {
            poolIdx = (poolIdx + 1) % POOL;
            if (poolRot[poolIdx] != publishedRot) break;   // never touch the live slot
        }
        qToMatrix(q, poolRot[poolIdx]);
        Matrix.transposeM(poolView[poolIdx], 0, poolRot[poolIdx], 0);
        publishedRot = poolRot[poolIdx];
        publishedView = poolView[poolIdx];
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
        Sensor grav = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (grav != null) sensorManager.registerListener(this, grav, SensorManager.SENSOR_DELAY_GAME);
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    // ================= quaternion math (pure, no shared state) =================

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

    private static void matrixToQuat(float[] m, float[] q) {
        float tr = m[0] + m[5] + m[10];
        if (tr > 0f) {
            float s = (float) Math.sqrt(tr + 1f) * 2f;
            q[0] = 0.25f * s;
            q[1] = (m[6] - m[9]) / s;
            q[2] = (m[8] - m[2]) / s;
            q[3] = (m[1] - m[4]) / s;
        } else if (m[0] > m[5] && m[0] > m[10]) {
            float s = (float) Math.sqrt(1f + m[0] - m[5] - m[10]) * 2f;
            q[0] = (m[6] - m[9]) / s;
            q[1] = 0.25f * s;
            q[2] = (m[1] + m[4]) / s;
            q[3] = (m[8] + m[2]) / s;
        } else if (m[5] > m[10]) {
            float s = (float) Math.sqrt(1f + m[5] - m[0] - m[10]) * 2f;
            q[0] = (m[8] - m[2]) / s;
            q[1] = (m[1] + m[4]) / s;
            q[2] = 0.25f * s;
            q[3] = (m[6] + m[9]) / s;
        } else {
            float s = (float) Math.sqrt(1f + m[10] - m[0] - m[5]) * 2f;
            q[0] = (m[1] - m[4]) / s;
            q[1] = (m[8] + m[2]) / s;
            q[2] = (m[6] + m[9]) / s;
            q[3] = 0.25f * s;
        }
        qNormalize(q);
    }

    // ================= calibration & mount =================

    private void calibrate() {
        float ax = accF[0], ay = accF[1], az = accF[2];
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 5f || len > 15f) return;
        ax /= len; ay /= len; az /= len;

        float bx = 0f, by = 0f, bz = 1f;              // back = out of the screen
        float rx = ay * bz - az * by;                  // right = up x back
        float ry = az * bx - ax * bz;
        float rz = ax * by - ay * bx;
        float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rl < 0.1f) return;
        rx /= rl; ry /= rl; rz /= rl;
        float ux = by * rz - bz * ry;                  // up = back x right
        float uy = bz * rx - bx * rz;
        float uz = bx * ry - by * rx;

        float[] m = new float[16];
        Matrix.setIdentityM(m, 0);
        m[0] = rx; m[1] = ry; m[2] = rz;
        m[4] = ux; m[5] = uy; m[6] = uz;
        m[8] = bx; m[9] = by; m[10] = bz;
        matrixToQuat(m, qMount);
        calibrated = true;

        // residual self-check: head up through the full chain vs world up (0,1,0)
        float qx = target[1], qy = target[2], qz = target[3], qw = target[0];
        float tyx = 2f * (qy * 0f - qz * 1f);      // t = 2 * qv x (0,1,0)
        float tyy = 2f * (qz * 0f - qx * 0f);
        float tyz = 2f * (qx * 1f - qy * 0f);
        float ou = 1f + qw * tyy + qy * tyz - qz * tyx;   // y of rotated up
        mountResidualDeg = (float) Math.toDegrees(Math.acos(
                Math.max(-1f, Math.min(1f, ou))));
    }

    public void refreshMount(boolean force) {
        synchronized (lock) {
            if (force && accValid) calibrate();
            int ex = settings == null ? 0 : settings.viewRot();
            qAxis(qRoll, 0f, 0f, 1f, (float) Math.toRadians(ex * 90f));
            composeAndPublish();
        }
    }

    private void composeAndPublish() {
        qMul(qMount, qRoll, tmpA);           // head -> device
        qMul(qSensor, tmpA, tmpB);           // -> android world
        qMul(Q_G, tmpB, tmpA);               // -> gl world
        qMul(qRecenter, tmpA, target);       // yaw correction
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
            // rotate forward by each candidate (v' = v + w t + qv x t, t = 2 qv x v)
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

    /** Current forward direction (GL world) from the published snapshot. */
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

    public boolean mountCalibrated() { return calibrated; }

    /** Live yaw/pitch/roll + health (diagnostics), cheap and race-free. */
    public String angles() {
        float[] m = publishedRot;
        float fx = -m[8], fy = -m[9], fz = -m[10];
        float yaw = (float) Math.toDegrees(Math.atan2(fx, -fz));
        float pitch = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, fy))));
        float roll = (float) Math.toDegrees(Math.atan2(m[1], m[5]));
        long age = lastRotVecMs == 0 ? -1 : System.currentTimeMillis() - lastRotVecMs;
        return "yaw " + Math.round(yaw) + "° pitch " + Math.round(pitch)
                + "° roll " + Math.round(roll) + "° · mount ∠" + Math.round(mountResidualDeg)
                + "° · rv " + (age < 0 ? "—" : age + "ms");
    }

    // ================= sensor events (single writer under lock) =================

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (event.values.length >= 3) {
                synchronized (lock) {
                    if (!accValid) {
                        accF[0] = event.values[0];
                        accF[1] = event.values[1];
                        accF[2] = event.values[2];
                        accValid = true;
                    } else {
                        accF[0] += (event.values[0] - accF[0]) * ACC_EMA;
                        accF[1] += (event.values[1] - accF[1]) * ACC_EMA;
                        accF[2] += (event.values[2] - accF[2]) * ACC_EMA;
                    }
                    if (!calibrated) {
                        calibrate();
                        composeAndPublish();
                    }
                }
            }
            return;
        }
        if (event.values.length < 3) return;

        long now = System.nanoTime();
        float dtMs = lastEventNs == 0 ? TAU_MS : (now - lastEventNs) / 1e6f;
        lastEventNs = now;
        lastRotVecMs = System.currentTimeMillis();

        synchronized (lock) {
            SensorManager.getQuaternionFromVector(tmpA, event.values);
            qSensor[0] = tmpA[0]; qSensor[1] = tmpA[1];
            qSensor[2] = tmpA[2]; qSensor[3] = tmpA[3];

            // time-constant smoothing: same behavior at any sampling rate
            float alpha = 1f - (float) Math.exp(-dtMs / TAU_MS);
            if (alpha > 1f) alpha = 1f;
            qMul(qMount, qRoll, tmpA);
            qMul(qSensor, tmpA, tmpB);
            qMul(Q_G, tmpB, tmpA);
            qMul(qRecenter, tmpA, target);
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
