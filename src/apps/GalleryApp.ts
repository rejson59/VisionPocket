import * as THREE from 'three';
import { C, alpha } from '../ui/theme';
import { button, card, icon, panelChrome, text, wrapText } from '../ui/draw';
import type { Panel } from '../ui/Panel';
import type { AppHost, VrApp } from './types';
import { mediaLibrary, type MediaItem } from '../core/media';
import { getThumb } from '../core/thumbs';
import { sfx } from '../core/sfx';

const VIEW_MAX_W = 1.62;
const VIEW_MAX_H = 0.72;

/** Galeria zdjęć + podgląd panoram 360°. */
export class GalleryApp implements VrApp {
  id = 'gallery';
  title = 'Galeria';
  subtitle = 'Zdjęcia i panoramy';
  icon = 'photo' as const;
  accent = C.violet;
  group = new THREE.Group();
  hint: string | null = 'Wybierz zdjęcie — szczypnij, aby powiększyć';

  private mode: 'grid' | 'view' = 'grid';
  private current: MediaItem | null = null;
  private page = 0;
  private sphere = false;
  private imageMesh: THREE.Mesh;
  private backdrop: THREE.Mesh;
  private envSphere: THREE.Mesh;
  private loader = new THREE.TextureLoader();
  private texture: THREE.Texture | null = null;
  private perPage = 8;

