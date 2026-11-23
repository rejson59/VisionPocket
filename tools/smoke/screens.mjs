// Konwertuje zrzuty z testu dymnego (PNG) na lekkie JPEG do README.
// Uruchomienie: npm run screens  (najpierw npm run smoke)
import { readdirSync, mkdirSync, writeFileSync, existsSync } from 'node:fs';
import { createCanvas, loadImage } from '@napi-rs/canvas';

const SRC = process.env.SMOKE_OUT ?? 'tools/smoke/shots';
const OUT = 'docs/screens';
const WIDTH = 960;
const QUALITY = 82;
const KEEP = ['Home', 'Cinema', 'Gallery', 'Game', 'Notes', 'Settings', 'Help', 'Diagnostics'];

if (!existsSync(SRC)) {
  console.error(`Brak katalogu ${SRC} — najpierw uruchom: npm run smoke`);
  process.exit(1);
}
mkdirSync(OUT, { recursive: true });

const files = readdirSync(SRC).filter((f) => f.endsWith('.png'));
let total = 0;
for (const file of files) {
  const name = file.replace(/\.png$/, '');
  if (!KEEP.includes(name)) continue;
  const img = await loadImage(`${SRC}/${file}`);
  const scale = Math.min(1, WIDTH / img.width);
  const canvas = createCanvas(Math.round(img.width * scale), Math.round(img.height * scale));
  const ctx = canvas.getContext('2d');
  ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
  const jpeg = await canvas.encode('jpeg', QUALITY);
  const target = `${OUT}/${name.toLowerCase()}.jpg`;
  writeFileSync(target, jpeg);
  total += jpeg.length;
  console.log(`✓ ${target}  ${(jpeg.length / 1024).toFixed(0)} kB`);
}
console.log(`\nRazem ${(total / 1024).toFixed(0)} kB w ${OUT}`);
