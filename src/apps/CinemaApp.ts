import * as THREE from 'three';
import { C, alpha } from '../ui/theme';
import { button, card, icon, panelChrome, pill, text, wrapText } from '../ui/draw';
import type { Panel } from '../ui/Panel';
import type { AppHost, VrApp } from './types';
import { mediaLibrary, type MediaItem } from '../core/media';
import { getThumb } from '../core/thumbs';
import { sfx } from '../core/sfx';

const VIDEO_AREA = { x: 24, y: 130, w: 1500, h: 660 };
const MAX_W = 1.66;
const MAX_H = 0.73;

function fmt(sec: number): string {
  if (!Number.isFinite(sec) || sec < 0) return '0:00';
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
}

/** Kino: filmy 2D na ekranie w panelu oraz wideo 360° wokół głowy. */
export class CinemaApp implements VrApp {
  id = 'cinema';
  title = 'Kino';
  subtitle = 'Filmy 2D i 360°';
  icon = 'play' as const;
  accent = C.cyan;
  group = new THREE.Group();
  hint: string | null = 'Wybierz film, potem steruj przyciskami';

  private mode: 'list' | 'player' = 'list';
  private current: MediaItem | null = null;
  private video: HTMLVideoElement | null = null;
  private texture: THREE.VideoTexture | null = null;
  private screen: THREE.Mesh;
  private backdrop: THREE.Mesh;
  private sphere: THREE.Mesh;
  private is360 = false;
  private soundOn = true;
  private uiTick = 0;

  constructor(private host: AppHost, private panel: Panel) {
    this.screen = new THREE.Mesh(
      new THREE.PlaneGeometry(1, 1),
      new THREE.MeshBasicMaterial({ color: 0x000000, toneMapped: false }),
    );
    this.screen.visible = false;
    this.screen.position.set(0, 0.029, 0.008);
    this.panel.group.add(this.screen);

    this.backdrop = new THREE.Mesh(
      new THREE.PlaneGeometry(VIDEO_AREA.w / 900, VIDEO_AREA.h / 900),
      new THREE.MeshBasicMaterial({ color: 0x04060d, toneMapped: false, fog: false }),
    );
    this.backdrop.position.set(0, 0.029, 0.004);
    this.backdrop.visible = false;
    this.panel.group.add(this.backdrop);

    this.sphere = new THREE.Mesh(
      new THREE.SphereGeometry(30, 48, 32),
      new THREE.MeshBasicMaterial({ color: 0x000000, side: THREE.BackSide, toneMapped: false, fog: false }),
    );
    this.sphere.visible = false;
    this.group.add(this.sphere);
  }

  enter(): void {
    this.mode = 'list';
    this.panel.markDirty();
  }

  exit(): void {
    this.stopVideo();
    this.screen.visible = false;
    this.backdrop.visible = false;
    this.sphere.visible = false;
  }

  private stopVideo(): void {
    if (this.video) {
      this.video.pause();
      this.video.removeAttribute('src');
      this.video.load();
      this.video = null;
    }
    this.texture?.dispose();
    this.texture = null;
    (this.screen.material as THREE.MeshBasicMaterial).map = null;
    (this.sphere.material as THREE.MeshBasicMaterial).map = null;
    (this.screen.material as THREE.MeshBasicMaterial).needsUpdate = true;
    (this.sphere.material as THREE.MeshBasicMaterial).needsUpdate = true;
    this.current = null;
  }

  private openVideo(item: MediaItem): void {
    this.stopVideo();
    this.current = item;
    this.mode = 'player';

    const video = document.createElement('video');
    video.src = item.url;
    video.loop = false;
    video.playsInline = true;
    video.preload = 'auto';
    video.muted = true;
    video.setAttribute('playsinline', 'true');
    this.video = video;

    const texture = new THREE.VideoTexture(video);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.minFilter = THREE.LinearFilter;
    texture.magFilter = THREE.LinearFilter;
    texture.generateMipmaps = false;
    this.texture = texture;

    const mat = this.screen.material as THREE.MeshBasicMaterial;
    mat.map = texture;
    mat.color.set(0xffffff);
    mat.needsUpdate = true;

    const sphereMat = this.sphere.material as THREE.MeshBasicMaterial;
    sphereMat.map = texture;
    sphereMat.color.set(0xffffff);
    sphereMat.needsUpdate = true;

    this.fitScreen(item.aspect);
    video.addEventListener('loadedmetadata', () => {
      this.fitScreen(video.videoWidth / Math.max(1, video.videoHeight));
      this.panel.markDirty();
    });
    video.addEventListener('error', () => {
      this.host.toast('Nie udało się odtworzyć tego pliku.');
      sfx.error();
    });

    // autoplay: zaczynamy od wyciszenia (dozwolone), potem włączamy dźwięk
    void video.play().then(() => {
      window.setTimeout(() => {
        if (this.video === video && this.soundOn) video.muted = false;
      }, 350);
    }).catch(() => {
      this.host.toast('Dotknij „Play”.');
    });

    this.screen.visible = !this.is360;
    this.backdrop.visible = true;
    this.sphere.visible = this.is360;
    this.panel.markDirty();
  }