  constructor(private host: AppHost, private panel: Panel) {
    this.imageMesh = new THREE.Mesh(
      new THREE.PlaneGeometry(1, 1),
      new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, toneMapped: false }),
    );
    this.imageMesh.visible = false;
    this.imageMesh.position.set(0, 0.029, 0.008);
    this.panel.group.add(this.imageMesh);

    this.backdrop = new THREE.Mesh(
      new THREE.PlaneGeometry(1500 / 900, 660 / 900),
      new THREE.MeshBasicMaterial({ color: 0x04060d, toneMapped: false, fog: false }),
    );
    this.backdrop.position.set(0, 0.029, 0.004);
    this.backdrop.visible = false;
    this.panel.group.add(this.backdrop);

    this.envSphere = new THREE.Mesh(
      new THREE.SphereGeometry(30, 48, 32),
      new THREE.MeshBasicMaterial({ color: 0xffffff, side: THREE.BackSide, toneMapped: false, fog: false }),
    );
    this.envSphere.visible = false;
    this.group.add(this.envSphere);
  }

  enter(): void {
    this.mode = 'grid';
    this.panel.markDirty();
  }

  exit(): void {
    this.closeView();
  }

  private closeView(): void {
    this.imageMesh.visible = false;
    this.backdrop.visible = false;
    this.envSphere.visible = false;
    this.texture?.dispose();
    this.texture = null;
    (this.imageMesh.material as THREE.MeshBasicMaterial).map = null;
    (this.envSphere.material as THREE.MeshBasicMaterial).map = null;
    this.current = null;
    this.mode = 'grid';
  }

  private openImage(item: MediaItem): void {
    this.closeView();
    this.mode = 'view';
    this.current = item;
    this.sphere = item.panorama;
    this.texture?.dispose();
    this.texture = this.loader.load(item.url, (tex) => {
      tex.colorSpace = THREE.SRGBColorSpace;
      const aspect = tex.image.width / Math.max(1, tex.image.height);
      let w = VIEW_MAX_W;
      let h = w / aspect;
      if (h > VIEW_MAX_H) {
        h = VIEW_MAX_H;
        w = h * aspect;
      }
      this.imageMesh.scale.set(w, h, 1);
      this.panel.markDirty();
    });
    this.texture.colorSpace = THREE.SRGBColorSpace;
    (this.imageMesh.material as THREE.MeshBasicMaterial).map = this.texture;
    (this.imageMesh.material as THREE.MeshBasicMaterial).needsUpdate = true;
    (this.envSphere.material as THREE.MeshBasicMaterial).map = this.texture;
    (this.envSphere.material as THREE.MeshBasicMaterial).needsUpdate = true;

    this.imageMesh.visible = !this.sphere;
    this.backdrop.visible = true;
    this.envSphere.visible = this.sphere;
    sfx.open();
    this.panel.markDirty();
  }

  draw(panel: Panel): void {
    const { ctx } = panel;
    const W = panel.pxW;
    const H = panel.pxH;
    panelChrome(ctx, W, H, { accent: this.accent });

    if (this.mode === 'view') {
      this.drawView(panel, W, H);
      return;
    }

    text(ctx, 'Galeria', 40, 76, { size: 40, weight: 700 });
    text(ctx, `${mediaLibrary.images.length} zdjęć w bibliotece`, 40, 112, {
      size: 21,
      color: C.muted,
    });

    button(ctx, W - 260, 50, 220, 62, 'Dodaj pliki', {
      hover: panel.hoverId === 'gallery:add',
      accent: this.accent,
      size: 21,
    });
    panel.hit('gallery:add', W - 260, 50, 220, 62);

    button(ctx, W - 470, 50, 190, 62, 'Wstecz', {
      hover: panel.hoverId === 'gallery:home',
      accent: C.cyan,
      size: 21,
    });
    panel.hit('gallery:home', W - 470, 50, 190, 62);

    const items = mediaLibrary.images;
    const margin = 40;
    const gap = 20;
    const cols = 4;
    const rows = 2;
    const tw = (W - margin * 2 - gap * (cols - 1)) / cols;
    const th = (H - 200 - 150 - gap) / rows;

    const pageItems = items.slice(this.page * 8, this.page * 8 + 8);
    if (pageItems.length === 0 && items.length === 0) {
      card(ctx, 40, 170, W - 80, 520, { fill: 'rgba(255,255,255,0.03)' });
      icon(ctx, 'photo', 130, 300, 84, alpha(this.accent, 0.8), 4);
      text(ctx, 'Brak zdjęć', 210, 290, { size: 34, weight: 700 });
      wrapText(
        ctx,
        'Dodaj zdjęcia z telefonu przyciskiem „Dodaj pliki”. Panoramy (zdjęcia szersze niż 1.85:1) otworzą się jako sfera 360° wokół Ciebie.',
        210,
        340,
        W - 420,
        { size: 24, color: C.textDim, lineHeight: 36, maxLines: 3 },
      );
      return;
    }

    pageItems.forEach((item, i) => {
      const col = i % cols;
      const row = Math.floor(i / cols);
      const x = margin + col * (tw + gap);
      const y = 170 + row * (th + gap);
      const id = `gallery:item:${item.id}`;
      const hover = panel.hoverId === id;
      card(ctx, x, y, tw, th, { hover, accent: this.accent, glow: hover ? 22 : 0 });

      const thumb = getThumb(item.thumb);
      const inner = { x: x + 10, y: y + 10, w: tw - 20, h: th - 20 };
      if (thumb) {
        ctx.save();
        ctx.beginPath();
        ctx.roundRect(inner.x, inner.y, inner.w, inner.h, 14);
        ctx.clip();
        const scale = Math.max(inner.w / thumb.naturalWidth, inner.h / thumb.naturalHeight);
        ctx.drawImage(
          thumb,
          inner.x + (inner.w - thumb.naturalWidth * scale) / 2,
          inner.y + (inner.h - thumb.naturalHeight * scale) / 2,
          thumb.naturalWidth * scale,
          thumb.naturalHeight * scale,
        );
        ctx.restore();
      } else {
        card(ctx, inner.x, inner.y, inner.w, inner.h, { fill: 'rgba(255,255,255,0.05)', r: 14 });
      }

      if (item.panorama) {
        ctx.save();
        ctx.fillStyle = 'rgba(5,7,15,0.7)';
        ctx.beginPath();
        ctx.roundRect(x + tw - 96, y + 18, 78, 32, 16);
        ctx.fill();
        text(ctx, '360°', x + tw - 57, y + 40, {
          size: 17,
          weight: 700,
          color: C.lime,
          align: 'center',
        });
        ctx.restore();
      }

      panel.hit(id, x, y, tw, th);
    });

    // pager
    const pages = Math.max(1, Math.ceil(items.length / this.perPage));
    if (pages > 1) {
      button(ctx, W / 2 - 190, H - 96, 120, 56, '‹', {
        hover: panel.hoverId === 'gallery:prev',
        accent: this.accent,
        size: 26,
        disabled: this.page === 0,
      });
      panel.hit('gallery:prev', W / 2 - 190, H - 96, 120, 56);
      text(ctx, `${this.page + 1} / ${pages}`, W / 2, H - 60, {
        size: 22,
        weight: 600,
        color: C.textDim,
        align: 'center',
      });
      button(ctx, W / 2 + 70, H - 96, 120, 56, '›', {
        hover: panel.hoverId === 'gallery:next',
        accent: this.accent,
        size: 26,
        disabled: this.page >= pages - 1,
      });
      panel.hit('gallery:next', W / 2 + 70, H - 96, 120, 56);
    }
  }

  private drawView(panel: Panel, W: number, H: number): void {
    const { ctx } = panel;
    text(ctx, this.current?.name ?? 'Zdjęcie', 40, 74, { size: 32, weight: 700 });
    text(
      ctx,
      this.sphere ? 'Panorama 360° — rozglądaj się dookoła' : 'Podgląd zdjęcia',
      40,
      108,
      { size: 19, color: C.muted },
    );

    button(ctx, W - 230, 46, 190, 60, 'Wstecz', {
      hover: panel.hoverId === 'gallery:close',
      accent: C.cyan,
      size: 20,
    });
    panel.hit('gallery:close', W - 230, 46, 190, 60);

    ctx.save();
    ctx.beginPath();
    ctx.roundRect(24, 130, W - 48, 660, 18);
    ctx.clip();
    ctx.clearRect(24, 130, W - 48, 660);
    ctx.restore();
    ctx.save();
    ctx.beginPath();
    ctx.roundRect(24, 130, W - 48, 660, 18);
    ctx.strokeStyle = alpha(this.accent, 0.22);
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.restore();

    if (this.sphere) {
      text(ctx, 'Obraz jest wokół Ciebie', W / 2, 460, {
        size: 26,
        weight: 600,
        color: C.textDim,
        align: 'center',
        baseline: 'middle',
      });
    }

    const by = 810;
    button(ctx, 40, by, 240, 76, this.sphere ? 'Pokaż płasko' : 'Pokaż 360°', {
      hover: panel.hoverId === 'gallery:mode',
      accent: this.sphere ? C.lime : C.violet,
      size: 20,
    });
    panel.hit('gallery:mode', 40, by, 240, 76);

    button(ctx, 300, by, 200, 76, 'Następne', {
      hover: panel.hoverId === 'gallery:nextimg',
      accent: C.violet,
      size: 20,
    });
    panel.hit('gallery:nextimg', 300, by, 200, 76);

    button(ctx, W - 240, by, 200, 76, 'Usuń z widoku', {
      hover: panel.hoverId === 'gallery:back',
      accent: C.cyan,
      size: 20,
    });
    panel.hit('gallery:back', W - 240, by, 200, 76);
  }

  click(panel: Panel, x: number, y: number, id: string | null): void {
    void panel;
    void x;
    void y;
    if (!id) return;

    if (id === 'gallery:add') {
      this.host.requestFiles();
      return;
    }
    if (id === 'gallery:home') {
      this.host.back();
      return;
    }
    if (id === 'gallery:prev') {
      this.page = Math.max(0, this.page - 1);
      sfx.click();
      this.panel.markDirty();
      return;
    }
    if (id === 'gallery:next') {
      this.page++;
      sfx.click();
      this.panel.markDirty();
      return;
    }
    if (id.startsWith('gallery:item:')) {
      const item = mediaLibrary.get(id.slice('gallery:item:'.length));
      if (item) this.openImage(item);
      return;
    }
    if (id === 'gallery:close') {
      this.closeView();
      sfx.back();
      this.panel.markDirty();
      return;
    }
    if (id === 'gallery:mode') {
      this.sphere = !this.sphere;
      this.imageMesh.visible = !this.sphere;
      this.backdrop.visible = true;
      this.envSphere.visible = this.sphere;
      sfx.toggle(this.sphere);
      this.panel.markDirty();
      return;
    }
    if (id === 'gallery:nextimg') {
      const items = mediaLibrary.images;
      const index = this.current ? items.findIndex((i) => i.id === this.current!.id) : -1;
      const next = items[(index + 1) % Math.max(1, items.length)];
      if (next) this.openImage(next);
      return;
    }
    if (id === 'gallery:back') {
      this.host.back();
    }
  }
}
