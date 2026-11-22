package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Environment window: switch the world background (passthrough / space / horizon). */
public class EnvWindow implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VpWindows.WmHost host;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSym = new Paint(Paint.ANTI_ALIAS_FLAG);

    public EnvWindow(VpWindows wm, VpWindows.WmHost host) {
        this.wm = wm;
        this.host = host;
        window = new VpWindows.Window("env", host.activity().getString(R.string.win_env));
        chrome = new Panel(0, 0, 0, 0, 1.15f, 1.24f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        chrome.add("env0", 60, 16 + s, 860, 196 + s);
        chrome.add("env1", 60, 216 + s, 860, 396 + s);
        chrome.add("env2", 60, 416 + s, 860, 596 + s);
        chrome.add("bright_m", 60, 620 + s, 260, 700 + s);
        chrome.add("bright_p", 280, 620 + s, 480, 700 + s);
        chrome.add("autorec", 520, 620 + s, 860, 700 + s);
        chrome.click = b -> onClick(b.id);

        pBg.setColor(0xFF101113);
        pBtn.setColor(0xFF1F2126);
        pBtnHover.setColor(0xFF2E323A);
        pBtnActive.setColor(0xFF343945);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF2E3138);
        pLabel.setColor(0xFFE8ECF5);
        pLabel.setTextSize(58f);
        pLabel.setTextAlign(Paint.Align.LEFT);
        pSym.setTextSize(72f);
        pSym.setTextAlign(Paint.Align.CENTER);

        wm.placeNew(window);
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF101113);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);
        int mode = host.settings().envMode();
        String[] icons = {"👁", "◻", "🌆"};
        String[] labels = {
                host.activity().getString(R.string.env_pt),
                host.activity().getString(R.string.env_space),
                host.activity().getString(R.string.env_horizon)
        };
        for (int i = 0; i < 3; i++) {
            Panel.Button b = null;
            for (Panel.Button bb : p.buttons) if (("env" + i).equals(bb.id)) b = bb;
            if (b == null) continue;
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            Paint fill = b.hover ? pBtnHover : (mode == i ? pBtnActive : pBtn);
            c.drawRoundRect(r, 28, 28, fill);
            pBorder.setColor(b.hover ? 0xFF8FA3C8 : (mode == i ? 0xFF8FA3C8 : 0xFF2E3138));
            c.drawRoundRect(r, 28, 28, pBorder);
            pSym.setTextAlign(Paint.Align.CENTER);
            c.drawText(icons[i], r.left + 110f, r.centerY() - (pSym.ascent() + pSym.descent()) / 2f, pSym);
            c.drawText(labels[i], r.left + 220f, r.centerY() - (pLabel.ascent() + pLabel.descent()) / 2f, pLabel);
        }

        // brightness row
        float br = host.settings().brightness();
        Paint valP = new Paint(pLabel);
        valP.setTextAlign(Paint.Align.CENTER);
        for (Panel.Button b : p.buttons) {
            if (!"bright_m".equals(b.id) && !"bright_p".equals(b.id)) continue;
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            c.drawRoundRect(r, 18, 18, b.hover ? pBtnHover : pBtn);
            c.drawRoundRect(r, 18, 18, pBorder);
            c.drawText("bright_m".equals(b.id) ? "−" : "+",
                    r.centerX(), r.centerY() - (pSym.ascent() + pSym.descent()) / 2f, pSym);
        }
        c.drawText("☀ " + String.format(java.util.Locale.US, "%.1f", br), 520f,
                684f + s, valP);

        // auto recenter toggle
        boolean ar = host.settings().autoRecenter();
        for (Panel.Button b : p.buttons) {
            if (!"autorec".equals(b.id)) continue;
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            c.drawRoundRect(r, 18, 18, b.hover ? pBtnHover : (ar ? pBtnActive : pBtn));
            pBorder.setColor(b.hover ? 0xFF8FA3C8 : (ar ? 0xFF8FA3C8 : 0xFF2E3138));
            c.drawRoundRect(r, 18, 18, pBorder);
            String t = host.activity().getString(R.string.env_auto_recenter) + " · "
                    + host.activity().getString(ar ? R.string.common_on : R.string.common_off);
            HomeSceneUtil.drawCentered(c, t, r, valP);
        }
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {
        if ("bright_m".equals(id)) { host.settings().setBrightness(host.settings().brightness() - 0.1f); chrome.dirty = true; return; }
        if ("bright_p".equals(id)) { host.settings().setBrightness(host.settings().brightness() + 0.1f); chrome.dirty = true; return; }
        if ("autorec".equals(id)) { host.settings().setAutoRecenter(!host.settings().autoRecenter()); chrome.dirty = true; return; }
        if (id.startsWith("env")) {
            int m = Integer.parseInt(id.substring(3));
            host.settings().setEnvMode(m);
            chrome.dirty = true;
            String[] labels = {
                    host.activity().getString(R.string.env_pt),
                    host.activity().getString(R.string.env_space),
                    host.activity().getString(R.string.env_horizon)
            };
            host.toast(labels[m]);
        }
    }

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {}

    @Override public void onClosed() {}

    @Override public void onRestored() {}
}
