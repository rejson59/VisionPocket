import { alpha, C, font } from './theme';

export interface HudState {
  mode: 'stereo' | 'flat';
  dwell: number;
  handCount: number;
  pinching: boolean;
  fps: number;
  hint: string | null;
  toast: string | null;
  gazeActive: boolean;
  tracking: boolean;
}

/**
 * Nakładka 2D na wierzchu obrazu: kropka celownika per oko,
 * pierścień „dwell”, podpowiedzi i licznik FPS.
 */
export class Hud {
  private ctx: CanvasRenderingContext2D;
  private dpr = 1;
  private lastKey = '';
  private lastDraw = 0;

  constructor(private canvas: HTMLCanvasElement) {
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('Brak kontekstu HUD');
    this.ctx = ctx;
    this.resize();
  }

  resize(): void {
    // HUD to nakładka wektorowa — 1.5× wystarczy, a oszczędza GPU
    this.dpr = Math.min(1.5, window.devicePixelRatio || 1);
    this.canvas.width = Math.floor(window.innerWidth * this.dpr);
    this.canvas.height = Math.floor(window.innerHeight * this.dpr);
    this.canvas.style.width = '100%';
    this.canvas.style.height = '100%';
  }

  draw(s: HudState): void {
    const now = performance.now();
    const key = [
      s.mode,
      Math.round(s.dwell * 24),
      s.handCount,
      s.pinching ? 1 : 0,
      Math.round(s.fps / 5),
      s.hint ?? '',
      s.toast ?? '',
      s.tracking ? 1 : 0,
    ].join('|');
    if (key === this.lastKey || now - this.lastDraw < 33) return;
    this.lastKey = key;
    this.lastDraw = now;

    const ctx = this.ctx;
    const w = this.canvas.width;
    const h = this.canvas.height;
    ctx.clearRect(0, 0, w, h);
    ctx.save();
    ctx.scale(this.dpr, this.dpr);
    const cw = w / this.dpr;
    const ch = h / this.dpr;

    const centers = s.mode === 'stereo' ? [cw * 0.25, cw * 0.75] : [cw * 0.5];
    for (const cx of centers) this.drawReticle(cx, ch * 0.5, s);

    // pasek statusu
    if (s.fps > 0) {
      ctx.save();
      ctx.globalAlpha = 0.75;
      ctx.fillStyle = s.fps > 50 ? C.lime : s.fps > 38 ? C.amber : C.red;
      ctx.font = font(12, 600);
      ctx.textAlign = 'left';
      ctx.textBaseline = 'top';
      const label = `${Math.round(s.fps)} FPS · ${s.handCount > 0 ? `${s.handCount}× dłoń` : s.tracking ? 'szukam dłoni' : 'wzrok'}`;
      ctx.fillText(label, 14, 12);
      ctx.restore();
    }

    if (s.hint) {
      ctx.save();
      ctx.globalAlpha = 0.85;
      const text = s.hint;
      ctx.font = font(14, 600);
      const tw = ctx.measureText(text).width;
      const bx = cw / 2 - tw / 2 - 16;
      const by = ch - 54;
      ctx.fillStyle = 'rgba(8,12,24,0.72)';
      ctx.beginPath();
      ctx.roundRect(bx, by, tw + 32, 34, 17);
      ctx.fill();
      ctx.strokeStyle = alpha(C.cyan, 0.3);
      ctx.lineWidth = 1.5;
      ctx.stroke();
      ctx.fillStyle = C.textDim;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(text, cw / 2, by + 17);
      ctx.restore();
    }

    if (s.toast) {
      ctx.save();
      ctx.font = font(16, 650);
      const tw = Math.min(cw - 60, ctx.measureText(s.toast).width + 44);
      const bx = cw / 2 - tw / 2;
      const by = ch * 0.72;
      ctx.fillStyle = 'rgba(10,16,32,0.93)';
      ctx.beginPath();
      ctx.roundRect(bx, by, tw, 46, 23);
      ctx.fill();
      ctx.strokeStyle = alpha(C.lime, 0.55);
      ctx.lineWidth = 2;
      ctx.stroke();
      ctx.fillStyle = C.text;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(s.toast, cw / 2, by + 24, tw - 30);
      ctx.restore();
    }

    ctx.restore();
  }

  private drawReticle(cx: number, cy: number, s: HudState): void {
    const ctx = this.ctx;
    const color = s.pinching ? C.lime : s.handCount > 0 ? C.cyan : alpha(C.textDim, 0.75);
    const radius = 15;

    ctx.save();
    ctx.translate(cx, cy);

    // pierścień bazowy
    ctx.beginPath();
    ctx.arc(0, 0, radius, 0, Math.PI * 2);
    ctx.strokeStyle = alpha(color, 0.45);
    ctx.lineWidth = 1.6;
    ctx.stroke();

    // kropka
    ctx.beginPath();
    ctx.arc(0, 0, s.handCount > 0 ? 3.4 : 2.4, 0, Math.PI * 2);
    ctx.fillStyle = color;
    ctx.fill();

    // postęp dwell
    if (s.dwell > 0.01) {
      ctx.beginPath();
      ctx.arc(0, 0, radius + 7, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * s.dwell);
      ctx.strokeStyle = C.lime;
      ctx.lineWidth = 4;
      ctx.lineCap = 'round';
      ctx.stroke();
    }

    // błysk szczypania
    if (s.pinching) {
      ctx.beginPath();
      ctx.arc(0, 0, radius + 3, 0, Math.PI * 2);
      ctx.strokeStyle = alpha(C.lime, 0.6);
      ctx.lineWidth = 2;
      ctx.stroke();
    }

    ctx.restore();
  }
}
