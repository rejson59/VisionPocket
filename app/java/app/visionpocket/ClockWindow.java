package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;

import java.util.Calendar;
import java.util.Locale;

/** Clock window: analog + digital clock, date, and a stopwatch. */
public class ClockWindow implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VpWindows.WmHost host;

    private int lastSecond = -1;
    private boolean swRunning = false;
    private long swStart = 0;
    private long swElapsed = 0;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFace = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHandH = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHandM = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHandS = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDigital = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDate = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSw = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ClockWindow(VpWindows wm, VpWindows.WmHost host) {
        this.wm = wm;
        this.host = host;
        window = new VpWindows.Window("clock", host.activity().getString(R.string.win_clock));
        chrome = new Panel(0, 0, 0, 0, 1.0f, 0.95f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        chrome.add("sw", 233, 750 + s, 533, 870 + s);
        chrome.add("swreset", 553, 750 + s, 833, 870 + s);
        chrome.click = b -> onClick(b.id);

        pBg.setColor(0xFF101113);
        pFace.setColor(0xFF1A1C21);
        pRing.setStyle(Paint.Style.STROKE);
        pRing.setStrokeWidth(6f);
        pRing.setColor(0xFF2E3138);
        pTick.setColor(0xFF565D6B);
        pHandH.setColor(0xFFF2F3F5);
        pHandH.setStrokeWidth(16f);
        pHandM.setColor(0xFFF2F3F5);
        pHandM.setStrokeWidth(11f);
        pHandS.setColor(0xFF8FA3C8);
        pHandS.setStrokeWidth(5f);
        pHub.setColor(0xFFF2F3F5);
        pDigital.setColor(0xFFE8ECF5);
        pDigital.setTextSize(84f);
        pDigital.setTextAlign(Paint.Align.CENTER);
        pDate.setColor(0xFF9BA0AB);
        pDate.setTextSize(40f);
        pDate.setTextAlign(Paint.Align.CENTER);
        pSw.setColor(0xFFE8ECF5);
        pSw.setTextSize(72f);
        pSw.setTextAlign(Paint.Align.CENTER);
        pBtn.setColor(0xFF1F2126);
        pBtnHover.setColor(0xFF2E323A);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF2E3138);
        pLabel.setColor(0xFFE8ECF5);
        pLabel.setTextSize(50f);
        pLabel.setTextAlign(Paint.Align.CENTER);

        wm.placeNew(window);
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF101113);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);

        Calendar cal = Calendar.getInstance();
        float cx = p.pw / 2f, cy = 290f, r = 200f;

        c.drawCircle(cx, cy, r, pFace);
        c.drawCircle(cx, cy, r, pRing);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6 - Math.PI / 2;
            float len = (i % 3 == 0) ? 34f : 20f;
            float x1 = cx + (float) Math.cos(a) * (r - 26f);
            float y1 = cy + (float) Math.sin(a) * (r - 26f);
            float x2 = cx + (float) Math.cos(a) * (r - 26f - len);
            float y2 = cy + (float) Math.sin(a) * (r - 26f - len);
            c.drawLine(x1, y1, x2, y2, pTick);
        }

        float sec = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f;
        float min = cal.get(Calendar.MINUTE) + sec / 60f;
        float hr = (cal.get(Calendar.HOUR) % 12) + min / 60f;
        drawHand(c, cx, cy, hr / 12f * (float) Math.PI * 2f - (float) Math.PI / 2f, r * 0.52f, pHandH);
        drawHand(c, cx, cy, min / 60f * (float) Math.PI * 2f - (float) Math.PI / 2f, r * 0.74f, pHandM);
        drawHand(c, cx, cy, sec / 60f * (float) Math.PI * 2f - (float) Math.PI / 2f, r * 0.82f, pHandS);
        c.drawCircle(cx, cy, 12f, pHub);

        String time = String.format(Locale.getDefault(), "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        c.drawText(time, cx, 640f, pDigital);
        String date = String.format(Locale.getDefault(), "%tA, %1$te %1$tB", cal);
        c.drawText(date, cx, 700f, pDate);

        long el = swElapsed + (swRunning ? SystemClock.uptimeMillis() - swStart : 0);
        c.drawText(fmtSw(el), cx, 730f, pSw);

        for (Panel.Button b : p.buttons) {
            RectF rr = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            c.drawRoundRect(rr, 20, 20, b.hover ? pBtnHover : pBtn);
            pBorder.setColor(b.hover ? 0xFF8FA3C8 : 0xFF2E3138);
            c.drawRoundRect(rr, 20, 20, pBorder);
            String t = "sw".equals(b.id)
                    ? host.activity().getString(swRunning ? R.string.sw_stop : R.string.sw_start)
                    : host.activity().getString(R.string.sw_reset);
            HomeSceneUtil.drawCentered(c, t, rr, pLabel);
        }
    }

    private void drawHand(Canvas c, float cx, float cy, float a, float len, Paint p) {
        c.drawLine(cx, cy, cx + (float) Math.cos(a) * len, cy + (float) Math.sin(a) * len, p);
    }

    private String fmtSw(long ms) {
        long t = ms / 100;
        long d = t % 10;
        long secTotal = t / 10;
        long hh = secTotal / 3600;
        long mm = (secTotal % 3600) / 60;
        long ss = secTotal % 60;
        if (hh > 0) return String.format(Locale.getDefault(), "%d:%02d:%02d.%d", hh, mm, ss, d);
        return String.format(Locale.getDefault(), "%02d:%02d.%d", mm, ss, d);
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {
        if ("sw".equals(id)) {
            if (swRunning) {
                swElapsed += SystemClock.uptimeMillis() - swStart;
                swRunning = false;
            } else {
                swStart = SystemClock.uptimeMillis();
                swRunning = true;
            }
        } else if ("swreset".equals(id)) {
            swRunning = false;
            swElapsed = 0;
        }
        chrome.dirty = true;
    }

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {
        if (swRunning) {
            chrome.dirty = true;
        } else {
            int sec = Calendar.getInstance().get(Calendar.SECOND);
            if (sec != lastSecond) {
                lastSecond = sec;
                chrome.dirty = true;
            }
        }
    }

    @Override public void onClosed() {}

    @Override public void onRestored() {}
}
