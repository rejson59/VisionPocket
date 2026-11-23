## ⬇️ POBIERZ

➡️ **[Strona instalacji](https://rejson59.github.io/VisionPocket/)** — przycisk „Zainstaluj" / QR / ADB
- bezpośrednio: [VisionPocket-v1.9.7.apk](https://rejson59.github.io/VisionPocket/VisionPocket-v1.9.7.apk)
- terminal: `adb install -r VisionPocket-v1.9.7.apk`

## 🆕 v1.9.7 — znaleziony ZWIĄZKOWY błąd wyścigu (to tłumaczy „ciągle takie samo")

**Kalibracja grawitacyjna z v1.9.5/1.9.6 wykonywała się ZANIM akcelerometr dostarczył pierwszą próbkę** — była po cichu pomijana, a ramka głowy zostawała tożsamościowa → **dokładnie ta sama stara rotacja, którą widziałeś od początku**. Wersje 1.9.5/1.9.6 zawierały poprawną matematykę, ale nigdy się nie uruchamiała.

**Naprawa:** kalibracja odpala automatycznie w momencie nadejścia pierwszych danych grawitacji (≈50 ms po starcie VR) + ponawiana przy każdym START VR.

## 🆕 Jak sprawdzisz, CO masz uruchomione (koniec zgadywania)

**Dolna linia pulpitu startowego pokazuje:** `v1.9.7 · fbo … · dist 0% · IPD … · viewRot 0 · mount ok · 60 fps`
- **`v1.9.7`** — jeśli widzisz inną wersję, instalacja się nie wykonała (wróć na stronę)
- **`mount ok`** — kalibracja grawitacyjna zadziałała
- jeśli po starcie widzisz `mount raw` → zrób zrzut i wyślij — to znaczy, że akcelerometr nie daje danych

## 🧪 Test (2 min)

1. Zainstaluj v1.9.7 (strona / ADB) → START VR
2. **Dolna linia: czy widzisz v1.9.7 i mount ok?**
3. Świat powinien wreszcie stać prosto (to pierwsza wersja, w której kalibracja na pewno się wykonuje)
4. Zrób ZRZUT pulpitu startowego i wyślij — linia statusu powie mi wszystko
