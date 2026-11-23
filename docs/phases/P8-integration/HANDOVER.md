# P8 — Integration and v1.0: Handover

| | |
|---|---|
| **Status** | In progress |
| **Spec** | [SPEC.md](SPEC.md), version v0.2 |
| **Branch** | `phase/p8-integration` |
| **Last session** | 2026-11-23 |

This document tracks the phase as it's built. Update it at the end of every session, so the next one can continue from here.

## Prompt to start or continue

Paste this prompt into a new Claude Code session to start or continue the phase. When the phase is done, replace it with "Phase done: no prompt needed."

```text
Start or continue phase P8 (Integration and v1.0) of the pills reminder.

1. Use the phase-start skill for P8. Read only docs/phases/P8-integration/HANDOVER.md and SPEC.md; they contain everything needed.
2. Carry on from "Next steps" in the handover. Work through the tasks in SPEC.md §7 in order, on branch phase/p8-integration. Commit after each task, once the build passes.
3. Follow SPEC.md. If something in it is wrong or unclear, stop and ask me before changing course. Record every decision or deviation in the handover.
4. P8 is mostly checks on devices. T1 and T2 need the emulator, and T2 the phone too; T4–T8 need the phone, and me with it for the reminder runs, the restart, the ring modes and the day and night of real use. Ask before changing anything on the phone, and never change its clock or time zone. The keystore and its passwords never go in the repo or in your output (SPEC §4.3.3, §5). ACCEPTANCE.md is public: times and conditions only, no treatment content from real use. Tag and release only after I've merged the pull request and said go (T10).
5. Before the session ends, or once T1–T9 are done, use the handover skill. When the done criteria before the pull request pass, open the pull request and wait for me to merge it.
```

## Current state

T1–T4 are done, and T5 nearly: A1, A9 and A12 pass, recorded in `ACCEPTANCE.md`; A9's take at 19:00 on the emulator is still to record. The release build is installed on the phone and on the emulator, each with the reminder check passing. Next: the screens on the phone (T6), then its reminder runs (T7), both with the user.

## Next steps