  private fitScreen(aspect: number): void {
    const a = Number.isFinite(aspect) && aspect > 0 ? aspect : 16 / 9;
    let w = MAX_W;
    let h = w / a;
    if (h > MAX_H) {
      h = MAX_H;
      w = h * a;
    }
    this.screen.scale.set(w, h, 1);
  }

  private togglePlay(): void {
    if (!this.video) return;
    if (this.video.paused) {
      void this.video.play().catch(() => undefined);
      if (this.soundOn) this.video.muted = false;
    } else {
      this.video.pause();
    }
    sfx.click();
    this.panel.markDirty();
  }

  update(dt: number): void {
    if (this.mode !== 'player' || !this.video) return;

    this.uiTick += dt;
    // pasek postępu odświeżamy ~5 razy na sekundę (oszczędzamy GPU)
    if (this.uiTick > 0.2) {
      this.uiTick = 0;
      this.panel.markDirty();
    }
    const player = this.mode === 'player';
    this.screen.visible = player && !this.is360;
    this.backdrop.visible = player;
    this.sphere.visible = player && this.is360;
  }

  draw(panel: Panel): void {
    const { ctx } = panel;
    const W = panel.pxW;
    const H = panel.pxH;
    panelChrome(ctx, W, H, { accent: this.accent });

    if (this.mode === 'list') {
      this.drawList(panel, W, H);
    } else {
      this.drawPlayer(panel, W, H);
    }
  }

