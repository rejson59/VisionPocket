import * as THREE from 'three';
import type { Settings } from '../core/settings';

const VERT = /* glsl */ `
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = vec4(position.xy, 0.0, 1.0);
  }
`;

const FRAG = /* glsl */ `
  precision highp float;
  uniform sampler2D uLeft;
  uniform sampler2D uRight;
  uniform float uK1;
  uniform float uZoom;
  uniform float uFit;
  uniform float uChroma;
  uniform float uVignette;
  uniform float uBrightness;
  uniform float uEyeAspect;
  varying vec2 vUv;

  vec3 fetch(sampler2D tex, vec2 uv) {
    return texture2D(tex, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
  }

  void main() {
    float right = step(0.5, vUv.x);
    vec2 luv = vec2(vUv.x * 2.0 - right, vUv.y);
    vec2 p = (luv * 2.0 - 1.0) * vec2(uEyeAspect, 1.0);

    float r2 = dot(p, p);
    float f = 1.0 + uK1 * r2 + uK1 * uK1 * 0.55 * r2 * r2;
    float scale = f * uFit * uZoom;
    vec2 src = p * scale;
    vec2 suv = vec2(src.x / uEyeAspect, src.y) * 0.5 + 0.5;

    vec3 col;
    if (right > 0.5) {
      col = fetch(uRight, suv);
    } else {
      col = fetch(uLeft, suv);
    }

    // aberracja chromatyczna — im dalej od środka, tym mocniejsza (jak w soczewce)
    if (uChroma > 0.001) {
      float ca = uChroma * r2 * 0.012;
      vec2 sr = vec2(src.x / uEyeAspect, src.y);
      vec2 dir = normalize(sr + 1e-5) * ca;
      vec3 rCol = right > 0.5
        ? fetch(uRight, (sr + dir) * 0.5 + 0.5)
        : fetch(uLeft, (sr + dir) * 0.5 + 0.5);
      vec3 bCol = right > 0.5
        ? fetch(uRight, (sr - dir) * 0.5 + 0.5)
        : fetch(uLeft, (sr - dir) * 0.5 + 0.5);
      col = vec3(rCol.r, col.g, bCol.b);
    }

    // winieta + delikatne przyciemnienie brzegów soczewki
    vec2 lp = luv * 2.0 - 1.0;
    float edge = length(lp);
    float vig = 1.0 - uVignette * pow(clamp(edge, 0.0, 1.0), 2.2);
    float mask = smoothstep(1.02, 0.985, length(p * 0.86));
    col *= vig * mask * uBrightness;

    // linowe -> sRGB (renderer ma outputColorSpace = Linear)
    col = pow(max(col, vec3(0.0)), vec3(1.0 / 2.2));
    gl_FragColor = vec4(col, 1.0);
  }
`;

const FLAT_FRAG = /* glsl */ `
  precision highp float;
  uniform sampler2D uLeft;
  uniform float uBrightness;
  varying vec2 vUv;
  void main() {
    vec3 col = texture2D(uLeft, vUv).rgb * uBrightness;
    col = pow(max(col, vec3(0.0)), vec3(1.0 / 2.2));
    gl_FragColor = vec4(col, 1.0);
  }
`;

export class StereoRenderer {
  readonly renderer: THREE.WebGLRenderer;
  readonly head = new THREE.Object3D();
  readonly leftEye: THREE.PerspectiveCamera;
  readonly rightEye: THREE.PerspectiveCamera;
  readonly flatEye: THREE.PerspectiveCamera;

  private rtLeft!: THREE.WebGLRenderTarget;
  private rtRight!: THREE.WebGLRenderTarget;
  private rtFlat!: THREE.WebGLRenderTarget;
  private quadScene = new THREE.Scene();
  private quadCam = new THREE.Camera();
  private material!: THREE.ShaderMaterial;
  private flatMaterial!: THREE.ShaderMaterial;
  private flatQuad!: THREE.Mesh;
  private width = 1;
  private height = 1;

