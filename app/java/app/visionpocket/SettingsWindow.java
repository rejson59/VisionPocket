package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.Locale;

/** Settings window: IPD, distortion, resolution, passthrough, perf, haptics. */
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
        chrome = new Panel(0, 0, 0, 0, 1.5f, 1.42f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        for (int i = 0; i < 10; i++) {
            int y = 8 + i * 98;
            switch (i) {
                case 0: chrome.add("ipd_m", 1010, y + 4 + s, 1180, y + 90 + s); chrome.add("ipd_p", 1200, y + 4 + s, 1370, y + 90 + s); break;
                case 1: chrome.add("dist_m", 1010, y + 4 + s, 1180, y + 90 + s); chrome.add("dist_p", 1200, y + 4 + s, 1370, y + 90 + s); break;
                case 2:
                    chrome.add("res0", 700, y + 4 + s, 890, y + 90 + s);
                    chrome.add("res1", 910, y + 4 + s, 1100, y + 90 + s);
                    chrome.add("res2", 1120, y + 4 + s, 1310, y + 90 + s);
                    break;
                case 3: chrome.add("pt", 700, y + 4 + s, 1370, y + 90 + s); break;
                case 4: chrome.add("rot", 700, y + 4 + s, 1370, y + 90 + s); break;
                case 5: chrome.add("zoom_m", 1010, y + 4 + s, 1180, y + 90 + s); chrome.add("zoom_p", 1200, y + 4 + s, 1370, y + 90 + s); break;
                case 6: chrome.add("selfie", 700, y + 4 + s, 1370, y + 90 + s); break;
                case 7:
                    chrome.add("perf0", 700, y + 4 + s, 890, y + 90 + s);
                    chrome.add("perf1", 910, y + 4 + s, 1100, y + 90 + s);
                    chrome.add("perf2", 1120, y + 4 + s, 1310, y + 90 + s);
                    break;
                case 8: chrome.add("haptics", 700, y + 4 + s, 1370, y + 90 + s); break;
                case 9: chrome.add("bkx", 700, y + 4 + s, 1020, y + 90 + s); chrome.add("bki", 1050, y + 4 + s, 1370, y + 90 + s); break;
            }
        }
        chrome.add("shot", 40, 1020 + s, 340, 1116 + s);
        chrome.add("calib", 360, 1020 + s, 660, 1116 + s);
        chrome.add("diag", 680, 1020 + s, 980, 1116 + s);
        chrome.add("do_recenter", 1000, 1020 + s, 1370, 1116 + s);
        chrome.click = b -> onClick(b.id);

        pBg.setColor(0xFF101113);
        pRow.setColor(0x2A1A1C21);
        pBtn.setColor(0xFF1F2126);
        pBtnHover.setColor(0xFF2E323A);
        pBtnActive.setColor(0xFF343945);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF2E3138);
        pLabel.setColor(0xFFC9D1E4);
        pLabel.setTextSize(42f);
        pLabel.setTextAlign(Paint.Align.LEFT);
        pValue.setColor(0xFFE8ECF5);
        pValue.setTextSize(48f);
        pValue.setTextAlign(Paint.Align.CENTER);
        pValue.setFakeBoldText(true);
        pSym.setTextSize(50f);
        pSym.setTextAlign(Paint.Align.CENTER);

        wm.placeNew(window);
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF101113);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);
        SettingsStore st = host.settings();
        String[] labels = {
                host.activity().getString(R.string.settings_ipd),
                host.activity().getString(R.string.settings_distortion),
                host.activity().getString(R.string.settings_resolution),
                host.activity().getString(R.string.settings_passthrough),
                host.activity().getString(R.string.settings_rotate),
                host.activity().getString(R.string.settings_zoom),
                host.activity().getString(R.string.settings_selfie),
                host.activity().getString(R.string.settings_perf),
                host.activity().getString(R.string.settings_haptics),
                host.activity().getString(R.string.settings_backup)
        };
        String[] values = {
                st.ipd() + " mm",
                st.distortionPct() + " %",
                null, null, null,
                String.format(Locale.US, "%.1f", st.zoom()),
                null, null, null
        };
        for (int i = 0; i < 10; i++) {
            int y = 8 + i * 98;
            RectF row = new RectF(40, y, 1400, y + 90);
            c.drawRoundRect(row, 22, 22, pRow);
            c.drawText(labels[i], 70, y + 58, pLabel);
            if (values[i] != null) c.drawText(values[i], 920, y + 60, pValue);
        }

        for (Panel.Button b : p.buttons) {
            if ("shot".equals(b.id) || "calib".equals(b.id) || "diag".equals(b.id) || "do_recenter".equals(b.id)) continue;
            int rowTop = 8, rowBot = 98;
            for (int i = 0; i < 10; i++) {
                int y = 8 + i * 98;
                if (b.rect.top - s >= y && b.rect.bottom - s <= y + 94) { rowTop = y; rowBot = y + 90; break; }
            }
            if (!(b.rect.top - s >= rowTop && b.rect.bottom - s <= rowBot)) continue;
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            Paint fill = b.hover ? pBtnHover : (isActive(b.id, st) ? pBtnActive : pBtn);
            c.drawRoundRect(r, 18, 18, fill);
            pBorder.setColor(b.hover ? 0xFF8FA3C8 : 0xFF2E3138);
            c.drawRoundRect(r, 18, 18, pBorder);
            String text = buttonLabel(b.id, st);
            if ("−".equals(text) || "+".equals(text)) {
                c.drawText(text, r.centerX(), r.centerY() - (pSym.ascent() + pSym.descent()) / 2f, pSym);
            } else {
                HomeSceneUtil.drawCentered(c, text, r, pValue);
            }
        }

        for (Panel.Button b : p.buttons) {
            if (!"shot".equals(b.id) && !"calib".equals(b.id) && !"diag".equals(b.id) && !"do_recenter".equals(b.id)) continue;
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            c.drawRoundRect(r, 20, 20, b.hover ? pBtnHover : pBtn);
            pBorder.setColor(b.hover ? 0xFF8FA3C8 : 0xFF2E3138);
            c.drawRoundRect(r, 20, 20, pBorder);
            String t;
            if ("shot".equals(b.id)) t = host.activity().getString(R.string.settings_shot);
            else if ("calib".equals(b.id)) t = "🎯  " + host.activity().getString(R.string.calib_open);
            else if ("diag".equals(b.id)) t = "🩺  " + host.activity().getString(R.string.win_diag);
            else t = host.activity().getString(R.string.common_recenter);
            HomeSceneUtil.drawCentered(c, t, r, pValue);
        }
    }

    private boolean isActive(String id, SettingsStore st) {
        switch (id) {
            case "res0": return st.resIdx() == 0;
            case "res1": return st.resIdx() == 1;
            case "res2": return st.resIdx() == 2;
            case "selfie": return st.frontCam();
            case "perf0": return st.perfMode() == 0;
            case "perf1": return st.perfMode() == 1;
            case "perf2": return st.perfMode() == 2;
            case "haptics": return st.haptics();
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
            case "selfie": return host.activity().getString(st.frontCam() ? R.string.common_on : R.string.common_off);
            case "perf0": return "🔋";
            case "perf1": return "⚖";
            case "perf2": return "⚡";
            case "haptics": return host.activity().getString(st.haptics() ? R.string.common_on : R.string.common_off);
            case "bkx": return "⬆  " + host.activity().getString(R.string.bk_export);
            case "bki": return "⬇  " + host.activity().getString(R.string.bk_import);
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
            case "selfie":
                st.setFrontCam(!st.frontCam());
                host.applyFrontCam();
                break;
            case "perf0": case "perf1": case "perf2":
                st.setPerfMode(Integer.parseInt(id.substring(4)));
                break;
            case "haptics": st.setHaptics(!st.haptics()); break;
            case "do_recenter": host.recenterView(); break;
            case "shot": host.vrScreenshot(); break;
            case "calib": host.openCalibration(); break;
            case "diag": host.openDiagnostics(); break;
            case "bkx": host.exportSettings(); break;
            case "bki": host.importSettings(); break;
        }
        chrome.dirty = true;
    }

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {}

    @Override public void onClosed() {}

    @Override public void onRestored() {}
}
