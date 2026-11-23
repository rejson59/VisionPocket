import * as THREE from 'three';
import { C, alpha } from '../ui/theme';
import { button, card, panelChrome, text } from '../ui/draw';
import type { Panel } from '../ui/Panel';
import type { AppHost, VrApp } from './types';

/**
 * Diagnostyka — jedyne miejsce, gdzie widać „dlaczego jest wolno”.
 * Pokazuje realny FPS, czas klatki GPU, rozmiar bufora, stan czujników
 * i tempo analizy dłoni. Przyciski pozwalają naprawić problem w 1 kliknięciu.
 */
export class DiagnosticsApp implements VrApp {
  id = 'diagnostics';
  title = 'Diagnostyka';
  subtitle = 'Wydajność, czujniki, kamera';
  icon = 'gear' as const;
  accent = C.amber;
  group = new THREE.Group();
  hint = 'Kliknij „Tryb płynności”, jeśli FPS jest poniżej 50';

  private active = false;
  private sinceRefresh = 0;
  private report = '';
  private copied = false;

  constructor(private host: AppHost, private panel: Panel) {}

  enter(): void {
    this.active = true;
    this.panel.markDirty();
  }

  exit(): void {
    this.active = false;
  }

  /** Odświeżanie 2×/s — bez timerów, żeby nie trzymać procesu przy życiu. */
  update(dt: number, panel: Panel): void {
    if (!this.active) return;
    this.sinceRefresh += dt;
    if (this.sinceRefresh >= 0.5) {
      this.sinceRefresh = 0;
      panel.markDirty();
    }
  }

  private buildReport(): string {
    const d = this.host.diagnostics();
    return [
      `VisionPocket v${__APP_VERSION__} — raport`,
      `tryb: ${d.mode}`,
      `fps: ${d.fps.toFixed(0)} (render ${d.renderMs.toFixed(1)} ms)`,
      `skala renderu: ${(d.renderScale * 100).toFixed(0)}%  pixelRatio: ${d.pixelRatio}`,
      `bufor: ${d.buffer.width}x${d.buffer.height}`,
      `ekran: ${d.viewport.width}x${d.viewport.height} @${d.dpr}`,
      `gpu: ${d.gpu}`,
      `czujniki: ${d.sensorsLive ? 'działają' : 'BRAK'} (absolute: ${d.sensorsAbsolute ? 'tak' : 'nie'})`,
      `kamera: ${d.camera}`,
      `dłonie: ${d.hands}`,
      `analiza dłoni: ${d.handMs.toFixed(1)} ms @ ${d.handHz.toFixed(1)} Hz`,
      `błąd: ${d.error ?? 'brak'}`,
      `ua: ${d.ua}`,
    ].join('\n');
  }

