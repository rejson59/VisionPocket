package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;

/** Virtual cinema: giant video screen (OES texture) plus canvas controls. */
public class CinemaScene implements Scene {

    private final SceneHost host;
    private final Panel controls;
    private final Panel info;
    private final List<Panel> panels = new ArrayList<>(2);

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDesc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBarBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBarFill = new Paint(Paint.ANTI_ALIAS_FLAG);

    // video state
    private Uri uri;
    private MediaPlayer mp;
    private SurfaceTexture vidST;
    private boolean prepared = false;
    private boolean error = false;
    public boolean videoReady = false;
    private int pos = 0, dur = 0;
    private int tickCount = 0;

    // video quad (world-locked), drawn by the renderer
    public float videoCx = 0f, videoCy = 0.25f, videoCz = -3.4f;
    public float videoYaw = 0f;
    public float videoW = 3.0f, videoH = 1.7f;

    public CinemaScene(SceneHost host) {
        this.host = host;
        controls = new Panel(0f, -0.62f, -2.3f, 0f, 1.3f, 0.24f, 1000f);
        info = new Panel(0f, 0.35f, -2.3f, 0f, 1.3f, 0.5f, 1000f);

        pBg.setColor(0xE6141622);
        pBtn.setColor(0xFF232838);
        pBtnHover.setColor(0xFF3D4964);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF39415A);
        pTitle.setColor(0xFFE8ECF5);
        pTitle.setTextSize(80f);
        pTitle.setTextAlign(Paint.Align.CENTER);
        pTitle.setFakeBoldText(true);
        pDesc.setColor(0xFF9AA3B8);
        pDesc.setTextSize(44f);
        pDesc.setTextAlign(Paint.Align.CENTER);
        pLabel.setColor(0xFFE8ECF5);
        pLabel.setTextSize(52f);
        pLabel.setTextAlign(Paint.Align.CENTER);
        pBarBg.setColor(0xFF262A3A);
        pBarFill.setColor(0xFF5B8DEF);

        controls.add("rew", 40, 60, 280, 220);
        controls.add("play", 320, 60, 560, 220);
        controls.add("ff", 600, 60, 840, 220);
        controls.add("pick", 880, 60, 1080, 220);
        controls.add("exit", 1120, 60, 1260, 220);

        info.add("pick_big", 350, 320, 950, 460);

