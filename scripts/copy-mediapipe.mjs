// Kopiuje runtime MediaPipe Hands z node_modules do public/mediapipe.
// Dzięki temu tracker dłoni działa w 100% offline — także w APK.
import { mkdir, copyFile, readdir, stat } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';

const SRC = path.join(process.cwd(), 'node_modules', '@mediapipe', 'hands');
const DST = path.join(process.cwd(), 'public', 'mediapipe');

// Pliki potrzebne w przeglądarce / WebView. Modele .tflite są zapakowane
// w hands_solution_packed_assets.data, więc ich nie kopiujemy.
const FILES = [
  'hands.js',
  'hands.binarypb',
  'hands_solution_packed_assets.data',
  'hands_solution_packed_assets_loader.js',
  'hands_solution_simd_wasm_bin.js',
  'hands_solution_simd_wasm_bin.wasm',
  'hands_solution_simd_wasm_bin.data',
  'hands_solution_wasm_bin.js',
  'hands_solution_wasm_bin.wasm',
];

if (!existsSync(SRC)) {
  console.error('[mediapipe] Brak node_modules/@mediapipe/hands — uruchom npm install.');
  process.exit(1);
}

await mkdir(DST, { recursive: true });

let copied = 0;
let bytes = 0;
for (const file of FILES) {
  const from = path.join(SRC, file);
  const to = path.join(DST, file);
  if (!existsSync(from)) {
    console.warn(`[mediapipe] pominięto (brak): ${file}`);
    continue;
  }
  await copyFile(from, to);
  bytes += (await stat(to)).size;
  copied++;
}

const rest = await readdir(DST);
console.log(`[mediapipe] ${copied} plików, ${(bytes / 1e6).toFixed(1)} MB → public/mediapipe (${rest.length} plików w katalogu)`);
