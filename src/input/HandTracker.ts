import * as THREE from 'three';
import type { Settings } from '../core/settings';

type Landmark = { x: number; y: number; z: number };

interface MPResults {
  multiHandLandmarks?: Landmark[][];
  multiHandedness?: { label: string; score: number }[];
}

export type Handedness = 'Left' | 'Right';

export interface HandFrame {
  handedness: Handedness;
  confidence: number;
  /** 21 punktów w przestrzeni kamery (metrach), oś -Z do przodu */
  points: THREE.Vector3[];
  /** pozycja czubka palca wskazującego */
  tip: THREE.Vector3;
  /** środek dłoni */
  palm: THREE.Vector3;
  /** 0 = palce złączone, 1 = szeroko rozstawione */
  pinch: number;
  pinching: boolean;
  updatedAt: number;
}

const CONNECTIONS: [number, number][] = [
  [0, 1], [1, 2], [2, 3], [3, 4],
  [0, 5], [5, 6], [6, 7], [7, 8],
  [5, 9], [9, 10], [10, 11], [11, 12],
  [9, 13], [13, 14], [14, 15], [15, 16],
  [13, 17], [17, 18], [18, 19], [19, 20],
  [0, 17],
];

export const HAND_CONNECTIONS = CONNECTIONS;

