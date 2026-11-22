package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Start window: welcome, tips and quick actions (opened automatically at boot). */
public class StartWindow implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VpWindows.WmHost host;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHead = new Paint(Paint.ANTI_ALIAS_FLAG);

    public StartWindow(VpWindows wm, VpWindows.WmHost host) {
        this.wm = wm;
        this.host = host;
        window = new VpWindows.Window("start", host.activity().getString(R.string.win_start));
        chrome = new Panel(0, 0, 0, 0, 1.45f, 1.3f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        chrome.add("do_recenter", 60, 620 + s, 560, 760 + s);
        chrome.add("close", 600, 620 + s, 1100, 760 + s);
        chrome.click = b -> onClick(b.id);

        pBg.setColor(0xFF0E101A);
        pBtn.setColor(0xFF232838);
        pBtnHover.setColor(0xFF3D4964);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF39415A);
        pLabel.setColor(0xFFE8ECF5);
        pLabel.setTextSize(56f);
        pLabel.setTextAlign(Paint.Align.CENTER);
        pText.setColor(0xFFB9C2D8);
        pText.setTextSize(44f);
        pText.setTextAlign(Paint.Align.LEFT);
        pHead.setColor(0xFF8FA7E8);
        pHead.setTextSize(50f);
        pHead.setFakeBoldText(true);
        pHead.setTextAlign(Paint.Align.LEFT);

        wm.placeNew(window);
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF0E101A);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);

        c.drawText(host.activity().getString(R.string.start_tagline), 60f, 80f, pHead);
        String[] tips = {
                host.activity().getString(R.string.start_tip1),
                host.activity().getString(R.string.start_tip2),
                host.activity().getString(R.string.start_tip3),
                host.activity().getString(R.string.start_tip4)
        };
        float y = 170f;
        for (String t : tips) {
            c.drawText("•  " + t, 60f, y, pText);
            y += 92f;
        }

        for (Panel.Button b : p.buttons) {
            Paint fill = b.hover ? pBtnHover : pBtn;
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            c.drawRoundRect(r, 26, 26, fill);
            pBorder.setColor(b.hover ? 0xFF5B8DEF : 0xFF39415A);
            c.drawRoundRect(r, 26, 26, pBorder);
            String label = "close".equals(b.id)
                    ? host.activity().getString(R.string.common_close)
                    : host.activity().getString(R.string.common_recenter);
            HomeSceneUtil.drawCentered(c, label, r, pLabel);
        }
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {
        if ("do_recenter".equals(id)) host.recenterView();
        else if ("close".equals(id)) wm.close(window);
    }

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {}

    @Override public void onClosed() {}

    @Override public void onRestored() {}
}
