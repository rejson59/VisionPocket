import * as THREE from 'three';
import { StereoRenderer } from '../vr/StereoRenderer';
import { HeadTracker } from '../vr/HeadTracker';
import { Environment } from '../world/Environment';
import { HandTracker, type HandFrame } from '../input/HandTracker';
import { HandVisuals } from '../input/HandVisuals';
import { PointerSystem, type PointerRay } from '../input/PointerSystem';
import { Panel } from '../ui/Panel';
import { Hud } from '../ui/Hud';
import { C, alpha } from '../ui/theme';
import { icon, pill, rrPath, text } from '../ui/draw';
import { settings, saveSettings } from './settings';
import { mediaLibrary } from './media';
import { setThumbLoadListener } from './thumbs';
import { sfx } from './sfx';
import { APP_LIST } from '../apps/registry';
import type { AppHost, VrApp } from '../apps/types';
import { HomeApp } from '../apps/HomeApp';
import { CinemaApp } from '../apps/CinemaApp';
import { GalleryApp } from '../apps/GalleryApp';
import { GameApp } from '../apps/GameApp';
import { NotesApp } from '../apps/NotesApp';
import { SettingsApp } from '../apps/SettingsApp';
import { HelpApp } from '../apps/HelpApp';
import { DiagnosticsApp } from '../apps/DiagnosticsApp';
import { applyPerformanceMode } from './settings';
import type { Diagnostics } from '../apps/types';

const STAGE = { x: 0, y: 0.02, z: -2.05 };
const DOCK = { x: 0, y: -0.63, z: -1.82, tilt: -0.26 };

export class App implements AppHost {
  readonly renderer: StereoRenderer;
  readonly scene = new THREE.Scene();
  readonly headTracker = new HeadTracker();
  readonly hands: HandTracker;
  readonly handVisuals = new HandVisuals();
  readonly pointer = new PointerSystem();
  readonly environment = new Environment();

  private hud: Hud;
  private headSpace = new THREE.Group();
  private contentRoot = new THREE.Group();
  private stage: Panel;
  private dock: Panel;
  private apps = new Map<string, VrApp>();
  private current: VrApp;
  private mode: 'stereo' | 'flat' = 'stereo';

  private raf = 0;
  private running = false;
  private last = 0;
  private elapsed = 0;
  private fpsAvg = 60;
  private frames = 0;
  private fpsTimer = 0;
  private slowTime = 0;
  private uiRefresh = 0;
  private lastRenderScale = settings.renderScale;
  private lastRotate = settings.handRotate;
  private lastPixelRatio = settings.pixelRatio;

  private prevPinch = false;
  private externalClick = false;
  private mouse = { x: 0, y: 0, down: false, moved: false };
  private drag = { active: false, x: 0, y: 0, moved: 0 };
  private manualYaw = 0;
  private manualPitch = 0;
  private keys = new Set<string>();
  private gamepadLatch = false;
  private wakeLock: any = null;
  private toastText: string | null = null;
  private toastUntil = 0;
  private stagePanelRef: Panel;
  private renderMs = 0;
  private lastError: string | null = null;
  private modeForced = false;
  private watchdog = 0;
  private startedAt = 0;

