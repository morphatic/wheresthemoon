# Where is the moon? — notes for AI-assisted sessions

Android widget + screensaver showing the current moon phase, sign, and
void-of-course status. **Read README.md first** — it explains the
architecture, the voc.db schema, and the regeneration workflow.

## Build & test

```sh
# Windows: JAVA_HOME must point at a JDK 17+, e.g.
#   export JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
./gradlew assembleDebug   # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew test            # JVM tests (JUnit + Robolectric)
```

## Ground rules

- The app has **zero runtime dependencies** — keep it that way.
- `Ephemeris.kt` and `tools/vocgen` (and the bundled voc.db) must stay
  mutually consistent: all use the swephrs/Swiss-Ephemeris *apparent
  longitude of date* convention, and Ephemeris.deltaT is a quadratic fitted
  to swephrs' ΔT. If you touch any of them, re-run EphemerisTest and
  consider regenerating reference values (the test file documents where
  they came from).
- voc.db codes (sign 0-11 from Aries, aspect 0-4, planet 0-9 Sun→Pluto with
  1=Moon unused) index straight into the glyph arrays in `MoonDisplay.kt`;
  they match the 2015 database format and must not change meaning.
- The moon_NNN drawables are looked up by name at runtime; res/raw/keep.xml
  protects them from the resource shrinker. Don't remove it.
- Data expires 2076-12-31. Regeneration: see README "Regenerating the
  database".
