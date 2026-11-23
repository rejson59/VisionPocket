// Rysowanie UI na canvasie 2D. Każdy panel to tekstura 3D, więc wszystko
// rysujemy „ręcznie” — pełna kontrola nad rozmiarem i kontrastem.

import { alpha, C, font } from './theme';

export type Ctx = CanvasRenderingContext2D;

export function rrPath(
  ctx: Ctx,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
): void {
  const rad = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + rad, y);
  ctx.lineTo(x + w - rad, y);
  ctx.arcTo(x + w, y, x + w, y + rad, rad);
  ctx.lineTo(x + w, y + h - rad);
  ctx.arcTo(x + w, y + h, x + w - rad, y + h, rad);
  ctx.lineTo(x + rad, y + h);
  ctx.arcTo(x, y + h, x, y + h - rad, rad);
  ctx.lineTo(x, y + rad);
  ctx.arcTo(x, y, x + rad, y, rad);
  ctx.closePath();
}

export function fillRound(
  ctx: Ctx,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
  fill: string | CanvasGradient,
): void {
  rrPath(ctx, x, y, w, h, r);
  ctx.fillStyle = fill;
  ctx.fill();
}

export function strokeRound(
  ctx: Ctx,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
  color: string,
  width = 2,
): void {
  rrPath(ctx, x, y, w, h, r);
  ctx.strokeStyle = color;
  ctx.lineWidth = width;
  ctx.stroke();
}

export function text(
  ctx: Ctx,
  value: string,
  x: number,
  y: number,
  opts: {
    size?: number;
    weight?: number | string;
    color?: string;
    align?: CanvasTextAlign;
    baseline?: CanvasTextBaseline;
    letterSpacing?: number;
  } = {},
): void {
  ctx.save();
  ctx.font = font(opts.size ?? 28, opts.weight ?? 500);
  ctx.fillStyle = opts.color ?? C.text;
  ctx.textAlign = opts.align ?? 'left';
  ctx.textBaseline = opts.baseline ?? 'alphabetic';
  if (opts.letterSpacing != null) {
    (ctx as unknown as { letterSpacing: string }).letterSpacing = `${opts.letterSpacing}px`;
  }
  ctx.fillText(value, x, y);
  ctx.restore();
}

export function wrapText(
  ctx: Ctx,
  value: string,
  x: number,
  y: number,
  maxWidth: number,
  opts: {
    size?: number;
    weight?: number | string;
    color?: string;
    lineHeight?: number;
    maxLines?: number;
  } = {},
): number {
  const size = opts.size ?? 24;
  ctx.save();
  ctx.font = font(size, opts.weight ?? 400);
  ctx.fillStyle = opts.color ?? C.textDim;
  const words = value.split(/\s+/);
  const lines: string[] = [];
  let line = '';
  for (const word of words) {
    const test = line ? `${line} ${word}` : word;
    if (ctx.measureText(test).width > maxWidth && line) {
      lines.push(line);
      line = word;
    } else {
      line = test;
    }
  }
  if (line) lines.push(line);
  const maxLines = opts.maxLines ?? lines.length;
  const shown = lines.slice(0, maxLines);
  const lh = opts.lineHeight ?? size * 1.45;
  shown.forEach((l, i) => {
    const isLast = i === shown.length - 1 && lines.length > maxLines;
    ctx.fillText(isLast ? `${l}…` : l, x, y + i * lh);
  });
  ctx.restore();
  return shown.length * lh;
}

