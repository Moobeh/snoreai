# SnoreSafe — Handoff (point in time: 9 Nov 2026)

Everything needed to pick this project up in a new session. Product decisions live in
`docs/feature-spec.md`; this file covers state, how things work, and what's next.

---

## 1. Where things stand

| | |
|---|---|
| Repo | `Moobeh/snoreai` (private). Default branch `main`. |
| Working branch | `claude/keen-bardeen-83nwoa` → PRs into `main`. Owner merges. |
| Merged | PR #1 (CI fix) and everything before it (skeleton, core, recording, detection). |
| Open | **PR #2** — everything below. CI green at each push. Ready to merge after on-phone checks. |
| App | **SnoreSafe** · App ID `com.moobehgames.snoresafe` · publisher moobehgames · v0.1.0 (versionCode 1) |
| Platforms | Android now (minSdk 24, target/compile 36). iOS **parked**. |
| Licence | Closed source, free, no ads, no accounts, no data collection, no internet permission. |

### Built (PR #2 unless noted)
- **Recording**: microphone foreground service; start delay; pause with auto-resume; checkpoint every minute; nights cut short are recovered and flagged.
- **On-device AI**: YAMNet (Apache-2.0) on LiteRT; snore detection with sensitivity, volume offset, light-snoring filter.
- **Other sounds**: ~30 categories (car/smoke alarm, siren, baby, glass, doorbell, talking, pets, traffic…), timed events, clips; notable ones under "Worth knowing".
- **Clips & playback**: AAC with 3 s pre-roll; modes best clips / all snoring / full night (10-min segments); timeline tap-to-play; pin, delete, protect night.
- **Remedies & factors**: 28 built-in + custom; tonight's picks; per-night notes, weight (kg/lb), rest rating; "What helps" (with vs without).
- **Remedy trials**: shuffled balanced schedule, nightly instruction, permutation-test verdict.
- **Disturbances**: sounds linked to snoring stopping / getting louder; bedroom background noise + tip.
- **Highlight reel**: ≤30 s montage of loudest snores + notable sounds, shared via share sheet.
- **Personal model**: snoring / not-snoring answers on clips → logistic head retrained on device → blended into detection. Training set stored separately from audio.
- **Encryption at rest**: AES-256-GCM, key in Android Keystore; legacy plaintext migrated on start.
- **Trends**: periods, comparison, score chart, calendar, What helps, trials.
- **Storage manager**: limit, retention, clips kept for older nights, protected nights.
- **Backup/restore**: password-encrypted file (chunked AES-GCM + PBKDF2) incl. logs, custom tags, personal labels. CSV/JSON export. PDF night/trend reports.
- **Tip jar**: Play Billing consumables `tip_coffee` / `tip_lunch` / `tip_thanks`; polite prompt rules; hidden after a tip or "already supported".
- **Battery guides**: maker detection (Samsung, Xiaomi/Redmi/POCO, Huawei/Honor, OnePlus/Oppo/Realme, vivo, other) + best-effort settings shortcuts.
- **Onboarding, results tutorial, FAQ, privacy, licences.**
- **Brand**: icon (shield + moon + audio bars), Play icon 512, feature graphic 1024×500, phone screenshots — `brand/`.

### Verified vs not
- Verified here: 56 core tests, 25 debug + 13 release app tests, lint 0 issues, debug + release builds, Robolectric renders of every screen, merged-manifest permission guard.
- Verified by owner on a phone: the first PR #2 build, with simulated sounds (looked good). A full night was being recorded on 8–9 Nov.
- **Not yet verified on a device**: Android Keystore encryption + migration of that night's data, playback from encrypted clips, labelling/retraining, remedy trials, disturbances, highlight reel (AAC decode/encode), maker settings shortcuts, Play Billing (needs Play distribution).

---

## 2. Next steps (in order)

1. **Owner device check** of the latest test APK (signed with the shared debug key, installs over earlier builds): last night's results, playback, a few labels, tonight's remedies, a trial, highlight reel, battery shortcut. Then merge PR #2.
2. **Remaining Android v1**: bedtime reminder (off by default, no exact-alarm permission) · crash reports (stored locally, user reviews then emails) · session extras (adjust bed/wake times, merge sessions, charger/low-storage warnings, face-down screen-off, night clock, quick-settings tile) · translations (EU languages + Arabic, RTL check).
3. **Release prep** (owner): signed AAB (Android Studio, or GitHub Actions with keystore as secrets — undecided) · privacy policy on GitHub Pages (Claude can write it) · Play Console tip products · data safety = no data collected · health-app declaration = wellness · formal trademark check "SnoreSafe" (UKIPO/EUIPO/USPTO) · recruit 12+ testers → 14-day closed test (required for this developer account).
4. **v1.1**: own risk questionnaire + remedy quiz, achievements, Health Connect, experimental breathing rate/pauses.
5. **Differentiator backlog**: **"Who's snoring?"** — separate two snorers using YAMNet embeddings + clustering + a few "me / them" answers. Prototype on real two-snorer recordings before promising; needs the YAMNet variant that outputs embeddings.
6. **iOS (parked)**: KMP shared core is iOS-ready (targets enabled on macOS). Needs SwiftUI app, AVAudioEngine background audio, YAMNet via Core ML or TFLite, Keychain key, StoreKit tips. Build/sign/upload via GitHub macOS runners → TestFlight (owner needs Apple Developer Program + App Store Connect API key as GitHub secrets). macOS CI minutes cost 10× on private repos — run iOS CI on demand/`main` only.

