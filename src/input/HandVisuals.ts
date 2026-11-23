import * as THREE from 'three';
import { getDotTexture } from '../ui/textures';
import { HAND_CONNECTIONS, type HandFrame } from './HandTracker';

const JOINT_COUNT = 21;

interface Slot {
  points: THREE.Points;
  bones: THREE.LineSegments;
  glow: THREE.Sprite;
  active: boolean;
  color: THREE.Color;
}

/**
 * Wizualizacja dłoni: stawy (punkty), kości (linie) i błysk szczypania.
 * Wszystko w przestrzeni głowy (grupa dziedziczy obrót kamery).
 */
export class HandVisuals {
  readonly group = new THREE.Group();
  readonly laser: THREE.Line;
  private slots: Slot[] = [];
  skeletonEnabled = true;
  private laserDot: THREE.Sprite;

  constructor() {
    for (let i = 0; i < 2; i++) {
      const color = new THREE.Color(i === 0 ? 0x8b7bff : 0x35e0ff);
      const geo = new THREE.BufferGeometry();
      geo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(JOINT_COUNT * 3), 3));
      const points = new THREE.Points(
        geo,
        new THREE.PointsMaterial({
          map: getDotTexture(),
          size: 0.026,
          transparent: true,
          depthWrite: false,
          depthTest: false,
          blending: THREE.AdditiveBlending,
          color,
          sizeAttenuation: true,
        }),
      );
      points.frustumCulled = false;
      points.visible = false;

      const boneGeo = new THREE.BufferGeometry();
      boneGeo.setAttribute(
        'position',
        new THREE.BufferAttribute(new Float32Array(HAND_CONNECTIONS.length * 2 * 3), 3),
      );
      const bones = new THREE.LineSegments(
        boneGeo,
        new THREE.LineBasicMaterial({
          color,
          transparent: true,
          opacity: 0.7,
          depthWrite: false,
          depthTest: false,
          blending: THREE.AdditiveBlending,
        }),
      );
      bones.frustumCulled = false;
      bones.visible = false;

      const glow = new THREE.Sprite(
        new THREE.SpriteMaterial({
          map: getDotTexture(),
          color: 0xffffff,
          transparent: true,
          opacity: 0.9,
          blending: THREE.AdditiveBlending,
          depthWrite: false,
          depthTest: false,
        }),
      );
      glow.scale.setScalar(0.07);
      glow.visible = false;

      this.group.add(points, bones, glow);
      this.slots.push({ points, bones, glow, active: false, color });
    }

    const laserGeo = new THREE.BufferGeometry();
    laserGeo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(6), 3));
    this.laser = new THREE.Line(
      laserGeo,
      new THREE.LineBasicMaterial({
        color: 0x35e0ff,
        transparent: true,
        opacity: 0.55,
        blending: THREE.AdditiveBlending,
        depthWrite: false,
        depthTest: false,
      }),
    );
    this.laser.frustumCulled = false;
    this.laser.visible = false;

    this.laserDot = new THREE.Sprite(
      new THREE.SpriteMaterial({
        map: getDotTexture(),
        color: 0x35e0ff,
        transparent: true,
        opacity: 0.95,
        blending: THREE.AdditiveBlending,
        depthWrite: false,
        depthTest: false,
      }),
    );
    this.laserDot.scale.setScalar(0.045);
    this.laserDot.visible = false;

    this.group.add(this.laser, this.laserDot);
  }

  setVisible(visible: boolean): void {
    this.group.visible = visible;
  }

  update(hands: HandFrame[], lasered: { origin: THREE.Vector3; end: THREE.Vector3 } | null): void {
    // sortuj: lewa dłoń do slotu 0, prawa do 1
    const order: (HandFrame | null)[] = [null, null];
    for (const hand of hands) {
      if (hand.handedness === 'Left') order[0] = hand;
      else order[1] = hand;
    }
    if (hands.length === 1 && !order[0] && !order[1]) order[1] = hands[0];

    for (let i = 0; i < 2; i++) {
      const slot = this.slots[i];
      const hand = order[i];
      if (!hand) {
        slot.points.visible = false;
        slot.bones.visible = false;
        slot.glow.visible = false;
        slot.active = false;
        continue;
      }
      slot.active = true;
      slot.points.visible = this.skeletonEnabled;
      slot.bones.visible = this.skeletonEnabled;

      const pAttr = slot.points.geometry.getAttribute('position') as THREE.BufferAttribute;
      const bAttr = slot.bones.geometry.getAttribute('position') as THREE.BufferAttribute;
      const arr = pAttr.array as Float32Array;
      const barr = bAttr.array as Float32Array;

      for (let j = 0; j < JOINT_COUNT; j++) {
        const p = hand.points[j];
        arr[j * 3] = p.x;
        arr[j * 3 + 1] = p.y;
        arr[j * 3 + 2] = p.z;
      }
      HAND_CONNECTIONS.forEach(([a, b], idx) => {
        const pa = hand.points[a];
        const pb = hand.points[b];
        barr[idx * 6] = pa.x;
        barr[idx * 6 + 1] = pa.y;
        barr[idx * 6 + 2] = pa.z;
        barr[idx * 6 + 3] = pb.x;
        barr[idx * 6 + 4] = pb.y;
        barr[idx * 6 + 5] = pb.z;
      });
      pAttr.needsUpdate = true;
      bAttr.needsUpdate = true;

      (slot.points.material as THREE.PointsMaterial).color.copy(slot.color);
      (slot.bones.material as THREE.LineBasicMaterial).color.copy(slot.color);

      // błysk przy szczypaniu — między kciukiem a palcem wskazującym
      const thumb = hand.points[4];
      const index = hand.points[8];
      const mid = thumb.clone().lerp(index, 0.5);
      slot.glow.position.copy(mid);
      slot.glow.visible = hand.pinch > 0.15;
      slot.glow.scale.setScalar(0.045 + hand.pinch * 0.09);
      (slot.glow.material as THREE.SpriteMaterial).opacity = 0.35 + hand.pinch * 0.6;
      (slot.glow.material as THREE.SpriteMaterial).color.set(hand.pinching ? 0xb6ff5a : 0x35e0ff);
    }

    if (lasered) {
      const attr = this.laser.geometry.getAttribute('position') as THREE.BufferAttribute;
      const a = attr.array as Float32Array;
      a[0] = lasered.origin.x;
      a[1] = lasered.origin.y;
      a[2] = lasered.origin.z;
      a[3] = lasered.end.x;
      a[4] = lasered.end.y;
      a[5] = lasered.end.z;
      attr.needsUpdate = true;
      this.laser.visible = true;
      this.laserDot.position.copy(lasered.end);
      this.laserDot.visible = true;
    } else {
      this.laser.visible = false;
      this.laserDot.visible = false;
    }
  }
}
