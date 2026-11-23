## ⬇️ POBIERZ

➡️ **[Strona instalacji](https://rejson59.github.io/VisionPocket/)** — przycisk „Zainstaluj" / QR
- terminal: `adb install -r VisionPocket-v2.0.apk`

## 🆕 v2.0 — ŚLEDZENIE GŁOWY NAPISANE OD ZERA (jak prosiłeś)

Cały podsystem orientacji jest nowym plikiem, bez jednej linii starego kodu:

- **Kwaterniony** zamiast łańcucha macierzy z lerpowaniem i re-ortogonalizacją (ten łańcuch mógł się cicho psuć na urządzeniu)
- **Samoweryfikujący recenter** — algorytm OBLICZA i TESTUJE oba znaki obrotu, wybiera ten, który realnie mapuje kierunek patrzenia na „przód". Koniec kategorii błędów „źle zgadnięty znak"
- **Kalibracja z akcelerometru** (fizyczne „w górę", nie konwencje) — odpala z pierwszą próbką grawitacji i przy każdym START VR
- **ŻYWY ODCZYT NA PULPICIE**: druga linia na dole pokazuje w czasie rzeczywistym:
  `yaw 12° pitch -3° roll 2° · mount ∠4° · rv 18ms`
  - `yaw/pitch/roll` — kąty Twojej głowy, aktualizują się na żywo (porusz głową — liczby muszą chodzić!)
  - `mount ∠…°` — rezydualna odczyt kalibracji (ma być blisko 0)
  - `rv …ms` — świeżość danych z żyroskopu
- [✓ Prosto] / [↻ Obróć 180°] i 🔄 ×N zostają jako ręczne korekty

## 🧪 Test (3 min)

1. Zainstaluj v2.0 → START VR
2. **Porusz głową i patrz na dolne linie**: kąty muszą się zmieniać; `mount ∠` blisko 0–30°
3. **ZRZUT PULPITU** (dolne 2 linie + obraz) → wyślij — jeśli cokolwiek jest krzywe, odczyt powie mi DOKŁADNIE która oś i o ile; naprawa będzie matematyczna, nie zgadywana
4. Obraz prosto? [✓]. Odwrócony? [↻ 180°]. Świat odchylony o ćwiartkę? 🔄 ×1
