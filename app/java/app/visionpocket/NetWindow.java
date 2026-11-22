package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.List;

/** Shared-room window: host a room on this phone or join one by IP. */
public class NetWindow implements WindowContent {

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
    private final Paint pDim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDot = new Paint(Paint.ANTI_ALIAS_FLAG);

    public NetWindow(VpWindows wm, VrRenderer ren) {
        this.wm = wm;
        this.ren = ren;
        window = new VpWindows.Window("net", ren.activity().getString(R.string.win_net));
        chrome = new Panel(0, 0, 0, 0, 1.4f, 1.25f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        chrome.add("host", 40, 28 + s, 300, 150 + s);
        chrome.add("join", 320, 28 + s, 580, 150 + s);
        chrome.add("leave", 600, 28 + s, 860, 150 + s);
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
        pText.setColor(0xFFE0E5EF);
        pText.setTextSize(42f);
        pText.setTextAlign(Paint.Align.LEFT);
        pDim.setColor(0xFF9BA0AB);
        pDim.setTextSize(38f);
        pDim.setTextAlign(Paint.Align.LEFT);
        pDot.setStyle(Paint.Style.FILL);

        wm.placeNew(window);
    }

    /** Refreshes when the network layer reports a change. */
    public void refresh() { chrome.dirty = true; }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF101113);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);

        NetService net = ren.net();
        String status = net.status();
        boolean on = net.active;
        boolean hosting = on && net.isHost;
        boolean joined = on && !net.isHost && status.startsWith("connected");

        pDot.setColor(joined || hosting ? 0xFF9BC29B : (on ? 0xFFC8B48F : 0xFF565D6B));
        c.drawCircle(64f, 250f, 14f, pDot);

        String role;
        if (hosting) role = ren.activity().getString(R.string.net_role_host) + "  ·  " + NetService.localIp() + ":" + NetService.PORT;
        else if (joined) role = ren.activity().getString(R.string.net_role_client) + "  ·  " + status.substring("connected ".length());
        else if (on) role = ren.activity().getString(R.string.net_connecting);
        else role = ren.activity().getString(R.string.net_role_off);
        c.drawText(role, 100f, 264f, pText);

        c.drawText(ren.activity().getString(R.string.net_peers), 64f, 360f, pDim);
        String names;
        if (hosting) {
            List<NetService.Peer> ps = net.snapshot();
            StringBuilder sb = new StringBuilder();
            for (NetService.Peer peer : ps) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(peer.name);
            }
            names = sb.length() == 0 ? ren.activity().getString(R.string.net_peer_none) : sb.toString();
        } else if (joined) {
            names = net.roomList.isEmpty() ? ren.activity().getString(R.string.net_peer_none) : net.roomList;
        } else {
            names = ren.activity().getString(R.string.net_peer_none);
        }
        drawWrapped(c, names, 64f, 412f, p.pw - 120f, pDim);

        c.drawText(ren.activity().getString(R.string.net_hint), 64f, 560f, pDim);

        for (Panel.Button b : p.buttons) {
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            c.drawRoundRect(r, 20, 20, b.hover ? pBtnHover : pBtn);
            pBorder.setColor(b.hover ? 0xFF8FA3C8 : 0xFF2E3138);
            c.drawRoundRect(r, 20, 20, pBorder);
            String t;
            if ("host".equals(b.id)) t = ren.activity().getString(R.string.net_host);
            else if ("join".equals(b.id)) t = ren.activity().getString(R.string.net_join);
            else t = ren.activity().getString(R.string.net_leave);
            HomeSceneUtil.drawCentered(c, t, r, pLabel);
        }
    }

    private void drawWrapped(Canvas c, String text, float x, float y, float maxW, Paint paint) {
        int start = 0;
        float lineH = paint.getTextSize() * 1.35f;
        while (start < text.length() && y < 640f) {
            int end = Math.min(text.length(), start + 60);
            int brk = text.lastIndexOf(' ', end);
            if (brk <= start) brk = end;
            String chunk = text.substring(start, brk);
            while (paint.measureText(chunk) > maxW && chunk.length() > 8) chunk = chunk.substring(0, chunk.length() - 1);
            c.drawText(chunk, x, y, paint);
            start = brk == text.length() ? text.length() : brk + 1;
            y += lineH;
        }
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {
        if ("host".equals(id)) {
            ren.netHost();
        } else if ("join".equals(id)) {
            new KeyboardWindow(wm, ren, ren.activity().getString(R.string.net_ip_hint),
                    "", new KeyboardWindow.Callback() {
                @Override public void onOk(String t) { ren.netJoin(t); }
                @Override public void onCancel() {}
            });
        } else if ("leave".equals(id)) {
            ren.netLeave();
        }
        ren.activity().playClick();
        chrome.dirty = true;
    }

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {
        // only keep live while connecting; events call refresh()
        if (ren.net().active && ren.net().status().startsWith("connecting")) chrome.dirty = true;
    }

    @Override public void onClosed() {}

    @Override public void onRestored() {}
}
