package app.visionpocket;

import android.opengl.GLES20;
import android.opengl.GLES11Ext;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Small OpenGL ES 2.0 helpers. */
public final class GlUtil {

    private GlUtil() {}

    public static FloatBuffer fb(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fbv = bb.asFloatBuffer();
        fbv.put(data);
        fbv.position(0);
        return fbv;
    }

    /** Unit quad: positions [-1..1], 4 vertices as triangle strip (TL, BL, BR, TR). */
    public static final float[] UNIT_QUAD = {
            -1f,  1f, 0f,
            -1f, -1f, 0f,
             1f, -1f, 0f,
             1f,  1f, 0f
    };
    public static final float[] UNIT_UV = {
            0f, 0f,
            0f, 1f,
            1f, 1f,
            1f, 0f
    };

    public static int compileShader(int type, String src) {
        int sh = GLES20.glCreateShader(type);
        GLES20.glShaderSource(sh, src);
        GLES20.glCompileShader(sh);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(sh);
            GLES20.glDeleteShader(sh);
            throw new IllegalStateException("Shader compile failed: " + log);
        }
        return sh;
    }

    public static int createProgram(String vsSrc, String fsSrc) {
        int vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSrc);
        int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSrc);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, vs);
        GLES20.glAttachShader(p, fs);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(p);
            GLES20.glDeleteProgram(p);
            throw new IllegalStateException("Program link failed: " + log);
        }
        return p;
    }

    public static int createTexture2D() {
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return t[0];
    }

    public static int createOesTexture() {
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, t[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return t[0];
    }

    /** Column-major 3x3 multiply: C = A * B. */
    public static void mat3mul(float[] A, float[] B, float[] C) {
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 3; r++) {
                float s = 0f;
                for (int k = 0; k < 3; k++) s += A[k * 3 + r] * B[c * 3 + k];
                C[c * 3 + r] = s;
            }
        }
    }

    /** Build 4x4 rotation (identity translation) from column-major 3x3. */
    public static void mat3to4(float[] m3, float[] m4) {
        Matrix.setIdentityM(m4, 0);
        for (int c = 0; c < 3; c++)
            for (int r = 0; r < 3; r++)
                m4[c * 4 + r] = m3[c * 3 + r];
    }
}
