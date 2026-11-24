// Ustawienia VisionPocket — domyślne wartości nastawione na PŁYNNOSĆ.
// Lepiej 60 FPS przy niższej rozdzielczości niż 24 FPS „na papierze”.

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
  /** aberracja chromatyczna na krawędziach (kosztuje GPU) */
  chromatic: boolean;
  /** winieta */
  vignette: number;
  /** jasność */
  brightness: number;
  /** skala renderu (0.4–1.0) — najważniejszy parametr wydajności */
  renderScale: number;
  /** mnożnik rozdzielczości canvasu (0.7–2.0). 1 = piksele CSS, 2 = ostro */
  pixelRatio: number;
  /** automatyczne obniżanie jakości przy spadkach FPS */
  adaptive: boolean;
  quality: Quality;
  /** śledzenie dłoni kamerą */
  handTracking: boolean;
  /** ile razy na sekundę analizujemy obraz z kamery (6–30) */
  handHz: number;
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
  // „Niska” = maksymalna płynność: bez aberracji, bez szkieletu dłoni
  low: { renderScale: 0.55, pixelRatio: 1, chromatic: false, vignette: 0.2, showSkeleton: false },
  // „Średnia” = domyślna: ostry obraz, 60 FPS na flagowcu
  medium: { renderScale: 0.7, pixelRatio: 1, chromatic: false, vignette: 0.16, showSkeleton: true },
  // „Wysoka” = dla mocnych telefonów
  high: { renderScale: 0.9, pixelRatio: 1.25, chromatic: true, vignette: 0.12, showSkeleton: true },
};

export const DEFAULT_SETTINGS: Settings = {
  ipd: 0.063,
  fov: 92,
  lens: 0.12,
  zoom: 1.06,
  chromatic: false,
  vignette: 0.16,
  brightness: 1.05,
  renderScale: 0.72,
  // startujemy z pełną ostrością — GPU w telefonie to nie wąskie gardło,
  // a gdy FPS spadnie, automat sam zejdzie z rozdzielczością
  pixelRatio: 2,
  adaptive: true,
  quality: 'medium',
  handTracking: true,
  handHz: 12,
  showSkeleton: true,
  passthrough: true,
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
    // migracja ze starych, „ciężkich” ustawień
    if (settings.renderScale > 0.85) settings.renderScale = 0.75;
    if (settings.handHz > 20) settings.handHz = 15;
  } catch {
    /* brak localStorage — działamy na domyślnych */
  }
  // dopasuj ostrość do ekranu telefonu (max 2×, żeby nie zabić GPU)
  if (typeof window !== 'undefined' && window.devicePixelRatio) {
    settings.pixelRatio = Math.min(settings.pixelRatio, 2, Math.max(1, window.devicePixelRatio));
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

/** Tryb „ratunkowy”: maksymalna płynność, minimum efektów. */
export function applyPerformanceMode(): void {
  settings.quality = 'low';
  settings.renderScale = 0.55;
  settings.pixelRatio = 0.85;
  settings.chromatic = false;
  settings.vignette = 0.12;
  settings.showSkeleton = false;
  settings.handHz = 8;
  settings.adaptive = true;
  saveSettings();
}
