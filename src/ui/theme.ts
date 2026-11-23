// Paleta i typografia VisionPocket. Duże litery i mocny kontrast —
// w tanim headsecie ostrość jest niższa niż w Queście, więc UI musi być „grubsze”.

export const C = {
  ink: '#05070f',
  panel: '#0b1120',
  panel2: '#111a2e',
  card: '#16203a',
  cardHover: '#1e2c4d',
  line: '#233252',
  text: '#eaf2ff',
  textDim: '#a9b8d4',
  muted: '#7d8dab',
  cyan: '#35e0ff',
  violet: '#8b7bff',
  magenta: '#ff3ea5',
  lime: '#b6ff5a',
  amber: '#ffc65a',
  red: '#ff5d6c',
  white: '#ffffff',
} as const;

export const ACCENT_ORDER = [C.cyan, C.violet, C.magenta, C.lime, C.amber] as const;

export function font(size: number, weight: number | string = 500): string {
  return `${weight} ${Math.round(size)}px system-ui, -apple-system, "Segoe UI", Roboto, sans-serif`;
}

export function alpha(hex: string, a: number): string {
  const h = hex.replace('#', '');
  const full = h.length === 3 ? h.split('').map((c) => c + c).join('') : h;
  const r = parseInt(full.slice(0, 2), 16);
  const g = parseInt(full.slice(2, 4), 16);
  const b = parseInt(full.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${a})`;
}
