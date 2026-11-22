package app.visionpocket;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.List;

/** Diagnostics window: device, GPU, sensors, permissions — ready to screenshot for support. */
public class DiagWindow implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VpWindows.WmHost host;
    private int tick = 0;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHead = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pVal = new Paint(Paint.ANTI_ALIAS_FLAG);

    public DiagWindow(VpWindows wm, VpWindows.WmHost host) {
        this.wm = wm;
        this.host = host;
        window = new VpWindows.Window("diag", host.activity().getString(R.string.win_diag));
        chrome = new Panel(0, 0, 0, 0, 1.55f, 1.3f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        pBg.setColor(0xFF0E101A);
        pHead.setColor(0xFF8FA7E8);
        pHead.setTextSize(42f);
        pHead.setFakeBoldText(true);
        pHead.setTextAlign(Paint.Align.LEFT);
        pVal.setColor(0xFFDCE3F2);
        pVal.setTextSize(40f);
        pVal.setTextAlign(Paint.Align.LEFT);

        wm.placeNew(window);
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF0E101A);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);

        float x = 40f;
        float y = 60f;
        float step = 56f;

        // device
        c.drawText(host.activity().getString(R.string.diag_dev), x, y, pHead);
        y += step;
        String model = Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")";
        c.drawText(model, x, y, pVal);
        y += step;
        String gpu = "GPU: " + GLES20.glGetString(GLES20.GL_RENDERER);
        c.drawText(gpu.length() > 64 ? gpu.substring(0, 63) + "…" : gpu, x, y, pVal);
        y += step;
        String gl = GLES20.glGetString(GLES20.GL_VERSION);
        c.drawText(gl.length() > 64 ? gl.substring(0, 63) + "…" : gl, x, y, pVal);
        y += step;

        // display + fps
        c.drawText(host.activity().getString(R.string.diag_disp), x, y, pHead);
        y += step;
        DisplayMetrics dm = host.activity().getResources().getDisplayMetrics();
        String disp = dm.widthPixels + "×" + dm.heightPixels + " @ " + dm.densityDpi + " dpi · "
                + Math.round(host.fps()) + " FPS";
        c.drawText(disp, x, y, pVal);
        y += step;

        // sensors
        c.drawText(host.activity().getString(R.string.diag_sens), x, y, pHead);
        y += step;
        SensorManager sm = (SensorManager) host.activity().getSystemService(android.content.Context.SENSOR_SERVICE);
        List<String> sens = new ArrayList<>();
        if (sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null) sens.add("rot-vector ✓");
        else sens.add("rot-vector ✗");
        if (sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) sens.add("gyro ✓");
        else sens.add("gyro ✗");
        if (sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null) sens.add("compass ✓");
        sens.add("accel ✓");
        c.drawText(android.text.TextUtils.join(" · ", sens), x, y, pVal);
        y += step;

        // permissions
        c.drawText(host.activity().getString(R.string.diag_perm), x, y, pHead);
        y += step;
        boolean cam = host.activity().checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        boolean mic = host.activity().checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        String perms = "camera " + (cam ? "✓" : "✗") + " · mic " + (mic ? "✓" : "✗");
        c.drawText(perms, x, y, pVal);
        y += step;

        // settings + storage
        c.drawText(host.activity().getString(R.string.diag_misc), x, y, pHead);
        y += step;
        SettingsStore st = host.settings();
        String misc = "IPD " + st.ipd() + " · res " + Math.round(st.resScale() * 100) + "% · "
                + new String[]{"battery", "balanced", "full"}[st.perfMode()]
                + " · " + host.activity().batteryPct() + "% 🔋";
        c.drawText(misc, x, y, pVal);
        y += step;
        try {
            StatFs sf = new StatFs(Environment.getDataDirectory().getPath());
            long gb = sf.getAvailableBytes() / (1024L * 1024L * 1024L);
            c.drawText(host.activity().getString(R.string.diag_storage, gb), x, y, pVal);
        } catch (Exception ignored) {}

        if (++tick % 120 == 0) chrome.dirty = true;
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {}

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {}

    @Override public void onClosed() {}

    @Override public void onRestored() {}
}
