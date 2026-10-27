# Przepisak

Osobisty menedżer przepisów kulinarnych i cukierniczych na Androida.
Architektura **offline‑first**: źródłem prawdy jest lokalna baza Room, a chmura
(Supabase) służy wyłącznie jako kopia zapasowa i sposób przeniesienia danych
między urządzeniami.

Aplikacja jest przeznaczona na własny użytek — nie wymaga publikacji w Google Play.

---

## Co potrafi

| Wymaganie | Realizacja |
|---|---|
| RF‑01, RF‑02 | Tworzenie i edycja przepisów: tytuł, opis, czasy, trudność, porcje, kategoria, tagi, grupy składników („ciasto”/„krem”), kroki ze zdjęciami etapów |
| RF‑03 | Wyszukiwanie pełnotekstowe (SQLite FTS4) po tytule, opisie, składnikach, tagach i notatkach + filtrowanie wielokryterialne |
| RF‑04 | Rejestracja w „Udostępnij” systemu Android — link z przeglądarki, YouTube’a czy TikToka trafia wprost do importu |
| RF‑05 | Import ze stron WWW: dane strukturalne Schema.org (JSON‑LD) → mikrodane → Gemini na wyłuskanej treści strony |
| RF‑06 | Import z filmów: napisy z YouTube’a (albo opis, gdy napisów nie ma) przepuszczone przez Gemini |
| RF‑07 | Własny klucz Gemini API wpisywany w Ustawieniach, przechowywany w `EncryptedSharedPreferences` |
| RF‑08 | Skalowanie porcji z inteligentnym formatowaniem ilości (½, 1¼) i polską odmianą jednostek |
| RF‑09 | Kalkulator form do pieczenia — przeliczanie powierzchni i objętości między tortownicą, blachą i formą z kominem |
| RF‑10 | Wbudowana baza wagowa produktów (objętość → gramy), m.in. 1 szklanka mąki pszennej = 160 g |
| RF‑11, RF‑12 | Tryb gotowania: powiększona czcionka, wielkie przyciski nawigacji, blokada wygaszania ekranu |
| RF‑13 | Minutniki wykrywane w treści kroków, uruchamiane jednym dotknięciem, działające w tle |
| RF‑14 | Automatyczna kompresja zdjęć przed zapisem (skalowanie + korekta orientacji EXIF + pętla jakości JPEG) |
| RF‑15 | Kopia zapasowa w Supabase (PostgreSQL + Storage) przez WorkManager |
| RNF‑01 | Wyłącznie darmowe plany — Supabase Free Tier i Google AI Studio |
| RNF‑03 | Tworzenie, edycja, przeglądanie i gotowanie działają bez internetu |

---

## Wymagania do zbudowania

- **JDK 17 lub 21.** Wtyczka Android Gradle 8.10 nie działa na JDK 26, które jest
  zainstalowane w systemie. Najprościej użyć JDK dołączonego do Android Studio
  (JetBrains Runtime 21) albo zainstalować osobno:
  ```bash
  # Manjaro / Arch
  sudo pacman -S jdk21-openjdk
  sudo archlinux-java set java-21-openjdk     # albo tylko dla tego projektu:
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
  ```
- **Android SDK** z Platform API 35 i Build‑Tools 35. Android Studio instaluje je
  sam; przy budowaniu z konsoli wskaż SDK w `local.properties`:
  ```properties
  sdk.dir=/home/<użytkownik>/Android/Sdk
  ```
- Gradle **nie** jest potrzebny w systemie — w repozytorium jest wrapper (`./gradlew`).

### Budowanie

```bash
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/
./gradlew installDebug           # instalacja na podłączonym urządzeniu
./gradlew test                   # testy jednostkowe (JVM, bez urządzenia)
```

Wersja `release` jest domyślnie **bez R8** (`isMinifyEnabled = false`) — Supabase
i Ktor korzystają z refleksji, a przy aplikacji na własny użytek kilka megabajtów
nie jest warte ryzyka. Reguły w `app/proguard-rules.pro` są gotowe, jeśli chcesz
włączyć skracanie i przetestować import.

Aby podpisać własnym kluczem, dopisz w `local.properties`:

```properties
przepisak.keystorePath=/home/<użytkownik>/przepisak.jks
przepisak.keystorePassword=...
przepisak.keyAlias=przepisak
przepisak.keyPassword=...
```

---

## Konfiguracja po pierwszym uruchomieniu

Aplikacja działa od razu — **bez żadnej konfiguracji** można dodawać, edytować
i gotować przepisy. Dwie funkcje wymagają własnych kluczy:

### 1. Import przez AI (RF‑05, RF‑06, RF‑07)

