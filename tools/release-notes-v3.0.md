## ⬇️ POBIERZ

➡️ **[Strona instalacji](https://rejson59.github.io/VisionPocket/)** — przycisk „Zainstaluj" / QR
- terminal: `adb install -r VisionPocket-v3.0.apk`

## 🌍 v3.0 — PRAWDZIWY ŚWIAT 360° (twoja diagnoza była trafna)

**Co było zepsute:** dotychczasowe „środowisko" to był **mały płaski prostokąt z gradientem za oknami**, a wszystko poza nim — czysta czerń (`glClear`). Dlatego wyglądało to jak „gogle, przez które tylko widzę, a reszta jest ciemna". Tracking miał się dobrze — **świat był fikcją**.

**Co jest teraz:**
1. **Kopuła 360° liczona per-piksel z kierunku spojrzenia** — gdziekolwiek się obejrzysz, jest świat:
   - **Pokój** (domyślny): podłoga z siatką, 4 ściany z panelami dekoracyjnymi, jasny panel świetlny pod sufitem, delikatna poświata przy podłodze
   - **Kosmos**: gwiazdy + mgławica + łuna przy horyzoncie
   - **Horyzont**: otwarta przestrzeń — gradient nieba + nieskończona siatka podłogi
2. **Kafle aplikacji w PIERŚCIENIU dookoła Twojej głowy** (co 60°: przód, obie boki, za plecami) — „widok wszędzie gdzie się spojrzę": 🎬 Kino · 🖼 Zdjęcia · ▣ Duplikat · 📝 Notatki · 🕒 Zegar · 🎯 Gra
3. **Pulpit przed sobą**: duży zegar + bateria, potwierdzenie orientacji, akcje, żywa telemetria (v · kąty YAW/PITCH/ROLL)
4. Śledzenie głowy z v2.1 (snapshot pool + filtr τ) zostaje — było mechanistycznie poprawne

## 🧪 Test (2 min)

1. Zainstaluj v3.0 → START VR
2. **Obejrzyj się dookoła** — podłoga, ściany, sufit mają być WSZĘDZIE, nie tylko z przodu
3. Kafle: przed tobą + boki + za plecami; kliknij dowolny wzrokiem
4. [✓ Prosto] / [↻ 180°] jeśli trzeba; Zniekształcenie pod soczewki
5. Zrzut pulpitu → wyślij
