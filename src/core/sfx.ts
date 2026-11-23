// Minimalny syntezator dźwięku — zero plików, zero pobierania, działa offline.

class Sfx {
  private ctx: AudioContext | null = null;
  private master: GainNode | null = null;
  enabled = true;

  private ensure(): AudioContext | null {
    if (!this.enabled) return null;
    if (!this.ctx) {
      const Ctor =
        window.AudioContext ||
        (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      if (!Ctor) return null;
      this.ctx = new Ctor();
      this.master = this.ctx.createGain();
      this.master.gain.value = 0.24;
      this.master.connect(this.ctx.destination);
    }
    if (this.ctx.state === 'suspended') void this.ctx.resume();
    return this.ctx;
  }

  unlock(): void {
    this.ensure();
  }

  private tone(
    freq: number,
    dur: number,
    type: OscillatorType,
    gain = 0.5,
    slideTo?: number,
  ): void {
    const ctx = this.ensure();
    if (!ctx || !this.master) return;
    const osc = ctx.createOscillator();
    const env = ctx.createGain();
    const now = ctx.currentTime;
    osc.type = type;
    osc.frequency.setValueAtTime(freq, now);
    if (slideTo) osc.frequency.exponentialRampToValueAtTime(Math.max(30, slideTo), now + dur);
    env.gain.setValueAtTime(0.0001, now);
    env.gain.exponentialRampToValueAtTime(gain, now + 0.012);
    env.gain.exponentialRampToValueAtTime(0.0001, now + dur);
    osc.connect(env);
    env.connect(this.master);
    osc.start(now);
    osc.stop(now + dur + 0.02);
  }

  click(): void {
    this.tone(760, 0.07, 'triangle', 0.35);
  }

  hover(): void {
    this.tone(1180, 0.035, 'sine', 0.12);
  }

  toggle(on: boolean): void {
    this.tone(on ? 620 : 420, 0.09, 'square', 0.18);
  }

  hit(): void {
    this.tone(520, 0.16, 'sawtooth', 0.4, 980);
    this.tone(1040, 0.1, 'sine', 0.2, 1500);
  }

  miss(): void {
    this.tone(180, 0.16, 'sine', 0.28, 90);
  }

  open(): void {
    this.tone(380, 0.18, 'sine', 0.3, 700);
  }

  back(): void {
    this.tone(560, 0.16, 'sine', 0.25, 300);
  }

  error(): void {
    this.tone(200, 0.22, 'square', 0.2, 140);
  }

  success(): void {
    this.tone(660, 0.1, 'triangle', 0.3);
    window.setTimeout(() => this.tone(990, 0.14, 'triangle', 0.28), 90);
  }
}

export const sfx = new Sfx();
