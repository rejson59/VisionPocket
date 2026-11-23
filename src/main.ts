import './styles.css';
import { App } from './core/App';
import { loadSettings } from './core/settings';
import { mediaLibrary } from './core/media';
import { sfx } from './core/sfx';
import { initNative } from './native';


// Polyfill roundRect — starsze WebView (Android 9/10) go nie mają.
if (typeof CanvasRenderingContext2D !== 'undefined' && !(CanvasRenderingContext2D.prototype as any).roundRect) {
  (CanvasRenderingContext2D.prototype as any).roundRect = function (
    x: number,
    y: number,
    w: number,
    h: number,
    r: number | number[] = 0,
  ) {
    const radius = Array.isArray(r) ? r[0] : r;
    const rad = Math.min(radius, Math.abs(w) / 2, Math.abs(h) / 2);
    this.moveTo(x + rad, y);
    this.lineTo(x + w - rad, y);
    this.arcTo(x + w, y, x + w, y + rad, rad);
    this.lineTo(x + w, y + h - rad);
    this.arcTo(x + w, y + h, x + w - rad, y + h, rad);
    this.lineTo(x + rad, y + h);
    this.arcTo(x, y + h, x, y + h - rad, rad);
    this.lineTo(x, y + rad);
    this.arcTo(x, y, x + rad, y, rad);
    this.closePath();
    return this;
  };
}

const canvas = document.getElementById('vr') as HTMLCanvasElement | null;
const hudCanvas = document.getElementById('hud') as HTMLCanvasElement | null;
const boot = document.getElementById('boot');
const startVr = document.getElementById('start-vr');
const startFlat = document.getElementById('start-flat');
const camBtn = document.getElementById('cam-perm');
const camState = document.getElementById('cam-state');
const filesInput = document.getElementById('files') as HTMLInputElement | null;
const filesCount = document.getElementById('files-count');
const deviceLine = document.getElementById('device-line');

if (!canvas || !hudCanvas || !boot) {
  throw new Error('Brak elementów interfejsu.');
}

loadSettings();
void initNative();

let app: App;
try {
  app = new App(canvas, hudCanvas);
} catch (error) {
  boot.innerHTML = `<div class="boot-card"><h1>Brak WebGL</h1><p class="lead">Ta przeglądarka nie obsługuje WebGL 2, więc VisionPocket nie może wystartować. Spróbuj Chrome na Androidzie.</p></div>`;
  console.error(error);
  throw error;
}

app.onExit = () => {
  boot.classList.remove('hide');
  if (camState) camState.textContent = app.cameraRunning() ? 'kamera aktywna' : 'śledzenie dłoni';
};

// --- odblokowanie audio i wejścia pierwszym dotknięciem
const unlock = () => {
  sfx.unlock();
  window.removeEventListener('pointerdown', unlock);
  window.removeEventListener('keydown', unlock);
};
window.addEventListener('pointerdown', unlock);
window.addEventListener('keydown', unlock);

// --- przyciski startowe
startVr?.addEventListener('click', () => {
  sfx.unlock();
  void app.start('stereo');
  boot.classList.add('hide');
});

startFlat?.addEventListener('click', () => {
  sfx.unlock();
  void app.start('flat');
  boot.classList.add('hide');
});

// --- kamera (prosimy o zgodę ZANIM telefon trafi do headsetu)
camBtn?.addEventListener('click', async () => {
  if (!camState) return;
  camState.textContent = 'uruchamiam…';
  await app.startCamera();
  camState.textContent = app.cameraRunning() ? 'kamera aktywna ✓' : 'brak dostępu';
});

// --- pliki z telefonu
filesInput?.addEventListener('change', async () => {
  if (!filesInput.files || filesInput.files.length === 0) return;
  if (filesCount) filesCount.textContent = 'wczytuję…';
  const added = await mediaLibrary.addFiles(filesInput.files);
  const total = mediaLibrary.items.length;
  if (filesCount) filesCount.textContent = `${total} plików w bibliotece`;
  app.toast(added > 0 ? `Dodano ${added} plików.` : 'Nie udało się dodać plików.');
  filesInput.value = '';
});

// --- informacja o urządzeniu
if (deviceLine) {
  const hasOrientation = typeof DeviceOrientationEvent !== 'undefined';
  const hasCamera = !!navigator.mediaDevices?.getUserMedia;
  const hasSpeech =
    !!((window as any).SpeechRecognition || (window as any).webkitSpeechRecognition);
  const secure = window.isSecureContext;
  const chunks = [
    `${hasOrientation ? 'czujniki ✓' : 'czujniki ✕'}`,
    `${hasCamera ? 'kamera ✓' : 'kamera ✕'}`,
    `${hasSpeech ? 'dyktowanie ✓' : 'dyktowanie ✕'}`,
    `${secure ? 'HTTPS ✓' : 'brak HTTPS'}`,
  ];
  deviceLine.textContent = chunks.join(' · ');
}

// --- blokada zoomu / odświeżania gestami
document.addEventListener('gesturestart', (e) => e.preventDefault());
document.addEventListener('dblclick', (e) => e.preventDefault());
window.addEventListener('beforeunload', () => {
  void app.stop();
});

// tryb „wróć do menu” po wyjściu z pełnego ekranu
document.addEventListener('fullscreenchange', () => {
  if (!document.fullscreenElement && app.cameraRunning()) {
    app.toast('Wyszedłeś z pełnego ekranu. Dotknij ekranu, aby wrócić.');
  }
});
