# P5 — Reminders on Android: Handover

| | |
|---|---|
| **Status** | Not started |
| **Spec** | [SPEC.md](SPEC.md), version v0.1 |
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

Not started. The SPEC is a draft (v0.1), waiting for review of Q1–Q4. P3 and P4 are merged, so P5 builds on the real store and the real planner; no fakes are needed.

## Next steps

1. Once SPEC.md is `Ready`: start with T1, the measurements of alarms and broadcasts on the phone (SPEC §4.6, A1–A8), with stock HyperOS settings first.

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

- Done: SPEC.md v0.1 and this handover, from the plan, the specification (v0.6), the code P5 builds on, and P1–P4's handovers.
- Notes: waiting for the review of SPEC §9 (Q1–Q4).