export function card(
  ctx: Ctx,
  x: number,
  y: number,
  w: number,
  h: number,
  opts: {
    r?: number;
    fill?: string | CanvasGradient;
    stroke?: string;
    hover?: boolean;
    accent?: string;
    glow?: number;
    strokeWidth?: number;
  } = {},
): void {
  const r = opts.r ?? 22;
  if (opts.glow) {
    ctx.save();
    ctx.shadowColor = alpha(opts.accent ?? C.cyan, 0.55);
    ctx.shadowBlur = opts.glow;
    fillRound(ctx, x, y, w, h, r, opts.fill ?? C.card);
    ctx.restore();
  } else {
    fillRound(ctx, x, y, w, h, r, opts.fill ?? C.card);
  }
  strokeRound(
    ctx,
    x,
    y,
    w,
    h,
    r,
    opts.stroke ?? (opts.hover ? alpha(opts.accent ?? C.cyan, 0.9) : alpha(C.line, 0.9)),
    opts.strokeWidth ?? (opts.hover ? 3 : 2),
  );
}

export function panelChrome(
  ctx: Ctx,
  w: number,
  h: number,
  opts: { accent?: string; title?: string; subtitle?: string; dense?: boolean } = {},
): void {
  const accent = opts.accent ?? C.cyan;
  ctx.clearRect(0, 0, w, h);

  // tło karty
  const bg = ctx.createLinearGradient(0, 0, w * 0.6, h);
  bg.addColorStop(0, 'rgba(12, 18, 34, 0.96)');
  bg.addColorStop(1, 'rgba(7, 11, 22, 0.97)');
  fillRound(ctx, 0, 0, w, h, 34, bg);

  // delikatna poświata w narożniku
  const glow = ctx.createRadialGradient(w * 0.15, 0, 0, w * 0.15, 0, h * 0.9);
  glow.addColorStop(0, alpha(accent, 0.14));
  glow.addColorStop(1, alpha(accent, 0));
  ctx.save();
  rrPath(ctx, 0, 0, w, h, 34);
  ctx.clip();
  ctx.fillStyle = glow;
  ctx.fillRect(0, 0, w, h);
  ctx.restore();

  // ramka + linia akcentu
  strokeRound(ctx, 1.5, 1.5, w - 3, h - 3, 34, alpha(accent, 0.28), 3);

  if (opts.title) {
    const top = opts.dense ? 56 : 78;
    text(ctx, opts.title, 40, top, { size: opts.dense ? 34 : 40, weight: 700, color: C.text });
    if (opts.subtitle) {
      text(ctx, opts.subtitle, 40, top + 34, {
        size: 22,
        weight: 500,
        color: C.muted,
      });
    }
  }
}

export function pill(
  ctx: Ctx,
  x: number,
  y: number,
  label: string,
  opts: { size?: number; color?: string; bg?: string; padX?: number; h?: number; dot?: boolean } = {},
): number {
  const size = opts.size ?? 20;
  const padX = opts.padX ?? 16;
  const h = opts.h ?? size * 2.1;
  ctx.save();
  ctx.font = font(size, 600);
  const dotSpace = opts.dot ? size * 0.9 : 0;
  const w = ctx.measureText(label).width + padX * 2 + dotSpace;
  const color = opts.color ?? C.cyan;
  fillRound(ctx, x, y, w, h, h / 2, opts.bg ?? alpha(color, 0.12));
  strokeRound(ctx, x, y, w, h, h / 2, alpha(color, 0.35), 2);
  if (opts.dot) {
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(x + padX + size * 0.28, y + h / 2, size * 0.28, 0, Math.PI * 2);
    ctx.fill();
  }
  ctx.fillStyle = color;
  ctx.textAlign = 'left';
  ctx.textBaseline = 'middle';
  ctx.fillText(label, x + padX + dotSpace, y + h / 2 + 1);
  ctx.restore();
  return w;
}

