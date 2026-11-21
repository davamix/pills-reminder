# P5 — Reminders on Android: Handover

| | |
|---|---|
| **Status** | In progress |
| **Spec** | [SPEC.md](SPEC.md), version v0.3 |
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
4. The phone is needed for T1, T2 and T8, and for the manual checks of other tasks, with me nearby: I swipe the app away, restart the phone and unlock it, listen for the sound, and look at the lock screen. Ask before changing a setting on the phone, forcing Doze, restarting it or granting a permission, and put things back; never change its clock or time zone. The emulator is needed from T5 on, for instrumented tests and the time and zone checks. T1 and T2 use throwaway probes that are never committed. If a measurement contradicts SPEC.md, stop and ask me before changing the design. T1's probe lives in app/src/debug/ and is kept out of git by .git/info/exclude; remove those lines from it when the probe is deleted, before T6 adds the real debug manifest. T1 is half done: the rest needs me with the phone for about an hour, so ask me first whether I'm available; if I'm not, ask whether to go ahead with T3 and T4, which need no device.
5. Before the session ends, or once all tasks are done, use the handover skill. When the done criteria pass, open the pull request and wait for me to merge it.
```

## Current state

T1 is half done, and no app code has been written yet. The branch's code is the same as `main`'s. Stopped because the user isn't available for the hands-on tests.

What T1 found so far (details in "Measurements"):

- `USE_EXACT_ALARM` is granted on the phone.
- With the screen off, HyperOS holds exact alarms (`setExactAndAllowWhileIdle()`) until a 5-minute grid, up to 4 min 15 s late, also with the battery saver at "No restrictions". Alarm clocks are on time. So the alarm is an alarm clock: SPEC v0.3 (Q5), plan v0.5 (`D-8`).
- Overnight, 60 alarm clocks 10 minutes apart, stock settings, unplugged: all on time, the worst 1.7 s late. The phone stayed in light Doze: deep Doze never starts while an alarm clock is due within 30 minutes.

Still to measure: alarm clocks in deep Doze (forced, then natural), after a swipe from Recents, after the process ends, after a restart and an update, a *Force stop*; then the emulator's A1 and A6. T2 (notifications) after that.

The throwaway probe is `app/src/debug/AndroidManifest.xml` and `app/src/debug/kotlin/io/github/davamix/pillsreminder/probe/Probe.kt`, kept out of git by two lines in `.git/info/exclude`. It exists only in this working copy (`F:\Development\pills-reminder`); in a fresh clone, write it again from this description: a receiver that isn't exported, for its alarm and the four system broadcasts, which logs each one's lateness, `isDeviceIdleMode`, `isInteractive`, the pid and the process start to logcat (`AlarmProbe`) and `files/probe.log`, and chains the next alarm; and `ProbeControl`, exported with `android:permission="android.permission.DUMP"`, for commands from adb. It is installed on the phone, and its log holds every run so far. Commands go through `ProbeControl`, which only the shell can reach (it needs `DUMP`):

```bash
source .claude/skills/phone/helpers.sh phone && ctl="$pkg/io.github.davamix.pillsreminder.probe.ProbeControl"
$adb shell am broadcast -n "$ctl" -a probe.SET --es tag <tag> --el delay_s 120 --ei count 3 --el interval_s 180 --es mode clock   # or mode exact
$adb shell am broadcast -n "$ctl" -a probe.MARK --es text "<step>"
$adb exec-out run-as $pkg cat files/probe.log
```

## Next steps

1. Ask the user whether they can be with the phone for about an hour. The phone must be left alone, screen off, during each run; check that with `dumpsys power` (`mWakefulness`), and turn the screen off with `input keyevent KEYCODE_SLEEP` if needed. All with alarm clocks (`--es mode clock`) and stock settings (Autostart off, battery saver default):
   1. **Forced deep Doze:** three alarm clocks 3 minutes apart; `doze_on` once they are set, `doze_state` must say `deep=IDLE`, and `doze_off` afterwards from a `trap`. About 12 minutes; no help needed.
   2. **Process ended:** an alarm clock 3 minutes ahead, then `end_process`, checked with `pidof`. The log's `pid` and `procStart` show a new process. About 5 minutes; no help needed.
   3. **Swipe:** the user opens the app and swipes it away from Recents; then an alarm clock 3 minutes ahead.
   4. **Restart (A4):** an alarm clock a few minutes ahead; the user restarts the phone and unlocks it only after that time. Record the `BCAST android.intent.action.BOOT_COMPLETED` line against the unlock (`am_user_state_changed` or `wm_set_keyguard_shown` in `logcat -b events`). The alarm doesn't survive a restart; the real app sets it again. Wireless debugging may need reconnecting.
   5. **Update (A5):** an alarm clock 5 minutes ahead, then `gradle installDebug`: check `alarms` before and after, and the `MY_PACKAGE_REPLACED` line. Note that the install resets op 10021 (`instrument` grants it again).
   6. **Force stop (A7):** an alarm clock ahead, `force_stop`, then `alarms` should list nothing.
2. **Natural deep Doze,** best at night: a chain of alarm clocks 3 hours apart (`--ei count 3 --el interval_s 10800`), the phone unplugged and still. Check the idling history for `deep-idle` before each alarm.
3. **On the emulator:** A1 (`reminder_health`), and A6: change the time, then the time zone, by hand in *Settings > System > Date & time*, with the app running; the `BCAST` lines must show `TIME_SET` and `TIMEZONE_CHANGED`, and `zone` and `clockZone` the new zone in the same `pid`. Put automatic time and zone back.
4. Relax the settings (Autostart on) only where a stock run fails, and record what each setting changed: that is P7's list.
5. Then T2. Delete the probe and its lines in `.git/info/exclude` once T2 is done.
6. If the user isn't available, ask whether to go ahead with T3 and T4 meanwhile: they need no device, and no pending measurement changes them.

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | Doing | Done: A1 on the phone, A2 with the screen off, A8, A3 overnight (light Doze). Changed `D-8` (SPEC v0.3, Q5). To do: deep Doze, process end, swipe, restart, update, Force stop, emulator A1 and A6. |
| T2 | To do | Measure notifications (phone, emulator) |
| T3 | To do | Reminder state in the store |
| T4 | To do | The reconcile on the JVM |
| T5 | To do | Android: channel, shade, alarm, receivers, manifest |
| T6 | To do | Debug tools |
| T7 | To do | Opening a take |
| T8 | To do | Device runs (phone, emulator) |
| T9 | To do | Wrap-up and pull request |

## Decisions and deviations

Anything decided during implementation that isn't in SPEC.md, or that changes it. A change to a requirement goes into SPEC.md or SPECIFICATION.md first.

| Date | Decision | Reason |
|---|---|---|
| 2026-11-20 | The alarm is an alarm clock, `setAlarmClock()` (`D-8`'s fallback). Agreed with the user; in SPEC v0.3 (Q5) and plan v0.5. | T1: with the screen off, `setExactAndAllowWhileIdle()` was held until a 5-minute grid, up to 4 min 15 s late, also exempt from battery optimisation; alarm clocks were within 0.1 s. |
| 2026-11-20 | The probe takes commands over adb through an exported receiver protected by `android.permission.DUMP`, instead of probe tests. | Only the shell holds `DUMP`, so no other app can reach it, and `am instrument` would end the app's process after each test. It is never committed. |

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
- Each record shows `timeout=PT72H`, which the probe didn't set: to check whether Android 16 removes a notification after 72 hours. The reconcile would post it again at the next run (P4-D2), but a weekly medicine's reminder can stay Due for a week.

## Known issues

- From earlier phases: AGP's Gradle 10 deprecation notice and ktlint's `sun.misc.Unsafe` warnings on JDK 25 are harmless (P1).

## Pending wiring

Connections to other phases left for later.

- **P6 (Take screen):** a tapped reminder opens `TakeRoute(medicineId, plannedAt)`, P1's placeholder until P6 replaces it. The route and its arguments stay as they are. An answer given on the Take screen removes its reminder by itself, through the reconcile on every change (SPEC P5-D2): nothing to wire.
- **P6 (minutes in debug builds):** the medicine editor offers the minutes unit in debug builds, with `validate(allowMinutes = BuildConfig.DEBUG)` (plan `D-15`).
- **P7 (reminder check):** the HyperOS settings and defaults that T1 and T2 find in the way go on the check's list (`RM-9`). P5 declares `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for it. Once the check gets the notification permission granted, `MainActivity.onStart`'s reconcile posts the reminders the plan holds. Taken on the home screen removes the reminder by itself (P5-D2).
- **P8:** `NF-1` and `NF-2` again on the release build, which has no debug screen and no reminder log.

