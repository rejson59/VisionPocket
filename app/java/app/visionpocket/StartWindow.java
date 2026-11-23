package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.Calendar;

/**
 * Home dashboard v2 — the "better interface":
 *  - greeting header with big clock + battery
 *  - 2x3 app tiles (icon + label + one-line description)
 *  - orientation confirm row (✓ straight / ↻ 180°)
 *  - actions row, live telemetry footer
 * All chrome drawn through UiKit for a consistent look.
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

    private final Paint pTime = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDate = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBattery = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pIcon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTileLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTileSub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStatus = UiKit.monoPaint();

    public StartWindow(VpWindows wm, VpWindows.WmHost host) {
        this.wm = wm;
        this.host = host;
        window = new VpWindows.Window("start", host.activity().getString(R.string.win_start));
        chrome = new Panel(0, 0, 0, 0, 1.6f, 1.5f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        String[] ids = {"cinema", "photos", "mirror", "notes", "clock", "game"};
        for (int i = 0; i < 6; i++) {
            int col = i % 3, row = i / 3;
            int x0 = 60 + col * 388;
            int y0 = 250 + row * 190;
            chrome.add(ids[i], x0, y0 + s, x0 + 368, y0 + 170 + s);
        }
        chrome.add("up_ok", 60, 660 + s, 620, 790 + s);
        chrome.add("up_flip", 660, 660 + s, 1220, 790 + s);
        chrome.add("do_recenter", 60, 810 + s, 620, 920 + s);
        chrome.add("close", 660, 810 + s, 1220, 920 + s);
        chrome.click = b -> onClick(b.id);

        pTime.setColor(UiKit.TEXT);
        pTime.setTextSize(110f);
        pTime.setFakeBoldText(true);
        pTime.setTextAlign(Paint.Align.LEFT);
        pDate.setColor(UiKit.TEXT_DIM);
        pDate.setTextSize(40f);
        pDate.setTextAlign(Paint.Align.LEFT);
        pBattery.setColor(UiKit.TEXT_DIM);
        pBattery.setTextSize(40f);
        pBattery.setTextAlign(Paint.Align.RIGHT);
        pIcon.setTextSize(64f);
        pIcon.setTextAlign(Paint.Align.CENTER);
        pTileLabel.setColor(UiKit.TEXT);
        pTileLabel.setTextSize(42f);
        pTileLabel.setTextAlign(Paint.Align.CENTER);
        pTileSub.setColor(UiKit.TEXT_DIM);
        pTileSub.setTextSize(28f);
        pTileSub.setTextAlign(Paint.Align.CENTER);

        wm.placeNew(window);
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(UiKit.BG);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, bg);

        // header
        Calendar cal = Calendar.getInstance();
        String time = String.format(java.util.Locale.getDefault(), "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        c.drawText(time, 60f, 160f, pTime);
        String date = String.format(java.util.Locale.getDefault(), "%tA, %1$te %1$tB", cal);
        c.drawText(date, 64f, 222f, pDate);
        c.drawText("🔋 " + host.activity().batteryPct() + "%", p.pw - 64f, 160f, pBattery);

        // tiles
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
        for (int i = 0; i < 6; i++) {
            Panel.Button b = null;
            for (Panel.Button bb : p.buttons) if (TILE_KINDS[i].equals(bb.id)) b = bb;
            if (b == null) continue;
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            f.setColor(b.hover ? UiKit.CARD_HOVER : UiKit.CARD);
            c.drawRoundRect(r, 26, 26, f);
            Paint br = new Paint(Paint.ANTI_ALIAS_FLAG);
            br.setStyle(Paint.Style.STROKE);
            br.setStrokeWidth(3f);
            br.setColor(b.hover ? UiKit.BORDER_HOVER : UiKit.BORDER);
            c.drawRoundRect(r, 26, 26, br);
            c.drawText(TILE_ICONS[i], r.centerX(), r.centerY() - 22f
                    - (pIcon.ascent() + pIcon.descent()) / 2f, pIcon);
            c.drawText(labels[i], r.centerX(), r.centerY() + 34f, pTileLabel);
            c.drawText(subs[i], r.centerX(), r.centerY() + 78f, pTileSub);
        }

        // orientation confirm + actions
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

        // live telemetry footer
        c.drawText(host.activity().getString(R.string.start_tip1), 64f, 1000f, pTileSub);
        UiKit.status(c, host.pipelineInfo(), 64f, 1060f);
        UiKit.status(c, host.headAngles(), 64f, 1110f);
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
