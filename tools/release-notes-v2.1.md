## ⬇️ POBIERZ

➡️ **[Strona instalacji](https://rejson59.github.io/VisionPocket/)** — przycisk „Zainstaluj" / QR
- terminal: `adb install -r VisionPocket-v2.1.apk`

## 🆕 v2.1 — koniec trzęsienia + nowy interfejs

**Dwie twarde przyczyny drgań znalezione w v2.0 (audyt wątków):**
1. **Wyścigi danych** — wątek sensora i wątek renderujący używali tych samych buforów jednocześnie (rozdarte kwaterniony = gwałtowne szarpnięcia). Teraz: **pool 16 par snapshotów** — pisarz nigdy nie dotyka aktualnie publikowanego slotu, więc render zawsze czyta spójny, niezmienny stan.
2. **Szum żyroskopu szedł na ekran** — lerp 0.45/event przy 100–500 Hz = brak wygładzenia. Teraz: **filtr o stałej czasowej τ=55 ms** (α = 1−e^(−dt/τ)) — identyczne, gładkie zachowanie niezależnie od częstotliwości próbkowania + EMA na akcelerometrze (stabilna kalibracja).

**Nowy interfejs:**
- `UiKit` — jedna biblioteka wyglądu (kolory, przyciski, karty, nagłówki) dla całej aplikacji
- Redesign pulpitu startowego: duży zegar + bateria, **kafle z ikoną, nazwą i opisem**, rząd orientacji, akcje, telemetria (wersja + kąty YAW/PITCH/ROLL na żywo)

## 🧪 Test (2 min)

1. Zainstaluj v2.1 → START VR
2. **Świat powinien być gładki** — obracaj głowę powoli i szybko; żadnych szarpań
3. Pulpit: nowy wygląd + dolne linie (`v2.1`, kąty na żywo)
4. [✓ Prosto] / [↻ 180°] jeśli trzeba; przez soczewki Zniekształcenie
5. Zrzut pulpitu → wyślij — kąty powiedzą mi wszystko