1. Wejdź na [Google AI Studio](https://aistudio.google.com/apikey) i wygeneruj
   darmowy klucz API.
2. W aplikacji: **Ustawienia → Import AI → Klucz Gemini API**, wklej klucz
   i dotknij „Sprawdź połączenie”.

Klucz trafia do `EncryptedSharedPreferences` i nie opuszcza urządzenia inaczej niż
w wywołaniu do API Google. Bez klucza import ze stron z danymi Schema.org **nadal
działa** — Gemini jest potrzebne tylko dla stron bez mikrodanych i dla filmów.

### 2. Kopia zapasowa w Supabase (RF‑15)

1. Utwórz darmowy projekt na [supabase.com](https://supabase.com).
2. Otwórz **SQL Editor** i wykonaj cały skrypt z [supabase/schema.sql](supabase/schema.sql)
   — zakłada tabelę, kubełek na zdjęcia i polityki RLS.
3. Skopiuj z **Project Settings → API**: `Project URL` oraz klucz `anon public`.
4. W aplikacji: **Ustawienia → Kopia zapasowa** — wklej URL i klucz, utwórz konto
   (e‑mail + hasło), włącz automatyczną synchronizację.

Adres i klucz można też ustawić domyślnie na etapie budowania (`local.properties`):

```properties
przepisak.supabaseUrl=https://xxxx.supabase.co
przepisak.supabaseAnonKey=ey...
```

---

## Jak to jest zbudowane

```
app/src/main/java/pl/przepisak/
├── core/
│   ├── util/          Outcome/AppError, normalizacja tekstu, źródło czasu
│   └── ui/            motyw Material 3 + komponenty wspólne
├── domain/
│   ├── model/         modele domenowe (bez zależności od Androida)
│   ├── scaling/       parsowanie i formatowanie ilości, skalowanie, przeliczniki
│   ├── timer/         wykrywanie czasu w treści kroków
│   └── importer/      klasyfikacja adresów URL
├── data/
│   ├── local/         Room: encje, DAO, relacje, mappery, dane startowe
│   ├── prefs/         DataStore + EncryptedSharedPreferences
│   ├── media/         kompresja i przechowywanie zdjęć
│   ├── remote/        Gemini, parsery HTML, YouTube, Supabase
│   └── repository/    repozytoria — jedyne wejście dla warstwy UI
├── feature/           ekrany (Compose + ViewModel na ekran)
├── notification/      minutniki: alarmy i powiadomienia
├── sync/              WorkManager
├── di/                moduły Hilta
└── nav/               trasy i NavHost
```

Kilka decyzji, które warto znać przed czytaniem kodu:

- **Baza jest źródłem prawdy, zawsze.** Zapis nigdy nie czeka na sieć.
  Synchronizacja to osobny, asynchroniczny proces.
- **Synchronizacja przenosi cały przepis jako jeden wiersz `jsonb`**, a nie sześć
  tabel relacyjnie. Konflikt rozstrzyga nowszy `updated_at` (last‑write‑wins) —
  przy jednym użytkowniku jest to przewidywalne i nie potrafi zgubić części przepisu.
  Kategoria i tagi jadą **po nazwie**, bo lokalne identyfikatory różnią się między
  urządzeniami.
- **Usuwanie jest miękkie** („nagrobek”), żeby usunięcie dotarło na pozostałe
  urządzenia zamiast wracać przy następnym pobraniu.
- **Diakrytyki normalizujemy sami**, zanim tekst trafi do indeksu FTS. Opcja
  `remove_diacritics` tokenizera SQLite zależy od wersji systemu, a „lodowka” ma
  znajdować „lodówka” na każdym Androidzie.
- **Minutniki nie używają usługi pierwszoplanowej.** Minutnik to zapisany w bazie
  moment końca + dokładny alarm (`setAlarmClock`) + powiadomienie z licznikiem
  odliczanym przez system. Przeżywa ubicie procesu, nie zużywa baterii i nie
  potrzebuje uprawnienia do usługi w tle.
- **Wersje bibliotek są celowo z jednego „release train”** (Kotlin 2.1.21 /
  Compose BOM 2025.06.01 / Room 2.7.2). Podnoś je grupami w
  [gradle/libs.versions.toml](gradle/libs.versions.toml): AGP razem z Gradle,
  Kotlin razem z KSP i wtyczką Compose.

## Stan weryfikacji

Kod powstał w środowisku **bez Android SDK i bez Gradle**, więc nie został
skompilowany ani uruchomiony. Warto o tym wiedzieć, zanim zaczniesz:

**Sprawdzone maszynowo:**
- każda klasa wskazana w modułach Hilta istnieje i ma konstruktor `@Inject`;
- wszystkie ekrany wołane przez `PrzepisakNavHost` istnieją;
- wszystkie klasy i style, do których odwołuje się `AndroidManifest.xml`, istnieją;
- każdy użyty `R.string.*` jest zdefiniowany, żaden klucz nie jest zdefiniowany dwa razy;
- interfejsy `SettingsRepository`, `SecretStore` i `TimerRepository` są zaimplementowane w całości;
- wbudowana baza wagowa: 172 wpisy, **bez powtórzeń** znormalizowanych nazw
  (na tej kolumnie jest indeks `UNIQUE`, więc duplikat wywróciłby pierwsze uruchomienie);
- wartości odniesienia z SRS: 1 szklanka mąki pszennej = 160 g, 1 szklanka cukru = 200 g.

**Do sprawdzenia przy pierwszym budowaniu:**
- `./gradlew test` — 181 przypadków testowych, w tym testy na prawdziwym SQLite
  (Robolectric), które wykonują zapytania pełnotekstowe, filtrowanie i wstawianie
  danych startowych;
- wersje bibliotek — dobrane jako jeden spójny zestaw, ale nikt ich razem nie
  skompilował; jeśli coś nie przejdzie, poprawka będzie w
  [gradle/libs.versions.toml](gradle/libs.versions.toml);
- import z YouTube’a opiera się na strukturze strony odtwarzacza, która nie jest
  publicznym API — może przestać działać po zmianie po stronie Google’a; ścieżka
  „opis filmu” jest wtedy nadal dostępna.

## Uprawnienia i dlaczego są potrzebne

| Uprawnienie | Po co |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | import przepisów, kopia zapasowa |
| `POST_NOTIFICATIONS` | powiadomienia minutników |
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | minutnik musi zadzwonić co do sekundy, także w trybie Doze |
| `VIBRATE` | sygnał zakończenia minutnika |
| `RECEIVE_BOOT_COMPLETED` | wznowienie minutników po restarcie telefonu |

Aplikacja **nie** prosi o dostęp do galerii ani do aparatu — zdjęcia wybiera się
przez systemowy Photo Picker, a robi się systemowym aparatem przez `FileProvider`.
