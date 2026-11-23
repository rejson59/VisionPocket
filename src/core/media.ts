// Biblioteka mediów: pliki użytkownika + dwa demo generowane proceduralnie,
// żeby galeria i kino miały co pokazać od pierwszej sekundy.

export type MediaKind = 'image' | 'video';

export interface MediaItem {
  id: string;
  name: string;
  kind: MediaKind;
  url: string;
  thumb: string;
  aspect: number;
  panorama: boolean;
  demo?: boolean;
}

type Listener = () => void;

class MediaLibrary {
  items: MediaItem[] = [];
  private listeners = new Set<Listener>();
  private seq = 0;

  constructor() {
    this.items.push(makeDemoPanorama(), makeDemoPoster());
  }

  onChange(cb: Listener): () => void {
    this.listeners.add(cb);
    return () => this.listeners.delete(cb);
  }

  private emit(): void {
    for (const cb of this.listeners) cb();
  }

  get videos(): MediaItem[] {
    return this.items.filter((i) => i.kind === 'video');
  }

  get images(): MediaItem[] {
    return this.items.filter((i) => i.kind === 'image');
  }

  get(id: string): MediaItem | undefined {
    return this.items.find((i) => i.id === id);
  }

  async addFiles(files: FileList | File[]): Promise<number> {
    let added = 0;
    for (const file of Array.from(files)) {
      const isVideo = file.type.startsWith('video/');
      const isImage = file.type.startsWith('image/');
      if (!isVideo && !isImage) continue;

      const url = URL.createObjectURL(file);
      try {
        const meta = isVideo ? await probeVideo(url) : await probeImage(url);
        this.items.unshift({
          id: `f${++this.seq}`,
          name: file.name.replace(/\.[^.]+$/, ''),
          kind: isVideo ? 'video' : 'image',
          url,
          thumb: await makeThumb(url, isVideo),
          aspect: meta.aspect,
          panorama: !isVideo && meta.aspect > 1.85,
        });
        added++;
      } catch {
        URL.revokeObjectURL(url);
      }
    }
    if (added) this.emit();
    return added;
  }
}

function probeImage(url: string): Promise<{ aspect: number }> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve({ aspect: img.naturalWidth / Math.max(1, img.naturalHeight) });
    img.onerror = () => reject(new Error('image'));
    img.src = url;
  });
}

function probeVideo(url: string): Promise<{ aspect: number; duration: number }> {
  return new Promise((resolve, reject) => {
    const v = document.createElement('video');
    v.preload = 'metadata';
    v.muted = true;
    const done = () => {
      const aspect = v.videoWidth / Math.max(1, v.videoHeight);
      const duration = Number.isFinite(v.duration) ? v.duration : 0;
      v.removeAttribute('src');
      v.load();
      resolve({ aspect, duration });
    };
    v.onloadedmetadata = done;
    v.onerror = () => reject(new Error('video'));
    v.src = url;
  });
}

function makeThumb(url: string, isVideo: boolean): Promise<string> {
  return new Promise((resolve) => {
    const size = 480;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d');
    if (!ctx) return resolve(url);

    const finish = () => {
      try {
        resolve(canvas.toDataURL('image/jpeg', 0.72));
      } catch {
        resolve(url);
      }
    };

    if (isVideo) {
      const v = document.createElement('video');
      v.muted = true;
      v.preload = 'auto';
      v.crossOrigin = 'anonymous';
      v.onloadeddata = () => {
        try {
          v.currentTime = Math.min(1.2, (v.duration || 1) * 0.15);
        } catch {
          drawFitted(ctx, v, size, size);
          finish();
        }
      };
      v.onseeked = () => {
        drawFitted(ctx, v, size, size);
        finish();
      };
      v.onerror = finish;
      v.src = url;
      return;
    }

    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      drawFitted(ctx, img, size, size);
      finish();
    };
    img.onerror = finish;
    img.src = url;
  });
}

function drawFitted(
  ctx: CanvasRenderingContext2D,
  src: CanvasImageSource,
  w: number,
  h: number,
): void {
  const sw = (src as HTMLVideoElement).videoWidth || (src as HTMLImageElement).naturalWidth || w;
  const sh = (src as HTMLVideoElement).videoHeight || (src as HTMLImageElement).naturalHeight || h;
  const scale = Math.max(w / sw, h / sh);
  const dw = sw * scale;
  const dh = sh * scale;
  ctx.save();
  ctx.beginPath();
  ctx.roundRect(0, 0, w, h, 26);
  ctx.clip();
  ctx.drawImage(src, (w - dw) / 2, (h - dh) / 2, dw, dh);
  ctx.restore();
}

