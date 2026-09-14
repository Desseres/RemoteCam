# H.265 + WebRTC — tryb eksperymentalny

Publiczna wersja testowa **0.3.3** (`versionCode` 19) zawiera trzeci format:
**H.265 + WebRTC (test)**, wcześniej sprawdzany w 0.3.3-h265.1 (`versionCode` 18).
Starsze publiczne wydanie 0.3.2 nie zawiera tej funkcji.
H.264 pozostaje zalecanym formatem WebRTC. Dotychczasowe ustawienia nie przełączają
się automatycznie na H.265; domyślny format nowej instalacji nadal jest JPEG.

## Co robi ta wersja

- Przekazuje obraz Camera2 do sprzętowego enkodera HEVC przez istniejącą ścieżkę
  tekstur WebRTC, bez pośredniej konwersji JPEG i bez enkodera programowego.
- Sprawdza rozdzielczości względem kamery, enkodera sprzętowego przy 30 kl./s,
  wejścia Surface oraz dostępności kodeka w WebRTC SDK **150.7871.01**.
- W razie braku obsługi pokazuje komunikat i pozwala wrócić do H.264 lub JPEG.
  Nie zastępuje H.265 innym kodekiem bez wiedzy testera.
- Współdzieli dotychczasowy limit bitrate 2–40 Mb/s, obrót, podgląd, zoom,
  ostrość, opcjonalne audio Opus i wyciszanie. Zapamiętuje wybrany format
  oraz rozdzielczość osobno dla kamery i formatu.
- Używa tych samych adresów `/webrtc` i `/whep`. Zmiana formatu zamyka poprzednie
  sesje i uruchamia nową konfigurację kamery; wbudowany odbiornik próbuje połączyć
  się ponownie. W go2rtc może być potrzebne ponowne połączenie źródła.
- Odbiornik oferuje tylko obsługiwane H.264/H.265 wraz z dostępnymi kodekami
  naprawczymi, m.in. RTX. Telefon wybiera wyłącznie kodek wskazany w aplikacji.
  Nieobsługiwany kodek skutkuje komunikatem w odbiorniku, bez pozornej transmisji H.265.

H.265 może poprawić jakość przy ograniczonej przepustowości, ale ten prototyp
nie potwierdza określonej oszczędności bitrate, mniejszego opóźnienia ani poboru energii.

## Uruchomienie

1. Zainstaluj APK **0.3.3** i połącz telefon oraz komputer z zaufaną siecią LAN.
2. W polu **Format** wybierz **H.265 + WebRTC (test)**. Wybierz rozdzielczość;
   na początek warto porównać 1920×1080 przy 12 Mb/s z H.264 w tych samych warunkach.
3. Włącz **Stream**. Otwórz adres `http://PHONE_IP:8080/webrtc?stats=1`.
4. W statystykach odbiornika sprawdź **`video/H265`** i rosnącą liczbę odebranych
   klatek. Sam napis w polu Format ani aktywny podgląd na telefonie nie potwierdza
   działania transmisji HEVC.
5. Dla dźwięku włącz **Microphone audio**, nadaj uprawnienie i uruchom dźwięk
   w odbiorniku. W OBS użyj **Control audio via OBS**.
6. Przy braku obrazu sprawdź komunikat odbiornika i wróć do **H.264 + WebRTC**.
   Powrót do H.264 odbywa się przez zmianę formatu, bez cofania wersji APK.

Konfiguracja go2rtc pozostaje taka sama:

```yaml
streams:
  phone:
    - webrtc:http://PHONE_IP:8080/whep
```

Sprawdź kodek zarówno po stronie źródła, jak i końcowego odbiornika. Obsługa HEVC
w przeglądarce systemowej nie oznacza automatycznie obsługi w OBS Browser Source.
Enkoder telefonu, WebRTC, relay i końcowy odtwarzacz muszą być zgodne; nagrywanie
HEVC w systemowej aplikacji aparatu też nie dowodzi zgodności z WebRTC.

## Walidacja lokalna

