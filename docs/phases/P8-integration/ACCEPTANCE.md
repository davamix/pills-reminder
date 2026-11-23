# Pills Reminder 1.0.0: Acceptance record

| | |
|---|---|
| **Status** | In progress (P8 T5–T9) |
| **App** | Version 1.0.0 (`versionCode` 1), release build, signed |
| **Commit** | To be filled in T9 |
| **APK SHA-256** | To be filled in T9, for the APK accepted on the phone |
| **Certificate** | `CN=Pills Reminder`, RSA 4096; SHA-256 digest `d28df13a9531debbba7c7d73eb2305121cea03bce885157ae533b4c4a263a5e7` |
| **Specification** | [SPECIFICATION.md](../../SPECIFICATION.md) v0.11 |
| **How** | [P8 SPEC](SPEC.md) §6.2: checks A1–A12 |

This record says how each requirement of the specification was checked before release 1.0.0: by the automated tests of the phase that built it, and by hand, on the phone, with the signed release build. The repository is public, so it holds times and conditions only: no treatment from real use, and no device serial number.

## 1. Devices

| Device | Details |
|---|---|
| **Phone** | POCO F3 (`M2012K11AG`), Android 13 (API 33), HyperOS `OS1.0.6.0.TKHEUXM`, security patch 2024-03-01. Spanish, Europe/Madrid, 24-hour clock. 3-button navigation. |
| **Emulator** | `PillsReminder_API_36`: Android 16 (API 36), Google Play image (`sdk_gphone64_x86_64`), security patch 2025-07-05. English (US), Europe/Paris, 12-hour clock. Same screen as the phone. |

## 2. Settings

The reminder check (`RM-9`) asks for these. Each acceptance check says whether they were set.

| Setting | Phone, release app | Emulator, release app |
|---|---|---|
| Notifications (permission) | Allowed, at the first launch's dialog (A1) | Allowed, at the first launch's dialog (A9) |
| Reminders category: on, pop-up, sound | On (A1: the check reads it) | On (A9) |
| Exact alarms | Allowed at install (`USE_EXACT_ALARM`) | Allowed at install (`USE_EXACT_ALARM`) |
| Battery optimisation | Off: exempt, standby bucket 5 (A1) | Off, through Android's dialog from the check (A9) |
| HyperOS: battery saver | No restrictions, set by the user in A1 | Not HyperOS |
| HyperOS: Autostart | On, set by the user in A1 (stock: off) | Not HyperOS |
| HyperOS: Floating notifications | On (stock), confirmed in A1 | Not HyperOS |
| HyperOS: Lock screen notifications | On (stock), confirmed in A1 | Not HyperOS |

## 3. Automated tests

To be filled in T9, at the commit above: the unit tests of `:core` and `:app`, and the instrumented suite on the emulator and on the phone.

## 4. Requirements

"Tests" names the phases whose automated tests cover the requirement (plan §5.3). "Checked by" names the acceptance checks of §5.