  private drawList(panel: Panel, W: number, H: number): void {
    const { ctx } = panel;
    text(ctx, 'Kino', 40, 76, { size: 40, weight: 700 });
    text(ctx, 'Wybierz film z pamięci telefonu', 40, 112, { size: 21, color: C.muted });

    button(ctx, W - 260, 50, 220, 62, 'Dodaj pliki', {
      hover: panel.hoverId === 'cinema:add',
      accent: this.accent,
      size: 21,
    });
    panel.hit('cinema:add', W - 260, 50, 220, 62);

    button(ctx, W - 470, 50, 190, 62, 'Wstecz', {
      hover: panel.hoverId === 'cinema:back',
      accent: C.violet,
      size: 21,
    });
    panel.hit('cinema:back', W - 470, 50, 190, 62);

    const videos = mediaLibrary.videos;
    if (videos.length === 0) {
      const bx = 40;
      const by = 170;
      const bw = W - 80;
      const bh = 560;
      card(ctx, bx, by, bw, bh, { fill: 'rgba(255,255,255,0.03)' });
      icon(ctx, 'play', bx + 90, by + 130, 84, alpha(this.accent, 0.8), 4);
      text(ctx, 'Brak filmów w bibliotece', bx + 170, by + 120, {
        size: 34,
        weight: 700,
      });
      wrapText(
        ctx,
        'Dodaj filmy z telefonu ZANIM założysz headset — dotknij „Dodaj pliki”. Obsługiwane są MP4, WebM i MKV obsługiwane przez telefon. Filmy 360° (equirectangular) też działają.',
        bx + 170,
        by + 170,
        bw - 320,
        { size: 24, color: C.textDim, lineHeight: 36, maxLines: 4 },
      );
      button(ctx, bx + 170, by + 330, 300, 78, 'Dodaj filmy', {
        hover: panel.hoverId === 'cinema:add2',
        primary: true,
        accent: this.accent,
        size: 24,
      });
      panel.hit('cinema:add2', bx + 170, by + 330, 300, 78);

      if (mediaLibrary.images.length > 0) {
        text(ctx, 'Masz zdjęcia — otwórz Galerię, żeby je zobaczyć w 360°.', bx + 170, by + 470, {
          size: 21,
          color: C.muted,
        });
      }
      return;
    }

    const margin = 40;
    const gap = 24;
    const cols = 3;
    const rows = 2;
    const tw = (W - margin * 2 - gap * (cols - 1)) / cols;
    const th = (H - 200 - 120 - gap) / rows;

    videos.slice(0, cols * rows).forEach((item, i) => {
      const col = i % cols;
      const row = Math.floor(i / cols);
      const x = margin + col * (tw + gap);
      const y = 170 + row * (th + gap);
      const hover = panel.hoverId === `cinema:item:${item.id}`;

      card(ctx, x, y, tw, th, { hover, accent: this.accent, glow: hover ? 22 : 0 });
      const thumb = getThumb(item.thumb);
      const imgH = th - 96;
      if (thumb) {
        ctx.save();
        ctx.beginPath();
        ctx.roundRect(x + 12, y + 12, tw - 24, imgH, 14);
        ctx.clip();
        const scale = Math.max((tw - 24) / thumb.naturalWidth, imgH / thumb.naturalHeight);
        ctx.drawImage(
          thumb,
          x + 12 + (tw - 24 - thumb.naturalWidth * scale) / 2,
          y + 12 + (imgH - thumb.naturalHeight * scale) / 2,
          thumb.naturalWidth * scale,
          thumb.naturalHeight * scale,
        );
        ctx.restore();
      } else {
        card(ctx, x + 12, y + 12, tw - 24, imgH, { fill: 'rgba(255,255,255,0.05)', r: 14 });
      }

      // ikona play na miniaturce
      ctx.save();
      ctx.globalAlpha = 0.9;
      ctx.fillStyle = 'rgba(5,7,15,0.55)';
      ctx.beginPath();
      ctx.arc(x + tw / 2, y + 12 + imgH / 2, 34, 0, Math.PI * 2);
      ctx.fill();
      icon(ctx, 'play', x + tw / 2, y + 12 + imgH / 2, 30, '#ffffff');
      ctx.restore();

      text(ctx, item.name.slice(0, 34), x + 18, y + th - 44, {
        size: 21,
        weight: 600,
        color: C.text,
      });
      text(ctx, item.panorama ? '360° · dotknij, aby odtworzyć' : 'dotknij, aby odtworzyć', x + 18, y + th - 16, {
        size: 17,
        color: C.muted,
      });

      panel.hit(`cinema:item:${item.id}`, x, y, tw, th);
    });
  }

