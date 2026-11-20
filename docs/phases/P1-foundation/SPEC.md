# P1 — Project foundation: Specification

| | |
|---|---|
| **Status** | Ready, v0.2 |
| **Phase** | P1, see [Implementation plan §5](../../IMPLEMENTATION_PLAN.md#5-phases) |
| **Depends on** | — |
| **Branch** | `phase/p1-foundation` |
| **Handover** | [HANDOVER.md](HANDOVER.md) |

This document holds everything needed to implement the phase. Working on it shouldn't require reading the full specification or plan.

## 1. Goal

An empty but complete app skeleton that builds, tests and installs on the emulator and the phone, with CI. Every later phase only adds code to the structure created here:

- the two modules, `:core` (pure Kotlin) and `:app` (Android);
- the theme, light and dark;
- a placeholder for each screen, wired into navigation;
- the checks, including the manifest's permission allowlist.

It also finishes the `phone` skill: the helpers that need an installed app are run for the first time, and the app's content area is measured on both devices.

## 2. Requirements covered

Quoted from [SPECIFICATION.md](../../SPECIFICATION.md) v0.4.

| ID | Requirement (quoted) | What this phase does for it |
|---|---|---|
| NF-3 | "**Offline and private:** no network permission, no accounts, no analytics, no ads. Treatment data stays on the phone. Android's cloud backup is turned off." | No `INTERNET` permission; a build check fails on any permission outside the allowlist (§5). Auto Backup is disabled, and the data extraction rules exclude everything. No analytics or ads libraries. |
| NF-6 | "**Compatibility:** runs on Android 13 (API 33) on the target phone, and keeps working if the phone is updated to a later Android version. The lowest version supported is set in the implementation plan." | `minSdk` 33 (plan `D-11`). The app is installed and run on the phone (API 33) and on the emulator (API 36). |
| NF-7 | "**Phone layout:** portrait only, for a screen about 393 dp wide. Touch targets are at least 48 dp. Text stays usable with the system's larger font sizes." | The activity is locked to portrait. The content area is measured on both devices (T8). Touch targets and font sizes apply to the real screens in P6 and P7. |
| NF-8 | "**Language and formats:** the UI is in English. All user-facing text lives in resource files, so a Spanish translation can be added later without code changes. Dates and times follow the phone's settings, such as its 24-hour clock." | Foundation: every text is in resources, one `strings_<feature>.xml` per feature (plan `D-14`). Dates and times come with the real screens. |
| NF-9 | "**Distribution:** installed as an APK from the development machine (`adb` or Android Studio). It is never published on Google Play." | `gradlew installDebug` installs on both devices, and the project opens in the installed Android Studio. |
| NF-10 | "**Theme:** follows the system's light or dark setting." | A Material 3 theme with a light and a dark colour scheme, chosen from the system setting. |

## 3. Scope

### In scope

- The Gradle project: wrapper, version catalog, and the `:core` and `:app` modules.
- The app skeleton: application class, `AppContainer`, single activity, theme, launcher icon, strings.
- Navigation with a placeholder for each of the seven screens in specification §6.
- Checks: Android Lint, ktlint, and the permission allowlist.
- An instrumented smoke test.
- GitHub Actions CI.
- The `phone` skill: run the helpers that need the app, measure the content area, and fix whatever doesn't work.
- `CLAUDE.md`: commands, code structure and conventions.

### Out of scope

| What | Phase |
|---|---|
| Model, schedule, take states, repository interface | P2 |
| Storage | P3 |
| Reminder planner | P4 |
| Alarms, notifications, receivers, the reminder permissions in the manifest, debug tools | P5 |
| Real treatment screens, replacing their placeholders | P6 |
| Real home and settings screens, the reminder check | P7 |
| Release signing, `v1.0.0` | P8 |

## 4. Design

### 4.1 Packages and files owned

The base package is `io.github.davamix.pillsreminder`.

```
settings.gradle.kts, build.gradle.kts, gradle.properties
gradle/libs.versions.toml, gradle/wrapper/*, gradlew, gradlew.bat
.github/workflows/ci.yml
CLAUDE.md                                         commands, code structure, conventions
.claude/skills/phone/SKILL.md, helpers.sh         fixes, and the measured content area
core/build.gradle.kts                             :core, no sources yet
app/build.gradle.kts                              includes the permission allowlist check
app/src/main/AndroidManifest.xml
app/src/main/kotlin/io/github/davamix/pillsreminder/
    PillsReminderApp.kt                           Application; creates the AppContainer
    MainActivity.kt
    di/AppContainer.kt
    navigation/Routes.kt, AppNavHost.kt
    ui/theme/Color.kt, Theme.kt, Type.kt
    ui/common/PlaceholderScreen.kt                removed once the last placeholder goes
    ui/home/HomeScreen.kt                         placeholder, replaced by P7
    ui/treatments/TreatmentsScreen.kt             placeholder, replaced by P6
    ui/treatment/TreatmentScreen.kt               placeholder, replaced by P6
    ui/editor/TreatmentEditorScreen.kt            placeholder, replaced by P6
    ui/editor/MedicineEditorScreen.kt             placeholder, replaced by P6
    ui/take/TakeScreen.kt                         placeholder, replaced by P6
    ui/settings/SettingsScreen.kt                 placeholder, replaced by P7
app/src/main/res/values/strings.xml               app name and shared text
app/src/main/res/values/strings_<feature>.xml     home, treatments, treatment, editor, take, settings
app/src/main/res/values/themes.xml                the window theme, light
app/src/main/res/values-night/themes.xml          the window theme, dark
app/src/main/res/xml/data_extraction_rules.xml
app/src/main/res/drawable/ic_launcher_foreground.xml, ic_launcher_monochrome.xml
app/src/main/res/mipmap-anydpi/ic_launcher.xml, ic_launcher_round.xml
app/src/debug/res/values/strings.xml              debug app name
app/src/androidTest/kotlin/io/github/davamix/pillsreminder/NavigationSmokeTest.kt
```

`.editorconfig`, `.gitattributes` and `.gitignore` already exist, copied from Workout Timer. Check them, and change them only if the build needs it.

**Shared files** that later phases change: the version catalog, the module build files, the manifest, `AppContainer`, `Routes`/`AppNavHost`, and `CLAUDE.md`. Each later phase only adds or changes its own entries. Each phase that replaces a placeholder owns that screen's route, and can change its arguments.

### 4.2 Interfaces and data

- **`PillsReminderApp : Application`** exposes `container: AppContainer`, created in `onCreate`.
- **`AppContainer(context: Context)`** holds the app's long-lived objects. It is empty in P1; P3 adds the repository, and P5 the reminders.
- **Routes** are type-safe Navigation Compose routes, made `@Serializable`:
  - `HomeRoute`: the start destination.
  - `TreatmentsRoute`
  - `TreatmentRoute(treatmentId: String)`
  - `TreatmentEditorRoute(treatmentId: String? = null)`: `null` means a new treatment.
  - `MedicineEditorRoute(medicineId: String? = null)`: `null` means a new medicine. P6 decides how it shares the treatment editor's unsaved state.
  - `TakeRoute(medicineId: String, plannedAt: String)`: `plannedAt` is the take's planned local date-time in ISO-8601 form. P5 opens it from a notification (spec `RM-6`).
  - `SettingsRoute`
- **Theme:** `PillsReminderTheme { }` picks a Material 3 light or dark colour scheme from `isSystemInDarkTheme()` (Q1).
- **Activity:** `MainActivity` calls `enableEdgeToEdge()` before `setContent`, and screens apply the system bar insets through `Scaffold` (P1-D4).

### 4.3 Behaviour

The placeholder screens show their name, plus buttons for the navigation they will have later. This keeps the whole navigation graph testable now:

| Screen | Placeholder buttons |
|---|---|
| Home | "Treatments" → treatments; "New treatment" → treatment editor (new); "Settings" → settings; "Sample take" → take, for medicine `sample` at `2026-11-23T16:00` |
| Treatments | "Open sample" → treatment `sample`; "New treatment" → treatment editor (new) |
| Treatment | "Edit" → treatment editor for `sample`; "Open take" → the sample take |
| Treatment editor | "Add medicine" → medicine editor (new) |
| Medicine editor | — |
| Take | — |
| Settings | — |

Every placeholder except Home has a top bar with a back arrow. The system Back gesture works as normal everywhere. A click that navigates goes through `dropUnlessResumed`, so a double tap can't open a screen twice (a Workout Timer convention).

Placeholder texts are suggestions: each screen's strings file holds the final wording.

### 4.4 Dependencies

Start from the versions Workout Timer pinned on 2026-11-02, on this same machine. In T1, check each for a newer stable release, and take it if it works with the installed Android Studio. Record what changed in the handover.

| Item | Version | Notes |
|---|---|---|
| Gradle | 9.8.2 | `-bin` distribution, pinned with its SHA-256 |
| Android Gradle Plugin | 9.3.3 | The newest the installed Android Studio 2026.1 supports, in Workout Timer (plan `D-13`). Needs Gradle 9.5 or later. |
| Kotlin | 2.4.21 | AGP's built-in Kotlin, so no `kotlin-android` plugin. Adds the Compose compiler and serialization plugins. |
| Compose BOM | 2026.09.00 | ui, material3, tooling, ui-test |
| activity-compose | 1.13.0 | `enableEdgeToEdge()` |
| navigation-compose | 2.10.2 | type-safe routes |
| lifecycle | 2.11.0 | runtime-compose (`dropUnlessResumed`), viewmodel-compose |
| kotlinx-serialization | 1.12.0 | the routes; P3 uses the JSON part |
| JUnit | 6.1.3 (BOM) | Jupiter, for unit tests in both modules, from P2 on |
| AndroidX Test | ext-junit 1.3.0, runner 1.7.0, Espresso 3.7.0 | instrumented tests |
| ktlint Gradle plugin | 14.2.0 | supports AGP 9's built-in Kotlin |

The catalog also declares what later phases will need, so parallel phases don't edit the same lines: coroutines 1.11.0 (core and test), DataStore 1.3.0 (`androidx.datastore:datastore`, the typed one, for P3, plan `D-4`), and kotlinx-serialization-json.

**SDK levels:** `minSdk` 33 (plan `D-11`). `compileSdk` and `targetSdk` 37, the newest platform installed (`android-37.0`), if AGP 9.3.3 supports it; otherwise the highest it does (P1-D8).

**JVM:** bytecode targets Java 17. The build runs on Android Studio's JDK (JBR 25) locally, and on Temurin 25 in CI.

**Pitfalls from Workout Timer's P1:**

- `gradlew` must be marked executable in git (`git update-index --chmod=+x gradlew`). Windows doesn't record the bit, and CI on Linux needs it.
- Compose UI tests use the rules from `androidx.compose.ui.test.junit4.v2`. The v1 rules are deprecated in Compose BOM 2026.09.00.
- `pressBack()` in a smoke test needs Espresso core.
- AGP 9.3.3 prints a Gradle 10 deprecation notice. It comes from AGP itself, not from the build scripts, and is harmless.
- ktlint prints `sun.misc.Unsafe` warnings on JDK 25. They are harmless; CLAUDE.md will show how to filter them.
- Workout Timer needed `res/xml/backup_rules.xml` and `android:fullBackupContent` because Android 8–11 ignore `dataExtractionRules`. With `minSdk` 33 they shouldn't be needed. Add them only if Lint asks.

### 4.5 Decisions made in this phase

| ID | Decision | Reason |
|---|---|---|
| P1-D1 | The application ID and namespace are `io.github.davamix.pillsreminder`. | The convention for a project hosted on GitHub, and what the `phone` skill already assumes (plan §2.1). It can't easily change once installed. |
| P1-D2 | Debug builds use the application ID suffix `.debug` and the name "Pills Reminder (debug)". | Debug and release builds can then be installed side by side, each with its own data (P8). |
| P1-D3 | A fixed colour scheme of the app's own, light and dark, with a teal main colour and no dynamic colour from the wallpaper (Q1). The launcher icon is a simple capsule in the main colour, on a light background, with a monochrome layer for themed icons (Q2). | The highlight of due takes (spec `HM-2`) keeps the same, checked contrast whatever the wallpaper. Screenshots and tests stay stable. |
| P1-D4 | The app draws edge to edge on every Android version (`enableEdgeToEdge()`), and screens keep their content out of the system bars with `Scaffold` insets. | With `targetSdk` 35 or later, Android 15 and later force edge-to-edge: the emulator (API 36) would draw under the bars and the phone (API 33) wouldn't. Doing it everywhere makes both look the same. |
| P1-D5 | Lint stops the build on errors; warnings are reported. The warnings about locking the orientation are suppressed on purpose, with a comment naming `NF-7`. | Strict, but no noise from a decision already made |
| P1-D6 | A **permission allowlist check** replaces Workout Timer's no-INTERNET check. A Gradle task reads the merged manifest of every variant, and fails `check` if it declares any permission outside the list in plan §3. `INTERNET` gets its own message, naming `NF-3`. The list already holds P5's permissions (`POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), so P5 needn't change the check. | A library can add a permission silently. An allowlist catches every one, not only `INTERNET`. Allowing a permission doesn't declare it. |
| P1-D7 | Instrumented tests use sentence names in backticks, like the unit tests. | D8 accepts spaces in method names when `minSdk` is 30 or more; Workout Timer couldn't, at 26. The smoke test checks it (T6). If D8 refuses, use camelCase names, and record it. To run a single test whose name has spaces, run its class instead (`instrument <Class>`), because `am instrument -e class Class#method` splits at the spaces. |
| P1-D8 | `compileSdk` and `targetSdk` 37, if AGP supports it. | The newest platform installed. The target level changes some behaviours on newer Android versions, and they are best met from the start. |

## 5. Security and privacy

- **Manifest:**
  - no `INTERNET` permission, and in P1 no permission at all of the app's own;
  - `android:allowBackup="false"`, plus data extraction rules that exclude everything from cloud backup and device transfer (plan `D-12`);
  - only the launcher activity is exported.
- **Permission allowlist check** (P1-D6). The allowed permissions are those in plan §3: `POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, and the ones AndroidX adds that give access to nothing, such as `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. List in the handover every permission the merged manifests actually hold.
- **CI:**
  - `permissions: contents: read`, and no secrets;
  - every action pinned to a commit SHA. Workout Timer's CI pins these, which can be reused: `actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1` (v7.0.1), `actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad` (v6.1.0), `gradle/actions/setup-gradle@8da46b539cd8fe6ab27ca75128919a18d757604c` (v6.4.1), `actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9` (v7.0.2). Check each for a newer release.
  - `setup-gradle` validates the Gradle wrapper.
- **Gradle wrapper:** pinned with `distributionSha256Sum`.
- **The repo is public.** No serial number, name or other personal data in code, logs, the handover or the skill. Write "the phone", not its serial. Screenshots meant for the repo come from the emulator.

## 6. Testing

| Test | Type | Covers |
|---|---|---|
| `NavigationSmokeTest`: the app starts on Home; each placeholder opens from its button, and Back returns | UI (instrumented) | The navigation skeleton |
| `NavigationSmokeTest`: the activity requests portrait | UI (instrumented) | `NF-7` |
| `NavigationSmokeTest`: test names with spaces compile and run | UI (instrumented) | P1-D7 |
| Permission allowlist check: passes as is; fails when `INTERNET` is added temporarily, and when another permission is | Build check, tried once by hand | `NF-3` |
| Every instrumented test passes on the emulator and the phone | UI (instrumented) | `NF-6` |
| CI run on the pull request | CI | Build, lint, ktlint, the allowlist check |

### Manual checks

On the **emulator**:

1. Switch to dark mode (`$adb shell cmd uimode night yes`) and back (`no`): the app follows, with readable text in both.
2. Rotate the screen: the app stays in portrait.
3. No content is drawn under the status bar or the navigation bar.

On the **phone**:

1. `gradlew installDebug` → "Pills Reminder (debug)" appears in the launcher, with its icon.
2. Open it → it shows in portrait. Turn the phone sideways → it stays in portrait.
3. Move through all the placeholder screens, with their buttons and with the system Back gesture.
4. No content is drawn under the status bar or the navigation bar.
5. Dark mode on the phone only if the user agrees to switch it, and switches it back.

## 7. Tasks

| ID | Task |
|---|---|
| T1 | Gradle project: wrapper with checksum, `settings.gradle.kts`, root build, `gradle.properties`, version catalog. Check for newer stable versions (§4.4). Mark `gradlew` executable in git. |
| T2 | `:core` module: Kotlin JVM, Java 17 target, JUnit Jupiter set up |
| T3 | `:app` module: build file, manifest, application class, `AppContainer`, theme (light and dark), edge-to-edge activity, launcher icon with a monochrome layer, strings |
| T4 | Navigation: routes, `AppNavHost`, placeholder screens |
| T5 | Checks: lint options, ktlint, and the permission allowlist task wired into `check`. Try it: it must fail with `INTERNET`, and with another permission added. |
| T6 | `NavigationSmokeTest`, with sentence names (P1-D7). Every instrumented test passes on the emulator and the phone. |
| T7 | CI workflow |
| T8 | `phone` skill: run `gradle`, `instrument`, `launch`, `force_stop` and `end_process` on both devices and fix what fails. Measure the app's content area on both, with a throwaway probe that logs the window size and the system bar insets in dp. Note the first launch time after installing. Record all of it in the skill. |
| T9 | `CLAUDE.md`: the commands table, the code structure and the conventions. Carry over the Workout Timer lessons that apply: Gradle's exit code through pipes, `ktlintFormat` as its own command, the test result one-liners, characters not allowed in test names, coroutine tests, and the Compose UI test pitfalls. |
| T10 | Manual checks on the emulator and the phone; pull request with CI green |

## 8. Done criteria

- [ ] All tasks done
- [ ] `gradlew build` passes locally and in CI
- [ ] Every instrumented test passes on the emulator and the phone
- [ ] Manual checks pass on the devices they name
- [ ] The `phone` skill's helpers all work, and the content area is recorded in it
- [ ] HANDOVER.md is up to date, and the plan's status table is updated

## 9. Open questions

None at the moment. New questions are numbered from Q3.

### Answered

| # | Question | Decision |
|---|---|---|
| Q1 | Colours | A fixed scheme of the app's own, light and dark, with a teal main colour. No dynamic colour. (P1-D3) |
| Q2 | Launcher icon | A simple capsule in the main colour, on a light background, with a monochrome layer for themed icons (P1-D3). |

## 10. Change log

| Version | Date | Changes |
|---|---|---|
| 0.1 | 2026-11-20 | First draft |
| 0.2 | 2026-11-20 | Review: Q1 and Q2 answered, confirming the draft assumptions (P1-D3). Ready. |
