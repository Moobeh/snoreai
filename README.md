# SnoreSafe

Private snore tracker by moobehgames. Free, no ads, no accounts, no data collection.
All listening and analysis happen on the phone; the app has no internet permission.

## Layout

| Module | What |
|---|---|
| `shared/` | Kotlin Multiplatform core: loudness, snore detection, Snore Score, session statistics, gap detection, on-device personal model. No Android code — tested on the JVM. |
| `androidApp/` | Android app: microphone foreground service, YAMNet on LiteRT, storage, Jetpack Compose UI. |
| `docs/feature-spec.md` | Product spec and decisions. |

## Pipeline

```
mic (16 kHz mono) → 0.975 s window, 0.49 s hop → YAMNet (LiteRT, on device)
  → SoundTaxonomy (snore probability + other night sound)
  → SnoreDetector (sensitivity, calibrated loudness → band)
  → SessionAccumulator (bands, minute timeline, interruptions, checkpoint every minute)
  → SessionSummary (saved on device)
```

Raw audio is analysed and discarded. If the phone kills the app overnight, the last
checkpoint is turned into a night marked "recovered" on the next launch.

## Build

Requires JDK 17+ and the Android SDK (platform 36).

```bash
./gradlew :shared:jvmTest
```

```bash
./gradlew :androidApp:check :androidApp:assembleDebug
```

`check` includes a permission guard: the build fails if any variant's merged manifest
requests internet, network-state or advertising-ID permissions.

## Versions

minSdk 24 · targetSdk / compileSdk 36 · App ID `com.moobehgames.snoresafe`.

## Licence

Closed source. © moobehgames. Third-party components: see `THIRD_PARTY_NOTICES.md`.