export function button(
  ctx: Ctx,
  x: number,
  y: number,
  w: number,
  h: number,
  label: string,
  opts: {
    hover?: boolean;
    primary?: boolean;
    accent?: string;
    icon?: string;
    size?: number;
    disabled?: boolean;
  } = {},
): void {
  const accent = opts.accent ?? C.cyan;
  const r = Math.min(h / 2, 24);
  if (opts.primary) {
    const grad = ctx.createLinearGradient(x, y, x + w, y + h);
    grad.addColorStop(0, opts.disabled ? '#2a3550' : accent);
    grad.addColorStop(1, opts.disabled ? '#222b42' : C.violet);
    fillRound(ctx, x, y, w, h, r, grad);
    ctx.save();
    ctx.globalAlpha = opts.hover ? 1 : 0.94;
    text(ctx, label, x + w / 2, y + h / 2 + 1, {
      size: opts.size ?? h * 0.34,
      weight: 700,
      color: '#04101c',
      align: 'center',
      baseline: 'middle',
    });
    ctx.restore();
    return;
  }
  fillRound(ctx, x, y, w, h, r, opts.hover ? alpha(accent, 0.18) : 'rgba(255,255,255,0.045)');
  strokeRound(ctx, x, y, w, h, r, alpha(accent, opts.hover ? 0.85 : 0.3), opts.hover ? 3 : 2);
  text(ctx, label, x + w / 2, y + h / 2 + 1, {
    size: opts.size ?? h * 0.34,
    weight: 600,
    color: opts.disabled ? C.muted : C.text,
    align: 'center',
    baseline: 'middle',
  });
}

