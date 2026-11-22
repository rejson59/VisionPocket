## ⬇️ POBIERZ APK

**Sposób 1:** [➡️ kliknij TUTAJ — plik APK](https://github.com/rejson59/VisionPocket/raw/arena/49b8c2a9-visionpocket/release/VisionPocket-v1.9.1.apk) → „Download raw file"
**Sposób 2:** „Source code (zip)" z tej strony → w środku `release/VisionPocket-v1.9.1.apk`

## 🚨 Co było nie tak (Twój zrzut ekranu)

Ta wersja to pierwszy w historii projekt uruchomienia silnika na prawdziwym telefonie — i Twoje zdjęcie pokazało, że **pre-warp soczewek startował z niesprawdzonych stałych (100%)**, wyginając obraz w złą stronę i poza teksturę (czarne kształty, skręcone pasy UI).

**v1.9.1 = start PŁASKI (0% dystorsji):**
- ekran pokaże czysty stereo bez wygięć (przez soczewki kartonowe będzie lekko „poduszka" — to normalne i czytelne)
- dystorsję dostrajasz sam: **Ustawienia → Zniekształcenie − / + (10% krok)** tak, by przez Twoje soczewki linie były proste
- 🩺 Diagnostyka pokazuje teraz linię silnika (scr/eye/fbo/dist/IPD/env/rot/fps) — **📤 wyślij mi raport**, jeśli coś dalej wygląda źle

## 🧪 Plan testu (5 min)

1. Zainstaluj v1.9.1 **nad** v1.9 (resetuje dystorsję automatycznie)
2. START VR → czy pulpity/okna wyglądają **prosto** na płaskim ekranie?
3. Patrz **przez soczewki** → Ustawienia → Zniekształcenie: dodawaj 10% aż obraz przez soczewki będzie najprostszy (zwykle 20–60% dla tanich kartonów)
4. Jeśli już na PŁASKIM ekranie okna są skrzywione → 📤 raport + zrzut, to będzie inna przyczyna i naprawię precyzyjnie
