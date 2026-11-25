# P8 — Integration and v1.0: Handover

| | |
|---|---|
| **Status** | In progress: T1–T9 done; pull request [#9](https://github.com/davamix/pills-reminder/pull/9) is waiting for the user to merge it, then T10 (the release) with the user's go-ahead |
| **Spec** | [SPEC.md](SPEC.md), version v0.2 |
| **Branch** | `phase/p8-integration` |
| **Last session** | 2026-11-25 |

This document tracks the phase as it's built. Update it at the end of every session, so the next one can continue from here.

## Prompt to start or continue

Paste this prompt into a new Claude Code session to start or continue the phase. When the phase is done, replace it with "Phase done: no prompt needed."

```text
Start or continue phase P8 (Integration and v1.0) of the pills reminder.

1. Use the phase-start skill for P8. Read only docs/phases/P8-integration/HANDOVER.md and SPEC.md; they contain everything needed.
2. Carry on from "Next steps" in the handover. Work through the tasks in SPEC.md §7 in order, on branch phase/p8-integration. Commit after each task, once the build passes.
3. Follow SPEC.md. If something in it is wrong or unclear, stop and ask me before changing course. Record every decision or deviation in the handover.
4. T1–T9 are done and pull request #9 is open. First check whether I've merged it (`gh pr view 9`) or left review comments; deal with comments on this branch. Once it's merged, T10: ask me for the go-ahead, then build the release on `main` at the merge commit, check it, tag `v1.0.0` and create the GitHub release, as SPEC §4.3.3 says, showing me the release notes first; then the phase-close skill. The keystore and its passwords never go in the repo or in your output (SPEC §4.3.3, §5). Never change the phone's clock or time zone.
5. Before the session ends, or once T1–T9 are done, use the handover skill. When the done criteria before the pull request pass, open the pull request and wait for me to merge it.
```

## Current state

T1–T9 are done. Every requirement passes on the phone with the signed release build, as `ACCEPTANCE.md` records, including a day and a night of real use; the late alerts found in deep Doze on 24 Nov are fixed. The build passes (`:core` 177, `:app` 260 unit tests), and the instrumented suite passes on the emulator and the phone (49 tests each) at the final code. Pull request [#9](https://github.com/davamix/pills-reminder/pull/9) is open, and CI passed on its first commit (`87e319e`, run 45682300060). Left: the user's merge, then T10, the `v1.0.0` tag and the GitHub release, with the user's go-ahead.

**The phone:** the release app, with "Example" still running (the user's real-use treatment), "Weekly" and "Hourly" (Finished); the debug app and its tests reinstalled by T9's instrumented run, with an empty store. The phone's ring and notification volume was 2 of 15: the user raises it. **The emulator:** the debug app, and the release app (the current build) with an "Example" made for the README's screenshots.

## Next steps

1. If the pull request has review comments, deal with them on this branch, then run the checks and push.
2. Once the user has merged it, **T10**, only with the user's go-ahead: on `main` at the merge commit, `gradle assembleRelease`; check it with `apksigner` (certificate digest `d28df13a…a5e7`) and its manifest (SPEC §4.3.3); copy it to the scratchpad as `pills-reminder-1.0.0.apk` and take its SHA-256; write the release notes in the scratchpad and show them to the user; `git tag -a v1.0.0 -m "Pills Reminder 1.0.0"` and push the tag; `gh release create v1.0.0 <apk> --title "Pills Reminder 1.0.0" --notes-file <notes>`; then install that APK on the phone over the accepted one (`adb install -r`, the same key, the data kept).
3. Then the `phase-close` skill: P8 `Done` in plan §5.1, SPEC §8 ticked, this handover finished, and the skills review (candidates in the session logs: the `phone` skill should time alerts by `notification_alert`, the heads-up over the app bar, `uiautomator dump`'s minute labels, the editor's field numbers, the transport-id fix to confirm, the README screenshots' crop tool).

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
| T7 | Done | A5–A8 on the phone, 24 Nov 17:00–19:12, with an hourly test treatment, "Hourly" (ended early at the end): every alert within 0.7 s; Do Not Disturb, vibrate and Silent modes; Snooze, swipe, Taken, Skip; swipe from Recents, restart, `end_process`, update (16:45), Force stop. Recorded in `ACCEPTANCE.md`. The 19:05 update of the timetable was dropped: the 16:45 install had already checked it. |
| T8 | Done | A11 on "Example", 24 Nov 19:15 to 25 Nov 19:50: every alert played within 0.45 s of its post, in deep Doze too (the fix holds); first alerts within 2.4 s of their time. The user heard none: the phone's ring and notification volume was 2 of 15. Decided by the user: they raise it; a low-volume warning becomes a future idea (specification v0.12). |
| T9 | In review | `ACCEPTANCE.md` finished (header, test totals, every row: Pass); the README for 1.0, with screenshots from the emulator (`docs/images/`, cropped by a throwaway Java program, example data only); CLAUDE.md's status and the receivers' settle. Instrumented suite at the final code: 49 on the emulator, 49 on the phone. Pull request [#9](https://github.com/davamix/pills-reminder/pull/9). |
| T10 | To do | After the merge, with the go-ahead: tag `v1.0.0` and the GitHub release |

## Decisions and deviations

Anything decided during implementation that isn't in SPEC.md, or that changes it. A change to a requirement goes into SPEC.md or SPECIFICATION.md first.

| Date | Decision | Reason |
|---|---|---|
| 2026-11-25 | A low or muted notification volume is not checked in v1.0: the user raises the phone's volume, and the specification's §10 gets the future idea (v0.12). Decided by the user. | A11: the alerts played on time with sound, but at the phone's ring and notification volume, 2 of 15, and the user heard none. `RM-9`'s check doesn't read the volume. |
| 2026-11-24 | **Alerts late in deep Doze, fixed** (SPEC §4.3.7, decided by the user): `Reminders.reconcileAndSettle(trigger)` returns 2 s (`ALERT_SETTLE_MILLIS`) after a reconcile whose plan alerted; `AlarmReceiver` and `SystemEventReceiver` call it, so their broadcast, and the alarm's wake lock, last until Android has played the alert. `RemindersTest`: three tests; deliberate bugs `no-settle`, `always-settle`, `settle-on-any-reminder` and `settle-too-short` all caught (the last after sharpening the test to require at least 1 s). | On the phone, in deep Doze, the alarm fired and the app posted on time, but the sound came 11 to 101 s later (`ACCEPTANCE.md`, "Finding"). Android 13, with a notification assistant (live on this phone), waits 200 ms of awake time before posting and alerting, holding no wake lock; the receiver had already finished. P5 measured the post (`late=` in the app's log), not the sound. No new permission: a partial wake lock would need `WAKE_LOCK`, outside plan §3. |
| 2026-11-24 | T4's finding corrected: `dumpsys usagestats`' `NOTIFICATION_INTERRUPTION` isn't an alert record (it had entries with no alert, and missed alerts). Alert times come from `logcat -b events` only, which reached back 36 hours on 24 Nov. | Compared with `notification_alert` over 23–24 Nov. |
| 2026-11-24 | The `phone` skill's `use_device` reads a serial with a space, and `$adb` is `adb -t <transport id>` (committed on this branch, for the skills review to confirm). | After an mDNS name clash, adb listed the phone as `adb-<serial>-<id> (2)._adb-tls-connect._tcp`; the helpers split on whitespace and didn't find it. |
| 2026-11-23 | AndroidX's `androidx.profileinstaller.ProfileInstallReceiver` is removed from the merged manifest (`tools:node="remove"` in `app/src/main/AndroidManifest.xml`, with `tools:ignore="MissingClass"`, since lint reads the compile classpath, where that runtime-only class isn't). Decided by the user. | T3 found it in the release APK, exported and guarded by `android.permission.DUMP`, in every build since P1; plan §3 and SPEC §4.3.3 allow only the launcher activity to be exported. It only serves adb and benchmark tools: AndroidX still installs its profiles when the app starts. |
| 2026-11-23 | The release APK is signed with the v2 scheme only (AGP's default for `minSdk` 33). | SPEC §4.3.3 asks for v2 or v3. Android 13 and later verify v2. |

## Known issues

- From earlier phases: AGP's Gradle 10 deprecation notice and ktlint's `sun.misc.Unsafe` warnings on JDK 25 are harmless (P1).
- The emulator's debug app keeps two notification categories from P5's probes ("Probe reminders" and `reminders-mutant`) and a `files/probe.log`. The release build, installed fresh, has none of them.
- Android 16 may time a notification out after 72 hours (P5); not checked, out of scope (SPEC §3).
- A low notification volume isn't checked by the reminder check: a future idea (specification v0.12). The user raises the phone's volume.

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
- The merged manifests and dependencies against `main`: `bash .claude/skills/handover/compare-manifests.sh <scratchpad>` (25 Nov: only `ProfileInstallReceiver` removed; release dependencies the same, 120 libraries).

## Session log

Newest first.

### 2026-11-25

- Done: T8's results read (A11: every alert played within 0.45 s of its post through a deep-Doze night; the user heard none, the phone's volume at 2 of 15); specification v0.12 (a future idea, decided by the user), merged into this branch; T9: the acceptance record finished, the README with screenshots, CLAUDE.md; the instrumented suite on both devices; pull request #9 opened (`handover`); CI passed on `87e319e` (run 45682300060).
- Checks: `./gradlew build` passes, `:core` 177, `:app` 260 unit tests, no failures. Instrumented: 49 pass on the emulator and 49 on the phone, at the app's final code (`53a00dc`; later commits change documents only). Merged manifests: only `ProfileInstallReceiver` removed, as decided; release dependencies the same as `main`.
- Notes: the first instrumented run of the day failed on the phone because its wireless connection had come back under a new transport id during the run (`$adb -t 16` then pointed nowhere), and the emulator had stopped; both runs passed once repeated. The emulator's clock and theme were set back after the screenshots (no 24-hour override, light theme).
- Device state left behind: the phone's settings as the user had them (ring mode normal, Do Not Disturb off); the debug app reinstalled on the phone by the instrumented run; the emulator running, without a window.

### 2026-11-24

- Done: the two results of T6 read from the phone's records (pass); the late alerts in deep Doze found, and fixed with the user's agreement; the `phone` skill's `use_device` fixed for a serial with a space; the fixed release build installed on the phone as an update (A8's update); T7 with the user (A5–A8). T8 started on "Example".
- Notes: the morning's late alerts were found only because the sound's own record (`notification_alert`) was compared with the post's (`notification_enqueue`); the app's debug log records the post. For the skills review: the `phone` skill's "Reminder device runs" should time the sound, not the post. A tap on a heads-up's Snooze didn't reach the app once (no `notification_action_clicked`); from the shade it did. A restart clears `logcat`'s buffers, so read the events before one.

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