  constructor(private canvas: HTMLCanvasElement, private hudCanvas: HTMLCanvasElement) {
    this.renderer = new StereoRenderer(canvas, settings);
    this.hands = new HandTracker(settings);
    this.hud = new Hud(hudCanvas);

    this.scene.fog = new THREE.FogExp2(0x05070f, 0.028);
    this.scene.add(this.environment.group);
    this.scene.add(this.contentRoot);
    this.scene.add(this.headSpace);
    this.headSpace.add(this.handVisuals.group);

    // panel sceny
    this.stage = new Panel({
      widthM: 1.72,
      heightM: 1.08,
      pxPerM: 900,
      draw: (panel) => this.current.draw(panel),
    });
    this.stage.group.position.set(STAGE.x, STAGE.y, STAGE.z);
    this.contentRoot.add(this.stage.group);
    this.stagePanelRef = this.stage;

    // dock
    this.dock = new Panel({
      widthM: 2.2,
      heightM: 0.3,
      pxPerM: 900,
      draw: (panel) => this.drawDock(panel),
    });
    this.dock.group.position.set(DOCK.x, DOCK.y, DOCK.z);
    this.dock.group.rotation.x = DOCK.tilt;
    this.contentRoot.add(this.dock.group);

    // aplikacje
    const host = this;
    this.apps.set('home', new HomeApp(host));
    this.apps.set('cinema', new CinemaApp(host, this.stage));
    this.apps.set('gallery', new GalleryApp(host, this.stage));
    this.apps.set('game', new GameApp(host, this.stage));
    this.apps.set('notes', new NotesApp(host, this.stage));
    this.apps.set('settings', new SettingsApp(host, this.stage));
    this.apps.set('help', new HelpApp(host, this.stage));
    this.current = this.apps.get('home')!;
    this.contentRoot.add(this.current.group);

    this.installErrorHandlers();
    this.pointer.setPanels([this.stage, this.dock]);
    setThumbLoadListener(() => this.stage.markDirty());
    this.bindInput();
    this.renderer.applySettings();
    this.environment.setPassthrough(settings.passthrough, this.hands.texture);
    this.handVisuals.skeletonEnabled = settings.showSkeleton;
  }

  // ------------------------------------------------------------------ start

  async start(mode: 'stereo' | 'flat'): Promise<void> {
    this.mode = mode;
    if (this.running) return;
    this.running = true;
    this.last = performance.now();

    await this.requestFullscreen();
    void this.requestWakeLock();

    this.startedAt = performance.now();
    this.startWatchdog();

    if (mode === 'stereo') {
      const ok = await this.headTracker.start();
      if (!ok) {
        this.mode = 'flat';
        this.toast(this.headTracker.error ?? 'Brak czujników ruchu — tryb 2D.', 5000);
      }
      // NIE przełączamy od razu: czujniki często startują po 1–3 s.
      // Watchdog niżej sam wróci do VR, gdy tylko pojawią się odczyty.
    }

    if (settings.handTracking && !this.hands.isRunning) {
      void this.startCamera();
    }

    this.current.enter?.();
    this.stage.markDirty();
    this.dock.markDirty();
    this.loop();
  }

  async stop(): Promise<void> {
    this.running = false;
    if (this.watchdog) window.clearInterval(this.watchdog);
    this.watchdog = 0;
    cancelAnimationFrame(this.raf);
    this.raf = 0;
    this.hands.stop();
    this.headTracker.stop();
    try {
      await this.wakeLock?.release?.();
    } catch {
      /* ignorujemy */
    }
    this.wakeLock = null;
  }

  private async requestFullscreen(): Promise<void> {
    try {
      const el = document.documentElement as any;
      if (!document.fullscreenElement && el.requestFullscreen) {
        await el.requestFullscreen({ navigationUI: 'hide' });
      }
    } catch {
      /* fullscreen może być zablokowany — nie blokujemy startu */
    }
    try {
      const orientation = screen.orientation as any;
      await orientation?.lock?.('landscape');
    } catch {
      /* orientacja może być zablokowana */
    }
  }

  private async requestWakeLock(): Promise<void> {
    try {
      const wl = (navigator as any).wakeLock;
      if (wl?.request) this.wakeLock = await wl.request('screen');
    } catch {
      /* brak wake locka — trudno */
    }
  }

  // ------------------------------------------------------------- AppHost API

  open(id: string): void {
    const next = this.apps.get(id);
    if (!next) return;
    if (this.current === next) return;
    this.current.exit?.();
    this.contentRoot.remove(this.current.group);
    this.current = next;
    this.contentRoot.add(next.group);
    next.enter?.();
    this.stage.markDirty();
    this.dock.markDirty();
    this.pointer.reset();
    sfx.open();
  }

  back(): void {
    if (this.current.id !== 'home') {
      this.open('home');
      sfx.back();
    }
  }

  toast(message: string, ms = 2600): void {
    this.toastText = message;
    this.toastUntil = performance.now() + ms;
  }

  recenter(): void {
    this.headTracker.recenter();
    this.manualYaw = 0;
    this.manualPitch = 0;
    this.toast('Widok wyśrodkowany.');
  }