---

## 3. Architecture

```
shared/      Kotlin Multiplatform core (JVM-tested, no Android code)
  analysis/  Loudness, SoundTaxonomy (YAMNet labels → NightSound), SnoreDetector, SoundEvents
  session/   SessionAccumulator (streaming stats, gaps, pauses, events), SnoreScore, models, SessionCodec (JSON)
  audio/     AudioClip/SessionAudio, ClipSelection (per mode), StorageCleanup
  ml/        PersonalHead (logistic regression, on-device training), PersonalTraining (training set)
  tags/      Remedies/factors catalog, NightLog, TagImpacts
  insights/  RemedyTrial(s) + permutation test, Disturbances, HighlightReel plan
  trends/    Trends (night day, stats, comparisons, points)
  settings/  AppSettings (+ RecordingMode, StorageSettings, WeightUnit)
  support/   SupportPolicy (tip prompt rules)
  export/    CsvExport
  device/    PhoneMaker
androidApp/
  recording/ RecordingService (mic FGS loop), RecordingState, NightFinisher, SessionRecovery, Notifications
  audio/     MicSource, AacFileWriter, ClipRecorder (pre-roll, segments, sealing), ReelBuilder
  ml/        YamnetClassifier (LiteRT)
  data/      Secure (Keystore AES-GCM), AtomicFile, Migration, stores: Session/Audio/Log/Settings/Checkpoint/PersonalModel
  backup/    BackupCipher (chunked AES-GCM + PBKDF2), BackupManager
  export/    PdfReport, Sharing (FileProvider: cache/shared only)
  billing/   TipJar
  ui/        Compose screens; simple string back-stack navigation in MainActivity
```

**Audio pipeline**: 16 kHz mono → 0.975 s window, 0.49 s hop → YAMNet → snore probability (blended with personal model) + other sound → detector → accumulator + clip recorder → on stop: NightFinisher (save, tags/trial into log, clip selection, storage limits, support moment).

**Snore Score** = band-weighted snoring minutes per hour monitored (light 1, loud 2, epic 3; 1 h non-stop epic = 180).

**Storage** (app-private `filesDir`, all encrypted "SSE1" header): `sessions/*.json`, `logs/*.json`, `audio/<id>/index.json + *.m4a`, `personal/{model,training-set}.json`, `settings.json`, `active-session.json`. Recordings write as `.part` (plain) then sealed; leftovers deleted on start.

**Privacy guard**: `verify<Variant>Permissions` (part of `check`) fails if the merged manifest has INTERNET / ACCESS_NETWORK_STATE / ad-ID / ad-services permissions or telemetry components (datatransport, Firebase, gms measurement/ads). Play Billing's bundled `datatransport` is stripped in the manifest (`tools:node="remove"`).

---

## 4. Build, test, CI

```bash
./gradlew :shared:jvmTest
```

```bash
./gradlew :androidApp:check :androidApp:assembleDebug :androidApp:assembleRelease
```

- `check` = lint (abortOnError), unit tests (incl. Robolectric renders → `androidApp/build/screenshots/`), permission guard.
- Screen render tests run in debug only (they need the debug-only Compose test activity).
- CI: `.github/workflows/ci.yml` on every push/PR (ubuntu, JDK 21, runner's Android SDK + platform 36). Uploads reports, screenshots, debug APK.
- Versions: Gradle 8.14.3, AGP 8.13.0, Kotlin 2.2.21, Compose BOM 2025.10.01, LiteRT 1.4.0, Billing 8.0.0, Robolectric 4.16.
- Test signing: `androidApp/debug.keystore` (shared standard debug key) so test builds install over each other. **Play upload key is separate and must be created/kept by the owner.**

### Sandbox quirks (cloud sessions)
- No Android SDK preinstalled: install cmdline-tools to `~/android-sdk`, `sdkmanager "platforms;android-36" "build-tools;36.0.0"`, and `local.properties` with `sdk.dir`.
- No KVM → no emulator. Use Robolectric renders + Python LiteRT (`ai-edge-litert`) for model checks.
- Maven Central sometimes returns **429**. Fetch large Robolectric artifacts (`android-all-instrumented`, `nativeruntime-dist-compat`) from `https://maven-central.storage-download.googleapis.com/maven2/` into `~/.m2/repository` and add `~/.gradle/init.d/local-m2.gradle` with `mavenLocal()` for group `org.robolectric`. Not part of the repo.
- Decompiling third-party APKs is blocked by the permission system; reading resources/manifest is fine.

---

## 5. Key decisions (summary — full list in feature-spec)

Free, closed source; no internet permission; on-device only (YAMNet/LiteRT; personal model on device); clean break from any earlier app — no references to it anywhere; name SnoreSafe, store title "SnoreSafe: Private Snore Tracker"; Android first; minSdk 24; phones only; donations via store IAP only (no external links/codes); wellness wording only (never "diagnose/detect apnea/treat"); English + AI-translated EU languages + Arabic at launch; no alarm, no soundscapes, no articles; dark night-first theme; privacy policy on GitHub Pages; encrypted local backup only (Android Auto Backup off).

## 6. Owner to-dos

- Test latest APK on phone; merge PR #2.
- Decide signed-AAB route; create Play upload key.
- Enable GitHub Pages for the privacy policy.
- Play Console: app record, tip products, data safety, health declaration, closed-test track, 12+ testers.
- Trademark check.
- (When iOS resumes) Apple Developer Program + App Store Connect API key.
