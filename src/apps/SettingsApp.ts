import * as THREE from 'three';
import { C, alpha } from '../ui/theme';
import { button, icon, panelChrome, slider, text } from '../ui/draw';
import type { Panel } from '../ui/Panel';
import type { AppHost, VrApp } from './types';
import { applyQuality, resetSettings, saveSettings, settings, type Quality } from '../core/settings';
import { sfx } from '../core/sfx';

const COL_A = { x: 40, w: 700 };
const COL_B = { x: 800, w: 708 };

/** Ustawienia soczewek, dłoni i wydajności. */
export class SettingsApp implements VrApp {
  id = 'settings';
  title = 'Ustawienia';
  subtitle = 'Soczewki, dłonie, wydajność';
  icon = 'gear' as const;
  accent = C.amber;
  group = new THREE.Group();
  hint: string | null = 'Reguluj suwaki szczypiąc w wybranym miejscu';

  constructor(private host: AppHost, private panel: Panel) {}

  enter(): void {
    this.panel.markDirty();
  }

  private sliderRow(
    panel: Panel,
    x: number,
    y: number,
    w: number,
    id: string,
    label: string,
    value: number,
    min: number,
    max: number,
    fmt: (v: number) => string,
  ): void {
    slider(panel.ctx, x, y, w, value, min, max, {
      label,
      valueLabel: fmt(value),
      accent: this.accent,
      hover: panel.hoverId === id,
      size: 22,
    });
    panel.hit(id, x - 10, y - 34, w + 20, 66);
  }

  private toggleRow(
    panel: Panel,
    x: number,
    y: number,
    w: number,
    id: string,
    label: string,
    on: boolean,
    accent: string = C.cyan,
  ): void {
    const { ctx } = panel;
    const hover = panel.hoverId === id;
    ctx.save();
    if (hover) {
      ctx.fillStyle = alpha(accent, 0.08);
      ctx.beginPath();
      ctx.roundRect(x - 12, y - 26, w + 24, 58, 14);
      ctx.fill();
    }
    ctx.restore();
    text(ctx, label, x, y + 8, { size: 22, color: C.textDim });
    const pillW = 96;
    const pillH = 42;
    const px = x + w - pillW;
    const py = y - 22;
    ctx.fillStyle = on ? alpha(accent, 0.85) : 'rgba(255,255,255,0.09)';
    ctx.beginPath();
    ctx.roundRect(px, py, pillW, pillH, pillH / 2);
    ctx.fill();
    ctx.fillStyle = on ? '#04101c' : C.muted;
    ctx.beginPath();
    ctx.arc(px + (on ? pillW - pillH / 2 : pillH / 2), py + pillH / 2, pillH / 2 - 5, 0, Math.PI * 2);
    ctx.fill();
    text(ctx, on ? 'ON' : 'OFF', px + (on ? 30 : pillW - 30), py + pillH / 2 + 1, {
      size: 15,
      weight: 800,
      color: on ? '#04101c' : C.muted,
      align: 'center',
      baseline: 'middle',
    });
    panel.hit(id, x - 12, y - 26, w + 24, 58);
  }

