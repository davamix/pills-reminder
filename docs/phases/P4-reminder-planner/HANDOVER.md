# P4 — Reminder planner: Handover

| | |
|---|---|
| **Status** | In progress |
| **Spec** | [SPEC.md](SPEC.md), version v0.2 |
| **Branch** | `phase/p4-reminder-planner` |
| **Last session** | 2026-11-20 |

This document tracks the phase as it's built. Update it at the end of every session, so the next one can continue from here.

## Prompt to start or continue

Paste this prompt into a new Claude Code session to start or continue the phase. When the phase is done, replace it with "Phase done: no prompt needed."

```text
Start or continue phase P4 (Reminder planner) of the pills reminder.

1. Use the phase-start skill for P4. Read only docs/phases/P4-reminder-planner/HANDOVER.md and SPEC.md; they contain everything needed.
2. Carry on from "Next steps" in the handover. Work through the tasks in SPEC.md §7 in order, on branch phase/p4-reminder-planner. Commit after each task, once the build passes.
3. Follow SPEC.md. If something in it is wrong or unclear, stop and ask me before changing course. Record every decision or deviation in the handover.
4. No device is needed: P4 is pure Kotlin in :core, and nothing in :app changes, so there are no instrumented tests or phone checks. Reuse P2's test helpers (core/src/test/.../ExampleData.kt and Invariants.kt). P6 or P7 may run in parallel: if this working copy is busy with another phase, use a worktree (phase-start, step 3). P4 doesn't add the reminder state to PillsData or to the JSON format; that is P5's (SPEC P4-D6).
5. Before the session ends, or once all tasks are done, use the handover skill. When the done criteria pass, open the pull request and wait for me to merge it.
```

## Current state

Started. SPEC.md v0.2 is `Ready`: the review confirmed Q1–Q3, which went into specification v0.6. The branch `phase/p4-reminder-planner` exists; no code yet.

## Next steps

1. Start with `T1`: the reminder state (`ReminderState`, `TakeReminder`, `snooze()`) and `ReminderStateTest`.

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | Done | `ReminderState`, `TakeReminder`, `snooze()`, and `ReminderPlanner`'s constants. `ReminderStateTest`: 14 tests. |
| T2 | Done | `ReminderPlanner.plan()`, `ReminderPlan`, `Reminder`, `removals`, and the clock check of §4.3.8. `ReminderPlannerTest`: 31 tests, each also checking the one-plan properties of §4.3.10 (`ReminderChecks.kt`: `assertPlanProperties`, `instant()`, `dueTakes()`), including a plan again at now, halfway to the next wake-up and 1 ms before it. Two deliberate mutations of the planner (no pull-back; a repeat 1 minute early) each failed tests, then were reverted. |
| T3 | To do | |
| T4 | To do | |

## Decisions and deviations

Anything decided during implementation that isn't in SPEC.md, or that changes it. A change to a requirement goes into SPEC.md or SPECIFICATION.md first.

| Date | Decision | Reason |
|---|---|---|
| 2026-11-20 | `TakeReminder.Snoozed` and `Alerted` refuse a time that isn't whole seconds (`IllegalArgumentException`, with only the time in the message). Not listed in SPEC §4.2. | P4-D5 says records hold whole seconds. Checking it in the type, as P2's `Schedule` checks whole minutes, means P5's decoding refuses any other form, and a planner bug can't store one. |

## Known issues

- From earlier phases: AGP's Gradle 10 deprecation notice and ktlint's `sun.misc.Unsafe` warnings on JDK 25 are harmless (P1).

## Pending wiring

Connections to other phases left for later.

- **P5 (reminder state in the store):** add `reminders: ReminderState` to `PillsData`, and to the JSON format in the same change, with a golden test (CLAUDE.md; P3 SPEC §4.3.6). SPEC P4-D6 suggests a shape. Then add `PillsData.snooze(key, now)` and a repository helper that runs it inside `update`, like P2's actions.
- **P5 (the reconcile):** apply plans as SPEC §4.3.7 says: work out the plan inside `update` on the latest data and store `recorded`, then remove `removals(…)` of the app's notifications in the shade, post the plan's reminders (with sound only when `alert` is true), and set or cancel the single alarm at `nextWakeUp`. Run a reconcile after every change, including a snooze.
- **P5 (deleting):** a deleted treatment's or removed medicine's records go at the next reconcile (P4-D4). If P5 wants them gone in the same write, `deleteTreatment` and `saveTreatment` can drop them once the field is in `PillsData`.
- **P5 (notifications):** what swiping a reminder away does (the plan lists it again at every reconcile while its take is Due), and whether reminders that alert at the same moment play one sound (`RM-4`).

## How to verify

- `./gradlew build` (CLAUDE.md shows how to filter its output and total the test results). `:core`'s tests include the planner's.
- No manual checks and no device: see SPEC.md §6.

## Session log

Newest first.

### 2026-11-20

- Done: SPEC.md v0.1 and this handover written (`phase-docs`). Plan §5.1: P4 is `Spec draft`.
- Notes: the SPEC's questions Q1–Q3 wait for review.