  applySettings(): void {
    saveSettings();
    this.renderer.applySettings();
    const scaleChanged = Math.abs(settings.renderScale - this.lastRenderScale) > 0.001;
    const pixelChanged = Math.abs(settings.pixelRatio - this.lastPixelRatio) > 0.001;
    if (scaleChanged || pixelChanged) {
      this.lastRenderScale = settings.renderScale;
      this.lastPixelRatio = settings.pixelRatio;
      this.renderer.resize();
    }
    if (settings.handRotate !== this.lastRotate) {
      this.lastRotate = settings.handRotate;
      this.hands.setRotate(settings.handRotate);
    }
    this.environment.setPassthrough(settings.passthrough, this.hands.texture);
    this.handVisuals.skeletonEnabled = settings.showSkeleton;
    this.stage.markDirty();
    this.dock.markDirty();
  }

  async setPassthrough(on: boolean): Promise<void> {
    settings.passthrough = on;
    if (on && !this.hands.isRunning) {
      await this.startCamera();
    }
    this.environment.setPassthrough(on, this.hands.texture);
    saveSettings();
    this.toast(on ? 'Tryb MR: widzisz otoczenie z kamery.' : 'Tryb VR: pełne otoczenie.');
  }

  cameraRunning(): boolean {
    return this.hands.isRunning;
  }

  async startCamera(): Promise<void> {
    if (this.hands.isRunning) return;
    await this.hands.start();
    if (this.hands.status === 'running') {
      this.environment.setPassthrough(settings.passthrough, this.hands.texture);
      this.toast('Kamera gotowa — pokaż dłoń przed sobą.');
    } else {
      this.toast(this.hands.error ?? 'Nie udało się uruchomić kamery.');
      sfx.error();
    }
  }

  stopCamera(): void {
    this.hands.stop();
    this.environment.setPassthrough(false, null);
    this.toast('Kamera wyłączona.');
  }

  handCount(): number {
    return this.hands.getHands().length;
  }

  fps(): number {
    return this.fpsAvg;
  }

  requestFiles(): void {
    const input = document.getElementById('files') as HTMLInputElement | null;
    try {
      input?.click();
    } catch {
      /* przeglądarka może zablokować okno bez gestu */
    }
    this.toast('Jeśli okno się nie otworzyło: dodaj pliki na ekranie startowym, zanim włożysz telefon do headsetu.', 4200);
  }

  openMedia(itemId: string): void {
    const item = mediaLibrary.get(itemId);
    if (!item) return;
    this.open(item.kind === 'video' ? 'cinema' : 'gallery');
  }

  currentMode(): 'stereo' | 'flat' {
    return this.mode;
  }

  /** Ręczne przełączenie trybu — wyłącza automatyczny watchdog. */
  setMode(next: 'stereo' | 'flat'): void {
    this.modeForced = true;
    if (this.mode === next) return;
    this.mode = next;
    this.toast(next === 'stereo' ? 'Tryb VR: obraz stereo dla dwojga oczu.' : 'Tryb 2D: jeden obraz.', 3000);
    this.stage.markDirty();
    this.dock.markDirty();
    if (next === 'stereo' && !this.headTracker.isLive) {
      void this.headTracker.start();
    }
  }

  performanceMode(): void {
    applyPerformanceMode();
    this.lastRenderScale = settings.renderScale;
    this.lastRotate = settings.handRotate;
    this.renderer.resize();
    this.renderer.applySettings();
    this.handVisuals.skeletonEnabled = settings.showSkeleton;
    saveSettings();
    this.stage.markDirty();
    this.toast('Tryb płynności: niższa rozdzielczość, mniej efektów.', 3000);
  }