1. Record A9's 19:00 take (the emulator: `pkg=$release_pkg alert_events`), then delete the A9 treatment from the emulator's release app (it alerts every hour for 7 days).
2. T6 with the user: A2, A3, A4 and A10 on the phone, with the release build. Before T7, ask the user to charge the phone (23% at 18:30), and to delete the debug app's test treatments (one still alerts: `NOTIFICATION_INTERRUPTION` at 18:00:13).

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | Done | Every item of SPEC §4.3.1 in place (see "Wiring status" below); no gap to wire. `EndAndDeleteTest` scrolls back to "Active" before asserting it: passes at font scale 1.0 and 1.4 on the emulator. `build` passes (`:app` 257, `:core` 177 unit tests); the instrumented suite passes on the emulator (48 tests). |
| T2 | Done | `EndToEndTest`, as SPEC §4.3.2: first takes yesterday at the suggested time, every 8 hours, so the latest take is Due at once. Passed on its first run on the emulator (25 s) and the phone (10 s), leaving no reminder, alarm or treatment behind. On the phone, one `notification_alert` played for the two reminders due together (`RM-4`'s single sound). The reminder's `contentIntent.send()` from the test opened the Take screen on Android 16 too, with no background-start option. Each wait has a name, so a failure says which step stopped. Deliberate bugs, on the emulator, all caught (see session log). The whole suite passes on the emulator and the phone (49 tests). |
| T3 | Done | Keystore created (below), signing in `app/build.gradle.kts`, `versionName` 1.0.0; CLAUDE.md's release commands. The APK checked (below). The user backed up the keystore and the properties (2026-11-23). |
| T4 | Done | On the phone, two records of each alert, both filtered to the app's package. `logcat -b events` (`alert_events`, with `pkg=$release_pkg`): 2 MiB, reaching back about 27.5 hours in the user's ordinary use (oldest line 22 Nov 14:20, read 23 Nov 17:46), with milliseconds, sound and vibration. `dumpsys usagestats`: a `NOTIFICATION_INTERRUPTION` event per alert, with the package and the channel, to the second, kept for days (each appears twice). A11 reads its alert times from `usagestats`, and their sound and vibration from the events buffer while it reaches back. |
| T5 | Doing | `ACCEPTANCE.md` started: devices, settings, the 48 rows. A9 on the emulator (install, first launch and the check, a first take in the past, `TIME_SET`, `TIMEZONE_CHANGED`, no logging, a repeat 0.24 s late); its 19:00 take still to record. A1 and A12 on the phone pass: the user followed the check; HyperOS's battery saver at No restrictions, Autostart on (Android then reports no 10008 entry, "No operations.", rather than `allow`), the notification steps confirmed. |
| T6 | To do | A2, A3, A4, A10 (phone) |
| T7 | To do | A5–A8 (phone) |
| T8 | To do | A11: a day and a night of real use |
| T9 | To do | `ACCEPTANCE.md`, README, CLAUDE.md; pull request |
| T10 | To do | After the merge, with the go-ahead: tag `v1.0.0` and the GitHub release |

## Decisions and deviations

Anything decided during implementation that isn't in SPEC.md, or that changes it. A change to a requirement goes into SPEC.md or SPECIFICATION.md first.

| Date | Decision | Reason |
|---|---|---|
| 2026-11-23 | AndroidX's `androidx.profileinstaller.ProfileInstallReceiver` is removed from the merged manifest (`tools:node="remove"` in `app/src/main/AndroidManifest.xml`, with `tools:ignore="MissingClass"`, since lint reads the compile classpath, where that runtime-only class isn't). Decided by the user. | T3 found it in the release APK, exported and guarded by `android.permission.DUMP`, in every build since P1; plan §3 and SPEC §4.3.3 allow only the launcher activity to be exported. It only serves adb and benchmark tools: AndroidX still installs its profiles when the app starts. |
| 2026-11-23 | The release APK is signed with the v2 scheme only (AGP's default for `minSdk` 33). | SPEC §4.3.3 asks for v2 or v3. Android 13 and later verify v2. |

## Known issues

- From earlier phases: AGP's Gradle 10 deprecation notice and ktlint's `sun.misc.Unsafe` warnings on JDK 25 are harmless (P1).
- The emulator's debug app keeps two notification categories from P5's probes ("Probe reminders" and `reminders-mutant`) and a `files/probe.log`. The release build, installed fresh, has none of them.
- Android 16 may time a notification out after 72 hours (P5); not checked, out of scope (SPEC §3).

## Pending wiring

None left for later phases: P8 is the last. What earlier phases left for P8 is in SPEC §4.3.1.

### Release build (T3, 2026-11-23)

- **Keystore:** `%USERPROFILE%\.android\keystores\pills-reminder-release.jks`, PKCS12, alias `pills-reminder`, RSA 4096, SHA384withRSA, `CN=Pills Reminder`, valid from 23 Nov 2026 to 23 Nov 2056. Created by a scratchpad script that passed a random password to `keytool` through `-storepass:env` and wrote it only to the user-level `gradle.properties`, with the path and the alias; the password was never printed, and isn't in the build's log or outputs (checked).
- **Certificate SHA-256 digest:** `d28df13a9531debbba7c7d73eb2305121cea03bce885157ae533b4c4a263a5e7` (public: it is in every signed APK).
- **APK** (`gradle assembleRelease`, 25 MB, not minified): `apksigner` verifies it, v2 scheme, one signer. Its manifest: `io.github.davamix.pillsreminder`, `versionName` 1.0.0, `versionCode` 1, `minSdk` 33, `targetSdk` 37; no `debuggable`, no `testOnly`; `allowBackup="false"` with the data extraction rules; permissions `POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and AndroidX's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature level); components `MainActivity` (the only one exported), the three receivers (not exported) and AndroidX Startup's `InitializationProvider` (not exported); no `DebugActivity`, no `android:process`. Exported before the fix: AndroidX's `ProfileInstallReceiver` (see "Decisions and deviations").
- **Release runtime dependencies:** groups `androidx.*`, `org.jetbrains.*`, `com.google.guava`, and the libraries AndroidX pulls in, `com.squareup.okio` and `org.jspecify`. Nothing for accounts, analytics or ads (NF-3).

### Wiring status (T1, 2026-11-23)

Each item of SPEC §4.3.1, read in the code on `main` at `80adaa0`:

| Item | Found |
|---|---|
| The real repository everywhere | `ScreenDependencies.of(container)` passes `container.repository` (`DataStoreTreatmentRepository`) and `container.checkStore` to every screen; `rememberAppDependencies()` is `AppNavHost`'s default. In `app/src/main`, `InMemoryTreatmentRepository` appears only in comments, and `InMemoryCheckStore` only where it is defined (`CheckStore.kt`, for tests). |
| A tapped reminder opens the Take screen | `ReminderIntents.openTake` → `MainActivity.takeOf` → `AppNavHost(openTake)` → `OpenTake`, which navigates to the real `TakeRoute` destination, above the editors when they are open. |
| Answers in the app remove their notification | `Reminders.start()` collects `repository.data` and reconciles after every value (P5-D2). |
| Home shows answers from a notification | `HomeViewModel`'s rows are `combine(deps.repository.data, deps.minuteTicks, marks)`. |
| Minutes in debug builds only | `allowMinutes = BuildConfig.DEBUG` in `ScreenDependencies.of`. (`core.actions.saveTreatment` validates with `allowMinutes = true`, as a safety net: the editor's own validation is the one that refuses minutes.) |
| No placeholder screen | Every route of `Routes.kt` has its real screen in `AppNavHost.kt` and `EditorGraph.kt`. |
| The lost-sound fix | `Reconciler` counts its own posts as in the shade until the shade lists them, or for `POSTED_GRACE`. |
| `CurrentZoneClock` in a running app | Left for A9, on the release build. |

## How to verify

- `./gradlew build`, then the unit test totals (CLAUDE.md, "Commands").
- `source .claude/skills/phone/helpers.sh emulator && gradle installDebug installDebugAndroidTest && instrument`, then the same on the phone.
- From T3: `gradle assembleRelease`, then `apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk`.
- From T5: `ACCEPTANCE.md`, row by row.

## Session log

Newest first.

### 2026-11-23 (2)

- Done: SPEC v0.2 agreed (every draft assumption), the phase marked `In progress`, the branch created. T1: the wiring confirmed, `EndAndDeleteTest` fixed. T2: `EndToEndTest`.
- T2's deliberate bugs (`deliberate-bugs`, on the emulator), each caught by `EndToEndTest`, at the step named:
  - `no-reconcile-on-change` (`Reminders.start()` reconciles only the first value): "both reminders are in the shade" timed out.
  - `receiver-ignores-taken` (`ReminderActionReceiver` only reconciles on Taken): "Amoxicillin's reminder leaves the shade" timed out.
  - `no-take-from-intent` (`takeOf` reads an empty intent): "the Take screen opens" timed out.
  - `home-first-data-only` (Home's rows `combine` the data's first value only): "Home shows Amoxicillin · 1 capsule with Example · 1 of 21" timed out.
  - `every-post-silent` (every reminder posted with `GROUP_ALERT_SUMMARY`): "Amoxicillin: 1 capsule alerts expected 2 but was 1".
- T4, a first look (read only, on the phone): the events buffer is 2 MiB, and at 17:46 its oldest line was from 14:20 the day before, about 27.5 hours, in the user's ordinary use. T4 checks it again after a night.
- Notes: the phone was on wireless debugging only, and the emulator running.

### 2026-11-23

- Done: SPEC.md v0.1 and this handover written (`phase-docs`), from plan v0.9 and specification v0.11, with what P1–P7's handovers left for P8.
- Notes: the phase hasn't started; its SPEC is waiting for review.
