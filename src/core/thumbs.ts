const cache = new Map<string, HTMLImageElement>();
let loadListener: (() => void) | null = null;

/** Panel musi się przerysować, gdy miniaturka w końcu się wczyta. */
export function setThumbLoadListener(cb: () => void): void {
  loadListener = cb;
}

/** Leniwie ładowane miniaturki do rysowania na panelach. */
export function getThumb(url: string): HTMLImageElement | null {
  const existing = cache.get(url);
  if (existing) return existing.complete && existing.naturalWidth > 0 ? existing : null;
  const img = new Image();
  img.decoding = 'async';
  img.onload = () => loadListener?.();
  img.src = url;
  cache.set(url, img);
  return null;
}

export function preloadThumb(url: string): void {
  getThumb(url);
}
