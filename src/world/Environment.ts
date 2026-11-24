import * as THREE from 'three';
import { getDotTexture, getGlowTexture } from '../ui/textures';

const GRID_VERT = /* glsl */ `
  varying vec3 vWorld;
  void main() {
    vec4 wp = modelMatrix * vec4(position, 1.0);
    vWorld = wp.xyz;
    gl_Position = projectionMatrix * viewMatrix * wp;
  }
`;

const GRID_FRAG = /* glsl */ `
  precision highp float;
  uniform float uTime;
  uniform vec3 uColor;
  uniform vec3 uAccent;
  varying vec3 vWorld;

  float gridMask(vec2 coord) {
    vec2 g = abs(fract(coord - 0.5) - 0.5) / max(fwidth(coord), vec2(1e-5));
    return 1.0 - min(min(g.x, g.y), 1.0);
  }

  void main() {
    vec2 p = vWorld.xz;
    float fine = gridMask(p * 2.0) * 0.28;
    float bold = gridMask(p * 0.5) * 0.85;
    float d = length(p);
    float fade = smoothstep(52.0, 3.0, d);
    float pulse = 0.72 + 0.28 * sin(d * 1.6 - uTime * 1.5);
    float lines = (fine + bold) * fade;
    vec3 col = uColor * lines * pulse + uAccent * 0.035 * fade;
    gl_FragColor = vec4(col, clamp(lines * 0.95 + 0.05 * fade, 0.0, 1.0));
  }
`;

function makeDomeTexture(): THREE.Texture {
  const canvas = document.createElement('canvas');
  canvas.width = 32;
  canvas.height = 512;
  const ctx = canvas.getContext('2d')!;
  const g = ctx.createLinearGradient(0, 0, 0, 512);
  g.addColorStop(0, '#02030a');
  g.addColorStop(0.34, '#070c1c');
  g.addColorStop(0.52, '#131c3c');
  g.addColorStop(0.62, '#243059');
  g.addColorStop(0.7, '#0a1024');
  g.addColorStop(1, '#03040a');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, 32, 512);
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.wrapS = THREE.RepeatWrapping;
  return tex;
}

/** Otoczenie: gradientowa kopuła, świecąca siatka podłogi, cząsteczki i neonowe słupy. */
export class Environment {
  readonly group = new THREE.Group();
  readonly dome: THREE.Mesh;
  readonly particles: THREE.Points;
  private gridMat: THREE.ShaderMaterial;
  private particlesBase: Float32Array;
  /** Obraz z kamery — trzymany w przestrzeni głowy (zawsze przed twarzą). */
  readonly passthroughGroup = new THREE.Group();
  private ptMain: THREE.Mesh;
  private ptHalo: THREE.Mesh;
  private ptMainMat: THREE.MeshBasicMaterial;
  private ptHaloMat: THREE.MeshBasicMaterial;
  private clutter: THREE.Object3D[] = [];
  private camFov = 62;
  private ptRotate: 0 | 90 | 180 | 270 = 0;
  private lastVideoKey = '';

  constructor() {
    // kopuła
    this.dome = new THREE.Mesh(
      new THREE.SphereGeometry(90, 32, 20),
      new THREE.MeshBasicMaterial({
        map: makeDomeTexture(),
        side: THREE.BackSide,
        depthWrite: false,
        toneMapped: false,
        fog: false,
      }),
    );
    this.dome.renderOrder = -10;
    this.group.add(this.dome);

    // podłoga
    this.gridMat = new THREE.ShaderMaterial({
      vertexShader: GRID_VERT,
      fragmentShader: GRID_FRAG,
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
      uniforms: {
        uTime: { value: 0 },
        uColor: { value: new THREE.Color(0x35e0ff) },
        uAccent: { value: new THREE.Color(0x8b7bff) },
      },
    });
    const floor = new THREE.Mesh(new THREE.PlaneGeometry(120, 120), this.gridMat);
    floor.rotation.x = -Math.PI / 2;
    floor.position.y = -1.35;
    this.group.add(floor);

    // cząsteczki
    const count = 420;
    const positions = new Float32Array(count * 3);
    this.particlesBase = positions;
    for (let i = 0; i < count; i++) {
      const radius = 2.5 + Math.random() * 14;
      const angle = Math.random() * Math.PI * 2;
      positions[i * 3] = Math.cos(angle) * radius;
      positions[i * 3 + 1] = -1 + Math.random() * 8;
      positions[i * 3 + 2] = Math.sin(angle) * radius;
    }
    const pGeo = new THREE.BufferGeometry();
    pGeo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    this.particles = new THREE.Points(
      pGeo,
      new THREE.PointsMaterial({
        map: getDotTexture(),
        color: 0x8fd8ff,
        size: 0.075,
        transparent: true,
        opacity: 0.55,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        sizeAttenuation: true,
      }),
    );
    this.group.add(this.particles);

    // neonowe słupy w oddali
    for (let i = 0; i < 8; i++) {
      const angle = (i / 8) * Math.PI * 2 + 0.3;
      const radius = 9 + (i % 3) * 2.5;
      const color = i % 2 === 0 ? 0x35e0ff : 0xff3ea5;
      const strip = new THREE.Mesh(
        new THREE.BoxGeometry(0.05, 4.2 + (i % 4) * 0.8, 0.05),
        new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.8, toneMapped: false }),
      );
      strip.position.set(Math.cos(angle) * radius, -0.4, Math.sin(angle) * radius);
      this.group.add(strip);

