package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.Calendar;

/** Home dashboard: big clock, battery, quick-launch tiles and recenter/close. */
public class StartWindow implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VpWindows.WmHost host;
    private int lastSecond = -1;

    private static final String[] TILE_KINDS = {"cinema", "photos", "mirror", "notes", "clock", "game"};
    private static final String[] TILE_ICONS = {"🎬", "🖼", "▣", "📝", "🕒", "🎯"};

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTile = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTileHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTime = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDate = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBattery = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pIcon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAccent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAccentHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPrimary = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStatus = new Paint(Paint.ANTI_ALIAS_FLAG);

    public StartWindow(VpWindows wm, VpWindows.WmHost host) {
        this.wm = wm;
        this.host = host;
        window = new VpWindows.Window("start", host.activity().getString(R.string.win_start));
        chrome = new Panel(0, 0, 0, 0, 1.5f, 1.35f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        String[] ids = {"cinema", "photos", "mirror", "notes", "clock", "game"};
        for (int i = 0; i < 6; i++) {
            int col = i % 3, row = i / 3;
            int x0 = 60 + col * 370;
            int y0 = 240 + row * 180;
            chrome.add(ids[i], x0, y0 + s, x0 + 340, y0 + 160 + s);
        }
        chrome.add("up_ok", 60, 600 + s, 560, 730 + s);
        chrome.add("up_flip", 620, 600 + s, 1140, 730 + s);
        chrome.add("do_recenter", 60, 750 + s, 560, 860 + s);
        chrome.add("close", 640, 750 + s, 1140, 860 + s);
        chrome.click = b -> onClick(b.id);

        pBg.setColor(0xFF101113);
        pTile.setColor(0xFF1A1C21);
        pTileHover.setColor(0xFF2E323A);
        pBtn.setColor(0xFF1F2126);
        pBtnHover.setColor(0xFF2E323A);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF2E3138);
        pTime.setColor(0xFFE8ECF5);
        pTime.setTextSize(120f);
        pTime.setFakeBoldText(true);
        pTime.setTextAlign(Paint.Align.LEFT);
        pDate.setColor(0xFF9BA0AB);
        pDate.setTextSize(44f);
        pDate.setTextAlign(Paint.Align.LEFT);
        pBattery.setColor(0xFF9BA0AB);
        pBattery.setTextSize(44f);
        pBattery.setTextAlign(Paint.Align.RIGHT);
        pIcon.setTextSize(62f);
        pIcon.setTextAlign(Paint.Align.CENTER);
        pLabel.setColor(0xFFDCE3F2);
        pLabel.setTextSize(40f);
        pLabel.setTextAlign(Paint.Align.CENTER);
        pText.setColor(0xFF8A909C);
        pText.setTextSize(38f);
        pText.setTextAlign(Paint.Align.LEFT);
        pAccent.setColor(0xFF8FA3C8);
        pAccentHover.setColor(0xFFB4C2DD);
        pPrimary.setColor(0xFF101216);
        pPrimary.setTextSize(46f);
        pPrimary.setTextAlign(Paint.Align.CENTER);
        pStatus.setColor(0xFF7E838E);
        pStatus.setTextSize(30f);
        pStatus.setTextAlign(Paint.Align.LEFT);

        wm.placeNew(window);
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF101113);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);

        Calendar cal = Calendar.getInstance();
        String time = String.format(java.util.Locale.getDefault(), "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        c.drawText(time, 60f, 170f, pTime);
        String date = String.format(java.util.Locale.getDefault(), "%tA, %1$te %1$tB", cal);
        c.drawText(date, 62f, 240f, pDate);
        c.drawText("🔋 " + host.activity().batteryPct() + "%", p.pw - 60f, 170f, pBattery);

        String[] labels = {
                host.activity().getString(R.string.win_cinema),
                host.activity().getString(R.string.win_photos),
                host.activity().getString(R.string.win_mirror),
                host.activity().getString(R.string.win_notes),
                host.activity().getString(R.string.win_clock),
                host.activity().getString(R.string.win_game)
        };
        for (int i = 0; i < 6; i++) {
            Panel.Button b = null;
            for (Panel.Button bb : p.buttons) if (TILE_KINDS[i].equals(bb.id)) b = bb;
            if (b == null) continue;
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            c.drawRoundRect(r, 24, 24, b.hover ? pTileHover : pTile);
            pBorder.setColor(b.hover ? 0xFF8FA3C8 : 0xFF2E3138);
            c.drawRoundRect(r, 24, 24, pBorder);
            c.drawText(TILE_ICONS[i], r.centerX(), r.centerY() - 8f - (pIcon.ascent() + pIcon.descent()) / 2f, pIcon);
            c.drawText(labels[i], r.centerX(), r.centerY() + 52f, pLabel);
        }

        for (Panel.Button b : p.buttons) {
            if ("up_ok".equals(b.id) || "up_flip".equals(b.id)
                    || "do_recenter".equals(b.id) || "close".equals(b.id)) {
                RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
                boolean primary = "up_ok".equals(b.id);
                if (primary) {
                    c.drawRoundRect(r, 26, 26, b.hover ? pAccentHover : pAccent);
                } else {
                    c.drawRoundRect(r, 26, 26, b.hover ? pBtnHover : pBtn);
                }
                pBorder.setColor(b.hover ? 0xFF8FA3C8 : 0xFF2E3138);
                c.drawRoundRect(r, 26, 26, pBorder);
                String label;
                switch (b.id) {
                    case "up_ok": label = host.activity().getString(R.string.start_up_ok); break;
                    case "up_flip": label = host.activity().getString(R.string.start_up_flip); break;
                    case "close": label = host.activity().getString(R.string.common_close); break;
                    default: label = host.activity().getString(R.string.common_recenter);
                }
                HomeSceneUtil.drawCentered(c, label, r, primary ? pPrimary : pLabel);
            }
        }
        c.drawText(host.activity().getString(R.string.start_tip1), 62f, 920f, pText);
        c.drawText(host.pipelineInfo(), 62f, 1000f, pStatus);
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
        if (sec != lastSecond) {
            lastSecond = sec;
            chrome.dirty = true;
        }
    }

    @Override public void onClosed() {}

    @Override public void onRestored() {}
}
