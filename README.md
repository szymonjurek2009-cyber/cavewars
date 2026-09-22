# CaveWars

Projekt pluginu CaveWars dla serwera Minecraft (Paper/Pufferfish 1.21.10, Java 21).

To repozytorium zawiera migawkę kodu i plików projektu z serwera `cavewars-gargamerl` z 22 września 2026 r. Kod modułów pochodzi z lokalnego katalogu roboczego `.cache/cavewars-merge/src`. Dołączone pliki JAR są artefaktami z serwera/katalogu roboczego; nie zakładaj, że da się je odtworzyć bez brakujących zależności i procesu budowania.

## Zawartość

- `src/main/java` — kod Java modułów CaveWars.
- `src/main/resources/plugin.yml` — deskryptor pluginu z katalogu roboczego.
- `tools` — lokalne skrypty używane przy łączeniu/aktualizowaniu modułów.
- `config-examples` — konfiguracje pluginów bez danych prywatnych.
- `dist` — gotowe, istniejące na serwerze artefakty CaveWars (o ile dołączone).

## Bezpieczeństwo

Repozytorium jest **publiczne**. Nie należy umieszczać tutaj plików `.env`, tokenów, haseł RCON, list graczy, logów, danych statystycznych graczy ani bieżących plików świata z prywatnymi danymi. Konfiguracje pod `config-examples` traktuj jako przykłady, a nie kopie całego działającego serwera.

Oryginały na serwerze pozostają nienaruszone.
