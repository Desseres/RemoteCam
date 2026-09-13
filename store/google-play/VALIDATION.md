# Weryfikacja pakietu — 11.09.2026

- Wersja 0.3.1 / code 16, gałąź `codex/google-play-preparation`; przygotowanie lokalne,
  bez publikacji w Google Play. `build-info.json` artefaktów wskazuje bazowy commit
  i informuje o niezacommitowanych zmianach.
- Nowa tożsamość instalacji: `pl.remotecam.app`. Potwierdzona w podpisanym APK
  (`aapt dump badging`) oraz bezpośrednio w protobuf manifestu podpisanego AAB.
  Activity i usługa mają poprawne pełne nazwy klas z dotychczasowego namespace.
  AAB i APK podpisano certyfikatem o odcisku podanym poniżej; sprawdzono oba podpisy.
  Artefakty: `dist/google-play/pl.remotecam.app/0.3.1/`.
- `:app:assembleDebug :app:assembleRelease :app:bundleRelease :app:testDebugUnitTest :app:lintRelease`
  zakończone pomyślnie. 21 testów, 0 błędów i 0 niepowodzeń. Lint: `No issues found.`
- Release APK: weryfikacja apksigner, jeden certyfikat RSA 4096, podpis v3;
  `zipalign -c -P 16 4` zakończone poprawnie. AAB: `jarsigner -verify` poprawnie;
  samopodpisany certyfikat Androida nie wymaga publicznego CA ani znacznika czasu.
- Certyfikat SHA-256: `09d27a8aaa9534acd782e745636d7274ff758bb014f1052d98be8760f92dcfbf`.
- Testy fizyczne poniżej wykonano wcześniej na wersji debug o poprzednim identyfikatorze
  `com.samsung.android.scan3d`, zainstalowanej jako aktualizacja na Honor BVL-N49 / Android 16.
  Nowego pakietu nie instalowano na telefonie w ramach samej przebudowy.
  Zapisane ustawienia zachowane. Zrzut nowego splash screena: `screenshots/05-launch-screen.png`.
  Ilustracja widoczna podczas startu; opóźnienie 1000 ms jest realizowane asynchronicznie
  od pierwszego przygotowania klatki, bez blokowania wątku UI. Powrót Home → aplikacja
  nie odtwarza splash screena; `captureGeneration=1` przed i po powrocie, licznik klatek rośnie.
- Po starcie działał odbiornik WebRTC: kamera bez błędów, około 30 fps i aktywne połączenie.
  Sprawdzono ręcznie Info → Privacy policy: poprawna treść, wydawca i klikalny adres e-mail.
- 6 grafik 1080 × 1920 + 2 zrzuty 1080 × 1920: PNG RGB; feature 1024 × 500 RGB;
  ikona 512 × 512 RGBA poniżej 1 MB. Obejrzano zestaw grafik i banner; brak uciętych nagłówków.
- Opisy: tytuł 9/30 znaków, krótki opis 71/80, pełny opis 2519/4000.
- Ilustracja używa wbudowanego `image_gen`; grafiki informacyjne i ikona to źródła wektorowe.

Nie wykonano: uploadu do Play, testów raportu przedpremierowego, weryfikacji konta,
zamkniętych testów wymaganych od niektórych kont, filmiku dla deklaracji FGS ani hostowania
polityki. Nie potwierdzono działania runtime na Androidzie 17 ani na wszystkich architekturach.
Identyfikator pakietu wybrano. Kwestie szyfrowania transportu i ujawnień dotyczących kamery w tle opisano
w [PLAY-CONSOLE.md](PLAY-CONSOLE.md); materiały nie są deklaracją pełnej zgodności ze sklepem.