      const glow = new THREE.Sprite(
        new THREE.SpriteMaterial({
          map: getGlowTexture(),
          color,
          transparent: true,
          opacity: 0.22,
          blending: THREE.AdditiveBlending,
          depthWrite: false,
        }),
      );
      glow.scale.setScalar(2.6);
      glow.position.copy(strip.position);
      this.group.add(glow);
    }

    // --- MR: obraz z kamery ---
    // Kamera telefonu ma ~62° pola widzenia, a ekran VR ~92°. Rozciągnięcie
    // zdjęcia na całą sferę dawało „rybie oko” i psuło cały efekt.
    // Dlatego: ostry obraz 1:1 w polu widzenia kamery + przyciemnione,
    // rozmyte obrzeże, żeby nie było widocznej krawędzi prostokąta.
    this.ptMainMat = new THREE.MeshBasicMaterial({
      color: 0xffffff,
      depthWrite: false,
      depthTest: false,
      toneMapped: false,
      fog: false,
    });
    this.ptHaloMat = new THREE.MeshBasicMaterial({
      map: null,
      color: 0x2b3346,
      depthWrite: false,
      depthTest: false,
      toneMapped: false,
      fog: false,
    });

    this.ptHalo = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), this.ptHaloMat);
    this.ptHalo.renderOrder = -31;
    this.ptHalo.frustumCulled = false;
    this.ptMain = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), this.ptMainMat);
    this.ptMain.renderOrder = -30;
    this.ptMain.frustumCulled = false;

    this.passthroughGroup.add(this.ptHalo, this.ptMain);
    this.passthroughGroup.visible = false;

    // co chowamy w MR, żeby nie pływało po prawdziwym pokoju
    this.clutter = [this.dome, floor, this.particles, ...this.group.children.filter((o) => o !== this.dome && o !== floor && o !== this.particles)];
  }

  setPassthrough(
    enabled: boolean,
    texture: THREE.Texture | null,
    camFovDeg?: number,
    rotate: 0 | 90 | 180 | 270 = 0,
  ): void {
    if (typeof camFovDeg === 'number') this.camFov = camFovDeg;
    if (rotate !== this.ptRotate) {
      this.ptRotate = rotate;
      this.lastVideoKey = '';
    }

    if (texture) {
      if (this.ptMainMat.map !== texture) {
        this.ptMainMat.map = texture;
        this.ptMainMat.needsUpdate = true;
      }
      if (this.ptHaloMat.map !== texture) {
        this.ptHaloMat.map = texture;
        this.ptHaloMat.needsUpdate = true;
      }
    }

    const active = enabled && !!this.ptMainMat.map;
    this.passthroughGroup.visible = active;
    for (const obj of this.clutter) obj.visible = !active;
    if (!active) this.dome.visible = true;
    this.layoutPassthrough();
  }

  /** Ustawia rozmiar obrazu z kamery tak, by zgadzał się z jej polem widzenia. */
  private layoutPassthrough(): void {
    if (!this.passthroughGroup.visible) return;
    const image = this.ptMainMat.map?.image as HTMLVideoElement | undefined;
    const vw = image?.videoWidth ?? 0;
    const vh = image?.videoHeight ?? 0;
    if (!vw || !vh) return;

    const key = `${vw}x${vh}:${this.camFov}:${this.ptRotate}`;
    if (key === this.lastVideoKey) return;
    this.lastVideoKey = key;

    const sideways = this.ptRotate === 90 || this.ptRotate === 270;
    const d = 1.6;
    const hV = 2 * d * Math.tan(THREE.MathUtils.degToRad(this.camFov) / 2);
    const wV = hV * (sideways ? vh / vw : vw / vh);

    this.ptMain.position.set(0, 0, -d);
    this.ptMain.scale.set(wV, hV, 1);
    this.ptMain.rotation.z = -THREE.MathUtils.degToRad(this.ptRotate);
    this.ptHalo.rotation.z = this.ptMain.rotation.z;

    // obrzeże: ten sam obraz, większy i przyciemniony — udaje kątem oka
    const k = 2.9;
    this.ptHalo.position.set(0, 0, -d - 0.06);
    this.ptHalo.scale.set(wV * k, hV * k, 1);
  }

  update(dt: number, t: number): void {
    if (this.passthroughGroup.visible) this.layoutPassthrough();
    this.gridMat.uniforms.uTime.value = t;
    this.particles.rotation.y += dt * 0.02;
    const arr = this.particles.geometry.getAttribute('position') as THREE.BufferAttribute;
    const data = arr.array as Float32Array;
    for (let i = 0; i < data.length; i += 3) {
      data[i + 1] += dt * 0.08;
      if (data[i + 1] > 7.5) data[i + 1] = -1.2;
    }
    arr.needsUpdate = true;
    void this.particlesBase;
  }

  dispose(): void {
    this.group.traverse((obj) => {
      const mesh = obj as THREE.Mesh;
      if (mesh.geometry) mesh.geometry.dispose();
      const mat = mesh.material as THREE.Material | THREE.Material[] | undefined;
      if (Array.isArray(mat)) mat.forEach((m) => m.dispose());
      else mat?.dispose();
    });
  }
}
