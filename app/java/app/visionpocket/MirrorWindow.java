package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Screen mirror window: the phone's display rendered on a big VR screen. */
public class MirrorWindow implements WindowContent {

    public final VpWindows.Window window;
    public final Panel screen;    // OES mirror surface part
    private final Panel chrome;
    private final VpWindows wm;
    private final VrRenderer ren;

    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMsg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int lastMode = -1;

    public MirrorWindow(VpWindows wm, VrRenderer ren) {
        this.wm = wm;
        this.ren = ren;
        window = new VpWindows.Window("mirror", ren.activity().getString(R.string.win_mirror));
        chrome = new Panel(0, 0, 0, 0, 1.3f, 0.4f, 800f);
        screen = new Panel(0, 0, 0, 0, 1.1f, 1.9f, 800f);
        screen.oes = true;
        screen.visible = false;

        window.addPart(screen, 0f, 0.4f / 2f + 1.9f / 2f + 0.06f, 0f);
        window.addPart(chrome, 0f, 0f, 0f);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        chrome.add("mstart", 60, 30 + s, 460, 170 + s);
        chrome.add("mstop", 500, 30 + s, 900, 170 + s);
        chrome.click = b -> onClick(b.id);

        pBtn.setColor(0xFF232838);
        pBtnHover.setColor(0xFF3D4964);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF39415A);
        pLabel.setColor(0xFFE8ECF5);
        pLabel.setTextSize(58f);
        pLabel.setTextAlign(Paint.Align.CENTER);
        pMsg.setColor(0xFF9AA3B8);
        pMsg.setTextSize(44f);
        pMsg.setTextAlign(Paint.Align.CENTER);

        wm.placeNew(window);
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        boolean live = ren.mirrorMode() == 1;
        if (!live) {
            c.drawText(ren.activity().getString(R.string.mirror_hint), p.pw / 2f, 120f, pMsg);
        }
        for (Panel.Button b : p.buttons) {
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            c.drawRoundRect(r, 22, 22, b.hover ? pBtnHover : pBtn);
            pBorder.setColor(b.hover ? 0xFF5B8DEF : 0xFF39415A);
            c.drawRoundRect(r, 22, 22, pBorder);
            String label = "mstart".equals(b.id)
                    ? ren.activity().getString(R.string.mirror_start)
                    : ren.activity().getString(R.string.mirror_stop);
            HomeSceneUtil.drawCentered(c, label, r, pLabel);
        }
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {
        if ("mstart".equals(id)) {
            ren.requestMirror();
        } else if ("mstop".equals(id)) {
            ren.stopMirror();
        }
        chrome.dirty = true;
    }

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {
        // redraw only when mirror state changed (canvas upload is expensive)
        int m = ren.mirrorMode();
        if (m != lastMode) { lastMode = m; chrome.dirty = true; }
    }

    @Override public void onClosed() { ren.stopMirror(); }

    @Override public void onRestored() { screen.visible = ren.mirrorMode() == 1; }

    /** Renderer informs about mirror aspect + live state. */
    public void setScreenAspect(float aspect) {
        float hv = 1.9f;
        float hw = Math.max(0.6f, Math.min(2.9f, hv * aspect));
        window.setPartGeometry(screen, hw, hv, 0f, 0.4f / 2f + hv / 2f + 0.06f, 0f);
        screen.visible = true;
        chrome.dirty = true;
    }
}
