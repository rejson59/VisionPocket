const cache = new Map<string, HTMLImageElement>();

/** Leniwie ładowane miniaturki do rysowania na panelach. */
export function getThumb(url: string): HTMLImageElement | null {
  const existing = cache.get(url);
  if (existing) return existing.complete && existing.naturalWidth > 0 ? existing : null;
  const img = new Image();
  img.decoding = 'async';
  img.src = url;
  cache.set(url, img);
  return null;
}

export function preloadThumb(url: string): void {
  getThumb(url);
}
