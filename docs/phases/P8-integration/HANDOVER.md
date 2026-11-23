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

Paused on 2026-11-23 at 19:40, at the user's request (usage limit), to resume after the limit resets. T1–T6 are done: the wiring, `EndToEndTest`, the signed release build, the measurement of T4, and A1, A2, A3, A4, A9, A10 and A12 pass, recorded in `ACCEPTANCE.md`. Two results of T6 are waiting to be read from the phone's records (next steps 1 and 2). T7 (A5–A8, reminder runs with the user) is next; its timetable is proposed below, waiting for the user's choice of start time.

**The phone now:** the release app only (the debug app and its tests were uninstalled with the user's agreement; T9's instrumented run reinstalls them, with an empty store). The release app holds "Example" (Amoxicillin every 12 hours since the edit, its 16:00 take Due until 20:00; Atrovent every 8 hours, its 00:00 take Taken early) and "Weekly" (Finished). Its next alerts: Amoxicillin 20:00 tonight, both at 08:00 tomorrow. The reminder check passes; Autostart on, battery saver No restrictions. Dark mode and font scale put back (light, 1.0).

**The emulator:** the debug app and the release app; the release app's A9 treatment deleted.

## Next steps

1. **A2's `TR-10` and `TK-3`, at 20:00 on 23 Nov:** read whether Amoxicillin's 16:00 take (key `2026-11-23T16:00|b81aa316…`) became Missed and left the shade at 20:00, and the 20:00 take alerted: `pkg=$release_pkg alert_events 10`, and `$adb logcat -d -b events -v time | grep "|$pkg|" | grep notification_cancel` around 20:00 (the events buffer reaches back about a day; `dumpsys usagestats` keeps the alerts longer). Record it in `ACCEPTANCE.md`, A2.
2. **A3's `TK-4`:** confirm no alert for Atrovent's take at 2026-11-24T00:00, Taken early. Atrovent's medicine ID isn't `b81aa316…` (Amoxicillin) nor `2b17ed83…` (Vitamin D): any alert or `NOTIFICATION_INTERRUPTION` near 00:00 on 24 Nov fails it. Record it in A3.
3. **T7 with the user**, A5–A8, at a start hour H the user picks. The proposal shown to the user: a test treatment with two medicines every hour from H:00 (§4.4's names); H:00 both alert, phone in hand (content, heads-up, sound, one sound for two); Snooze one (back at H:10); repeats at H:10, H:20, H:30 in Do Not Disturb, vibrate mode and Silent (put back afterwards); swipe one away at H:12 (back at H:20); H+1:00 screen off and locked (lock-screen content, the unanswered take Missed and removed); H+1:05 swipe the app from Recents; H+1:15 the user restarts the phone and unlocks after H+1:20; H+1:25 `end_process`; H+1:3x Skip and Taken from the notification, Taken in the app; H+2:05 `adb install -r` of the same APK with a reminder in the shade (checks Autostart: `MY_PACKAGE_REPLACED`); H+2:15 the user Force stops the app, H+2:25 opens it (`alarms` at once). End the test treatment early at the end. Lateness from `alert_events` with `pkg=$release_pkg`.
4. **Ask the user about T8** (Q3): their own treatment, or the §4.4 example kept running, for the day and the night of real use.

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | Done | Every item of SPEC §4.3.1 in place (see "Wiring status" below); no gap to wire. `EndAndDeleteTest` scrolls back to "Active" before asserting it: passes at font scale 1.0 and 1.4 on the emulator. `build` passes (`:app` 257, `:core` 177 unit tests); the instrumented suite passes on the emulator (48 tests). |
| T2 | Done | `EndToEndTest`, as SPEC §4.3.2: first takes yesterday at the suggested time, every 8 hours, so the latest take is Due at once. Passed on its first run on the emulator (25 s) and the phone (10 s), leaving no reminder, alarm or treatment behind. On the phone, one `notification_alert` played for the two reminders due together (`RM-4`'s single sound). The reminder's `contentIntent.send()` from the test opened the Take screen on Android 16 too, with no background-start option. Each wait has a name, so a failure says which step stopped. Deliberate bugs, on the emulator, all caught (see session log). The whole suite passes on the emulator and the phone (49 tests). |
| T3 | Done | Keystore created (below), signing in `app/build.gradle.kts`, `versionName` 1.0.0; CLAUDE.md's release commands. The APK checked (below). The user backed up the keystore and the properties (2026-11-23). |
| T4 | Done | On the phone, two records of each alert, both filtered to the app's package. `logcat -b events` (`alert_events`, with `pkg=$release_pkg`): 2 MiB, reaching back about 27.5 hours in the user's ordinary use (oldest line 22 Nov 14:20, read 23 Nov 17:46), with milliseconds, sound and vibration. `dumpsys usagestats`: a `NOTIFICATION_INTERRUPTION` event per alert, with the package and the channel, to the second, kept for days (each appears twice). A11 reads its alert times from `usagestats`, and their sound and vibration from the events buffer while it reaches back. |
| T5 | Done | `ACCEPTANCE.md` started: devices, settings, the 48 rows. A9 on the emulator (install, first launch and the check, a first take in the past, `TIME_SET`, `TIMEZONE_CHANGED`, no logging, a repeat 0.24 s late); its 19:00 take on time, 0.39 s. A1 and A12 on the phone pass: the user followed the check; HyperOS's battery saver at No restrictions, Autostart on (Android then reports no 10008 entry, "No operations.", rather than `allow`), the notification steps confirmed. |
| T6 | Done | A2, A3, A4 and A10 on the phone, recorded. Two results to read from the phone's records: `TR-10`/`TK-3` at 20:00 on 23 Nov, and `TK-4` at 00:00 on 24 Nov (next steps 1 and 2). |
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

### 2026-11-23 (3)

- Done: T3 finished (the user backed up the keystore); T4; T5 (A9 on the emulator; A1 and A12 on the phone); T6 (A2, A3, A4, A10 on the phone). The debug app and its tests uninstalled from the phone, with the user's agreement.
- Notes: on the phone a heads-up stays 6 to 26 s over the app bar (`notification_visibility` in `logcat -b events`), and taps there land on it: two taps on the Take screen's Back icon did nothing for that reason. For the `phone` skill at the skills review, with: `uiautomator dump` works on the phone (it prints a HyperOS stack trace first, harmlessly) and lists the time picker's minutes as `content-desc="5 minutes"`; the medicine editor's text fields include the two read-only unit menus (fields 5 and 7), so "For" is field 6; `dumpsys usagestats` keeps each alert as `NOTIFICATION_INTERRUPTION`, to the second, for days.
- Paused at 19:40 for the usage limit.

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
