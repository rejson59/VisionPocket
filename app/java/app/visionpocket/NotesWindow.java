package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import org.json.JSONArray;

import java.util.ArrayList;

/** Notes window: multiple sticky notes, edited with the 3D keyboard, persisted locally. */
public class NotesWindow implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VpWindows.WmHost host;
    private final ArrayList<String> notes = new ArrayList<>();
    private int scroll = 0;

    private static final int VISIBLE = 6;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNote = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pIdx = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMsg = new Paint(Paint.ANTI_ALIAS_FLAG);

    public NotesWindow(VpWindows wm, VpWindows.WmHost host) {
        this.wm = wm;
        this.host = host;
        loadNotes();
        window = new VpWindows.Window("notes", host.activity().getString(R.string.win_notes));
        chrome = new Panel(0, 0, 0, 0, 1.4f, 1.05f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        chrome.add("up", 40, 34 + s, 110, 146 + s);
        chrome.add("dn", 120, 34 + s, 190, 146 + s);
        chrome.add("add", chrome.pw - 400, 34 + s, chrome.pw - 40, 146 + s);
        for (int i = 0; i < VISIBLE; i++) {
            int y0 = 190 + i * 132;
            chrome.add("r" + i, 40, y0 + s, chrome.pw - 160, y0 + 120 + s);
            chrome.add("d" + i, chrome.pw - 140, y0 + s, chrome.pw - 40, y0 + 120 + s);
        }
        chrome.click = b -> onClick(b.id);

        pBg.setColor(0xFF101113);
        pBtn.setColor(0xFF1F2126);
        pBtnHover.setColor(0xFF2E323A);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF2E3138);
        pRow.setColor(0xFF1A1C21);
        pLabel.setColor(0xFFE8ECF5);
        pLabel.setTextSize(50f);
        pLabel.setTextAlign(Paint.Align.CENTER);
        pNote.setColor(0xFFE0E5EF);
        pNote.setTextSize(42f);
        pNote.setTextAlign(Paint.Align.LEFT);
        pIdx.setColor(0xFF9BA0AB);
        pIdx.setTextSize(36f);
        pIdx.setTextAlign(Paint.Align.CENTER);
        pMsg.setColor(0xFF9BA0AB);
        pMsg.setTextSize(44f);
        pMsg.setTextAlign(Paint.Align.CENTER);

        wm.placeNew(window);
    }

    private void loadNotes() {
        notes.clear();
        try {
            JSONArray arr = new JSONArray(host.settings().notesJson());
            for (int i = 0; i < arr.length(); i++) notes.add(arr.optString(i));
        } catch (Exception ignored) {}
        if (notes.isEmpty()) {
            String legacy = host.settings().note();
            if (legacy != null && !legacy.isEmpty()) notes.add(legacy);
        }
    }

    private void saveNotes() {
        JSONArray arr = new JSONArray();
        for (String n : notes) arr.put(n);
        host.settings().setNotesJson(arr.toString());
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF101113);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);

        int pages = Math.max(1, (notes.size() + VISIBLE - 1) / VISIBLE);
        int cur = scroll / VISIBLE + 1;
        c.drawText(cur + " / " + pages, 290f, 106f, pMsg);

        for (Panel.Button b : p.buttons) {
            String id = b.id;
            int row = -1;
            boolean del = false;
            if (id.startsWith("r")) row = Integer.parseInt(id.substring(1));
            else if (id.startsWith("d")) { row = Integer.parseInt(id.substring(1)); del = true; }
            if (row >= 0) {
                int idx = scroll + row;
                if (idx >= notes.size()) continue;
                RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
                c.drawRoundRect(r, 16, 16, del ? (b.hover ? pBtnHover : pBtn) : pRow);
                pBorder.setColor(b.hover ? 0xFF8FA3C8 : 0xFF2E3138);
                c.drawRoundRect(r, 16, 16, pBorder);
                if (del) {
                    HomeSceneUtil.drawCentered(c, "✕", r, pLabel);
                } else {
                    String t = notes.get(idx).replace('\n', ' ');
                    if (t.length() > 40) t = t.substring(0, 40) + "…";
                    if (t.isEmpty()) t = host.activity().getString(R.string.notes_empty_row);
                    c.drawText(t, r.left + 96f, r.centerY() - (pNote.ascent() + pNote.descent()) / 2f, pNote);
                    c.drawText(String.valueOf(idx + 1), r.left + 48f,
                            r.centerY() - (pIdx.ascent() + pIdx.descent()) / 2f, pIdx);
                }
                continue;
            }
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            c.drawRoundRect(r, 20, 20, b.hover ? pBtnHover : pBtn);
            pBorder.setColor(b.hover ? 0xFF8FA3C8 : 0xFF2E3138);
            c.drawRoundRect(r, 20, 20, pBorder);
            String label = "add".equals(id) ? host.activity().getString(R.string.notes_add)
                    : ("up".equals(id) ? "▲" : "▼");
            HomeSceneUtil.drawCentered(c, label, r, pLabel);
        }

        if (notes.isEmpty()) {
            c.drawText(host.activity().getString(R.string.notes_empty),
                    p.pw / 2f, 190f + 3 * 132f, pMsg);
        }
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {
        if ("add".equals(id)) {
            notes.add("");
            scroll = Math.max(0, notes.size() - VISIBLE);
            saveNotes();
            editNote(notes.size() - 1);
        } else if ("up".equals(id)) {
            if (scroll > 0) { scroll -= VISIBLE; chrome.dirty = true; }
        } else if ("dn".equals(id)) {
            if (scroll + VISIBLE < notes.size()) { scroll += VISIBLE; chrome.dirty = true; }
        } else if (id.startsWith("r")) {
            int idx = scroll + Integer.parseInt(id.substring(1));
            if (idx < notes.size()) editNote(idx);
        } else if (id.startsWith("d")) {
            int idx = scroll + Integer.parseInt(id.substring(1));
            if (idx < notes.size()) {
                notes.remove(idx);
                if (scroll > 0 && scroll >= notes.size()) scroll = Math.max(0, notes.size() - VISIBLE);
                saveNotes();
                chrome.dirty = true;
            }
        }
    }

    private void editNote(final int idx) {
        new KeyboardWindow(wm, host, host.activity().getString(R.string.kb_title_note),
                notes.get(idx), new KeyboardWindow.Callback() {
            @Override public void onOk(String t) {
                notes.set(idx, t);
                saveNotes();
                chrome.dirty = true;
            }
            @Override public void onCancel() {}
        });
    }

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {}

    @Override public void onClosed() { saveNotes(); }

    @Override public void onRestored() {}
}
