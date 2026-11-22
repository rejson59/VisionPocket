package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.List;

/**
 * Spatial window manager: Windows-style floating windows that can be opened,
 * grabbed, moved in 3D, pushed/pulled in depth, scaled and minimized.
 */
public class VpWindows {

    public static final int STRIP_PX = 110; // title strip height in canvas px

    public interface WmHost {
        SettingsStore settings();
        MainActivity activity();
        void toast(String msg);
        void recenterView();
        void applyResolution();
        void pickVideo();
        void pickImage();
        float headYaw();
    }

    // ============================ Window ============================

    public static class Window {
        public final String kind;
        public String title;
        public WindowContent content;
        public boolean minimized = false;
        public boolean focused = false;
        public float scale = 1f;
        public float minScale = 0.45f, maxScale = 2.3f;

        // anchor (world)
        public float ax, ay, az, ayaw;

        public final List<Part> parts = new ArrayList<>();
        public Panel chromePanel;

        // chrome paints
        private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pStrip = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pStripFocused = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pSym = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pLine = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Window(String kind, String title) {
            this.kind = kind;
            this.title = title;
            pBg.setColor(0xF1141622);
            pStrip.setColor(0xFF1B2030);
            pStripFocused.setColor(0xFF28406E);
            pBtn.setColor(0x333B4E66);
            pBtnHover.setColor(0xFFB3402F);
            pTitle.setColor(0xFFE8ECF5);
            pTitle.setTextSize(54f);
            pTitle.setTextAlign(Paint.Align.LEFT);
            pTitle.setFakeBoldText(true);
            pSym.setColor(0xFFE8ECF5);
            pSym.setTextSize(60f);
            pSym.setTextAlign(Paint.Align.CENTER);
            pLine.setStyle(Paint.Style.STROKE);
            pLine.setStrokeWidth(3f);
            pLine.setColor(0xFF39415A);
        }

        public static class Part {
            public final Panel panel;
            public float offX, offY, offZ;
            public float baseW, baseH;
            public Part(Panel p, float ox, float oy, float oz) {
                panel = p; offX = ox; offY = oy; offZ = oz;
                baseW = p.width; baseH = p.height;
            }
        }

        public void addPart(Panel p, float ox, float oy, float oz) {
            parts.add(new Part(p, ox, oy, oz));
        }

        /** Attaches the chrome (title strip + close/minimize) to a panel. */
        public void attachChrome(Panel p) {
            chromePanel = p;
            p.add("chrome_min", p.pw - 210, 12, p.pw - 120, 98);
            p.add("chrome_close", p.pw - 110, 12, p.pw - 20, 98);
            p.hook = this::draw;
        }

        /** Updates a part's base size/offset (e.g. cinema video aspect). */
        public void setPartGeometry(Panel p, float baseW, float baseH, float offX, float offY, float offZ) {
            for (Part pt : parts) {
                if (pt.panel == p) {
                    pt.baseW = baseW; pt.baseH = baseH;
                    pt.offX = offX; pt.offY = offY; pt.offZ = offZ;
                }
            }
            layout();
        }

        public void layout() {
            float s = scale;
            for (Part pt : parts) {
                Panel p = pt.panel;
                p.cx = ax + pt.offX * s;
                p.cy = ay + pt.offY * s;
                p.cz = az + pt.offZ * s;
                p.yaw = ayaw;
                p.width = Math.max(0.15f, pt.baseW * s);
                p.height = Math.max(0.1f, pt.baseH * s);
            }
        }

        public void moveBy(float dx, float dy, float dz) {
            ax += dx; ay += dy; az += dz;
            // keep windows within a sane sphere around the head
            float r2 = ax * ax + az * az;
            float maxR = 6.5f;
            if (r2 > maxR * maxR) {
                float f = maxR / (float) Math.sqrt(r2);
                ax *= f; az *= f;
            }
            if (az > -0.35f) az = -0.35f;
            ay = Math.max(-1.5f, Math.min(1.9f, ay));
            layout();
        }

        public void applyScale(float f) {
            scale = Math.max(minScale, Math.min(maxScale, f));
            layout();
        }

        public void setMinimized(boolean m) {
            minimized = m;
            for (Part pt : parts) pt.panel.visible = !m;
            if (!m && content != null) content.onRestored();
        }

