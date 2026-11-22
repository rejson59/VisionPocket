package app.visionpocket;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;

import java.io.InputStream;
import java.util.ArrayList;

/** Photos window: multi-page thumbnail gallery of images picked from the phone. */
public class PhotosWindow implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VpWindows.WmHost host;

    private static final int PER_PAGE = 12;
    private static final int MAX_ITEMS = 60;

    private static class Item { Uri uri; Bitmap thumb; boolean failed; }
    private final ArrayList<Item> items = new ArrayList<>();
    private int page = 0;
    private int bigIdx = -1;
    private Bitmap bigBmp;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMsg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBmp = new Paint(Paint.FILTER_BITMAP_FLAG);

    public PhotosWindow(VpWindows wm, VpWindows.WmHost host) {
        this.wm = wm;
        this.host = host;
        window = new VpWindows.Window("photos", host.activity().getString(R.string.win_photos));
        chrome = new Panel(0, 0, 0, 0, 1.85f, 1.7f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        chrome.add("prev", 40, 34 + s, 130, 146 + s);
        chrome.add("next", 140, 34 + s, 230, 146 + s);
        chrome.add("back", 40, 34 + s, 230, 146 + s);
        chrome.add("pick", chrome.pw - 260, 34 + s, chrome.pw - 40, 146 + s);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 4; c++) {
                int x0 = 136 + c * 336;
                int y0 = 190 + r * 336;
                chrome.add("c" + (r * 4 + c), x0, y0 + s, x0 + 320, y0 + 320 + s);
            }
        }
        chrome.click = b -> onClick(b.id);

        pBg.setColor(0xFF101113);
        pBtn.setColor(0xFF1F2126);
        pBtnHover.setColor(0xFF2E323A);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF2E3138);
        pCell.setColor(0xFF1A1C21);
        pLabel.setColor(0xFFE8ECF5);
        pLabel.setTextSize(52f);
        pLabel.setTextAlign(Paint.Align.CENTER);
        pMsg.setColor(0xFF9BA0AB);
        pMsg.setTextSize(46f);
        pMsg.setTextAlign(Paint.Align.CENTER);

        wm.placeNew(window);
    }

    /** Adds another image to the gallery (called when the user picks a photo). */
    public void loadImage(Uri u) {
        if (u == null) return;
        if (items.size() >= MAX_ITEMS) {
            Item old = items.remove(0);
            if (old.thumb != null) old.thumb.recycle();
            if (page > 0) page--;
            if (bigIdx >= 0) bigIdx--;
        }
        Item it = new Item();
        it.uri = u;
        items.add(it);
        decodeThumb(it);
        chrome.dirty = true;
    }

    private void decodeThumb(final Item it) {
        new Thread(() -> {
            Bitmap b = decode(it.uri, 256);
            if (b != null) it.thumb = b; else it.failed = true;
            chrome.dirty = true;
        }, "vp-thumb").start();
    }

    private Bitmap decode(Uri u, int maxSide) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = host.activity().getContentResolver().openInputStream(u);
            BitmapFactory.decodeStream(in, null, o);
            if (in != null) in.close();
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= maxSide && o.outHeight / (sample * 2) >= maxSide) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            InputStream in2 = host.activity().getContentResolver().openInputStream(u);
            Bitmap b = BitmapFactory.decodeStream(in2, null, o2);
            if (in2 != null) in2.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private void openBig(int idx) {
        bigIdx = idx;
        chrome.dirty = true;
        new Thread(() -> {
            try {
                Bitmap b = decode(items.get(idx).uri, 1400);
                if (bigIdx == idx && b != null) {
                    if (bigBmp != null && !bigBmp.isRecycled()) bigBmp.recycle();
                    bigBmp = b;
                    chrome.dirty = true;
                } else if (b != null) {
                    b.recycle();
                }
            } catch (Exception ignored) {}
        }, "vp-big").start();
    }

    private void closeBig() {
        bigIdx = -1;
        if (bigBmp != null) { bigBmp.recycle(); bigBmp = null; }
        chrome.dirty = true;
    }

    private void drawFit(Canvas c, Bitmap b, RectF cell) {
        float ar = b.getWidth() / (float) b.getHeight();
        float cw = cell.width(), ch = cell.height();
        float dw = cw, dh = cw / ar;
        if (dh > ch) { dh = ch; dw = dh * ar; }
        float dx = cell.left + (cw - dw) / 2f;
        float dy = cell.top + (ch - dh) / 2f;
        c.drawBitmap(b, null, new RectF(dx, dy, dx + dw, dy + dh), pBmp);
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF101113);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);

        boolean big = bigIdx >= 0;
        for (Panel.Button b : p.buttons) {
            String id = b.id;
            boolean isCell = id.startsWith("c");
            if (isCell && big) continue;
            if (("prev".equals(id) || "next".equals(id)) && big) continue;
            if ("back".equals(id) && !big) continue;
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            if (isCell) {
                int cellIdx = page * PER_PAGE + Integer.parseInt(id.substring(1));
                if (cellIdx < items.size()) {
                    Item it = items.get(cellIdx);
                    c.drawRoundRect(r, 14, 14, pCell);
                    if (it.thumb != null && !it.thumb.isRecycled()) drawFit(c, it.thumb, r);
                    else if (it.failed) c.drawText("✕", r.centerX(), r.centerY(), pMsg);
                } else {
                    c.drawRoundRect(r, 14, 14, pCell);
                }
                c.drawRoundRect(r, 14, 14, pBorder);
                continue;
            }
            c.drawRoundRect(r, 22, 22, b.hover ? pBtnHover : pBtn);
            pBorder.setColor(b.hover ? 0xFF8FA3C8 : 0xFF2E3138);
            c.drawRoundRect(r, 22, 22, pBorder);
            String t = "pick".equals(id) ? "📂" : ("prev".equals(id) ? "‹" : ("next".equals(id) ? "›" : "‹ ‹"));
            HomeSceneUtil.drawCentered(c, t, r, pLabel);
        }

        if (big) {
            int pages = Math.max(1, (items.size() + PER_PAGE - 1) / PER_PAGE);
            if (bigBmp != null && !bigBmp.isRecycled()) {
                float availW = p.pw - 60f;
                float availH = p.ph - s - 130f;
                float ar = bigBmp.getWidth() / (float) bigBmp.getHeight();
                float dw = availW, dh = availW / ar;
                if (dh > availH) { dh = availH; dw = dh * ar; }
                float dx = (p.pw - dw) / 2f;
                float dy = 160f + (availH - dh) / 2f;
                c.drawBitmap(bigBmp, null, new RectF(dx, dy, dx + dw, dy + dh), pBmp);
            } else {
                c.drawText(host.activity().getString(R.string.photos_loading),
                        p.pw / 2f, (p.ph - s) / 2f, pMsg);
            }
            c.drawText((bigIdx + 1) + " / " + items.size(), p.pw / 2f, 110f, pMsg);
        } else {
            int pages = Math.max(1, (items.size() + PER_PAGE - 1) / PER_PAGE);
            c.drawText((page + 1) + " / " + pages, 290f, 106f, pMsg);
            if (items.isEmpty()) {
                c.drawText(host.activity().getString(R.string.photos_hint), p.pw / 2f, (p.ph - s) / 2f, pMsg);
            }
        }
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {
        if ("pick".equals(id)) {
            host.pickImage();
        } else if ("prev".equals(id)) {
            if (page > 0) { page--; chrome.dirty = true; }
        } else if ("next".equals(id)) {
            if ((page + 1) * PER_PAGE < items.size()) { page++; chrome.dirty = true; }
        } else if ("back".equals(id)) {
            closeBig();
        } else if (id.startsWith("c")) {
            int cell = Integer.parseInt(id.substring(1));
            int idx = page * PER_PAGE + cell;
            if (idx < items.size()) openBig(idx);
        }
    }

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {}

    @Override public void onClosed() {
        for (Item it : items) if (it.thumb != null) it.thumb.recycle();
        items.clear();
        if (bigBmp != null) { bigBmp.recycle(); bigBmp = null; }
        bigIdx = -1;
    }

    @Override public void onRestored() {}
}