        controls.hook = this::drawControls;
        controls.click = this::onPanelClick;
        info.hook = this::drawInfo;
        info.click = this::onPanelClick;
        panels.add(controls);
        panels.add(info);
    }

    private void drawControls(Canvas c, Panel p) {
        pBg.setColor(0xE6141622);
        c.drawRoundRect(0, 0, p.pw, p.ph, 40, 40, pBg);
        // progress bar
        c.drawRoundRect(40, 16, 1260, 34, 9, 9, pBarBg);
        if (dur > 0 && videoReady) {
            float fx = 40f + 1220f * Math.max(0f, Math.min(1f, pos / (float) dur));
            c.drawRoundRect(40, 16, fx, 34, 9, 9, pBarFill);
        }
        boolean playing = mp != null && prepared && mp.isPlaying();
        for (Panel.Button b : p.buttons) {
            Paint fill = b.hover ? pBtnHover : pBtn;
            c.drawRoundRect(b.rect, 24, 24, fill);
            pBorder.setColor(b.hover ? 0xFF5B8DEF : 0xFF39415A);
            c.drawRoundRect(b.rect, 24, 24, pBorder);
            String label;
            switch (b.id) {
                case "rew": label = host.activity().getString(R.string.cinema_btn_rew); break;
                case "play": label = playing ? host.activity().getString(R.string.cinema_btn_pause)
                        : host.activity().getString(R.string.cinema_btn_play); break;
                case "ff": label = host.activity().getString(R.string.cinema_btn_ff); break;
                case "pick": label = "📂"; break;
                default: label = host.activity().getString(R.string.cinema_btn_exit);
            }
            HomeScene.drawCentered(c, label, b.rect, pLabel);
        }
    }

    private void drawInfo(Canvas c, Panel p) {
        pBg.setColor(0xE6141622);
        c.drawRoundRect(0, 0, p.pw, p.ph, 48, 48, pBg);
        c.drawText(host.activity().getString(R.string.cinema_hint_title), p.pw / 2f, 130f, pTitle);
        String desc;
        if (error) desc = host.activity().getString(R.string.cinema_error);
        else if (uri != null && !prepared) desc = host.activity().getString(R.string.cinema_loading);
        else desc = host.activity().getString(R.string.cinema_hint_desc);
        drawWrapped(c, desc, p.pw / 2f, 200f, 36f, pDesc);
        Panel.Button big = null;
        for (Panel.Button b : p.buttons) if ("pick_big".equals(b.id)) big = b;
        if (big != null) {
            Paint fill = big.hover ? pBtnHover : pBtn;
            c.drawRoundRect(big.rect, 32, 32, fill);
            pBorder.setColor(big.hover ? 0xFF5B8DEF : 0xFF39415A);
            c.drawRoundRect(big.rect, 32, 32, pBorder);
            HomeScene.drawCentered(c, host.activity().getString(R.string.cinema_pick_video), big.rect, pLabel);
        }
    }

    static void drawWrapped(Canvas c, String text, float centerX, float topY, float size, Paint paint) {
        if (text.length() <= size) {
            c.drawText(text, centerX, topY, paint);
            return;
        }
        int mid = text.lastIndexOf(' ', (int) size);
        if (mid <= 0) mid = (int) size;
        c.drawText(text.substring(0, mid), centerX, topY, paint);
        c.drawText(text.substring(mid + 1), centerX, topY + paint.getTextSize() * 1.35f, paint);
    }

    public void loadVideo(Uri u, SurfaceTexture st, Surface surface) {
        uri = u;
        vidST = st;
        error = false;
        prepared = false;
        videoReady = false;
        releaseMp();
        try {
            mp = new MediaPlayer();
            mp.setDataSource(host.activity().getApplicationContext(), u);
            mp.setSurface(surface);
            mp.setOnPreparedListener(p -> {
                prepared = true;
                try {
                    int vw = p.getVideoWidth(), vh = p.getVideoHeight();
                    if (vw > 0 && vh > 0 && vidST != null) vidST.setDefaultBufferSize(vw, vh);
                    videoH = 1.7f;
                    videoW = Math.max(0.9f, Math.min(3.4f, 1.7f * vw / (float) vh));
                    videoReady = true;
                    p.start();
                } catch (Exception ignored) {}
                controls.dirty = true;
                info.dirty = true;
            });
            mp.setOnErrorListener((p, what, extra) -> {
                error = true;
                controls.dirty = true;
                info.dirty = true;
                return true;
            });
            mp.setOnCompletionListener(p -> controls.dirty = true);
            mp.prepareAsync();
        } catch (Exception e) {
            error = true;
        }
        info.dirty = true;
        controls.dirty = true;
    }

    public void togglePlay() {
        if (mp == null || !prepared) { host.requestVideoPicker(); return; }
        try {
            if (mp.isPlaying()) mp.pause(); else mp.start();
        } catch (Exception ignored) {}
        controls.dirty = true;
    }

    private void seek(int delta) {
        if (mp == null || !prepared) return;
        try {
            int d = mp.getDuration();
            int np = Math.max(0, Math.min(d - 200, pos + delta));
            mp.seekTo(np);
        } catch (Exception ignored) {}
    }

    private void releaseMp() {
        if (mp != null) {
            try { mp.release(); } catch (Exception ignored) {}
            mp = null;
        }
        prepared = false;
        videoReady = false;
    }

    private void onPanelClick(Panel.Button b) {
        switch (b.id) {
            case "rew": seek(-30000); break;
            case "ff": seek(30000); break;
            case "play": togglePlay(); break;
            case "pick": case "pick_big": host.requestVideoPicker(); break;
            case "exit": host.showScene(SceneManager.SceneId.HOME); break;
        }
    }

    @Override public List<Panel> panels() { return panels; }

    @Override public void onClick(Panel.Button b) { onPanelClick(b); }

    @Override public void onShown() {
        controls.dirty = true;
        info.dirty = true;
    }

    @Override public void onHidden() {
        try { if (mp != null && mp.isPlaying()) mp.pause(); } catch (Exception ignored) {}
        controls.clearHover();
        info.clearHover();
    }

    @Override public void update() {
        info.visible = (uri == null) || error;
        if (++tickCount % 20 == 0 && mp != null && prepared) {
            try {
                int np = mp.getCurrentPosition();
                int nd = mp.getDuration();
                if (np != pos || nd != dur) {
                    pos = np; dur = nd;
                    controls.dirty = true;
                }
            } catch (Exception ignored) {}
        }
    }

    @Override public void onRecenter(float d) {
        controls.rotateBy(d);
        info.rotateBy(d);
        float c = (float) Math.cos(d), s = (float) Math.sin(d);
        float nx = videoCx * c - videoCz * s;
        float nz = videoCx * s + videoCz * c;
        videoCx = nx;
        videoCz = nz;
        videoYaw -= d;
    }
}
