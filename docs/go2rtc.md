# RemoteCam + go2rtc — konfiguracja i przykłady

Ten przewodnik opisuje odbiór obrazu z RemoteCam przez go2rtc oraz przekazanie go
do przeglądarki i OBS. Połączenie sprawdzono z **RemoteCam 0.2.8**, telefonem
z **Androidem 16** i **go2rtc 1.9.14**. Aktualne wydania go2rtc są dostępne na poniższej stronie.

- [Pobierz go2rtc — oficjalne wydania](https://github.com/AlexxIT/go2rtc/releases/).
- [Repozytorium i dokumentacja go2rtc](https://github.com/AlexxIT/go2rtc).
- [Repozytorium RemoteCam](https://github.com/Desseres/RemoteCam).
- [Gotowy przykład go2rtc.yaml](../examples/go2rtc.yaml).

## 1. Przygotuj telefon

W RemoteCam wybierz **Format → H.264 + WebRTC**, kamerę, rozdzielczość oraz obrót
i włącz **Stream**. **Preview** steruje wyłącznie lokalnym podglądem: możesz go
wyłączyć bez zatrzymywania transmisji. **Stop** zamyka kamerę i serwer aplikacji.

Telefon i komputer powinny mieć dostęp do siebie w tej samej sieci lokalnej.
W przykładach telefon ma adres `192.168.1.11`, a go2rtc działa na komputerze z OBS.
Zastąp ten adres aktualnym adresem telefonu, widocznym w RemoteCam. Zmiana adresu
telefonu wymaga aktualizacji konfiguracji go2rtc.

## 2. Pobierz i uruchom go2rtc na Windows

Na stronie [wydań go2rtc](https://github.com/AlexxIT/go2rtc/releases/) wybierz
archiwum dla swojej architektury; dla typowego Windows x64 jest to
`go2rtc_win64.zip`. Rozpakuj je do wybranego folderu. Nazwy pakietów opisuje
[instrukcja instalacji autora](https://github.com/AlexxIT/go2rtc/tree/v1.9.14#go2rtc-binary).

Umieść obok `go2rtc.exe` plik `go2rtc.yaml`. Możesz skopiować
[przykład z tego repozytorium](../examples/go2rtc.yaml) i zmienić adres telefonu:

```yaml
streams:
  H6:
    - webrtc:http://192.168.1.11:8080/whep
```

`H6` to dowolna nazwa źródła. Zachowaj jej pisownię w adresach odbiorników.
W YAML stosuj spacje zamiast tabulatorów. Jeżeli plik zawiera już inne kamery,
dodaj `H6` do istniejącej sekcji `streams`, zamiast tworzyć drugą sekcję o tej nazwie.

Otwórz PowerShell w folderze z programem i uruchom:

```powershell
.\go2rtc.exe -c .\go2rtc.yaml
```

Pozostaw program uruchomiony podczas korzystania z obrazu. Po ręcznej zmianie
pliku zatrzymaj tę instancję przez `Ctrl+C` i uruchom ją ponownie tym samym
poleceniem. Parametr `-c` wskazuje konkretny plik konfiguracji — opisuje go
[interfejs programu go2rtc](https://github.com/AlexxIT/go2rtc/blob/v1.9.14/internal/app/app.go).

## 3. Otwórz obraz w przeglądarce

Na komputerze z go2rtc otwórz [panel go2rtc](http://127.0.0.1:1984/).
Powinno pojawić się źródło **H6**. Możesz też użyć poniższych adresów:

| Odbiór | Adres na komputerze z go2rtc |
| --- | --- |
| WebRTC | [Otwórz H6 przez WebRTC](http://127.0.0.1:1984/stream.html?src=H6&mode=webrtc) |
| WebRTC przez TCP — test porównawczy | [Otwórz H6 przez WebRTC/TCP](http://127.0.0.1:1984/stream.html?src=H6&mode=webrtc/tcp) |
| MSE w przeglądarce | [Otwórz H6 przez MSE](http://127.0.0.1:1984/stream.html?src=H6&mode=mse) |
| Strumień MP4 dla kompatybilnego odtwarzacza | `http://127.0.0.1:1984/api/stream.mp4?src=H6` |
| RTSP dla odtwarzacza lub OBS | `rtsp://127.0.0.1:8554/H6` |

Parametry `src` i `mode` należą do
[odtwarzacza go2rtc](https://github.com/AlexxIT/go2rtc/blob/v1.9.14/www/stream.html).
Wyjścia [MP4](https://github.com/AlexxIT/go2rtc/tree/v1.9.14#module-mp4)
i [RTSP](https://github.com/AlexxIT/go2rtc/tree/v1.9.14#module-rtsp) opisuje dokumentacja autora.

`127.0.0.1` oznacza komputer, na którym otwierasz adres. Jeśli odbiornik działa na
innym komputerze, wpisz adres komputera z go2rtc, np. `192.168.1.10`, a nie adres
telefonu. W takim układzie komputer z go2rtc musi przyjmować połączenia z sieci LAN.

## 4. Dodaj źródło w OBS

### Wariant A: Przeglądarka / Browser Source

Dodaj źródło **Przeglądarka**, wyłącz opcję pliku lokalnego i wklej:

```text
http://127.0.0.1:1984/stream.html?src=H6&mode=webrtc
```

Ustaw rozmiar źródła zgodnie z otrzymywanym kadrem: przykładowo **1920 × 1080**
dla poziomego Full HD lub **1080 × 1920** dla pionowego. Włącz własną częstotliwość
renderowania i ustaw **30 FPS**. Dopasuj źródło proporcjonalnie do sceny;
rozciąganie pionowego obrazu do poziomego prostokąta zniekształci proporcje.

Jeśli chcesz utrzymywać połączenie przy przełączaniu scen, pozostaw wyłączone
opcje zamykania źródła, gdy jest niewidoczne, oraz odświeżania po aktywowaniu sceny.
Właściwości tych opcji opisuje [dokumentacja OBS Browser Source](https://obsproject.com/kb/browser-source).

### Wariant B: Źródło multimedialne / Media Source

Dodaj **Źródło multimedialne**, wyłącz **Plik lokalny** i wpisz jako wejście:

```text
rtsp://127.0.0.1:8554/H6
```

Jeśli OBS wymaga wskazania formatu wejścia, wpisz `rtsp`. To przykład alternatywnego
odbioru przez przekaźnik; sprawdzaj jego płynność i opóźnienie w swoim OBS.
W tym projekcie testy integracji go2rtc potwierdziły WebRTC i MP4; ten wariant RTSP
nie był osobno testowany na telefonie z raportu.
Opis źródła znajduje się w [dokumentacji OBS Media Source](https://obsproject.com/kb/media-sources).

RemoteCam przesyła wyłącznie obraz. Mikrofon komputera dodaj jako osobne źródło
dźwięku w OBS. Do oceny synchronizacji nagraj próbę z klaśnięciem; porównuj obraz
i dźwięk w nagraniu. Jeśli dźwięk stale wyprzedza obraz, opóźnij mikrofon o zmierzoną
różnicę w zaawansowanych właściwościach dźwięku.

## 5. Przykład: dwa telefony

Każdy telefon uruchamia własny RemoteCam z włączonym WebRTC i Stream:

```yaml
streams:
  H6:
    - webrtc:http://192.168.1.11:8080/whep
  DrugiTelefon:
    - webrtc:http://192.168.1.12:8080/whep
```

W OBS utwórz dwa źródła przeglądarki, używając odpowiednio:

```text
http://127.0.0.1:1984/stream.html?src=H6&mode=webrtc
http://127.0.0.1:1984/stream.html?src=DrugiTelefon&mode=webrtc
```

Jest to przykład konfiguracji, nie wynik testu dwóch telefonów. Dodanie dwóch
nazw dla jednego adresu telefonu nie uruchomi dwóch różnych kamer tego telefonu:
RemoteCam udostępnia aktualnie wybraną kamerę.

## 6. Który adres do czego służy?

| Adres RemoteCam | Zastosowanie | Wymagany format |
| --- | --- | --- |
| `http://192.168.1.11:8080/webrtc` | Strona odtwarzacza; bezpośrednio do OBS Browser lub przeglądarki, bez go2rtc | H.264 + WebRTC |
| `webrtc:http://192.168.1.11:8080/whep` | Wartość źródła w konfiguracji go2rtc | H.264 + WebRTC |
| `http://192.168.1.11:8080/view` | Odtwarzacz JPEG dla przeglądarki i OBS Browser | JPEG |
| `http://192.168.1.11:8080/cam.mjpeg` | Bezpośredni strumień MJPEG | JPEG |

Prefiks **`webrtc:`** w konfiguracji go2rtc wybiera sposób nawiązania połączenia.
Nie wpisuj go do paska adresu zwykłej przeglądarki. Samo otwarcie `/whep` przez
przeglądarkę wykonuje GET i zwraca **405** — ten punkt oczekuje oferty SDP przez POST.
Obsługiwany sposób wymiany opisuje
[dokumentacja wejścia WebRTC w go2rtc](https://github.com/AlexxIT/go2rtc/blob/v1.9.14/internal/webrtc/README.md#whep).

## 7. Typowe problemy

| Objaw | Przyczyna lub sprawdzenie |
| --- | --- |
| `magic: unsupported header: 3c21646f` | go2rtc dostało HTML, którego początek to `<!do`. Zmień źródło z `/webrtc` na pełne `webrtc:http://ADRES_TELEFONU:8080/whep`. |
| `failed to unmarshal SDP` | go2rtc mogło otrzymać tekst błędu zamiast SDP. Sprawdź wersję RemoteCam, dokładny adres, format H.264 + WebRTC oraz włączony Stream. Nie jest to samo w sobie dowodem błędu kodeka. |
| 404 dla `/whep` | Upewnij się, że łączysz się z telefonem na porcie 8080 i korzystasz z RemoteCam 0.2.8 lub nowszego wydania zawierającego tę funkcję. |
| 503 przy nawiązywaniu połączenia | WebRTC może być wyłączone, kamera niedostępna albo limit odbiorników osiągnięty. Sprawdź komunikat odpowiedzi i stan aplikacji; zamknij zbędne odbiorniki. |
| Panel go2rtc nie otwiera się | Sprawdź, czy program działa i nasłuchuje na porcie 1984. Na drugim komputerze użyj adresu komputera z go2rtc zamiast `127.0.0.1`. |
| Nie ma źródła H6 | Sprawdź plik wskazany przez `-c`, wcięcia YAML i pisownię nazwy. Po ręcznej zmianie konfiguracji uruchom go2rtc ponownie. |
| Odbiornik dołączający później czeka na obraz | Zaktualizuj RemoteCam do wersji z regularnymi klatkami kluczowymi (0.2.8). Odśwież odbiornik i sprawdź, czy źródło nadal odbiera dane. |
| Obraz ma złe proporcje lub obrót | Ustaw obrót na telefonie, sprawdź rozmiar otrzymanego kadru i usuń dodatkowe rozciąganie lub obrót w OBS. |
| Brak dźwięku z telefonu | To oczekiwane: ta wersja RemoteCam wysyła tylko wideo. |

Do sprawdzenia stanu bez zmieniania konfiguracji można użyć PowerShell:

```powershell
Invoke-RestMethod 'http://127.0.0.1:1984/api'
Invoke-RestMethod 'http://127.0.0.1:1984/api/streams' | ConvertTo-Json -Depth 12
```

Przy aktywnym odbiorniku szukaj źródła `H6`, kodeka `H264` i rosnących liczników
`bytes_recv` lub `bytes` w kolejnych odczytach. Bez odbiornika źródło może pozostawać
nieaktywne. Wzrost liczników potwierdza przepływ danych, ale nie mierzy opóźnienia
obrazu na ekranie.

### Ścinki i artefakty tylko przez go2rtc

W go2rtc 1.9.14 potwierdziliśmy brak odczytu RTCP nadajników, przez co nie działała
retransmisja NACK. Przy odbiorze w Firefoxie powodowało to zatrzymania do około
1,9 sekundy po utracie pakietów. Repozytorium zawiera
[lokalną poprawkę go2rtc, test regresji i instrukcję kompilacji](../patches/README.md).
Aktualizacja samego APK nie naprawia pliku `go2rtc.exe`. Poprawka nie obniża
rozdzielczości ani bitrate i nie jest oficjalnym wydaniem go2rtc.

Porównuj odbiorniki pojedynczo, z tymi samymi ustawieniami telefonu i w tej samej
przeglądarce. Sam licznik 30 FPS nie wyklucza uszkodzeń obrazu ani przerw w jego
wyświetlaniu.

1. Sprawdź bezpośredni [podgląd RemoteCam ze statystykami](http://192.168.1.11:8080/webrtc?stats=1).
2. Sprawdź jawnie wybrany [WebRTC w go2rtc](http://127.0.0.1:1984/stream.html?src=H6&mode=webrtc).
   Adres bez `mode` może automatycznie wybrać sposób odtwarzania.
3. Porównaj [WebRTC/TCP](http://127.0.0.1:1984/stream.html?src=H6&mode=webrtc/tcp).
   Zmienia to transport **go2rtc → przeglądarka**, a nie połączenie telefonu z go2rtc.
   Jeśli TCP również się zacina, nie traktuj go jako naprawy.
4. Porównaj [MSE](http://127.0.0.1:1984/stream.html?src=H6&mode=mse).
   To ten sam zakodowany H.264, lecz inna ścieżka odtwarzania. Oceń również
   opóźnienie i proporcje obrazu, zanim wybierzesz ten wariant do OBS.

Tryby i wybór kandydatów TCP opisuje
[kod odtwarzacza go2rtc 1.9.14](https://github.com/AlexxIT/go2rtc/blob/v1.9.14/www/video-rtc.js).
MSE w tej wersji utrzymuje zapas danych rzędu sekundy; jego płynność nie oznacza
takiego samego opóźnienia jak w WebRTC. Przy źródłach WebRTC bez SPS/PPS w SDP
go2rtc może też utworzyć początkowy nagłówek MP4 z zastępczymi parametrami obrazu
([implementacja MP4](https://github.com/AlexxIT/go2rtc/blob/v1.9.14/pkg/mp4/muxer.go)).
W lokalnym teście tego źródła MSE raportowało 1920 × 1440 mimo obrazu 1920 × 1080;
dlatego nie należy uznawać go za bezwarunkowy zamiennik WebRTC.

W diagnostyce WebRTC porównuj przyrosty `packetsLost`, `nackCount`, `pliCount`,
`framesDecoded`, `framesDropped` i — jeśli przeglądarka je udostępnia —
`freezeCount` oraz `totalFreezesDuration`. Straty po stronie odbiornika go2rtc
nie muszą pojawić się w statystykach drugiego połączenia do przeglądarki.
Nie zwiększaj bufora ani nie zmniejszaj jakości wyłącznie na podstawie wartości
Mb/s: najpierw ustal, na którym odcinku pojawia się problem.

## 8. Jakość, opóźnienie i zakres obsługi

Przykładowe połączenie przesyła H.264 z telefonu do go2rtc, które przekazuje go dalej
bez dodatkowego kodowania wideo. Nie wymaga konfiguracji FFmpeg. Przekaźnik i sam
odbiornik nadal mogą wprowadzać opóźnienie — porównaj wynik z bezpośrednim `/webrtc`.

Limit bitrate w RemoteCam to pułap, nie gwarantowana stała przepływność. Klatki
kluczowe są żądane co około 2 sekundy, aby nowy odbiornik mógł rozpocząć dekodowanie.
Nie oznacza to dodania 2 sekund bufora do już odtwarzanego obrazu. Większy bufor
nie usuwa trwałego niedoboru przepustowości i zwiększa opóźnienie.

RemoteCam obsługuje pełne oferty i odpowiedzi SDP z kandydatami ICE oraz usuwanie
sesji przez DELETE. Ten punkt HTTP nie implementuje trickle ICE/PATCH ani restartów
ICE; w razie utraty połączenia odbiornik powinien utworzyć nową sesję. Limit
bezpośrednich połączeń WebRTC z telefonem wynosi cztery, a możliwości sprzętowego
enkodera mogą ograniczyć go wcześniej. Odbiorniki jednego źródła H6 w go2rtc mogą
współdzielić jego połączenie z telefonem.

Instrukcja dotyczy zaufanej sieci lokalnej. Sygnalizacja RemoteCam nie wymaga hasła;
nie wystawiaj jej portu 8080 ani panelu go2rtc bezpośrednio do Internetu.

Szczegóły wykonanych testów znajdziesz w [raporcie modernizacji](modernization.md).

[Powrót do README](../README.md)