| ID | Requirement, in short | Tests | Checked by | Date | Result |
|---|---|---|---|---|---|
| TR-1 | Several treatments, more than one active | P2, P6 | A2 | | |
| TR-2 | One or more medicines per treatment | P2, P6 | A2 | | |
| TR-3 | Name, free-text dose, interval in hours, days or weeks, duration in days or weeks or no end | P2, P6 | A2 | | |
| TR-4 | Each medicine's own first take; the next full hour suggested; a first take in the past | P2, P6 | A2, A9 | | |
| TR-5 | A treatment's name and optional notes | P2, P6 | A2 | | |
| TR-6 | Optional instructions, shown by the reminder | P2, P6 | A2, A5, A9 | | |
| TR-7 | The dose is free text | P2, P6 | A2 | | |
| TR-8 | Validation | P2, P6 | A2 | | |
| TR-9 | Create, edit, delete with confirmation; deleting deletes its takes | P3, P5, P6 | A2, A6 | | |
| TR-10 | Editing a started treatment changes only what is to come | P2, P3, P6 | A2 | | |
| TR-11 | Ending a treatment early | P2, P5, P6 | A2, A8 | | |
| TR-12 | Finished treatments stay until deleted | P2, P3, P6 | A2 | | |
| TR-13 | Leaving an editor with changes asks first | P6 | A2 | | |
| SC-1 | One take every interval, from the first take, for the duration | P2 | A2, A5 | | |
| SC-2 | Every planned take worked out; the editor shows the count and the last one | P2, P6 | A2 | | |
| SC-3 | Planned times are fixed | P2 | A3, A4 | | |
| SC-4 | Clock times across daylight saving and time zones | P2 | A9 (time zone); daylight saving in P2's unit tests | | |
| SC-5 | A medicine with no end; "No end" | P2, P6, P7 | A2, A3 | | |
| RM-1 | The reminder names the medicine, dose, treatment and instructions | P5, P8 (`EndToEndTest`) | A5, A9 | | |
| RM-2 | Heads-up with sound, no vibration; vibrates in vibrate mode; app closed, screen off, locked, restarted | P5 | A5, A7, A8 | | |
| RM-3 | Taken, Snooze and Skip | P4, P5, P8 (`EndToEndTest`) | A6 | | |
| RM-4 | One reminder per take, grouped | P4, P5, P8 (`EndToEndTest`) | A5 | | |
| RM-5 | Repeats every 10 minutes, 3 times; swiping away | P4, P5 | A6, A9 | | |
| RM-6 | Tapping a reminder opens its take | P5, P6, P8 (`EndToEndTest`) | A4 | | |
| RM-7 | Answered in the app, or Missed: the reminder goes | P4, P5, P8 (`EndToEndTest`) | A6, A9 | | |
| RM-8 | Do Not Disturb and silent mode: no sound | P5 | A7 | | |
| RM-9 | The reminder check | P5, P7 | A1, A9 | | |
| RM-11 | Full content on the lock screen | P5 | A5 | | |
| TK-1 | Each take's state | P2, P4 | A3, A4 | | |
| TK-2 | Taken records the time, which can be corrected | P2, P5, P6, P7, P8 (`EndToEndTest`) | A4, A6 | | |
| TK-3 | Missed one interval later | P2, P4 | A6, A9 | | |
| TK-4 | The next take marked Taken early | P2, P7 | A3 | | |
| TK-5 | The last answered take can be changed | P2, P6 | A4 | | |
| HM-1 | The next take of each medicine, with the time left | P7 | A3 | | |
| HM-2 | Due takes first, highlighted, with Taken | P7, P8 (`EndToEndTest`) | A3 | | |
| HM-3 | Progress and treatment name | P7, P8 (`EndToEndTest`) | A3 | | |
| HM-4 | Upcoming treatments; the empty state | P7 | A3, A9 | | |
| HM-5 | Stays current while open | P7 | A3 | | |
| NF-1 | On time, within 1 minute, in Doze, screen off, swiped away, restarted | P4, P5 | A5, A8, A11 | | |
| NF-2 | Reminders survive restarts, updates, time and zone changes, the process ending; Force stop until opened | P5 | A8, A9 | | |
| NF-3 | Offline and private | P1 | A12, A9 | | |
| NF-4 | Data survives restarts and updates; interrupted saves | P3 | A8; interrupted saves in P3's tests | | |
| NF-5 | Nothing runs between reminders | P5 | A11, A12 | | |
| NF-6 | Android 13 on the phone, and later versions | P1, P5 | A1 (API 33), A9 (API 36) | | |
| NF-7 | Portrait, 48 dp targets, large fonts | P1, P6, P7 | A10 | | |
| NF-8 | English, text in resources, 24-hour setting, day and month names | P1, P6, P7 | A10 | | |
| NF-9 | Installed from the PC, never on Google Play | P1 | A1, A9 | | |
| NF-10 | Light and dark theme | P1, P6, P7 | A10 | | |

## 5. Checks

### A1. Install and first launch on the phone (2026-11-23)

**Install** (NF-9, NF-6, NF-3): the signed release APK installed from the PC over adb (`gradle installRelease`), beside the debug app, at 18:29. Version 1.0.0. Android's package flags have neither `DEBUGGABLE` nor `ALLOW_BACKUP`. Granted at install: `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`; not yet `POST_NOTIFICATIONS`. No `INTERNET`. HyperOS's stock settings for an app installed from the PC: battery optimised (standby bucket 50, never used yet), Autostart off (`MIUIOP(10008): ignore`).

**First launch** (RM-9): the reminder check opened above Home, with Android's dialog, in the phone's language: "¿Permitir que Pills Reminder te envíe notificaciones?"; the user allowed it. The check: "4 settings to check." Notifications allowed; Exact alarms allowed; Battery optimisation "On: Android may delay reminders…", with "This also sets HyperOS's Battery saver to No restrictions." and Turn off; under HyperOS, "The app can't read these. Check each one, then mark it done.", with Autostart, Floating notifications and Lock screen notifications, each with Open settings and Done.

