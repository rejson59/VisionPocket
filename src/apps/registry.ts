import { C } from '../ui/theme';
import type { IconName } from '../ui/draw';

export interface AppMeta {
  id: string;
  title: string;
  subtitle: string;
  icon: IconName;
  accent: string;
}

/** Kolejność kafelków na ekranie głównym. */
export const APP_LIST: AppMeta[] = [
  { id: 'cinema', title: 'Kino', subtitle: 'Filmy 2D i 360°, ekran w ciemności', icon: 'play', accent: C.cyan },
  { id: 'gallery', title: 'Galeria', subtitle: 'Zdjęcia i panoramy z telefonu', icon: 'photo', accent: C.violet },
  { id: 'game', title: 'Gra', subtitle: 'Neonowe cele — strzelaj szczypaniem', icon: 'target', accent: C.magenta },
  { id: 'notes', title: 'Notatki', subtitle: 'Dyktuj głosem, zapisuj w telefonie', icon: 'note', accent: C.lime },
  { id: 'settings', title: 'Ustawienia', subtitle: 'Soczewki, IPD, dłonie, wydajność', icon: 'gear', accent: C.amber },
  { id: 'help', title: 'Pomoc', subtitle: 'Gesty, headset, pierwsze kroki', icon: 'help', accent: C.textDim },
];