  diagnostics(): Diagnostics {
    const buf = this.renderer.bufferSize;
    return {
      mode: this.mode,
      fps: this.fpsAvg,
      renderMs: this.renderMs,
      renderScale: settings.renderScale,
      pixelRatio: settings.pixelRatio,
      buffer: { width: Math.round(buf.width), height: Math.round(buf.height) },
      gpu: this.renderer.gpuInfo,
      sensorsLive: this.headTracker.isLive,
      sensorsAbsolute: this.headTracker.absolute,
      camera: this.hands.status,
      hands: this.hands.getHands().length,
      handMs: this.hands.inferenceMs,
      handHz: this.hands.measuredHz,
      error: this.hands.error ?? this.lastError,
      dpr: window.devicePixelRatio || 1,
      viewport: { width: window.innerWidth, height: window.innerHeight },
      ua: navigator.userAgent,
    };
  }

  /** Pilnuje czujników ruchu: nie wyłącza VR na stałe, tylko czeka i wraca. */
  private startWatchdog(): void {
    if (this.watchdog) window.clearInterval(this.watchdog);
    this.watchdog = window.setInterval(() => {
      if (!this.running || this.modeForced) return;
      const live = this.headTracker.isLive;
      const age = (performance.now() - this.startedAt) / 1000;
      if (!live && this.mode === 'stereo' && age > 6) {
        this.mode = 'flat';
        this.toast('Brak czujników ruchu — tryb 2D. Wrócę do VR, gdy zadziałają.', 5000);
      } else if (live && this.mode === 'flat') {
        this.mode = 'stereo';
        this.toast('Czujniki działają — wracam do VR.', 2500);
      }
    }, 1000);
  }

  /** Pokazuje błędy na wierzchu — bez tego „nie działa” jest nie do zdiagnozowania. */
  private installErrorHandlers(): void {
    const show = (message: string) => {
      this.lastError = message.slice(0, 240);
      let banner = document.getElementById('err-banner');
      if (!banner) {
        banner = document.createElement('div');
        banner.id = 'err-banner';
        document.body.appendChild(banner);
      }
      banner.textContent = `VisionPocket — błąd: ${this.lastError}`;
      banner.classList.add('show');
      window.setTimeout(() => banner?.classList.remove('show'), 12000);
    };
    window.addEventListener('error', (event) => show(String(event.message ?? event.error ?? 'nieznany błąd')));
    window.addEventListener('unhandledrejection', (event) => {
      const reason = event.reason;
      show(reason instanceof Error ? reason.message : String(reason));
    });
  }

  // ------------------------------------------------------------------- loop

  private loop = (): void => {
    if (!this.running) return;
    this.raf = requestAnimationFrame(this.loop);
    try {
      this.frame();
    } catch (error) {
      this.lastError = error instanceof Error ? error.message : String(error);
      console.error(error);
    }
  };

  private frame(): void {

    const now = performance.now();
    const dt = Math.min(0.1, Math.max(0.0001, (now - this.last) / 1000));
    this.last = now;
    this.elapsed += dt;

    this.uiRefresh += dt;
    if (this.uiRefresh > 10) {
      this.uiRefresh = 0;
      this.stage.markDirty();
    }

    this.frames++;
    this.fpsTimer += dt;
    if (this.fpsTimer >= 0.5) {
      this.fpsAvg = this.frames / this.fpsTimer;
      this.adjustQuality(dt);
      this.frames = 0;
      this.fpsTimer = 0;
    }

    this.updateHead(dt);
    const hands = settings.handTracking ? this.hands.getHands() : [];
    const ray = this.computeRay(hands);
    const pinch = this.anyPinch(hands);
    const pinchEdge = pinch && !this.prevPinch;
    this.prevPinch = pinch;

    const update = this.pointer.update({
      dt,
      ray,
      dwellEnabled: settings.dwell > 0,
      dwellTarget: settings.dwell,
      pinch: pinchEdge,
      externalClick: this.externalClick,
      allowDwell: hands.length === 0,
    });
    this.externalClick = false;

    if (update.click) {
      this.handlePanelClick(update.click.panel, update.click.id, update.click.px, update.click.py);
    }
    if (update.hoverChanged) sfx.hover();

    const tappedSpace = (pinchEdge || this.wasExternalClick) && !update.click;
    if (tappedSpace && this.current.onSpaceTap) {
      this.current.onSpaceTap(ray.origin, ray.dir);
    }
    this.wasExternalClick = false;

    this.updateLaser(update.hover?.point ?? null, hands, ray);
    this.handVisuals.update(hands, this.laserPath);

    this.current.update?.(dt, this.stage);
    this.environment.update(dt, this.elapsed);

    this.stage.flush(now);
    this.dock.flush(now);

    const r0 = performance.now();
    this.renderer.render(this.mode, this.scene);
    this.renderMs = this.renderMs * 0.85 + (performance.now() - r0) * 0.15;

    this.drawHud(update.dwell, hands, pinch);
  }

