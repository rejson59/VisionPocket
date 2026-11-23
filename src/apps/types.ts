import type * as THREE from 'three';
import type { Panel } from '../ui/Panel';
import type { IconName } from '../ui/draw';

export interface Diagnostics {
  mode: 'stereo' | 'flat';
  fps: number;
  renderMs: number;
  renderScale: number;
  pixelRatio: number;
  buffer: { width: number; height: number };
  gpu: string;
  sensorsLive: boolean;
  sensorsAbsolute: boolean;
  camera: string;
  hands: number;
  handMs: number;
  handHz: number;
  error: string | null;
  dpr: number;
  viewport: { width: number; height: number };
  ua: string;
}

export interface AppHost {
  open(id: string): void;
  back(): void;
  toast(message: string, ms?: number): void;
  recenter(): void;
  applySettings(): void;
  setPassthrough(on: boolean): Promise<void>;
  cameraRunning(): boolean;
  startCamera(): Promise<void>;
  stopCamera(): void;
  handCount(): number;
  requestFiles(): void;
  fps(): number;
  /** uruchamia pełnoekranowe wideo 360/2D — używane przez Kino i Galerię */
  openMedia(itemId: string): void;
  /** ręczne przełączenie VR (stereo) / 2D */
  setMode(mode: 'stereo' | 'flat'): void;
  currentMode(): 'stereo' | 'flat';
  diagnostics(): Diagnostics;
  /** tryb ratunkowy: minimalna jakość, maksymalna płynność */
  performanceMode(): void;
}

export interface VrApp {
  id: string;
  title: string;
  subtitle: string;
  icon: IconName;
  accent: string;
  /** obiekty 3D tej aplikacji (ekran kina, cele w grze itd.) */
  group: THREE.Group;
  enter?(): void;
  exit?(): void;
  draw(panel: Panel): void;
  click?(panel: Panel, x: number, y: number, id: string | null): void;
  update?(dt: number, panel: Panel): void;
  /** szczypnięcie/laser w przestrzeni, poza panelami (np. strzał w grze) */
  onSpaceTap?(origin: THREE.Vector3, dir: THREE.Vector3): void;
  hint?: string | null;
}
