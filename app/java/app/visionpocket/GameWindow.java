package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** "Focus" — a calm reaction game: soft white targets appear in your space; look and tap. */
public class GameWindow implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VrRenderer ren;
    private final Random rnd = new Random();

    public final List<Panel> targets = new ArrayList<>();
    private static final int MAX_TARGETS = 16;

    private boolean running = false;
    private long roundEnd = 0;
    private float lastTick = 0;
    private int score = 0, streak = 0;
    private float nextSpawn = 0;
    private boolean overShown = false;

    private static final float TTL = 2.4f;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pVal = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMsg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDisc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pShadow = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GameWindow(VpWindows wm, VrRenderer ren) {
        this.wm = wm;
        this.ren = ren;
        window = new VpWindows.Window("game", ren.activity().getString(R.string.win_game));
        chrome = new Panel(0, 0, 0, 0, 1.25f, 0.5f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        chrome.add("start", 40, 28 + s, 300, 168 + s);
        chrome.add("reset", 320, 28 + s, 580, 168 + s);
        chrome.click = b -> onClick(b.id);

        pBg.setColor(0xFF101113);
        pBtn.setColor(0xFF1F2126);
        pBtnHover.setColor(0xFF2E323A);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF2E3138);
        pLabel.setColor(0xFFF2F3F5);
        pLabel.setTextSize(54f);
        pLabel.setTextAlign(Paint.Align.CENTER);
        pVal.setColor(0xFFE8ECF5);
        pVal.setTextSize(46f);
        pVal.setTextAlign(Paint.Align.CENTER);
        pMsg.setColor(0xFF9BA0AB);
        pMsg.setTextSize(40f);
        pMsg.setTextAlign(Paint.Align.CENTER);

        pDisc.setColor(0xFFF2F3F5);
        pRing.setColor(0x66F2F3F5);
        pRing.setStyle(Paint.Style.STROKE);
        pRing.setStrokeWidth(10f);
        pShadow.setColor(0x33888E99);
        pShadow.setStyle(Paint.Style.STROKE);
        pShadow.setStrokeWidth(22f);

        wm.placeNew(window);
    }

    private void startRound() {
        running = true;
        overShown = false;
        score = 0;
        streak = 0;
        roundEnd = SystemClock.uptimeMillis() + 60000;
        nextSpawn = 0;
        clearTargets();
        chrome.dirty = true;
    }

    private void stopRound() {
        running = false;
        clearTargets();
        chrome.dirty = true;
    }

    private void clearTargets() {
        for (Panel t : targets) {
            t.visible = false;
            t.clearHover();
        }
        chrome.dirty = true;
    }

    private void spawn() {
        Panel t = null;
        for (Panel cand : targets) if (!cand.visible) { t = cand; break; }
        if (t == null) {
            if (targets.size() >= MAX_TARGETS) return;
            t = new Panel(0, 0, 0, 0, 0.26f, 0.26f, 900f);
            t.hook = (c, p) -> drawTarget(c, p);
            targets.add(t);
        }
        float yaw = ren.headYaw() + (rnd.nextFloat() - 0.5f) * 1.5f;
        float pitch = (rnd.nextFloat() - 0.35f) * 0.95f;
        float dist = 1.7f + rnd.nextFloat() * 1.1f;
        float cp = (float) Math.cos(pitch);
        t.cx = (float) Math.sin(yaw) * cp * dist;
        t.cy = (float) Math.sin(pitch) * dist + 0.1f;
        t.cz = -(float) Math.cos(yaw) * cp * dist;
        t.yaw = yaw;
        t.tag = SystemClock.uptimeMillis();
        t.visible = true;
        t.dirty = true;
    }

    private void drawTarget(Canvas c, Panel p) {
        long born = (Long) p.tag;
        float age = (SystemClock.uptimeMillis() - born) / 1000f;
        float life = 1f - Math.min(1f, Math.max(0f, (age - (TTL - 0.5f)) / 0.5f));
        float grow = Math.min(1f, age / 0.14f);
        float r = p.pw / 2f * 0.42f * grow * (0.65f + 0.35f * life);
        float cx = p.pw / 2f, cy = p.ph / 2f;
        if (p.hovered) {
            pDisc.setColor(0xFFFFFFFF);
            pRing.setColor(0xCCF2F3F5);
        } else {
            pDisc.setColor(0xEEF2F3F5);
            pRing.setColor(0x55F2F3F5);
        }
        c.drawCircle(cx, cy, r * 1.22f, pShadow);
        c.drawCircle(cx, cy, r, pDisc);
        c.drawCircle(cx, cy, r * 1.3f, pRing);
    }

    private void pop(Panel t) {
        streak++;
        score += 100 + (streak - 1) * 25;
        t.visible = false;
        ren.activity().playClick();
        chrome.dirty = true;
    }

    private void miss() {
        streak = 0;
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF101113);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);
        int secs = running ? Math.max(0, Math.round((roundEnd - SystemClock.uptimeMillis()) / 1000f)) : 60;
        Paint left = new Paint(pVal); left.setTextAlign(Paint.Align.LEFT);
        Paint right = new Paint(pVal); right.setTextAlign(Paint.Align.RIGHT);
        c.drawText(host().getString(R.string.game_score) + " " + score, 40f, 86f, left);
        c.drawText(secs + " s", p.pw / 2f, 86f, pVal);
        c.drawText(host().getString(R.string.game_best) + " " + ren.settings().focusBest(),
                p.pw - 40f, 86f, right);
        if (!running) {
            c.drawText(host().getString(R.string.game_hint), p.pw / 2f, 190f, pMsg);
            if (overShown) {
                c.drawText(host().getString(R.string.game_over, score), p.pw / 2f, 250f, pVal);
            }
        }
        for (Panel.Button b : p.buttons) {
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            c.drawRoundRect(r, 20, 20, b.hover ? pBtnHover : pBtn);
            pBorder.setColor(b.hover ? 0xFF8FA3C8 : 0xFF2E3138);
            c.drawRoundRect(r, 20, 20, pBorder);
            String t = "start".equals(b.id)
                    ? (running ? host().getString(R.string.game_stop) : host().getString(R.string.game_start))
                    : host().getString(R.string.game_reset);
            HomeSceneUtil.drawCentered(c, t, r, pLabel);
        }
    }

    private android.content.Context host() { return ren.activity(); }

    @Override public void onClick(String id) {
        if ("start".equals(id)) {
            if (running) stopRound(); else startRound();
        } else if ("reset".equals(id)) {
            score = 0;
            streak = 0;
            overShown = false;
            clearTargets();
        }
        chrome.dirty = true;
    }

    @Override public void onBodyClick(Panel part) {
        if (running && part.visible) pop(part);
    }

    public void rotateOverlay(float d) {
        for (Panel t : targets) if (t.visible) t.rotateBy(d);
    }

    @Override public void update() {
        float now = SystemClock.uptimeMillis() / 1000f;
        float dt = lastTick == 0 ? 0.016f : Math.min(0.1f, now - lastTick);
        lastTick = now;
        if (!running) return;
        if (now * 1000 >= roundEnd) {
            running = false;
            overShown = true;
            clearTargets();
            SettingsStore st = ren.settings();
            if (score > st.focusBest()) {
                st.setFocusBest(score);
                ren.toast(host().getString(R.string.game_new_best, score));
            } else {
                ren.toast(host().getString(R.string.game_over, score));
            }
            chrome.dirty = true;
            return;
        }
        // expire targets
        boolean changed = false;
        for (Panel t : targets) {
            if (!t.visible) continue;
            long born = (Long) t.tag;
            if ((SystemClock.uptimeMillis() - born) > TTL * 1000f) {
                t.visible = false;
                miss();
                changed = true;
            } else {
                t.dirty = true; // animate
            }
        }
        // spawn to keep 3 alive
        int alive = 0;
        for (Panel t : targets) if (t.visible) alive++;
        nextSpawn -= dt;
        if (alive < 3 && nextSpawn <= 0) {
            spawn();
            nextSpawn = 0.35f;
        }
        if (changed) chrome.dirty = true;
    }

    @Override public void onClosed() { running = false; clearTargets(); }

    @Override public void onRestored() {}
}