function dist2(a: Landmark, b: Landmark): number {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

/**
 * Śledzenie dłoni z tylnej kamery telefonu (MediaPipe Hands, w pełni offline).
 *
 * Wydajność: analiza obrazu NIE może iść 25×/s — na telefonie każda klatka
 * MediaPipe blokuje główny wątek na ~20–40 ms (stąd spadki do ~24 FPS).
 * Dlatego: adaptacyjne tempo (12 Hz gdy dłoń widoczna, 5 Hz gdy nie),
 * mały bufor wejściowy i pomiar czasu wnioskowania.
 */
export class HandTracker {
  status: 'idle' | 'loading' | 'running' | 'denied' | 'error' = 'idle';
  error: string | null = null;
  video: HTMLVideoElement | null = null;
  stream: MediaStream | null = null;
  texture: THREE.VideoTexture | null = null;
  rotate: 0 | 90 | 180 | 270 = 0;

  /** statystyki do diagnostyki */
  inferenceMs = 0;
  measuredHz = 0;
  inferenceCount = 0;
  lastHandAt = 0;

  private hands: any = null;
  private detectCanvas: HTMLCanvasElement | null = null;
  private detectCtx: CanvasRenderingContext2D | null = null;
  private busy = false;
  private lastSend = 0;
  private frames = new Map<Handedness, HandFrame>();
  private previous = new Map<Handedness, THREE.Vector3[]>();
  private raf = 0;
  private loaded = false;
  private hzWindowStart = 0;
  private hzWindowCount = 0;
  private static readonly INPUT_W = 256;

  constructor(private settings: Settings) {}

  get isRunning(): boolean {
    return this.status === 'running';
  }

  async start(): Promise<void> {
    if (this.status === 'loading' || this.status === 'running') return;
    this.status = 'loading';
    this.error = null;

    try {
      await this.ensureVideo();
      await this.ensureMediapipe();
      this.status = 'running';
      this.loop();
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      this.error = message;
      this.status =
        message.includes('denied') || message.includes('NotAllowed') || message.includes('Permission')
          ? 'denied'
          : 'error';
      this.stop();
    }
  }

  private async ensureVideo(): Promise<void> {
    if (this.stream) return;
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error('Ta przeglądarka nie udostępnia kamery (wymagane HTTPS).');
    }
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: false,
      video: {
        facingMode: { ideal: 'environment' },
        width: { ideal: 640 },
        height: { ideal: 480 },
        // 20 fps wystarczy analizie dłoni i mocno ogranicza grzanie telefonu
        frameRate: { ideal: 20, max: 24 },
      },
    });
    this.stream = stream;
    const video = document.createElement('video');
    video.playsInline = true;
    video.muted = true;
    video.autoplay = true;
    video.setAttribute('playsinline', 'true');
    video.srcObject = stream;
    await video.play().catch(() => undefined);
    this.video = video;

    const texture = new THREE.VideoTexture(video);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.minFilter = THREE.LinearFilter;
    texture.magFilter = THREE.LinearFilter;
    texture.generateMipmaps = false;
    this.texture = texture;

    const size = this.rotatedSize(HandTracker.INPUT_W, (HandTracker.INPUT_W * 3) / 4);
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(size.w);
    canvas.height = Math.round(size.h);
    this.detectCanvas = canvas;
    this.detectCtx = canvas.getContext('2d', { alpha: false });
  }

  private rotatedSize(w: number, h: number): { w: number; h: number } {
    return this.rotate === 90 || this.rotate === 270 ? { w: h, h: w } : { w, h };
  }

  private async ensureMediapipe(): Promise<void> {
    if (this.loaded) return;
    const base = new URL('mediapipe/', document.baseURI).href;
    if (!(window as any).Hands) {
      await new Promise<void>((resolve, reject) => {
        const existing = document.querySelector<HTMLScriptElement>('script[data-mp="hands"]');
        if (existing) {
          existing.addEventListener('load', () => resolve());
          existing.addEventListener('error', () => reject(new Error('Nie udało się wczytać MediaPipe.')));
          return;
        }
        const script = document.createElement('script');
        script.src = `${base}hands.js`;
        script.async = true;
        script.dataset.mp = 'hands';
        script.onload = () => resolve();
        script.onerror = () => reject(new Error('Brak plików MediaPipe (public/mediapipe).'));
        document.head.appendChild(script);
      });
    }

    const HandsCtor = (window as any).Hands;
    if (!HandsCtor) throw new Error('MediaPipe Hands niedostępne.');

    this.hands = new HandsCtor({ locateFile: (file: string) => `${base}${file}` });
    this.hands.setOptions({
      maxNumHands: 2,
      modelComplexity: 0,
      minDetectionConfidence: 0.6,
      minTrackingConfidence: 0.45,
      selfieMode: false,
    });
    this.hands.onResults((results: MPResults) => this.onResults(results));
    this.loaded = true;
  }

  /** Ile ms czekamy między klatkami analizy — zależnie od tego, czy widzimy dłoń. */
  private get interval(): number {
    const target = Math.min(30, Math.max(6, this.settings.handHz || 12));
    const sinceHand = performance.now() - this.lastHandAt;
    const effective = sinceHand < 700 ? target : Math.max(5, Math.min(8, target * 0.6));
    return 1000 / effective;
  }

  private loop = (): void => {
    this.raf = requestAnimationFrame(this.loop);
    if (this.status !== 'running' || this.busy || !this.hands || !this.video) return;
    if (document.hidden) return;
    const now = performance.now();
    if (now - this.lastSend < this.interval) return;
    if (this.video.readyState < 2) return;
    this.lastSend = now;
    this.busy = true;
    this.grabFrame();
    const image = this.detectCanvas;
    if (!image) {
      this.busy = false;
      return;
    }
    const t0 = performance.now();
    Promise.resolve(this.hands.send({ image }))
      .catch(() => undefined)
      .finally(() => {
        const ms = performance.now() - t0;
        this.inferenceMs = this.inferenceMs * 0.8 + ms * 0.2;
        this.inferenceCount++;
        this.hzWindowCount++;
        if (now - this.hzWindowStart > 1000) {
          this.measuredHz = (this.hzWindowCount * 1000) / (now - this.hzWindowStart);
          this.hzWindowStart = now;
          this.hzWindowCount = 0;
        }
        this.busy = false;
      });
  };

  private grabFrame(): void {
    const video = this.video;
    const ctx = this.detectCtx;
    const canvas = this.detectCanvas;
    if (!video || !ctx || !canvas) return;
    const vw = video.videoWidth || 640;
    const vh = video.videoHeight || 480;
    if (vw < 2 || vh < 2) return;

    const targetW = canvas.width;
    const scale = targetW / vw;

    ctx.save();
    ctx.translate(canvas.width / 2, canvas.height / 2);
    if (this.rotate) ctx.rotate((this.rotate * Math.PI) / 180);
    const dw = vw * scale;
    const dh = vh * scale;
    ctx.drawImage(video, -dw / 2, -dh / 2, dw, dh);
    ctx.restore();
  }

  private onResults(results: MPResults): void {
    const now = performance.now();
    const landmarks = results.multiHandLandmarks ?? [];
    const handedness = results.multiHandedness ?? [];

    if (landmarks.length > 0) this.lastHandAt = now;

    for (let i = 0; i < landmarks.length; i++) {
      const lm = landmarks[i];
      if (!lm || lm.length < 21) continue;
      const labelRaw = handedness[i]?.label ?? 'Right';
      const label: Handedness = labelRaw === 'Left' ? 'Left' : 'Right';
      const score = handedness[i]?.score ?? 0.7;
      const frame = this.toFrame(lm, label, score, now);
      if (!frame) continue;
      this.frames.set(label, frame);
    }

    for (const [label, frame] of this.frames) {
      if (now - frame.updatedAt > 500) this.frames.delete(label);
      else if (!landmarks.length) frame.pinching = false;
    }
  }

  private toFrame(
    lm: Landmark[],
    label: Handedness,
    confidence: number,
    now: number,
  ): HandFrame | null {
    const aspect = this.detectCanvas
      ? this.detectCanvas.width / this.detectCanvas.height
      : 4 / 3;
    const span = Math.max(0.02, dist2(lm[0], lm[9]));
    const depth = THREE.MathUtils.clamp(this.settings.handDepthK / span, 0.16, 1.1);

    const tanV = Math.tan(THREE.MathUtils.degToRad(this.settings.cameraFov) / 2);
    const points: THREE.Vector3[] = [];
    for (const p of lm) {
      const x = (p.x - 0.5) * 2 * depth * tanV * aspect;
      const y = -(p.y - 0.5) * 2 * depth * tanV;
      const z = -(depth + p.z * depth * 0.55);
      points.push(new THREE.Vector3(x, y, z));
    }

    const prev = this.previous.get(label);
    if (prev && prev.length === points.length) {
      for (let i = 0; i < points.length; i++) points[i].lerp(prev[i], 0.42);
    }
    this.previous.set(label, points.map((p) => p.clone()));

    const pinchRatio = dist2(lm[4], lm[8]) / span;
    const pinch = THREE.MathUtils.clamp(1 - pinchRatio / 1.1, 0, 1);
    const prevFrame = this.frames.get(label);
    const thresholdOn = 0.52;
    const thresholdOff = 0.62;
    let pinching = prevFrame?.pinching ?? false;
    if (!pinching && pinchRatio < thresholdOn) pinching = true;
    else if (pinching && pinchRatio > thresholdOff) pinching = false;

    return {
      handedness: label,
      confidence,
      points,
      tip: points[8].clone(),
      palm: points[9].clone(),
      pinch,
      pinching,
      updatedAt: now,
    };
  }

  getHands(): HandFrame[] {
    return [...this.frames.values()];
  }

  get primary(): HandFrame | null {
    let best: HandFrame | null = null;
    for (const hand of this.frames.values()) {
      if (!best || hand.confidence > best.confidence) best = hand;
    }
    return best;
  }

  setRotate(deg: 0 | 90 | 180 | 270): void {
    this.rotate = deg;
    if (this.detectCanvas) {
      const size = this.rotatedSize(HandTracker.INPUT_W, (HandTracker.INPUT_W * 3) / 4);
      this.detectCanvas.width = Math.round(size.w);
      this.detectCanvas.height = Math.round(size.h);
    }
  }

  stop(): void {
    cancelAnimationFrame(this.raf);
    this.raf = 0;
    if (this.stream) {
      this.stream.getTracks().forEach((t) => t.stop());
      this.stream = null;
    }
    this.texture?.dispose();
    this.texture = null;
    this.video = null;
    this.frames.clear();
    this.previous.clear();
    this.inferenceMs = 0;
    this.measuredHz = 0;
    if (this.status === 'running') this.status = 'idle';
  }
}
