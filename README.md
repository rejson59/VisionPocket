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

## Design / Styl (v1.5)

**Quest-style design language** — neutral dark surfaces, white content, one muted
steel accent; **no neon, no glow spam**. Environments reworked: **Studio** (clean
dark gradient) and **Dusk** (soft, desaturated horizon) replace the old flashy ones.

## Features / Funkcje (v1.7)

- 🖼️ **Spatial windows (Windows-style)** — apps open as floating windows with a title
  bar, ✕ close and — minimize; bring to front, cascade placement, minimized windows
  live in the dock; released windows glide with **inertia** and gently snap to face you
- 🎬 **Curved cinema screen** — Quest-style cylindrical mesh (radius 5.5 m) that wraps
  toward the viewer, keeping the video aspect
- 🌐 **Panorama 360°** — load an equirectangular photo or video and stand *inside* it:
  the world is a sphere around you, head-tracking included
- 📱 **Screen mirroring** — mirror the whole phone display (YouTube, games, anything)
  onto a big virtual screen via MediaProjection (foreground service, one-tap consent)
- ✋ **3D hand cursor** — procedural hand sprite with fingers: opens to point,
  closes to grab (replaces the plain dot while interacting)
- 🎮 **Bluetooth gamepad support** — left stick = laser cursor with **on-screen laser
  beam**, A = click, B = back, R1 = grab & drag windows, R2 = push/pull depth,
  START = recenter
- 🚀 **App launcher** — launch any installed app; pair it with Screen mirroring to
  use it on the giant screen
- ⌨️ **3D QWERTY keyboard** — spatial input dialog with blinking cursor, symbols row
- 📝 **Notes** — quick note app persisted on the phone
- 📡 **URL streams** — type an MP4 / HLS (m3u8) / radio URL on the 3D keyboard and
  watch it in the cinema (cleartext HTTP supported)
- 🎵 **Music visualizer** — real waveform bars (Visualizer API; mic permission used
  only for visualization) with a graceful fake-dance fallback
- 🌐 **360° projection modes** — Equirect / Dome / Tiny planet, switchable live
- 🤳 **Selfie AR** — passthrough from the **front camera** (see yourself and your
  room behind you, Quest-style) — toggle in Settings
- 📸 **VR screenshots** — stereoscopic PNG saved to Pictures/VisionPocket
- ⚡ **Adaptive eye resolution** — automatic FBO scaling (55–100 %) keeps the frame
  rate smooth on slower phones, like Quest Dynamic Resolution
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
- 🎬 **Cinema window** — pick any video (SAF), play/pause (tap the screen), ±30 s
  seek, progress bar
- 🖼 **Photos window** — place any image from the phone into your space
- ⚙️ **Settings window** — IPD, lens distortion strength, resolution, passthrough
  on/off/rotation/zoom
- 🌐 **PL + EN**, boot chime + click sounds, status bar (time · FPS · battery)
- 💾 **Settings backup / restore** — export all settings, notes and desktop
  session to a JSON file and restore them later or on another phone
  (Settings → Kopia ustawień → ⬆/⬇)
- 🛡 **Render guard** — a frame-level exception trap keeps the app alive and
  notifies instead of crashing
- 🕒 **Clock window** — analog + digital clock, local date, built-in stopwatch
- 🖼️ **Photo gallery** — multi-page thumbnail grid (up to 60 photos), tap to view
  full-size, ‹ › pages, 📂 to add more
- 📝 **Sticky notes** — multiple notes now (was one), tap to edit with the 3D
  keyboard, ✕ to delete, old single note is migrated automatically
- 🎯 **Focus game** — soft white targets appear around your space; look & tap to
  pop them, chain combos in 60-second rounds, best score kept
- 🔢 **2048** — the classic, neutral tiles, arrow controls, best score kept
- 🧠 **Desktop session** — window layout, dock placement **and the last cinema
  video** survive app restarts
