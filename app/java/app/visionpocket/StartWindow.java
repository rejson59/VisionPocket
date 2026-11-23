package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.Calendar;

/**
 * Home hub v3 — built for the 360-degree world:
 *  - the window anchor sits AT the user, so its parts form a ring around them
 *  - dashboard panel right in front (clock, battery, orientation, actions,
 *    live telemetry)
 *  - six app tiles placed around the head at 60-degree steps (front, both
 *    sides, behind) — look anywhere, there is an app there
 */
public class StartWindow implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VpWindows.WmHost host;
    private int lastSecond = -1;
    private int tick = 0;

    private static final String[] TILE_KINDS = {"cinema", "photos", "mirror", "notes", "clock", "game"};
    private static final String[] TILE_ICONS = {"🎬", "🖼", "▣", "📝", "🕒", "🎯"};
    // Quest-like launcher: dashboard above, one arc row of tiles below.
    // ALL UI lives inside the visible FOV — nothing behind the user.
    private static final float[] TILE_YAW_DEG = {-30f, -18f, -6f, 6f, 18f, 30f};
    private static final float RING_R = 2.7f;
    private static final float TILE_Y = -0.25f;

    private final Paint pTime = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDate = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBattery = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pIcon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTileLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTileSub = new Paint(Paint.ANTI_ALIAS_FLAG);

    public StartWindow(VpWindows wm, VpWindows.WmHost host) {
        this.wm = wm;
        this.host = host;
        window = new VpWindows.Window("start", host.activity().getString(R.string.win_start));

        // dashboard raised, in front of the user
        chrome = new Panel(0, 0, 0, 0, 1.30f, 0.85f, 800f);
        window.addPart(chrome, 0f, 0.45f, -2.2f);
        window.attachChrome(chrome);
        window.content = this;
        int s = VpWindows.STRIP_PX;
        chrome.add("up_ok", 60, 280 + s, 500, 385 + s);
        chrome.add("up_flip", 540, 280 + s, 980, 385 + s);
        chrome.add("do_recenter", 60, 405 + s, 500, 510 + s);
        chrome.add("close", 540, 405 + s, 980, 510 + s);
        chrome.click = b -> onClick(b.id);

        // one arc row of app tiles below the dashboard, all facing the user
        for (int i = 0; i < 6; i++) {
            float a = (float) Math.toRadians(TILE_YAW_DEG[i]);
            Panel tile = new Panel(0, 0, 0, a, 0.62f, 0.42f, 800f);
            tile.add(TILE_KINDS[i], 0, 0, tile.pw, tile.ph);
            final int idx = i;
            tile.hook = (c, p) -> drawTile(c, p, idx);
            float ox = (float) Math.sin(a) * RING_R;
            float oz = -(float) Math.cos(a) * RING_R;
            window.addPartRing(tile, ox, TILE_Y, oz, a);
        }

        pTime.setColor(UiKit.TEXT);
        pTime.setTextSize(84f);
        pTime.setFakeBoldText(true);
        pTime.setTextAlign(Paint.Align.LEFT);
        pDate.setColor(UiKit.TEXT_DIM);
        pDate.setTextSize(32f);
        pDate.setTextAlign(Paint.Align.LEFT);
        pBattery.setColor(UiKit.TEXT_DIM);
        pBattery.setTextSize(32f);
        pBattery.setTextAlign(Paint.Align.RIGHT);
        pIcon.setTextSize(60f);
        pIcon.setTextAlign(Paint.Align.CENTER);
        pTileLabel.setColor(UiKit.TEXT);
        pTileLabel.setTextSize(40f);
        pTileLabel.setTextAlign(Paint.Align.CENTER);
        pTileSub.setColor(UiKit.TEXT_DIM);
        pTileSub.setTextSize(24f);
        pTileSub.setTextAlign(Paint.Align.CENTER);

        // anchor AT the user: parts are positioned relative to the head,
        // recenter rotates the whole ring around the user
        window.ax = 0f; window.ay = 0f; window.az = 0f;
        window.ayaw = host.headYaw();
        window.layout();
        wm.add(window);
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(UiKit.BG);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, bg);

        Calendar cal = Calendar.getInstance();
        String time = String.format(java.util.Locale.getDefault(), "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        c.drawText(time, 56f, 135f, pTime);
        String date = String.format(java.util.Locale.getDefault(), "%tA, %1$te %1$tB", cal);
        c.drawText(date, 60f, 195f, pDate);
        c.drawText("🔋 " + host.activity().batteryPct() + "%", p.pw - 56f, 135f, pBattery);

        UiKit.section(c, host.activity().getString(R.string.start_sec_orient), 60f, 262f);
        for (Panel.Button b : p.buttons) {
            if (!"up_ok".equals(b.id) && !"up_flip".equals(b.id)
                    && !"do_recenter".equals(b.id) && !"close".equals(b.id)) continue;
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            boolean primary = "up_ok".equals(b.id);
            String label;
            switch (b.id) {
                case "up_ok": label = host.activity().getString(R.string.start_up_ok); break;
                case "up_flip": label = host.activity().getString(R.string.start_up_flip); break;
                case "close": label = host.activity().getString(R.string.common_close); break;
                default: label = host.activity().getString(R.string.common_recenter);
            }
            UiKit.button(c, r, label, b.hover, primary);
        }

        // live telemetry
        UiKit.status(c, host.pipelineInfo(), 60f, 580f);
        UiKit.status(c, host.headAngles(), 60f, 630f);
    }

    private void drawTile(Canvas c, Panel p, int idx) {
        String[] labels = {
                host.activity().getString(R.string.win_cinema),
                host.activity().getString(R.string.win_photos),
                host.activity().getString(R.string.win_mirror),
                host.activity().getString(R.string.win_notes),
                host.activity().getString(R.string.win_clock),
                host.activity().getString(R.string.win_game)
        };
        String[] subs = {
                host.activity().getString(R.string.start_sub_cinema),
                host.activity().getString(R.string.start_sub_photos),
                host.activity().getString(R.string.start_sub_mirror),
                host.activity().getString(R.string.start_sub_notes),
                host.activity().getString(R.string.start_sub_clock),
                host.activity().getString(R.string.start_sub_game)
        };
        Panel.Button b = p.buttons.isEmpty() ? null : p.buttons.get(0);
        boolean hover = b != null && b.hover;
        RectF r = new RectF(10, 10, p.pw - 10, p.ph - 10);
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setColor(hover ? UiKit.CARD_HOVER : UiKit.CARD);
        c.drawRoundRect(r, 30, 30, f);
        Paint br = new Paint(Paint.ANTI_ALIAS_FLAG);
        br.setStyle(Paint.Style.STROKE);
        br.setStrokeWidth(4f);
        br.setColor(hover ? UiKit.BORDER_HOVER : UiKit.BORDER);
        c.drawRoundRect(r, 30, 30, br);
        c.drawText(TILE_ICONS[idx], p.pw / 2f, p.ph * 0.44f, pIcon);
        c.drawText(labels[idx], p.pw / 2f, p.ph * 0.74f, pTileLabel);
        c.drawText(subs[idx], p.pw / 2f, p.ph * 0.92f, pTileSub);
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {
        if ("up_ok".equals(id)) {
            host.activity().playChime();
        } else if ("up_flip".equals(id)) {
            host.settings().setViewRot(host.settings().viewRot() + 2);
            host.recenterView();
            host.activity().playChime();
        } else if ("do_recenter".equals(id)) host.recenterView();
        else if ("close".equals(id)) wm.close(window);
        else host.openApp(id);
        chrome.dirty = true;
    }

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {
        int sec = Calendar.getInstance().get(Calendar.SECOND);
        if (sec != lastSecond || ++tick % 5 == 0) {
            lastSecond = sec;
            chrome.dirty = true;
        }
    }

    @Override public void onClosed() {}

    @Override public void onRestored() {}
}
