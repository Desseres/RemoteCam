# Źródła materiałów

`*.svg` to edytowalne układy wygenerowane przez `tools/generate-store-assets.cjs`.
Pierwsze cztery osadzają rzeczywiste PNG z `../screenshots/`. Nie odtwarzają ani nie
retuszują UI. `../graphics/` zawiera eksporty PNG, a `../contact-sheet.png` podgląd zestawu.
Do odtworzenia uruchom `node tools/generate-store-assets.cjs` z zainstalowanym `sharp`.

Ikona jest wektorowa, z własnym symbolem aparatu i łączności. Wersja adaptacyjna Androida
jest w `app/src/main/res/mipmap-anydpi/ic_launcher.xml`, jej znak w `drawable/ic_camera_mark.xml`.
Natywna ikona i ikona sklepu korzystają z tych samych współrzędnych i kolorów.

Ilustracja startowa jest wygenerowana przez wbudowane narzędzie `image_gen` (bez CLI/API).
Oryginał projektu znajduje się w `app/src/main/res/drawable-nodpi/launch_illustration.png`.
Nie przedstawia interfejsu aplikacji ani prawdziwego obrazu kamery. Prompt jest w
`splash-prompt.txt`; model może przy powtórzeniu stworzyć inny obraz.

Polityka HTML powstaje z tekstu dołączonego do aplikacji:
`python tools/prepare-play-documents.py`. Skrypt sprawdza też długości opisów sklepowych.

Kolory: tło #171819 / #151515, akcent #FFE15A, tekst #F4F3EF, karty #252729.
Font grafik: Segoe UI (fallback Arial / sans-serif), używany przy rasteryzacji na Windows.
Do reprodukcji na innym systemie użyj tej samej dostępnej czcionki lub sprawdź ponownie łamanie wierszy.
