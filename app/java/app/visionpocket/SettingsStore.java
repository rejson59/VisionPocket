package app.visionpocket;

import android.content.Context;
import android.content.SharedPreferences;

/** User settings persisted with SharedPreferences. */
public class SettingsStore {

    private final SharedPreferences prefs;

    public SettingsStore(Context ctx) {
        prefs = ctx.getSharedPreferences("visionpocket", Context.MODE_PRIVATE);
    }

    /** Eye distance in millimeters (58..74). */
    public int ipd() { return prefs.getInt("ipd", 64); }
    public void setIpd(int v) { prefs.edit().putInt("ipd", clamp(v, 58, 74)).apply(); }

    /** Lens distortion strength percent (0..150). */
    public int distortionPct() { return prefs.getInt("distortion", 100); }
    public void setDistortionPct(int v) { prefs.edit().putInt("distortion", clamp(v, 0, 150)).apply(); }

    /** Eye resolution scale index 0=low,1=med,2=high. */
    public int resIdx() { return prefs.getInt("resIdx", 1); }
    public void setResIdx(int v) { prefs.edit().putInt("resIdx", clamp(v, 0, 2)).apply(); }
    public float resScale() { return new float[]{0.6f, 0.75f, 1.0f}[resIdx()]; }

    /** Camera passthrough on/off. */
    public boolean passthrough() { return prefs.getBoolean("passthrough", true); }
    public void setPassthrough(boolean b) { prefs.edit().putBoolean("passthrough", b).apply(); }

    /** Manual passthrough rotation (0/90/180/270). */
    public int rotDeg() { return prefs.getInt("rot", 90); }
    public void setRotDeg(int v) { prefs.edit().putInt("rot", ((v % 360) + 360) % 360).apply(); }

    /** Passthrough zoom (0.6..2.0). */
    public float zoom() { return prefs.getFloat("zoom", 1.0f); }
    public void setZoom(float v) { prefs.edit().putFloat("zoom", clamp(v, 0.6f, 2.0f)).apply(); }

    /** World background: 0=passthrough, 1=space, 2=horizon. */
    public int envMode() { return prefs.getInt("env", 0); }
    public void setEnvMode(int v) { prefs.edit().putInt("env", clamp(v, 0, 2)).apply(); }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
