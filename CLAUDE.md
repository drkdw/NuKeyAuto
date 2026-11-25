# NuKey Auto

Android-app (Kotlin, Material 3, Android Auto) die een Nuki-slot bedient via de Nuki Web API. Gratis en open source, op Google Play als `com.ddw.nukeyauto`.

> De basisregels staan in `~/Documents/GitHub/CLAUDE.md` en laden vanzelf. Hieronder enkel wat specifiek is voor dit project.

## Eerst dit, elke sessie

1. `git pull`
2. Lees `docs/ARCHITECTURE.md` als je aan de repository of het auto-scherm werkt.

## Draaien, testen, bouwen

- Omgeving: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME=~/Library/Android/sdk`
- Testen: `./gradlew testDebugUnitTest` (repository, demo, Android Auto-scherm via Robolectric)
- Debug-build: `./gradlew assembleDebug`, installeren met `android install app/build/outputs/apk/debug/app-debug.apk`
- Debug-builds heten "NuKey Auto debug" (`com.ddw.nukeyauto.debug`) en staan naast de Play-versie.
- **Testen zonder echt slot: altijd de demomodus** (welkomstscherm "Try the demo" of Settings > Demo mode). Nooit een echt token in een emulator.
- Android Auto bekijken: gsm draadloos koppelen (`adb pair`), in Android Auto de ontwikkelaarsmodus en "Head unit-server starten", dan `adb forward tcp:5277 tcp:5277` en `~/Library/Android/sdk/extras/google/auto/desktop-head-unit`. De emulator kan geen echte Android Auto draaien.
- Bewijs dat het werkt: tests groen, plus screenshot in de demo (gsm en DHU).

## Git en release (release-please, zoals Flux Settings)

- Werk op een branch, PR naar `main`. Conventional commits bepalen de versie: `feat` = minor, `fix` = patch.
- release-please opent op `main` een PR `chore(release): x.y.z`. Versie (`versionName` in `app/build.gradle.kts`) en `CHANGELOG.md` **nooit met de hand** aanpassen.
- **Internal testing ("it") eerst:** checkout de release-PR-branch, `./gradlew bundleRelease -PversionSuffix=-it.1`, upload naar internal testing. Nieuwe poging: `-it.2`, enzovoort.
- Goedgekeurd: merge de release-PR (tag `x.y.z` + GitHub-release), bouw vanaf de tag zonder suffix, upload naar productie.
- `versionCode` is een tijdstempel, dus elke upload is hoger dan de vorige.
- Signeren: `local.properties` met `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Keystore en `local.properties` staan nooit in git.
- Nooit pushen of uploaden naar Play zonder expliciete ok.

## Niet aankomen, niet voorstellen

- Smart Lock ID is een gewoon nummerveld zonder oogje: het ID is niet geheim (2026-11-25).

## Valkuilen

- Geen Java in PATH op de werk-Mac: zonder `JAVA_HOME` hierboven faalt Gradle met "Unable to locate a Java Runtime".
- Nuki Web is cloud-gecached: na een actie kan de eerste status nog de oude zijn. De repository pollt tot het verwachte resultaat en toont anders "not confirmed".
- `prefs` in een Activity pas lezen na `super.onCreate()` (Hilt injecteert daar).
