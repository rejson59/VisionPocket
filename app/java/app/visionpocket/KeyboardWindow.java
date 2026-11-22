package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** 3D QWERTY input dialog window (used for notes, URLs, any text field). */
public class KeyboardWindow implements WindowContent {

    public interface Callback { void onOk(String text); void onCancel(); }

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VpWindows.WmHost host;
    private final Callback cb;
    private StringBuilder text;
    private final boolean maskTitle;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pField = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pKey = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final String[] ROWS = {
            "1234567890",
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm",
    };

    public KeyboardWindow(VpWindows wm, VpWindows.WmHost host, String title,
                          String initial, boolean maskTitle, Callback cb) {
        this.wm = wm;
        this.host = host;
        this.cb = cb;
        this.text = new StringBuilder(initial == null ? "" : initial);
        this.maskTitle = maskTitle;
        window = new VpWindows.Window("kb", title);
        chrome = new Panel(0, 0, 0, 0, 1.7f, 1.34f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;
        chrome.click = b -> onClick(b.id);

        int s = VpWindows.STRIP_PX;
        // input field display
        // key rows: 4 letter rows + bottom action row
        float keyW = 116f, keyH = 96f, gapX = 10f, gapY = 10f;
        for (int r = 0; r < ROWS.length; r++) {
            String row = ROWS[r];
            int n = row.length();
            float rowW = n * keyW + (n - 1) * gapX;
            float x0 = (chrome.pw - rowW) / 2f;
            float y = 130 + r * (keyH + gapY) + s;
            for (int i = 0; i < n; i++) {
                chrome.add("k_" + row.charAt(i), x0 + i * (keyW + gapX), y,
                        x0 + i * (keyW + gapX) + keyW, y + keyH);
            }
        }
        // bottom row: url extras, space, backspace, cancel, ok
        int y5 = 130 + 4 * (int) (keyH + gapY) + s;
        float x = 40;
        chrome.add("k_:", x, y5, x + 90, y5 + keyH); x += 100;
        chrome.add("k_/", x, y5, x + 90, y5 + keyH); x += 100;
        chrome.add("k_.", x, y5, x + 90, y5 + keyH); x += 100;
        chrome.add("k_-", x, y5, x + 90, y5 + keyH); x += 110;
        chrome.add("space", x, y5, x + 330, y5 + keyH); x += 340;
        chrome.add("bs", x, y5, x + 150, y5 + keyH); x += 160;
        chrome.add("cancel", x, y5, x + 170, y5 + keyH); x += 180;
        chrome.add("ok", x, y5, x + 190, y5 + keyH);

        pBg.setColor(0xFF0E101A);
        pField.setColor(0xFF171B2A);
        pBtn.setColor(0xFF232838);
        pBtnHover.setColor(0xFF3D4964);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(2.5f);
        pBorder.setColor(0xFF39415A);
        pText.setColor(0xFFE8ECF5);
        pText.setTextSize(54f);
        pText.setTextAlign(Paint.Align.LEFT);
        pKey.setColor(0xFFDCE3F2);
        pKey.setTextSize(58f);
        pKey.setTextAlign(Paint.Align.CENTER);

        wm.placeNew(window);
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF0E101A);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);
        // input field
        c.drawRoundRect(40, 14, p.pw - 40, 108, 20, 20, pField);
        c.drawRoundRect(40, 14, p.pw - 40, 108, 20, 20, pBorder);
        String shown = text.toString();
        if (maskTitle && shown.length() > 42) shown = "…" + shown.substring(shown.length() - 42);
        pText.setTextAlign(Paint.Align.LEFT);
        c.drawText(shown.isEmpty() ? host.activity().getString(R.string.url_hint) : shown,
                64f, 78f, shown.isEmpty() ? pKey : pText);
        // cursor blink
        if (SystemClock365() % 1000 < 500) {
            float tw = pText.measureText(shown);
            c.drawRoundRect(64 + tw + 6, 34, 64 + tw + 12, 92, 3, 3, pKey);
        }

        for (Panel.Button b : p.buttons) {
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            c.drawRoundRect(r, 16, 16, b.hover ? pBtnHover : pBtn);
            pBorder.setColor(b.hover ? 0xFF5B8DEF : 0xFF39415A);
            c.drawRoundRect(r, 16, 16, pBorder);
            String label = keyLabel(b.id);
            c.drawText(label, r.centerX(), r.centerY() - (pKey.ascent() + pKey.descent()) / 2f, pKey);
        }
    }

    private long SystemClock365() {
        return android.os.SystemClock.uptimeMillis();
    }

    private String keyLabel(String id) {
        if (id.startsWith("k_")) return id.substring(2);
        switch (id) {
            case "space": return "␣";
            case "bs": return "⌫";
            case "ok": return host.activity().getString(R.string.kb_ok);
            case "cancel": return host.activity().getString(R.string.kb_cancel);
            default: return "";
        }
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {
        if (id.startsWith("k_") && id.length() > 2) {
            text.append(id.substring(2));
        } else if ("space".equals(id)) {
            text.append(' ');
        } else if ("bs".equals(id)) {
            if (text.length() > 0) text.setLength(text.length() - 1);
        } else if ("ok".equals(id)) {
            String t = text.toString();
            wm.close(window);
            cb.onOk(t);
            return;
        } else if ("cancel".equals(id)) {
            wm.close(window);
            cb.onCancel();
            return;
        }
        chrome.dirty = true;
    }

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {}

    @Override public void onClosed() {}

    @Override public void onRestored() {}
}
