# RemoteCam — pakiet publikacji Google Play

Stan przygotowania: 11 września 2026. Wydawca: **Desseres**. Kontakt: **apps@kasztelan.me**.
To instrukcja przygotowania zgłoszenia, a nie potwierdzenie zatwierdzenia przez Google.
Właściciel wskazał pakiet `pl.remotecam.app` i potwierdził dodanie odcisku certyfikatu.
Nie wysłano wydania do Play Console. Strona i polityka prywatności zostały opublikowane i sprawdzone 2026-09-12.

## 1. Dane do karty sklepowej

| Pole | Wartość |
|---|---|
| Nazwa | RemoteCam |
| Domyślny język materiałów | Polski, pl-PL |
| Typ | Aplikacja |
| Kategoria | Odtwarzacze i edytory wideo / Video Players & Editors — sprawdź dostępny odpowiednik w konsoli |
| Model | Bezpłatna, bez reklam, zakupów i subskrypcji |
| Wydawca | Desseres |
| E-mail pomocy / prywatności | apps@kasztelan.me |
| Witryna projektu | https://github.com/Desseres/RemoteCam |
| Krótki i pełny opis | [listing-pl.txt](listing-pl.txt), gotowe do skopiowania |
| Ikona sklepu | [graphics/play-icon-512.png](graphics/play-icon-512.png), 512 × 512, PNG RGBA |
| Grafika wyróżniająca | [graphics/feature-graphic-1024x500.png](graphics/feature-graphic-1024x500.png), 1024 × 500, PNG RGB |
| Telefon — galeria | 6 grafik `graphics/01-…06-*.png`, 1080 × 1920; dodatkowo 2 rzeczywiste zrzuty `graphics/screenshot-*.png` |
| Film promocyjny | Opcjonalny, nie przygotowano linku YouTube |
| Publiczna polityka prywatności | `https://remotecam.kasztelan.me/privacy.php` — opublikowana i sprawdzona 2026-09-12; źródła oraz skrypt: [docs/website.md](../../docs/website.md) |

