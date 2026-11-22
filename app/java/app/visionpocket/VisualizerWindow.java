package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;

/** Music visualizer window: FFT/waveform bars (Visualizer API with graceful fallback). */
public class VisualizerWindow implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VpWindows.WmHost host;

    private Visualizer viz;
    private volatile boolean live = false;
    private final byte[] wave = new byte[64];
    private final float[] bars = new float[32];
    private int tick = 0;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBar2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMsg = new Paint(Paint.ANTI_ALIAS_FLAG);

    public VisualizerWindow(VpWindows wm, VpWindows.WmHost host) {
        this.wm = wm;
        this.host = host;
        window = new VpWindows.Window("viz", host.activity().getString(R.string.win_viz));
        chrome = new Panel(0, 0, 0, 0, 1.5f, 1.0f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        pBg.setColor(0xFF101113);
        pBar.setColor(0xFF8FA3C8);
        pBar2.setColor(0xFF8FA3C8);
        pMsg.setColor(0xFF9BA0AB);
        pMsg.setTextSize(40f);
        pMsg.setTextAlign(Paint.Align.CENTER);

        tryInitViz();
        wm.placeNew(window);
    }

    private void tryInitViz() {
        try {
            viz = new Visualizer(0);
            viz.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            viz.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] waveform, int samplingRate) {
                    int step = waveform.length / wave.length;
                    for (int i = 0; i < wave.length; i++) wave[i] = waveform[i * step];
                    live = true;
                }
                @Override public void onFftDataCapture(Visualizer v, byte[] fft, int samplingRate) {}
            }, Visualizer.getMaxCaptureRate() / 2, true, false);
            viz.setEnabled(true);
            live = true;
        } catch (Exception e) {
            live = false;
        }
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF101113);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);

        float baseY = p.ph - s - 60f;
        float maxH = p.ph - s - 160f;
        float bw = (p.pw - 80f) / bars.length;

        if (!live && tick % 30 == 0) {
            // graceful fallback: gentle fake dance
            for (int i = 0; i < bars.length; i++) {
                float t = (System.nanoTime() / 100000000L) * 0.9f + i * 0.55f;
                bars[i] = 0.25f + 0.2f * (float) Math.sin(t) + 0.12f * (float) Math.sin(t * 2.7f);
            }
        } else if (live) {
            for (int i = 0; i < bars.length; i++) {
                int a = Math.abs(wave[i * 2]);
                int b = Math.abs(wave[i * 2 + 1]);
                float target = Math.max(a, b) / 128f;
                bars[i] = bars[i] * 0.55f + Math.min(1.15f, target * 1.4f) * 0.45f;
            }
        }

        for (int i = 0; i < bars.length; i++) {
            float h = Math.max(6f, Math.min(1f, bars[i]) * maxH);
            float x = 40f + i * bw;
            c.drawRoundRect(x + bw * 0.12f, baseY - h, x + bw * 0.88f, baseY, bw * 0.3f, bw * 0.3f,
                    i % 2 == 0 ? pBar : pBar2);
        }

        c.drawText(host.activity().getString(R.string.viz_hint), p.pw / 2f, 70f, pMsg);
        c.drawText(host.activity().getString(R.string.viz_perm), p.pw / 2f, 118f, pMsg);
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {}

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {
        // ~24 fps canvas refresh
        if (++tick % 2 == 0) chrome.dirty = true;
    }

    @Override public void onClosed() {
        try {
            if (viz != null) { viz.setEnabled(false); viz.release(); }
        } catch (Exception ignored) {}
        viz = null;
        live = false;
    }

    @Override public void onRestored() { if (viz == null) tryInitViz(); }
}
