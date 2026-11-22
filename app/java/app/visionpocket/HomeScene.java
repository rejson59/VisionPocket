package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/** MR home: launcher panel with app buttons. */
public class HomeScene implements Scene {

    private final SceneHost host;
    private final Panel main;
    private final List<Panel> panels = new ArrayList<>(1);

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HomeScene(SceneHost host) {
        this.host = host;
        main = new Panel(0f, 0.15f, -2.2f, 0f, 1.5f, 1.0f, 1000f);

        pBg.setColor(0xE6141622);
        pBtn.setColor(0xFF232838);
        pBtnHover.setColor(0xFF3D4964);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF39415A);
        pTitle.setColor(0xFFE8ECF5);
        pTitle.setTextSize(106f);
        pTitle.setTextAlign(Paint.Align.CENTER);
        pTitle.setFakeBoldText(true);
        pSub.setColor(0xFF8FA7E8);
        pSub.setTextSize(42f);
        pSub.setTextAlign(Paint.Align.CENTER);
        pLabel.setColor(0xFFE8ECF5);
        pLabel.setTextSize(58f);
        pLabel.setTextAlign(Paint.Align.CENTER);
        pLabel.setFakeBoldText(true);
        pHint.setColor(0xFF7A8298);
        pHint.setTextSize(34f);
        pHint.setTextAlign(Paint.Align.CENTER);

        main.add("cinema", 90, 270, 730, 570);
        main.add("settings", 770, 270, 1410, 570);
        main.add("passthrough", 90, 600, 730, 900);
        main.add("recenter", 770, 600, 1410, 900);

        main.hook = this::draw;
        main.click = this::onPanelClick;
        panels.add(main);
    }

    private void draw(Canvas c, Panel p) {
        pBg.setColor(0xE6141622);
        c.drawRoundRect(0, 0, p.pw, p.ph, 56, 56, pBg);
        c.drawText(host.activity().getString(R.string.home_title), p.pw / 2f, 158f, pTitle);
        c.drawText(host.activity().getString(R.string.home_subtitle), p.pw / 2f, 226f, pSub);

        SettingsStore st = host.settings();
        for (Panel.Button b : p.buttons) {
            Paint fill = b.hover ? pBtnHover : pBtn;
            c.drawRoundRect(b.rect, 40, 40, fill);
            pBorder.setColor(b.hover ? 0xFF5B8DEF : 0xFF39415A);
            c.drawRoundRect(b.rect, 40, 40, pBorder);
            String label;
            switch (b.id) {
                case "cinema": label = host.activity().getString(R.string.home_btn_cinema); break;
                case "settings": label = host.activity().getString(R.string.home_btn_settings); break;
                case "recenter": label = host.activity().getString(R.string.home_btn_recenter); break;
                default:
                    label = host.activity().getString(R.string.home_btn_passthrough,
                            host.activity().getString(st.passthrough() ? R.string.common_on : R.string.common_off));
            }
            drawCentered(c, label, b.rect, pLabel);
        }
        c.drawText(host.activity().getString(R.string.home_hint), p.pw / 2f, 972f, pHint);
    }

    static void drawCentered(Canvas c, String text, RectF r, Paint paint) {
        c.drawText(text, r.centerX(), r.centerY() - (paint.ascent() + paint.descent()) / 2f, paint);
    }

    private void onPanelClick(Panel.Button b) {
        switch (b.id) {
            case "cinema": host.showScene(SceneManager.SceneId.CINEMA); break;
            case "settings": host.showScene(SceneManager.SceneId.SETTINGS); break;
            case "recenter": host.recenterView(); break;
            case "passthrough": {
                SettingsStore st = host.settings();
                st.setPassthrough(!st.passthrough());
                main.dirty = true;
                break;
            }
        }
    }

    @Override public List<Panel> panels() { return panels; }
    @Override public void onShown() { main.dirty = true; }
    @Override public void onHidden() { main.clearHover(); }
    @Override public void update() {}
    @Override public void onRecenter(float d) { main.rotateBy(d); }
    @Override public void onClick(Panel.Button b) { onPanelClick(b); }
}
