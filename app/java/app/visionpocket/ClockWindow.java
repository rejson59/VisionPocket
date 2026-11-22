package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;

import java.util.Calendar;
import java.util.Locale;

/** Clock window: analog + digital clock, date, stopwatch and a countdown timer. */
public class ClockWindow implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VrRenderer ren;

    private int lastSecond = -1;
    private boolean swRunning = false;
    private long swStart = 0;
    private long swElapsed = 0;
    private boolean timerRunning = false;
    private long timerEnd = 0;

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
    private final Paint pCount = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ClockWindow(VpWindows wm, VrRenderer ren) {
        this.wm = wm;
        this.ren = ren;
        window = new VpWindows.Window("clock", ren.activity().getString(R.string.win_clock));
        chrome = new Panel(0, 0, 0, 0, 1.0f, 1.5f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        chrome.add("sw", 160, 720 + s, 440, 840 + s);
        chrome.add("swreset", 460, 720 + s, 740, 840 + s);
        chrome.add("t5", 60, 870 + s, 230, 980 + s);
        chrome.add("t15", 240, 870 + s, 410, 980 + s);
        chrome.add("t25", 420, 870 + s, 590, 980 + s);
        chrome.add("tstop", 600, 870 + s, 740, 980 + s);
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
        pSw.setTextSize(66f);
        pSw.setTextAlign(Paint.Align.CENTER);
        pCount.setColor(0xFF8FA3C8);
        pCount.setTextSize(58f);
        pCount.setTextAlign(Paint.Align.CENTER);
        pBtn.setColor(0xFF1F2126);
        pBtnHover.setColor(0xFF2E323A);
        pBtnActive.setColor(0xFF343945);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF2E3138);
        pLabel.setColor(0xFFE8ECF5);
        pLabel.setTextSize(48f);
        pLabel.setTextAlign(Paint.Align.CENTER);

        wm.placeNew(window);
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF101113);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);

        Calendar cal = Calendar.getInstance();
        float cx = p.pw / 2f, cy = 250f, r = 185f;

        c.drawCircle(cx, cy, r, pFace);
        c.drawCircle(cx, cy, r, pRing);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6 - Math.PI / 2;
            float len = (i % 3 == 0) ? 32f : 18f;
            float x1 = cx + (float) Math.cos(a) * (r - 24f);
            float y1 = cy + (float) Math.sin(a) * (r - 24f);
            float x2 = cx + (float) Math.cos(a) * (r - 24f - len);
            float y2 = cy + (float) Math.sin(a) * (r - 24f - len);
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
        c.drawText(time, cx, 560f, pDigital);
        String date = String.format(Locale.getDefault(), "%tA, %1$te %1$tB", cal);
        c.drawText(date, cx, 620f, pDate);

        long el = swElapsed + (swRunning ? SystemClock.uptimeMillis() - swStart : 0);
        c.drawText(fmtSw(el), cx, 700f, pSw);

        if (timerRunning) {
            long left = Math.max(0, timerEnd - System.currentTimeMillis());
            c.drawText(mmss((int) left) + " ⏳", cx, 1055f, pCount);
        } else {
            c.drawText("⏱ 5 / 15 / 25", cx, 1055f, pCount);
        }

        for (Panel.Button b : p.buttons) {
            RectF rr = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            boolean active = ("sw".equals(b.id) && swRunning) || (timerRunning && b.id.startsWith("t")
                    && !"tstop".equals(b.id));
            c.drawRoundRect(rr, 20, 20, b.hover ? pBtnHover : (active ? pBtnActive : pBtn));
            pBorder.setColor(b.hover ? 0xFF8FA3C8 : 0xFF2E3138);
            c.drawRoundRect(rr, 20, 20, pBorder);
            String t;
            switch (b.id) {
                case "sw": t = ren.activity().getString(swRunning ? R.string.sw_stop : R.string.sw_start); break;
                case "swreset": t = ren.activity().getString(R.string.sw_reset); break;
                case "t5": t = "5"; break;
                case "t15": t = "15"; break;
                case "t25": t = "25"; break;
                default: t = "⏹";
            }
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

    private String mmss(int ms) {
        return String.format(Locale.US, "%d:%02d", ms / 60000, (ms / 1000) % 60);
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
        } else if ("t5".equals(id) || "t15".equals(id) || "t25".equals(id)) {
            int mins = "t5".equals(id) ? 5 : ("t15".equals(id) ? 15 : 25);
            timerEnd = System.currentTimeMillis() + mins * 60000L;
            timerRunning = true;
            ren.toast(mins + " min ⏳");
        } else if ("tstop".equals(id)) {
            timerRunning = false;
        }
        chrome.dirty = true;
    }

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {
        if (timerRunning && System.currentTimeMillis() >= timerEnd) {
            timerRunning = false;
            ren.activity().playChime();
            ren.toast(ren.activity().getString(R.string.timer_done));
        }
        if (swRunning || timerRunning) {
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
