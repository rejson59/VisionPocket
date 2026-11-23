import * as THREE from 'three';
import { C, alpha } from '../ui/theme';
import { button, card, icon, panelChrome, text, wrapText } from '../ui/draw';
import type { Panel } from '../ui/Panel';
import type { AppHost, VrApp } from './types';
import { getGlowTexture } from '../ui/textures';
import { sfx } from '../core/sfx';

interface Target {
  group: THREE.Group;
  hitMesh: THREE.Mesh;
  base: THREE.Vector3;
  phase: number;
  speed: number;
  color: number;
}

interface Burst {
  sprite: THREE.Sprite;
  age: number;
  life: number;
}

const BEST_KEY = 'visionpocket.game.best';

/** „Neonowe cele” — strzelanie szczypaniem w przestrzeni wokół głowy. */
export class GameApp implements VrApp {
  id = 'game';
  title = 'Gra';
  subtitle = 'Neonowe cele';
  icon = 'target' as const;
  accent = C.magenta;
  group = new THREE.Group();
  hint: string | null = 'Szczypnij w stronę celu — laser leci z Twojego palca';

  private targets: Target[] = [];
  private bursts: Burst[] = [];
  private rays: { line: THREE.Line; age: number }[] = [];
  private score = 0;
  private best = 0;
  private combo = 1;
  private timeLeft = 60;
  private playing = false;
  private hits = 0;
  private shots = 0;
  private uiTimer = 0;

  constructor(private host: AppHost, private panel: Panel) {
    try {
      this.best = Number(localStorage.getItem(BEST_KEY) ?? 0) || 0;
    } catch {
      this.best = 0;
    }
    for (let i = 0; i < 5; i++) this.spawn(i);
  }

  enter(): void {
    this.resetTargets();
    this.playing = false;
    this.score = 0;
    this.timeLeft = 60;
    this.combo = 1;
    this.panel.markDirty();
  }

  exit(): void {
    this.playing = false;
    this.clearEffects();
  }

  private resetTargets(): void {
    for (const t of this.targets) this.group.remove(t.group);
    this.targets = [];
    for (let i = 0; i < 5; i++) this.spawn(i);
  }

  private clearEffects(): void {
    for (const b of this.bursts) this.group.remove(b.sprite);
    this.bursts = [];
    for (const r of this.rays) this.group.remove(r.line);
    this.rays = [];
  }

  private spawn(index: number, depthHint?: number): void {
    const group = new THREE.Group();
    const color = [0x35e0ff, 0xff3ea5, 0xb6ff5a, 0x8b7bff, 0xffc65a][index % 5];

    const core = new THREE.Mesh(
      new THREE.IcosahedronGeometry(0.13, 1),
      new THREE.MeshBasicMaterial({ color, toneMapped: false }),
    );
    const wire = new THREE.Mesh(
      new THREE.IcosahedronGeometry(0.2, 1),
      new THREE.MeshBasicMaterial({
        color,
        wireframe: true,
        transparent: true,
        opacity: 0.65,
        blending: THREE.AdditiveBlending,
        toneMapped: false,
      }),
    );
    const glow = new THREE.Sprite(
      new THREE.SpriteMaterial({
        map: getGlowTexture(),
        color,
        transparent: true,
        opacity: 0.5,
        blending: THREE.AdditiveBlending,
        depthWrite: false,
      }),
    );
    glow.scale.setScalar(0.9);
    group.add(core, wire, glow);

    const dist = 1.9 + Math.random() * 1.7;
    const angle = (Math.random() - 0.5) * 1.7;
    const base = new THREE.Vector3(
      Math.sin(angle) * dist,
      -0.35 + Math.random() * 1.15,
      -Math.cos(angle) * dist,
    );
    group.position.copy(base);
    this.group.add(group);

    this.targets.push({
      group,
      hitMesh: core,
      base,
      phase: Math.random() * Math.PI * 2,
      speed: 0.35 + Math.random() * 0.5,
      color,
    });
    void depthHint;
  }

