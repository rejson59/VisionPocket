## ⬇️ POBIERZ APK

**Sposób 1:** [➡️ kliknij TUTAJ — plik APK](https://github.com/rejson59/VisionPocket/raw/arena/49b8c2a9-visionpocket/release/VisionPocket-v1.9.3.apk) → „Download raw file"
**Sposób 2:** „Source code (zip)" z tej strony → w środku `release/VisionPocket-v1.9.3.apk`

## 🛠 Trzecia przyczyna znaleziona (i tym razem udowodniona matematycznie)

Twoje 3. zrzuty ekranu wykazały coś ważnego: celownik i pasek („przypięte do głowy") były PROSTE, a **cały pulpit zniknął za plecami** (widać było tylko klin passthrough). Przyczyna: **odwrócony znak obrotu przy wyśrodkowaniu** — `recenter()` obracał widok o R(+ψ), a pulpit o R(−ψ). Każde wyśrodkowanie — także **automatyczne przy starcie** — wysyłało okna 2× dalej za głowę.

Sprawdziłem też, jak to robi oficjalny SDK Google Cardboard: recenter = zerowanie yaw tak, by obecny kierunek stał się „przodem", bliźniacze viewporty z tą samą sceną + finalna dystryorsja ([GvrView docs](https://developers.google.com/vr/reference/android/com/google/vr/sdk/base/GvrView), [Cardboard SDK](https://vrarwiki.com/wiki/Google_Cardboard)). Nasz pipeline strukturalnie się zgadza — błąd był tylko w tym jednym znaku.

**Naprawa:** obrót świata zgodny z obrotem widoku (R(+ψ) dla obiektów). Po tej zmianie:
- START VR → pulpit **stoi przed Tobą** (auto-recenter teraz go tam ustawia, nie wyrzuca)
- każde ręczne wyśrodkowanie ⟲ bringuje pulpit z powrotem na środek

## 🧪 Test (2 min)

1. Zainstaluj v1.9.3 nad poprzednią
2. START VR → **powinno być: prosto + pulpit przed Tobą + passthrough wypełnia tło**
3. Dostroj: Zniekształcenie (przez soczewki), 🔄 tylko jeśli telefon leży nietypowo
4. Napisz co widzisz — jeśli pulpit jest, ale np. **góra/dół odwrócona** (pitch), to ostatnia możliwa oś i znam na nią gotową poprawkę