- 🩺 **Diagnostics window** — device, GPU, sensors, permissions, FPS at a glance
  (Settings → 🩺) — screenshot it for support
- 🔊 **Hardware volume buttons** control the cinema (system volume elsewhere)
- 🔄 **Auto-recenter on start** (toggle in Environment) and **☀ passthrough
  brightness** (0.5–1.5)
- 🎞 **Side-by-side 3D movies** — cinema auto-detects SBS files and plays them in
  proper stereoscopy (left half → left eye, right half → right eye)
- 🔉 **Cinema volume + buffering indicator**, **🔋 performance profiles**
  (battery / balanced / full power) shaping the adaptive resolution
- 🎯 **IPD calibration wizard** — line pattern test with −/+ and it remembers
- ⌨ **Keyboard shift key**, ✨ **click haptics** (toggle), **🛡 crash-guard** —
  the app recovers gracefully and keeps your settings
- 📦 **Tiny APK (~0.27 MB)** — includes two games — native Android (Java + OpenGL ES 2.0), instant startup

## Install / Instalacja

1. Download `release/VisionPocket-v1.7.apk` (Android 8.0+ / API 26+) — or grab it from the [Releases](../../releases) page.
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
| Gamepad | Stick = laser, A = click, R1 = grab, R2 = depth, B = back / Pady BT j.w. |
| Type text | Notes → tap field → 3D keyboard / Notatki → klawiatura 3D |
| Throw a window | Release a fast drag — it glides, then faces you / Puść szybkie przeciągnięcie — okno dojedzie z bezwładnością |
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
    CinemaWindow.java          video/stream player window (curved OES surface)
    PanoramaWindow.java        360° photo/video panorama window + projection modes
    MirrorWindow.java          screen-mirror window (MediaProjection)
    MirrorService.java         foreground screen-capture service
    LauncherWindow.java        installed-apps launcher window
    NotesWindow.java           quick notes window
    VisualizerWindow.java      music visualizer window
    ClockWindow.java           analog clock, date, stopwatch
    GameWindow.java            "Focus" reaction game (spatial targets)
    Game2048Window.java        2048 in a window
    Shaders.java               all GLSL sources (extracted from the renderer)
    KeyboardWindow.java        3D QWERTY input dialog (shift key)
    CalibrationWindow.java     IPD calibration wizard
    PhotosWindow.java          image viewer window
    EnvWindow.java             environment switcher (passthrough/space/horizon)
    SettingsWindow.java        IPD / distortion / resolution / passthrough options
    StartWindow.java           welcome + tips (opened automatically)
    HeadTracker.java           3DoF orientation + smoothing + recenter
    CameraPipeline.java        Camera2 passthrough into a GL SurfaceTexture
    Mesh.java                  cylinder + sphere meshes (curved screens, panoramas)
    Panel.java                 canvas-drawn quad + buttons + ray hit-testing
    SettingsStore.java         persisted settings
    GlUtil.java / HomeSceneUtil.java   utilities
tools/
  build_apk.py                 full APK build pipeline (no Gradle, no SDK download)
  vp-debug.keystore            debug signing key (passphrase: visionpocket)
release/
  VisionPocket-v1.7.apk        signed, ready-to-install build
```

## Build from source / Budowanie ze źródeł

See [BUILDING.md](BUILDING.md). Short version:

```bash
python3 tools/build_apk.py --version-name 1.7 --version-code 9
# -> release/VisionPocket-v1.7.apk
```

The pipeline (aapt2 → javac → D8 → aligned zip → apksigner) needs **no Android SDK
and no Gradle**. Signing uses the committed debug key, so rebuilds install over
previous ones without uninstalling.

## Roadmap / Plany

- Multiplayer rooms over Wi-Fi (shared spaces between phones)
- Spatial anchors (QR/ArUco pinning), PC streaming client
- Virtual desktop spaces, gamepad haptics polish

*VisionPocket — big-tech immersion, pocket-sized budget.*
