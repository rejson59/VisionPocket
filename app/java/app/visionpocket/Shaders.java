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
            "void main(){\n" +
            "  if (!gl_FrontFacing) discard;\n" +
            "  vec4 c = texture2D(uTex, applyMode(vUV, uMode)); gl_FragColor = vec4(c.rgb, c.a * uAlpha);\n" +
            "}\n";

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
            "uniform vec2 uFit;\n" +
            "uniform float uMirror;\n" +
            "varying vec2 vUV;\n" +
            MODE_GLSL +
            "void main(){\n" +
            "  if (!gl_FrontFacing) discard;\n" +
            "  vec2 c = vUV - vec2(0.5);\n" +
            "  if (uMirror > 0.5) { c.x = -c.x; }\n" +
            "  float cs = cos(uAngle);\n" +
            "  float sn = sin(uAngle);\n" +
            "  c = vec2(c.x*cs - c.y*sn, c.x*sn + c.y*cs);\n" +
            "  c = c / (uZoom * uOverscan * uFit);\n" +
            "  vec2 suv = vec2(0.5) + c;\n" +
            "  if (abs(uSide) > 0.5) { suv.x = (suv.x + (uSide < 0.0 ? 0.0 : 1.0)) * 0.5; }\n" +
            "  vec4 col = texture2D(uCam, applyMode(suv, uMode));\n" +
            "  gl_FragColor = vec4(col.rgb * uBright, col.a);\n" +
            "}\n";

    public static final String VS_DOME =
            "attribute vec4 aPos;\n" +
            "attribute vec2 aUV;\n" +
            "varying vec2 vUV;\n" +
            "void main(){ vUV = aUV; gl_Position = vec4(aPos.xy, 0.999, 1.0); }\n";

    /**
     * TRUE 360-degree environment: per-pixel view ray, so the world exists in
     * EVERY direction (the old backdrop was a small flat gradient quad with
     * black glClear outside it - the "looking through a keyhole" bug).
     * uMode: 0 = room (floor grid + walls + ceiling), 1 = space (stars),
     * 2 = horizon (open gradient + infinite grid floor).
     */
    public static final String FS_DOME =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
            "precision highp float;\n" +
            "#else\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "uniform vec3 uRight;\n" +
            "uniform vec3 uUp;\n" +
            "uniform vec3 uFwd;\n" +
            "uniform float uTanX;\n" +
            "uniform float uTanY;\n" +
            "uniform float uMode;\n" +
            "uniform float uTime;\n" +
            "varying vec2 vUV;\n" +
            "float hash(vec3 p){ p = fract(p*0.3183099+vec3(0.11,0.17,0.23)); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }\n" +
            "float gline(float x){ float f = abs(fract(x+0.5)-0.5); return 1.0 - smoothstep(0.014,0.048,f); }\n" +
            "void main(){\n" +
            "  vec2 ndc = vec2(vUV.x*2.0-1.0, (1.0-vUV.y)*2.0-1.0);\n" +
            "  vec3 dir = normalize(uFwd + ndc.x*uTanX*uRight + ndc.y*uTanY*uUp);\n" +
            "  float up = dir.y;\n" +
            "  vec3 col;\n" +
            "  if (uMode < 0.5) {\n" +
            "    float R = 8.5; float FL = -1.55; float CE = 2.6;\n" +
            "    float tw = R / max(length(dir.xz), 0.0001);\n" +
            "    float tf = FL / min(dir.y, -0.0001);\n" +
            "    float tc = CE / max(dir.y, 0.0001);\n" +
            "    if (up < 0.0 && tf <= tw) {\n" +
            "      vec3 p = dir*tf; float fade = exp(-tf*0.20);\n" +
            "      float g = max(gline(p.x), gline(p.z));\n" +
            "      col = mix(vec3(0.082,0.086,0.100), vec3(0.130,0.140,0.162), g*fade);\n" +
            "      float r = length(p.xz);\n" +
            "      col += vec3(0.020,0.022,0.028)*(1.0-smoothstep(3.0,3.2,r))*smoothstep(3.2,3.4,r)*fade;\n" +
            "    } else if (up > 0.0 && tc <= tw) {\n" +
            "      vec3 p = dir*tc; float fade = exp(-tc*0.22);\n" +
            "      float g = max(gline(p.x/1.5), gline(p.z/1.5));\n" +
            "      col = mix(vec3(0.052,0.055,0.064), vec3(0.082,0.088,0.102), g*fade*0.8);\n" +
            "      float zen = length(dir.xz)/(abs(dir.y)+0.0001);\n" +
            "      col += vec3(0.085,0.090,0.105)*(1.0-smoothstep(0.18,0.62,zen));\n" +
            "    } else {\n" +
            "      float h = dir.y*tw;\n" +
            "      float vgrad = smoothstep(-1.5, 2.4, h);\n" +
            "      col = mix(vec3(0.072,0.077,0.088), vec3(0.145,0.153,0.170), vgrad*0.9);\n" +
            "      float yawD = degrees(atan(dir.x, -dir.z));\n" +
            "      float corner = abs(fract(yawD/90.0+0.5)-0.5)*90.0;\n" +
            "      col += vec3(0.028,0.030,0.036)*(1.0-smoothstep(0.4,2.2,corner))*(0.35+0.65*vgrad);\n" +
            "      float ay = abs(yawD);\n" +
            "      float pd = min(abs(ay-90.0), min(abs(ay-180.0), abs(ay-270.0)));\n" +
            "      float inX = 1.0-smoothstep(7.5,9.5,pd);\n" +
            "      float inY = smoothstep(0.35,0.65,h)*(1.0-smoothstep(1.95,2.35,h));\n" +
            "      float panel = inX*inY;\n" +
            "      col = mix(col, vec3(0.108,0.117,0.138), panel*0.9);\n" +
            "      col += vec3(0.040,0.044,0.054)*(1.0-smoothstep(0.15,0.7,abs(pd-8.6)))*inY;\n" +
            "      col += vec3(0.050,0.055,0.066)*exp(-abs(h+1.50)*6.0);\n" +
            "    }\n" +
            "  } else if (uMode < 1.5) {\n" +
            "    col = mix(vec3(0.022,0.024,0.030), vec3(0.007,0.008,0.012), smoothstep(-0.15,0.75,up));\n" +
            "    vec3 sp = dir*64.0;\n" +
            "    vec3 cell = floor(sp);\n" +
            "    float h1 = hash(cell);\n" +
            "    float star = step(0.994, h1)*pow(max(0.0,1.0-length(fract(sp)-0.5)*2.0), 6.0);\n" +
            "    col += vec3(0.70,0.73,0.80)*star;\n" +
            "    col += vec3(0.032,0.037,0.052)*exp(-pow(dot(dir, normalize(vec3(0.35,0.72,0.25))),2.0)*14.0);\n" +
            "    col += vec3(0.040,0.045,0.056)*exp(-abs(up)*7.0);\n" +
            "  } else {\n" +
            "    if (up < 0.0) {\n" +
            "      float tf2 = -1.6 / min(dir.y, -0.0001);\n" +
            "      vec3 p = dir*tf2; float fade = exp(-tf2*0.16);\n" +
            "      float g = max(gline(p.x), gline(p.z));\n" +
            "      col = mix(vec3(0.060,0.064,0.074), vec3(0.105,0.114,0.132), g*fade);\n" +
            "      col = mix(col, vec3(0.100,0.106,0.120), smoothstep(-0.30,0.0,up));\n" +
            "    } else {\n" +
            "      col = mix(vec3(0.105,0.112,0.126), vec3(0.028,0.031,0.040), smoothstep(0.0,0.85,up));\n" +
            "      col += vec3(0.062,0.066,0.078)*exp(-abs(up)*11.0);\n" +
            "    }\n" +
            "  }\n" +
            "  gl_FragColor = vec4(col, 1.0);\n" +
            "}\n";

    /** Eye-quad placement: uScale/uOffset put the unit quad into one half of
     *  the target (side-by-side or stacked, per the surface's long axis). */
    public static final String VS_DIST =
            "uniform vec2 uScale;\n" +
            "uniform vec2 uOffset;\n" +
            "attribute vec4 aPos;\n" +
            "varying vec2 vPos;\n" +
            "void main(){ vPos = aPos.xy; gl_Position = vec4(aPos.x * uScale.x + uOffset.x, aPos.y * uScale.y + uOffset.y, 0.0, 1.0); }\n";

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
            "  uv = clamp(uv, uEyeCenter - vec2(0.499), uEyeCenter + vec2(0.499));\n" +
            "  vec3 scene = texture2D(uScene, uv).rgb;\n" +
            "  vec2 b = vec2(0.985, 0.985);\n" +
            "  float cr = 0.30;\n" +
            "  vec2 cq = abs(vPos) - b + cr;\n" +
            "  float d = length(max(cq, vec2(0.0))) + min(max(cq.x, cq.y), 0.0) - cr;\n" +
            "  float m = 1.0 - smoothstep(-0.02, 0.008, d);\n" +
            "  gl_FragColor = vec4(scene * m, 1.0);\n" +
            "}\n";

    /** Cardboard overlay: black outside the two rounded eye rects (direct path). */
    public static final String FS_MASK =
            "precision mediump float;\n" +
            "uniform float uHalf;\n" +
            "varying vec2 vPos;\n" +
            "void main(){\n" +
            "  vec2 b = vec2(0.985, 0.985);\n" +
            "  float cr = 0.30;\n" +
            "  vec2 cq = abs(vPos) - b + cr;\n" +
            "  float d = length(max(cq, vec2(0.0))) + min(max(cq.x, cq.y), 0.0) - cr;\n" +
            "  float m = 1.0 - smoothstep(-0.02, 0.008, d);\n" +
            "  gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0 - m);\n" +
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