/** Proceduralna panorama 360° — żeby „Kino/Galeria” działały bez plików. */
function makeDemoPanorama(): MediaItem {
  const w = 2048;
  const h = 1024;
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d')!;

  const sky = ctx.createLinearGradient(0, 0, 0, h);
  sky.addColorStop(0, '#040616');
  sky.addColorStop(0.45, '#101a3d');
  sky.addColorStop(0.62, '#33305f');
  sky.addColorStop(0.72, '#7b3f6d');
  sky.addColorStop(0.78, '#ff7a4d');
  sky.addColorStop(0.84, '#1a1024');
  sky.addColorStop(1, '#05030c');
  ctx.fillStyle = sky;
  ctx.fillRect(0, 0, w, h);

  // gwiazdy
  for (let i = 0; i < 900; i++) {
    const x = Math.random() * w;
    const y = Math.random() * h * 0.55;
    const a = Math.random() * 0.8 + 0.2;
    const r = Math.random() * 1.6 + 0.4;
    ctx.fillStyle = `rgba(${200 + Math.random() * 55}, ${220 + Math.random() * 35}, 255, ${a})`;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fill();
  }

  // słońce
  const sunX = w * 0.68;
  const sunY = h * 0.76;
  const glow = ctx.createRadialGradient(sunX, sunY, 0, sunX, sunY, 420);
  glow.addColorStop(0, 'rgba(255, 226, 160, 0.95)');
  glow.addColorStop(0.25, 'rgba(255, 140, 80, 0.55)');
  glow.addColorStop(1, 'rgba(255, 90, 60, 0)');
  ctx.fillStyle = glow;
  ctx.beginPath();
  ctx.arc(sunX, sunY, 420, 0, Math.PI * 2);
  ctx.fill();

  // góry
  for (let layer = 0; layer < 3; layer++) {
    const base = h * (0.79 + layer * 0.025);
    const amp = 120 - layer * 34;
    ctx.fillStyle = ['#150f26', '#0e0a1c', '#070612'][layer];
    ctx.beginPath();
    ctx.moveTo(0, h);
    for (let x = 0; x <= w; x += 16) {
      const y =
        base -
        Math.abs(Math.sin(x * 0.0031 + layer * 2.1)) * amp -
        Math.abs(Math.sin(x * 0.011 + layer)) * amp * 0.35;
      ctx.lineTo(x, y);
    }
    ctx.lineTo(w, h);
    ctx.closePath();
    ctx.fill();
  }

  // siatka perspektywiczna na „ziemi”
  ctx.strokeStyle = 'rgba(53, 224, 255, 0.28)';
  ctx.lineWidth = 1.4;
  for (let i = -12; i <= 12; i++) {
    ctx.beginPath();
    ctx.moveTo(w / 2 + i * 24, h * 0.83);
    ctx.lineTo(w / 2 + i * 300, h);
    ctx.stroke();
  }
  for (let i = 0; i < 14; i++) {
    const y = h * 0.83 + Math.pow(i / 14, 1.9) * h * 0.17;
    ctx.globalAlpha = 1 - i / 16;
    ctx.beginPath();
    ctx.moveTo(0, y);
    ctx.lineTo(w, y);
    ctx.stroke();
  }
  ctx.globalAlpha = 1;

  const url = canvas.toDataURL('image/jpeg', 0.85);
  return {
    id: 'demo-pano',
    name: 'Demo · panorama 360°',
    kind: 'image',
    url,
    thumb: url,
    aspect: 2,
    panorama: true,
    demo: true,
  };
}

function makeDemoPoster(): MediaItem {
  const w = 1600;
  const h = 900;
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d')!;

  const bg = ctx.createLinearGradient(0, 0, w, h);
  bg.addColorStop(0, '#061226');
  bg.addColorStop(0.5, '#0d1b3a');
  bg.addColorStop(1, '#2a1145');
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, w, h);

  // poświata
  const g1 = ctx.createRadialGradient(w * 0.75, h * 0.3, 0, w * 0.75, h * 0.3, 520);
  g1.addColorStop(0, 'rgba(53, 224, 255, 0.35)');
  g1.addColorStop(1, 'rgba(53, 224, 255, 0)');
  ctx.fillStyle = g1;
  ctx.fillRect(0, 0, w, h);

  const g2 = ctx.createRadialGradient(w * 0.2, h * 0.85, 0, w * 0.2, h * 0.85, 460);
  g2.addColorStop(0, 'rgba(255, 62, 165, 0.3)');
  g2.addColorStop(1, 'rgba(255, 62, 165, 0)');
  ctx.fillStyle = g2;
  ctx.fillRect(0, 0, w, h);

  ctx.strokeStyle = 'rgba(53, 224, 255, 0.12)';
  ctx.lineWidth = 1;
  for (let x = 0; x < w; x += 64) {
    ctx.beginPath();
    ctx.moveTo(x, 0);
    ctx.lineTo(x, h);
    ctx.stroke();
  }
  for (let y = 0; y < h; y += 64) {
    ctx.beginPath();
    ctx.moveTo(0, y);
    ctx.lineTo(w, y);
    ctx.stroke();
  }

  ctx.fillStyle = '#eaf2ff';
  ctx.font = '800 132px system-ui, sans-serif';
  ctx.fillText('VisionPocket', 110, 420);
  ctx.fillStyle = '#35e0ff';
  ctx.font = '600 46px system-ui, sans-serif';
  ctx.fillText('VR w Twojej kieszeni', 116, 500);
  ctx.fillStyle = 'rgba(182, 255, 90, 0.9)';
  ctx.font = '500 30px system-ui, sans-serif';
  ctx.fillText('telefon + tani headset + Twoje dłonie', 118, 570);

  const url = canvas.toDataURL('image/jpeg', 0.86);
  return {
    id: 'demo-poster',
    name: 'Demo · plakat',
    kind: 'image',
    url,
    thumb: url,
    aspect: 16 / 9,
    panorama: false,
    demo: true,
  };
}

export const mediaLibrary = new MediaLibrary();
