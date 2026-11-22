## ⬇️ POBIERZ APK

**Sposób 1 (najszybszy):** [➡️ kliknij TUTAJ — plik APK](https://github.com/rejson59/VisionPocket/raw/arena/49b8c2a9-visionpocket/release/VisionPocket-v1.9.apk) → przycisk „Download raw file" / zapisz link

**Sposób 2:** pobierz **„Source code (zip)"** z tej strony → rozpakuj → w środku jest `release/VisionPocket-v1.9.apk` *(zweryfikowane: versionCode 11 ✓)*

*(Upload plików binarnych do wydania jest zablokowany w moim środowisku budowania — dlatego APK żyje w repo.)*

## 🚨 NAJWAŻNIEJSZE: naprawiony crash przy starcie

**Wszystkie wcześniejsze wersje wywalały się na prawdziwym telefonie** — Twój zrzut ekranu to pokazał: `res/raw/chime.wav` był **skompresowany** w APK, a `SoundPool` wymaga plików nieskompresowanych (mmap). Wina leżała w naszym pipeline pakowania. v1.9:
- poprawia pakowanie (`res/raw`, media i `resources.arsc` są teraz STORED + asercja po każdej budowie),
- dodaje zabezpieczenie: dźwięki nigdy nie zabiją startu aplikacji.

**To jest pierwsza wersja, która w ogóle uruchomi się na telefonie.** Przepraszam za wcześniejsze nieudane instalacje — Twój raport trafił w 100%.

## 🆕 Co nowego / What's new (v1.9)

- 🏠 **Pulpit startowy** — duży zegar, bateria, 6 kafli szybkiego startu (kino, galeria, mirroring, notatki, zegar, gra)
- 🎬 **Kino: napisy (CC)** — wczytaj plik `.srt` do filmu lub streamu; pasek napisów pod obrazem
- 🎬 **Kino: wznawianie** — „Wznawiam od 12:34" po powrocie do filmu (per film, zapisane między uruchomieniami)
- 💼 **Presety scen** (🌍): 💼 Praca (notatki + zegar + mirroring), 🎬 Kino, 🎯 Gra — jedno stuknięcie układa pulpit
- 🎙 **Timery 5/15/25 min** z dzwonkiem (🕒, obok stopera)
- 📤 **Wyślij raport** (🩺 Diagnostyka) — pełna diagnoza tekstem do Messenger/maila jednym przyciskiem
- 🖌 Polerowanie UI: obwódka akcentu aktywnego okna, czyściejszy pasek okna

*(Voice notes przechodzą do v1.10. Binary asset upload is blocked — APK lives in the repo under `release/`.)*

## 🧪 Teraz porządny test (10 min)

1. **Zainstaluj v1.9** — powinna wystartować po raz pierwszy ✅
2. START VR → pulpit → stuknij kafla 🎬 → 📂 → film → **CC** → wybierz `.srt`
3. Zatrzymaj film, wróć — „Wznawiam od…"
4. 🌍 → 💼 Praca → pulpit ustawia się sam
5. 🩺 → **📤 Wyślij raport** → podeślij mi, jeśli coś nie działa

*Debug-signed build (CN=VisionPocket Debug). Nic nie jest nagrywane ani wysyłane.*
