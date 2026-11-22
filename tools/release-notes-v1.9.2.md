## ⬇️ POBIERZ APK

**Sposób 1:** [➡️ kliknij TUTAJ — plik APK](https://github.com/rejson59/VisionPocket/raw/arena/49b8c2a9-visionpocket/release/VisionPocket-v1.9.2.apk) → „Download raw file"
**Sposób 2:** „Source code (zip)" → w środku `release/VisionPocket-v1.9.2.apk`

## 🛠 Prawdziwa przyczyna przekręconego obrazu

**Śledzenie ruchu głowy (gyroskop) liczyło obraz tak, jakby telefon stał PIONOWO — a Ty trzymasz go POZIOMO.** Cała scena była więc obrócona o ~90°: okna „na boki", pasy UI skręcone, passthrough w kliny. (Warp soczewek z v1.9.1 był tylko współwinnym — teraz startuje z 0 i tak zostaje.)

**v1.9.2 naprawia ramkę śledzenia:**
- automatyczna korekta wg orientacji ekranu (poziomo-lewo / poziomo-prawo / pion)
- dodatkowy przycisk **🔄 ×N** (Ustawienia, dolny rząd) — kalibracja roli co 90°: tapnij tyle razy, aż obraz stoi prosto (zapisywane na stałe)

## 🧪 Test (3 min)

1. Zainstaluj v1.9.2 nad poprzednią
2. START VR → **obraz powinien być prosto** (telefon poziomo, góra w lewo = standard kartonów)
3. Jeśli nadal krzywo → tapaj **🔄** w Ustawieniach (0→1→2→3) aż stanie prosto
4. Patrz przez soczewki → **Zniekształcenie − / +** do dostrojenia (zwykle 20–60%)
5. Jeśli **góra/dół jest odwrócona** (patrzysz w górę, a scena idzie w dół) → 📤 raport — to osobna, 1-liniowa naprawa
