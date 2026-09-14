# Podpisywanie i przygotowanie wydania

APK przeznaczone do pobierania powinno pochodzić z wariantu **release** i być
podpisane stałym, prywatnym kluczem wydawcy. Gradle nie używa automatycznie klucza
debug do release. Skrypt poniżej podpisuje APK dopiero po jego zbudowaniu.

## Opisy wersji

- [0.3.3 — wspólne wydanie Android + Windows (English)](releases/v0.3.3.md):
  podpisane APK 0.3.3 i instalator Desktop 0.1.10 w jednym wydaniu testowym.
- [0.3.2 — dźwięk z mikrofonu przez WebRTC](releases/v0.3.2.md): zmiany,
  konfiguracja OBS i go2rtc, zgodność, wyniki testów oraz pliki do pobrania.
- [0.3.0 — modernizacja aplikacji i transmisji](releases/v0.3.0.md).

## Klucz wydawcy — jednorazowo

W PowerShell na Windows:

```powershell
.\tools\new-release-key-windows.ps1
```

Skrypt tworzy klucz RSA 4096 bitów w kontenerze PKCS#12, z aliasem
`remotecam-release`, w `%LOCALAPPDATA%\RemoteCam\signing`. Hasło jest losowe
i zapisane osobno w pliku `remotecam-release.password.dpapi`, chronionym przez
Windows DPAPI bieżącego użytkownika. Folder ma ograniczone uprawnienia dostępu.
Skrypt odmawia nadpisania istniejących plików. Klucz i hasło nie należą do Git
ani do załączników wydania.

**Zachowaj bezpieczną kopię klucza oraz hasła przed publikacją.** Plik DPAPI nie
zastępuje przenośnej kopii hasła: może nie dać się odszyfrować po zmianie konta
lub reinstalacji systemu. Aby samodzielnie odczytać hasło w swoim lokalnym terminalu
i zapisać je w bezpiecznym magazynie, użyj:

```powershell
$secret = (Get-Content "$env:LOCALAPPDATA\RemoteCam\signing\remotecam-release.password.dpapi" -Raw).Trim() | ConvertTo-SecureString
[Net.NetworkCredential]::new('', $secret).Password
$secret.Dispose()
```

To polecenie wyświetla sekret — nie wklejaj jego wyniku do issue, PR, logów ani
repozytorium. Do przyszłych aktualizacji używaj tego samego klucza. Zewnętrzny klucz
można wykorzystać, przygotowując własny katalog z tymi nazwami plików i aliasem,
albo podpisując APK bezpośrednio narzędziem Android `apksigner`.

## Wydanie z oznaczonego commita

1. Zwiększ `versionCode` i `versionName` w `app/build.gradle` oraz przygotuj opis
   `docs/releases/vWERSJA.md`.
2. Przeprowadź walidację:

   ```powershell
   .\tools\build-windows.ps1 :app:assembleRelease :app:testDebugUnitTest :app:lintRelease
   ```

3. Zrób commit i adnotowany tag `vWERSJA`, zgodny z `versionName`.
4. Uruchom:

   ```powershell
   .\tools\package-release-windows.ps1
   ```

Skrypt wymaga czystego drzewa Git, taga wskazującego HEAD, JDK i Android SDK
Build-Tools 37.0.0. Buduje uniwersalne APK, wykonuje wyrównanie dla 16 KB,
podpisuje APK (schemat v3 dla Androida 9+) i sprawdza podpis, wyrównanie, numer wersji oraz brak
flagi `debuggable`. Nie zapisuje haseł w argumentach procesu ani w konfiguracji
Gradle. Skrypt nie publikuje wydania w GitHubie.

W `dist/vWERSJA/` powstają:

- `RemoteCam-WERSJA.apk` — APK do pobrania;
- `SHA256SUMS.txt` — sumy kontrolne;
- `release-notes.md` — gotowy opis wydania;
- `build-info.json` — commit, tag, wersja aplikacji i publiczny odcisk certyfikatu.

Katalog `dist/` jest ignorowany przez Git. Skrypt nie nadpisuje plików istniejącego
wydania; pusty katalog po przerwanym pakowaniu może wykorzystać ponownie.
Pliki można załączyć do GitHub Release utworzonego z tego samego
taga. Nie dodawaj klucza podpisywania jako assetu. Samo utworzenie commita i taga
nie publikuje pliku APK.

Wcześniejsze APK debug mają inny certyfikat niż release. Android nie pozwoli
zaktualizować takiej instalacji nowym podpisem: potrzebna jest deinstalacja,
która usuwa jej konfigurację. Skrypty wydania nie odinstalowują aplikacji z telefonu.

Dokumentacja Android: [podpisywanie aplikacji](https://developer.android.com/studio/publish/app-signing),
[apksigner](https://developer.android.com/tools/apksigner),
[zipalign](https://developer.android.com/tools/zipalign).
