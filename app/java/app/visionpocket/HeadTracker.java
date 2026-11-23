package app.visionpocket;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.Matrix;

/**
 * Head tracking v2 — full quaternion rewrite.
 *
 * Chain (applied to a head-frame vector, right to left):
 *   head --qRoll--> head' --qMountBasis--> device --qSensor--> android world
 *        --qG--> GL world --qRecenter--> yaw-corrected GL world
 *
 * Design goals (learned from real-device failures):
 *  - no matrix lerp / re-orthonormalization (that pipeline corrupted slowly);
 *    nlerp on quaternions instead
 *  - recenter is SELF-VERIFYING: both candidate yaw signs are tested against
 *    the forward vector, the one that maps it nearest to -Z wins — no sign
 *    conventions to guess
 *  - the mount basis is built from the ACCELEROMETER (physical up), never from
 *    display-rotation tables; calibration auto-runs on the first gravity sample
 *    (the v1.9.5/1.9.6 race) and on every VR start
 *  - live diagnostics: yaw/pitch/roll, mount residual, sensor age
 */
public class HeadTracker implements SensorEventListener {

    public interface RecenterListener { void onRecenter(float deltaYaw); }

    private final SensorManager sensorManager;
    private final Context appCtx;
    private Sensor rotSensor;
    public boolean hasSensor = false;

    // quaternions, [w, x, y, z]
    private final float[] qSensor = unit();    // device -> android world
    private final float[] qMount = unit();     // head -> device (gravity basis)
    private final float[] qRoll = unit();      // head roll steps (viewRot)
    private final float[] qRecenter = unit();  // yaw-only view correction
    private final float[] qFinal = unit();     // head -> gl (smoothed)
    private static final float[] Q_G = gf();   // android world -> gl (fixed)

    private final float[] acc = new float[3];
    private boolean accValid = false;
    private boolean calibrated = false;
    private float mountResidualDeg = 999f;
    private long lastRotVecMs = 0;

    private final float[] target = unit();
    private final float[] tmpA = unit();
    private final float[] tmpB = unit();

    private final float[] rotation = new float[16]; // head -> gl (matrix)
    private final float[] view = new float[16];     // gl -> head

    private final float[] fwd = new float[3];
    private RecenterListener listener;
    private SettingsStore settings;

    private static float[] unit() { return new float[]{1f, 0f, 0f, 0f}; }

    /** android(X east, Y north, Z up) -> GL(x right, y up, z back): -90 deg about X. */
    private static float[] gf() { return new float[]{0.70710678f, -0.70710678f, 0f, 0f}; }

    public HeadTracker(Context ctx) {
        appCtx = ctx;
        sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        rotSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rotSensor == null) {
            rotSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        }
        hasSensor = rotSensor != null;
        Matrix.setIdentityM(rotation, 0);
        Matrix.setIdentityM(view, 0);
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

    // ================= quaternion math =================

