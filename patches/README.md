# Lokalna poprawka go2rtc 1.9.14: retransmisja WebRTC

## Odbiornik Windows 0.1.3

[go2rtc-1.9.14-video-repair.patch](go2rtc-1.9.14-video-repair.patch) to zbiorcza
poprawka dla tej samej rewizji upstream. Zawiera poniższą naprawę wyjściowego NACK
oraz porządkowanie pakietów H.264/H.265, oczekiwanie na retransmisję do 150 ms
i odzyskiwanie klatki kluczowej po stracie danych. Wykrywa również utratę fragmentów
w lokalnej kolejce RTSP i zamyka taką sesję, aby odbiornik ją odnowił.
Nie należy nakładać obu patchy
jednocześnie; skrypt `windows/prepare-dependencies.ps1` obsługuje migrację.

Nowa ścieżka wejściowa jest aktywna tylko z `REMOTECAM_RTP_REPAIR=1`, ustawianym
przez RemoteCam Desktop dla jego procesu go2rtc. Nie zmienia osobnej instalacji OBS.
Opis testów i ograniczeń: [Windows validation](../windows/VALIDATION.md).

## Dotychczasowa poprawka wyjściowego NACK

[go2rtc-1.9.14-nack.patch](go2rtc-1.9.14-nack.patch) dotyczy **go2rtc**, a nie APK
RemoteCam. To lokalna poprawka do oficjalnego tagu `v1.9.14`, commit
`b5948cfb25404cc5cb37b166ecaa2dca20b11d4b`. Nie jest oficjalnym wydaniem autora go2rtc.
Oryginalny projekt i poprawka pozostają na licencji MIT.

Poprawkę i test zgłoszono do oficjalnego repozytorium jako
[PR #2486 — webrtc: read RTCP feedback to enable NACK retransmissions](https://github.com/AlexxIT/go2rtc/pull/2486).
Aktualny stan przeglądu i ewentualnego włączenia zmiany można sprawdzić pod tym linkiem.

## Problem i zmiana

W tej wersji go2rtc rejestruje mechanizm NACK w Pion, ale nie odczytuje RTCP
z nadajników. Żądania ponownego wysłania utraconych pakietów nie docierają więc do
mechanizmu retransmisji. Przy utracie części klatki H.264 odbiornik może czekać na
następną klatkę kluczową. Pion opisuje wymagany odczyt w swoim
[przykładzie reflect](https://github.com/pion/webrtc/blob/v4.2.3/examples/reflect/main.go).

Poprawka uruchamia odczyt RTCP dla nadajników po połączeniu. `sync.Once` chroni
przed uruchomieniem kilku czytników po powtórzonym zdarzeniu połączenia; zamknięcie
nadajnika kończy odczyt. Standardowy mechanizm Pion odsyła żądany pakiet z pamięci.
Nie zmienia to kodeka, rozdzielczości, limitu bitrate ani bufora odtwarzania.

To naprawa retransmisji na wyjściu **go2rtc → odbiornik**. Nie rozwiązuje każdego
problemu sieciowego ani błędów składania klatek na wejściu do przekaźnika. Nie
dodaje również przekazywania żądań PLI/FIR do telefonu. RemoteCam nadal okresowo
generuje klatki kluczowe, potrzebne m.in. odbiornikom MP4/MSE.

## Odtworzenie kompilacji na Windows

Potrzebne są Git i Go obsługujący wymagania `go.mod` go2rtc. Lokalny build wykonano
Go **1.27.1** dla Windows amd64; zależności pochodzą z niezmienionych `go.mod` i
`go.sum` tagu `v1.9.14`.

W PowerShell, w katalogu RemoteCam:

```powershell
$patchPath = Join-Path $PWD 'patches\go2rtc-1.9.14-nack.patch'
New-Item -ItemType Directory -Force build | Out-Null
git clone --depth 1 --branch v1.9.14 https://github.com/AlexxIT/go2rtc.git build/go2rtc-local
Set-Location build/go2rtc-local
git apply --check $patchPath
git apply $patchPath
go test ./pkg/webrtc -count=1 -v
go build -trimpath -ldflags '-s -w' -o go2rtc-remotecam.exe .
```

Nie kontynuuj po błędzie któregokolwiek polecenia. Wersja kompilowana ze zmienionego
drzewa jest raportowana jako `1.9.14+dev.b5948cf.dirty`. Zachowaj oryginalny plik
wykonywalny przed zastąpieniem go lokalną kompilacją i zatrzymaj program przed
podmianą. Plik `go2rtc.yaml` oraz adres źródła H6 pozostają takie same.

## Test regresji

Patch zawiera `TestConnectionRetransmitsNack`: dwie lokalne sesje WebRTC wymieniają
RTP, odbiornik żąda powtórzenia konkretnego pakietu, a test sprawdza jego numer,
znacznik czasu i zawartość. Przed poprawką test kończy się brakiem retransmisji;
po poprawce przechodzi, razem z pozostałymi testami `pkg/webrtc`.

Wyłącznie odbiornik w tym teście dopuszcza duplikat SRTP, żeby deterministycznie
sprawdzić retransmisję bez losowego gubienia pakietów. Ustawienia ochrony przed
powtórzeniami w działającym go2rtc pozostają bez zmian.

## Sprawdzenie na telefonie i w Firefoxie

11 września 2026 porównano to samo źródło H6: RemoteCam 0.2.8, Honor BVL_N49
z Androidem 16, 1920 × 1080, około 30 FPS i niezmieniony limit 40 Mb/s.
Przed poprawką Firefox 155 zarejestrował 40 zatrzymań o łącznym czasie 65,3 sekundy
w ciągu 276 sekund pomiaru. Początkowe zdarzenia potwierdzały związek między
utratą pakietów, żądaniami NACK i czekaniem na klatkę kluczową.

Po poprawce, w nowym połączeniu, licznik `freezeCount` pozostał równy zero przez
około trzy minuty, mimo ponad 2700 żądań NACK. Dekodowanie utrzymywało około
30 FPS. Część tego pomiaru odbyła się z kartą diagnostyczną w tle; nie należy
utożsamiać go z ciągłym pomiarem renderowania aktywnego okna. Użytkownik osobno
potwierdził płynny obraz w zwykłym podglądzie go2rtc. Nie mierzono całkowitego
opóźnienia kamera–OBS ani synchronizacji z mikrofonem.

W lokalnej instalacji zachowano oryginał jako `go2rtc.official-1.9.14.exe`.
Powrót do oryginału wymaga zatrzymania programu i przywrócenia tego pliku pod
nazwą `go2rtc.exe`; konfiguracja źródła H6 nie została zmieniona.

[Konfiguracja RemoteCam + go2rtc](../docs/go2rtc.md)
