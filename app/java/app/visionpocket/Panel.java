package app.visionpocket;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A world-locked or head-locked quad with a Canvas-drawn texture and
 * rectangular buttons hit-tested by the gaze ray.
 */
public class Panel {

    public interface DrawHook { void draw(Canvas c, Panel p); }
    public interface Click { void onClick(Button b); }

    public static class Button {
        public final String id;
        public final RectF rect;
        public boolean hover = false;
        public Button(String id, RectF rect) { this.id = id; this.rect = rect; }
        public boolean contains(float x, float y) { return rect.contains(x, y); }
    }

    // world/head-space placement
    public float cx, cy, cz, yaw;   // yaw in radians
    public float width, height;     // meters
    public boolean visible = true;

    // texture
    public final int pw, ph;        // bitmap pixels
    public final Bitmap bmp;
    public final Canvas canvas;
    public int tex = -1;
    public boolean dirty = true;

    public final List<Button> buttons = new ArrayList<>();
    public DrawHook hook;
    public Click click;

    public Panel(float cx, float cy, float cz, float yaw, float width, float height, float pxPerMeter) {
        this.cx = cx; this.cy = cy; this.cz = cz;
        this.yaw = yaw;
        this.width = width; this.height = height;
        pw = Math.max(8, Math.round(width * pxPerMeter));
        ph = Math.max(8, Math.round(height * pxPerMeter));
        bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bmp);
    }

    public Button add(String id, float l, float t, float r, float b) {
        Button btn = new Button(id, new RectF(l, t, r, b));
        buttons.add(btn);
        return btn;
    }

    public void clearButtons() { buttons.clear(); }

    /** Redraws the bitmap via the hook (called on the GL thread). */
    public void renderIfNeeded() {
        if (!dirty && tex >= 0) return;
        if (hook != null) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            try { hook.draw(canvas, this); } catch (Exception ignored) {}
        }
        if (tex < 0) tex = GlUtil.createTexture2D();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
        dirty = false;
    }

    /**
     * Intersect the world-space gaze ray with this panel; updates hover state.
     * Returns the hovered button or null.
     */
    public Button hitTest(float ox, float oy, float oz, float dx, float dy, float dz) {
        float c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
        float oxl = ox - cx, oyl = oy - cy, ozl = oz - cz;
        float lx = oxl * c - ozl * s;
        float ly = oyl;
        float lz = oxl * s + ozl * c;
        float ldx = dx * c - dz * s;
        float ldy = dy;
        float ldz = dx * s + dz * c;
        if (Math.abs(ldz) < 1e-6f) { clearHover(); return null; }
        float t = -lz / ldz;
        if (t <= 0) { clearHover(); return null; }
        float hx = lx + t * ldx;
        float hy = ly + t * ldy;
        float hw = width / 2f, hh = height / 2f;
        if (hx < -hw || hx > hw || hy < -hh || hy > hh) { clearHover(); return null; }
        float u = (hx + hw) / width;
        float v = 0.5f - hy / height;
        float px = u * pw, py = v * ph;
        Button found = null;
        for (Button b : buttons) {
            boolean hov = b.contains(px, py);
            if (hov != b.hover) { b.hover = hov; dirty = true; }
            if (hov) found = b;
        }
        return found;
    }

    public void clearHover() {
        for (Button b : buttons) {
            if (b.hover) { b.hover = false; dirty = true; }
        }
    }

    /** Re-anchors this panel after a view recenter (rotate world position by -deltaYaw). */
    public void rotateBy(float d) {
        float c = (float) Math.cos(d), s = (float) Math.sin(d);
        float nx = cx * c - cz * s;
        float nz = cx * s + cz * c;
        cx = nx; cz = nz;
        yaw -= d;
    }

    public void release() {
        if (tex >= 0) { GLES20.glDeleteTextures(1, new int[]{tex}, 0); tex = -1; }
        bmp.recycle();
    }

    // --- shared geometry (unit quad, scaled via model matrix) ---
    private static FloatBuffer unitQuadPos, unitQuadUv;

    public static FloatBuffer quadPos() {
        if (unitQuadPos == null) unitQuadPos = GlUtil.fb(GlUtil.UNIT_QUAD);
        return unitQuadPos;
    }

    public static FloatBuffer quadUv() {
        if (unitQuadUv == null) unitQuadUv = GlUtil.fb(GlUtil.UNIT_UV);
        return unitQuadUv;
    }
}