Limity tekstu: tytuł 30 znaków, krótki opis 80, pełny opis 4000. Materiały nie obiecują
zerowego opóźnienia ani funkcji nieobsługiwanych przez aparat. Interfejs aplikacji jest obecnie
angielski; polskie materiały nie oznaczają polskiej lokalizacji UI. [Zasady karty aplikacji](https://support.google.com/googleplay/android-developer/answer/9859152),
[wymagania grafik](https://support.google.com/googleplay/android-developer/answer/9866151).

## 2. Tożsamość aplikacji i plik do wysłania

- Wersja przygotowywana w tej gałęzi: **0.3.1**, `versionCode=16`.
- `applicationId` nowej linii APK/AAB: **pl.remotecam.app**.
  Namespace klas źródłowych pozostaje `com.samsung.android.scan3d`; nie jest identyfikatorem instalacji.
- Minimum Android 9 / API 28; compile/target API 37. Test fizyczny: Android 16.
- Dla nowej aplikacji w Play użyj **podpisanego AAB**, nie APK z GitHub Release.
- Skrypt `tools/package-play-windows.ps1` buduje i podpisuje AAB oraz testowy release APK
  w `dist/google-play/pl.remotecam.app/0.3.1/`. Manifest kompilacji zapisuje również stan Git i SHA-256.
- AAB należy najpierw sprawdzić na ścieżce testów wewnętrznych i w raporcie przedpremierowym.
  Nie został jeszcze przesłany do Play. [Format AAB](https://support.google.com/googleplay/android-developer/answer/9844279).

**Wybrany identyfikator: `pl.remotecam.app`.** Użyj nowych artefaktów z katalogu powyżej.
Poprzednie APK/AAB z `com.samsung.android.scan3d`, w tym wcześniejszy ZIP przygotowawczy,
nie odpowiadają temu pakietowi. Zmiana oznacza osobną instalację i nie przeniesie ustawień
poprzedniej aplikacji. Wersję 0.3.1 / code 16 zachowano, ponieważ jest to pierwsza kompilacja
pod nowym ID. Nazwa pakietu po publikacji jest trwałą tożsamością aplikacji.
[Przygotowanie aplikacji w konsoli](https://support.google.com/googleplay/android-developer/answer/9859152).

### Podpisywanie

Dedykowany lokalny klucz wydania został już utworzony; nie dołączaj go do żadnego assetu.
Instrukcja kopii zapasowej: [docs/releases.md](../../docs/releases.md).
Przy pierwszym wydaniu wybierz konfigurację **Play App Signing**. Jeśli instalacje z GitHuba
i Play mają się wzajemnie aktualizować, potrzebują tego samego ID i zgodnego podpisu:
możesz przekazać istniejący klucz podpisywania do Play oficjalną procedurą PEPK lub używać
APK podpisanych przez Play również poza sklepem. Samo podpisanie uploadu naszym kluczem
nie sprawia, że wygenerowane przez Play APK będą miały ten sam certyfikat.
Zalecane jest oddzielenie klucza uploadu od klucza podpisywania aplikacji; konfigurację
wykonuje właściciel konta. [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756).

## 3. App content — odpowiedzi i uzasadnienia

| Formularz | Przygotowana odpowiedź |
|---|---|
| Reklamy | Nie — brak reklam i SDK reklamowych |
| App access | Wszystkie funkcje dostępne bez konta i logowania; potrzebny telefon z kamerą i odbiornik w LAN |
| Tworzenie konta | Nie; nie ma kont ani funkcji ich usuwania |
| Zakupy | Nie |
| News | Nie |
| Health | Brak funkcji zdrowotnych/medycznych |
| Financial features | Brak funkcji finansowych |
| Government | Nie jest aplikacją rządową |
| Uprawnienia wysokiego ryzyka SMS/połączeń/lokalizacji | Nie występują |
| Foreground service | `camera`, przypadek użycia Background Camera Streaming — tekst niżej |
| Target audience | Narzędzie dla twórców i użytkowników komputerów; właściciel musi wskazać faktyczne grupy wiekowe, nie deklarować kierowania do dzieci bez oceny Families |
| Content rating / IARC | Wypełnij kwestionariusz: brak dostarczanych treści seksualnych, przemocy, hazardu, zakupów, reklam i społecznościowego feedu. Obraz pochodzi z aparatu użytkownika i jest przesyłany do jego odbiornika; uwzględnij to, jeśli pytanie dotyczy udostępniania treści. Nie wpisuj z góry PEGI 3 — ocenę wylicza IARC |

Konsola może pokazać dodatkowe pytania zależne od konta i krajów. To nie jest gotowy eksport
odpowiedzi z konkretnej sesji Play Console. [Przygotowanie do oceny](https://support.google.com/googleplay/android-developer/answer/9859455).

### Instrukcja dla recenzenta — do wklejenia (EN)

> No account, login, subscription or payment is required. Use a physical Android 9+
> phone with a camera and a computer on the same trusted private network. Open
> RemoteCam and allow camera, notifications and local-network access when requested.
> Select JPEG, enable Stream and open the phone's displayed http://PHONE_IP:8080/view
> address on the computer. For H.264 select H.264 + WebRTC and open /webrtc; this mode
> requires a compatible hardware encoder. Hide Preview to see all controls. Use
> Latency & bandwidth for the built-in guide and Info → Privacy policy for privacy
> details. Press Home to verify continued camera streaming; return through the
> foreground notification. Use Stop in the app or notification to end the service.
> Stream is restored if previously enabled when reopening the app. No audio is sent.
> Network isolation/firewall rules can prevent a receiver from connecting. These are
> local phone URLs, not public demonstration servers. Contact: apps@kasztelan.me.

### Deklaracja foreground service — do wklejenia (EN)

> RemoteCam's core purpose is user-controlled live camera streaming to a receiver
> on the local network. The camera foreground service keeps the capture session
> running when the user switches apps or hides the local preview, avoiding interruption
> of the ongoing broadcast. The service is started from the visible activity after
> permission is granted. A persistent notification provides controls to reopen the
> app and stop the service. Stopping the app through these controls releases the
> camera and closes the server. The service is not silently restarted after process
> death. This work cannot be deferred because it delivers a live video feed.

**Do nagrania:** krótki film na rzeczywistym urządzeniu: otwarcie aplikacji → Stream →
działający odbiornik → Home → powiadomienie usługi → ciągły obraz w odbiorniku → Stop.
Nie podawaj nieistniejącego linku; dodaj dostępny recenzentom film przed zgłoszeniem.
[Wymagania FGS, w tym film](https://support.google.com/googleplay/android-developer/answer/13392821).

## 4. Bezpieczeństwo danych — analiza kodu i proponowane deklaracje

Nie zaznaczaj automatycznie „brak zbierania danych” tylko dlatego, że nie ma backendu.
Definicja Google obejmuje transmisję poza urządzenie; trzeba uwzględnić wszystkie tryby.

| Dane / pytanie | Zachowanie kodu i propozycja |
|---|---|
| Photos and videos → Videos | Obraz kamery opuszcza telefon: deklaruj zbieranie, cel App functionality. W JPEG rozważ również Photos, ponieważ wysyłane są pojedyncze obrazy; formularz i rzeczywiste użycie muszą być spójne |
| Wymagane czy opcjonalne | Wymagane dla podstawowej funkcji transmisji obrazu, mimo że użytkownik może wyłączyć Stream |
| Przetwarzanie efemeryczne | Aplikacja trzyma klatki w RAM; odbiornik może je nagrywać. Nie deklaruj bezwarunkowo efemeryczności całego przepływu. Konserwatywnie „nie”, chyba że uzasadnisz zakres deklaracji i zachowanie odbiorników |
| Udostępnianie | Odbiornik wybiera użytkownik. Można zastosować wyjątek user-initiated transfer, jeśli użytkownik świadomie uruchamia odbiór. Nie oznacza to braku transmisji danych |
| Szyfrowanie podczas transmisji | **Nie dla całej aplikacji**: JPEG/MJPEG i HTTP/WS signaling nie są szyfrowane; WebRTC media używa DTLS-SRTP |
| Usuwanie danych | Brak serwera wydawcy z nagraniami; usuń ustawienia przez pamięć aplikacji, nagrania w odbiorniku. Brak kont. E-mail wsparcia nie jest automatycznym systemem usuwania danych |
| Dane osobowe, lokalizacja, audio, kontakty | Brak osobnego odczytu/wysyłki tych kategorii przez aplikację; zawartość kadru może zawierać osoby lub informacje |
| Identyfikatory | Brak Advertising ID ani identyfikatorów reklamowych. Lokalne adresy IP, ID sesji i statystyki służą ustanowieniu połączenia, nie profilowaniu; rozstrzygnij kategorię Device or other IDs zgodnie z opisem formularza |
| Analityka i diagnostyka | Brak zdalnej analityki i automatycznego raportowania awarii. Statystyki transmisji są lokalne; logi techniczne mogą istnieć w systemie Android |

To propozycja oparta na aktualnym kodzie, wymagająca zatwierdzenia przez wydawcę przed
wysłaniem. Dokumentacja Google rozróżnia zbieranie, udostępnianie, przetwarzanie efemeryczne
i wyjątek transferu inicjowanego przez użytkownika. [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469).

**Kwestia do rozwiązania przed publikacją:** polityka User Data wymaga bezpiecznej transmisji
danych wrażliwych z użyciem nowoczesnej kryptografii. Obecny HTTP oraz brak uwierzytelniania
odbiornika wymagają oceny i prawdopodobnie osobnej konfiguracji bezpiecznej wersji Play
(szyfrowana sygnalizacja/transport i autoryzacja). Dodatkowo oceń potrzebę osobnej informacji
i aktywnej zgody przed pierwszym uruchomieniem kamery w tle: polityka dostępna w Info
nie zastępuje prominent disclosure w normalnym przepływie. Sam opis „zaufana sieć” ani deklaracja
„nie” przy szyfrowaniu nie gwarantują zgodności. Zachowano dotychczasowe tryby, zgodnie
z zakresem aplikacji; nie przebudowano tutaj protokołów. [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

## 5. Polityka prywatności

Treść angielska jest dostępna offline w aplikacji: **Info → Privacy policy**.
Ten sam tekst jest w [privacy-policy.html](privacy-policy.html), gotowym do hostowania
pod publicznym adresem HTTPS (bez logowania, blokady regionu i formatu PDF).
Wymienia wydawcę i e-mail, obraz, ustawienia, uprawnienia, zachowanie w tle, retencję,
zewnętrzne odbiorniki i ograniczenia szyfrowania. Przed publikacją właściciel musi
potwierdzić praktyki obsługi korespondencji i zgodność danych wydawcy z kontem.
Wklej działający adres do pola Privacy policy w Play Console; tekst w repozytorium
lub plik lokalny nie jest potwierdzeniem działającego hostingu.
[Wymagania polityki](https://support.google.com/googleplay/android-developer/answer/9859455).

## 6. Konto, kraje, testy i ostatnie kroki

1. Zweryfikuj konto deweloperskie i dane kontaktowe; przygotuj wymagane przez konsolę
   dane prawne/adres/telefon oraz D-U-N-S, jeśli dotyczy konta organizacji.
   „Desseres” to nazwa publiczna, nie zastępuje tożsamości prawnej.
2. Wybierz kraje dystrybucji, status przedsiębiorcy w EOG i rzeczywistą grupę docelową.
   Nie przyjęto tych deklaracji za właściciela.
3. Użyj `pl.remotecam.app`; rozstrzygnij transport danych oraz Play App Signing.
4. Wpisz opublikowany URL polityki `https://remotecam.kasztelan.me/privacy.php`; wgraj teksty i grafiki.
5. Prześlij AAB do testów wewnętrznych; przejrzyj pre-launch report i wynik kontroli
   natywnych bibliotek / stron pamięci 16 KB. Sprawdź start, zgodę na kamerę, odmowę
   uprawnień, tło, Stop, wszystkie formaty, orientację i odbiór w LAN.
6. Uzupełnij App content, Data safety i rating; nagraj film dla FGS.
7. Dla osobistego konta utworzonego po 13.11.2023 obecne zasady wymagają zamkniętego
   testu z minimum **12 testerami przez 14 kolejnych dni** przed wnioskiem o produkcję.
   Właściciel musi ustalić, czy warunek dotyczy jego konta.
8. Dopiero po powyższym wyślij wydanie do weryfikacji; nie zakładaj automatycznej akceptacji.

[Weryfikacja kont](https://support.google.com/googleplay/android-developer/answer/13628312),
[testy nowych kont](https://support.google.com/googleplay/android-developer/answer/14151465),
[16 KB](https://developer.android.com/guide/practices/page-sizes).

## 7. Materiały wizualne i opisy alternatywne

| Plik z graphics/ | Opis alternatywny |
|---|---|
| 01-camera.png | Ustawienia kamery RemoteCam: format H.264, rozdzielczość, zoom i obrót obrazu. |
| 02-focus.png | Wybór autofokusa, blokady ostrości i ręcznej ostrości w RemoteCam. |
| 03-receivers.png | Adresy połączeń RemoteCam dla OBS Browser, MJPEG i go2rtc. |
| 04-guide.png | Wbudowany poradnik formatów, przepustowości i konfiguracji OBS. |
| 05-how-it-works.png | Trzy kroki: wspólna sieć, włączenie Stream i otwarcie adresu odbiornika. |
| 06-formats.png | Porównanie H.264 z WebRTC, JPEG Browser i MJPEG. |
| feature-graphic-1024x500.png | RemoteCam: kamera telefonu przesyłająca obraz na komputer. |
| screenshot-1-1080x1920.png | Pełny ekran konfiguracji kamery bez lokalnego podglądu. |
| screenshot-2-1080x1920.png | Pełny ekran adresów odbiorników i statystyk transmisji. |

Do galerii telefonu można wgrać najwyżej 8 obrazów: przykładowo 2 czyste zrzuty i 6 grafik.
Oryginały w `screenshots/` mają proporcje telefonu 1280 × 2800 i przekraczają limit 2:1;
**nie wysyłaj ich bezpośrednio**. Przygotowane pliki w `graphics/` mają 1080 × 1920
bez przezroczystości. Pełny interfejs zachowano bez rozciągania i bez podmiany treści.
Pierwsze cztery grafiki zawierają rzeczywiste UI, dwie ostatnie są objaśnieniami.
Nie przedstawiają nowego interfejsu ani gwarantowanych parametrów transmisji.
Google preferuje dominujący rzeczywisty interfejs w screenshotach, więc czyste zrzuty
warto umieścić na początku. Materiały są dla telefonów; nie udają zrzutów tabletowych.
[Specyfikacja](https://support.google.com/googleplay/android-developer/answer/9866151).

Źródła SVG, generator i prompt ilustracji pozwalają odtworzyć grafiki. Zrzuty wykonano
na Honor BVL-N49 z Androidem 16; lokalny adres IP jest adresem przykładowej instalacji,
nie publicznym punktem dostępu. Zrzuty ustawień pochodzą z testowego 0.2.8 — te ekrany zachowano
w 0.3.1. Nowy ekran startowy i politykę sprawdzono osobno w aktualnym buildzie.
