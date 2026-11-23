import * as THREE from 'three';
import { C, alpha } from '../ui/theme';
import { button, card, icon, panelChrome, text, wrapText } from '../ui/draw';
import type { Panel } from '../ui/Panel';
import type { AppHost, VrApp } from './types';
import { sfx } from '../core/sfx';

interface Note {
  id: string;
  text: string;
  updatedAt: number;
}

const STORAGE = 'visionpocket.notes.v1';

function loadNotes(): Note[] {
  try {
    const raw = localStorage.getItem(STORAGE);
    if (raw) return JSON.parse(raw) as Note[];
  } catch {
    /* ignorujemy */
  }
  return [
    {
      id: 'n1',
      text: 'Witaj w VisionPocket. Naciśnij mikrofon i mów — tekst pojawi się tutaj, a notatka zapisze się w telefonie.',
      updatedAt: Date.now(),
    },
  ];
}

function saveNotes(notes: Note[]): void {
  try {
    localStorage.setItem(STORAGE, JSON.stringify(notes));
  } catch {
    /* ignorujemy */
  }
}

/** Notatki z dyktowaniem głosowym (Web Speech API, pl-PL). */
export class NotesApp implements VrApp {
  id = 'notes';
  title = 'Notatki';
  subtitle = 'Dyktowanie głosem';
  icon = 'note' as const;
  accent = C.lime;
  group = new THREE.Group();
  hint: string | null = 'Szczypnij mikrofon i mów — notatka zapisze się sama';

  private notes = loadNotes();
  private activeId: string | null = null;
  private listening = false;
  private recognition: any = null;
  private interim = '';
  private error: string | null = null;
  private pulse = 0;

  constructor(private host: AppHost, private panel: Panel) {}

  enter(): void {
    this.panel.markDirty();
  }

  exit(): void {
    this.stopListening();
    this.activeId = null;
  }

  private get active(): Note | null {
    return this.notes.find((n) => n.id === this.activeId) ?? null;
  }

  private create(): void {
    const note: Note = { id: `n${Date.now()}`, text: '', updatedAt: Date.now() };
    this.notes.unshift(note);
    this.activeId = note.id;
    saveNotes(this.notes);
    sfx.open();
    this.panel.markDirty();
  }