  update(dt: number): void {
    const t = performance.now() / 1000;

    for (const target of this.targets) {
      const bob = Math.sin(t * target.speed + target.phase) * 0.16;
      target.group.position.set(target.base.x, target.base.y + bob, target.base.z);
      target.group.rotation.y += dt * 0.8;
      target.group.rotation.x += dt * 0.35;
    }

    // efekty trafień
    for (let i = this.bursts.length - 1; i >= 0; i--) {
      const burst = this.bursts[i];
      burst.age += dt;
      const k = burst.age / burst.life;
      burst.sprite.scale.setScalar(0.35 + k * 1.5);
      (burst.sprite.material as THREE.SpriteMaterial).opacity = Math.max(0, 1 - k);
      if (k >= 1) {
        this.group.remove(burst.sprite);
        this.bursts.splice(i, 1);
      }
    }

    // smugi strzałów
    for (let i = this.rays.length - 1; i >= 0; i--) {
      const ray = this.rays[i];
      ray.age += dt;
      (ray.line.material as THREE.LineBasicMaterial).opacity = Math.max(0, 0.85 - ray.age * 4);
      if (ray.age > 0.25) {
        this.group.remove(ray.line);
        this.rays.splice(i, 1);
      }
    }

    if (this.playing) {
      this.timeLeft = Math.max(0, this.timeLeft - dt);
      this.uiTimer += dt;
      if (this.uiTimer > 0.1) {
        this.uiTimer = 0;
        this.panel.markDirty();
      }
      if (this.timeLeft <= 0) this.finish();
    }
  }

  private finish(): void {
    this.playing = false;
    if (this.score > this.best) {
      this.best = this.score;
      try {
        localStorage.setItem(BEST_KEY, String(this.best));
      } catch {
        /* ignorujemy */
      }
      sfx.success();
      this.host.toast(`Nowy rekord: ${this.best} pkt!`);
    } else {
      sfx.back();
    }
    this.panel.markDirty();
  }

  onSpaceTap(origin: THREE.Vector3, dir: THREE.Vector3): void {
    if (!this.playing) return;
    this.shots++;

    const raycaster = new THREE.Raycaster(origin, dir.clone().normalize(), 0.1, 12);
    const meshes = this.targets.map((t) => t.hitMesh);
    const hits = raycaster.intersectObjects(meshes, false);

    // smuga lasera
    const end = hits.length
      ? hits[0].point
      : origin.clone().add(dir.clone().normalize().multiplyScalar(6));
    const geo = new THREE.BufferGeometry().setFromPoints([origin.clone(), end]);
    const line = new THREE.Line(
      geo,
      new THREE.LineBasicMaterial({
        color: 0xb6ff5a,
        transparent: true,
        opacity: 0.85,
        blending: THREE.AdditiveBlending,
        depthWrite: false,
      }),
    );
    line.frustumCulled = false;
    this.group.add(line);
    this.rays.push({ line, age: 0 });

    if (!hits.length) {
      // brak trafienia — combo spada, ale bez kary czasowej
      this.combo = 1;
      sfx.miss();
      this.panel.markDirty();
      return;
    }

    const hitMesh = hits[0].object as THREE.Mesh;
    const index = this.targets.findIndex((t) => t.hitMesh === hitMesh);
    if (index < 0) return;
    const target = this.targets[index];

    // eksplozja
    const sprite = new THREE.Sprite(
      new THREE.SpriteMaterial({
        map: getGlowTexture(),
        color: target.color,
        transparent: true,
        opacity: 1,
        blending: THREE.AdditiveBlending,
        depthWrite: false,
      }),
    );
    sprite.position.copy(target.group.position);
    this.group.add(sprite);
    this.bursts.push({ sprite, age: 0, life: 0.45 });

    this.group.remove(target.group);
    this.targets.splice(index, 1);
    this.hits++;
    this.combo = Math.min(8, this.combo + 1);
    this.score += 100 * this.combo;
    sfx.hit();
    this.spawn(Math.floor(Math.random() * 5));
    this.panel.markDirty();
  }

