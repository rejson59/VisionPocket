## ⬇️ POBIERZ

➡️ **[Strona instalacji](https://rejson59.github.io/VisionPocket/)** — przycisk „Zainstaluj" / QR
- terminal: `adb install -r VisionPocket-v3.4.apk`

## 🔺 v3.4 — FIX „TRÓJKĄTA" (znaleziony mechanizm, nie zgadywanka)

**Diagnoza z Twojego zrzutu:** lewa połowa czarna, prawa = jeden kwadratowy region → **powierzchnia GL jest dostarczana w PIONIE** (np. 720×1536), a pola widzenia oczu rysowały się OBOK SIEBIE (poziomo). Kompozycja systemowa obraca taki bufor o 90° na poziomy panel → każde oko widziało miksy obu oczu + przycięcia = „trójkąt".

**Fix — niezmiennik cardboard:** pola oczu układane **WZDŁUŻ DŁUŻSZEJ OSI POWIERZCHNI**:
- powierzchnia pozioma → pola obok siebie (jak dotychczas)
- powierzchnia pionowa → **jedno pole NAD DRUGIM** → po obrocie kompozycji wylądują na lewej/prawej połowie panelu — poprawnie w goglach
- działa dla OBU ścieżek (bezpośredniej i z Zniekształceniem), maska 2 pól też się przełącza
- telemetria pokazuje tryb: `surf 720x1536·S` (stacked) lub `...·L` — **twarde liczby na zrzucie**

## 🧪 Test (2 min)

1. Zainstaluj v3.4 → START VR
2. Oczekiwane: dwa pełne, proste pola widzenia — content CAŁY w każdym polu
3. Przyślij zrzut pulpitu — dolna linia pokaże `surf ...x...·S` albo `·L` (kluczowa informacja diagnostyczna)
