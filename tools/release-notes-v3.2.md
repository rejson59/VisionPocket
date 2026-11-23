## ⬇️ POBIERZ

➡️ **[Strona instalacji](https://rejson59.github.io/VisionPocket/)** — przycisk „Zainstaluj" / QR
- terminal: `adb install -r VisionPocket-v3.2.apk`

## 🧠 v3.2 — śledzenie głowy wg GOOGLE CARDBOARD SDK (kod Google, nie mój)

Zrobiłem to, co kazałeś: **poszedłem do internetu i wziąłem algorytm z prawdziwego projektu** — oficjalnego, open-source'owego **Google Cardboard SDK** (github.com/googlevr/cardboard, plik `sdk/head_tracker.cc`, licencja Apache 2.0). Matematyka przeniesiona **1:1**, z atrybucją w kodzie:

1. **Rotation vector → widok** dokładnie łańcuchem Google: `SensorToDisplay[viewport] × sensorRotation × EkfToHeadTracker[viewport]` — z oficjalnymi tablicami rotacji dla LandscapeLeft/Right/Portrait/PortraitUD (skopiowane verbatim).
2. **KONIEC zamrożonej kalibracji** (przyczyna WSZYSTKICH dotychczasowych przechyleń): rotacja świata jest referencjonowana do grawitacji **w każdej klatce** — nie ma już kalibracji z jednej próbki akcelerometru, która zamarzała z pochylonym światem. Niezależnie jak telefon leży w gnieździe — **horyzont poziomy**.
3. **Orientacja ekranu (LandscapeLeft/Right/Portrait)** obsługiwana oficjalnymi tablicami Google + `sensorLandscape` systemowo.
4. Auto-recenter 600 ms po starcie VR + ręczny Recenter zawsze dostępny.
5. Kamera pełnoekranowa z v3.1 zostaje + dodatkowe pokrycie 1.04× (koniec czarnych klinów przy krawędziach).

## 🧪 Test (2 min)

1. Zainstaluj v3.2 → START VR
2. **Horyzont poziomy, świat stoi prosto** — przechyl telefon w gnieździe: świat NIE przechyla się z nim
3. Obejrzyj się dookoła (świat 360° z v3.0 + kafle w pierścieniu)
4. Jeśli forward nie tam gdzie chcesz → Recenter (1 tap)
5. Zrzut pulpitu → wyślij
