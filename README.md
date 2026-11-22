# VisionPocket

**Mixed reality in your pocket — no expensive headset required.**
**Rzeczywistość mieszana w Twojej kieszeni — bez drogich gogli.**

Slip your phone into a cheap cardboard or plastic headset, run VisionPocket, and get a
Meta-Quest-style spatial desktop: **floating windows you can open, grab, drag through
3D space, push/pull in depth and resize** — over a live camera passthrough or beautiful
virtual environments.

Włóż telefon do tanich gogli (kartonowych lub plastikowych), uruchom VisionPocket
i uzyskaj przestrzenny pulpit w stylu Meta Quest: **pływające okna, które otwierasz,
chwytasz, przeciągasz w 3D, wysuwasz w głąb i skalujesz** — na tle podglądu z kamery
lub pięknych środowisk wirtualnych.

---

## Features / Funkcje (v1.1)

- 🖼️ **Spatial windows (Windows-style)** — apps open as floating windows with a title
  bar, ✕ close and — minimize; bring to front, cascade placement, minimized windows
  live in the dock
- ✋ **Hand-style interaction** — your finger is the hand: touch a window and drag to
  move it on the view plane, **two-finger drag = depth (push/pull)**,
  **two-finger pinch = resize**, hand cursor (✋/✊) sits on the window surface
- 📌 **Movable dock** — the taskbar itself is a grabbable world panel: cinema, photos,
  environment, settings, recenter; open/minimized state shown per icon
- 🎥 **Camera passthrough (MR)** — live rear-camera view as your world, rendered in
  stereo with lens-distortion correction
- 🌌 **Virtual environments** — procedural **Space** (twinkling stars, nebula gradient)
  and **Horizon** (glowing perspective grid) shaders, plus passthrough — switch anytime
- 👓 **Stereoscopic rendering** — per-eye viewports, adjustable IPD (58–74 mm),
  barrel-distortion post-pass, eye resolution Low/Med/High
- 🧠 **3DoF head tracking** — fused rotation sensor with exponential smoothing,
  one-tap **Recenter**
- 🎬 **Cinema window** — pick any video (SAF), giant screen that keeps video aspect,
  play/pause (tap the screen), ±30 s seek, progress bar
- 🖼 **Photos window** — place any image from the phone into your space
- ⚙️ **Settings window** — IPD, lens distortion strength, resolution, passthrough
  on/off/rotation/zoom
- 🌐 **PL + EN**, boot chime + click sounds, status bar (time · FPS · battery)
- 📦 **Tiny APK (~0.23 MB)** — native Android (Java + OpenGL ES 2.0), instant startup

## Install / Instalacja

1. Download `release/VisionPocket-v1.1.apk` (Android 8.0+ / API 26+).
2. Allow "install unknown apps" for your file manager, then tap the APK.
3. Run → **START VR** → grant camera permission (or "Start without camera").
4. Insert the phone into any phone-VR headset, **landscape** orientation.

### Controls / Sterowanie

| Action / Akcja | How / Jak |
|---|---|
| Press a button | Look at it, tap the screen / Spójrz i stuknij ekran |
| Move a window / dock | Touch it and drag with one finger / Chwyć i przeciągnij jednym palcem |
| Depth (push/pull) | Two-finger drag up/down / Dwa palce góra-dół |
| Resize window | Two-finger pinch / Szczypnięcie dwoma palcami |
| Open / minimize app | Tap its dock icon again / Stuknij ponownie ikonę w docku |
| Play/pause video | Tap the video / Stuknij w film |
| Recenter | Dock ⟲ or Settings / Dock ⟲ lub Ustawienia |
| Close window | ✕ in the title bar / ✕ na pasku tytułu |
| Exit to launcher | System back (closes focused window first) / Wstecz zamyka aktywne okno |

## Honest limitations / Uczciwe ograniczenia

A phone has no outward-facing depth cameras, so VisionPocket gives you **3DoF**
rotation tracking (no positional walking) and panoramic (not parallax) passthrough.
Window movement is driven by your finger on the touchscreen — not by camera-based
hand skeletons. Even so, for cinema, spatial multitasking and gaze-driven UI the
everyday difference versus an expensive headset is small — and the price is the
price of lunch. 🍜

## Project layout

```
app/
  AndroidManifest.xml          app metadata, CAMERA permission
  res/                         strings (EN + PL), layout, styles, icons, sounds
  java/app/visionpocket/
    MainActivity.java          onboarding, permissions, touch forwarding, pickers
    VrRenderer.java            stereo renderer, distortion pass, env shaders,
                               dock/status/reticle, hand-interaction state machine
    VpWindows.java             spatial window manager (Window, parts, chrome,
                               move/scale/minimize, raycasting, spawn cascade)
    WindowContent.java         window body interface
    CinemaWindow.java          video player window (OES surface + controls)
    PhotosWindow.java          image viewer window
    EnvWindow.java             environment switcher (passthrough/space/horizon)
    SettingsWindow.java        IPD / distortion / resolution / passthrough options
    StartWindow.java           welcome + tips (opened automatically)
    HeadTracker.java           3DoF orientation + smoothing + recenter
    CameraPipeline.java        Camera2 passthrough into a GL SurfaceTexture
    Panel.java                 canvas-drawn quad + buttons + ray hit-testing
    SettingsStore.java         persisted settings
    GlUtil.java / HomeSceneUtil.java   utilities
tools/
  build_apk.py                 full APK build pipeline (no Gradle, no SDK download)
  vp-debug.keystore            debug signing key (passphrase: visionpocket)
release/
  VisionPocket-v1.1.apk        signed, ready-to-install build
```

## Build from source / Budowanie ze źródeł

See [BUILDING.md](BUILDING.md). Short version:

```bash
python3 tools/build_apk.py --version-name 1.1 --version-code 2
# -> release/VisionPocket-v1.1.apk
```

The pipeline (aapt2 → javac → D8 → aligned zip → apksigner) needs **no Android SDK
and no Gradle**. Signing uses the committed debug key, so rebuilds install over
previous ones without uninstalling.

## Roadmap / Plany

- On-screen virtual hand model with finger animation during grabs
- Bluetooth gamepad support, curved cinema screen, 360° photo viewer
- Passthrough spatial anchors (windows pinned to real-world places)
- Optional MediaPipe-style hand tracking where the device supports it

*VisionPocket — big-tech immersion, pocket-sized budget.*