  draw(panel: Panel): void {
    const { ctx } = panel;
    const W = panel.pxW;
    const H = panel.pxH;
    panelChrome(ctx, W, H, { accent: this.accent });

    const d = this.host.diagnostics();
    this.report = this.buildReport();

    text(ctx, 'Diagnostyka', 40, 72, { size: 36, weight: 700 });
    text(ctx, `Realne liczby z tego telefonu · v${__APP_VERSION__}`, 40, 104, { size: 19, color: C.muted });

    button(ctx, W - 230, 46, 190, 60, 'Wstecz', {
      hover: panel.hoverId === 'diag:back',
      accent: C.cyan,
      size: 20,
    });
    panel.hit('diag:back', W - 230, 46, 190, 60);

    // Kafelki z najważniejszymi liczbami
    const tiles = [
      {
        label: 'FPS',
        value: d.fps.toFixed(0),
        good: d.fps >= 55,
        warn: d.fps >= 40,
        note: `render ${d.renderMs.toFixed(1)} ms`,
      },
      {
        label: 'Czujniki',
        value: d.sensorsLive ? 'OK' : 'BRAK',
        good: d.sensorsLive,
        warn: false,
        note: d.sensorsAbsolute ? 'kompas + żyro' : 'tylko żyroskop',
      },
      {
        label: 'Kamera',
        value: d.camera === 'running' ? 'OK' : d.camera === 'denied' ? 'BRAK ZGODY' : d.camera,
        good: d.camera === 'running',
        warn: d.camera === 'loading',
        note: d.hands === 1 ? '1 dłoń w kadrze' : `${d.hands} dłonie w kadrze`,
      },
      {
        label: 'Analiza dłoni',
        value: `${d.handMs.toFixed(0)} ms`,
        good: d.handMs > 0 && d.handMs < 22,
        warn: d.handMs < 40,
        note: `${d.handHz.toFixed(0)}×/s`,
      },
    ];

    const margin = 40;
    const gap = 18;
    const tw = (W - margin * 2 - gap * 3) / 4;
    tiles.forEach((t, i) => {
      const x = margin + i * (tw + gap);
      const y = 150;
      const color = !t.good ? (t.warn ? C.amber : C.red) : C.lime;
      card(ctx, x, y, tw, 150, { fill: alpha(color, 0.07), stroke: alpha(color, 0.3) });
      text(ctx, t.label, x + 22, y + 36, { size: 18, color: C.muted, weight: 600 });
      text(ctx, t.value, x + 22, y + 84, { size: 34, weight: 700, color });
      text(ctx, t.note, x + 22, y + 120, { size: 17, color: C.textDim });
    });

    // Pasek: skala renderu / bufor
    const barY = 330;
    card(ctx, margin, barY, W - margin * 2, 108, { fill: 'rgba(255,255,255,0.035)' });
    const info = [
      `Skala renderu ${(d.renderScale * 100).toFixed(0)}%`,
      `Bufor ${d.buffer.width}×${d.buffer.height}`,
      `Ekran ${d.viewport.width}×${d.viewport.height} @${d.dpr.toFixed(1)}`,
      `Tryb ${d.mode === 'stereo' ? 'VR stereo' : '2D'}`,
    ];
    info.forEach((line, i) => {
      text(ctx, line, margin + 26 + (i % 2) * 470, barY + 42 + Math.floor(i / 2) * 36, {
        size: 21,
        color: C.textDim,
      });
    });

    // Przyciski naprawcze
    const by = 470;
    const bw = (W - margin * 2 - 40) / 3;
    const actions = [
      { id: 'diag:perf', label: 'Tryb płynności', color: C.lime },
      { id: 'diag:mode', label: d.mode === 'stereo' ? 'Przełącz na 2D' : 'Przełącz na VR', color: C.cyan },
      { id: 'diag:copy', label: this.copied ? 'Skopiowano!' : 'Kopiuj raport', color: C.violet },
    ];
    actions.forEach((a, i) => {
      const x = margin + i * (bw + 20);
      button(ctx, x, by, bw, 76, a.label, {
        hover: panel.hoverId === a.id,
        accent: a.color,
        size: 22,
      });
      panel.hit(a.id, x, by, bw, 76);
    });

    // Podgląd raportu
    const ry = by + 100;
    card(ctx, margin, ry, W - margin * 2, H - ry - 40, {
      fill: 'rgba(0,0,0,0.35)',
      stroke: alpha(C.amber, 0.25),
    });
    text(ctx, 'Raport (wyślij mi go, jeśli coś nie działa)', margin + 26, ry + 40, {
      size: 20,
      weight: 700,
      color: C.amber,
    });
    const lines = this.report.split('\n').slice(1, 9);
    lines.forEach((line, i) => {
      text(ctx, line, margin + 26, ry + 80 + i * 30, { size: 18, color: C.textDim });
    });

    if (d.error) {
      text(ctx, `Błąd: ${d.error}`.slice(0, 120), margin + 26, H - 60, {
        size: 18,
        color: C.red,
      });
    }
  }

  private async copyReport(): Promise<void> {
    const ok = await this.writeClipboard(this.report);
    this.copied = ok;
    this.host.toast(
      ok ? 'Raport w schowku — wklej go w czacie.' : 'Nie udało się skopiować — przepisz liczby z ekranu.',
      ok ? 3000 : 4000,
    );
  }

  private async writeClipboard(value: string): Promise<boolean> {
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(value);
        return true;
      }
    } catch {
      /* przechodzimy do metody zapasowej */
    }
    try {
      const area = document.createElement('textarea');
      area.value = value;
      area.style.position = 'fixed';
      area.style.opacity = '0';
      document.body.appendChild(area);
      area.select?.();
      const ok = typeof document.execCommand === 'function' && document.execCommand('copy');
      document.body.removeChild(area);
      return !!ok;
    } catch {
      return false;
    }
  }

  click(panel: Panel, x: number, y: number, id: string | null): void {
    void panel;
    void x;
    void y;
    switch (id) {
      case 'diag:back':
        this.host.back();
        break;
      case 'diag:perf':
        this.host.performanceMode();
        break;
      case 'diag:mode':
        this.host.setMode(this.host.currentMode() === 'stereo' ? 'flat' : 'stereo');
        break;
      case 'diag:copy': {
        this.report = this.buildReport();
        void this.copyReport();
        this.panel.markDirty();
        break;
      }
      default:
        break;
    }
  }
}
