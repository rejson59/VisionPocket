import * as THREE from 'three';
import { C, alpha } from '../ui/theme';
import { button, card, icon, panelChrome, pill, text, wrapText } from '../ui/draw';
import type { Panel } from '../ui/Panel';
import type { AppHost, VrApp } from './types';
import { APP_LIST } from './registry';

interface Tile {
  id: string;
  x: number;
  y: number;
  w: number;
  h: number;
}

/** Ekran główny — zegar, kafelki aplikacji, status sprzętu. */
export class HomeApp implements VrApp {
  id = 'home';
  title = 'VisionPocket';
  subtitle = 'Twój wirtualny dom';
  icon = 'home' as const;
  accent = C.cyan;
  group = new THREE.Group();
  private tiles: Tile[] = [];

  constructor(private host: AppHost) {}

  draw(panel: Panel): void {
    const { ctx } = panel;
    const W = panel.pxW;
    const H = panel.pxH;
    panelChrome(ctx, W, H, { accent: this.accent });

    const now = new Date();
    const time = now.toLocaleTimeString('pl-PL', { hour: '2-digit', minute: '2-digit' });
    const date = now.toLocaleDateString('pl-PL', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    });

    text(ctx, 'VisionPocket', 40, 74, { size: 30, weight: 800, color: C.text });
    text(ctx, 'VR w Twojej kieszeni', 40, 106, { size: 19, weight: 500, color: C.muted });

    // zegar po prawej
    text(ctx, time, W - 40, 92, {
      size: 74,
      weight: 800,
      color: C.text,
      align: 'right',
    });
    text(ctx, date, W - 42, 124, {
      size: 19,
      weight: 500,
      color: C.muted,
      align: 'right',
    });

    // kafelki
    const margin = 40;
    const gap = 24;
    const top = 170;
    const bottom = 200;
    const cols = 3;
    const rows = 2;
    const tw = (W - margin * 2 - gap * (cols - 1)) / cols;
    const th = (H - top - bottom - gap * (rows - 1)) / rows;

    this.tiles = [];
    APP_LIST.forEach((app, index) => {
      const col = index % cols;
      const row = Math.floor(index / cols);
      const x = margin + col * (tw + gap);
      const y = top + row * (th + gap);
      this.tiles.push({ id: app.id, x, y, w: tw, h: th });

      const hover = panel.hoverId === `tile:${app.id}`;
      const gradient = ctx.createLinearGradient(x, y, x + tw, y + th);
      gradient.addColorStop(0, hover ? alpha(app.accent, 0.22) : 'rgba(255,255,255,0.045)');
      gradient.addColorStop(1, hover ? alpha(app.accent, 0.08) : 'rgba(255,255,255,0.015)');
      card(ctx, x, y, tw, th, {
        fill: gradient,
        hover,
        accent: app.accent,
        glow: hover ? 26 : 0,
      });

      icon(ctx, app.icon, x + 62, y + 66, 46, app.accent, 3.4);
      text(ctx, app.title, x + 26, y + 148, { size: 34, weight: 700, color: C.text });
      wrapText(ctx, app.subtitle, x + 26, y + 186, tw - 52, {
        size: 21,
        color: C.muted,
        lineHeight: 28,
        maxLines: 2,
      });

      if (hover) {
        text(ctx, 'SZCZYPNIJ', x + tw - 26, y + th - 24, {
          size: 17,
          weight: 700,
          color: app.accent,
          align: 'right',
        });
      }

      panel.hit(`tile:${app.id}`, x, y, tw, th);
    });

    // pasek statusu
    const statusY = H - 112;
    const hands = this.host.handCount();
    let cursor = margin;
    cursor += pill(ctx, cursor, statusY, hands > 0 ? `Dłonie: ${hands}` : 'Szukam dłoni', {
      color: hands > 0 ? C.lime : C.amber,
      dot: true,
    }) + 12;
    cursor += pill(ctx, cursor, statusY, this.host.cameraRunning() ? 'Kamera: ON' : 'Kamera: OFF', {
      color: this.host.cameraRunning() ? C.cyan : C.muted,
    }) + 12;
    cursor += pill(ctx, cursor, statusY, `${Math.round(this.host.fps())} FPS`, {
      color: C.violet,
    }) + 12;

    button(ctx, W - 250, statusY - 8, 210, 62, 'Ustawienia', {
      hover: panel.hoverId === 'quick:settings',
      accent: C.violet,
      size: 21,
    });
    panel.hit('quick:settings', W - 250, statusY - 8, 210, 62);
  }

  click(panel: Panel, x: number, y: number, id: string | null): void {
    void panel;
    void x;
    void y;
    if (!id) return;
    if (id.startsWith('tile:')) this.host.open(id.slice(5));
    else if (id === 'quick:settings') this.host.open('settings');
  }

  hint = 'Patrz na kafelek i szczypnij palcami, aby otworzyć';
}
