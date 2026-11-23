// Test dymny VisionPocket: renderuje każdy ekran na canvasie (Skia) i klika
// wszystkie obszary interaktywne. Uruchamiaj: npm run smoke
// Zrzuty ekranu lądują w tools/smoke/shots/.
// Test dymny: renderuje każdy ekran VisionPocket na canvasie (Skia) i klika
// wszystkie zarejestrowane obszary. Łapie crashe w kodzie rysowania i handlerach.
import { createCanvas, type Canvas } from '@napi-rs/canvas';
import { mkdirSync, writeFileSync } from 'node:fs';

const OUT = process.env.SMOKE_OUT ?? 'tools/smoke/shots';
mkdirSync(OUT, { recursive: true });

const g = globalThis as any;

function fakeVideo(): any {
  return {
    style: {},
    muted: true,
    paused: true,
    currentTime: 0,
    duration: 100,
    videoWidth: 640,
    videoHeight: 360,
    playsInline: true,
    preload: 'auto',
    src: '',
    addEventListener() {},
    removeEventListener() {},
    removeAttribute() {},
    load() {},
    play: () => Promise.resolve(),
    pause() {},
    setAttribute() {},
    play_: undefined,
  };
}

g.window = g;
g.innerWidth = 1600;
g.innerHeight = 900;
g.devicePixelRatio = 1;
g.performance = g.performance ?? { now: () => Date.now() };
g.requestAnimationFrame = (cb: any) => setTimeout(() => cb(Date.now()), 16) as any;
g.cancelAnimationFrame = (id: any) => clearTimeout(id);
g.setTimeout = setTimeout;
g.screen = { orientation: { angle: 90, lock: () => Promise.resolve() } };
Object.defineProperty(g, 'navigator', {
  configurable: true,
  value: {
    mediaDevices: {},
    getGamepads: () => [],
    wakeLock: { request: () => Promise.resolve({ release: () => Promise.resolve() }) },
  },
});
g.localStorage = {
  store: new Map<string, string>(),
  getItem(k: string) {
    return this.store.get(k) ?? null;
  },
  setItem(k: string, v: string) {
    this.store.set(k, String(v));
  },
  removeItem(k: string) {
    this.store.delete(k);
  },
};
g.Image = class {
  style = {};
  addEventListener() {}
  removeEventListener() {}
  width = 640;
  height = 360;
  naturalWidth = 640;
  naturalHeight = 360;
  complete = false;
  decoding = 'async';
  crossOrigin = '';
  onload: any = null;
  onerror: any = null;
  set src(_v: string) {
    /* nigdy nie ładujemy — panel rysuje placeholder */
  }
};
g.HTMLVideoElement = class {};
g.HTMLImageElement = class {};
g.HTMLCanvasElement = class {};

function fakeCanvas(w = 320, h = 200): any {
  const c: any = createCanvas(w, h);
  c.style = {};
  return c;
}

const docStub = {
  createElement(tag: string) {
    if (tag === 'canvas') return fakeCanvas(320, 200);
    if (tag === 'video') return fakeVideo();
    if (tag === 'img') return new g.Image();
    return { style: {}, setAttribute() {}, appendChild() {}, addEventListener() {} };
  },
  createElementNS(_ns: string, tag: string) {
    return this.createElement(tag);
  },
  head: { appendChild() {} },
  body: { appendChild() {} },
  documentElement: { requestFullscreen: () => Promise.resolve() },
  getElementById: () => null,
  addEventListener() {},
  removeEventListener() {},
  hidden: false,
  fullscreenElement: null,
  baseURI: 'http://localhost/',
};
g.document = docStub;
g.URL.createObjectURL = () => 'blob:fake';
g.URL.revokeObjectURL = () => {};

// ---------------------------------------------------------------------------

const results: { name: string; ok: boolean; error?: string }[] = [];