export function slider(
  ctx: Ctx,
  x: number,
  y: number,
  w: number,
  value: number,
  min: number,
  max: number,
  opts: {
    label: string;
    valueLabel?: string;
    accent?: string;
    hover?: boolean;
    size?: number;
  },
): void {
  const accent = opts.accent ?? C.cyan;
  const size = opts.size ?? 22;
  const labelY = y;
  text(ctx, opts.label, x, labelY, { size, weight: 500, color: C.textDim });
  text(ctx, opts.valueLabel ?? value.toFixed(2), x + w, labelY, {
    size,
    weight: 700,
    color: accent,
    align: 'right',
  });
  const t = Math.min(1, Math.max(0, (value - min) / (max - min)));
  const trackY = labelY + size * 0.9;
  const trackH = opts.hover ? 14 : 10;
  fillRound(ctx, x, trackY, w, trackH, trackH / 2, 'rgba(255,255,255,0.08)');
  const fillW = Math.max(trackH, w * t);
  const grad = ctx.createLinearGradient(x, 0, x + w, 0);
  grad.addColorStop(0, alpha(accent, 0.75));
  grad.addColorStop(1, accent);
  fillRound(ctx, x, trackY, fillW, trackH, trackH / 2, grad);
  // gałka
  ctx.save();
  ctx.shadowColor = alpha(accent, 0.8);
  ctx.shadowBlur = opts.hover ? 22 : 12;
  ctx.fillStyle = opts.hover ? '#ffffff' : C.text;
  ctx.beginPath();
  ctx.arc(x + w * t, trackY + trackH / 2, opts.hover ? 15 : 12, 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();
}

export function divider(ctx: Ctx, x: number, y: number, w: number, color = C.line): void {
  ctx.strokeStyle = alpha(color, 0.6);
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(x, y);
  ctx.lineTo(x + w, y);
  ctx.stroke();
}

export function drawImageCover(
  ctx: Ctx,
  img: CanvasImageSource,
  x: number,
  y: number,
  w: number,
  h: number,
  radius = 18,
): void {
  const iw = (img as HTMLVideoElement).videoWidth || (img as HTMLImageElement).naturalWidth || 1;
  const ih = (img as HTMLVideoElement).videoHeight || (img as HTMLImageElement).naturalHeight || 1;
  const scale = Math.max(w / iw, h / ih);
  const dw = iw * scale;
  const dh = ih * scale;
  ctx.save();
  rrPath(ctx, x, y, w, h, radius);
  ctx.clip();
  ctx.drawImage(img, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh);
  ctx.restore();
}

// ---------------------------------------------------------------------------
// Ikony — rysowane wektorowo, więc nie zależą od emoji w systemie.
// ---------------------------------------------------------------------------

export type IconName =
  | 'play'
  | 'pause'
  | 'photo'
  | 'note'
  | 'game'
  | 'gear'
  | 'camera'
  | 'help'
  | 'back'
  | 'mic'
  | 'plus'
  | 'home'
  | 'hand'
  | 'target'
  | 'check'
  | 'close'
  | 'panorama'
  | 'clock'
  | 'recenter'
  | 'screen'
  | 'sparkle'
  | 'trash'
  | 'stop';

export function icon(
  ctx: Ctx,
  name: IconName,
  cx: number,
  cy: number,
  size: number,
  color: string = C.text,
  lineWidth = 3,
): void {
  const s = size / 2;
  ctx.save();
  ctx.translate(cx, cy);
  ctx.strokeStyle = color;
  ctx.fillStyle = color;
  ctx.lineWidth = lineWidth;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';

  const stroke = (fn: () => void) => {
    ctx.beginPath();
    fn();
    ctx.stroke();
  };

  switch (name) {
    case 'play':
      ctx.beginPath();
      ctx.moveTo(-s * 0.5, -s * 0.72);
      ctx.lineTo(s * 0.8, 0);
      ctx.lineTo(-s * 0.5, s * 0.72);
      ctx.closePath();
      ctx.fill();
      break;
    case 'pause':
      fillRound(ctx, -s * 0.55, -s * 0.7, s * 0.45, s * 1.4, 4, color);
      fillRound(ctx, s * 0.12, -s * 0.7, s * 0.45, s * 1.4, 4, color);
      break;
    case 'stop':
      fillRound(ctx, -s * 0.6, -s * 0.6, s * 1.2, s * 1.2, 6, color);
      break;
    case 'photo':
      stroke(() => {
        ctx.rect(-s * 0.78, -s * 0.62, s * 1.56, s * 1.24);
      });
      ctx.beginPath();
      ctx.arc(s * 0.28, -s * 0.2, s * 0.17, 0, Math.PI * 2);
      ctx.fill();
      stroke(() => {
        ctx.moveTo(-s * 0.72, s * 0.52);
        ctx.lineTo(-s * 0.14, -s * 0.12);
        ctx.lineTo(s * 0.36, s * 0.52);
      });
      break;
    case 'note':
      stroke(() => {
        ctx.rect(-s * 0.62, -s * 0.78, s * 1.24, s * 1.56);
      });
      ctx.lineWidth = lineWidth * 0.8;
      for (let i = 0; i < 3; i++) {
        stroke(() => {
          ctx.moveTo(-s * 0.34, -s * 0.34 + i * s * 0.34);
          ctx.lineTo(s * 0.34, -s * 0.34 + i * s * 0.34);
        });
      }
      break;
    case 'game':
      stroke(() => {
        ctx.roundRect(-s * 0.85, -s * 0.5, s * 1.7, s * 1.0, s * 0.34);
      });
      stroke(() => {
        ctx.moveTo(-s * 0.5, 0);
        ctx.lineTo(-s * 0.16, 0);
        ctx.moveTo(-s * 0.33, -s * 0.17);
        ctx.lineTo(-s * 0.33, s * 0.17);
      });
      ctx.beginPath();
      ctx.arc(s * 0.36, -s * 0.14, s * 0.11, 0, Math.PI * 2);
      ctx.arc(s * 0.56, s * 0.12, s * 0.11, 0, Math.PI * 2);
      ctx.fill();
      break;
    case 'gear': {
      const teeth = 8;
      ctx.beginPath();
      for (let i = 0; i < teeth * 2; i++) {
        const a = (i / (teeth * 2)) * Math.PI * 2;
        const r = i % 2 === 0 ? s * 0.82 : s * 0.62;
        const px = Math.cos(a) * r;
        const py = Math.sin(a) * r;
        if (i === 0) ctx.moveTo(px, py);
        else ctx.lineTo(px, py);
      }
      ctx.closePath();
      ctx.stroke();
      ctx.beginPath();
      ctx.arc(0, 0, s * 0.3, 0, Math.PI * 2);
      ctx.stroke();
      break;
    }
    case 'camera':
      stroke(() => {
        ctx.roundRect(-s * 0.82, -s * 0.52, s * 1.64, s * 1.14, s * 0.24);
      });
      stroke(() => {
        ctx.moveTo(-s * 0.28, -s * 0.52);
        ctx.lineTo(-s * 0.12, -s * 0.78);
        ctx.lineTo(s * 0.16, -s * 0.78);
        ctx.lineTo(s * 0.32, -s * 0.52);
      });
      ctx.beginPath();
      ctx.arc(0, s * 0.06, s * 0.34, 0, Math.PI * 2);
      ctx.stroke();
      break;
    case 'help':
      ctx.beginPath();
      ctx.arc(0, 0, s * 0.82, 0, Math.PI * 2);
      ctx.stroke();
      ctx.beginPath();
      ctx.arc(0, -s * 0.16, s * 0.28, Math.PI * 0.9, Math.PI * 2.35);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(0, s * 0.12);
      ctx.lineTo(0, s * 0.3);
      ctx.stroke();
      ctx.beginPath();
      ctx.arc(0, s * 0.5, lineWidth * 0.9, 0, Math.PI * 2);
      ctx.fill();
      break;
    case 'back':
      stroke(() => {
        ctx.moveTo(s * 0.6, 0);
        ctx.lineTo(-s * 0.5, 0);
        ctx.moveTo(-s * 0.1, -s * 0.45);
        ctx.lineTo(-s * 0.6, 0);
        ctx.lineTo(-s * 0.1, s * 0.45);
      });
      break;
    case 'mic':
      stroke(() => {
        ctx.roundRect(-s * 0.3, -s * 0.78, s * 0.6, s * 0.94, s * 0.3);
      });
      stroke(() => {
        ctx.moveTo(-s * 0.56, -s * 0.04);
        ctx.arc(0, -s * 0.04, s * 0.56, Math.PI, 0, false);
        ctx.moveTo(0, s * 0.52);
        ctx.lineTo(0, s * 0.82);
      });
      break;
    case 'plus':
      stroke(() => {
        ctx.moveTo(-s * 0.6, 0);
        ctx.lineTo(s * 0.6, 0);
        ctx.moveTo(0, -s * 0.6);
        ctx.lineTo(0, s * 0.6);
      });
      break;
    case 'home':
      stroke(() => {
        ctx.moveTo(-s * 0.75, s * 0.05);
        ctx.lineTo(0, -s * 0.7);
        ctx.lineTo(s * 0.75, s * 0.05);
        ctx.lineTo(s * 0.75, s * 0.7);
        ctx.lineTo(-s * 0.75, s * 0.7);
        ctx.closePath();
      });
      break;
    case 'hand': {
      // dłoń: cztery palce + kciuk
      const fingers = [
        [-0.55, -0.2, 0.18, 0.75],
        [-0.18, -0.45, 0.18, 0.9],
        [0.18, -0.42, 0.18, 0.86],
        [0.52, -0.15, 0.18, 0.6],
      ];
      for (const [fx, fy, fw, fh] of fingers) {
        stroke(() => {
          ctx.roundRect(fx * s - fw * s * 0.5, fy * s, fw * s, fh * s, fw * s * 0.5);
        });
      }
      stroke(() => {
        ctx.roundRect(-s * 0.68, s * 0.28, s * 1.36, s * 0.5, s * 0.25);
      });
      stroke(() => {
        ctx.moveTo(-s * 0.66, s * 0.4);
        ctx.lineTo(-s * 0.92, s * 0.02);
      });
      break;
    }
    case 'target':
      ctx.beginPath();
      ctx.arc(0, 0, s * 0.82, 0, Math.PI * 2);
      ctx.stroke();
      ctx.beginPath();
      ctx.arc(0, 0, s * 0.42, 0, Math.PI * 2);
      ctx.stroke();
      ctx.beginPath();
      ctx.arc(0, 0, s * 0.1, 0, Math.PI * 2);
      ctx.fill();
      break;
    case 'check':
      stroke(() => {
        ctx.moveTo(-s * 0.62, 0);
        ctx.lineTo(-s * 0.16, s * 0.45);
        ctx.lineTo(s * 0.66, -s * 0.48);
      });
      break;
    case 'close':
      stroke(() => {
        ctx.moveTo(-s * 0.55, -s * 0.55);
        ctx.lineTo(s * 0.55, s * 0.55);
        ctx.moveTo(s * 0.55, -s * 0.55);
        ctx.lineTo(-s * 0.55, s * 0.55);
      });
      break;
    case 'panorama':
      stroke(() => {
        ctx.arc(0, s * 0.25, s * 0.9, Math.PI * 1.15, Math.PI * 1.85, false);
      });
      stroke(() => {
        ctx.moveTo(-s * 0.9, s * 0.5);
        ctx.quadraticCurveTo(0, -s * 0.35, s * 0.9, s * 0.5);
      });
      break;
    case 'clock':
      ctx.beginPath();
      ctx.arc(0, 0, s * 0.8, 0, Math.PI * 2);
      ctx.stroke();
      stroke(() => {
        ctx.moveTo(0, -s * 0.45);
        ctx.lineTo(0, 0);
        ctx.lineTo(s * 0.36, s * 0.22);
      });
      break;
    case 'recenter':
      ctx.beginPath();
      ctx.arc(0, 0, s * 0.55, 0, Math.PI * 2);
      ctx.stroke();
      stroke(() => {
        ctx.moveTo(0, -s * 0.85);
        ctx.lineTo(0, -s * 0.62);
        ctx.moveTo(0, s * 0.62);
        ctx.lineTo(0, s * 0.85);
        ctx.moveTo(-s * 0.85, 0);
        ctx.lineTo(-s * 0.62, 0);
        ctx.moveTo(s * 0.62, 0);
        ctx.lineTo(s * 0.85, 0);
      });
      break;
    case 'screen':
      stroke(() => {
        ctx.rect(-s * 0.85, -s * 0.6, s * 1.7, s * 1.2);
      });
      stroke(() => {
        ctx.moveTo(-s * 0.4, s * 0.82);
        ctx.lineTo(s * 0.4, s * 0.82);
      });
      break;
    case 'sparkle':
      ctx.beginPath();
      ctx.moveTo(0, -s * 0.85);
      ctx.lineTo(s * 0.2, -s * 0.2);
      ctx.lineTo(s * 0.85, 0);
      ctx.lineTo(s * 0.2, s * 0.2);
      ctx.lineTo(0, s * 0.85);
      ctx.lineTo(-s * 0.2, s * 0.2);
      ctx.lineTo(-s * 0.85, 0);
      ctx.lineTo(-s * 0.2, -s * 0.2);
      ctx.closePath();
      ctx.fill();
      break;
    case 'trash':
      stroke(() => {
        ctx.moveTo(-s * 0.5, -s * 0.45);
        ctx.lineTo(-s * 0.4, s * 0.7);
        ctx.lineTo(s * 0.4, s * 0.7);
        ctx.lineTo(s * 0.5, -s * 0.45);
      });
      stroke(() => {
        ctx.moveTo(-s * 0.72, -s * 0.45);
        ctx.lineTo(s * 0.72, -s * 0.45);
        ctx.moveTo(-s * 0.2, -s * 0.7);
        ctx.lineTo(s * 0.2, -s * 0.7);
      });
      break;
  }
  ctx.restore();
}
