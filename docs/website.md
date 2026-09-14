# Strona RemoteCam i aktualizacja hostingu

Aktualizacja 2026-09-14 dodaje pobieranie **RemoteCam Desktop 0.1.10-test**
z [GitHub Releases](https://github.com/Desseres/RemoteCam/releases/tag/windows-v0.1.10).
Przyciski APK/EXE mają ikony Androida i Windowsa. Sekcja pobierania rozdziela oba
pakiety i ich sumy SHA-256; instrukcja Desktop opisuje połączenie, OBS, tray i audio.
Android na stronie pozostaje w opublikowanej wersji **0.3.2**, zgodnej z H.264.
Strona PL/EN została sprawdzona lokalnie przy 320, 390 i 1440 px, w tym galeria,
brak poziomego przepełnienia i zgodność sumy pobieranego APK.
Po publikacji przez FTPS powtórzono te kontrole na publicznej stronie; polityka
prywatności zwróciła HTTP 200 z nagłówkiem CSP. Pobieranie EXE z GitHuba bez
logowania przeszło weryfikację SHA-256 względem pliku dołączonego do wydania.

Strona została opublikowana i sprawdzona 2026-09-12 pod **https://remotecam.kasztelan.me/**.
Aktualny pakiet na stronie: **0.3.2 (17)**, podpisany dotychczasowym kluczem wydania.
Aktualizacja obejmuje opisy audio PL/EN, instrukcję mikrofonu w OBS/go2rtc, historię
wersji i politykę prywatności zgodną z aplikacją. Pobieranie APK i SHA-256 sprawdzono
po publikacji; stare pliki APK pozostały na serwerze.
Układ pracy nawiązuje do projektu Image Viewer / TraceLens: PHP, CSS i JS,
dwa języki, galeria, `versions.json`, lokalny `.env.deploy` i publikacja przez curl/FTP.
Publikacja używa osobnego konta FTP RemoteCam i jego katalogu `/public_html/`.
Host FTPS `s32.cyber-folks.pl` odpowiada certyfikatowi serwera. Dane logowania pozostają
wyłącznie w ignorowanym przez Git pliku `.env.deploy`.

## Pliki

- `website/index.php`: opis, funkcje, instrukcja OBS/go2rtc, galeria, wersje, pobieranie APK.
- `website/privacy.php`: publiczna polityka prywatności, identyczna z tekstem w aplikacji.
- `website/config.php`: docelowy URL, repozytorium, kontakt, wersja/linki wydania Windows i opcjonalny link do Google Play.
- `website/translations.php`: komplet tekstów PL/EN; język wybiera parametr `?lang=pl/en`.
- `website/versions.json`: historia wersji, najnowsza na początku.
- `website/assets/`: ilustracja, ikona, baner społecznościowy i prawdziwe zrzuty aplikacji.
- `scripts/publish-site.ps1`: przygotowanie katalogu `build/website-preview` i opcjonalny upload.

Wymagania: PHP 8.1+ na hostingu, PowerShell 7 i curl.exe na komputerze. Hosting musi
obsługiwać explicit FTPS z poprawnym certyfikatem. Nie wyłączaj sprawdzania certyfikatów.
`.htaccess` zachowuje handler PHP 8.4 zastany w katalogu RemoteCam. Przy zmianie hostingu
dostosuj go do nowego serwera. Serwer inny niż Apache wymaga odpowiednika DirectoryIndex i MIME dla APK.

## Przygotowanie i podgląd

```powershell
pwsh -File scripts/publish-site.ps1
php -S 127.0.0.1:8092 -t build/website-preview
```

Otwórz `http://127.0.0.1:8092/`. Domyślne uruchomienie **nie wysyła nic do sieci**.
Skrypt wybiera opublikowaną wersję Androida z pierwszego wpisu `website/versions.json`
(nie z rozwojowej wersji Gradle), po czym sprawdza metadane i SHA-256 podpisanego APK z
`dist/google-play/pl.remotecam.app/<wersja>/`, kopiuje je do `downloads/` i tworzy sumę
kontrolną. Nie przebudowuje aplikacji i nie przenosi kluczy podpisywania do strony.
Opcjonalnie wskaż `-ApkPath`, z `build-info.json` i `SHA256SUMS.txt` obok APK.

Sam katalog źródłowy `website/` nie zawiera APK; gdy pliku brakuje, strona proponuje kod
źródłowy zamiast niedziałającego pobrania. Do publikacji używaj skryptu i przygotowanego katalogu.

## Publikacja

1. W panelu hostingu przypisz domenę do **osobnego katalogu RemoteCam** i włącz HTTPS/PHP.
2. Skopiuj `.env.deploy.example` do `.env.deploy`. Wpisz host, port, użytkownika, hasło
   i prawidłowy absolutny katalog FTP. Nie używaj katalogu strony TraceLens.
3. Sprawdź podgląd PL/EN, politykę, galerię i pobieranie pliku.
4. Uruchom świadomie:

```powershell
pwsh -File scripts/publish-site.ps1 -Upload
```

Hasło trafia do curl przez stdin. Nie jest argumentem procesu ani elementem katalogu
publikacyjnego. Skrypt wysyła wyłącznie listę publicznych plików, APK i checksumę; nie
wysyła repozytorium, AAB, `.env` ani keystore. Najpierw wysyła zasoby i APK, później dane
wersji i punkt wejścia. Nie usuwa niczego na serwerze. To nie jest atomowa podmiana całej
strony: po przerwaniu sprawdź błąd i uruchom ponownie. Stare APK pozostają na hostingu.

Po publikacji sprawdź:

- `https://remotecam.kasztelan.me/` i `/?lang=en`;
- `https://remotecam.kasztelan.me/privacy.php`;
- pobranie APK i zgodność z `.sha256`;
- poprawny certyfikat HTTPS oraz konfigurację PHP (serwer nie może zwracać kodu PHP jako tekstu).

Po sprawdzeniu działającego adresu można wpisać `/privacy.php` do Play Console.
Link do Play w `config.php` pozostaw pusty, dopóki karta `pl.remotecam.app` nie zostanie
opublikowana. Witryna obecnie informuje o przygotowaniach do publikacji.

## Kolejna wersja

1. Zbuduj i podpisz aplikację przez `tools/package-play-windows.ps1`.
2. Dodaj opis PL/EN i datę nowego wydania na początku `website/versions.json`.
3. W razie potrzeby zaktualizuj zrzuty i teksty. Tekst polityki jest podczas przygotowania
   kopiowany z zasobu aplikacji; `website/privacy-policy.txt` służy podglądowi źródeł.
4. Ponów lokalny podgląd, a następnie publikację. CSS i JS mają automatyczne parametry
   wersji na podstawie hasha zawartości, więc aktualizacja odświeża cache odbiorców.

Dla nowej wersji Windows najpierw opublikuj EXE, jego plik `.sha256` oraz
`build-info.json` w wydaniu testowym GitHub. Tag musi wskazywać commit z metadanych
zbudowanej aplikacji. Po sprawdzeniu załączników zaktualizuj `windows_version`,
`windows_download` i `windows_release` w `website/config.php`, a następnie opublikuj
stronę. Linki są przypięte do wydania — niezależnie od numeracji APK i EXE.

Strona nie używa analityki, fontów z CDN ani cookies. Techniczne logi serwera zależą
od operatora hostingu. Polityka aplikacji uczciwie opisuje HTTP w trybach JPEG i sygnalizacji;
publikacja tej strony sama nie rozwiązuje kwestii bezpieczeństwa transportu aplikacji.
