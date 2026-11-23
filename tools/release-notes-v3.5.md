## ⬇️ POBIERZ

➡️ **[Strona instalacji](https://rejson59.github.io/VisionPocket/)** — przycisk „Zainstaluj" / QR
- terminal: `adb install -r VisionPocket-v3.5.apk`

## ⚔️ v3.5 — RAZ NA ZAWSZE: stara ścieżka renderowania USUNIĘTA

**Dlaczego nic się nie zmieniało, mimo kolejnych poprawek:** aplikacja ma ustawienie „Zniekształtenie" (od wersji z soczewkami). Jeśli miało wartość > 0 — **CAŁY obraz szedł starą ścieżką FBO+distortion**, którą przepisuję dopiero od v3.3, a którą Twoje preferencje (przetrwały przez wszystkie instalacje) włączały w kółko. Wszystkie moje poprawki z v3.2–v3.4 trafiały w ścieżkę, której Twój telefon nie używał. To tłumaczy WSZYSTKO.

**Co zrobione:**
1. Ścieżka FBO+distortion **trwale wyłączona w kodzie** — zostaje DOKŁADNIE JEDNA ścieżka: bezpośrednia (viewport = fizyczna połowa ekranu, pola oczu wzdłuż dłuższej osi powierzchni). Uszkodzenia geometrii nie mają gdzie się ukryć.
2. Telemetria pokazuje teraz `direct` zamiast `dist N%` — żeby było oczywiste, że działa nowa ścieżka.
3. Wszystko z v3.2–v3.4 zostaje: śledzenie Google Cardboard (grawitacja co klatkę), kamera pełnoekranowa, układ Quest (pulpit + łuk kafli), maska 2 pól, auto-stacking przy pionowej powierzchni.

## 🧪 Test (2 min)

1. Zainstaluj v3.5 → START VR
2. Dolna linia pulpitu musi pokazać `v3.5 · direct` — to potwierdzenie nowej ścieżki
3. Oczekiwane: dwa pełne, proste pola widzenia (albo `surf ...·S` przy pionowej powierzchni — stacking zadziała)
4. Zrzut pulpitu → wyślij (litera S/L + liczby = ostateczna diagnostyka)