  /** Automatyczne schodzenie z jakością — po kolei, aż FPS wróci do normy. */
  private adjustQuality(_dt: number): void {
    if (!settings.adaptive) return;
    if (this.fpsAvg >= 46) {
      this.slowTime = Math.max(0, this.slowTime - 0.5);
      return;
    }

    this.slowTime += 0.5;
    // reagujemy po ~1.5 s, nie po 4 s
    if (this.slowTime < 1.5) return;
    this.slowTime = 0;

    if (settings.renderScale > 0.52) {
      settings.renderScale = Math.max(0.52, Number((settings.renderScale - 0.08).toFixed(2)));
      this.lastRenderScale = settings.renderScale;
    this.lastPixelRatio = settings.pixelRatio;
      this.renderer.resize();
      saveSettings();
      this.toast('Obniżam rozdzielczość, żeby utrzymać płynność.', 1800);
      return;
    }
    if (settings.pixelRatio > 0.8) {
      settings.pixelRatio = 0.8;
      this.lastPixelRatio = 0.8;
      this.renderer.resize();
      this.toast('Niższa rozdzielczość ekranu (tryb płynności).', 1800);
      return;
    }
    if (settings.chromatic) {
      settings.chromatic = false;
      this.renderer.applySettings();
      return;
    }
    if (settings.showSkeleton) {
      settings.showSkeleton = false;
      this.handVisuals.skeletonEnabled = false;
      return;
    }
    if (settings.handHz > 8) {
      settings.handHz = 8;
      this.toast('Rzadsza analiza dłoni (8×/s).', 1800);
      return;
    }
    this.stage.markDirty();
  }

  private updateHead(dt: number): void {
    // klawiatura obraca widok w trybie 2D
    if (this.mode === 'flat') {
      const speed = 1.6 * dt;
      if (this.keys.has('arrowleft') || this.keys.has('a')) this.manualYaw += speed;
      if (this.keys.has('arrowright') || this.keys.has('d')) this.manualYaw -= speed;
      if (this.keys.has('arrowup') || this.keys.has('w')) this.manualPitch = Math.min(1.2, this.manualPitch + speed);
      if (this.keys.has('arrowdown') || this.keys.has('s')) this.manualPitch = Math.max(-1.2, this.manualPitch - speed);
      this.headTracker.setManual(this.manualYaw + this.dragOffset.yaw, this.manualPitch + this.dragOffset.pitch);
    }

    const q = this.headTracker.update(dt);
    this.renderer.setHeadQuaternion(q);
    this.headSpace.quaternion.copy(q);
  }

  private dragOffset = { yaw: 0, pitch: 0 };

  private anyPinch(hands: HandFrame[]): boolean {
    return hands.some((h) => h.pinching);
  }

  private computeRay(hands: HandFrame[]): PointerRay {
    const q = this.renderer.head.quaternion;
    const forward = new THREE.Vector3(0, 0, -1).applyQuaternion(q);
    const eye = new THREE.Vector3(0, 0, 0);

    // dłoń ma priorytet, gdy jest widoczna
    const hand = hands.length ? hands.reduce((a, b) => (a.confidence > b.confidence ? a : b)) : null;
    if (hand) {
      const tipWorld = hand.tip.clone().applyQuaternion(q);
      const dir = tipWorld.clone().normalize();
      if (dir.lengthSq() < 1e-6) return { origin: eye, dir: forward, source: 'gaze' };
      return { origin: tipWorld, dir, source: 'hand' };
    }

    if (this.mode === 'flat' && (this.mouse.x !== 0 || this.mouse.y !== 0)) {
      this.renderer.head.updateMatrixWorld(true);
      const ndc = new THREE.Vector2(this.mouse.x, this.mouse.y);
      const raycaster = new THREE.Raycaster();
      raycaster.setFromCamera(ndc, this.renderer.flatEye);
      return {
        origin: raycaster.ray.origin.clone(),
        dir: raycaster.ray.direction.clone().normalize(),
        source: 'mouse',
      };
    }

    return { origin: eye, dir: forward, source: 'gaze' };
  }

