# P5 — Reminders on Android: Handover

| | |
|---|---|
| **Status** | In progress |
| **Spec** | [SPEC.md](SPEC.md), version v0.4 |
| **Branch** | `phase/p5-reminders` |
| **Last session** | 2026-11-21 |

This document tracks the phase as it's built. Update it at the end of every session, so the next one can continue from here.

## Prompt to start or continue

Paste this prompt into a new Claude Code session to start or continue the phase. When the phase is done, replace it with "Phase done: no prompt needed."

```text
Start or continue phase P5 (Reminders on Android) of the pills reminder.

1. Use the phase-start skill for P5. Read only docs/phases/P5-reminders/HANDOVER.md and SPEC.md; they contain everything needed.
2. Carry on from "Next steps" in the handover. Work through the tasks in SPEC.md §7 in order, on branch phase/p5-reminders. Commit after each task, once the build passes.
3. Follow SPEC.md. If something in it is wrong or unclear, stop and ask me before changing course. Record every decision or deviation in the handover.
4. T1 and T2 (the measurements) are done; T3 and T4 need no device. The phone is needed again for T5's instrumented tests and T8's device runs, with me nearby: I swipe the app away, restart the phone and unlock it, listen for the sound, and look at the lock screen. Ask before changing a setting on the phone, forcing Doze, restarting it or granting a permission, and put things back; never change its clock or time zone. The emulator is needed from T5 on, for instrumented tests and the time and zone checks.
5. Before the session ends, or once all tasks are done, use the handover skill. When the done criteria pass, open the pull request and wait for me to merge it.
```

## Current state

