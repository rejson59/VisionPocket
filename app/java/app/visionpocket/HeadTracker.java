package app.visionpocket;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.Matrix;

/**
 * 3DoF head tracking from the fused rotation vector sensor.
 * Produces a device->GL-world rotation (GL world: Y up, forward -Z)
 * with a yaw-only "recenter" correction applied on top.
 */
public class HeadTracker implements SensorEventListener {

    public interface RecenterListener { void onRecenter(float deltaYaw); }

    private final SensorManager sensorManager;
    private final Context appCtx;
    private Sensor sensor;
    public boolean hasSensor = false;

    // device -> android world rotation from the sensor
    private final float[] R3 = new float[9];
    // android world -> gl world (x, z, -y)
    private static final float[] G = { 1f, 0f, 0f,   0f, 0f, 1f,   0f, -1f, 0f };
    private final float[] tmp3 = new float[9];
    private final float[] dg4 = new float[16];      // device -> gl, raw
    private final float[] target = new float[16];   // device -> gl, target (unsmoothed)
    private final float[] recenterM = new float[16]; // accumulated yaw correction

    private static final float SMOOTH = 0.45f; // exponential smoothing toward sensor

    private final float[] rotation = new float[16]; // device -> gl final
    private final float[] view = new float[16];     // inverse (transpose), rotation only

    private RecenterListener listener;

    // Gravity-calibrated head mount (no display conventions involved):
    // "up" comes from the accelerometer in the DEVICE frame, "back" is out of
    // the screen, "right" = up x back (forced by orthonormality, so no mirror
    // ambiguity). The only possible residual error is a 180-degree upside-down
    // insert, correctable with the user roll steps (rot90 button).
    private final float[] mount = new float[16];
    private final float[] calibB = new float[16];   // last gravity calibration
    private final float[] tmp4 = new float[16];
    private final float[] acc = new float[3];
    private boolean accValid = false;
    private boolean calibrated = false;   // gravity basis applied at least once
    private int extraQuarters = -1;
    private SettingsStore settings;
    private int events = 0;

    public HeadTracker(Context ctx) {
        appCtx = ctx;
        sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        Matrix.setIdentityM(recenterM, 0);
        Matrix.setIdentityM(rotation, 0);
        Matrix.setIdentityM(view, 0);
        Matrix.setIdentityM(mount, 0);
        Matrix.setIdentityM(calibB, 0);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (sensor == null) sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        hasSensor = sensor != null;
    }

    public void setRecenterListener(RecenterListener l) { listener = l; }

    /** Gives the tracker access to persisted view-rotation steps. */
    public void attach(SettingsStore st) { settings = st; refreshMount(true); }

    /** Rebuilds the head mount: gravity calibration (only when forced) + user roll steps. */
    public void refreshMount(boolean force) {
        int ex = settings == null ? 0 : settings.viewRot();
        if (!force && ex == extraQuarters) return;
        extraQuarters = ex;
        if (force && accValid) {
            calibrated = true;
            // up = accelerometer direction (world up expressed in device frame)
            float ax = acc[0], ay = acc[1], az = acc[2];
            float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            if (len > 5f && len < 15f) {
                // build the calibrated basis into calibB, then copy to mount
                ax /= len; ay /= len; az /= len;
                // back = out of the screen (+Z device)
                float bx = 0f, by = 0f, bz = 1f;
                // right = up x back (right-handed triple: right x up = back)
                float rx = ay * bz - az * by;
                float ry = az * bx - ax * bz;
                float rz = ax * by - ay * bx;
                float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
                if (rl > 0.1f) {
                    rx /= rl; ry /= rl; rz /= rl;
                    // re-orthogonalize up = back x right
                    float ux = by * rz - bz * ry;
                    float uy = bz * rx - bx * rz;
                    float uz = bx * ry - by * rx;
                    // column-major 4x4 with columns (right, up, back)
                    System.arraycopy(calibB, 0, mount, 0, 16);
                    mount[0] = rx; mount[1] = ry; mount[2] = rz;
                    mount[4] = ux; mount[5] = uy; mount[6] = uz;
                    mount[8] = bx; mount[9] = by; mount[10] = bz;
                    System.arraycopy(mount, 0, calibB, 0, 16);
                }
            }
        } else {
            System.arraycopy(calibB, 0, mount, 0, 16);
        }
        if (ex != 0) Matrix.rotateM(mount, 0, ex * 90f, 0f, 0f, 1f);
    }

