# Where is the moon?

An Android home-screen widget and screensaver for astrologers, showing at a
glance:

- the **current moon phase** (as an image) and the moon's **sign and degree**
- whether the moon is currently **void of course**
- the **last aspect** the moon makes before leaving its sign (which starts
  the void period), and the time it **ingresses** into the next sign

Originally written in 2015 and sold (well, *listed*) on the Play Store;
modernized in 2026 for current Android versions. It is now a personal,
sideloaded app.

![Daydream screenshot](docs/screenshot-daydream.png)

## How it works

There are only five Kotlin files (`app/src/main/java/com/lapislucera/wheresthemoon/`):

| File | Role |
|---|---|
| `WTMAppWidget.kt` | The home-screen widget. Renders the moon info into `RemoteViews` (custom fonts are drawn to bitmaps, since widgets can't use font assets directly) and schedules exact alarms so the "void" line flips at the right minute. |
| `WTMDream.kt` | The screensaver (Daydream): a full-screen image of the current moon phase. |
| `VocDatabase.kt` | Installs `assets/databases/voc.db` into the app's data directory and answers the one query the app needs: *the next sign ingress after now, with the last aspect before it*. |
| `Ephemeris.kt` | Pure-Kotlin Sun/Moon positions (Meeus, "Astronomical Algorithms": solar theory ch. 25, lunar theory ch. 47). Replaces the JNI Swiss Ephemeris binaries the 2015 app bundled. Moon accurate to ~20″, Sun to ~40″ vs Swiss Ephemeris — far beyond what the display needs. |
| `MoonDisplay.kt` | Pure display logic: Kairon-font glyph tables, phase-image selection, degree formatting. |

**The key design split**: the *current* moon position and phase are computed
live by `Ephemeris.kt`, but void-of-course data (exact ingress and last-aspect
event times) is **precomputed** into a bundled SQLite database, because
finding those events requires root-solving against nine planets — not
something to do on a phone every 30 minutes.

### The database

`app/src/main/assets/databases/voc.db`, one table:

```sql
CREATE TABLE voc ( sign int, ingress int primary key, aspect int, planet int, asptime int );
```

| Column | Meaning |
|---|---|
| `sign` | Sign the moon enters at `ingress`: 0 = Aries … 11 = Pisces |
| `ingress` | Unix time (UTC, seconds) of the moon's ingress into `sign` |
| `aspect` | Last aspect before the ingress: 0 conjunction, 1 sextile, 2 square, 3 trine, 4 opposition |
| `planet` | Partner of that aspect: 0 Sun, 2 Mercury, 3 Venus, 4 Mars, 5 Jupiter, 6 Saturn, 7 Uranus, 8 Neptune, 9 Pluto (1 = Moon, unused) |
| `asptime` | Unix time of that last exact aspect — the moment the void period begins |

The moon is void of course between `asptime` and `ingress`.

**Current data coverage: 2025-07-01 → 2076-12-31** (8,263 rows, ~340 KB).

`PRAGMA user_version` is stamped with the generation date (`20260704`).
`VocDatabase` compares the asset's version against the installed copy's (read
directly from byte 60 of the SQLite header) and reinstalls when the app ships
newer data. If the data ever runs out, the widget shows *"VOC data expired —
update the app."* instead of crashing (which is effectively what the 2015
version did when its 10 years of data lapsed).

### Regenerating the database (when 2076 approaches, or sooner)