    private static void qMul(float[] a, float[] b, float[] out) {
        float w = a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3];
        float x = a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2];
        float y = a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1];
        float z = a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0];
        out[0] = w; out[1] = x; out[2] = y; out[3] = z;
    }

    private static void qAxis(float[] out, float ax, float ay, float az, float rad) {
        float s = (float) Math.sin(rad / 2f);
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-8f) { out[0] = 1; out[1] = out[2] = out[3] = 0; return; }
        out[0] = (float) Math.cos(rad / 2f);
        out[1] = ax / len * s;
        out[2] = ay / len * s;
        out[3] = az / len * s;
    }

    private static void qRot(float[] q, float x, float y, float z, float[] out) {
        // out = q * v * q^-1  (efficient form)
        float qx = q[1], qy = q[2], qz = q[3], qw = q[0];
        float tx = 2f * (qy * z - qz * y);
        float ty = 2f * (qz * x - qx * z);
        float tz = 2f * (qx * y - qy * x);
        out[0] = x + qw * tx + qy * tz - qz * ty;
        out[1] = y + qw * ty + qz * tx - qx * tz;
        out[2] = z + qw * tz + qx * ty - qy * tx;
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

    /** Column-major 4x4 (rotation only) -> quaternion. */
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

    /** Builds the head->device quaternion from the accelerometer (physical up). */
    private void calibrate() {
        float ax = acc[0], ay = acc[1], az = acc[2];
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 5f || len > 15f) return;   // not a trustworthy gravity reading
        ax /= len; ay /= len; az /= len;

        // back = out of the screen (+Z device, toward the eyes in a viewer)
        float bx = 0f, by = 0f, bz = 1f;
        // right = up x back  (right-handed: right x up = back)
        float rx = ay * bz - az * by;
        float ry = az * bx - ax * bz;
        float rz = ax * by - ay * bx;
        float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rl < 0.1f) return;               // up parallel to back — undefined
        rx /= rl; ry /= rl; rz /= rl;
        // re-orthogonalize: up = back x right
        float ux = by * rz - bz * ry;
        float uy = bz * rx - bx * rz;
        float uz = bx * ry - by * rx;

        float[] m = new float[16];
        Matrix.setIdentityM(m, 0);
        m[0] = rx; m[1] = ry; m[2] = rz;     // column 0: head right  -> device
        m[4] = ux; m[5] = uy; m[6] = uz;     // column 1: head up
        m[8] = bx; m[9] = by; m[10] = bz;    // column 2: head back

        matrixToQuat(m, qMount);
        calibrated = true;

        // self-check residual: head up after the full chain should be gl up
        compose(target);
        qRot(target, 0f, 1f, 0f, tmpA);
        mountResidualDeg = (float) Math.toDegrees(Math.acos(
                Math.max(-1f, Math.min(1f, tmpA[1]))));
    }

    /** Rebuilds mount (when forced + gravity available) and the roll-steps quaternion. */
    public void refreshMount(boolean force) {
        if (force && accValid) calibrate();
        int ex = settings == null ? 0 : settings.viewRot();
        qAxis(qRoll, 0f, 0f, 1f, (float) Math.toRadians(ex * 90f));
    }

    /** Full chain as one quaternion into `out`. */
    private void compose(float[] out) {
        qMul(qMount, qRoll, tmpA);           // head -> device
        qMul(qSensor, tmpA, tmpB);           // -> android world
        qMul(Q_G, tmpB, tmpA);               // -> gl world
        qMul(qRecenter, tmpA, out);          // yaw correction
    }

    // ================= recenter (self-verifying) =================

    /** Yaw-only recenter; returns the applied view yaw (listener semantics as before). */
    public float recenter() {
        forward(fwd);
        float fx = fwd[0], fz = fwd[2];
        float a = (float) Math.atan2(fx, -fz);
        // pick the sign that actually maps the current forward to -Z
        float[] c1 = unit(), c2 = unit(), v1 = new float[3], v2 = new float[3];
        qAxis(c1, 0f, 1f, 0f, a);
        qAxis(c2, 0f, 1f, 0f, -a);
        qRot(c1, fwd[0], fwd[1], fwd[2], v1);
        qRot(c2, fwd[0], fwd[1], fwd[2], v2);
        float d1 = -v1[2], d2 = -v2[2];      // progress toward (0,0,-1)
        float chosen = d1 >= d2 ? a : -a;
        qAxis(qRecenter, 0f, 1f, 0f, chosen);
        // refresh the smoothed orientation immediately
        compose(target);
        System.arraycopy(target, 0, qFinal, 0, 4);
        qToMatrix(qFinal, rotation);
        Matrix.transposeM(view, 0, rotation, 0);
        if (listener != null) listener.onRecenter(chosen);
        return chosen;
    }

    // ================= queries =================

    /** Current forward direction (GL world). */
    public float[] forward(float[] out3) {
        qRot(qFinal, 0f, 0f, -1f, out3);
        return out3;
    }

    /** head -> gl rotation matrix (column-major 4x4), recenter applied. */
    public float[] rotation() { return rotation; }

    /** view matrix (inverse of rotation()). */
    public float[] view() { return view; }

    /** Live yaw/pitch/roll in degrees (diagnostics). */
    public String angles() {
        forward(fwd);
        float yaw = (float) Math.toDegrees(Math.atan2(fwd[0], -fwd[2]));
        float pitch = (float) Math.toDegrees(Math.asin(
                Math.max(-1f, Math.min(1f, fwd[1]))));
        float ry = rotation[1], uy = rotation[5]; // column-major: right.y, up.y
        float roll = (float) Math.toDegrees(Math.atan2(ry, uy));
        long age = lastRotVecMs == 0 ? -1 : System.currentTimeMillis() - lastRotVecMs;
        return "yaw " + Math.round(yaw) + "° pitch " + Math.round(pitch)
                + "° roll " + Math.round(roll) + "° · mount ∠" + Math.round(mountResidualDeg)
                + "° · rv " + (age < 0 ? "—": age + "ms");
    }

    public boolean mountCalibrated() { return calibrated; }

    // ================= sensor events =================

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (event.values.length >= 3) {
                acc[0] = event.values[0];
                acc[1] = event.values[1];
                acc[2] = event.values[2];
                accValid = true;
                if (!calibrated) refreshMount(true);   // first gravity sample
            }
            return;
        }
        if (event.values.length < 3) return;
        lastRotVecMs = System.currentTimeMillis();
        SensorManager.getQuaternionFromVector(tmpA, event.values); // device -> android
        System.arraycopy(tmpA, 0, qSensor, 0, 4);

        compose(target);
        // normalized lerp toward the target orientation
        float t = 0.45f;
        for (int i = 0; i < 4; i++) qFinal[i] += (target[i] - qFinal[i]) * t;
        qNormalize(qFinal);
        // keep sign continuity (avoid flipping to the antipodal quat)
        if (qFinal[0] < 0) { qFinal[0] = -qFinal[0]; qFinal[1] = -qFinal[1];
            qFinal[2] = -qFinal[2]; qFinal[3] = -qFinal[3]; }
        qToMatrix(qFinal, rotation);
        Matrix.transposeM(view, 0, rotation, 0);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