  constructor(canvas: HTMLCanvasElement, private settings: Settings) {
    this.renderer = new THREE.WebGLRenderer({
      canvas,
      antialias: false,
      alpha: false,
      powerPreference: 'high-performance',
      stencil: false,
      depth: true,
    });
    this.renderer.setClearColor(0x000000, 1);
    this.renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
    this.renderer.setPixelRatio(1);
    this.renderer.autoClear = true;

    this.leftEye = new THREE.PerspectiveCamera(settings.fov, 1, 0.02, 300);
    this.rightEye = new THREE.PerspectiveCamera(settings.fov, 1, 0.02, 300);
    this.flatEye = new THREE.PerspectiveCamera(62, 1, 0.02, 300);
    this.head.add(this.leftEye, this.rightEye, this.flatEye);

    this.buildQuad();
    this.resize();
  }

  private buildQuad(): void {
    const geo = new THREE.PlaneGeometry(2, 2);

    this.material = new THREE.ShaderMaterial({
      vertexShader: VERT,
      fragmentShader: FRAG,
      depthTest: false,
      depthWrite: false,
      uniforms: {
        uLeft: { value: null },
        uRight: { value: null },
        uK1: { value: this.settings.lens },
        uZoom: { value: 1 },
        uFit: { value: 1 },
        uChroma: { value: this.settings.chromatic ? 1 : 0 },
        uVignette: { value: this.settings.vignette },
        uBrightness: { value: this.settings.brightness },
        uEyeAspect: { value: 1 },
      },
    });

    this.flatMaterial = new THREE.ShaderMaterial({
      vertexShader: VERT,
      fragmentShader: FLAT_FRAG,
      depthTest: false,
      depthWrite: false,
      uniforms: {
        uLeft: { value: null },
        uBrightness: { value: this.settings.brightness },
      },
    });

    const stereoQuad = new THREE.Mesh(geo, this.material);
    stereoQuad.frustumCulled = false;
    stereoQuad.name = 'stereoQuad';
    this.quadScene.add(stereoQuad);

    this.flatQuad = new THREE.Mesh(geo, this.flatMaterial);
    this.flatQuad.frustumCulled = false;
    this.flatQuad.visible = false;
    this.quadScene.add(this.flatQuad);
  }

  resize(): void {
    const w = Math.max(2, Math.floor(window.innerWidth));
    const h = Math.max(2, Math.floor(window.innerHeight));
    this.width = w;
    this.height = h;
    this.renderer.setSize(w, h, false);
    (this.renderer.domElement as HTMLCanvasElement).style.width = '100%';
    (this.renderer.domElement as HTMLCanvasElement).style.height = '100%';

    const scale = Math.min(1, Math.max(0.4, this.settings.renderScale));
    const eyeW = Math.max(2, Math.floor((w / 2) * scale));
    const eyeH = Math.max(2, Math.floor(h * scale));

    this.rtLeft?.dispose();
    this.rtRight?.dispose();
    this.rtLeft = new THREE.WebGLRenderTarget(eyeW, eyeH, {
      minFilter: THREE.LinearFilter,
      magFilter: THREE.LinearFilter,
      depthBuffer: true,
      stencilBuffer: false,
      type: THREE.UnsignedByteType,
    });
    this.rtRight = new THREE.WebGLRenderTarget(eyeW, eyeH, {
      minFilter: THREE.LinearFilter,
      magFilter: THREE.LinearFilter,
      depthBuffer: true,
      stencilBuffer: false,
      type: THREE.UnsignedByteType,
    });
    this.rtLeft.texture.colorSpace = THREE.LinearSRGBColorSpace;
    this.rtRight.texture.colorSpace = THREE.LinearSRGBColorSpace;

    this.rtFlat?.dispose();
    this.rtFlat = new THREE.WebGLRenderTarget(Math.max(2, Math.floor(w * scale)), Math.max(2, Math.floor(h * scale)), {
      minFilter: THREE.LinearFilter,
      magFilter: THREE.LinearFilter,
      depthBuffer: true,
      stencilBuffer: false,
      type: THREE.UnsignedByteType,
    });
    this.rtFlat.texture.colorSpace = THREE.LinearSRGBColorSpace;

    const eyeAspect = w / 2 / h;
    this.leftEye.aspect = eyeAspect;
    this.rightEye.aspect = eyeAspect;
    this.flatEye.aspect = w / h;
    this.leftEye.updateProjectionMatrix();
    this.rightEye.updateProjectionMatrix();
    this.flatEye.updateProjectionMatrix();

    // punktowa kalibracja dystorsji: przy jakim r obraz wychodzi poza zakres
    const rMax = eyeAspect * eyeAspect + 1;
    const fit = 1 / (1 + this.settings.lens * rMax + this.settings.lens * this.settings.lens * 0.55 * rMax * rMax);
    this.material.uniforms.uFit.value = fit;
    this.material.uniforms.uEyeAspect.value = eyeAspect;
  }

