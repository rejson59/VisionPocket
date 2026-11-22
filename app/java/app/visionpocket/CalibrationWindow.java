package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** IPD calibration window: adjust until the line pattern looks sharpest. */
public class CalibrationWindow implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VpWindows.WmHost host;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CalibrationWindow(VpWindows wm, VpWindows.WmHost host) {
        this.wm = wm;
        this.host = host;
        window = new VpWindows.Window("calib", host.activity().getString(R.string.win_calib));
        chrome = new Panel(0, 0, 0, 0, 1.35f, 1.0f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        chrome.add("ipd_m", 60, 560 + s, 360, 670 + s);
        chrome.add("ipd_p", 400, 560 + s, 700, 670 + s);
        chrome.add("done", 740, 560 + s, 1020, 670 + s);
        chrome.click = b -> onClick(b.id);

        pBg.setColor(0xFF101113);
        pLine.setColor(0xFF8FA3C8);
        pLine.setStrokeWidth(4f);
        pBtn.setColor(0xFF1F2126);
        pBtnHover.setColor(0xFF2E323A);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF2E3138);
        pLabel.setColor(0xFFE8ECF5);
        pLabel.setTextSize(54f);
        pLabel.setTextAlign(Paint.Align.CENTER);
        pText.setColor(0xFF9BA0AB);
        pText.setTextSize(40f);
        pText.setTextAlign(Paint.Align.LEFT);

        wm.placeNew(window);
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF101113);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);

        // vertical line test pattern (adjust IPD until it looks crisp & single)
        int top = 16, bot = 380;
        for (int i = 0; i < 14; i++) {
            float x = 40 + i * (p.pw - 80) / 13f;
            c.drawLine(x, top, x, bot, i % 3 == 0 ? pLine : pLine);
        }
        c.drawLine(40, top, p.pw - 40, top, pLine);
        c.drawLine(40, bot, p.pw - 40, bot, pLine);

        c.drawText(host.activity().getString(R.string.calib_hint1), 40f, 440f, pText);
        c.drawText(host.activity().getString(R.string.calib_hint2), 40f, 488f, pText);

        for (Panel.Button b : p.buttons) {
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            c.drawRoundRect(r, 20, 20, b.hover ? pBtnHover : pBtn);
            pBorder.setColor(b.hover ? 0xFF8FA3C8 : 0xFF2E3138);
            c.drawRoundRect(r, 20, 20, pBorder);
            String t;
            if ("ipd_m".equals(b.id)) t = "−  " + host.settings().ipd() + " mm";
            else if ("ipd_p".equals(b.id)) t = "+  " + host.settings().ipd() + " mm";
            else t = host.activity().getString(R.string.calib_done);
            HomeSceneUtil.drawCentered(c, t, r, pLabel);
        }
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {
        if ("ipd_m".equals(id)) host.settings().setIpd(host.settings().ipd() - 1);
        else if ("ipd_p".equals(id)) host.settings().setIpd(host.settings().ipd() + 1);
        else if ("done".equals(id)) wm.close(window);
        chrome.dirty = true;
    }

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {}

    @Override public void onClosed() {}

    @Override public void onRestored() {}
}