  draw(panel: Panel): void {
    const { ctx } = panel;
    const W = panel.pxW;
    const H = panel.pxH;
    panelChrome(ctx, W, H, { accent: this.accent });

    text(ctx, 'Neonowe cele', 40, 74, { size: 34, weight: 700 });
    text(ctx, 'Strzelaj szczypaniem w kierunku celu', 40, 108, { size: 19, color: C.muted });

    // wynik
    text(ctx, `${this.score}`, W - 40, 88, {
      size: 62,
      weight: 800,
      color: this.score > 0 ? C.lime : C.text,
      align: 'right',
    });
    text(ctx, `REKORD ${this.best}`, W - 42, 122, {
      size: 18,
      weight: 600,
      color: C.muted,
      align: 'right',
    });

    // pasek czasu
    const barY = 150;
    const barW = W - 80;
    const progress = this.timeLeft / 60;
    card(ctx, 40, barY, barW, 18, { fill: 'rgba(255,255,255,0.08)', r: 9 });
    card(ctx, 40, barY, Math.max(18, barW * progress), 18, {
      fill: progress > 0.3 ? C.cyan : C.red,
      r: 9,
    });
    text(ctx, `${Math.ceil(this.timeLeft)}s`, 40 + barW, barY + 46, {
      size: 20,
      weight: 600,
      color: progress > 0.3 ? C.textDim : C.red,
      align: 'right',
    });
    text(ctx, `COMBO x${this.combo}`, 44, barY + 46, {
      size: 20,
      weight: 700,
      color: this.combo > 1 ? C.magenta : C.muted,
    });

    if (!this.playing) {
      const bx = 120;
      const by = 250;
      const bw = W - 240;
      const bh = 470;
      card(ctx, bx, by, bw, bh, { fill: 'rgba(255,255,255,0.035)', stroke: alpha(this.accent, 0.35) });
      icon(ctx, 'target', bx + 110, by + 120, 92, this.accent, 4);
      text(
        ctx,
        this.timeLeft >= 60 && this.score === 0 ? 'Gotowy?' : 'Koniec czasu',
        bx + 200,
        by + 100,
        { size: 40, weight: 800 },
      );
      wrapText(
        ctx,
        'Patrz na świecący cel i szczypnij palcami (kciuk + wskazujący). Każde trafienie zwiększa mnożnik — seria daje nawet x8. Masz 60 sekund.',
        bx + 200,
        by + 150,
        bw - 320,
        { size: 23, color: C.textDim, lineHeight: 34, maxLines: 3 },
      );

      button(ctx, bx + 200, by + 290, 340, 92, this.score === 0 && this.timeLeft >= 60 ? 'Zacznij grę' : 'Zagraj jeszcze raz', {
        hover: panel.hoverId === 'game:start',
        primary: true,
        accent: this.accent,
        size: 26,
      });
      panel.hit('game:start', bx + 200, by + 290, 340, 92);

      if (this.score > 0) {
        text(ctx, `Wynik: ${this.score} · trafienia: ${this.hits}/${this.shots} · celność: ${Math.round((this.hits / Math.max(1, this.shots)) * 100)}%`, bx + 200, by + 410, {
          size: 21,
          color: C.textDim,
        });
      }
      return;
    }

    button(ctx, W - 250, H - 92, 210, 62, 'Przerwij', {
      hover: panel.hoverId === 'game:stop',
      accent: C.muted,
      size: 20,
    });
    panel.hit('game:stop', W - 250, H - 92, 210, 62);

    text(ctx, 'Szczypnij w przestrzeni — nie w panel', 40, H - 46, {
      size: 18,
      color: C.muted,
    });
  }

  click(panel: Panel, x: number, y: number, id: string | null): void {
    void panel;
    void x;
    void y;
    if (id === 'game:start') {
      this.score = 0;
      this.combo = 1;
      this.hits = 0;
      this.shots = 0;
      this.timeLeft = 60;
      this.playing = true;
      this.resetTargets();
      this.panel.markDirty();
      sfx.open();
      return;
    }
    if (id === 'game:stop') {
      this.playing = false;
      this.finish();
    }
  }
}