14 września 2026: kompilacja debug zakończona powodzeniem, **30 testów JVM**
bez błędów, **6 testów odbiornika Node** bez błędów, lint debug bez uwag.
Kompilacja release i lint release również zakończyły się powodzeniem.

Lokalny podpisany pakiet testowy jest przygotowywany w
`dist/testing/0.3.3-h265.1/RemoteCam-0.3.3-h265.1.apk`, obok `build-info.json`
i `SHA256SUMS.txt`. Podpis wydawcy jest taki sam jak w publicznym 0.3.2, dzięki
czemu pakiet może aktualizować tamtą instalację bez usuwania ustawień.
Sam build debug ma odrębny podpis deweloperski.

```powershell
.\tools\build-windows.ps1 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
node --test tools/test-webrtc-receiver.cjs
```

Testy JVM obejmują rozpoznawanie kodeka w aktywnej sekcji SDP, odrzucanie błędnego
payloadu, nieaktywnego obrazu i błędnego kierunku odpowiedzi, ścieżki WebSocket/WHEP
z opcjonalnym audio oraz zgodność zapisanych ustawień.
Testy Node uruchamiają rzeczywisty skrypt odbiornika z atrapami DOM/WebRTC:
sprawdzają oferowane kodeki, RTX, brak zgodności, komunikaty i ponowne łączenie
oraz zachowanie wyciszonego odbiornika z audio.

Te testy nie wykonują sprzętowego kodowania, dekodowania ani transmisji sieciowej.
Po udostępnieniu telefonu wykonano także rzeczywisty test na **Honor BVL-N49
z Androidem 16**: sprzętowy HEVC 1080p przy około 30 kl./s, odbiór bezpośredni
i przez go2rtc/WHEP oraz zachowanie po przejściu aplikacji w tło.
Zakres potwierdzenia i ograniczenia opisuje [raport H.265](h265-validation.md).

## Lista prób na urządzeniach

Stan wykonanych prób dla telefonu Honor znajduje się w powyższym raporcie.
Poniższa lista służy również do weryfikacji kolejnych urządzeń i odbiorników.

| Próba | Oczekiwany wynik |
| --- | --- |
| H.264 → H.265 → H.264, z audio i bez | Odbiornik odzyskuje obraz, statystyki pokazują wybrany kodek; brak poprzednich sesji audio/wideo. |
| H.265 → JPEG → H.265 | JPEG/MJPEG działają w swoim trybie, mikrofon jest zwalniany w JPEG. |
| Telefon bez kompatybilnego HEVC | Czytelny błąd; możliwość wybrania H.264/JPEG. |
| Odbiornik obsługujący tylko H.264 | Komunikat o braku H.265, bez cichego fallbacku. |
| Przeglądarka, go2rtc, OBS osobno | H.265 na wejściu i poprawnie dekodowane, przyrastające klatki na wyjściu. |
| Różne kamery, rozdzielczości, obrót, zoom, ostrość | Prawidłowe proporcje i obraz; rozdzielczość dozwolona przez wybrany enkoder. |
| Podgląd, tło i ponowne otwarcie aplikacji | Stabilność, zapamiętany format i audio; osobno sprawdzić blokadę ekranu/Standby. |
| Mikrofon, mute, odmowa uprawnienia | Obraz działa niezależnie; mute nie przerywa obrazu; odmowa nie uruchamia mikrofonu. |
| Porównanie H.264/H.265 i dłuższa transmisja | Zanotować FPS, bitrate, artefakty, temperaturę i synchronizację audio; RTT nie jest opóźnieniem końcowym. |

## Źródła zgodności

- [WebRTC SDK Android — wydania z obsługą H.265](https://github.com/webrtc-sdk/android/releases).
- [Android — formaty multimedialne](https://developer.android.com/media/platform/supported-formats): obecność dekodera nie gwarantuje enkodera HEVC.
- [Chrome 136 — H.265 w WebRTC](https://developer.chrome.com/blog/chrome-136-beta): dostępność zależy także od sprzętu i platformy.
- [go2rtc — kodeki i ograniczenia odbiorników](https://github.com/AlexxIT/go2rtc#codecs-madness).
