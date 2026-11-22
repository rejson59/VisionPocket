package app.visionpocket;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;

import java.io.InputStream;

/** Photos window: pick an image from the phone and place it in space. */
public class PhotosWindow implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VpWindows.WmHost host;
    private Bitmap image;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMsg = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PhotosWindow(VpWindows wm, VpWindows.WmHost host) {
        this.wm = wm;
        this.host = host;
        window = new VpWindows.Window("photos", host.activity().getString(R.string.win_photos));
        chrome = new Panel(0, 0, 0, 0, 1.5f, 1.25f, 700f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        chrome.add("pick", chrome.pw - 260, 30 + s, chrome.pw - 40, 150 + s);
        chrome.click = b -> onClick(b.id);

        pBg.setColor(0xFF101113);
        pBtn.setColor(0xFF1F2126);
        pBtnHover.setColor(0xFF2E323A);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF2E3138);
        pLabel.setColor(0xFFE8ECF5);
        pLabel.setTextSize(52f);
        pLabel.setTextAlign(Paint.Align.CENTER);
        pMsg.setColor(0xFF9BA0AB);
        pMsg.setTextSize(46f);
        pMsg.setTextAlign(Paint.Align.CENTER);

        wm.placeNew(window);
    }

    public void loadImage(Uri u) {
        new Thread(() -> {
            try {
                InputStream in = host.activity().getContentResolver().openInputStream(u);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(in, null, o);
                if (in != null) in.close();
                int sample = 1;
                while (o.outWidth / sample > 1400 || o.outHeight / sample > 1400) sample *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                InputStream in2 = host.activity().getContentResolver().openInputStream(u);
                Bitmap b = BitmapFactory.decodeStream(in2, null, o2);
                if (in2 != null) in2.close();
                if (b != null) {
                    if (image != null && !image.isRecycled()) image.recycle();
                    image = b;
                    chrome.dirty = true;
                }
            } catch (Exception e) {
                chrome.dirty = true;
            }
        }, "vp-img").start();
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF101113);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);

        for (Panel.Button b : p.buttons) {
            Paint fill = b.hover ? pBtnHover : pBtn;
            c.drawRoundRect(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s, 22, 22, fill);
            pBorder.setColor(b.hover ? 0xFF8FA3C8 : 0xFF2E3138);
            c.drawRoundRect(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s, 22, 22, pBorder);
            HomeSceneUtil.drawCentered(c, "📂",
                    new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s), pLabel);
        }

        if (image != null && !image.isRecycled()) {
            float availW = p.pw - 60f;
            float availH = p.ph - s - 120f;
            float ar = image.getWidth() / (float) image.getHeight();
            float dw = availW, dh = availW / ar;
            if (dh > availH) { dh = availH; dw = dh * ar; }
            float dx = (p.pw - dw) / 2f;
            float dy = 110f + (availH - dh) / 2f;
            c.drawBitmap(image, null, new RectF(dx, dy, dx + dw, dy + dh), new Paint(Paint.FILTER_BITMAP_FLAG));
        } else {
            c.drawText(host.activity().getString(R.string.photos_hint), p.pw / 2f, (p.ph - s) / 2f, pMsg);
        }
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {
        if ("pick".equals(id)) host.pickImage();
    }

    @Override public void onBodyClick(Panel part) { host.pickImage(); }

    @Override public void update() {}

    @Override public void onClosed() {
        if (image != null) { image.recycle(); image = null; }
    }

    @Override public void onRestored() {}
}