  private laserPath: { origin: THREE.Vector3; end: THREE.Vector3 } | null = null;

  private updateLaser(
    hoverPoint: THREE.Vector3 | null,
    hands: HandFrame[],
    ray: PointerRay,
  ): void {
    const hand = hands.find((h) => h.handedness === 'Right') ?? hands[0] ?? null;
    if (!hand) {
      this.laserPath = null;
      return;
    }
    const q = this.renderer.head.quaternion;
    const origin = hand.tip.clone().applyQuaternion(q);
    const end = hoverPoint
      ? hoverPoint.clone()
      : origin.clone().add(ray.dir.clone().multiplyScalar(3));
    this.laserPath = { origin, end };
  }

  // -------------------------------------------------------------------- HUD

  private drawHud(dwell: number, hands: HandFrame[], pinching: boolean): void {
    const now = performance.now();
    const toast = this.toastText && now < this.toastUntil ? this.toastText : null;
    if (this.toastText && now >= this.toastUntil) this.toastText = null;

    let hint = this.current.hint ?? null;
    if (!settings.handTracking) hint = 'Dłonie wyłączone — patrz na przycisk ok. 1 s (dwell)';
    else if (hands.length === 0) hint = 'Pokaż dłoń przed kamerą — albo patrz na przycisk ~1 s';

    this.hud.draw({
      mode: this.mode,
      dwell,
      handCount: hands.length,
      pinching,
      fps: settings.showFps ? this.fpsAvg : 0,
      hint,
      toast,
      gazeActive: hands.length === 0,
      tracking: settings.handTracking && this.hands.isRunning,
    });
  }

  // ------------------------------------------------------------------- dock

  private drawDock(panel: Panel): void {
    const { ctx } = panel;
    const W = panel.pxW;
    const H = panel.pxH;
    ctx.clearRect(0, 0, W, H);

    const bg = ctx.createLinearGradient(0, 0, W, H);
    bg.addColorStop(0, 'rgba(11,17,32,0.94)');
    bg.addColorStop(1, 'rgba(7,11,22,0.96)');
    rrPath(ctx, 0, 0, W, H, 26);
    ctx.fillStyle = bg;
    ctx.fill();
    ctx.strokeStyle = alpha(C.cyan, 0.22);
    ctx.lineWidth = 2;
    ctx.stroke();

    const items = [
      { id: 'home', title: 'Dom', icon: 'home' as const, accent: C.cyan },
      ...APP_LIST.map((a) => ({ id: a.id, title: a.title, icon: a.icon, accent: a.accent })),
      { id: 'exit', title: 'Wyjdź', icon: 'close' as const, accent: C.muted },
    ];
    const padding = 14;
    const tileW = (W - padding * 2 - 18 * (items.length - 1)) / items.length;
    const tileH = H - padding * 2;

    items.forEach((item, i) => {
      const x = padding + i * (tileW + 18);
      const y = padding;
      const active = this.current.id === item.id;
      const hover = panel.hoverId === `dock:${item.id}`;
      const accent = item.accent;

      const fill = active
        ? alpha(accent, 0.22)
        : hover
          ? 'rgba(255,255,255,0.09)'
          : 'rgba(255,255,255,0.035)';
      rrPath(ctx, x, y, tileW, tileH, 18);
      ctx.fillStyle = fill;
      ctx.fill();
      if (active || hover) {
        ctx.strokeStyle = alpha(accent, active ? 0.85 : 0.5);
        ctx.lineWidth = 2;
        ctx.stroke();
      }

      icon(ctx, item.icon, x + 40, y + tileH / 2, 30, active ? accent : C.textDim, 2.6);
      text(ctx, item.title, x + 68, y + tileH / 2 + 2, {
        size: 24,
        weight: active ? 700 : 500,
        color: active ? C.text : C.textDim,
        baseline: 'middle',
      });

      panel.hit(`dock:${item.id}`, x, y, tileW, tileH);
    });
  }

