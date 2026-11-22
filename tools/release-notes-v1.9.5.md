## ⬇️ POBIERZ APK

**Sposób 1:** [➡️ kliknij TUTAJ — plik APK](https://github.com/rejson59/VisionPocket/raw/arena/49b8c2a9-visionpocket/release/VisionPocket-v1.9.5.apk) → „Download raw file"
**Sposób 2:** „Source code (zip)" → w środku `release/VisionPocket-v1.9.5.apk`

## 🧭 v1.9.5 — kalibracja z FIZYKI, nie z konwencji

Przeglądam wczorajsze próby: używałem tabel „jak Android mapuje osie przy obrocie ekranu" — a każda z nich okazywała się zła o ćwiartkę obrotu. v1.9.5 **wyrzuca wszystkie tabele** i kalibruje ramkę głowy z **akcelerometru**:

- akcelerometr fizycznie mierzy, gdzie jest „w górę" — to nie konwencja, tego nie da się pomylić
- „przód" wynika z geometrii kartonu (przez ekran od oczu), „prawy" jest wymuszony ortogonalnością — zero lustrzanych pułapek
- kalibracja dzieje się w momencie START VR — dla **dokładnie tego ułożenia telefonu**, w jakim go włożyłeś
- jedyna możliwa resztkowa pomyłka to telefon włożony „do góry nogami" — naprawa: **🔄 ×2** w Ustawieniach (i tyle)

## 🧪 Test (2 min)

1. Włóż telefon do kartonu, jak zwykle
2. START VR → **świat powinien być prosto i stać nieruchomo względem pokoju**
3. Obróć głowę — okna zostają na swoich miejscach w przestrzeni ✓
4. Gdyby wszystko było „do góry nogami" → Ustawienia → 🔄 ×2
5. Przez soczewki: Zniekształcenie 20–60%
6. Napisz co widzisz / wyślij 📤 raport (linia `mount ok` potwierdzi kalibrację)
