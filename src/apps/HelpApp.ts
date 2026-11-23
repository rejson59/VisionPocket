import * as THREE from 'three';
import { C, alpha } from '../ui/theme';
import { button, card, icon, panelChrome, text, wrapText } from '../ui/draw';
import type { Panel } from '../ui/Panel';
import type { AppHost, VrApp } from './types';

/** Pomoc: gesty, konfiguracja headsetu, bezpieczeństwo. */
export class HelpApp implements VrApp {
  id = 'help';
  title = 'Pomoc';
  subtitle = 'Gesty i pierwsze kroki';
  icon = 'help' as const;
  accent = C.textDim;
  group = new THREE.Group();
  hint: string | null = 'Bez dłoni: patrz na przycisk ~1 s (dwell)';

  constructor(private host: AppHost, private panel: Panel) {}

  enter(): void {
    this.panel.markDirty();
  }

  draw(panel: Panel): void {
    const { ctx } = panel;
    const W = panel.pxW;
    const H = panel.pxH;
    panelChrome(ctx, W, H, { accent: this.accent });

    text(ctx, 'Pomoc', 40, 74, { size: 36, weight: 700 });
    text(ctx, 'Wszystko, co trzeba wiedzieć w 30 sekund', 40, 106, {
      size: 19,
      color: C.muted,
    });

    button(ctx, W - 230, 46, 190, 60, 'Wstecz', {
      hover: panel.hoverId === 'help:home',
      accent: C.cyan,
      size: 20,
    });
    panel.hit('help:home', W - 230, 46, 190, 60);

    const cards = [
      {
        icon: 'hand' as const,
        color: C.lime,
        title: 'Szczypnięcie = klik',
        body: 'Zbliż kciuk i palec wskazujący. Kursor pokaże się na panelu, a błysk potwierdzi kliknięcie.',
      },
      {
        icon: 'target' as const,
        color: C.magenta,
        title: 'Laser z palca',
        body: 'Wyciągnięty palec wskazujący działa jak pilot: celuje tam, gdzie wskazujesz dłonią.',
      },
      {
        icon: 'clock' as const,
        color: C.amber,
        title: 'Bez dłoni: patrzenie',
        body: 'Patrz na przycisk około 1 sekundy — pierścień wypełni się i kliknie sam. Działa zawsze.',
      },
      {
        icon: 'camera' as const,
        color: C.cyan,
        title: 'Kamera i światło',
        body: 'Dłonie muszą być w kadrze kamery (ok. 25–60 cm). Włącz światło — w ciemności tracking pada.',
      },
      {
        icon: 'gear' as const,
        color: C.violet,
        title: 'Kalibracja obrazu',
        body: 'Ustawienia → IPD, korekcja soczewek i dopasowanie obrazu. 30 sekund i obraz jest ostry.',
      },
      {
        icon: 'screen' as const,
        color: C.textDim,
        title: 'Headset',
        body: 'Telefon wsuń ekranem do soczewek, kamerą na zewnątrz. Zdejmij etui i folię z kamery.',
      },
    ];

    const margin = 40;
    const gap = 20;
    const cols = 2;
    const tw = (W - margin * 2 - gap) / cols;
    const th = 250;
    cards.forEach((c, i) => {
      const col = i % cols;
      const row = Math.floor(i / cols);
      const x = margin + col * (tw + gap);
      const y = 160 + row * (th + 18);
      card(ctx, x, y, tw, th, { fill: 'rgba(255,255,255,0.035)' });
      icon(ctx, c.icon, x + 52, y + 54, 34, c.color, 3);
      text(ctx, c.title, x + 96, y + 62, { size: 26, weight: 700 });
      wrapText(ctx, c.body, x + 28, y + 120, tw - 56, {
        size: 21,
        color: C.textDim,
        lineHeight: 30,
        maxLines: 4,
      });
    });

    const warnY = 160 + 3 * (th + 18) + 6;
    if (warnY < H - 100) {
      card(ctx, margin, warnY, W - margin * 2, H - warnY - 40, {
        fill: alpha(C.amber, 0.08),
        stroke: alpha(C.amber, 0.35),
      });
      text(ctx, 'Bezpieczeństwo', margin + 26, warnY + 44, {
        size: 24,
        weight: 700,
        color: C.amber,
      });
      wrapText(
        ctx,
        'Zrób miejsce wokół siebie i usiądź. VR może powodować zawroty głowy — rób przerwy co 20 minut. Nigdy nie używaj w ruchu ani podczas prowadzenia.',
        margin + 26,
        warnY + 82,
        W - margin * 2 - 52,
        { size: 20, color: C.textDim, lineHeight: 28, maxLines: 2 },
      );
    }
  }

  click(panel: Panel, x: number, y: number, id: string | null): void {
    void panel;
    void x;
    void y;
    if (id === 'help:home') this.host.back();
  }
}