async function main(): Promise<void> {
  const { Panel } = await import('../../src/ui/Panel.ts');
  const { HomeApp } = await import('../../src/apps/HomeApp.ts');
  const { CinemaApp } = await import('../../src/apps/CinemaApp.ts');
  const { GalleryApp } = await import('../../src/apps/GalleryApp.ts');
  const { GameApp } = await import('../../src/apps/GameApp.ts');
  const { NotesApp } = await import('../../src/apps/NotesApp.ts');
  const { SettingsApp } = await import('../../src/apps/SettingsApp.ts');
  const { HelpApp } = await import('../../src/apps/HelpApp.ts');
  const { DiagnosticsApp } = await import('../../src/apps/DiagnosticsApp.ts');
  const { PointerSystem } = await import('../../src/input/PointerSystem.ts');
  const { Hud } = await import('../../src/ui/Hud.ts');
  const THREE = await import('three');

  let toasts = 0;
  const host = {
    open() {},
    back() {},
    toast() {
      toasts++;
    },
    recenter() {},
    applySettings() {},
    setPassthrough: async () => {},
    cameraRunning: () => true,
    startCamera: async () => {},
    stopCamera: () => {},
    handCount: () => 1,
    requestFiles() {},
    fps: () => 58,
    openMedia() {},
    setMode() {},
    currentMode: () => 'stereo' as const,
    performanceMode() {},
    diagnostics: () => ({
      mode: 'stereo' as const,
      fps: 58,
      renderMs: 7.4,
      renderScale: 0.7,
      pixelRatio: 1,
      buffer: { width: 1200, height: 540 },
      gpu: 'Adreno 740',
      sensorsLive: true,
      sensorsAbsolute: true,
      camera: 'running',
      hands: 1,
      handMs: 14.2,
      handHz: 12,
      error: null,
      dpr: 2.6,
      viewport: { width: 1200, height: 540 },
      ua: 'smoke-test',
    }),
  };

  const panel = new Panel({ widthM: 1.72, heightM: 1.08, draw: () => {} });
  const dockPanel = new Panel({ widthM: 2.2, heightM: 0.3, draw: () => {} });

  const apps = [
    ['Home', new HomeApp(host)],
    ['Cinema', new CinemaApp(host, panel)],
    ['Gallery', new GalleryApp(host, panel)],
    ['Game', new GameApp(host, panel)],
    ['Notes', new NotesApp(host, panel)],
    ['Settings', new SettingsApp(host, panel)],
    ['Help', new HelpApp(host, panel)],
    ['Diagnostics', new DiagnosticsApp(host, panel)],
  ] as const;

  for (const [name, app] of apps) {
    try {
      app.enter?.();
      panel.draw = (p: any) => app.draw(p);
      panel.markDirty();
      panel.flush(0, true);
      writeFileSync(`${OUT}/${name}.png`, (panel.canvas as any).toBuffer('image/png'));
      const hits = panel.hits.length;
      if (hits === 0) throw new Error('brak obszarów klikalnych');

      // hover na każdym obszarze — sprawdza warianty rysowania
      for (const hit of [...panel.hits].slice(0, 40)) {
        panel.setHover(hit.id);
        panel.flush(0, true);
      }
      panel.setHover(null);

      // kliknięcia (z obsługą błędów w handlerach)
      let clicks = 0;
      for (const hit of [...panel.hits].slice(0, 20)) {
        if (app.click) {
          app.click(panel, hit.x + hit.w / 2, hit.y + hit.h / 2, hit.id);
          clicks++;
          panel.markDirty();
          panel.flush(0, true);
        }
      }

      // aktualizacja klatki
      for (let i = 0; i < 5; i++) app.update?.(0.016, panel);
      panel.flush(0, true);

      results.push({ name, ok: true });
      console.log(`✓ ${name}: ${hits} obszarów, ${clicks} kliknięć, ${toasts} toastów`);
    } catch (error) {
      results.push({ name, ok: false, error: String(error) });
      console.log(`✗ ${name}: ${(error as Error)?.stack ?? error}`);
    }
  }

  // --- HUD
  try {
    const hudCanvas = fakeCanvas(1600, 900) as HTMLCanvasElement;
    const hud = new Hud(hudCanvas);
    hud.draw({
      mode: 'stereo',
      dwell: 0.4,
      handCount: 2,
      pinching: true,
      fps: 55,
      hint: 'test',
      toast: 'test',
      gazeActive: false,
      tracking: true,
    });
    console.log('✓ HUD');
    results.push({ name: 'HUD', ok: true });
  } catch (error) {
    console.log('✗ HUD', error);
    results.push({ name: 'HUD', ok: false, error: String(error) });
  }

  // --- raycast: czy promień trafia w panel i w konkretny przycisk
  try {
    const scene = new THREE.Scene();
    panel.group.position.set(0, 0.02, -2.05);
    scene.add(panel.group);
    scene.updateMatrixWorld(true);
    const pointer = new PointerSystem();
    pointer.setPanels([panel]);
    const origin = new THREE.Vector3(0, 0.02, 0);
    const dir = new THREE.Vector3(0, 0, -1);
    let hitCount = 0;
    for (let i = 0; i < 20; i++) {
      const up = pointer.update({
        dt: 0.016,
        ray: { origin, dir: dir.clone(), source: 'gaze' },
        dwellEnabled: true,
        dwellTarget: 0.4,
        pinch: false,
        externalClick: i === 19,
        allowDwell: true,
      });
      if (up.hover) hitCount++;
    }
    if (hitCount === 0) throw new Error('promień nie trafił w panel');
    console.log(`✓ Raycast: trafienie w ${hitCount}/20 klatek`);
    results.push({ name: 'Raycast', ok: true });
  } catch (error) {
    console.log('✗ Raycast', error);
    results.push({ name: 'Raycast', ok: false, error: String(error) });
  }

  // --- dock (rysowanie)
  try {
    dockPanel.draw = (p: any) => {
      // rysujemy „dock” w uproszczeniu: sprawdzamy tylko podstawowe API panelu
      const { ctx } = p;
      const W = p.pxW;
      const H = p.pxH;
      ctx.fillStyle = '#0b1120';
      ctx.fillRect(0, 0, W, H);
      for (let i = 0; i < 8; i++) p.hit(`tile${i}`, i * 200, 0, 190, H);
    };
    dockPanel.flush(0, true);
    console.log(`✓ Dock: ${dockPanel.hits.length} kafelków`);
    results.push({ name: 'Dock', ok: true });
  } catch (error) {
    console.log('✗ Dock', error);
    results.push({ name: 'Dock', ok: false, error: String(error) });
  }

  const failed = results.filter((r) => !r.ok);
  console.log(`\n=== ${results.length - failed.length}/${results.length} OK ===`);
  if (failed.length) {
    console.log('BŁĘDY:', failed);
    process.exitCode = 1;
  }
}

await main();