## How to verify

The commands and manual steps that check the current state.

- `./gradlew build` (CLAUDE.md shows how to keep its exit code and filter its output). Run it only with the probe in place if that's intended: it compiles and lints the debug source set too.
- The measurements so far: `source .claude/skills/phone/helpers.sh phone && $adb exec-out run-as $pkg cat files/probe.log`, and "Measurements" above.

**Checks at the end of 2026-11-21's session:** no build was run. The branch's code is the same as `main`'s (`git diff --stat origin/main..HEAD -- app/ core/ gradle/ '*.gradle.kts'` prints nothing), and CI passed on `main` at `cc80648`; its last code change is P4's `ef7988e`. No instrumented run, and no manifest comparison, since nothing in `app/` changed in git. The uncommitted probe was built with `assembleDebug` and installed with `gradle installDebug`.

**Device state left behind:** the phone has the debug app with the probe installed, and its `files/probe.log`. No probe alarm is pending (`alarms`). The battery saver is back to its default (`reminder_health`: optimised), Autostart is off, Doze was never forced, and no setting was changed over adb; the screen was turned off with `KEYCODE_SLEEP` before runs. The emulator wasn't used.

## Session log

Newest first.

### 2026-11-21

- Done: read the overnight run (A3 night): 60 alarm clocks, all on time, in light Doze only. Recorded it; planned the rest of T1 in "Next steps".
- Notes: stopped because the user isn't available for the hands-on runs (swipe, restart). Deep Doze doesn't start while an alarm clock is due within 30 minutes, so a natural deep-Doze run needs alarms more than an hour apart.

### 2026-11-20, evening

- Done: T1's probe; A1 on the phone; A2 with the screen off, three runs (stock twice, battery saver "No restrictions" once); A8 with alarm clocks. They showed HyperOS's 5-minute grid for exact alarms, so the alarm became an alarm clock, agreed with the user: plan v0.5, SPEC v0.3 (Q5), merged into the branch. Started the overnight run (A3 night).
- Notes: the phone's screen turns off with `input keyevent KEYCODE_SLEEP` (wakefulness `Dozing`). Don't poll the phone over adb during a run: wait locally, then read the log. Logcat's times are local (UTC+1), the probe's are UTC.

### 2026-11-20

- Done: SPEC.md v0.1 and this handover, from the plan, the specification (v0.6), the code P5 builds on, and P1–P4's handovers. The review confirmed Q1–Q4: specification v0.7 (`RM-4`, `RM-5`), plan v0.4 (§2.4, §3), SPEC v0.2, Ready. The phase is in progress, on `phase/p5-reminders`.
- Notes: P4's SPEC still quotes `RM-4` and `RM-5` as in specification v0.6; P4 is done, so its SPEC stays as the record of what it built.
