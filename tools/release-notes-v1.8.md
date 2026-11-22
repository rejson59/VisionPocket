## ⬇️ POBIERZ APK

Pobierz **„Source code (zip)"** z tej strony → rozpakuj → w środku: `release/VisionPocket-v1.8.apk`.
(Najnowsza wersja: **[v1.9](https://github.com/rejson59/VisionPocket/releases/tag/v1.9)** — pierwsza bez buga crashu startu!)

## 📱 Instalacja / Install

**Pobierz APK bezpośrednio z repozytorium:**
➡️ [release/VisionPocket-v1.8.apk](https://github.com/rejson59/VisionPocket/raw/arena/49b8c2a9-visionpocket/release/VisionPocket-v1.8.apk) *(kliknij prawym → „Zapisz link jako...")*

Android 8.0+ (API 26+). Instaluje się nad poprzednimi wersjami.

## 🆕 Co nowego / What's new (v1.8 — wspólny pokój)

- 🌐 **Multiplayer Wi-Fi** — telefony w tej samej sieci widzą **ten sam układ okien**: otwieranie, przenoszenie, skalowanie i minimalizacja synchronizują się na żywo
- 👀 **Awatary spojrzenia** — mały biały romb pokazuje, gdzie patrzy druga osoba
- Dock: 14 ikon (🌐 między ▦ a 🌍) + naprawa przycinania ostatniej ikony
- 🔧 Doda: **uprawnienie INTERNET** (naprawia też strumienie URL w kinie!)

**Jak zacząć:** oba telefony w tej samej Wi-Fi → 🌐 → na jednym „⌂ Hostuj" (pokaże IP) → na drugim „⇥ Dołącz" i wpisz to IP. Zdalnie otwarte okna (kino/galeria) czekają na własny wybór treści — synchronizuje się układ, nie media. Bez internetu, czysty LAN.

*(Binary asset upload is blocked in this environment — the APK lives in the repo under `release/`.)*

## 🧪 Test dwutelefonowy

1. Telefon A: 🌐 → Hostuj → zapisz IP z góry okna
2. Telefon B: 🌐 → Dołącz → IP telefonu A
3. B: przeciągnij okno / otwórz 🎬 → A: sprawdź, że to samo widzi
4. Spójrz w bok — na drugim telefonie romb pokaże Twój kierunek
5. ✕ Opuść → rozłączenie (uwaga: wspólny pokój **nie był jeszcze testowany na prawdziwych telefonach** — jeśli coś nie gra, 🩺 Diagnostyka + zrzut!)

*Debug-signed build. Ruch w sieci dotyczy tylko LAN (port 47777) — nic nie wychodzi do internetu.*
