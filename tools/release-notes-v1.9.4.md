## ⬇️ POBIERZ APK

**Sposób 1:** [➡️ kliknij TUTAJ — plik APK](https://github.com/rejson59/VisionPocket/raw/arena/49b8c2a9-visionpocket/release/VisionPocket-v1.9.4.apk) → „Download raw file"
**Sposób 2:** „Source code (zip)" → w środku `release/VisionPocket-v1.9.4.apk`

## 🛠 Czwarta (i ostatnia możliwa) przyczyna roli 90°

v1.9.3 przywróciła pulpit przed oczy (Twoje 4. zdjęcie to potwierdza — widać kafle), ale **rola dalej była zła**. Przyczyna: mój ręczny „montaż" orientacji miał odwrócone przypadki ROTATION_90/270. Zamiast zgadywać dalej — **v1.9.4 używa kanonicznej tabeli `SensorManager.remapCoordinateSystem`** (tej samej, którą używają aplikacje kompasu, żeby igła zgadzała się z tym, co widzisz) + **sensorLandscape** (obie strony poziome obsługiwane poprawnie).

- 🔄 w Ustawieniach zostaje jako ręczna kalibracja (gdyby Twój karton wymagał nietypowej roli)
- 🩺 raport pokazuje teraz `dispRot` i `viewRot` — będzie widać dokładnie konfigurację

## 🧪 Test (2 min)

1. Zainstaluj v1.9.4 → START VR → **pulpit powinien stać PROSTO przed Tobą** (kafle czytelne normalnie, dock poziomo na dole)
2. Jeśli nadal krzywo → tapaj 🔄 (×1, ×2, ×3) — jedna z czterech pozycji MUSI być prosta, bo to obrotowa kalibracja o pełne ćwiartki
3. Przez soczewki: Zniekształtenie − / +
4. Wyślij mi wynik: (a) prosto?, (b) pitch w dobrą stronę?, (c) jeśli coś nie tak → 📤 raport
