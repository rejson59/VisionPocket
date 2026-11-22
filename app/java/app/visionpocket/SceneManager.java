package app.visionpocket;

import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

/** Scene switching with fade transitions. */
public class SceneManager {

    public enum SceneId { HOME, SETTINGS, CINEMA }

    private static final long FADE_MS = 260;

    private final SceneHost host;
    public final HomeScene home;
    public final SettingsScene settingsScene;
    public final CinemaScene cinema;

    private SceneId current = SceneId.HOME;
    private SceneId pending = null;
    private boolean fadingOut = false;
    private long fadeStart = 0;
    private float fade = 1f;

    public SceneManager(SceneHost host) {
        this.host = host;
        home = new HomeScene(host);
        settingsScene = new SettingsScene(host);
        cinema = new CinemaScene(host);
        home.onShown();
    }

    public SceneId current() { return current; }
    public float fade() { return fade; }

    private Scene scene(SceneId s) {
        switch (s) {
            case SETTINGS: return settingsScene;
            case CINEMA: return cinema;
            default: return home;
        }
    }

    public List<Panel> panels() {
        List<Panel> out = new ArrayList<>(4);
        for (Panel p : scene(current).panels()) if (p.visible) out.add(p);
        return out;
    }

    public void show(SceneId s) {
        if (s == current && pending == null) return;
        pending = s;
        fadingOut = true;
        fadeStart = SystemClock.uptimeMillis();
    }

    public void tick() {
        if (pending == null) return;
        long now = SystemClock.uptimeMillis();
        if (fadingOut) {
            fade = Math.min(1f, (now - fadeStart) / (float) FADE_MS);
            if (fade >= 1f) {
                scene(current).onHidden();
                current = pending;
                pending = null;
                scene(current).onShown();
                try { host.activity().playChime(); } catch (Exception ignored) {}
                fadingOut = false;
                fadeStart = now;
            }
        } else {
            fade = Math.max(0f, 1f - (now - fadeStart) / (float) FADE_MS);
            if (fade <= 0f) {
                pending = null;
            }
        }
    }

    public void onClick(Panel.Button b) {
        if (b == null) return;
        scene(current).onClick(b);
    }

    public void update() { scene(current).update(); }

    public void onRecenter(float d) {
        home.onRecenter(d);
        settingsScene.onRecenter(d);
        cinema.onRecenter(d);
    }

    /** Cinema helper used by the renderer. */
    public void cinemaLoadVideo(Uri u, SurfaceTexture st, android.view.Surface surface) {
        cinema.loadVideo(u, st, surface);
    }

    public CinemaScene cinema() { return cinema; }
}
