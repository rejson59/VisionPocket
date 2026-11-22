## 📱 Instalacja / Install

**Pobierz APK bezpośrednio z repozytorium:**
➡️ [release/VisionPocket-v1.7.apk](https://github.com/rejson59/VisionPocket/raw/arena/49b8c2a9-visionpocket/release/VisionPocket-v1.7.apk) *(kliknij prawym → „Zapisz link jako...")*

Android 8.0+ (API 26+). Instaluje się nad poprzednimi wersjami.

## 🆕 Co nowego / What's new (v1.7 — hardening)

- 💾 **Kopia / przywracanie ustawień** — Ustawienia → „Kopia ustawień": ⬆ zapisuje WSZYSTKO (ustawienia, notatki, układ pulpitu, kalibrację IPD, sesję) do pliku JSON, ⬇ wczytuje na tym samym lub innym telefonie
- 🛡 **Osłona renderowania** — wyjątek w klatce nie zabija już aplikacji: toast „próbuję kontynuować"
- 🧹 **Refaktor** — shadery GLSL wyciągnięte do osobnego `Shaders.java` (renderer mniejszy i czystszy)
- 🎨 Kosmetyka: pasy wierszy Ustawień w neutralnej palecie

*(Binary asset upload is blocked in this environment — the APK lives in the repo under `release/`.)*

## 🧪 Warto sprawdzić

1. Ustawienia → Kopia ustawień → ⬆ Zapisz → plik trafia np. do Pobranych
2. Zmień coś (IPD, środowisko) → ⬇ Wczytaj → potwierdź przywrócenie
3. Reszta bez zmian — jeśli coś się sypie: 🩺 Diagnostyka + zrzut

*Debug-signed build (CN=VisionPocket Debug). Nic nie jest nagrywane ani wysyłane.*
