# RemoteCam Desktop 0.1.10 — Windows public test

Telefon jako kamera Windows, dostępna bezpośrednio na liście urządzeń w OBS.
To testowa aplikacja towarzysząca RemoteCam na Androida.

## Pobieranie i uruchomienie

1. Pobierz **RemoteCam-Desktop-0.1.10-test-Setup.exe** z załączników poniżej.
   Wymagany **Windows 11 x64** i uprawnienia administratora do instalacji kamery.
2. Zainstaluj [RemoteCam 0.3.2 na Androidzie](https://github.com/Desseres/RemoteCam/releases/tag/v0.3.2).
   Połącz telefon i komputer z tą samą zaufaną siecią lokalną.
3. W telefonie wybierz **H.264 + WebRTC** i włącz **Stream**.
4. W RemoteCam Desktop wpisz `ADRES_IP_TELEFONU:8080` i kliknij **Połącz**.
5. W OBS dodaj **Urządzenie do przechwytywania wideo** i wybierz **RemoteCam**.

Przy aktualizacji wybierz **Zakończ** z menu ikony RemoteCam w trayu.
Krzyżyk okna chowa aplikację, pozostawiając aktywną transmisję.

## Co zawiera wersja testowa

- Wirtualna kamera Windows: wyjście 1920 × 1080 przy 30 kl./s.
- Odbiór H.264 oraz eksperymentalny H.265, dekodowanie GPU D3D11VA ze ścieżką CPU.
  Publiczne APK 0.3.2 nadaje H.264; H.265 wymaga osobnej wersji rozwojowej Androida.
- Odtwarzanie odbioru po przerwaniu połączenia lub zmianie strumienia.
- Praca w trayu i wyłączenie podglądu bez zatrzymywania kamery.
- Informacje o sprzęcie i zalecenia jakości na podstawie obserwacji odbioru.
- Opcjonalny moduł mikrofonu przez VB-CABLE, z regulacją głośności i wyciszeniem.
- Ciemny interfejs, logo, informacje o projekcie i instalator w jednym pliku EXE.
- Poprawiony układ zakładki Mikrofon i wysokość pola adresu IP.

## Mikrofon

W zakładce **Mikrofon** można osobno uruchomić oryginalny instalator VB-CABLE.
W OBS dodaj **Przechwytywanie wejścia dźwięku → CABLE Output**.
Wybierz **Monitorowanie wyłączone**, wycisz odtwarzacz w przeglądarce i pozostaw
wyłączone **Nasłuchuj tego urządzenia** w Windows. Fizyczny mikrofon pozostaje
osobnym źródłem. Instalator VB-CABLE może zmienić domyślne urządzenia audio Windows;
po instalacji sprawdź swoje głośniki i mikrofon.

VB-CABLE to osobny produkt VB-Audio (donationware), z własną licencją.
Jego instalacja jest opcjonalna; aplikacja nie ustawia kabla jako domyślnych głośników.

## Status testów i ograniczenia

Sprawdzono odbiór z telefonu, kamerę przez systemowe API Windows, ciągłość
transmisji w trayu, wyciszenie i wznowienie osobnego wejścia audio oraz układ UI.
Wyniki nie stanowią pomiaru całkowitego opóźnienia ani gwarancji wydajności 4K
na każdym komputerze. Zacznij od H.264, 1920 × 1080, 30 kl./s.

Interfejs aplikacji jest po polsku. To aplikacja działająca w sesji użytkownika,
nie usługa systemowa; zamknięcie przez Zakończ lub wylogowanie zatrzymuje odbiór.
Instalator RemoteCam nie ma podpisu Authenticode. Obok EXE znajduje się suma SHA-256
oraz `build-info.json` wskazujący commit źródeł.

## English quick start

Install the EXE on **Windows 11 x64** and the linked **Android 0.3.2 APK** on your phone.
Enable **H.264 + WebRTC / Stream**, connect Desktop to `PHONE_IP:8080`, then select
**RemoteCam** as a Video Capture Device in OBS. Closing the window keeps it in the
tray; **Zakończ** exits. Optional phone audio uses a separately installed VB-CABLE
recording endpoint (**CABLE Output**), with monitoring off. The UI is in Polish.
This is an unsigned test installer; the adjacent SHA-256 file verifies its contents.

Rozwój RemoteCam: © 2026 Paweł Kasztelan (Desseres). Projekt bazuje na RemoteCam
autorstwa Thomasa SIMONA (Ruddle), © 2023. Licencja MIT; składniki zewnętrzne mają
osobne informacje licencyjne dołączone do aplikacji.

[Strona projektu](https://remotecam.kasztelan.me/) ·
[Zgłoś problem](https://github.com/Desseres/RemoteCam/issues)
