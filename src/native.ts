// Warstwa natywna (Capacitor). W przeglądarce wszystko jest „no-op”,
// więc ten sam kod działa i na Vercelu, i w APK.

export function isNative(): boolean {
  const cap = (window as any).Capacitor;
  return !!(cap && typeof cap.isNativePlatform === 'function' && cap.isNativePlatform());
}

export async function initNative(): Promise<void> {
  if (!isNative()) return;

  // ukryj pasek statusu i nawigację — pełny ekran jak w goglach VR
  try {
    const { StatusBar } = await import('@capacitor/status-bar');
    await StatusBar.hide();
    await StatusBar.setOverlaysWebView({ overlay: true });
  } catch {
    /* plugin niedostępny — ignorujemy */
  }

  // wymuś poziomą orientację
  try {
    const { ScreenOrientation } = await import('@capacitor/screen-orientation');
    await ScreenOrientation.lock({ orientation: 'landscape' });
  } catch {
    /* ignorujemy */
  }

  // zgoda na kamerę — pytamy ZANIM telefon trafi do headsetu
  try {
    const { Camera } = await import('@capacitor/camera');
    const status = await Camera.checkPermissions();
    if (status.camera !== 'granted') {
      await Camera.requestPermissions({ permissions: ['camera'] });
    }
  } catch {
    /* brak zgody => śledzenie dłoni po prostu nie wystartuje */
  }
}

/** Trzyma ekran włączony także poza przeglądarką (Wake Lock API działa w WebView). */
export async function keepAwake(): Promise<void> {
  try {
    const wl = (navigator as any).wakeLock;
    if (wl?.request) await wl.request('screen');
  } catch {
    /* ignorujemy */
  }
}
