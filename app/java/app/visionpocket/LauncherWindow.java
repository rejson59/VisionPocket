package app.visionpocket;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Apps window: launch installed apps on the phone (great with screen mirroring). */
public class LauncherWindow implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VpWindows.WmHost host;

    private final List<ApplicationInfo> apps = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private int page = 0;
    private static final int COLS = 2, ROWS = 4, PER = COLS * ROWS;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMsg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pArrow = new Paint(Paint.ANTI_ALIAS_FLAG);

    public LauncherWindow(VpWindows wm, VpWindows.WmHost host) {
        this.wm = wm;
        this.host = host;
        window = new VpWindows.Window("apps", host.activity().getString(R.string.win_apps));
        chrome = new Panel(0, 0, 0, 0, 1.6f, 1.25f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;
        chrome.click = b -> onClick(b.id);

        int s = VpWindows.STRIP_PX;
        for (int i = 0; i < PER; i++) {
            int col = i % COLS, row = i / COLS;
            float x = 50 + col * 640;
            float y = 30 + row * 210 + s;
            chrome.add("app" + i, x, y, x + 600, y + 190);
        }
        chrome.add("prev", 340, 890 + s, 560, 980 + s);
        chrome.add("next", 700, 890 + s, 920, 980 + s);

        pBg.setColor(0xFF0E101A);
        pBtn.setColor(0xFF232838);
        pBtnHover.setColor(0xFF3D4964);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF39415A);
        pLabel.setColor(0xFFE8ECF5);
        pLabel.setTextSize(48f);
        pLabel.setTextAlign(Paint.Align.CENTER);
        pMsg.setColor(0xFF9AA3B8);
        pMsg.setTextSize(42f);
        pMsg.setTextAlign(Paint.Align.CENTER);
        pArrow.setTextSize(64f);
        pArrow.setTextAlign(Paint.Align.CENTER);
        pArrow.setColor(0xFFE8ECF5);

        loadApps();
        wm.placeNew(window);
    }

    private void loadApps() {
        new Thread(() -> {
            try {
                PackageManager pm = host.activity().getPackageManager();
                Intent main = new Intent(Intent.ACTION_MAIN);
                main.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ApplicationInfo> all = pm.getInstalledApplications(0);
                List<ApplicationInfo> found = new ArrayList<>();
                for (ApplicationInfo ai : all) {
                    if (pm.getLaunchIntentForPackage(ai.packageName) != null) found.add(ai);
                }
                Collections.sort(found, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                        a.loadLabel(pm).toString(), b.loadLabel(pm).toString()));
                synchronized (apps) {
                    apps.clear();
                    labels.clear();
                    for (ApplicationInfo ai : found) {
                        apps.add(ai);
                        labels.add(ai.loadLabel(pm).toString());
                    }
                }
                chrome.dirty = true;
            } catch (Exception ignored) {}
        }, "vp-apps").start();
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF0E101A);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);

        int pages = Math.max(1, (apps.size() + PER - 1) / PER);
        if (page >= pages) page = pages - 1;

        synchronized (apps) {
            if (apps.isEmpty()) {
                c.drawText(host.activity().getString(R.string.apps_none), p.pw / 2f, 400f, pMsg);
            } else {
                for (int i = 0; i < PER; i++) {
                    int idx = page * PER + i;
                    if (idx >= apps.size()) break;
                    Panel.Button b = null;
                    for (Panel.Button bb : p.buttons) if (("app" + i).equals(bb.id)) b = bb;
                    if (b == null) continue;
                    RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
                    c.drawRoundRect(r, 26, 26, b.hover ? pBtnHover : pBtn);
                    pBorder.setColor(b.hover ? 0xFF5B8DEF : 0xFF39415A);
                    c.drawRoundRect(r, 26, 26, pBorder);
                    String name = labels.get(idx);
                    if (name.length() > 22) name = name.substring(0, 21) + "…";
                    HomeSceneUtil.drawCentered(c, name, r, pLabel);
                }
            }
        }

        if (apps.size() > PER) {
            for (Panel.Button b : p.buttons) {
                if (!"prev".equals(b.id) && !"next".equals(b.id)) continue;
                RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
                c.drawRoundRect(r, 20, 20, b.hover ? pBtnHover : pBtn);
                c.drawRoundRect(r, 20, 20, pBorder);
                c.drawText("prev".equals(b.id) ? "‹" : "›",
                        r.centerX(), r.centerY() - (pArrow.ascent() + pArrow.descent()) / 2f, pArrow);
            }
            c.drawText((page + 1) + " / " + pages, p.pw / 2f, 872f, pMsg);
        }

        // hint line
        c.drawText(host.activity().getString(R.string.apps_hint), p.pw / 2f, p.ph - s - 16f, pMsg);
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {
        if ("prev".equals(id)) { if (page > 0) page--; chrome.dirty = true; return; }
        if ("next".equals(id)) { page++; chrome.dirty = true; return; }
        if (id.startsWith("app")) {
            int i = Integer.parseInt(id.substring(3));
            int idx = page * PER + i;
            ApplicationInfo ai = null;
            synchronized (apps) { if (idx < apps.size()) ai = apps.get(idx); }
            if (ai != null) {
                Intent it = host.activity().getPackageManager().getLaunchIntentForPackage(ai.packageName);
                if (it != null) {
                    try {
                        host.activity().startActivity(it);
                        host.toast(host.activity().getString(R.string.toast_app_started));
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {}

    @Override public void onClosed() {}

    @Override public void onRestored() {}
}