  private toggleListening(): void {
    if (this.listening) {
      this.stopListening();
      return;
    }
    const SR =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition || null;
    if (!SR) {
      this.error = 'Ta przeglądarka nie ma dyktowania. Wpisz tekst w trybie 2D lub użyj Chrome.';
      this.panel.markDirty();
      sfx.error();
      return;
    }
    const note = this.active;
    if (!note) return;

    const rec = new SR();
    rec.lang = 'pl-PL';
    rec.continuous = true;
    rec.interimResults = true;
    rec.onresult = (event: any) => {
      let interim = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i];
        const transcript = result[0].transcript as string;
        if (result.isFinal) {
          const target = this.active;
          if (target) {
            target.text = `${target.text}${target.text ? ' ' : ''}${transcript.trim()}`;
            target.updatedAt = Date.now();
            saveNotes(this.notes);
          }
        } else {
          interim += transcript;
        }
      }
      this.interim = interim;
      this.panel.markDirty();
    };
    rec.onerror = (event: any) => {
      const err = event?.error ? String(event.error) : 'nieznany';
      if (err !== 'no-speech') {
        this.error = `Dyktowanie: ${err}`;
        this.listening = false;
        this.panel.markDirty();
      }
    };
    rec.onend = () => {
      // dyktowanie ciągłe bywa przerywane przez system — wznawiamy
      if (this.listening) {
        try {
          rec.start();
        } catch {
          this.listening = false;
          this.panel.markDirty();
        }
      }
    };

    try {
      rec.start();
      this.recognition = rec;
      this.listening = true;
      this.error = null;
      sfx.success();
    } catch {
      this.error = 'Nie udało się uruchomić mikrofonu.';
      sfx.error();
    }
    this.panel.markDirty();
  }

  private stopListening(): void {
    this.listening = false;
    this.interim = '';
    if (this.recognition) {
      try {
        this.recognition.stop();
      } catch {
        /* ignorujemy */
      }
      this.recognition = null;
    }
  }

  update(dt: number): void {
    if (this.listening) {
      this.pulse += dt;
      this.panel.markDirty();
    }
  }

  draw(panel: Panel): void {
    const { ctx } = panel;
    const W = panel.pxW;
    const H = panel.pxH;
    panelChrome(ctx, W, H, { accent: this.accent });

    const active = this.active;
    if (active) {
      this.drawEditor(panel, W, H, active);
    } else {
      this.drawList(panel, W, H);
    }
  }

  private drawList(panel: Panel, W: number, H: number): void {
    const { ctx } = panel;
    text(ctx, 'Notatki', 40, 76, { size: 40, weight: 700 });
    text(ctx, `${this.notes.length} zapisanych w telefonie`, 40, 112, {
      size: 21,
      color: C.muted,
    });

    button(ctx, W - 260, 50, 220, 62, '+ Nowa', {
      hover: panel.hoverId === 'notes:new',
      accent: this.accent,
      primary: true,
      size: 22,
    });
    panel.hit('notes:new', W - 260, 50, 220, 62);

    button(ctx, W - 470, 50, 190, 62, 'Wstecz', {
      hover: panel.hoverId === 'notes:home',
      accent: C.cyan,
      size: 21,
    });
    panel.hit('notes:home', W - 470, 50, 190, 62);

    const margin = 40;
    const gap = 20;
    const cols = 2;
    const rows = 3;
    const tw = (W - margin * 2 - gap) / cols;
    const th = (H - 190 - 60 - gap * (rows - 1)) / rows;

    this.notes.slice(0, cols * rows).forEach((note, i) => {
      const col = i % cols;
      const row = Math.floor(i / cols);
      const x = margin + col * (tw + gap);
      const y = 180 + row * (th + gap);
      const id = `notes:open:${note.id}`;
      const hover = panel.hoverId === id;
      card(ctx, x, y, tw, th, { hover, accent: this.accent, glow: hover ? 18 : 0 });
      const date = new Date(note.updatedAt).toLocaleString('pl-PL', {
        day: '2-digit',
        month: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      });
      text(ctx, date, x + tw - 22, y + 38, {
        size: 17,
        color: C.muted,
        align: 'right',
      });
      icon(ctx, 'note', x + 40, y + 42, 24, alpha(this.accent, 0.9));
      wrapText(ctx, note.text || '(pusta notatka)', x + 24, y + 84, tw - 48, {
        size: 22,
        color: note.text ? C.textDim : C.muted,
        lineHeight: 32,
        maxLines: 3,
      });
      panel.hit(id, x, y, tw, th);
    });
  }

  private drawEditor(panel: Panel, W: number, H: number, note: Note): void {
    const { ctx } = panel;
    text(ctx, 'Notatka', 40, 76, { size: 40, weight: 700 });
    text(
      ctx,
      this.listening ? 'Słucham… mów swobodnie' : 'Dotknij mikrofonu, aby dyktować',
      40,
      112,
      { size: 21, color: this.listening ? C.lime : C.muted },
    );

    button(ctx, W - 230, 50, 190, 62, 'Zamknij', {
      hover: panel.hoverId === 'notes:back',
      accent: C.cyan,
      size: 21,
    });
    panel.hit('notes:back', W - 230, 50, 190, 62);

    card(ctx, 40, 150, W - 80, 580, { fill: 'rgba(255,255,255,0.035)' });
    const body = `${note.text}${this.interim ? ` ${this.interim}` : ''}`;
    wrapText(ctx, body || 'Zacznij mówić — tekst pojawi się tutaj.', 70, 200, W - 140, {
      size: 28,
      color: body ? C.text : C.muted,
      lineHeight: 42,
      maxLines: 11,
    });

    if (this.error) {
      text(ctx, this.error, 40, 760, { size: 19, color: C.amber });
    }

    const micId = 'notes:mic';
    const micHover = panel.hoverId === micId;
    const cx = 150;
    const cy = 830;
    ctx.save();
    if (this.listening) {
      const p = 1 + Math.sin(this.pulse * 6) * 0.12;
      ctx.globalAlpha = 0.35;
      ctx.fillStyle = C.red;
      ctx.beginPath();
      ctx.arc(cx, cy, 54 * p, 0, Math.PI * 2);
      ctx.fill();
      ctx.globalAlpha = 1;
    }
    ctx.fillStyle = this.listening ? alpha(C.red, 0.9) : micHover ? alpha(this.accent, 0.3) : 'rgba(255,255,255,0.06)';
    ctx.beginPath();
    ctx.arc(cx, cy, 46, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = alpha(this.listening ? C.red : this.accent, 0.8);
    ctx.lineWidth = 3;
    ctx.stroke();
    icon(ctx, 'mic', cx, cy, 40, this.listening ? '#ffffff' : C.text);
    ctx.restore();
    panel.hit(micId, cx - 60, cy - 60, 120, 120);

    button(ctx, 260, 794, 250, 72, this.listening ? 'Zatrzymaj' : 'Dyktuj', {
      hover: micHover,
      accent: this.listening ? C.red : this.accent,
      size: 22,
    });
    panel.hit('notes:dictate', 260, 794, 250, 72);

    button(ctx, 530, 794, 200, 72, 'Wyczyść', {
      hover: panel.hoverId === 'notes:clear',
      accent: C.amber,
      size: 21,
    });
    panel.hit('notes:clear', 530, 794, 200, 72);

    button(ctx, W - 260, 794, 220, 72, 'Usuń', {
      hover: panel.hoverId === 'notes:delete',
      accent: C.red,
      size: 21,
      icon: 'trash',
    });
    panel.hit('notes:delete', W - 260, 794, 220, 72);
  }

  click(panel: Panel, x: number, y: number, id: string | null): void {
    void panel;
    void x;
    void y;
    if (!id) return;
    if (id === 'notes:new') {
      this.create();
      return;
    }
    if (id === 'notes:home') {
      this.host.back();
      return;
    }
    if (id.startsWith('notes:open:')) {
      this.activeId = id.slice('notes:open:'.length);
      sfx.open();
      this.panel.markDirty();
      return;
    }
    if (id === 'notes:back') {
      this.stopListening();
      this.activeId = null;
      this.panel.markDirty();
      sfx.back();
      return;
    }
    if (id === 'notes:mic' || id === 'notes:dictate') {
      this.toggleListening();
      return;
    }
    if (id === 'notes:clear') {
      const note = this.active;
      if (note) {
        note.text = '';
        note.updatedAt = Date.now();
        saveNotes(this.notes);
        this.panel.markDirty();
        sfx.click();
      }
      return;
    }
    if (id === 'notes:delete') {
      this.notes = this.notes.filter((n) => n.id !== this.activeId);
      saveNotes(this.notes);
      this.stopListening();
      this.activeId = null;
      this.panel.markDirty();
      sfx.back();
    }
  }
}
