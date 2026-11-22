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
    private final java.util.ArrayList<long[]> subTimes = new java.util.ArrayList<>();
    private final java.util.ArrayList<String> subTexts = new java.util.ArrayList<>();
    private boolean resumeApplied = false;
    private boolean prepared = false;
    private boolean error = false;
    private boolean buffering = false;
    public boolean videoReady = false;
    private int pos = 0, dur = 0;
    private int tick = 0;
    private float vol = 1f;

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
        chrome = new Panel(0, 0, 0, 0, 1.3f, 0.55f, 800f);
        video = new Panel(0, 0, 0, 0, 3.0f, 1.69f, 800f);
        video.oes = true;
        video.visible = false;

        window.addPart(video, 0f, 0.55f / 2f + 1.69f / 2f + 0.06f, 0f);
        window.addPart(chrome, 0f, 0f, 0f);
        window.attachChrome(chrome);
        window.content = this;

        // buttons in body coords + STRIP offset (hit-test uses full-canvas px)
        int s = VpWindows.STRIP_PX;
        chrome.add("voldown", 30, 30 + s, 122, 170 + s);
        chrome.add("rew", 142, 30 + s, 234, 170 + s);
        chrome.add("play", 254, 30 + s, 346, 170 + s);
        chrome.add("ff", 366, 30 + s, 458, 170 + s);
        chrome.add("volup", 478, 30 + s, 570, 170 + s);
        chrome.add("pick", 590, 30 + s, 682, 170 + s);
        chrome.add("srt", 702, 30 + s, 794, 170 + s);
        chrome.add("url", 814, 30 + s, 906, 170 + s);
        chrome.add("exit", 926, 30 + s, 1018, 170 + s);
        chrome.click = b -> onClick(b.id);
        video.click = b -> {};

        pBg.setColor(0x00000000);
        pBtn.setColor(0xFF1F2126);
        pBtnHover.setColor(0xFF2E323A);
        pBtnActive.setColor(0xFF343945);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF2E3138);
        pLabel.setColor(0xFFE8ECF5);
        pLabel.setTextSize(62f);
        pLabel.setTextAlign(Paint.Align.CENTER);
        pMsg.setColor(0xFF9BA0AB);
        pMsg.setTextSize(44f);
        pMsg.setTextAlign(Paint.Align.CENTER);
        pBarBg.setColor(0xFF26272B);
        pBarFill.setColor(0xFF8FA3C8);

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
        if (buffering) {
            c.drawText(host.activity().getString(R.string.cinema_buffering), p.pw / 2f, 60f, pMsg);
        }
        if (!videoReady && uri == null && !error) {
            c.drawText(host.activity().getString(R.string.cinema_hint_title), p.pw / 2f, 120f, pMsg);
        } else if (error) {
            c.drawText(host.activity().getString(R.string.cinema_error), p.pw / 2f, 120f, pMsg);
        } else if (uri != null && !prepared) {
            c.drawText(host.activity().getString(R.string.cinema_loading), p.pw / 2f, 120f, pMsg);
        }
        for (Panel.Button b : p.buttons) {
            Paint fill = b.hover ? pBtnHover
                    : ((playing && "play".equals(b.id)) || ("srt".equals(b.id) && subTimes.size() > 0)
                        ? pBtnActive : pBtn);
            c.drawRoundRect(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s, 22, 22, fill);
            pBorder.setColor(b.hover ? 0xFF8FA3C8 : 0xFF2E3138);
            c.drawRoundRect(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s, 22, 22, pBorder);
            String label;
            switch (b.id) {
                case "rew": label = host.activity().getString(R.string.cinema_btn_rew); break;
                case "play": label = playing ? host.activity().getString(R.string.cinema_btn_pause)
                        : host.activity().getString(R.string.cinema_btn_play); break;
                case "ff": label = host.activity().getString(R.string.cinema_btn_ff); break;
                case "pick": label = "📂"; break;
                case "url": label = "📡"; break;
                case "exit": label = "⏏"; break;
                case "voldown": label = "🔉"; break;
                case "volup": label = "🔊"; break;
                case "srt": label = "CC"; break;
                default: label = "";
            }
            HomeSceneUtil.drawCentered(c, label,
                    new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s), pLabel);
        }

        // subtitles
        if (subTimes.size() > 0 && videoReady) {
            String line = null;
            for (int i = 0; i < subTimes.size(); i++) {
                long[] t = subTimes.get(i);
                if (pos >= t[0] && pos <= t[1]) { line = subTexts.get(i); break; }
            }
            if (line != null) {
                Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG);
                sub.setColor(0xFFF2F3F5);
                sub.setTextSize(52f);
                sub.setTextAlign(Paint.Align.CENTER);
                sub.setShadowLayer(8f, 0f, 3f, 0xE6000000);
                String[] lines = line.split("\n");
                float by = p.ph - s - 96f;
                for (int i = Math.max(0, lines.length - 2); i < lines.length; i++) {
                    c.drawText(lines[i], p.pw / 2f, by, sub);
                    by += 62f;
                }
            }
        }
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    public void loadVideo(Uri u, SurfaceTexture st, Surface surface) {
        uri = u;
        error = false;
        resumeApplied = false;
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
                    boolean sbs = vw > vh * 2 + 8; // side-by-side 3D file
                    video.sbsSide = sbs ? -1 : 0;
                    float contentW = sbs ? vw / 2f : vw;
                    float hv = 1.69f;
                    float hw = Math.max(0.9f, Math.min(3.4f, hv * contentW / (float) vh));
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
            mp.setOnInfoListener((p, what, extra) -> {
                if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START) buffering = true;
                else if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END) buffering = false;
                chrome.dirty = true;
                return false;
            });
            mp.setOnErrorListener((p, what, extra) -> {
                error = true;
                buffering = false;
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

    private void setVol(float v) {
        vol = Math.max(0f, Math.min(1f, v));
        try { if (mp != null && prepared) mp.setVolume(vol, vol); } catch (Exception ignored) {}
        chrome.dirty = true;
    }

    /** Called by hardware volume keys when the cinema is focused. */
    public void nudgeVolume(float d) { setVol(vol + d); }

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
            case "voldown": setVol(vol - 0.1f); break;
            case "volup": setVol(vol + 0.1f); break;
            case "rew": seek(-30000); break;
            case "ff": seek(30000); break;
            case "play": togglePlay(); break;
            case "pick": host.pickVideo(); break;
            case "url": host.openStreamDialog(); break;
            case "exit":
                if (uri != null && pos > 0) host.settings().setResumeMs(uri.toString(), pos);
                wm.close(window);
                break;
        }
    }

    @Override public void onBodyClick(Panel part) {
        if (part == video || part == chrome) togglePlay();
    }

    /** Loads an SRT subtitle file and syncs it with playback. */
    public void loadSubtitles(Uri u) {
        subTimes.clear();
        subTexts.clear();
        new Thread(() -> {
            try {
                java.io.BufferedReader in = new java.io.BufferedReader(
                        new java.io.InputStreamReader(host.activity().getContentResolver().openInputStream(u), "UTF-8"));
                java.util.regex.Pattern time = java.util.regex.Pattern.compile(
                        "(\\d+):(\\d+):(\\d+)[,.](\\d+)\\s*-->\\s*(\\d+):(\\d+):(\\d+)[,.](\\d+)");
                String l;
                java.util.ArrayList<String> pending = new java.util.ArrayList<>();
                long[][] cur = new long[1][];
                while ((l = in.readLine()) != null) {
                    java.util.regex.Matcher m = time.matcher(l);
                    if (m.find()) {
                        flushSub(cur[0], pending);
                        cur[0] = new long[]{
                                Long.parseLong(m.group(1)) * 3600000L + Long.parseLong(m.group(2)) * 60000L
                                        + Long.parseLong(m.group(3)) * 1000L + Long.parseLong(m.group(4)),
                                Long.parseLong(m.group(5)) * 3600000L + Long.parseLong(m.group(6)) * 60000L
                                        + Long.parseLong(m.group(7)) * 1000L + Long.parseLong(m.group(8))};
                        pending.clear();
                    } else if (cur[0] != null) {
                        String t = l.trim();
                        if (t.isEmpty()) { flushSub(cur[0], pending); cur[0] = null; }
                        else if (!t.matches("\\d+")) pending.add(t);
                    }
                }
                flushSub(cur[0], pending);
                in.close();
            } catch (Exception ignored) {}
            chrome.dirty = true;
        }, "vp-srt").start();
    }

    private void flushSub(long[] t, java.util.ArrayList<String> lines) {
        if (t == null || lines.isEmpty()) return;
        subTimes.add(t);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        subTexts.add(sb.toString());
    }

    private String mmss(int ms) {
        return String.format(java.util.Locale.US, "%d:%02d", ms / 60000, (ms / 1000) % 60);
    }

    /** Streams a remote URL (mp4 / HLS / radio) on the cinema screen. */
    public void loadStream(String url) {
        if (url == null || url.trim().isEmpty()) return;
        Uri u;
        try {
            u = Uri.parse(url.trim());
        } catch (Exception e) { return; }
        loadVideo(u, null, null);
        host.toast(host.activity().getString(R.string.stream_started));
    }

    @Override public void update() {
        if (mp != null && prepared && dur > 0 && !resumeApplied) {
            resumeApplied = true;
            if (uri != null) {
                long rs = host.settings().resumeMs(uri.toString());
                if (rs > 30000 && rs < dur - 10000) {
                    try {
                        mp.seekTo((int) rs);
                        host.toast(host.activity().getString(R.string.cinema_resume, mmss((int) rs)));
                    } catch (Exception ignored) {}
                }
            }
        }
        if (++tick % 20 == 0 && mp != null && prepared) {
            try {
                int np = mp.getCurrentPosition();
                int nd = mp.getDuration();
                if (np != pos || nd != dur) { pos = np; dur = nd; chrome.dirty = true; }
                if (mp.isPlaying() && tick % 180 == 0 && uri != null && pos > 0) {
                    host.settings().setResumeMs(uri.toString(), pos);
                }
            } catch (Exception ignored) {}
        }
    }

    @Override public void onClosed() {
        if (uri != null && pos > 0) host.settings().setResumeMs(uri.toString(), pos);
        releaseMp();
    }

    @Override public void onRestored() { video.visible = videoReady; }
}