T1 to T5 are done: **the app has real reminders.** A saved treatment sets an alarm clock; when it fires, the reconcile posts the reminder, with sound, its repeats, and Taken, Snooze and Skip, which record the answer and remove it. Checked end to end on the emulator (T5's session log). What is built:

- T3, the reminder state in the store: `PillsData.reminders`, the snooze action, records dropped with deleted or removed medicines, and the format's optional `reminders` list, with a new golden file.
- T4, the reconcile in `app/.../reminders/`: `Reconciler`, `Reminders` (the observer of the data, the answers, `runAsync`), and the interfaces it works on, tested on the JVM with fakes.
- T5, the Android side: `ReminderChannel`, `AndroidReminderShade`, `AlarmClockWakeUp`, `ReminderIntents`, the three receivers (`Receivers.kt`), `FileReminderLog`; the manifest's four permissions and three receivers; `AppContainer.reminders`, started by `PillsReminderApp`, and `MainActivity.onStart`'s reconcile.

`./gradlew build` passes; every instrumented test passes on the emulator and the phone. No way yet to create a treatment in the app (P6) or from the debug screen (T6), and a tapped reminder opens the app but not yet the take (T7).

T1 and T2's measurements changed the design, with the user, in SPEC v0.4 (Q5–Q10), plan v0.6 (`D-8`, `D-10`) and specification v0.8 (`RM-2`):

- **The alarm is an alarm clock** (`setAlarmClock()`): HyperOS holds exact alarms for up to 5 minutes with the screen off. Alarm clocks were on time in every case, with stock settings: screen off, light and deep Doze, a night, the process ended, a swipe from Recents, an update.
- **Notifications:** no category, so Do Not Disturb silences them (HyperOS lets the *reminder* category through); public on each notification, since an app can't set the channel's lock-screen visibility; silent posts as group children with `GROUP_ALERT_SUMMARY` (the platform has no `setSilent()`). In vibrate mode, Android vibrates instead of sounding, now allowed by `RM-2`.
- **Boot receiver** at `android:priority="999"`: `BOOT_COMPLETED` 8.7 s after the unlock, instead of 59 s.
- **Updates** remove the notifications, and HyperOS blocks `MY_PACKAGE_REPLACED` while Autostart is off: Autostart goes on P7's list.

The probes are deleted, and their lines in `.git/info/exclude` removed. The phone has this branch's clean debug build. The night in natural deep Doze, with reminders hours apart, moved to T8 (M7).

## Next steps

1. **T6, the debug tools** (SPEC §4.3.9, Q3): `DebugActivity` with its own launcher icon, "Pills debug", in a new `app/src/debug/AndroidManifest.xml` (exported, as a launcher activity must be: plan §3 allows it in debug builds); `DebugScreen`, `DebugContent` and `DebugViewModel` (factory in the companion, CLAUDE.md "Screens"); `TestSchedules` (a "Test" treatment with ID prefix `debug-`, one or two medicines "Test A" and "Test B", "1 tablet", every N minutes, default 45, first take in M minutes, default 2, for 1 day or no end, validated with `allowMinutes = true`); `strings_debug.xml` in `app/src/debug/res/values/`; `TestSchedulesTest` in `app/src/testDebug/`.
   - The screen shows the plan's next wake-up, whether the alarm is set (`ReminderIntents.wakeUp(…, FLAG_NO_CREATE)`), the records, the reminders in the shade, what reminders depend on (notifications allowed, `canScheduleExactAlarms()`, battery optimisation, the channel's importance), and the log's last lines (`files/reminders-log.txt`).
   - Buttons: create, end and delete the test treatments (through P2's helpers, so the observer reconciles); Reconcile now (`DEBUG`); Allow notifications (the runtime request); Clear log.
   - Draw every state in light and dark and at the largest font (`phone` skill, "Checking a screen in every state"); it must fit 392.7 × 796.4 dp, scrolling where it must.
2. With T6, on the emulator: show that `ReminderChannelTest` and `ReminderShadeTest` can fail (T5's session log, "Not done yet").
3. Then T7: a tapped reminder opens its take (`ReminderIntents.keyOf`, `MainActivity`, `AppNavHost`, `OpenTakeTest`).

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | Done | A1–A8 on the phone and the emulator; the natural deep-Doze night moved to T8 (M7). Changed `D-8` (SPEC v0.3, Q5), the boot receiver's priority (Q9), and found the update and Autostart (Q10). |
| T2 | Done | N1–N7 on the phone and the emulator, and two fix variants. Changed `D-10` and `RM-2` (SPEC v0.4, Q6–Q8). |
| T3 | Done | `PillsData.reminders`; `PillsData.snooze` and `repository.snooze` (`core/actions/ReminderActions.kt`, `RepositoryActions.kt`); `deleteTreatment` and `saveTreatment` drop records (P5-D8); the format's `ReminderV1` and its strict mapping; `pills-v1-reminders.json`. Tests: `ReminderActionsTest`, new cases in `DeleteTreatmentTest` and `SaveTreatmentTest`; `ReminderFormatTest`, 15 damaged-record cases in `DamagedDataTest`, a restart in `DataStoreTreatmentRepositoryTest`. Eight deliberate bugs, each caught (session log). |
| T4 | Done | `Reconciler`, `Reminders`, `ReminderShade`, `WakeUpAlarm`, `ReminderLog`, `ReminderContent`, `TakeKeyCodec` in `app/.../reminders/`. Tests: `ReconcilerTest` (the §4.3.11 timeline, the order, idempotence, silent reposts, removals, failures, the log), `RemindersTest` (answers, the observer, `runAsync`), `ReminderContentTest`, `TakeKeyCodecTest`, with `Fakes.kt`. Twelve deliberate bugs, each caught (session log). |
| T5 | Done | `ReminderChannel`, `AndroidReminderShade`, `AlarmClockWakeUp`, `ReminderIntents`, `Receivers.kt`, `FileReminderLog`, `strings_reminders.xml`, `ic_notification`; the manifest; `AppContainer`, `PillsReminderApp`, `MainActivity`. Instrumented: `ReminderChannelTest` (1), `ReminderShadeTest` (7), `WakeUpAlarmTest` (3). A first reminder end to end on the emulator. Every instrumented test passes on both devices (19). |
| T6 | To do | Debug tools |
| T7 | To do | Opening a take |
| T8 | To do | Device runs (phone, emulator) |
| T9 | To do | Wrap-up and pull request |

## Decisions and deviations

Anything decided during implementation that isn't in SPEC.md, or that changes it. A change to a requirement goes into SPEC.md or SPECIFICATION.md first.

| Date | Decision | Reason |
|---|---|---|
| 2026-11-20 | The alarm is an alarm clock, `setAlarmClock()` (`D-8`'s fallback). Agreed with the user; in SPEC v0.3 (Q5) and plan v0.5. | T1: with the screen off, `setExactAndAllowWhileIdle()` was held until a 5-minute grid, up to 4 min 15 s late, also exempt from battery optimisation; alarm clocks were within 0.1 s. |
| 2026-11-20 | The probe takes commands over adb through an exported receiver protected by `android.permission.DUMP`, instead of probe tests. | Only the shell holds `DUMP`, so no other app can reach it, and `am instrument` would end the app's process after each test. It was never committed, and was deleted after T2. |
| 2026-11-21 | Reminders are silent in Do Not Disturb, so the notifications have no category; in vibrate mode they vibrate. Decided by the user; SPEC v0.4 (Q6, Q7), plan v0.6 (`D-10`), specification v0.8 (`RM-2`). | T2: HyperOS's Do Not Disturb lets the *reminder* category sound, with no setting to change it; Android's fallback vibration in vibrate mode can't be stopped without losing the pop-up. |
| 2026-11-21 | Silent posts by group alert behaviour; public visibility on each notification; `android:priority="999"` on the boot receiver. SPEC v0.4 (Q8, Q9). | T2: the platform has no `setSilent()`, and ignores an app's channel lock-screen visibility. T1: the priority brought `BOOT_COMPLETED` from 59 s to 8.7 s after the unlock. |
| 2026-11-21 | T4's shapes, where they differ from SPEC §4.2: `ReminderShade.shown()` returns each reminder's fingerprint by key, and the `Reconciler`, not the shade, decides that a silent post is needed (missing, or a different fingerprint, P5-D5). `ReminderShade.prepare()` creates the channel, called by `Reminders.start()`, which takes the shade. `WakeUpAlarm.set(at, retry)`, so the alarm receiver can say `RETRY`. `ReminderLog` is a `fun interface` with `event(line)`. `TakeKeyCodec` writes `<plannedAt>|<medicineId>`. A failure while applying the plan is also `Failed`, with the retry at the next wake-up when that comes sooner. To fold into the SPEC with T5–T7's, in T9. | The rule of P5-D5 then has JVM tests of its own, rather than living in a fake. The time first in the key means a medicine ID can hold any character. Same behaviour as the SPEC describes. |
| 2026-11-21 | T5's details, where SPEC §4.1–§4.3 is silent or names them otherwise: the three receivers in one file, `Receivers.kt`; `AlarmClockWakeUp(context, action, log)` tells the test alarm apart by its action (`…WAKE_UP.TEST`), which `dumpsys alarm` shows as its tag, and `AlarmReceiver` takes any action starting with `…WAKE_UP`; the summary's tag is `<prefix>-summary` and the group is `…group.<prefix>`, so a test's notifications form a group of their own; a reminder with no dose has the medicine alone as its title; `MainActivity.onStart` reconciles through `Reminders.runAsync`, in the process's scope; `FileReminderLog` stamps its lines with the container's clock. To fold into the SPEC in T9, with T4's. | The tests' alarm must show in `dumpsys alarm` apart from the app's; a title "Minutes: " would be odd. |
| 2026-11-21 | `@SuppressLint("MissingPermission")` on `setAlarmClock`. | Lint wants `SCHEDULE_EXACT_ALARM`; `USE_EXACT_ALARM` covers alarm clocks (D-8), and `canScheduleExactAlarms()` is checked first. Without the suppression Lint warned (checked in T5). |
| 2026-11-21 | The natural deep-Doze night moved from T1 to T8 (M7). | Alarm clocks 10 minutes apart keep the phone out of deep Doze; the night needs reminders hours apart, which T8's test treatment gives. Forced deep Doze already passed. |

## Measurements (T1, T2)

The phone: POCO F3, Android 13, HyperOS `OS1.0.6.0.TKHEUXM`, on wireless debugging only, unplugged. Times are UTC, as the probe logs them; logcat shows local time (UTC+1). The probe is a debug-only receiver, never committed, that logs each alarm's lateness, `isDeviceIdleMode`, `isInteractive` and the process to `files/probe.log`.

**Stock settings:** battery optimisation on (read with `reminder_health`), standby bucket 40 before the install and 10–20 after it. HyperOS's battery saver for the app: "Battery saver", its default; Autostart off (both read by the user in Settings).

| # | When (UTC) | Setup | Result |
|---|---|---|---|
| A1 | 2026-11-20 20:57 | After `gradle installDebug` | `USE_EXACT_ALARM: granted=true`; `canScheduleExactAlarms()` true |
| A2 run 1 | 20:58–21:06 | `setExactAndAllowWhileIdle`, 3 alarms: +90 s, then 2 min apart. The app was launched, but the screen stayed off throughout (`interactive=false`), so this was "screen off", not "app in front". Not in Doze (`idle=false`). | 1st: late 25 ms. 2nd: late **255 s**: `AlarmManager` sent it at 22:05:48 local, alone; no other alarm fired then. 3rd (already past when set): 5 s after it was set. The screen was off from 21:58:09 to 22:10:06 local (`screen_toggled` in the events log), and the user didn't touch the phone, so nothing on the screen released it. Nothing in logcat or `dumpsys alarm` says why it was held. |
| A2 run 2 | 21:20–21:31 | The same, stock, screen off (turned off with `KEYCODE_SLEEP` at 21:20:46; untouched until the end), not in Doze. 3 alarms: +120 s, then 3 min apart. No adb polling during the run. | 1st: late **180 s**, at 21:25:48.7. 2nd (past when set): 5 s after it was set. 3rd: late **120 s**, at 21:30:48.7. |

**Finding, stock settings, screen off:** `setExactAndAllowWhileIdle` alarms are delivered on a **5-minute grid**: 21:05:48.7, 21:25:48.7 and 21:30:48.7 in both runs, the same second within the minute. An alarm can be up to 5 minutes late, not in Doze, so `NF-1` fails. Only run 1's first alarm, 84 s after the screen went off, was on time. An alarm already past when it is set fires about 5 s later.

| # | When (UTC) | Setup | Result |
|---|---|---|---|
| A8 run 1 | 21:32–21:41 | `setAlarmClock()`, stock settings, screen off throughout (no `screen_toggled`), not in Doze. 3 alarms: +120 s, then 3 min apart. | All three on time: late 53 ms, 45 ms, 51 ms. |
| A2 run 3 | 21:45–21:56 | `setExactAndAllowWhileIdle` again, with the app's battery saver set to **"No restrictions"** by the user (Autostart still off). That makes Android treat the app as exempt from battery optimisation (`reminder_health`: exempt, standby bucket 5). Screen off throughout. | 1st: late **176 s**, at 21:50:48.7. 2nd: late 1.2 s (set 4 s before it was due). 3rd: late **116 s**, at 21:55:48.7. Still the 5-minute grid. |

**The grid** is on `elapsedRealtime`: every held alarm was delivered at an exact multiple of 300 000 ms since boot (9 900 028, 10 200 047, 11 400 035, 11 700 028 ms). It is HyperOS's own alignment of background apps' alarms while the screen is off: Android's battery optimisation exemption doesn't lift it, and `setAlarmClock()` isn't subject to it.

After run 3, the user set the battery saver back to its default (`reminder_health`: optimised, bucket 10).

| # | When (UTC) | Setup | Result |
|---|---|---|---|
| A3 night | 2026-11-20 22:01 → 2026-11-21 07:57 | `setAlarmClock()`, 60 alarms 10 minutes apart from 22:06:49, stock settings, screen off, unplugged (53 %), wireless debugging, no adb during the night | **All 60 on time:** late 25 ms to 1.7 s (most 0.5–0.7 s or 1.4–1.7 s). Screen off at every alarm, the same process all night (never ended). Battery 53 % → 47 %. |

**No deep Doze that night,** only light Doze: the idling history has 46 `light-idle` periods, 37 `light-maint` windows and 17 returns to `normal`, mostly marked `(alarm)`, and no `deep` entry. Android doesn't enter deep Doze while an alarm clock is due within `min_time_to_alarm` (30 minutes on this phone), and these came every 10. Real reminders are hours apart, so the phone will reach deep Doze between them: deep Doze is still to be measured, forced (`doze_on`) and natural (an alarm clock more than an hour away, the phone left still).

| # | When (UTC) | Setup | Result |
|---|---|---|---|
| A2 forced Doze | 2026-11-21 12:12–12:21 | 3 alarm clocks: +150 s, then 3 min apart; `doze_on` right after setting them; stock settings, screen off | **Deep Doze didn't start:** `doze_state` said `deep=INACTIVE` after `doze_on`, and the phone went into light Doze. Android refuses deep idle, even forced, while a wake-from-idle alarm (an alarm clock) is due within `min_time_to_alarm`, 30 minutes. The three alarm clocks: late 34, 68 and 57 ms. |

So the phone doesn't enter deep Doze in the 30 minutes before an alarm clock. If it is already in deep Doze when that window starts, as it will be with reminders hours apart, the alarm clock itself is what must bring it out, on time. That is the case still to measure: an alarm clock more than 30 minutes ahead, with `doze_on` straight after it is set.

| # | When (UTC) | Setup | Result |
|---|---|---|---|
| A2 process ended | 2026-11-21 12:22–12:25 | An alarm clock at +180 s, then `end_process` (`am kill`; `pidof` empty afterwards). Stock settings, Autostart off, screen off. | **On time:** late 491 ms, in a new process (pid 10012, started at the alarm): the alarm clock started the app's process with Autostart off. |
| A2 swipe | 2026-11-21 12:27–12:31 | The user opened the app; an alarm clock at +240 s; the user swiped the app from Recents and locked the phone. Stock settings, Autostart off. | The swipe ended the process (at 12:27:54) and removed the app from Recents. **On time:** late 307 ms, in a new process (pid 10950). |
| A4 restart | 2026-11-21 12:32–12:40 | An alarm clock at +180 s, then the user restarted the phone; it fired (late 22 ms) before the restart, so the restart's clearing of alarms wasn't seen (`alarms` empty after it, as expected). Booted 12:38:09; the user unlocked at 12:39:18 (`keyguardGoingAway`; `am_user_state_changed [0,3]` at 12:39:20.66). Stock settings, **Autostart off**. Wireless debugging reconnected by itself. | `BOOT_COMPLETED` reached the receiver that isn't exported, in a new process, at 12:40:20.0: **59.3 s after the unlock**. Autostart off didn't block it. Android delivered it to 215 receivers in turn (`BOOT_COMPLETED_BROADCAST_COMPLETION_LATENCY_REPORTED … completeLatency:60120 … receiversSize:215`), and the app's came near the end. `LOCKED_BOOT_COMPLETED` had gone at 12:38:11. |

**After a restart, the reconcile can start up to a minute after the unlock,** because the app waits its turn among every app's `BOOT_COMPLETED` receivers. It is within `NF-1`'s minute here, but grows with the number of apps. To try: `android:priority="999"` on the intent filter, since Android delivers this broadcast in priority order.

| # | When (UTC) | Setup | Result |
|---|---|---|---|
| A5 update | 2026-11-21 12:41–12:46 | An alarm clock at +240 s, then `gradle installDebug` (an update: the probe with `android:priority="999"` on its intent filter). Stock settings, Autostart off. | **The alarm survived the update** (`alarms` listed it before and after) and fired late 875 ms, in a new process. **`MY_PACKAGE_REPLACED` was blocked:** `BroadcastQueueInjector: Unable to launch app … for broadcast Intent { act=android.intent.action.MY_PACKAGE_REPLACED … }: process is not permitted to  auto start`. HyperOS force-stops the app during the install (`AutoStartManagerService: prepare force stop`). |

**With Autostart off,** HyperOS blocks `MY_PACKAGE_REPLACED`, but let `BOOT_COMPLETED` through (A4). The alarm survives an update by itself, so the next reminder still comes; what an update does to the notifications in the shade is for T2. Updates come only from the PC (`NF-9`). To try with Autostart on.

| # | When (UTC) | Setup | Result |
|---|---|---|---|
| A4 restart 2 | 2026-11-21 12:47–12:50 | The same as the first restart, with `android:priority="999"` on the receiver's intent filter. The user unlocked at 12:50:12 (`am_user_state_changed [0,3]` at 12:50:13.92). Wireless debugging took about a minute to reconnect after the unlock. | `BOOT_COMPLETED` at 12:50:22.6: **8.7 s after the unlock** (the process started at 12:50:21.0), against 59.3 s without the priority. Autostart off didn't block it. |
| A2 deep Doze | 12:51–13:37 | After A7: the app opened once (to leave the stopped state), screen off, an alarm clock at **+45 min**, then `force-idle` ("Now forced in to deep idle mode", `deep=IDLE`). Stock settings, unplugged. | The idling history: `deep-idle` from 12:51:32 to 13:31:36, then `normal (alarm)`: Android left deep Doze about 5 minutes before the alarm clock. **On time:** late 41 ms. Forced Doze turned off afterwards (`doze_off`). |
| A7 Force stop | 12:51 | An alarm clock at +300 s, then `force_stop` | `alarms`: 1 before, **0 after**. The app is then in Android's stopped state: it gets no broadcast, the shell's included, until it is opened. |

**On the emulator** (`PillsReminder_API_36`, Android 16, API 36), 2026-11-21 12:52–12:54 UTC:

| # | Setup | Result |
|---|---|---|
| A1 | `gradle installDebug` | `USE_EXACT_ALARM: granted=true`; `canScheduleExactAlarms()` true; an alarm clock 30 s ahead fired 3 ms late. `MY_PACKAGE_REPLACED` reached the receiver at the install (no HyperOS here). |
| A6 zone | The app open (pid 3240); `cmd alarm set-timezone Europe/London` from the shell | `TIMEZONE_CHANGED` reached the receiver that isn't exported, in the same process; `ZoneId.systemDefault()` and `CurrentZoneClock().zone` both said Europe/London: **P2's pending check passes.** |
| A6 time | `cmd alarm set-time <now + 1 h in ms>` | `TIME_SET` reached the receiver, in the same process. |

Then the clock and the zone were put back (`set-time` to the PC's time, `set-timezone Europe/Paris`); automatic time and zone stayed on. The shell **can** change the emulator's time and zone with `cmd alarm set-time` and `set-timezone`, unlike `cmd time_zone_detector` (the `phone` skill says it can't): for the skills review in `phase-close`. Never on the phone.

**T2 on the emulator** (the notification probe: `NotificationProbe.kt` in the same excluded folder, commands through `NotificationProbeControl`, its own channel `probe_reminders`; 2026-11-21 12:56 UTC):

- **`Notification.Builder.setSilent()` doesn't exist** on the platform: it is AndroidX's `NotificationCompat`, which mutes a post by making it a group child with `GROUP_ALERT_SUMMARY`. SPEC P5-D4 and §4.3.4 name it wrongly. The probe posts silently that way: a child with `GROUP_ALERT_SUMMARY` (muted), an alert with `GROUP_ALERT_CHILDREN`, and the summary with `GROUP_ALERT_CHILDREN` (always muted). To fix in the SPEC.
- **The channel's lock-screen visibility can't be set by the app:** after `lockscreenVisibility = VISIBILITY_PUBLIC`, the created channel reads `-1000` (`VISIBILITY_NO_OVERRIDE`). Lock-screen content then follows each notification's own `setVisibility(VISIBILITY_PUBLIC)` (`vis=1` in the record). SPEC §4.3.4's channel table says otherwise: to fix.
- The channel: importance 4 (high), `shouldVibrate()` false, the default notification sound, no Do Not Disturb bypass.
- Two reminders posted 80 ms apart in the group, both with sound: both shown heads-up (`airtimeCount=1` each), but **one sound**: `mSoundNotificationKey` names the first. `RM-4` and Q4 allow it.
**T2 on the phone** (stock settings; the probe updated with `gradle installDebug`, and `POST_NOTIFICATIONS` granted with `pm grant`, agreed with the user):

| # | When (UTC) | Setup | Result |
|---|---|---|---|
| N1 | 2026-11-21 13:41 | HyperOS's settings for the debug app, read by the user, untouched | App: Floating notifications **on**, Lock screen notifications **on**, Sound on, Vibration on. The channel "Probe reminders": Show notifications on, Floating notifications on, Vibration off. Nothing for P7's list here. |
| N3, N4 | 13:39:46–13:40:34 | Phone unlocked, home screen, normal ring mode (`mode_ringer=2`, `zen_mode=0`). A with sound; A again with sound 15 s later; A again silently (child with `GROUP_ALERT_SUMMARY`) 15 s later; B and C with sound together 15 s later | The user: each sounded and popped up (heads-up), no vibration, and they stayed in the shade. `notification_alert` in the events log, `[key, buzz, beep, blink]`: A `0,1,0`; A again `0,1,0` (**a repeat sounds again**); the silent post: **no alert**; B `0,1,0` and nothing for C: **one sound for two**. |
| N6, N5, RM-11 | 13:48–13:52 | The user locked the phone (`isKeyguardShowing=true`, `Dozing`); D posted with sound; then, without unlocking, Taken on D, Snooze on C, Skip on B; then a tap on A | D sounded (`0,1,0`) and **lit the screen**; the lock screen showed its **full content** (title and "Test · with food"). **All three actions reached the receiver that isn't exported, locked** (`ANSWER … locked=true`), with no unlock asked. The tap asked for the unlock, then **opened `MainActivity`** (`START u0 {id=open/A flg=0x34000000 cmp=…MainActivity (has extras)}`); HyperOS didn't block it. The probe doesn't remove a reminder after an answer, so they stayed. |
| N2 | 13:54–14:09 | Each ring mode, set by the user; a reminder with sound posted after each change. The probe's channel has vibration off and category *reminder*. | **HyperOS Silent mode** (Android reads `mode_ringer=1`, notification stream muted): E popped up, **no sound, no vibration**. **Vibrate mode** (also `mode_ringer=1`, stream muted): M3 **vibrated** (`notification_alert` `buzz=1, beep=0`) and popped up. Android turns a notification's sound into its fallback vibration in vibrate mode when the record has no vibration of its own (`NotificationManagerService`: "Demote sound to vibration if vibration missing & phone in vibration mode"). HyperOS's Silent mode is silent inside Android, so it doesn't. **Normal mode:** M2 and M4 sounded. **Do Not Disturb** (`ZEN_MODE_IMPORTANT_INTERRUPTIONS`): DND1 **sounded**, with no pop-up. The phone's policy has `allowReminders=true` (`PRIORITY_CATEGORY_REMINDERS`), and the user sees no list of exceptions in HyperOS's Do Not Disturb settings, so a *reminder* notification gets through. `notification_alert` logs `beep=1` even when the ring mode mutes the sound: only the user's ears tell. |

| # | When (UTC) | Setup | Result |
|---|---|---|---|
| N7 | 14:15 | The user swiped Test B away, on its own | `notification_canceled … take/B … reason 2` (`REASON_CANCEL`, by the user); the delete intent reached the receiver (`DISMISSED key=B`). A silent repost 16 s later **brought it back, silently**, with no pop-up (the user; no `notification_alert`). |
| N5 | 14:16 | Home screen, then `end_process` (`am kill`; `pidof` empty), then the user tapped Test C | **The app opened:** `START u0 {id=open/C … MainActivity (has extras)}`, then `Start proc … for next-top-activity`. HyperOS didn't block it. |
| Update with reminders | 14:16 | 10 of the app's notifications in the shade, then `gradle installDebug` | **All removed:** 0 after the update. Installing force-stops the app, and Android cancels a force-stopped app's notifications; a *Force stop* (A7) does the same. With Autostart off, `MY_PACKAGE_REPLACED` is blocked (A5), so the reminders stay gone until the next alarm or until the app is opened. |

| Update, Autostart on | 14:19 | The user turned **Autostart on**; `gradle installDebug` | **`MY_PACKAGE_REPLACED` reached the receiver,** 10 s after the install started, in a new process. Autostart is what the update broadcast needs. |
| Fixes | 14:20–14:21 | A second channel with its own vibration, `vibrationPattern = [0, 1]` (1 ms); Q1 in normal mode and Q2 in vibrate mode on it; Q3 on it with **no category**, in Do Not Disturb | Q1: sound, as before (`buzz=1, beep=1`). Q2: **the user felt a vibration** (`buzz=1, beep=0`): a channel vibration of 1 ms doesn't prevent it. Q3: **no alert at all**, no pop-up, in the shade: Do Not Disturb holds back a notification without the *reminder* category. |

Afterwards the probe's notifications and both channels were removed (`probe.CLEANUP`). Autostart was left **on**, as the user turned it.

**What T1 and T2 mean for P7's reminder check (`RM-9`),** on this phone:

- **Notification permission:** needed (Android).
- **Exact alarms:** `USE_EXACT_ALARM` is granted at install on Android 13 and 16; the check only confirms it.
- **Battery optimisation, and HyperOS's battery saver:** not needed. With both at their defaults, alarm clocks were on time in every case: screen off, light and deep Doze, the process ended, a swipe from Recents, after an update. Exempting the app didn't help exact alarms either.
- **Autostart:** needed only for `MY_PACKAGE_REPLACED`, so that reminders come back right after an app update, which removes them. With it off, `BOOT_COMPLETED` still arrived after a restart.
- **HyperOS's notification settings** for the app, "Floating notifications" and "Lock screen notifications": on by default. Worth listing as steps to confirm, since turning them off would hide the pop-up and the lock screen content.

**HyperOS keeps at most 10 notifications per app:** when DND1 was posted, with 10 of the app's notifications already in the shade (A to M4 and the summary), HyperOS cancelled the oldest, `take/A`, 2 ms later (`notification_canceled … take/A … reason 8`, `REASON_APP_CANCEL`, though the app cancelled nothing). The shade then held 9 reminders and the summary. A medicine has at most one Due take, so this matters only with 10 or more medicines due at once; the reconcile would post the dropped one again, which would drop another.

**Both contradict the specification:** `RM-8` says a reminder appears without sound in Do Not Disturb, and `RM-2` and spec Q20 say it never vibrates. To decide with the user (SPEC Q6, Q7): leaving out the *reminder* category would let Do Not Disturb silence it; giving the channel its own imperceptible vibration (a pattern of 1 ms) would stop Android's fallback vibration in vibrate mode. Or the specification changes.

- Each record shows `timeout=PT72H`, which the probe didn't set: to check whether Android 16 removes a notification after 72 hours. The reconcile would post it again at the next run (P4-D2), but a weekly medicine's reminder can stay Due for a week.

## Known issues

- **A number written as text reads as the number:** kotlinx.serialization accepts `"alerts": "2"` as 2, in strict mode too, as it does for every number of the format (`amount`, `count`), since P3. Nothing is lost: the next write writes a number. `DamagedDataTest` tests a count of 2.5 instead.
- **Android 16 may time notifications out:** the emulator's records showed `timeout=PT72H`, which the probe didn't set. Not checked: a weekly medicine's reminder can stay Due for a week. The reconcile posts a missing reminder again, silently, so it would come back at the next reconcile. To look at in T5 or T8 on the emulator.
- **The debug app on the phone keeps the probe's `files/probe.log`** (times and tags only, no content). Harmless; it goes when the app's storage is cleared.
- **For the skills review** (`phase-close`): the shell can change the emulator's time and zone with `cmd alarm set-time` and `set-timezone` (the `phone` skill says it can't); `notification_alert` in `logcat -b events` gives `[key, buzz, beep, blink]` for each alert, though `beep=1` doesn't mean the ring mode let the sound play; wireless debugging reconnects by itself after a restart, sometimes a minute after the unlock; HyperOS keeps at most 10 notifications per app.
- From earlier phases: AGP's Gradle 10 deprecation notice and ktlint's `sun.misc.Unsafe` warnings on JDK 25 are harmless (P1).

## Pending wiring

Connections to other phases left for later.

- **P6 (Take screen):** a tapped reminder opens `TakeRoute(medicineId, plannedAt)`, P1's placeholder until P6 replaces it. The route and its arguments stay as they are. An answer given on the Take screen removes its reminder by itself, through the reconcile on every change (SPEC P5-D2): nothing to wire.
- **P6 (minutes in debug builds):** the medicine editor offers the minutes unit in debug builds, with `validate(allowMinutes = BuildConfig.DEBUG)` (plan `D-15`).
- **P7 (reminder check, `RM-9`):** what T1 and T2 found on this phone (SPEC §4.6): the notification permission is needed; `USE_EXACT_ALARM` is granted at install; battery optimisation and HyperOS's battery saver aren't needed for alarm clocks, though `RM-9` still asks the check to show battery optimisation; **Autostart** is needed so that reminders come back right after an app update (`MY_PACKAGE_REPLACED`), not for restarts; HyperOS's "Floating notifications" and "Lock screen notifications" are on by default, and worth listing as steps to confirm. P5 declares `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for the check. Once the check gets the notification permission granted, `MainActivity.onStart`'s reconcile posts the reminders the plan holds. Taken on the home screen removes the reminder by itself (P5-D2).
- **P8:** `NF-1` and `NF-2` again on the release build, which has no debug screen and no reminder log.

## How to verify

The commands and manual steps that check the current state.

- `./gradlew build` (CLAUDE.md shows how to keep its exit code and filter its output).
- The measurements: "Measurements" above, and SPEC §4.6.

**Checks at the end of 2026-11-21's second session:** no build was run. The branch's code is still the same as `main`'s (`git diff --stat origin/main..HEAD -- app/ core/ gradle/ '*.gradle.kts'` prints nothing), and CI passed on `main` at `cc80648`, whose last code change is P4's `ef7988e`; the commits since are documents. No instrumented run and no manifest comparison, since nothing in `app/` changed in git. The probes were built and installed only on the devices, then deleted.

**Device state after T5:** the phone has this branch's debug build with T5 (no alarm, no notification, empty data), and the debug app has `POST_NOTIFICATIONS`, granted by `ReminderShadeTest`. The emulator has the same build, with empty data and the permission; it is shut down.

**Device state left behind earlier:** the phone has this branch's clean debug build (installed with `gradle installDebug` after the probes were deleted), with no alarm and no notification. The debug app's notification permission went with the probe's manifest. HyperOS's battery saver is at its default; **Autostart is on**, as the user turned it for the last run. Normal ring mode, Do Not Disturb off, Doze not forced (`doze_off` ran after both forced runs). The phone was restarted twice, by the user. The emulator had its time and zone changed from the shell and put back (automatic time and zone stayed on), and is shut down.

## Session log

Newest first.

### 2026-11-21, evening: T5

- Done: T5, the Android side. `./gradlew build` passes: `:app` 117 unit tests, `:core` 177, no failures; Lint "No issues found"; ktlint and the permission allowlist pass.
- Instrumented tests: every one passes on the emulator (API 36) and on the phone (Android 13, HyperOS, unlocked; `instrument` granted op 10021 again after the update): `OK (19 tests)` on both, `StoreDeviceCheck`'s two skipped by their assumption. The shade test grants the debug app `POST_NOTIFICATIONS` through `UiAutomation` and posts only silent reminders.
- A first reminder end to end on the emulator, with a throwaway instrumented test that saved a "Test" treatment (every 45 minutes, first take 2 minutes ahead) in the real store, then deleted it; the test is deleted. The observer reconciled and set the alarm clock for 16:03; at 16:03 the alarm started the process and the reconcile posted "Test A: 1 tablet / Test · with food", **433 ms late**, with the summary "1 take due" and the repeat's alarm at 16:13. Taken, tapped in the emulator's shade, reached `ReminderActionReceiver` ("action taken … applied"), removed the reminder, and moved the alarm to the next take at 16:48. Deleting the treatment cancelled the alarm and left the store's file as `{"schemaVersion":1,"treatments":[],"answers":[]}`. `MY_PACKAGE_REPLACED` reconciled at the install (the emulator has no HyperOS).
- Manifests and libraries (`compare-manifests.sh`): debug and release each gain exactly `AlarmReceiver`, `ReminderActionReceiver`, `SystemEventReceiver` and the four permissions of plan §3; the release libraries are the same as `main`'s (120). In the merged release manifest the three receivers are `exported="false"`, the boot receiver's filter has `android:priority="999"`, and nothing sets `android:process`.
- **Not done yet:** `ReminderChannelTest` and `ReminderShadeTest` passed on their first run, and haven't been shown able to fail (CLAUDE.md); `WakeUpAlarmTest` failed first, on the duplicate blocks. To do with T6, on the emulator: one deliberate bug at a time in `AndroidReminderShade` and `ReminderChannel` (for example the category set back, `VISIBILITY_PRIVATE`, the group alert behaviour swapped, an action dropped, vibration on), each run with `instrument reminders.ReminderShadeTest,reminders.ReminderChannelTest`. A changed channel needs a fresh install, since Android keeps a channel's settings.
- Notes: `dumpsys alarm` prints a pending alarm in more than one list; `WakeUpAlarmTest` counts distinct `Alarm{<hash>` objects. Its header line holds `origWhen <epoch ms>`, and an alarm clock has an `Alarm clock:` section with its `showIntent`.

### 2026-11-21, evening: T4

- Done: T4, the reconcile on the JVM. `./gradlew build` passes: `:app` 117 unit tests (33 new, in `reminders`), `:core` 177, no failures; Lint, ktlint and the permission allowlist pass. Three tests failed at first, each because its expectation forgot that both 08:00 takes alerted (two removals at 16:00; both repeat at 08:10 and 08:15): the tests were corrected, not the code.
- Tests that can fail: twelve bugs, one at a time, each caught, each file restored and checked with `cmp`:
  - silent reminders posted at every reconcile → `ReconcilerTest` (between alerts; the second reconcile; the edit; the swipe; the timeline);
  - silent reminders never posted → `ReconcilerTest` (a plan with no change; the edit; the swipe; the timeline);
  - the alarm set after the posts → the order test;
  - no retry alarm after a failure → both failure tests;
  - Unreadable treated as a failed write → the Unreadable test;
  - a failure logged with its message → the class-only test;
  - a repeat's lateness counted from the planned time → the lateness test;
  - the observer reconciling only on the first data → both observer tests;
  - no reconcile after an answer → all four answer tests;
  - `runAsync` without its guard → the `runAsync` test;
  - `TakeKeyCodec` accepting seconds → its refusal test;
  - the fingerprint leaving out the instructions → the fingerprint test.
- Notes: ktlint split two long `if`s inside lambdas in the tests; replaced with a local `val` and a helper, as CLAUDE.md says. The observer's tests run `Reminders` in a scope of its own on the test's scheduler, so `advanceUntilIdle()` runs it, cancelled at the end of each test.

### 2026-11-21, evening: T3

- Done: T3, the reminder state in the store. `./gradlew build` passes: `:app` 84 unit tests, `:core` 177, no failures; Lint, ktlint and the permission allowlist pass. The golden file `pills-v1-reminders.json` was made as CLAUDE.md says, from a throwaway test, and checked by eye against SPEC §4.3.7; the test is deleted. CLAUDE.md's line on `core.reminders` now says the state is in `PillsData`.
- Tests that can fail: eight bugs, one at a time, each caught, each file restored and checked with `cmp`:
  - the records not sorted → `ReminderFormatTest` (sorted);
  - `readInstant` without its form check → `DamagedDataTest` (an instant with an offset; the reasons);
  - no check that a record is one kind → `DamagedDataTest` (both kinds; the reasons);
  - no range check on the count → `DamagedDataTest` (the reasons; Alerted's own check still refuses it, as `IllegalArgumentException`);
  - the records not written → `ReminderFormatTest`, `DamagedDataTest`, `DataStoreTreatmentRepositoryTest` (the write check refuses);
  - `deleteTreatment` keeping the records → `DeleteTreatmentTest`;
  - `saveTreatment` keeping a removed medicine's record → `SaveTreatmentTest`;
  - a refused snooze returning the data → `ReminderActionsTest` (both refusal tests).
- Notes: `DamagedDataTest`'s "an unknown top-level field" used `"reminders"`, now part of the format; it uses `"snoozes"` instead. No instrumented run: the store's format changed, so T5 runs the whole suite on both devices.

### 2026-11-21, afternoon

- Done: the rest of T1 (forced deep Doze, the process ended, a swipe, two restarts, an update, a *Force stop*, the emulator's A1 and A6) and all of T2 (N1–N7 on the phone, with the user listening and looking, and two fix variants), with alarm clocks and stock settings. The user decided Q6 (silent in Do Not Disturb) and Q7 (vibrate in vibrate mode). Specification v0.8, plan v0.6 and SPEC v0.4 on `main`, merged into the branch. The probes deleted; the clean build installed on the phone.
- Notes: Android won't enter deep Doze in the 30 minutes before an alarm clock, even when forced: a forced-Doze test needs the alarm clock more than 30 minutes ahead. A *Force stop* leaves the app unable to receive even the shell's broadcasts until it is opened. HyperOS's "Silent mode" reads as vibrate (`mode_ringer=1`) inside Android but doesn't vibrate; its vibrate mode does.

### 2026-11-21, morning

- Done: read the overnight run (A3 night): 60 alarm clocks, all on time, in light Doze only. Recorded it; planned the rest of T1 in "Next steps".
- Notes: stopped because the user isn't available for the hands-on runs (swipe, restart). Deep Doze doesn't start while an alarm clock is due within 30 minutes, so a natural deep-Doze run needs alarms more than an hour apart.

### 2026-11-20, evening

- Done: T1's probe; A1 on the phone; A2 with the screen off, three runs (stock twice, battery saver "No restrictions" once); A8 with alarm clocks. They showed HyperOS's 5-minute grid for exact alarms, so the alarm became an alarm clock, agreed with the user: plan v0.5, SPEC v0.3 (Q5), merged into the branch. Started the overnight run (A3 night).
- Notes: the phone's screen turns off with `input keyevent KEYCODE_SLEEP` (wakefulness `Dozing`). Don't poll the phone over adb during a run: wait locally, then read the log. Logcat's times are local (UTC+1), the probe's are UTC.

### 2026-11-20

- Done: SPEC.md v0.1 and this handover, from the plan, the specification (v0.6), the code P5 builds on, and P1–P4's handovers. The review confirmed Q1–Q4: specification v0.7 (`RM-4`, `RM-5`), plan v0.4 (§2.4, §3), SPEC v0.2, Ready. The phase is in progress, on `phase/p5-reminders`.
- Notes: P4's SPEC still quotes `RM-4` and `RM-5` as in specification v0.6; P4 is done, so its SPEC stays as the record of what it built.
