// Ustawienia VisionPocket — trwałe, z sensownymi domyślnymi dla flagowca.

export type Quality = 'low' | 'medium' | 'high';

export interface Settings {
  /** rozstaw oczu w metrach */
  ipd: number;
  /** pionowe pole widzenia renderowanej kamery (stopnie) */
  fov: number;
  /** baryłkowa dystorsja soczewek (0 = brak) */
  lens: number;
  /** dopasowanie obrazu do soczewek (1 = pełne) */
  zoom: number;
  /** aberracja chromatyczna na krawędziach */
  chromatic: boolean;
  /** winieta */
  vignette: number;
  /** jasność */
  brightness: number;
  /** skala renderu (0.5–1.0) — najważniejszy parametr wydajności */
  renderScale: number;
  quality: Quality;
  /** śledzenie dłoni kamerą */
  handTracking: boolean;
  /** rysowanie szkieletu dłoni */
  showSkeleton: boolean;
  /** obraz z kamery jako tło (tryb MR) */
  passthrough: boolean;
  /** obrót obrazu z kamery, jeśli producent telefonu go nie znormalizował */
  handRotate: 0 | 90 | 180 | 270;
  /** czas patrzenia potrzebny na klik (sekundy, 0 = wyłączone) */
  dwell: number;
  /** przybliżone pionowe FOV kamery telefonu (do wyliczenia głębi dłoni) */
  cameraFov: number;
  /** stała głębi dłoni — kalibracja odległości */
  handDepthK: number;
  /** pokaż licznik FPS */
  showFps: boolean;
}

export const QUALITY_PRESETS: Record<Quality, Partial<Settings>> = {
  low: { renderScale: 0.62, chromatic: false, vignette: 0.22, showSkeleton: false },
  medium: { renderScale: 0.78, chromatic: true, vignette: 0.18, showSkeleton: true },
  high: { renderScale: 0.95, chromatic: true, vignette: 0.14, showSkeleton: true },
};

export const DEFAULT_SETTINGS: Settings = {
  ipd: 0.063,
  fov: 92,
  lens: 0.12,
  zoom: 1.06,
  chromatic: true,
  vignette: 0.18,
  brightness: 1.05,
  renderScale: 0.8,
  quality: 'medium',
  handTracking: true,
  showSkeleton: true,
  passthrough: false,
  handRotate: 0,
  dwell: 1.15,
  cameraFov: 62,
  handDepthK: 0.075,
  showFps: true,
};

const STORAGE_KEY = 'visionpocket.settings.v1';

export const settings: Settings = { ...DEFAULT_SETTINGS };

export function loadSettings(): Settings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) Object.assign(settings, DEFAULT_SETTINGS, JSON.parse(raw));
  } catch {
    /* brak localStorage — działamy na domyślnych */
  }
  return settings;
}

export function saveSettings(): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch {
    /* ignorujemy */
  }
}

export function applyQuality(q: Quality): void {
  settings.quality = q;
  Object.assign(settings, QUALITY_PRESETS[q]);
  saveSettings();
}

export function resetSettings(): void {
  for (const key of Object.keys(DEFAULT_SETTINGS) as (keyof Settings)[]) {
    // @ts-expect-error — celowe kopiowanie po kluczach zgodnych typów
    settings[key] = DEFAULT_SETTINGS[key];
  }
  saveSettings();
}
