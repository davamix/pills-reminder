# P5 — Reminders on Android: Handover

| | |
|---|---|
| **Status** | In progress |
| **Spec** | [SPEC.md](SPEC.md), version v0.2 |
| **Branch** | `phase/p5-reminders` |
| **Last session** | 2026-11-20 |

This document tracks the phase as it's built. Update it at the end of every session, so the next one can continue from here.

## Prompt to start or continue

Paste this prompt into a new Claude Code session to start or continue the phase. When the phase is done, replace it with "Phase done: no prompt needed."

```text
Start or continue phase P5 (Reminders on Android) of the pills reminder.

1. Use the phase-start skill for P5. Read only docs/phases/P5-reminders/HANDOVER.md and SPEC.md; they contain everything needed.
2. Carry on from "Next steps" in the handover. Work through the tasks in SPEC.md §7 in order, on branch phase/p5-reminders. Commit after each task, once the build passes.
3. Follow SPEC.md. If something in it is wrong or unclear, stop and ask me before changing course. Record every decision or deviation in the handover.
4. The phone is needed for T1, T2 and T8, and for the manual checks of other tasks, with me nearby: I swipe the app away, restart the phone and unlock it, listen for the sound, and look at the lock screen. Ask before changing a setting on the phone, forcing Doze, restarting it or granting a permission, and put things back; never change its clock or time zone. The emulator is needed from T5 on, for instrumented tests and the time and zone checks. T1 and T2 use throwaway probes that are never committed. If a measurement contradicts SPEC.md (for example exact alarms late in Doze, which would mean D-8's fallback to setAlarmClock), stop and ask me before changing the design.
5. Before the session ends, or once all tasks are done, use the handover skill. When the done criteria pass, open the pull request and wait for me to merge it.
```

## Current state

Started: the branch exists, and no code has been written yet. The SPEC is Ready (v0.2), with Q1–Q4 answered. P3 and P4 are merged, so P5 builds on the real store and the real planner; no fakes are needed.

## Next steps

1. T1, the measurements of alarms and broadcasts on the phone (SPEC §4.6, A1–A8), with stock HyperOS settings first. Write the throwaway probe in the debug source set, and never commit it.

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | To do | Measure alarms and broadcasts (phone, emulator) |
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

## Measurements (T1, T2)

The phone: POCO F3, Android 13, HyperOS `OS1.0.6.0.TKHEUXM`, on wireless debugging only, unplugged. Times are UTC, as the probe logs them; logcat shows local time (UTC+1). The probe is a debug-only receiver, never committed, that logs each alarm's lateness, `isDeviceIdleMode`, `isInteractive` and the process to `files/probe.log`.

**Stock settings:** battery optimisation on (read with `reminder_health`), standby bucket 40 before the install and 10–20 after it. HyperOS's battery saver for the app: "Battery saver", its default (the user). Autostart not yet confirmed.

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

- `./gradlew build` (CLAUDE.md shows how to keep its exit code and filter its output).

## Session log

Newest first.

### 2026-11-20

- Done: SPEC.md v0.1 and this handover, from the plan, the specification (v0.6), the code P5 builds on, and P1–P4's handovers. The review confirmed Q1–Q4: specification v0.7 (`RM-4`, `RM-5`), plan v0.4 (§2.4, §3), SPEC v0.2, Ready. The phase is in progress, on `phase/p5-reminders`.
- Notes: P4's SPEC still quotes `RM-4` and `RM-5` as in specification v0.6; P4 is done, so its SPEC stays as the record of what it built.