  draw(panel: Panel): void {
    const { ctx } = panel;
    const W = panel.pxW;
    const H = panel.pxH;
    panelChrome(ctx, W, H, { accent: this.accent });

    text(ctx, 'Ustawienia', 40, 74, { size: 36, weight: 700 });
    text(ctx, 'Dopasuj obraz do swoich soczewek i telefonu', 40, 106, {
      size: 19,
      color: C.muted,
    });

    button(ctx, W - 230, 46, 190, 60, 'Wstecz', {
      hover: panel.hoverId === 'set:home',
      accent: C.cyan,
      size: 20,
    });
    panel.hit('set:home', W - 230, 46, 190, 60);

    // --- kolumna A: optyka ---
    text(ctx, 'OPTYKA', COL_A.x, 158, { size: 16, weight: 700, color: C.amber });
    this.sliderRow(panel, COL_A.x, 208, COL_A.w, 'set:ipd', 'Rozstaw oczu (IPD)', settings.ipd * 1000, 54, 76, (v) => `${Math.round(v)} mm`);
    this.sliderRow(panel, COL_A.x, 300, COL_A.w, 'set:fov', 'Pole widzenia', settings.fov, 70, 110, (v) => `${Math.round(v)}°`);
    this.sliderRow(panel, COL_A.x, 392, COL_A.w, 'set:lens', 'Korekcja soczewek', settings.lens, 0, 0.35, (v) => v.toFixed(2));
    this.sliderRow(panel, COL_A.x, 484, COL_A.w, 'set:zoom', 'Dopasowanie obrazu', settings.zoom, 0.8, 1.3, (v) => `${v.toFixed(2)}×`);
    this.sliderRow(panel, COL_A.x, 576, COL_A.w, 'set:brightness', 'Jasność', settings.brightness, 0.6, 1.7, (v) => `${Math.round(v * 100)}%`);

    // --- kolumna B: wydajność i dłonie ---
    text(ctx, 'WYDAJNOŚĆ I DŁONIE', COL_B.x, 158, { size: 16, weight: 700, color: C.amber });
    this.sliderRow(panel, COL_B.x, 208, COL_B.w, 'set:scale', 'Skala renderu', settings.renderScale, 0.5, 1, (v) => `${Math.round(v * 100)}%`);

    text(ctx, 'Jakość', COL_B.x, 288, { size: 20, color: C.textDim });
    const qualities: Quality[] = ['low', 'medium', 'high'];
    const labels: Record<Quality, string> = { low: 'Niska', medium: 'Średnia', high: 'Wysoka' };
    qualities.forEach((q, i) => {
      const bw = 200;
      const bx = COL_B.x + 110 + i * (bw + 12);
      const id = `set:quality:${q}`;
      button(ctx, bx, 262, bw, 52, labels[q], {
        hover: panel.hoverId === id,
        primary: settings.quality === q,
        accent: this.accent,
        size: 19,
      });
      panel.hit(id, bx, 262, bw, 52);
    });

    this.toggleRow(panel, COL_B.x, 366, COL_B.w, 'set:hands', 'Śledzenie dłoni', settings.handTracking, C.lime);
    this.toggleRow(panel, COL_B.x, 436, COL_B.w, 'set:skeleton', 'Rysuj szkielet dłoni', settings.showSkeleton, C.cyan);
    this.toggleRow(panel, COL_B.x, 506, COL_B.w, 'set:mr', 'Obraz z kamery (MR)', settings.passthrough, C.violet);
    this.toggleRow(panel, COL_B.x, 576, COL_B.w, 'set:fps', 'Licznik FPS', settings.showFps, C.textDim);

    // obrót kamery
    text(ctx, 'Obrót obrazu kamery', COL_B.x, 646, { size: 20, color: C.textDim });
    ([0, 90, 180, 270] as const).forEach((deg, i) => {
      const bw = 96;
      const bx = COL_B.x + 250 + i * (bw + 10);
      const id = `set:rotate:${deg}`;
      button(ctx, bx, 620, bw, 50, `${deg}°`, {
        hover: panel.hoverId === id,
        primary: settings.handRotate === deg,
        accent: C.violet,
        size: 18,
      });
      panel.hit(id, bx, 620, bw, 50);
    });

    // --- dolny pasek ---
    const by = H - 150;
    button(ctx, 40, by, 260, 74, 'Wyśrodkuj widok', {
      hover: panel.hoverId === 'set:recenter',
      accent: C.cyan,
      size: 20,
    });
    panel.hit('set:recenter', 40, by, 260, 74);

    button(ctx, 320, by, 220, 74, this.host.cameraRunning() ? 'Wyłącz kamerę' : 'Włącz kamerę', {
      hover: panel.hoverId === 'set:camera',
      accent: this.host.cameraRunning() ? C.red : C.lime,
      size: 20,
    });
    panel.hit('set:camera', 320, by, 220, 74);

    button(ctx, 560, by, 220, 74, 'Reset', {
      hover: panel.hoverId === 'set:reset',
      accent: C.amber,
      size: 20,
    });
    panel.hit('set:reset', 560, by, 220, 74);

    // status
    const hands = this.host.handCount();
    ctx.save();
    ctx.globalAlpha = 0.9;
    icon(ctx, 'hand', W - 300, by + 38, 30, hands > 0 ? C.lime : C.muted);
    text(ctx, hands > 0 ? `Dłonie widoczne: ${hands}` : 'Brak dłoni w kadrze', W - 275, by + 38, {
      size: 18,
      color: hands > 0 ? C.lime : C.muted,
    });
    text(ctx, `${Math.round(this.host.fps())} FPS · render ${Math.round(settings.renderScale * 100)}%`, W - 275, by + 70, {
      size: 17,
      color: C.muted,
    });
    ctx.restore();

    text(ctx, 'Wskazówka: zejdź ze skalą renderu do ~70%, jeśli obraz „gubi” klatki.', 40, H - 30, {
      size: 17,
      color: C.muted,
    });
  }