**Following the check:** the user set HyperOS's battery saver to No restrictions; the check then read "Off for this app" (Android: exempt, standby bucket 5). Each HyperOS step's Open settings opened its screen: the notification steps the app's notification settings (`Settings$AppNotificationSettingsActivity`), Autostart HyperOS's Autostart list (`AutoStartManagementActivity`). The user turned Autostart on for Pills Reminder, kept the two notification settings on, and marked each step Done. The check: "Everything reminders need is set." Home: the empty state, with no warning (HM-4); Home's Settings button opens the same check.

**Autostart, as Android reports it:** `ignore` right after the install; after the user turned it on, no entry at all ("No operations."), where the debug app, also on, reads `allow`. A8's update checks that it works: with Autostart off, HyperOS blocks `MY_PACKAGE_REPLACED` (P5).

### A12. Closed and private (2026-11-23)

**The APK** (P8 T3, on the PC): signed with the v2 scheme by `CN=Pills Reminder`. Its manifest: no `android:debuggable`, `allowBackup="false"` with data extraction rules that exclude everything; permissions `POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, and AndroidX's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which gives access to nothing; only `MainActivity` exported; the three receivers not exported; no service and no job (NF-5); no debug screen. AndroidX's `ProfileInstallReceiver`, exported behind `DUMP`, was removed from the manifest in P8. Its runtime libraries: AndroidX, JetBrains, Google, and Okio and JSpecify, which AndroidX uses; nothing for accounts, analytics or ads (NF-3).

**On the phone:** `run-as io.github.davamix.pillsreminder` is refused ("package not debuggable"), so not even adb can read its data; no `INTERNET` permission. After the first launch and the check, logcat held 161 lines from the app's process, every one from Android, HyperOS or the graphics driver (window, input, rendering, the back callback, and HyperOS's `PerfDebugMonitorImpl` at process start); none from the app's own tags (`PillsReminders`, `PillsStore`).

### A9. The emulator (2026-11-23)

The release build, signed, installed fresh on the emulator with `gradle installRelease`, beside the debug app.

**Install** (NF-6, NF-9, NF-3): version 1.0.0; Android's package flags have neither `DEBUGGABLE` nor `ALLOW_BACKUP`; `USE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED` granted at install, `POST_NOTIFICATIONS` not yet; no `INTERNET`.

**First launch** (RM-9, HM-4): the reminder check opened above Home, and Android's dialog asked for the notification permission ("Allow Pills Reminder to send you notifications?"); allowed. The check then showed Notifications allowed, Exact alarms allowed, and Battery optimisation "On: Android may delay reminders…", with Turn off; no HyperOS steps. Turn off opened Android's own dialog ("Let app always run in background?"); allowed. The check: "Everything reminders need is set." Back on Home: the empty state, "No treatments active or upcoming.", with no warning.

**A treatment with a first take in the past** (TR-4, TR-6, D-15): "Example", Amoxicillin, 1 capsule, "with food", every 1 hour for 7 days, first take yesterday at 7:00 PM, the suggested time. The interval's unit menu offered hour, day and week: no minutes in a release build. The editor said "The first take is in the past. Its takes before now will be missed, apart from the latest while it is due." and "168 takes. The last one is on Sun 29 Nov at 6:00 PM."

**The reminder** (RM-1, RM-4): saved at 18:11:32; the 18:00 take, Due, alerted at once (`notification_alert` 18:11:32.864, `beep=1`, `buzz=0`): "Amoxicillin: 1 capsule", "Example · with food", in a group with its summary, "1 take due", which didn't alert. The single alarm clock was set for its first repeat, 10 minutes later (RM-5).

**`TIME_SET`** (NF-2): with automatic time off, the clock set an hour ahead, to 19:11:57. The 18:00 take, now Missed, left the shade (RM-7, TK-3); the 19:00 take, Due, alerted at 19:11:53; the alarm moved to its repeat, 19:21:53. Automatic time on again, back to 18:12:10: the 18:00 take was Due again and alerted (18:12:05); the alarm at 18:22:04.

**`TIMEZONE_CHANGED`** (NF-2, SC-4): with automatic zone off, America/New_York, 12:12 local time. The shade then held the take planned at **12:00** local time, which alerted at once (12:12:17 local), and the 18:00 one had gone: the takes kept their clock times in the new zone. The alarm at 12:22:16 local. Back to Europe/Paris, automatic time and zone on: the 18:00 take Due again, alerted at 18:12:29, the alarm at 18:22:29.

**No logging** (NF-3, plan §3): with the release app running and reconciling through all of the above, logcat had no line from its process. The ten lines under the app's tags (`PillsReminders`) all came from the debug app's process, which got the same time broadcasts.

**On time** (NF-6 on API 36): to be filled in with the 18:22:29 repeat and the 19:00 take.
