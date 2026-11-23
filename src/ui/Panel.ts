import * as THREE from 'three';

export interface Hit {
  id: string;
  x: number;
  y: number;
  w: number;
  h: number;
}

export type DrawFn = (panel: Panel) => void;

/**
 * Panel = canvas 2D renderowany jako tekstura na płaszczyźnie w 3D.
 * Rejestruje „hity” (prostokąty klikalne) w układzie pikseli canvasu.
 */
export class Panel {
  readonly canvas: HTMLCanvasElement;
  readonly ctx: CanvasRenderingContext2D;
  readonly texture: THREE.CanvasTexture;
  readonly mesh: THREE.Mesh;
  readonly group: THREE.Group;
  readonly widthM: number;
  readonly heightM: number;
  readonly pxW: number;
  readonly pxH: number;

  hits: Hit[] = [];
  hoverId: string | null = null;
  dirty = true;
  draw: DrawFn;
  private lastDraw = 0;
  private maxPx = 2048;

  constructor(opts: {
    widthM: number;
    heightM: number;
    pxPerM?: number;
    draw: DrawFn;
    doubleSided?: boolean;
  }) {
    this.widthM = opts.widthM;
    this.heightM = opts.heightM;
    const pxPerM = opts.pxPerM ?? 900;
    this.pxW = Math.min(this.maxPx, Math.round(opts.widthM * pxPerM));
    this.pxH = Math.min(this.maxPx, Math.round(opts.heightM * pxPerM));

    this.canvas = document.createElement('canvas');
    this.canvas.width = this.pxW;
    this.canvas.height = this.pxH;
    const ctx = this.canvas.getContext('2d', { alpha: true });
    if (!ctx) throw new Error('Brak kontekstu 2D dla panelu');
    this.ctx = ctx;

    this.texture = new THREE.CanvasTexture(this.canvas);
    this.texture.colorSpace = THREE.SRGBColorSpace;
    this.texture.anisotropy = 8;
    this.texture.minFilter = THREE.LinearMipmapLinearFilter;
    this.texture.magFilter = THREE.LinearFilter;
    this.texture.generateMipmaps = true;

    const material = new THREE.MeshBasicMaterial({
      map: this.texture,
      transparent: true,
      depthWrite: false,
      side: opts.doubleSided ? THREE.DoubleSide : THREE.FrontSide,
      toneMapped: false,
    });

    this.mesh = new THREE.Mesh(new THREE.PlaneGeometry(this.widthM, this.heightM), material);
    this.mesh.userData.panel = this;
    this.group = new THREE.Group();
    this.group.add(this.mesh);
    this.draw = opts.draw;
  }

  /** Zarejestruj klikalny obszar (w pikselach canvasu). */
  hit(id: string, x: number, y: number, w: number, h: number): void {
    this.hits.push({ id, x, y, w, h });
  }

  /** Obszar obejmujący cały panel — do „tapnij gdziekolwiek”. */
  hitAll(id: string): void {
    this.hit(id, 0, 0, this.pxW, this.pxH);
  }

  hitAt(x: number, y: number): Hit | null {
    // od ostatniego dodanego — pozwala nakładać mniejsze obszary na większe
    for (let i = this.hits.length - 1; i >= 0; i--) {
      const h = this.hits[i];
      if (x >= h.x && x <= h.x + h.w && y >= h.y && y <= h.y + h.h) return h;
    }
    return null;
  }

  /** Zamiana uv z trafienia w promień na piksele canvasu. */
  uvToPx(u: number, v: number): { x: number; y: number } {
    return { x: u * this.pxW, y: (1 - v) * this.pxH };
  }

  markDirty(): void {
    this.dirty = true;
  }

  setHover(id: string | null): boolean {
    if (this.hoverId === id) return false;
    this.hoverId = id;
    this.dirty = true;
    return true;
  }

  /** Przerysowanie z limitem ~30 Hz, tylko gdy coś się zmieniło. */
  flush(now: number, force = false): void {
    if (!this.dirty && !force) return;
    if (!force && now - this.lastDraw < 33) return;
    this.lastDraw = now;
    this.dirty = false;
    this.hits.length = 0;
    this.ctx.clearRect(0, 0, this.pxW, this.pxH);
    this.draw(this);
    this.texture.needsUpdate = true;
  }

  dispose(): void {
    this.texture.dispose();
    (this.mesh.material as THREE.Material).dispose();
    this.mesh.geometry.dispose();
  }
}
