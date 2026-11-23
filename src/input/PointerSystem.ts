import * as THREE from 'three';
import type { Panel } from '../ui/Panel';

export interface PointerRay {
  origin: THREE.Vector3;
  dir: THREE.Vector3;
  source: 'hand' | 'gaze' | 'mouse';
}

export interface HoverState {
  panel: Panel;
  id: string | null;
  point: THREE.Vector3;
  distance: number;
  px: number;
  py: number;
}

export interface PointerEventPayload {
  panel: Panel;
  id: string | null;
  px: number;
  py: number;
  point: THREE.Vector3;
  source: PointerRay['source'];
}

export interface PointerUpdate {
  ray: PointerRay;
  hover: HoverState | null;
  /** kliknięcie w tym kadrze */
  click: PointerEventPayload | null;
  dwell: number;
  hoverChanged: boolean;
}

/**
 * Wspólny wskaźnik: dłoń (laser z palca), wzrok (kropka + „dwell”) lub mysz w trybie 2D.
 */
export class PointerSystem {
  readonly raycaster = new THREE.Raycaster();
  private hover: HoverState | null = null;
  private dwellTime = 0;
  private pinchLatch = false;
  private dwellKey: string | null = null;
  private meshes: THREE.Mesh[] = [];

  setPanels(panels: Panel[]): void {
    this.meshes = panels.map((p) => p.mesh);
  }

  update(opts: {
    dt: number;
    ray: PointerRay;
    dwellEnabled: boolean;
    dwellTarget: number;
    pinch: boolean;
    /** kliknięcie z myszy / klawiatury / pada (tryb 2D) */
    externalClick?: boolean;
    allowDwell: boolean;
  }): PointerUpdate {
    const { dt, ray } = opts;
    this.raycaster.set(ray.origin, ray.dir.clone().normalize());
    this.raycaster.far = 12;

    const hits = this.raycaster.intersectObjects(this.meshes, false);
    let nextHover: HoverState | null = null;

    for (const hit of hits) {
      const panel = hit.object.userData.panel as Panel | undefined;
      if (!panel) continue;
      const uv = hit.uv;
      if (!uv) continue;
      const { x, y } = panel.uvToPx(uv.x, uv.y);
      nextHover = {
        panel,
        id: panel.hitAt(x, y)?.id ?? null,
        point: hit.point.clone(),
        distance: hit.distance,
        px: x,
        py: y,
      };
      break;
    }

    let hoverChanged = false;
    const prevPanel = this.hover?.panel ?? null;
    const prevId = this.hover?.id ?? null;

    if (nextHover?.panel !== prevPanel) {
      prevPanel?.setHover(null);
    }
    if (nextHover) {
      hoverChanged = nextHover.panel.setHover(nextHover.id) || prevPanel !== nextHover.panel;
    } else if (prevPanel) {
      prevPanel.setHover(null);
      hoverChanged = true;
    }
    this.hover = nextHover;

    // „dwell” — kliknięcie samym patrzeniem
    let dwell = 0;
    const hoverKey = nextHover ? `${nextHover.panel.mesh.uuid}:${nextHover.id}` : null;
    if (hoverKey !== this.dwellKey) {
      this.dwellTime = 0;
      this.dwellKey = hoverKey;
    }
    if (opts.allowDwell && opts.dwellEnabled && nextHover?.id) {
      this.dwellTime += dt;
      dwell = Math.min(1, this.dwellTime / Math.max(0.25, opts.dwellTarget));
    } else {
      this.dwellTime = 0;
    }

    let click: PointerEventPayload | null = null;
    const pinchEdge = opts.pinch && !this.pinchLatch;
    this.pinchLatch = opts.pinch;

    if (nextHover) {
      if (pinchEdge || opts.externalClick) {
        click = {
          panel: nextHover.panel,
          id: nextHover.id,
          px: nextHover.px,
          py: nextHover.py,
          point: nextHover.point,
          source: ray.source,
        };
        this.dwellTime = 0;
        dwell = 0;
      } else if (dwell >= 1) {
        click = {
          panel: nextHover.panel,
          id: nextHover.id,
          px: nextHover.px,
          py: nextHover.py,
          point: nextHover.point,
          source: 'gaze',
        };
        this.dwellTime = 0;
        dwell = 0;
      }
    }

    return { ray, hover: nextHover, click, dwell, hoverChanged };
  }

  reset(): void {
    this.hover?.panel.setHover(null);
    this.hover = null;
    this.dwellTime = 0;
    this.dwellKey = null;
    this.pinchLatch = false;
  }

  get currentHover(): HoverState | null {
    return this.hover;
  }
}
