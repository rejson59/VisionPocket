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

    /** Passthrough lens: false = back camera, true = front camera (selfie AR). */
    public boolean frontCam() { return prefs.getBoolean("frontCam", false); }
    public void setFrontCam(boolean b) { prefs.edit().putBoolean("frontCam", b).apply(); }

    /** 360° projection: 0=equirect, 1=dome, 2=tiny planet. */
    public int panoProj() { return prefs.getInt("panoProj", 0); }
    public void setPanoProj(int v) { prefs.edit().putInt("panoProj", clamp(v, 0, 2)).apply(); }

    /** Quick note text. */
    public String note() { return prefs.getString("note", ""); }
    public void setNote(String s) { prefs.edit().putString("note", s == null ? "" : s).apply(); }

    /** Performance profile: 0=battery, 1=balanced (adaptive), 2=performance. */
    public int perfMode() { return prefs.getInt("perf", 1); }
    public void setPerfMode(int v) { prefs.edit().putInt("perf", clamp(v, 0, 2)).apply(); }

    /** Click haptics on/off. */
    public boolean haptics() { return prefs.getBoolean("haptics", true); }
    public void setHaptics(boolean b) { prefs.edit().putBoolean("haptics", b).apply(); }

    /** Timestamp of the last crash (crash-guard). */
    public long lastCrash() { return prefs.getLong("lastCrash", 0); }
    public void setLastCrash(long ts) { prefs.edit().putLong("lastCrash", ts).apply(); }

    /** Serialized window session (JSON) + dock placement. */
    public String session() { return prefs.getString("session", ""); }
    public void setSession(String s) { prefs.edit().putString("session", s == null ? "" : s).apply(); }

    /** Passthrough brightness multiplier (0.5..1.5). */
    public float brightness() { return prefs.getFloat("bright", 1f); }
    public void setBrightness(float v) { prefs.edit().putFloat("bright", clamp(v, 0.5f, 1.5f)).apply(); }

    /** Auto-recenter right after VR starts. */
    public boolean autoRecenter() { return prefs.getBoolean("autoRec", true); }
    public void setAutoRecenter(boolean b) { prefs.edit().putBoolean("autoRec", b).apply(); }

    /** Last cinema video URI (persistable permission), restored with the session. */
    public String lastVideo() { return prefs.getString("lastVideo", ""); }
    public void setLastVideo(String s) { prefs.edit().putString("lastVideo", s == null ? "" : s).apply(); }

    /** Best score in the Focus game. */
    public int focusBest() { return prefs.getInt("focusBest", 0); }
    public void setFocusBest(int v) { prefs.edit().putInt("focusBest", v).apply(); }

    /** Best score in 2048. */
    public int best2048() { return prefs.getInt("best2048", 0); }
    public void setBest2048(int v) { prefs.edit().putInt("best2048", v).apply(); }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
