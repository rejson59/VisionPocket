import * as THREE from 'three';

/**
 * Śledzenie głowy w stylu Cardboard: żyroskop + akcelerometr z API
 * DeviceOrientation (system już je fuzjuje). Dodajemy własny offset
 * „recenter”, żeby móc ustawić przód tam, gdzie aktualnie patrzysz.
 */
export class HeadTracker {
  readonly quaternion = new THREE.Quaternion();
  active = false;
  absolute = false;
  error: string | null = null;

  private raw = new THREE.Quaternion();
  private target = new THREE.Quaternion();
  private offset = new THREE.Quaternion();
  private euler = new THREE.Euler();
  private q1 = new THREE.Quaternion(-Math.SQRT1_2, 0, 0, Math.SQRT1_2);
  private zee = new THREE.Vector3(0, 0, 1);
  private q0 = new THREE.Quaternion();
  private lastEvent = 0;
  private boundHandler = (e: DeviceOrientationEvent) => this.onOrientation(e);
  private boundAbsolute = (e: DeviceOrientationEvent) => this.onOrientation(e, true);

  async start(): Promise<boolean> {
    const anyDO = DeviceOrientationEvent as unknown as {
      requestPermission?: () => Promise<'granted' | 'denied'>;
    };
    if (typeof anyDO?.requestPermission === 'function') {
      try {
        const state = await anyDO.requestPermission();
        if (state !== 'granted') {
          this.error = 'Brak zgody na czujniki ruchu.';
          return false;
        }
      } catch {
        this.error = 'Nie udało się uzyskać dostępu do czujników ruchu.';
        return false;
      }
    }

    window.addEventListener('deviceorientationabsolute', this.boundAbsolute as EventListener, true);
    window.addEventListener('deviceorientation', this.boundHandler, true);
    return true;
  }

  stop(): void {
    window.removeEventListener('deviceorientationabsolute', this.boundAbsolute as EventListener, true);
    window.removeEventListener('deviceorientation', this.boundHandler, true);
  }

  get isLive(): boolean {
    return this.active && performance.now() - this.lastEvent < 1500;
  }

  private onOrientation(e: DeviceOrientationEvent, isAbsolute = false): void {
    if (e.alpha == null && e.beta == null && e.gamma == null) return;
    this.lastEvent = performance.now();
    if (isAbsolute || e.absolute) this.absolute = true;
    if (!this.active) this.active = true;

    const alpha = THREE.MathUtils.degToRad(e.alpha ?? 0);
    const beta = THREE.MathUtils.degToRad(e.beta ?? 0);
    const gamma = THREE.MathUtils.degToRad(e.gamma ?? 0);

    const screenAngle =
      (screen.orientation && typeof screen.orientation.angle === 'number'
        ? screen.orientation.angle
        : (window as unknown as { orientation?: number }).orientation) ?? 0;

    this.euler.set(beta, alpha, -gamma, 'YXZ');
    this.raw.setFromEuler(this.euler);
    this.raw.multiply(this.q1);
    this.raw.multiply(this.q0.setFromAxisAngle(this.zee, -THREE.MathUtils.degToRad(screenAngle)));

    this.target.copy(this.offset).multiply(this.raw);
  }

  /** Ustawia aktualny kierunek patrzenia jako „przód” sceny. */
  recenter(): void {
    this.euler.setFromQuaternion(this.target, 'YXZ');
    this.offset.setFromEuler(new THREE.Euler(0, -this.euler.y, 0, 'YXZ'));
    this.target.copy(this.offset).multiply(this.raw);
  }

  /** Ręczne sterowanie (tryb 2D na komputerze): kąty w radianach. */
  setManual(yaw: number, pitch: number): void {
    this.euler.set(pitch, yaw, 0, 'YXZ');
    this.target.setFromEuler(this.euler);
    this.active = true;
    this.lastEvent = performance.now();
  }

  update(dt: number): THREE.Quaternion {
    // lekkie wygładzenie — usuwa drgania czujnika, opóźnienie ~8 ms
    const t = 1 - Math.exp(-Math.max(0, dt) * 45);
    this.quaternion.slerp(this.target, Math.min(1, Math.max(0.05, t)));
    return this.quaternion;
  }
}
