## ⬇️ POBIERZ

➡️ **[Strona instalacji — klik i gotowe](https://rejson59.github.io/VisionPocket/)** (przycisk „Zainstaluj”, QR, ADB)
- albo bezpośrednio: [VisionPocket-v1.9.6.apk](https://rejson59.github.io/VisionPocket/VisionPocket-v1.9.6.apk)
- albo z terminala: `adb install -r VisionPocket-v1.9.6.apk`

## 🆕 v1.9.6 — wygląd w stylu cardboard (jak w dobrych DIY) + koniec zgadywania orientacji

- 👓 **Czyste 2 viewporty z zaokrąglonymi rogami i czarnym rozdziałem** — na surowym ekranie wygląda dokładnie jak porządne gogle kartonowe (scena prosto, wypełnia całość oka, zero klinów na krawędziach)
- ✅ **Okno startowe pyta: „Obraz prosto?”** — jeden tap [✓ Prosto] albo [↻ Obróć 180°]; zapisywane na stałe. Koniec pętli „znowu krzywo”
- 🩹 **FIX: przycisk „Zainstaluj” na stronie zwracał 404** — `.gitignore` blokował APK w docs/; teraz plik leży na Pages i pobieranie działa
- kalibracja grawitacyjna z v1.9.5 bez zmian (fizyka, nie konwencje)

## 🧪 Test (2 min)

1. Strona → Zainstaluj (teraz naprawdę pobiera) / ADB
2. START VR → patrz na surowy ekran: **dwa proste, zaokrąglone okruchy obrazu**
3. Okno startowe: obraz prosto? → ✓; odwrócony? → ↻ Obróć 180° (zapamiętuje)
4. Przez soczewki: Zniekształcenie 20–60% do dostrojenia
