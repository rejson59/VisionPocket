package app.visionpocket;

import java.nio.FloatBuffer;

/** Simple static mesh (positions + UVs) for curved window surfaces. */
public class Mesh {

    public final FloatBuffer pos, uv;
    public final int count;
    public float baseW = 1f, baseH = 1f;

    public Mesh(float[] posArr, float[] uvArr) {
        pos = GlUtil.fb(posArr);
        uv = GlUtil.fb(uvArr);
        count = posArr.length / 3;
    }

    /**
     * Cylindrical screen segment that wraps toward the viewer (like Quest screens).
     * Local origin = center of the screen surface; the front face points +Z.
     *
     * @param chordWidth total horizontal width of the screen (flat chord, meters)
     * @param height     screen height (meters)
     * @param radius     cylinder radius (meters); the arc adapts to the chord
     */
    public static Mesh cylinder(float chordWidth, float height, float radius, int cols, int rows) {
        float arc = (float) (2.0 * Math.asin(Math.min(1.0, chordWidth / (2.0 * radius))));
        if (arc > (float) Math.toRadians(150)) arc = (float) Math.toRadians(150);
        float chord = 2f * radius * (float) Math.sin(arc / 2f);

        int vw = cols + 1, vh = rows + 1;
        float[] p = new float[vw * vh * 3];
        float[] t = new float[vw * vh * 2];
        for (int r = 0; r <= rows; r++) {
            float v = r / (float) rows;
            float y = (0.5f - v) * height;
            for (int c = 0; c <= cols; c++) {
                float u = c / (float) cols;
                float phi = (u - 0.5f) * arc;
                int i = r * vw + c;
                p[i * 3] = radius * (float) Math.sin(phi);
                p[i * 3 + 1] = y;
                p[i * 3 + 2] = radius * ((float) Math.cos(phi) - 1f);
                t[i * 2] = u;
                t[i * 2 + 1] = v;
            }
        }
        int quads = cols * rows;
        float[] tp = new float[quads * 6 * 3];
        float[] tt = new float[quads * 6 * 2];
        int k = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i00 = r * vw + c, i01 = r * vw + c + 1;
                int i10 = (r + 1) * vw + c, i11 = (r + 1) * vw + c + 1;
                k = emit(tp, tt, k, p, t, i00, i10, i11);
                k = emit(tp, tt, k, p, t, i00, i11, i01);
            }
        }
        Mesh m = new Mesh(tp, tt);
        m.baseW = chord;
        m.baseH = height;
        return m;
    }

    /**
     * Inside-out sphere for 360° equirectangular panoramas.
     * Longitude 0 maps to -Z (forward at load), u = lon/2pi + 0.5, v = 0 at top.
     */
    public static Mesh sphere(float radius, int cols, int rows) {
        int vw = cols + 1, vh = rows + 1;
        float[] p = new float[vw * vh * 3];
        float[] t = new float[vw * vh * 2];
        for (int r = 0; r <= rows; r++) {
            float v = r / (float) rows;
            float lat = (0.5f - v) * (float) Math.PI;
            for (int c = 0; c <= cols; c++) {
                float u = c / (float) cols;
                float lon = (u - 0.5f) * 2f * (float) Math.PI;
                int i = r * vw + c;
                p[i * 3] = radius * (float) Math.cos(lat) * (float) Math.sin(lon);
                p[i * 3 + 1] = radius * (float) Math.sin(lat);
                p[i * 3 + 2] = -radius * (float) Math.cos(lat) * (float) Math.cos(lon);
                t[i * 2] = u;
                t[i * 2 + 1] = v;
            }
        }
        int quads = cols * rows;
        float[] tp = new float[quads * 6 * 3];
        float[] tt = new float[quads * 6 * 2];
        int k = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i00 = r * vw + c, i01 = r * vw + c + 1;
                int i10 = (r + 1) * vw + c, i11 = (r + 1) * vw + c + 1;
                k = emit(tp, tt, k, p, t, i00, i10, i11);
                k = emit(tp, tt, k, p, t, i00, i11, i01);
            }
        }
        return new Mesh(tp, tt);
    }

    private static int emit(float[] tp, float[] tt, int k, float[] p, float[] t, int a, int b, int c) {
        tp[k * 3] = p[a * 3]; tp[k * 3 + 1] = p[a * 3 + 1]; tp[k * 3 + 2] = p[a * 3 + 2];
        tt[k * 2] = t[a * 2]; tt[k * 2 + 1] = t[a * 2 + 1];
        k++;
        tp[k * 3] = p[b * 3]; tp[k * 3 + 1] = p[b * 3 + 1]; tp[k * 3 + 2] = p[b * 3 + 2];
        tt[k * 2] = t[b * 2]; tt[k * 2 + 1] = t[b * 2 + 1];
        k++;
        tp[k * 3] = p[c * 3]; tp[k * 3 + 1] = p[c * 3 + 1]; tp[k * 3 + 2] = p[c * 3 + 2];
        tt[k * 2] = t[c * 2]; tt[k * 2 + 1] = t[c * 2 + 1];
        k++;
        return k;
    }
}