  private drawPlayer(panel: Panel, W: number, H: number): void {
    const { ctx } = panel;
    const video = this.video;
    const duration = video?.duration ?? 0;
    const time = video?.currentTime ?? 0;
    const playing = video ? !video.paused : false;

    text(ctx, this.current?.name ?? 'Film', 40, 74, { size: 34, weight: 700 });
    text(ctx, this.is360 ? 'Tryb 360° — rozglądaj się' : 'Ekran kinowy', 40, 108, {
      size: 19,
      color: C.muted,
    });

    button(ctx, W - 230, 46, 190, 60, 'Wstecz', {
      hover: panel.hoverId === 'cinema:back',
      accent: C.violet,
      size: 20,
    });
    panel.hit('cinema:back', W - 230, 46, 190, 60);

    // obszar „ekranu” — dziura w panelu, żeby widzieć teksturę wideo
    ctx.save();
    ctx.beginPath();
    ctx.roundRect(VIDEO_AREA.x, VIDEO_AREA.y, VIDEO_AREA.w, VIDEO_AREA.h, 18);
    ctx.clip();
    ctx.clearRect(VIDEO_AREA.x, VIDEO_AREA.y, VIDEO_AREA.w, VIDEO_AREA.h);
    ctx.restore();
    ctx.save();
    ctx.beginPath();
    ctx.roundRect(VIDEO_AREA.x, VIDEO_AREA.y, VIDEO_AREA.w, VIDEO_AREA.h, 18);
    ctx.strokeStyle = alpha(C.cyan, 0.22);
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.restore();

    if (this.is360) {
      text(ctx, 'Obraz 360° jest wokół Ciebie — obróć głowę', VIDEO_AREA.x + VIDEO_AREA.w / 2, VIDEO_AREA.y + VIDEO_AREA.h / 2, {
        size: 26,
        weight: 600,
        color: C.textDim,
        align: 'center',
        baseline: 'middle',
      });
    }

    // pasek sterowania
    const cy = 806;
    const playBtn = { x: 40, y: cy, w: 116, h: 84 };
    button(ctx, playBtn.x, playBtn.y, playBtn.w, playBtn.h, '', {
      hover: panel.hoverId === 'cinema:play',
      accent: this.accent,
    });
    icon(ctx, playing ? 'pause' : 'play', playBtn.x + playBtn.w / 2, playBtn.y + playBtn.h / 2, 34, C.text);
    panel.hit('cinema:play', playBtn.x, playBtn.y, playBtn.w, playBtn.h);

    const mk = (x: number, label: string, id: string) => {
      button(ctx, x, cy + 8, 104, 68, label, {
        hover: panel.hoverId === id,
        accent: C.violet,
        size: 20,
      });
      panel.hit(id, x, cy + 8, 104, 68);
    };
    mk(176, '-10s', 'cinema:back10');
    mk(292, '+10s', 'cinema:fw10');

    text(ctx, `${fmt(time)} / ${fmt(duration)}`, 420, cy + 52, {
      size: 24,
      weight: 600,
      color: C.text,
    });

    // pasek postępu
    const barX = 640;
    const barW = 520;
    const barY = cy + 50;
    const progress = duration > 0 ? Math.min(1, time / duration) : 0;
    card(ctx, barX, barY - 5, barW, 14, { fill: 'rgba(255,255,255,0.1)', r: 7 });
    card(ctx, barX, barY - 5, Math.max(14, barW * progress), 14, {
      fill: alpha(this.accent, 0.95),
      r: 7,
    });
    const hx = barX + barW * progress;
    ctx.save();
    ctx.fillStyle = '#ffffff';
    ctx.shadowColor = alpha(this.accent, 0.9);
    ctx.shadowBlur = 16;
    ctx.beginPath();
    ctx.arc(hx, barY + 2, 13, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
    panel.hit('cinema:seek', barX - 20, barY - 30, barW + 40, 64);

    const toggle = (x: number, label: string, id: string, active: boolean) => {
      button(ctx, x, cy + 8, 150, 68, label, {
        hover: panel.hoverId === id,
        accent: active ? C.lime : C.muted,
        size: 19,
      });
      panel.hit(id, x, cy + 8, 150, 68);
    };
    toggle(1180, this.is360 ? '360°' : '2D', 'cinema:mode', this.is360);
    toggle(1348, this.soundOn ? 'Dźwięk' : 'Cisza', 'cinema:sound', this.soundOn);

    text(ctx, 'Szczypnij przycisk lub przeciągnij wzrok na pasek, aby przewinąć', 40, H - 26, {
      size: 17,
      color: C.muted,
    });
  }

  click(panel: Panel, x: number, y: number, id: string | null): void {
    if (id === 'cinema:back') {
      if (this.mode === 'player') {
        this.stopVideo();
        this.mode = 'list';
        this.hint = 'Wybierz film, potem steruj przyciskami';
        this.panel.markDirty();
        sfx.back();
      } else {
        this.host.back();
      }
      return;
    }
    if (id === 'cinema:add' || id === 'cinema:add2') {
      this.host.requestFiles();
      return;
    }
    if (id?.startsWith('cinema:item:')) {
      const item = mediaLibrary.get(id.slice('cinema:item:'.length));
      if (item) this.openVideo(item);
      return;
    }

    if (id === 'cinema:play') {
      this.togglePlay();
      return;
    }
    if (id === 'cinema:back10' || id === 'cinema:fw10') {
      if (this.video) {
        const delta = id === 'cinema:back10' ? -10 : 10;
        this.video.currentTime = Math.max(0, Math.min(this.video.duration || 0, this.video.currentTime + delta));
        sfx.click();
        this.panel.markDirty();
      }
      return;
    }
    if (id === 'cinema:seek') {
      if (this.video && this.video.duration > 0) {
        const t = Math.min(1, Math.max(0, (x - 640) / 520));
        this.video.currentTime = t * this.video.duration;
        sfx.click();
        this.panel.markDirty();
      }
      return;
    }
    if (id === 'cinema:mode') {
      this.is360 = !this.is360;
      this.screen.visible = !this.is360;
      this.sphere.visible = this.is360;
      sfx.toggle(this.is360);
      this.panel.markDirty();
      return;
    }
    if (id === 'cinema:sound') {
      this.soundOn = !this.soundOn;
      if (this.video) this.video.muted = !this.soundOn;
      sfx.toggle(this.soundOn);
      this.panel.markDirty();
      return;
    }
    void y;
  }
}
