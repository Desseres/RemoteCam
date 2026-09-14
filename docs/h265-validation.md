# H.265 — walidacja na urządzeniu

Data: **14 września 2026**. Aplikacja **0.3.3-h265.1 (18)**, źródła
`7593428de3e66430cdbfe6ca3b322caee2a5f763`. Telefon **Honor BVL-N49, Android 16**.
Zainstalowano build debug jako aktualizację wcześniejszego debug 0.3.2, zachowując
ustawienia. Publicznego wydania 0.3.2 na GitHubie nie zmieniano.

## Potwierdzone

- Kamera przednia (ID 1), 1920×1080, obrót 270°, limit 40 Mb/s:
  **sprzętowy enkoder `c2.qti.hevc.encoder`**, bez błędu kamery lub enkodera.
- Bezpośredni odtwarzacz `/webrtc?stats=1&muted=1`: rzeczywiste **`video/H265`**,
  obraz 1920×1080 i około **30 kl./s**. Licznik zdekodowanych klatek rósł
  m.in. z 409 do 3667 i 4859; to potwierdzenie odbioru, nie tylko działania podglądu.
- Podczas ręcznych zmian użytkownika odbiornik odzyskał H.265 po zmianie
  rozdzielczości (zaobserwowano też 176×144) i przełączeniu na tylną kamerę ID 0.
  Końcowy odczyt dla kamery tylnej: 1920×1080, 29 kl./s, 586 zdekodowanych klatek;
  telefon nadal raportował `c2.qti.hevc.encoder`, bez błędu przechwytywania.
- Kamera nadal przesyłała obraz przy wyłączonym podglądzie oraz po przejściu
  aplikacji w tło i powrocie. Generacja przechwytywania pozostała równa 2,
  licznik klatek na telefonie rósł, a dekodowanie w odbiorniku trwało.
- WHEP przez lokalny **go2rtc 1.9.14+dev.b5948cf.dirty** (istniejący build projektu
  z poprawką retransmisji): źródło i odbiornik negocjowały **H.265 + Opus**.
  W jednej próbce źródło odebrało 21 532 pakiety HEVC oraz 350 pakietów Opus.
  Odbiornik przeglądarkowy za relayem odtwarzał 1920×1080, a czas odtwarzania
  wzrósł z 30,990 do 71,349 s.
- Odbiornik używał silnika Chromium 152 na Windows. Odbiór bezpośredni i przez
  relay sprawdzono także równocześnie jako dwa połączenia z telefonem.
- Oferta WHEP zawierająca wyłącznie H.264, przy wybranym H.265 na telefonie,
  została odrzucona z HTTP 503 i komunikatem o braku wybranego kodeka oraz
  możliwości przełączenia telefonu na H.264. Bieżąca transmisja HEVC działała dalej.
- Telefon raportował aktywne przechwytywanie mikrofonu i brak błędów audio.
  go2rtc potwierdził pakiety Opus; odtwarzacze testowe były wyciszone.

## Granice wyniku

- To sprawdzenie jednego telefonu i jednej konfiguracji kamery. Nie potwierdza
  zgodności wszystkich urządzeń, rozdzielczości ani profili HEVC.
- Nie wykonywano odsłuchu ani pomiaru synchronizacji ust; same pakiety Opus
  i stan mikrofonu nie potwierdzają jakości dźwięku.
- Do wykonania pozostaje kontrolowana próba H.265 → H.264/JPEG → H.265,
  wyciszania i wyłączania audio oraz urządzenia bez enkodera HEVC. Nie ukończono
  tych prób podczas równoległej obsługi telefonu przez użytkownika.
- Nie wykonano testu w OBS Browser Source, pomiaru końcowego opóźnienia,
  zużycia baterii, temperatury, odporności na utratę pakietów ani długiego testu
  przy zablokowanym ekranie / Standby.
- Bitrate zmieniał się wraz ze sceną i liczbą odbiorników. Nie było kontrolowanego
  porównania jakości H.264/H.265, więc nie wyciągamy wniosku o procentowej
  oszczędności pasma. RTT i średni bufor jitter nie są opóźnieniem kamera–ekran.
- Podpisane APK release pochodzi z tych samych źródeł, ale na telefonie testowano
  wariant debug, zgodny z podpisem poprzednio zainstalowanej aplikacji.

Lista dalszych prób: [instrukcja testowania HEVC](h265-testing.md).
Tymczasowe logi i diagnostyka pozostają w ignorowanym katalogu `build/`.
