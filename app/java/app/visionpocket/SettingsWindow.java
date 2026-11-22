package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.Locale;

/** Settings window: IPD, distortion, resolution, passthrough options. */
public class SettingsWindow implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VpWindows.WmHost host;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pValue = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSym = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SettingsWindow(VpWindows wm, VpWindows.WmHost host) {
        this.wm = wm;
        this.host = host;
        window = new VpWindows.Window("settings", host.activity().getString(R.string.win_settings));
        chrome = new Panel(0, 0, 0, 0, 1.5f, 1.18f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        for (int i = 0; i < 6; i++) {
            int y = 14 + i * 118;
            switch (i) {
                case 0: chrome.add("ipd_m", 1010, y + 6 + s, 1180, y + 100 + s); chrome.add("ipd_p", 1200, y + 6 + s, 1370, y + 100 + s); break;
                case 1: chrome.add("dist_m", 1010, y + 6 + s, 1180, y + 100 + s); chrome.add("dist_p", 1200, y + 6 + s, 1370, y + 100 + s); break;
                case 2:
                    chrome.add("res0", 700, y + 6 + s, 890, y + 100 + s);
                    chrome.add("res1", 910, y + 6 + s, 1100, y + 100 + s);
                    chrome.add("res2", 1120, y + 6 + s, 1310, y + 100 + s);
                    break;
                case 3: chrome.add("pt", 700, y + 6 + s, 1370, y + 100 + s); break;
                case 4: chrome.add("rot", 700, y + 6 + s, 1370, y + 100 + s); break;
                case 5: chrome.add("zoom_m", 1010, y + 6 + s, 1180, y + 100 + s); chrome.add("zoom_p", 1200, y + 6 + s, 1370, y + 100 + s); break;
            }
        }
        chrome.add("do_recenter", 940, 730 + s, 1370, 830 + s);
        chrome.click = b -> onClick(b.id);

        pBg.setColor(0xFF0E101A);
        pRow.setColor(0x2A1D2130);
        pBtn.setColor(0xFF232838);
        pBtnHover.setColor(0xFF3D4964);
        pBtnActive.setColor(0xFF31518C);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF39415A);
        pLabel.setColor(0xFFC9D1E4);
        pLabel.setTextSize(44f);
        pLabel.setTextAlign(Paint.Align.LEFT);
        pValue.setColor(0xFFE8ECF5);
        pValue.setTextSize(50f);
        pValue.setTextAlign(Paint.Align.CENTER);
        pValue.setFakeBoldText(true);
        pSym.setTextSize(56f);
        pSym.setTextAlign(Paint.Align.CENTER);

        wm.placeNew(window);
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF0E101A);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);
        SettingsStore st = host.settings();
        String[] labels = {
                host.activity().getString(R.string.settings_ipd),
                host.activity().getString(R.string.settings_distortion),
                host.activity().getString(R.string.settings_resolution),
                host.activity().getString(R.string.settings_passthrough),
                host.activity().getString(R.string.settings_rotate),
                host.activity().getString(R.string.settings_zoom)
        };
        String[] values = {
                st.ipd() + " mm",
                st.distortionPct() + " %",
                null,
                null,
                null,
                String.format(Locale.US, "%.1f", st.zoom())
        };
        for (int i = 0; i < 6; i++) {
            int y = 14 + i * 118;
            RectF row = new RectF(40, y, 1400, y + 106);
            c.drawRoundRect(row, 24, 24, pRow);
            c.drawText(labels[i], 70, y + 68, pLabel);
            if (values[i] != null) c.drawText(values[i], 920, y + 70, pValue);
        }

        for (Panel.Button b : p.buttons) {
            if ("do_recenter".equals(b.id)) continue;
            int rowTop = 14, rowBot = 120;
            for (int i = 0; i < 6; i++) {
                int y = 14 + i * 118;
                if (b.rect.top - s >= y && b.rect.bottom - s <= y + 112) { rowTop = y; rowBot = y + 106; break; }
            }
            boolean inRow = b.rect.top - s >= rowTop && b.rect.bottom - s <= rowBot;
            if (!inRow) continue;
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            Paint fill = b.hover ? pBtnHover : (isActive(b.id, st) ? pBtnActive : pBtn);
            c.drawRoundRect(r, 20, 20, fill);
            pBorder.setColor(b.hover ? 0xFF5B8DEF : 0xFF39415A);
            c.drawRoundRect(r, 20, 20, pBorder);
            String text = buttonLabel(b.id, st);
            if (text.length() <= 2 && ("−".equals(text) || "+".equals(text))) {
                c.drawText(text, r.centerX(), r.centerY() - (pSym.ascent() + pSym.descent()) / 2f, pSym);
            } else {
                HomeSceneUtil.drawCentered(c, text, r, pValue);
            }
        }

        for (Panel.Button b : p.buttons) {
            if (!"do_recenter".equals(b.id)) continue;
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            c.drawRoundRect(r, 22, 22, b.hover ? pBtnHover : pBtn);
            pBorder.setColor(b.hover ? 0xFF5B8DEF : 0xFF39415A);
            c.drawRoundRect(r, 22, 22, pBorder);
            HomeSceneUtil.drawCentered(c, host.activity().getString(R.string.common_recenter), r, pValue);
        }
    }

    private boolean isActive(String id, SettingsStore st) {
        switch (id) {
            case "res0": return st.resIdx() == 0;
            case "res1": return st.resIdx() == 1;
            case "res2": return st.resIdx() == 2;
            default: return false;
        }
    }

    private String buttonLabel(String id, SettingsStore st) {
        switch (id) {
            case "ipd_m": case "dist_m": case "zoom_m": return "−";
            case "ipd_p": case "dist_p": case "zoom_p": return "+";
            case "res0": return host.activity().getString(R.string.res_low);
            case "res1": return host.activity().getString(R.string.res_medium);
            case "res2": return host.activity().getString(R.string.res_high);
            case "pt": return host.activity().getString(st.passthrough() ? R.string.common_on : R.string.common_off);
            case "rot": return st.rotDeg() + "°";
            default: return "";
        }
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {
        SettingsStore st = host.settings();
        switch (id) {
            case "ipd_m": st.setIpd(st.ipd() - 1); break;
            case "ipd_p": st.setIpd(st.ipd() + 1); break;
            case "dist_m": st.setDistortionPct(st.distortionPct() - 10); break;
            case "dist_p": st.setDistortionPct(st.distortionPct() + 10); break;
            case "res0": case "res1": case "res2":
                st.setResIdx(Integer.parseInt(id.substring(3)));
                host.applyResolution();
                break;
            case "pt": st.setPassthrough(!st.passthrough()); break;
            case "rot": st.setRotDeg(st.rotDeg() + 90); break;
            case "zoom_m": st.setZoom(st.zoom() - 0.1f); break;
            case "zoom_p": st.setZoom(st.zoom() + 0.1f); break;
            case "do_recenter": host.recenterView(); break;
        }
        chrome.dirty = true;
    }

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {}

    @Override public void onClosed() {}

    @Override public void onRestored() {}
}
