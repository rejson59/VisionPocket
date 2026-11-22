package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Notes window: quick note with the 3D keyboard, persisted locally. */
public class NotesWindow implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VpWindows.WmHost host;
    private String note;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNote = new Paint(Paint.ANTI_ALIAS_FLAG);

    public NotesWindow(VpWindows wm, VpWindows.WmHost host) {
        this.wm = wm;
        this.host = host;
        note = host.settings().note();
        window = new VpWindows.Window("notes", host.activity().getString(R.string.win_notes));
        chrome = new Panel(0, 0, 0, 0, 1.4f, 1.05f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        chrome.add("edit", 40, 14 + s, chrome.pw - 40, 430 + s);
        chrome.add("save", 100, 470 + s, 500, 590 + s);
        chrome.add("clear", 540, 470 + s, 940, 590 + s);
        chrome.click = b -> onClick(b.id);

        pBg.setColor(0xFF101113);
        pBtn.setColor(0xFF1F2126);
        pBtnHover.setColor(0xFF2E323A);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF2E3138);
        pLabel.setColor(0xFFE8ECF5);
        pLabel.setTextSize(54f);
        pLabel.setTextAlign(Paint.Align.CENTER);
        pNote.setColor(0xFFDCE3F2);
        pNote.setTextSize(44f);
        pNote.setTextAlign(Paint.Align.LEFT);

        wm.placeNew(window);
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF101113);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);

        for (Panel.Button b : p.buttons) {
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            c.drawRoundRect(r, 20, 20, b.hover ? pBtnHover : pBtn);
            pBorder.setColor(b.hover ? 0xFF8FA3C8 : 0xFF2E3138);
            c.drawRoundRect(r, 20, 20, pBorder);
            String label;
            if ("edit".equals(b.id)) label = null;
            else if ("save".equals(b.id)) label = host.activity().getString(R.string.notes_save);
            else label = host.activity().getString(R.string.notes_clear);
            if (label != null) HomeSceneUtil.drawCentered(c, label, r, pLabel);
        }

        // note text area (button acts as tap target)
        Panel.Button edit = null;
        for (Panel.Button b : p.buttons) if ("edit".equals(b.id)) edit = b;
        if (edit != null) {
            RectF r = new RectF(edit.rect.left, edit.rect.top - s, edit.rect.right, edit.rect.bottom - s);
            c.drawRoundRect(r, 20, 20, pBtn);
            c.drawRoundRect(r, 20, 20, pBorder);
            String t = note.isEmpty() ? host.activity().getString(R.string.notes_hint) : note;
            if (t.length() > 120) t = t.substring(0, 120) + "…";
            // simple wrap
            float y = r.top + 60f;
            int start = 0;
            Paint m = new Paint(pNote);
            while (start < t.length() && y < r.bottom - 20f) {
                int end = Math.min(t.length(), start + 42);
                int brk = t.lastIndexOf(' ', end);
                if (brk <= start) brk = end;
                c.drawText(t.substring(start, brk), r.left + 30f, y, pNote);
                start = brk == t.length() ? t.length() : brk + 1;
                y += 58f;
            }
        }
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {
        if ("edit".equals(id)) {
            new KeyboardWindow(wm, host, host.activity().getString(R.string.kb_title_note),
                    note, new KeyboardWindow.Callback() {
                @Override public void onOk(String t) {
                    note = t;
                    chrome.dirty = true;
                }
                @Override public void onCancel() {}
            });
        } else if ("save".equals(id)) {
            host.settings().setNote(note);
            host.toast(host.activity().getString(R.string.notes_saved));
            chrome.dirty = true;
        } else if ("clear".equals(id)) {
            note = "";
            host.settings().setNote("");
            chrome.dirty = true;
        }
    }

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {}

    @Override public void onClosed() { host.settings().setNote(note); }

    @Override public void onRestored() {}
}