  click(panel: Panel, x: number, y: number, id: string | null): void {
    if (!id) return;
    const setValue = (value: number, apply = true) => {
      saveSettings();
      if (apply) this.host.applySettings();
      this.panel.markDirty();
    };

    const trackT = (trackX: number, trackW: number, min: number, max: number): number =>
      min + ((Math.min(trackX + trackW, Math.max(trackX, x)) - trackX) / trackW) * (max - min);

    switch (id) {
      case 'set:ipd':
        settings.ipd = trackT(COL_A.x, COL_A.w, 54, 76) / 1000;
        setValue(settings.ipd);
        break;
      case 'set:fov':
        settings.fov = Math.round(trackT(COL_A.x, COL_A.w, 70, 110));
        setValue(settings.fov);
        break;
      case 'set:lens':
        settings.lens = Number(trackT(COL_A.x, COL_A.w, 0, 0.35).toFixed(3));
        setValue(settings.lens);
        break;
      case 'set:zoom':
        settings.zoom = Number(trackT(COL_A.x, COL_A.w, 0.8, 1.3).toFixed(3));
        setValue(settings.zoom);
        break;
      case 'set:brightness':
        settings.brightness = Number(trackT(COL_A.x, COL_A.w, 0.6, 1.7).toFixed(2));
        setValue(settings.brightness);
        break;
      case 'set:scale':
        settings.renderScale = Number(trackT(COL_B.x, COL_B.w, 0.5, 1).toFixed(2));
        setValue(settings.renderScale);
        break;
      case 'set:hands':
        settings.handTracking = !settings.handTracking;
        if (settings.handTracking) void this.host.startCamera();
        else this.host.stopCamera();
        setValue(0);
        sfx.toggle(settings.handTracking);
        break;
      case 'set:skeleton':
        settings.showSkeleton = !settings.showSkeleton;
        setValue(0);
        sfx.toggle(settings.showSkeleton);
        break;
      case 'set:mr':
        settings.passthrough = !settings.passthrough;
        void this.host.setPassthrough(settings.passthrough);
        setValue(0);
        sfx.toggle(settings.passthrough);
        break;
      case 'set:fps':
        settings.showFps = !settings.showFps;
        setValue(0);
        break;
      case 'set:recenter':
        this.host.recenter();
        this.panel.markDirty();
        sfx.click();
        break;
      case 'set:camera':
        if (this.host.cameraRunning()) this.host.stopCamera();
        else void this.host.startCamera();
        this.panel.markDirty();
        break;
      case 'set:reset':
        resetSettings();
        this.host.applySettings();
        this.panel.markDirty();
        this.host.toast('Ustawienia przywrócone.');
        break;
      case 'set:home':
        this.host.back();
        break;
      default:
        if (id.startsWith('set:quality:')) {
          const q = id.slice('set:quality:'.length) as Quality;
          applyQuality(q);
          this.host.applySettings();
          this.panel.markDirty();
          sfx.click();
        } else if (id.startsWith('set:rotate:')) {
          const deg = Number(id.slice('set:rotate:'.length)) as 0 | 90 | 180 | 270;
          settings.handRotate = deg;
          saveSettings();
          this.host.applySettings();
          this.panel.markDirty();
          sfx.click();
        }
    }
    void y;
  }
}