  // ------------------------------------------------------------------ input

  private wasExternalClick = false;

  private bindInput(): void {
    const canvas = this.canvas;

    // mysz / dotyk — tryb 2D
    canvas.addEventListener('pointermove', (e) => {
      const rect = canvas.getBoundingClientRect();
      const x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
      const y = -(((e.clientY - rect.top) / rect.height) * 2 - 1);
      this.mouse.x = THREE.MathUtils.clamp(x, -1, 1);
      this.mouse.y = THREE.MathUtils.clamp(y, -1, 1);
      if (this.drag.active && this.mode === 'flat') {
        const dx = e.clientX - this.drag.x;
        const dy = e.clientY - this.drag.y;
        this.drag.x = e.clientX;
        this.drag.y = e.clientY;
        this.drag.moved += Math.abs(dx) + Math.abs(dy);
        this.dragOffset.yaw -= dx * 0.0035;
        this.dragOffset.pitch = THREE.MathUtils.clamp(
          this.dragOffset.pitch - dy * 0.0035,
          -1.2,
          1.2,
        );
      }
    });

    canvas.addEventListener('pointerdown', (e) => {
      this.drag.active = true;
      this.drag.x = e.clientX;
      this.drag.y = e.clientY;
      this.drag.moved = 0;
      if (e.button === 0) {
        this.externalClick = true;
        this.wasExternalClick = true;
      }
    });

    canvas.addEventListener('pointerup', () => {
      this.drag.active = false;
    });

    canvas.addEventListener('contextmenu', (e) => e.preventDefault());

    window.addEventListener('keydown', (e) => {
      const key = e.key.toLowerCase();
      this.keys.add(key);
      if (key === ' ' || key === 'enter') {
        this.externalClick = true;
        this.wasExternalClick = true;
      }
      if (key === 'r') this.recenter();
      if (key === 'escape') this.back();
    });
    window.addEventListener('keyup', (e) => this.keys.delete(e.key.toLowerCase()));

    window.addEventListener('resize', () => this.handleResize());
    window.addEventListener('orientationchange', () => this.handleResize());
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) this.last = performance.now();
    });

    // pad Bluetooth — A/trigger klika, prawa gałka obraca widok
    const pollGamepad = () => {
      const pads = navigator.getGamepads?.() ?? [];
      const pad = Array.from(pads).find((p) => p && p.connected);
      if (pad) {
        const pressed = !!pad.buttons[0]?.pressed || !!pad.buttons[7]?.pressed;
        if (pressed && !this.gamepadLatch) {
          this.externalClick = true;
          this.wasExternalClick = true;
        }
        this.gamepadLatch = pressed;
        const ax = pad.axes[2] ?? 0;
        const ay = pad.axes[3] ?? 0;
        if (Math.abs(ax) > 0.2 || Math.abs(ay) > 0.2) {
          this.dragOffset.yaw -= ax * 0.03;
          this.dragOffset.pitch = THREE.MathUtils.clamp(this.dragOffset.pitch - ay * 0.03, -1.2, 1.2);
        }
      }
      requestAnimationFrame(pollGamepad);
    };
    requestAnimationFrame(pollGamepad);
  }

  private handleResize(): void {
    this.renderer.resize();
    this.hud.resize();
  }

  /** wywoływane po wyjściu z VR (powrót do ekranu startowego) */
  onExit: (() => void) | null = null;

  private handlePanelClick(panel: Panel, id: string | null, px: number, py: number): void {
    if (panel === this.stage) {
      this.current.click?.(panel, px, py, id);
      return;
    }
    if (panel === this.dock && id?.startsWith('dock:')) {
      const target = id.slice(5);
      if (target === 'exit') {
        void this.exitToMenu();
        return;
      }
      this.open(target);
      sfx.click();
    }
  }

  async exitToMenu(): Promise<void> {
    await this.stop();
    this.onExit?.();
  }

  get stagePanel(): Panel {
    return this.stagePanelRef;
  }

  get currentApp(): VrApp {
    return this.current;
  }
}
