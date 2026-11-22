package app.visionpocket;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;

import java.io.InputStream;

/** Panorama 360 window: place an equirectangular photo or video around the user. */
public class PanoramaWindow implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VrRenderer ren;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int lastMode = -1;

    public PanoramaWindow(VpWindows wm, VrRenderer ren) {
        this.wm = wm;
        this.ren = ren;
        window = new VpWindows.Window("pano", ren.activity().getString(R.string.win_pano));
        chrome = new Panel(0, 0, 0, 0, 1.2f, 0.92f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        chrome.add("img", 60, 16 + s, 900, 196 + s);
        chrome.add("vid", 60, 216 + s, 900, 396 + s);
        chrome.add("off", 60, 416 + s, 900, 566 + s);
        chrome.add("proj0", 60, 586 + s, 300, 686 + s);
        chrome.add("proj1", 330, 586 + s, 570, 686 + s);
        chrome.add("proj2", 600, 586 + s, 840, 686 + s);
        chrome.click = b -> onClick(b.id);

        pBg.setColor(0xFF101113);
        pBtn.setColor(0xFF1F2126);
        pBtnHover.setColor(0xFF2E323A);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF2E3138);
        pLabel.setColor(0xFFE8ECF5);
        pLabel.setTextSize(54f);
        pLabel.setTextAlign(Paint.Align.LEFT);
        pText.setColor(0xFF9BA0AB);
        pText.setTextSize(40f);
        pText.setTextAlign(Paint.Align.LEFT);
        pLabel2.setColor(0xFFE8ECF5);
        pLabel2.setTextSize(46f);
        pLabel2.setTextAlign(Paint.Align.CENTER);

        wm.placeNew(window);
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF101113);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);
        c.drawText(ren.activity().getString(R.string.pano_hint), 60f, (p.ph - s) / 2f + 30f, pText);

        String[] icons = {"🖼", "🎬", "⏹"};
        String[] labels = {
                ren.activity().getString(R.string.pano_img),
                ren.activity().getString(R.string.pano_vid),
                ren.activity().getString(R.string.pano_off)
        };
        int mode = ren.panoMode();
        String[] ids = {"img", "vid", "off"};
        for (int i = 0; i < 3; i++) {
            Panel.Button b = null;
            for (Panel.Button bb : p.buttons) if (ids[i].equals(bb.id)) b = bb;
            if (b == null) continue;
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            boolean active = (i == 0 && mode == 1) || (i == 1 && mode == 2);
            c.drawRoundRect(r, 26, 26, b.hover ? pBtnHover : pBtn);
            pBorder.setColor(b.hover ? 0xFF8FA3C8 : (active ? 0xFF8FA3C8 : 0xFF2E3138));
            c.drawRoundRect(r, 26, 26, pBorder);
            Paint sym = new Paint(pLabel);
            sym.setTextSize(64f);
            sym.setTextAlign(Paint.Align.CENTER);
            c.drawText(icons[i], r.left + 90f, r.centerY() - (sym.ascent() + sym.descent()) / 2f, sym);
            c.drawText(labels[i], r.left + 170f, r.centerY() - (pLabel.ascent() + pLabel.descent()) / 2f, pLabel);
        }
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {
        if ("img".equals(id)) {
            ren.requestPanoImage();
        } else if ("vid".equals(id)) {
            ren.requestPanoVideo();
        } else if ("off".equals(id)) {
            ren.panoClear();
            chrome.dirty = true;
        } else if (id.startsWith("proj")) {
            ren.setPanoProj(Integer.parseInt(id.substring(4)));
        }
        chrome.dirty = true;
    }

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {
        // redraw only when the panorama mode changed (canvas upload is expensive)
        int m = ren.panoMode();
        if (m != lastMode) { lastMode = m; chrome.dirty = true; }
    }

    @Override public void onClosed() { ren.panoClear(); }

    @Override public void onRestored() {}

    /** Called by the renderer after a 360° photo was picked and decoded. */
    public void photoLoaded(boolean ok) {
        chrome.dirty = true;
    }

    /** Decodes the picked image and uploads it as the panorama sphere. */
    public void loadImage(Uri u) {
        new Thread(() -> {
            Bitmap b = null;
            try {
                InputStream in = ren.activity().getContentResolver().openInputStream(u);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(in, null, o);
                if (in != null) in.close();
                int sample = 1;
                while (o.outWidth / sample > 2048 || o.outHeight / sample > 1024) sample *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                InputStream in2 = ren.activity().getContentResolver().openInputStream(u);
                b = BitmapFactory.decodeStream(in2, null, o2);
                if (in2 != null) in2.close();
            } catch (Exception ignored) {}
            ren.setPanoImage(b);
        }, "vp-pano").start();
    }
}
