package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Settings panel: IPD, lens distortion, resolution, passthrough options. */
public class SettingsScene implements Scene {

    private final SceneHost host;
    private final Panel main;
    private final List<Panel> panels = new ArrayList<>(1);

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pValue = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSym = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SettingsScene(SceneHost host) {
        this.host = host;
        main = new Panel(0f, 0.15f, -2.2f, 0f, 1.5f, 1.15f, 1000f);

        pBg.setColor(0xE6141622);
        pRow.setColor(0x2A1D2130);
        pBtn.setColor(0xFF232838);
        pBtnHover.setColor(0xFF3D4964);
        pBtnActive.setColor(0xFF31518C);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF39415A);
        pTitle.setColor(0xFFE8ECF5);
        pTitle.setTextSize(88f);
        pTitle.setTextAlign(Paint.Align.CENTER);
        pTitle.setFakeBoldText(true);
        pLabel.setColor(0xFFC9D1E4);
        pLabel.setTextSize(46f);
        pLabel.setTextAlign(Paint.Align.LEFT);
        pValue.setColor(0xFFE8ECF5);
        pValue.setTextSize(52f);
        pValue.setTextAlign(Paint.Align.CENTER);
        pValue.setFakeBoldText(true);
        pSym.setColor(0xFFE8ECF5);
        pSym.setTextSize(64f);
        pSym.setTextAlign(Paint.Align.CENTER);

        // rows
        for (int i = 0; i < 6; i++) {
            int y = 160 + i * 132;
            switch (i) {
                case 0: main.add("ipd_m", 1010, y + 9, 1190, y + 109); main.add("ipd_p", 1210, y + 9, 1390, y + 109); break;
                case 1: main.add("dist_m", 1010, y + 9, 1190, y + 109); main.add("dist_p", 1210, y + 9, 1390, y + 109); break;
                case 2:
                    main.add("res0", 700, y + 9, 900, y + 109);
                    main.add("res1", 920, y + 9, 1120, y + 109);
                    main.add("res2", 1140, y + 9, 1340, y + 109);
                    break;
                case 3: main.add("pt", 700, y + 9, 1390, y + 109); break;
                case 4: main.add("rot", 700, y + 9, 1390, y + 109); break;
                case 5: main.add("zoom_m", 1010, y + 9, 1190, y + 109); main.add("zoom_p", 1210, y + 9, 1390, y + 109); break;
            }
        }
        main.add("back", 90, 975, 560, 1085);
        main.add("do_recenter", 940, 975, 1410, 1085);

        main.hook = this::draw;
        main.click = this::onPanelClick;
        panels.add(main);
    }

    private void draw(Canvas c, Panel p) {
        pBg.setColor(0xE6141622);
        c.drawRoundRect(0, 0, p.pw, p.ph, 56, 56, pBg);
        c.drawText(host.activity().getString(R.string.settings_title), p.pw / 2f, 125f, pTitle);

        SettingsStore st = host.settings();
        for (int i = 0; i < 6; i++) {
            int y = 160 + i * 132;
            RectF row = new RectF(60, y, 1440, y + 118);
            c.drawRoundRect(row, 28, 28, pRow);
            String label;
            String value = null;
            switch (i) {
                case 0: label = host.activity().getString(R.string.settings_ipd); value = st.ipd() + " mm"; break;
                case 1: label = host.activity().getString(R.string.settings_distortion); value = st.distortionPct() + " %"; break;
                case 2: label = host.activity().getString(R.string.settings_resolution); break;
                case 3: label = host.activity().getString(R.string.settings_passthrough); break;
                case 4: label = host.activity().getString(R.string.settings_rotate); break;
                default: label = host.activity().getString(R.string.settings_zoom); value = String.format(Locale.US, "%.1f", st.zoom()); break;
            }
            c.drawText(label, 90, y + 74, pLabel);

            for (Panel.Button b : p.buttons) {
                if (b.rect.top < y || b.rect.bottom > y + 120) continue;
                Paint fill = b.hover ? pBtnHover : (isActive(b.id, st) ? pBtnActive : pBtn);
                c.drawRoundRect(b.rect, 24, 24, fill);
                pBorder.setColor(b.hover ? 0xFF5B8DEF : 0xFF39415A);
                c.drawRoundRect(b.rect, 24, 24, pBorder);
                String text = buttonLabel(b.id, st);
                if ("ipd_m".equals(b.id) || "dist_m".equals(b.id) || "zoom_m".equals(b.id) || "ipd_p".equals(b.id) || "dist_p".equals(b.id) || "zoom_p".equals(b.id)) {
                    drawCenteredSym(c, text, b.rect);
                } else {
                    HomeScene.drawCentered(c, text, b.rect, pValue);
                }
            }
            if (value != null) {
                c.drawText(value, 930, y + 74, pValue);
            }
        }

        for (Panel.Button b : p.buttons) {
            if (!"back".equals(b.id) && !"do_recenter".equals(b.id)) continue;
            Paint fill = b.hover ? pBtnHover : pBtn;
            c.drawRoundRect(b.rect, 30, 30, fill);
            pBorder.setColor(b.hover ? 0xFF5B8DEF : 0xFF39415A);
            c.drawRoundRect(b.rect, 30, 30, pBorder);
            String text = "back".equals(b.id) ? host.activity().getString(R.string.common_back)
                    : host.activity().getString(R.string.common_recenter);
            HomeScene.drawCentered(c, text, b.rect, pValue);
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

    private void drawCenteredSym(Canvas c, String text, RectF r) {
        c.drawText(text, r.centerX(), r.centerY() - (pSym.ascent() + pSym.descent()) / 2f, pSym);
    }

    private void onPanelClick(Panel.Button b) {
        SettingsStore st = host.settings();
        switch (b.id) {
            case "ipd_m": st.setIpd(st.ipd() - 1); break;
            case "ipd_p": st.setIpd(st.ipd() + 1); break;
            case "dist_m": st.setDistortionPct(st.distortionPct() - 10); break;
            case "dist_p": st.setDistortionPct(st.distortionPct() + 10); break;
            case "res0": case "res1": case "res2":
                st.setResIdx(Integer.parseInt(b.id.substring(3)));
                host.applyResolution();
                break;
            case "pt": st.setPassthrough(!st.passthrough()); break;
            case "rot": st.setRotDeg(st.rotDeg() + 90); break;
            case "zoom_m": st.setZoom(st.zoom() - 0.1f); break;
            case "zoom_p": st.setZoom(st.zoom() + 0.1f); break;
            case "do_recenter": host.recenterView(); break;
            case "back": host.showScene(SceneManager.SceneId.HOME); break;
        }
        main.dirty = true;
    }

    @Override public List<Panel> panels() { return panels; }
    @Override public void onShown() { main.dirty = true; }
    @Override public void onHidden() { main.clearHover(); }
    @Override public void update() {}
    @Override public void onRecenter(float d) { main.rotateBy(d); }
    @Override public void onClick(Panel.Button b) { onPanelClick(b); }
}
