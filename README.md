# VisionPocket

**Mixed reality in your pocket — no expensive headset required.**
**Rzeczywistość mieszana w Twojej kieszeni — bez drogich gogli.**

Slip your phone into a cheap cardboard or plastic headset, run VisionPocket, and get a
Meta-Quest-style experience: the camera becomes your passthrough world and floating
panels appear in front of you. Look at a panel and tap anywhere to click it.

Włóż telefon do tanich gogli (kartonowych lub plastikowych), uruchom VisionPocket
i uzyskaj doświadczenie w stylu Meta Quest: obraz z kamery staje się Twoim otoczeniem,
a przed Tobą unoszą się wirtualne panele. Spójrz na panel i stuknij gdziekolwiek, aby kliknąć.

---

## Features / Funkcje

- 🎥 **Camera passthrough (MR)** — live camera view as your environment, rendered in stereo
  with lens-distortion correction (`Podgląd z kamery` / passthrough)
- 👓 **Stereoscopic VR rendering** — per-eye viewports, adjustable IPD, barrel-distortion
  post-pass, FOV tuned for phone headsets
- 🧠 **3DoF head tracking** — fused rotation-vector sensor (gyro) with smooth tracking
  and a **Recenter** action (`Wyśrodkuj`)
- 🖥️ **MR home with floating panels** — launcher, settings, status bar (time · FPS · battery)
- 🎬 **Virtual cinema** — pick any video from the phone (SAF picker), giant curved-in-space
  screen, play/pause, ±30 s seek, progress bar
- ⚙️ **Settings panel** — IPD (58–74 mm), lens distortion strength, eye resolution
  (Low/Med/High), passthrough on/off, passthrough rotation, passthrough zoom
- 🌐 **PL + EN** — the UI follows the system language (Polish and English included)
- 🔉 **Boot chime**, gaze reticle with hover feedback, scene fade transitions
- 📦 **Tiny APK (~0.2 MB)** — native Android (Java + OpenGL ES 2.0), instant startup,
  no WebView, no dependencies

## Install / Instalacja

1. Download `release/VisionPocket-v1.0.apk` from this repository.
2. Copy it to your Android phone (Android 8.0+ / API 26+).
3. Allow "install unknown apps" for your file manager, then tap the APK.
4. Insert the phone into any phone-VR headset (cardboard-style viewer).
   The phone should be in **landscape** orientation.

Zainstaluj APK, uruchom, nadaj uprawnienie kamery, włóż telefon do gogli — gotowe.

### Controls / Sterowanie

| Action / Akcja | How / Jak |
|---|---|
| Click | Look at a button, then tap the screen anywhere / Spójrz na przycisk i stuknij ekran |
| Play/pause in cinema | Tap anywhere while watching / Stuknij gdziekolwiek podczas oglądania |
| Recenter | `🔄 Wyśrodkuj` button (home or settings) |
| Exit to home | `⬅ Wróć` / system back key |
| Screen | Tap = gaze cursor click; screen stays awake at full brightness |

## Honest limitations / Uczciwe ograniczenia

A phone cannot see the world the way a Quest 3 does. VisionPocket gives you:

- **3DoF** rotation tracking (yaw/pitch/roll) — no positional tracking (no 6DoF),
  because a phone has no outward-facing depth cameras.
- Passthrough is **monocular and software-warped** — it pans like a panorama as you
  turn (3DoF AR style), with a small latency from the camera pipeline.
- Panel positions are re-anchored on recenter rather than physically tracked.

Even so, for **cinema, gaze-driven UI and MR-style panels** the everyday difference
versus an expensive headset is small — and the price is the price of lunch. 🍜

## Project layout

```
app/
  AndroidManifest.xml          app metadata, CAMERA permission
  res/                         strings (EN + PL), layout, styles, icons, chime
  java/app/visionpocket/       the whole engine (plain Java, no dependencies)
    MainActivity.java          onboarding, permissions, immersive mode, picker
    VrRenderer.java            stereo renderer, distortion pass, gaze raycast
    HeadTracker.java           3DoF orientation + recenter
    CameraPipeline.java        Camera2 passthrough into a GL SurfaceTexture
    SceneManager.java          scene switching + fades
    HomeScene/SettingsScene/CinemaScene.java   floating panels
    Panel.java                 canvas-drawn UI panels + buttons
    GlUtil.java / Mat helpers  OpenGL utilities
tools/
  build_apk.py                 full APK build pipeline (no Gradle, no SDK download)
  vp-debug.keystore            debug signing key (passphrase: visionpocket)
  icon_src.png                 launcher icon source
release/
  VisionPocket-v1.0.apk        signed, ready-to-install build
```

## Build from source / Budowanie ze źródeł

See [BUILDING.md](BUILDING.md). Short version:

```bash
python3 tools/build_apk.py            # -> release/VisionPocket-v1.0.apk
```

The pipeline (aapt2 → javac → D8 → aligned zip → apksigner) needs **no Android SDK
and no Gradle** — it downloads/uses a self-contained toolchain (JDK, android.jar,
aapt2, D8, apksigner). Signing uses the committed debug key, so rebuilds install
over previous ones without uninstalling.

## Roadmap / Plany

- 360° photo viewer, curved cinema screen
- Bluetooth gamepad support (Quest-controller-style navigation)
- Front-camera AR selfie mode, passthrough zoom presets
- IPD/distortion auto-calibration wizard

*VisionPocket — big-tech immersion, pocket-sized budget.*