        public void rotateBy(float d) {
            float c = (float) Math.cos(d), s = (float) Math.sin(d);
            float nx = ax * c - az * s;
            float nz = ax * s + az * c;
            ax = nx; az = nz;
            ayaw -= d;
            layout();
        }

        /** Draws title strip + content body (hook of the chrome panel). */
        public void draw(Canvas c, Panel p) {
            pBg.setColor(0xF1141622);
            c.drawRoundRect(0, 0, p.pw, p.ph, 44, 44, pBg);
            // rounded-top title strip
            pStripFocused.setColor(0xFF28406E);
            c.drawRoundRect(0, 0, p.pw, STRIP_PX + 40, 44, 44, focused ? pStripFocused : pStrip);
            c.drawRect(0, STRIP_PX - 40, p.pw, STRIP_PX, focused ? pStripFocused : pStrip);
            c.drawText(title, 34f, 72f, pTitle);
            for (Panel.Button b : p.buttons) {
                if (!"chrome_close".equals(b.id) && !"chrome_min".equals(b.id)) continue;
                c.drawRoundRect(b.rect, 18, 18, b.hover ? pBtnHover : pBtn);
                c.drawRoundRect(b.rect, 18, 18, pLine);
                pSym.setTextAlign(Paint.Align.CENTER);
                c.drawText("chrome_min".equals(b.id) ? "—" : "✕",
                        (b.rect.left + b.rect.right) / 2f, 74f, pSym);
            }
            if (content != null) {
                c.save();
                c.translate(0, STRIP_PX);
                try { content.draw(c, p, focused); } catch (Exception ignored) {}
                c.restore();
            }
        }
    }

    // ============================ Manager ============================

    public static class Hit {
        public final Window win;   // null for standalone panels (dock)
        public final Panel panel;
        public final float t, px, py;
        public Hit(Window w, Panel p, float t, float px, float py) {
            this.win = w; this.panel = p; this.t = t; this.px = px; this.py = py;
        }
    }

    public final List<Window> list = new ArrayList<>();
    private final WmHost host;
    private int spawnShift = 0;

    public VpWindows(WmHost host) { this.host = host; }

    public Window find(String kind) {
        for (Window w : list) if (w.kind.equals(kind)) return w;
        return null;
    }

    public Window focusedWindow() {
        for (int i = list.size() - 1; i >= 0; i--) {
            Window w = list.get(i);
            if (!w.minimized) return w;
        }
        return null;
    }

    public void focus(Window w) {
        list.remove(w);
        list.add(w);
        w.focused = true;
        w.setMinimized(false);
        for (Window o : list) if (o != w) o.focused = false;
    }

    public void add(Window w) {
        list.add(w);
        focus(w);
    }

    public void close(Window w) {
        if (w.content != null) w.content.onClosed();
        list.remove(w);
        for (Window o : list) o.focused = false;
        Window top = focusedWindow();
        if (top != null) top.focused = true;
    }

    /** Places a new window in front of the user with simple cascade. */
    public void placeNew(Window w) {
        float yaw = host.headYaw();
        float dist = 2.3f;
        float fx = (float) Math.sin(yaw), fz = -(float) Math.cos(yaw);
        float px = (float) Math.cos(yaw), pz = (float) Math.sin(yaw);
        float shift = spawnShift * 0.55f;
        spawnShift = (spawnShift + 1) % 3;
        w.ax = fx * dist + px * shift;
        w.az = fz * dist + pz * shift;
        w.ay = 0.18f - spawnShift * 0.06f;
        w.ayaw = yaw;
        w.layout();
        add(w);
    }

    /** Raycast against all visible windows; nearest hit wins. */
    public Hit raycast(float ox, float oy, float oz, float dx, float dy, float dz) {
        Hit best = null;
        for (Window w : list) {
            if (w.minimized) continue;
            for (Window.Part pt : w.parts) {
                Panel p = pt.panel;
                if (!p.visible) continue;
                float[] h = p.rayHit(ox, oy, oz, dx, dy, dz);
                if (h != null && (best == null || h[0] < best.t)) {
                    best = new Hit(w, p, h[0], h[1], h[2]);
                }
            }
        }
        return best;
    }

    public void clearHover() {
        for (Window w : list) for (Window.Part pt : w.parts) pt.panel.clearHover();
    }

    public void onRecenter(float d) {
        for (Window w : list) w.rotateBy(d);
    }

    public void update() {
        for (Window w : list) if (w.content != null && !w.minimized) w.content.update();
    }
}
