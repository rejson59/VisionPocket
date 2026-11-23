## ⬇️ POBIERZ

➡️ **[Strona instalacji](https://rejson59.github.io/VisionPocket/)** — przycisk „Zainstaluj" / QR
- terminal: `adb install -r VisionPocket-v3.3.apk`

## 🖼 v3.3 — PRZEBUDOWA WYGLĄDU W OKULARACH (dokładnie Twoja prośba: „2 okrągłe kółka na oczy, nie losowe kształty")

**Co widziałem na Twoim zrzucie:** content obrócony o 90°, kafle/dock ukośne, viewporty pionowe — klasyczny obraz, gdy render wewnętrzny idzie do bufora o ZŁYM aspekcie/orientacji względem fizycznego ekranu, a UI stoi pod kątem do osi widoku.

**Co zmienione (warstwa prezentacyjna przepisana na model Google Cardboard):**
1. **RENDER BEZPOŚREDNI NA EKRAN** — zero pośredniego bufora FBO w domyślnej ścieżce: każde oko renderuje się **dosłownie w fizycznej połowie panelu** (viewport = połowa ekranu). Geometria/poprawność „2 kółek" wynika z konstrukcji, nie z moich obliczeń aspektu. Stary FBO+distortion zostaje tylko dla ręcznie włączonego Zniekształcenia > 0.
2. **MASKA 2 OKRĄGŁYCH PÓL** jako overlay — czysty wygląd cardboard, czarne tło, zaokrąglone pola.
3. **AUTO-KOREKTA POWIERZCHNI**: jeśli system dostarczy powierzchnię w pionie przy poziomym telefonie — wymiary są zamieniane automatycznie (guard), a telemetria pokazuje `surf SZERxWYS` (+ „!") — **twarde liczby na zrzucie**, nie zgadywanie.
4. **UKŁAD JAK W QUEST**: pulpit u góry przed Tobą, **rząd 6 kafli w łuku** pod nim (±30°) — wszystko w polu widzenia, zero kafli za plecami/na bokach pod kątem.
5. Śledzenie z v3.2 (Google Cardboard chain, grawitacja co klatkę) i kamera pełnoekranowa z v3.1 zostają.

## 🧪 Test (2 min)

1. Zainstaluj v3.3 → START VR
2. **Dwa proste, zaokrąglone pola widoku, content PROSTO** — pulpit u góry, rząd kafli pod nim
3. Dolna linia pulpitu: `surf 1536x720` (albo podobne) — przyślij zrzut z tymi liczbami
