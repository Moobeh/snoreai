# SnoreSafe — Feature Spec (draft)

Name: **SnoreSafe** (store title "SnoreSafe: Private Snore Tracker"). No conflict found in a web search; formal UKIPO / EUIPO / USPTO search pending before launch. App ID: `com.moobehgames.snoresafe`. Publisher: moobehgames.

Principles: free, no ads, no accounts, no data collection, no network access, all analysis on device, user owns and controls every byte.

Status legend: **Settled** = agreed in design review · **Open** = still being decided.

---

## 1. Platform & stack — Settled

- Kotlin Multiplatform: shared core (audio pipeline, ML, storage, stats); native UI (Jetpack Compose on Android, SwiftUI on iOS).
- Android first. iOS second.
- Phones only (no tablet layouts, no watch) for v1.
- Android minSdk 24, target/compile SDK 36 (same as the other moobehgames app). Features needing newer APIs (e.g. Health Connect, API 26+) are hidden on older devices.
- Closed source. Free to use.
- No `INTERNET` permission (subject to the donation mechanism, see §11).

## 2. Recording session — Settled

- Start now or after a delay; pre-sleep screen.
- Foreground microphone service that survives overnight.
- Pause / preview, auto-resume, delay after resume; resume stopped session; merge with previous.
- Min / max session length; adjust time-in-bed afterwards; late sessions count toward the previous night.
- Reliability: battery-optimisation exemption, step-by-step guides for each phone maker's battery killers, gap detection + "monitoring interrupted X–Y" in the morning report, low battery / low storage warnings, charger reminder.
- Placement guidance, face-down screen-off (proximity sensor), night clock while charging.
- Pauses other audio apps while recording.
- Home-screen shortcut / quick-settings tile to start a session.
- Bedtime reminder notification at a user-set time (off by default; no exact-alarm permission).
- No alarm, no smart alarm.
- Not included: torch.

## 3. Detection & audio (on-device AI) — Settled

- Engine: LiteRT on Android, Core ML on iOS. No remote AI.
- Base sound classifier: YAMNet (Apache-2.0), which already includes snoring and other night sounds. No central fine-tuning for v1.
- Snore intensity from calibrated loudness × duration.
- **Personal model:** user marks clips "snoring" / "not snoring"; the classifier retrains on the phone and adapts to that bedroom.
- Experimental: breathing rate, breathing pauses. Never a diagnosis; long pauses show a "consider seeing a doctor" note.
- Sensitivity control, per-session or default volume offset, light-snoring filter, device calibration.
- Recording modes: snore clips only · all snoring (default) · full night (storage cost shown before enabling).
- Audio encrypted at rest.
- Pin / favourite / name clips.

## 4. Night results — Settled

- Snore Score; quiet / light / loud / epic bands with %.
- Time in bed, monitored time, time snoring, snoring %, peak volume, pauses, interruptions.
- Intensity timeline; tap to play.
- Comparison with the user's own history (never with other users).
- Remedies & factors used, notes, weight, wake-up rest rating.
- Clip list: filter, sort, grid/list, multi-select, bulk delete.

## 5. Remedies & factors — Settled

- Built-in list (devices, sleeping position, nasal aids, lifestyle factors such as alcohol, late meals, caffeine, blocked nose, illness) + custom entries. All descriptions written fresh.
- Trends by remedy / factor: average score with vs without.

## 6. Trends & history — Settled

- Calendar, period filters, interactive charts, averages, change vs previous period, weight tracking.

## 7. Questionnaires — Settled

- Own risk questionnaire built on public risk factors (age, sex, BMI, neck size, blood pressure, observed pauses). Marked "not a diagnosis".
- Own "which remedies might suit you" quiz with fresh scoring.
- No licensed third-party scales.

## 8. Achievements — Settled

- Local notifications only: lowest score, monthly summary, session milestones. Each switchable off.

## 9. Health integration — Settled

- Optional, off by default. Health Connect (Android) / HealthKit (iOS): sleep sessions and weight. Sleep stages, heart rate and SpO₂ are imported from these sources only, never estimated from audio.

## 10. Data, export, backup — Settled

- Encrypted export / import to a file or folder the user picks (system file picker). Android Auto Backup off.
- CSV / JSON data export.
- PDF night or trend report + selected clips via share sheet, for a doctor or dentist. Free.
- Storage manager: storage limit, delete old audio (keep N recent sessions, N clips for older), protected sessions.

## 11. Donations — Settled

- Store tip jar branded "Buy me a coffee": three one-off tip sizes (£1.99 "a coffee" / £4.99 "a lunch" / £9.99 "a big thank-you", auto-converted per country) via Play Billing / App Store in-app purchase. No perks.
- Hidden automatically after any tip (purchase state is read on the device). "I've already supported you" button also hides it without paying.
- One small card, shown only at good moments (7th night, new lowest score, successful export), at most once a month, never during recording or onboarding. Small "Support" entry in settings until a tip is made.
- No codes, no external payment links.
- To confirm in the first build: Play Billing works with no `INTERNET` permission.

## 12. Profile & settings — Settled

- Optional: age, sex, height, weight, neck size (metric / imperial). Kept on the device only. Skipping never blocks a feature.
- Preferences, audio, storage, permissions explanations, bundled FAQ, medical disclaimer, privacy statement.
- Onboarding, results tutorial.

## 13. Localisation — Settled

- English primary. AI-translated EU languages + Arabic at launch. Right-to-left layout supported from day one.

## 14. Not included

Accounts, cloud sync, ads, analytics, crash-reporting SDKs (crashes are stored locally; user can review and email a report), advertising ID, attribution, remote content, sponsored or affiliate content, cross-promotion, rating nags, paywalls, usage limits, articles (for now), soundscapes, alarm.

## 15. Design — Settled

Dark, low-glare Material 3 theme at night and while recording; system light/dark elsewhere. New brand identity.

## 16. Release — Settled

Play internal test → closed test (12+ testers, 14+ days, as required for this developer account) → production. iOS follows Android.

## 17. Compliance — Settled

- Wellness positioning only: never "diagnose", "detect apnea" or "treat"; use "track", "understand", "see what helps". First-run disclaimer + FAQ entry. Play health-apps declaration: wellness.
- Privacy policy on GitHub Pages ("nothing collected"). Play data safety form: no data collected or shared.

## 18. Release scope — Settled

- **v1 (Android):** recording, detection + on-device personalisation, night results, remedies & factors, trends, export / backup, PDF report, storage manager, bedtime reminder, tip jar, English + EU languages + Arabic.
- **v1.1:** questionnaires, achievements, Health Connect, experimental breathing rate & pauses.
- **v2:** iOS.

## 19. Quality bar — Settled

Modular architecture, CI, automated tests, reproducible builds, accessibility (WCAG), localisation-ready, long-term maintainability.
