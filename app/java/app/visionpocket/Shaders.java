package app.visionpocket;

/** All GLSL shader sources used by the renderer (extracted from VrRenderer). */
public final class Shaders {
    private Shaders() {}

    public static final String VS_TEX =
            "uniform mat4 uMVP;\n" +
            "attribute vec4 aPos;\n" +
            "attribute vec2 aUV;\n" +
            "varying vec2 vUV;\n" +
            "void main(){ gl_Position = uMVP * aPos; vUV = aUV; }\n";

    public static final String MODE_GLSL =
            "vec2 applyMode(vec2 uv, float m){\n" +
            "  if (m > 0.5 && m < 1.5) {\n" +
            "    uv.y = 0.5 + (uv.y - 0.5) * abs(uv.y - 0.5) * 2.0;\n" +
            "  } else if (m > 1.5) {\n" +
            "    float v = 1.0 - uv.y;\n" +
            "    uv.y = 0.5 + (v - 0.5) * abs(v - 0.5) * 2.0;\n" +
            "    uv.x = 1.0 - uv.x;\n" +
            "  }\n" +
            "  return uv;\n" +
            "}\n";

    public static final String FS_PANEL =
            "precision mediump float;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform float uAlpha;\n" +
            "uniform float uMode;\n" +
            "varying vec2 vUV;\n" +
            MODE_GLSL +
            "void main(){ vec4 c = texture2D(uTex, applyMode(vUV, uMode)); gl_FragColor = vec4(c.rgb, c.a * uAlpha); }\n";

    public static final String FS_CAM =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES uCam;\n" +
            "uniform float uAngle;\n" +
            "uniform float uZoom;\n" +
            "uniform float uOverscan;\n" +
            "uniform float uMode;\n" +
            "uniform float uSide;\n" +
            "uniform float uBright;\n" +
            "varying vec2 vUV;\n" +
            MODE_GLSL +
            "void main(){\n" +
            "  vec2 c = vUV - vec2(0.5);\n" +
            "  float cs = cos(uAngle);\n" +
            "  float sn = sin(uAngle);\n" +
            "  c = vec2(c.x*cs - c.y*sn, c.x*sn + c.y*cs);\n" +
            "  c = c / (uZoom * uOverscan);\n" +
            "  vec2 suv = vec2(0.5) + c;\n" +
            "  if (abs(uSide) > 0.5) { suv.x = (suv.x + (uSide < 0.0 ? 0.0 : 1.0)) * 0.5; }\n" +
            "  vec4 col = texture2D(uCam, applyMode(suv, uMode));\n" +
            "  gl_FragColor = vec4(col.rgb * uBright, col.a);\n" +
            "}\n";

    public static final String FS_SPACE =
            "precision mediump float;\n" +
            "uniform float uTime;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vec2 uv = vUV;\n" +
            "  vec3 col = mix(vec3(0.047, 0.050, 0.058), vec3(0.030, 0.032, 0.037), uv.y);\n" +
            "  float vig = smoothstep(1.15, 0.30, distance(uv, vec2(0.5, 0.42)));\n" +
            "  col *= 0.74 + 0.26 * vig;\n" +
            "  col += vec3(0.055, 0.058, 0.066) * pow(max(0.0, 1.0 - distance(uv, vec2(0.5, 0.94)) * 1.7), 3.0);\n" +
            "  gl_FragColor = vec4(col, 1.0);\n" +
            "}\n";

    public static final String FS_HORIZON =
            "precision mediump float;\n" +
            "uniform float uTime;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vec2 uv = vUV;\n" +
            "  float horizon = 0.55;\n" +
            "  vec3 col;\n" +
            "  if (uv.y > horizon) {\n" +
            "    col = mix(vec3(0.135, 0.145, 0.165), vec3(0.35, 0.34, 0.335), smoothstep(horizon, 0.78, uv.y));\n" +
            "    col = mix(col, vec3(0.118, 0.122, 0.138), smoothstep(0.78, 1.0, uv.y));\n" +
            "    col += vec3(0.10, 0.085, 0.07) * exp(-abs(uv.y - horizon) * 14.0) * exp(-abs(uv.x - 0.5) * 3.0);\n" +
            "  } else {\n" +
            "    col = mix(vec3(0.055, 0.055, 0.060), vec3(0.030, 0.031, 0.034), smoothstep(horizon, 0.0, uv.y));\n" +
            "  }\n" +
            "  gl_FragColor = vec4(col, 1.0);\n" +
            "}\n";

    public static final String VS_DIST =
            "uniform float uHalf;\n" +
            "attribute vec4 aPos;\n" +
            "varying vec2 vPos;\n" +
            "void main(){ vPos = aPos.xy; gl_Position = vec4(aPos.x * 0.5 + uHalf, aPos.y, 0.0, 1.0); }\n";

    public static final String FS_DIST =
            "precision mediump float;\n" +
            "uniform sampler2D uScene;\n" +
            "uniform vec2 uEyeCenter;\n" +
            "uniform float uAspect;\n" +
            "uniform float uK1;\n" +
            "uniform float uK2;\n" +
            "varying vec2 vPos;\n" +
            "void main(){\n" +
            "  vec2 q = vec2(vPos.x * uAspect, vPos.y);\n" +
            "  float r2 = dot(q, q);\n" +
            "  float f = 1.0 + uK1 * r2 + uK2 * r2 * r2;\n" +
            "  vec2 uv = uEyeCenter + (q * f) / vec2(uAspect, 1.0) * 0.5;\n" +
            "  uv.x = clamp(uv.x, uEyeCenter.x - 0.499, uEyeCenter.x + 0.499);\n" +
            "  uv.y = clamp(uv.y, 0.001, 0.999);\n" +
            "  vec3 scene = texture2D(uScene, uv).rgb;\n" +
            "  vec2 b = vec2(0.985, 0.985);\n" +
            "  float cr = 0.30;\n" +
            "  vec2 cq = abs(vPos) - b + cr;\n" +
            "  float d = length(max(cq, vec2(0.0))) + min(max(cq.x, cq.y), 0.0) - cr;\n" +
            "  float m = 1.0 - smoothstep(-0.02, 0.008, d);\n" +
            "  gl_FragColor = vec4(scene * m, 1.0);\n" +
            "}\n";

    public static final String VS_COLOR =
            "uniform mat4 uMVP;\n" +
            "attribute vec4 aPos;\n" +
            "void main(){ gl_Position = uMVP * aPos; }\n";

    public static final String FS_COLOR =
            "precision mediump float;\n" +
            "uniform vec4 uColor;\n" +
            "void main(){ gl_FragColor = uColor; }\n";
}