  applySettings(): void {
    const s = this.settings;
    const ipd = Math.max(0.04, Math.min(0.09, s.ipd));
    this.leftEye.position.set(-ipd / 2, 0, 0);
    this.rightEye.position.set(ipd / 2, 0, 0);
    this.leftEye.fov = s.fov;
    this.rightEye.fov = s.fov;
    this.leftEye.updateProjectionMatrix();
    this.rightEye.updateProjectionMatrix();

    if (this.material) {
      this.material.uniforms.uK1.value = s.lens;
      this.material.uniforms.uZoom.value = s.zoom;
      this.material.uniforms.uChroma.value = s.chromatic ? 1 : 0;
      this.material.uniforms.uVignette.value = s.vignette;
      this.material.uniforms.uBrightness.value = s.brightness;
      const eyeAspect = this.width / 2 / this.height;
      const rMax = eyeAspect * eyeAspect + 1;
      const fit =
        1 / (1 + s.lens * rMax + s.lens * s.lens * 0.55 * rMax * rMax);
      this.material.uniforms.uFit.value = fit;
      this.material.uniforms.uEyeAspect.value = eyeAspect;
    }
    if (this.flatMaterial) {
      this.flatMaterial.uniforms.uBrightness.value = s.brightness;
    }
  }

  setHeadQuaternion(q: THREE.Quaternion): void {
    this.head.quaternion.copy(q);
  }

  /**
   * @param mode 'stereo' — dwa oczy z dystorsją; 'flat' — jeden obraz (podgląd na telefonie/PC)
   */
  render(mode: 'stereo' | 'flat', scene: THREE.Scene): void {
    if (mode === 'flat') {
      this.renderFlat(scene);
      return;
    }

    this.quadScene.children.forEach((c) => (c.visible = c !== this.flatQuad));

    // lewe oko
    this.renderer.setRenderTarget(this.rtLeft);
    this.renderer.clear();
    this.renderer.render(scene, this.leftEye);

    // prawe oko
    this.renderer.setRenderTarget(this.rtRight);
    this.renderer.clear();
    this.renderer.render(scene, this.rightEye);

    // kompozycja z dystorsją obiektywów
    this.renderer.setRenderTarget(null);
    this.material.uniforms.uLeft.value = this.rtLeft.texture;
    this.material.uniforms.uRight.value = this.rtRight.texture;
    this.renderer.clear();
    this.renderer.render(this.quadScene, this.quadCam);
  }

  /** Render „płaski” — jeden obraz, ta sama korekcja kolorów co w VR. */
  renderFlat(scene: THREE.Scene): void {
    this.renderer.setRenderTarget(this.rtFlat);
    this.renderer.clear();
    this.renderer.render(scene, this.flatEye);

    this.renderer.setRenderTarget(null);
    this.quadScene.children.forEach((c) => (c.visible = c === this.flatQuad));
    this.flatMaterial.uniforms.uLeft.value = this.rtFlat.texture;
    this.renderer.clear();
    this.renderer.render(this.quadScene, this.quadCam);
  }

  get size(): { width: number; height: number } {
    return { width: this.width, height: this.height };
  }
}
