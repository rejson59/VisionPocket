package app.visionpocket;

import android.content.Context;
import android.hardware.Sensor;
import android.view.Display;
import android.view.Surface;
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

    // mount correction: screen frame (as held) -> device frame, plus user roll steps
    private final float[] mount = new float[16];
    private final float[] tmp4 = new float[16];
    private int dispRot = -1;
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
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (sensor == null) sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        hasSensor = sensor != null;
    }

    public void setRecenterListener(RecenterListener l) { listener = l; }

    /** Gives the tracker access to persisted view-rotation steps. */
    public void attach(SettingsStore st) { settings = st; refreshMount(true); }

    /** Rebuilds the mount matrix (display orientation + user roll steps). */
    public void refreshMount(boolean force) {
        int dr = -1;
        try {
            Display d = ((android.app.Activity) (ctx())).getWindowManager().getDefaultDisplay();
            dr = d.getRotation();
        } catch (Exception ignored) {}
        int ex = settings == null ? 0 : settings.viewRot();
        if (!force && dr == dispRot && ex == extraQuarters) return;
        dispRot = dr;
        extraQuarters = ex;
        Matrix.setIdentityM(mount, 0);
        // sensor frame is tied to the NATURAL (portrait) orientation; the phone is
        // held landscape, so remap screen->device before going to world coords
        if (dr == Surface.ROTATION_90) {          // top of device to the right
            mount[0] = 0; mount[1] = 1;           // screen X = device +Y
            mount[4] = -1; mount[5] = 0;          // screen Y = device -X
        } else if (dr == Surface.ROTATION_270) {  // top of device to the left
            mount[0] = 0; mount[1] = -1;          // screen X = device -Y
            mount[4] = 1; mount[5] = 0;           // screen Y = device +X
        } else if (dr == Surface.ROTATION_180) {
            mount[0] = -1;
            mount[5] = -1;
        }
        if (ex != 0) Matrix.rotateM(mount, 0, ex * 90f, 0f, 0f, 1f);
    }

    private Context ctx() { return appCtx; }

    public void start() {
        if (sensor != null) sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
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