    /** Whether the gravity calibration produced a usable mount (diagnostics). */
    public boolean mountCalibrated() { return accValid; }

    private Context ctx() { return appCtx; }

    public void start() {
        if (sensor != null) sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        Sensor grav = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (grav != null) sensorManager.registerListener(this, grav, SensorManager.SENSOR_DELAY_GAME);
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    /** Re-centers the view so that the current head yaw becomes forward (-Z). Returns applied delta in radians. */
    public float recenter() {
        float[] f = forward(new float[3]);
        float yaw = (float) Math.atan2(f[0], -f[2]);
        float[] corr = new float[16];
        Matrix.setIdentityM(corr, 0);
        Matrix.rotateM(corr, 0, (float) Math.toDegrees(yaw), 0f, 1f, 0f);
        float[] next = new float[16];
        Matrix.multiplyMM(next, 0, corr, 0, recenterM, 0);
        System.arraycopy(next, 0, recenterM, 0, 16);
        if (listener != null) listener.onRecenter(yaw);
        return yaw;
    }

    /** Current forward direction (GL world). */
    public float[] forward(float[] out3) {
        // rotate (0,0,-1) by `rotation`
        out3[0] = -rotation[8];
        out3[1] = -rotation[9];
        out3[2] = -rotation[10];
        return out3;
    }

    /** device->gl rotation matrix (column-major 4x4), recenter applied. */
    public float[] rotation() { return rotation; }

    /** view matrix (rotation-only inverse of rotation()). */
    public float[] view() { return view; }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (event.values.length >= 3) {
                acc[0] = event.values[0];
                acc[1] = event.values[1];
                acc[2] = event.values[2];
                boolean had = accValid;
                accValid = true;
                // RACE FIX: the calibration used to run before the first
                // accelerometer sample ever arrived (VR start) and was silently
                // skipped -> identity mount -> the old rotation persisted.
                // Calibrate as soon as gravity data exists, and re-run when the
                // user (re)starts VR (phone may have been re-inserted).
                if (!had || !calibrated) refreshMount(true);
            }
            return;
        }
        if (event.values.length < 4) return;
        if (++events % 30 == 0) refreshMount(false);
        SensorManager.getRotationMatrixFromVector(R3, event.values);
        GlUtil.mat3mul(G, R3, tmp3);
        GlUtil.mat3to4(tmp3, dg4);
        // target = recenter * dg * mount
        Matrix.multiplyMM(tmp4, 0, dg4, 0, mount, 0);
        Matrix.multiplyMM(target, 0, recenterM, 0, tmp4, 0);
        // exponential smoothing toward the sensor reading (reduces jitter)
        for (int i = 0; i < 16; i++) {
            rotation[i] += (target[i] - rotation[i]) * SMOOTH;
        }
        orthonormalize(rotation);
        Matrix.transposeM(view, 0, rotation, 0);
    }

    /** Re-orthonormalizes the 3x3 rotation part after lerping. */
    private static void orthonormalize(float[] m) {
        float ax = m[0], ay = m[1], az = m[2];
        float l0 = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (l0 < 1e-8f) return;
        ax /= l0; ay /= l0; az /= l0;
        float bx = m[4], by = m[5], bz = m[6];
        float d = bx * ax + by * ay + bz * az;
        bx -= d * ax; by -= d * ay; bz -= d * az;
        float l1 = (float) Math.sqrt(bx * bx + by * by + bz * bz);
        if (l1 < 1e-8f) return;
        bx /= l1; by /= l1; bz /= l1;
        float cx = ay * bz - az * by;
        float cy = az * bx - ax * bz;
        float cz = ax * by - ay * bx;
        m[0] = ax; m[1] = ay; m[2] = az;
        m[4] = bx; m[5] = by; m[6] = bz;
        m[8] = cx; m[9] = cy; m[10] = cz;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