`tools/vocgen` is a Rust CLI that rebuilds `voc.db` using
[swephrs](https://github.com/morphatic/swephrs) — the same Swiss Ephemeris
engine that powers morphemeris.com (and the same engine the 2015 app embedded
as `libswe.so`). It scans the range hour-by-hour, finds every moon sign
ingress and every exact Ptolemaic aspect by bisection (to < 0.5 s), and picks
the last aspect before each ingress.

```sh
cd tools/vocgen
cargo build --release
./target/release/vocgen 2025-07-01 2077-01-01 voc.db 20260704   # start end out user_version
cp voc.db ../../app/src/main/assets/databases/voc.db
# bump versionCode in app/build.gradle.kts, rebuild, reinstall
```

Use today's date (YYYYMMDD) as the `user_version` so installed devices pick up
the new data.

**Validation** (2026): regenerating the original 2015-2025 window reproduced
all 1,605 rows of the 2015 database with identical sign/aspect/planet codes
and times within 15 seconds; ingress times spot-checked against the
independent Morphemeris API agreed to the sub-second.
`tools/vocgen/compare.py` (Python stdlib only) diffs two databases if you
ever want to re-validate.

## Building

Prerequisites:

- **Android SDK** with platform 36 (`sdkmanager "platforms;android-36"`).
  Installing [Android Studio](https://developer.android.com/studio) is the
  easiest way to get one. Note: this project uses AGP 8.13, which needs
  Android Studio **Narwhal 3 (2025.1.3) or newer** if you open it in the IDE;
  command-line builds don't care.
- **JDK 17+**. Android Studio bundles one — on Windows point `JAVA_HOME` at
  `C:\Program Files\Android\Android Studio\jbr`.
- A `local.properties` in the repo root pointing at the SDK (Android Studio
  creates this automatically):
  `sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk`

Then:

```sh
./gradlew assembleDebug      # → app/build/outputs/apk/debug/app-debug.apk
./gradlew test               # 25 JVM tests (JUnit + Robolectric)
./gradlew connectedDebugAndroidTest   # on-device smoke tests (device/emulator required)
```

## Installing on a phone

The debug APK is signed with the local debug keystore, so it sideloads
without any Play signing ceremony:

1. `./gradlew assembleDebug`
2. Copy `app/build/outputs/apk/debug/app-debug.apk` to the phone (or
   `adb install app-debug.apk` with USB debugging enabled).
3. Open the APK on the phone and allow "install unknown apps" when prompted.
4. Long-press the home screen → Widgets → *Where is the moon?* — and/or set
   it as the screensaver under Settings → Display → Screen saver.

There is deliberately **no launcher activity** — the app is only a widget and
a screensaver, so it won't appear in the app drawer. To uninstall, use
Settings → Apps.

Upgrading later: installs signed with the same debug keystore update in
place. The debug keystore lives in `~/.android/debug.keystore` and is
generated per-machine — if you rebuild on a new machine, uninstall the old
app first (or copy the keystore over).

## Project history

- **2015**: v1.x built against SDK 22 with JNI Swiss Ephemeris libraries and
  10 years of VOC data. The first commit in this repo is that version,
  recovered from an old hard drive.
- **2025**: the bundled data ran out (June 29), and Android 14+ refuses to
  install SDK-22 apps — the app died of two independent causes.
- **2026**: Kotlin rewrite (v2.0.0): modern toolchain (Gradle 8.13 /
  AGP 8.13 / Kotlin 2.2 / target SDK 36), JNI replaced with pure-Kotlin
  Meeus algorithms, database regenerated through 2076 with a generator that
  now lives in this repo, tests added. Old signed APKs are kept out of git
  (`legacy-apks/`, see `.gitignore`).

Modern-Android specifics worth remembering (the things that broke between
2015 and 2026):

- Background services can't be started from alarms (Android 8+); the alarms
  now broadcast straight back to the widget provider.
- `PendingIntent` requires an explicit mutability flag (Android 12+).
- Exact alarms need a permission: `USE_EXACT_ALARM` (auto-granted, Android
  13+) / `SCHEDULE_EXACT_ALARM` (12/12L), with a `setWindow` fallback if the
  grant is missing. Play policy restricts `USE_EXACT_ALARM` to alarm/calendar
  apps, but sideloaded apps don't answer to Play policy.
- The resource shrinker would strip the dynamically-looked-up
  `moon_NNN.png` images without `res/raw/keep.xml`.

## Lock screen widgets (Samsung One UI)

Two compact widgets (`WTMAspectComplication`, `WTMIngressComplication`)
appear in Samsung's lock-screen widget picker, meant to sit side by side
under the clock: the last aspect and the next ingress. During a void
period their labels change to **"Void since"** / **"Void until"**, and
their text turns gold — though the gold is only visible when the same
tiles are placed on the home screen (see below).

Getting into that picker required reverse-engineering One UI's private
"complication" scheme (observed on a Galaxy S24 Ultra, One UI 8 /
Android 16); the standard `widgetCategory="keyguard"` is **not** what
Samsung filters on:

- The receiver needs `<meta-data android:name="widgetStyle"
  android:value="complication"/>` in the manifest.
- The appwidget-provider XML needs Samsung's private attributes, declared
  locally in `values/attrs.xml` and emitted under `res-auto`:
  `app:targetHost="6"`, `app:widgetStyle="2"`, and `app:widgetSize="2"`.
- Samsung's framework detects these and rewrites the provider's
  `widgetCategory` to their private bit 0x2000 at registration.
- The lock host grants each tile **~123×54 dp** (two fit side by side;
  that's the entire widget area) and reports it only via
  `OPTION_APPWIDGET_SIZES`, not the MIN_WIDTH/HEIGHT ints launchers set.
  The tiles read it and scale their text to fill the space.
- The lock host **strips all color**: bitmaps are used as alpha masks
  and re-tinted white (verified by pixel-sampling a screenshot after
  rendering amber text — it arrived as RGB 243,243,243). Hence the label
  swap as the void indicator on the lock screen, and why the
  photographic moon renders as a white disc there
  (`WidgetRender.phaseSilhouetteBitmap` draws a phase shape that
  survives alpha-only rendering, kept for future use).

All of this is undocumented private Samsung behavior and could change in
any One UI update; the tiles degrade to normal small home-screen widgets
everywhere else (with the gold visible).

## Fonts

- **Kairon Semiserif** (astrological glyphs) — © Kilian Sternad, from the
  free Kairon astrology software. "This Font is FREE, it must not be sold."
- **Alegreya Sans SC** — © Juan Pablo del Peral / Huerta Tipográfica,
  [SIL Open Font License 1.1](https://openfontlicense.org/).

## License

Application code and tools: MIT (see `LICENSE`). Fonts remain under their
own licenses above.
