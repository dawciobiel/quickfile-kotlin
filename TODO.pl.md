# TODO Lista

Ten plik opisuje najbliższy plan prac wynikający z zaakceptowanych ADR-ów i roadmapy v1.

## Teraz

- [ ] Milestone 2: zdefiniować granicę browse w `:core`
- [ ] Zastąpić demo-dane paneli prawdziwym flow browse
- [ ] Zaimplementować discovery pluginów z katalogu konfiguracyjnego
- [ ] Zaimplementować parsowanie `plugin.json` oraz sprawdzanie wersji API
- [ ] Zaimplementować uruchamianie procesu JVM pluginu i handshake startowy

## Następnie

- [ ] Stworzyć referencyjny plugin lokalnego filesystemu
- [ ] Podłączyć jeden panel do prawdziwego listowania katalogu
- [ ] Rozszerzyć flow browse na oba panele
- [ ] Wprowadzić typowane błędy browse w stanie panelu
- [ ] Dodać pierwsze testy integracyjne host/runtime/plugin

## Później

- [ ] Zaimplementować `JobRegistry` w `:core`
- [ ] Dodać operacje na plikach jako obserwowalne joby
- [ ] Dodać anulowanie i obsługę stanów terminalnych jobów
- [ ] Dodać focus klawiatury, aktywny panel i skróty
- [ ] Dodać nawigację ustawień i trwałość preferencji

## Backlog

- [ ] Zaprojektować kontrolowane przez hosta punkty rozszerzeń UI dla pluginów
- [ ] Dodać UX instalacji i pakowania pluginów
- [ ] Ocenić szersze okno kompatybilności API pluginów po v1
- [ ] Dodać pełniejszą checklistę regresji dla browse i operacji
