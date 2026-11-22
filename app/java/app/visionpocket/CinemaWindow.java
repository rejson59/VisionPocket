package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Surface;

/** Cinema window: giant video surface (OES part) + controls below. */
public class CinemaWindow implements WindowContent {

    public final VpWindows.Window window;
    public final Panel video;     // OES video surface part
    private final Panel chrome;   // title + controls
    private final VpWindows wm;
    private final VpWindows.WmHost host;

    private Uri uri;
    private MediaPlayer mp;
    private boolean prepared = false;
    private boolean error = false;
    public boolean videoReady = false;
    private int pos = 0, dur = 0;
    private int tick = 0;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMsg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBarBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBarFill = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CinemaWindow(VpWindows wm, VpWindows.WmHost host) {
        this.wm = wm;
        this.host = host;
        window = new VpWindows.Window("cinema", host.activity().getString(R.string.win_cinema));
        chrome = new Panel(0, 0, 0, 0, 1.3f, 0.38f, 800f);
        video = new Panel(0, 0, 0, 0, 3.0f, 1.69f, 800f);
        video.oes = true;
        video.visible = false;

        window.addPart(video, 0f, 0.38f / 2f + 1.69f / 2f + 0.06f, 0f);
        window.addPart(chrome, 0f, 0f, 0f);
        window.attachChrome(chrome);
        window.content = this;

        // buttons in body coords + STRIP offset (hit-test uses full-canvas px)
        int s = VpWindows.STRIP_PX;
        chrome.add("rew", 60, 30 + s, 300, 170 + s);
        chrome.add("play", 340, 30 + s, 580, 170 + s);
        chrome.add("ff", 620, 30 + s, 860, 170 + s);
        chrome.add("pick", 900, 30 + s, 1000, 170 + s);
        chrome.click = b -> onClick(b.id);
        video.click = b -> {};

        pBg.setColor(0x00000000);
        pBtn.setColor(0xFF232838);
        pBtnHover.setColor(0xFF3D4964);
        pBtnActive.setColor(0xFF31518C);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF39415A);
        pLabel.setColor(0xFFE8ECF5);
        pLabel.setTextSize(62f);
        pLabel.setTextAlign(Paint.Align.CENTER);
        pMsg.setColor(0xFF9AA3B8);
        pMsg.setTextSize(44f);
        pMsg.setTextAlign(Paint.Align.CENTER);
        pBarBg.setColor(0xFF262A3A);
        pBarFill.setColor(0xFF5B8DEF);

        wm.placeNew(window);
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        // progress bar across the top of the body
        int s = VpWindows.STRIP_PX;
        c.drawRoundRect(30, 8, p.pw - 30, 26, 9, 9, pBarBg);
        if (dur > 0 && videoReady) {
            float fx = 30f + (p.pw - 60f) * Math.max(0f, Math.min(1f, pos / (float) dur));
            c.drawRoundRect(30, 8, fx, 26, 9, 9, pBarFill);
        }
        boolean playing = mp != null && prepared && mp.isPlaying();
        if (!videoReady && uri == null && !error) {
            c.drawText(host.activity().getString(R.string.cinema_hint_title), p.pw / 2f, 120f, pMsg);
        } else if (error) {
            c.drawText(host.activity().getString(R.string.cinema_error), p.pw / 2f, 120f, pMsg);
        } else if (uri != null && !prepared) {
            c.drawText(host.activity().getString(R.string.cinema_loading), p.pw / 2f, 120f, pMsg);
        }
        for (Panel.Button b : p.buttons) {
            Paint fill = b.hover ? pBtnHover : (playing && "play".equals(b.id) ? pBtnActive : pBtn);
            c.drawRoundRect(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s, 22, 22, fill);
            pBorder.setColor(b.hover ? 0xFF5B8DEF : 0xFF39415A);
            c.drawRoundRect(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s, 22, 22, pBorder);
            String label;
            switch (b.id) {
                case "rew": label = host.activity().getString(R.string.cinema_btn_rew); break;
                case "play": label = playing ? host.activity().getString(R.string.cinema_btn_pause)
                        : host.activity().getString(R.string.cinema_btn_play); break;
                case "ff": label = host.activity().getString(R.string.cinema_btn_ff); break;
                case "pick": label = "📂"; break;
                default: label = "";
            }
            HomeSceneUtil.drawCentered(c, label,
                    new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s), pLabel);
        }
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    public void loadVideo(Uri u, SurfaceTexture st, Surface surface) {
        uri = u;
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
                    if (vw > 0 && vh > 0 && st != null) st.setDefaultBufferSize(vw, vh);
                    float hv = 1.69f;
                    float hw = Math.max(0.9f, Math.min(3.4f, hv * vw / (float) vh));
                    float offY = 0.38f / 2f + hv / 2f + 0.06f;
                    window.setPartGeometry(video, hw, hv, 0f, offY, 0f);
                    // curved Quest-style screen wrapping toward the viewer
                    video.mesh = Mesh.cylinder(hw, hv, 5.5f, 28, 1);
                    video.visible = true;
                    videoReady = true;
                    p.start();
                } catch (Exception ignored) {}
                chrome.dirty = true;
            });
            mp.setOnErrorListener((p, what, extra) -> {
                error = true;
                chrome.dirty = true;
                host.toast(host.activity().getString(R.string.cinema_error));
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            error = true;
        }
        chrome.dirty = true;
    }

    public void togglePlay() {
        if (mp == null || !prepared) { host.pickVideo(); return; }
        try {
            if (mp.isPlaying()) mp.pause(); else mp.start();
        } catch (Exception ignored) {}
        chrome.dirty = true;
    }

    private void seek(int delta) {
        if (mp == null || !prepared) return;
        try {
            int d = mp.getDuration();
            mp.seekTo(Math.max(0, Math.min(d - 200, pos + delta)));
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

    @Override public void onClick(String id) {
        switch (id) {
            case "rew": seek(-30000); break;
            case "ff": seek(30000); break;
            case "play": togglePlay(); break;
            case "pick": host.pickVideo(); break;
        }
    }

    @Override public void onBodyClick(Panel part) {
        if (part == video || part == chrome) togglePlay();
    }

    @Override public void update() {
        if (++tick % 20 == 0 && mp != null && prepared) {
            try {
                int np = mp.getCurrentPosition();
                int nd = mp.getDuration();
                if (np != pos || nd != dur) { pos = np; dur = nd; chrome.dirty = true; }
            } catch (Exception ignored) {}
        }
    }

    @Override public void onClosed() { releaseMp(); }

    @Override public void onRestored() { video.visible = videoReady; }
}
