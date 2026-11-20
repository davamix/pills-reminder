# P4 — Reminder planner: Handover

| | |
|---|---|
| **Status** | Done. Merged in pull request [#4](https://github.com/davamix/pills-reminder/pull/4) on 2026-11-20. |
| **Spec** | [SPEC.md](SPEC.md), version v0.2 |
| **Branch** | `phase/p4-reminder-planner` |
| **Last session** | 2026-11-20 |

This document tracks the phase as it's built. Update it at the end of every session, so the next one can continue from here.

## Prompt to start or continue

Phase done: no prompt needed.

## Current state

Merged into `main` in pull request [#4](https://github.com/davamix/pills-reminder/pull/4) (merge commit `d87ac3f`), with CI green on the branch's last commit `ead4f7a` ([run](https://github.com/davamix/pills-reminder/actions/runs/44806611884)).

- **What's built:** `io.github.davamix.pillsreminder.core.reminders`. It holds `ReminderState` (one record per Due take, `Snoozed` or `Alerted`, in whole seconds), `snooze()`, and `ReminderPlanner.plan()`. The plan returns the reminders the shade should hold, which of them alert, the next wake-up, the records to store and `removals(inShade)`. Nothing in `:app` uses it yet: P5 wires it in.
- **Checks:** `./gradlew build` passes: 173 unit tests in `:core` (56 new: 14 + 31 + 11) and 60 in `:app`, 0 failures; lint, ktlint and the permission allowlist pass. Four deliberate planner bugs each made tests fail, then were reverted. No instrumented run or device check: nothing in `:app` changed (SPEC §6). The manifests and dependencies are the same as `main`'s, because no build file or `:app` file changed.
- **Documents:** SPEC.md v0.2, with every done criterion ticked; specification v0.6; plan §5.1 shows P4 `Done`. `CLAUDE.md` describes the `reminders` package.

## Next steps

None for P4. These phases can start now, since all their dependencies are `Done`: P5 (reminders on Android), P6 (treatment management UI) and P7 (home screen and reminder check). None has a SPEC.md or HANDOVER.md yet; the `phase-docs` skill writes them. P5 is on the critical path and is the riskiest phase (plan §5.1).

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | Done | `ReminderState`, `TakeReminder`, `snooze()`, and `ReminderPlanner`'s constants. `ReminderStateTest`: 14 tests. |
| T2 | Done | `ReminderPlanner.plan()`, `ReminderPlan`, `Reminder`, `removals`, and the clock check of §4.3.8. `ReminderPlannerTest`: 31 tests, each also checking the one-plan properties of §4.3.10 (`ReminderChecks.kt`: `assertPlanProperties`, `instant()`, `dueTakes()`), including a plan again at now, halfway to the next wake-up and 1 ms before it. Two deliberate mutations of the planner (no pull-back; a repeat 1 minute early) each failed tests, then were reverted. |
| T3 | Done | `FakePhone.kt`: the fake phone (shade, alarm, store; walks with exact or late alarms, random reconciles, responses and events), the two oracles (`expected` with exact alarms and responses, `expectedAt` from the moments the phone reconciled), and `assertSameLog`. `ReminderWalkTest`: 11 tests, 5 seeds each for the long walks, 1.5 s in all. Two mutations of the planner that only walks see (a snooze's end not a wake-up; a repeat alarm a minute late) each failed walks, then were reverted. |
| T4 | Done | Every row of SPEC §6 and every rule of §4.3 checked against the tests; KDoc on the public API; `CLAUDE.md` describes the `reminders` package; `build` passes; pull request [#4](https://github.com/davamix/pills-reminder/pull/4) open, CI green. |

## Decisions and deviations

Anything decided during implementation that isn't in SPEC.md, or that changes it. A change to a requirement goes into SPEC.md or SPECIFICATION.md first.

| Date | Decision | Reason |
|---|---|---|
| 2026-11-20 | `TakeReminder.Snoozed` and `Alerted` refuse a time that isn't whole seconds (`IllegalArgumentException`, with only the time in the message). Not listed in SPEC §4.2. | P4-D5 says records hold whole seconds. Checking it in the type, as P2's `Schedule` checks whole minutes, means P5's decoding refuses any other form, and a planner bug can't store one. |
| 2026-11-20 | Test helpers beyond SPEC §4.1's list: `ReminderChecks.kt` (`assertPlanProperties`, `instant()`, `dueTakes()`), shared by the planner tests and the walks. `FakePhone.kt` also holds the oracles and `assertSameLog`. | §4.1's file names are suggestions. Each plan in every test, the walks' included, is checked against the one-plan properties of §4.3.10. |
| 2026-11-20 | The walks' responses come 13 seconds past a minute (`after` = whole minutes + 13 s). | Alerts and Missed times fall on whole minutes, or a whole number of 13 s steps after them, so no response ties with an alarm. The oracle doesn't need a rule for which comes first. |

## Known issues

- From earlier phases: AGP's Gradle 10 deprecation notice and ktlint's `sun.misc.Unsafe` warnings on JDK 25 are harmless (P1).

## Pending wiring

Connections to other phases left for later.

- **P5 (reminder state in the store):** add `reminders: ReminderState` to `PillsData`, and to the JSON format in the same change, with a golden test (CLAUDE.md; P3 SPEC §4.3.6). SPEC P4-D6 suggests a shape. Then add `PillsData.snooze(key, now)` and a repository helper that runs it inside `update`, like P2's actions.
- **P5 (the reconcile):** apply plans as SPEC §4.3.7 says: work out the plan inside `update` on the latest data and store `recorded`, then remove `removals(…)` of the app's notifications in the shade, post the plan's reminders (with sound only when `alert` is true), and set or cancel the single alarm at `nextWakeUp`. Run a reconcile after every change, including a snooze.
- **P5 (deleting):** a deleted treatment's or removed medicine's records go at the next reconcile (P4-D4). If P5 wants them gone in the same write, `deleteTreatment` and `saveTreatment` can drop them once the field is in `PillsData`.
- **P5 (notifications):** what swiping a reminder away does (the plan lists it again at every reconcile while its take is Due), and whether reminders that alert at the same moment play one sound (`RM-4`).

## How to verify

- `./gradlew build` (CLAUDE.md shows how to filter its output and total the test results). `:core`'s tests include the planner's: `ReminderStateTest`, `ReminderPlannerTest` and `ReminderWalkTest`, in `core/build/test-results/test/`.
- Only the planner's tests: `./gradlew :core:test --tests '*reminders*'`.
- No manual checks and no device: see SPEC.md §6.

## Session log

Newest first.

### 2026-11-20 (close)

- The user merged pull request #4. The phase is closed: plan §5.1 shows `Done`, and SPEC §8 is ticked.

### 2026-11-20 (implementation)

- Done: the review of SPEC v0.1. Q1–Q3 were confirmed and went into specification v0.6 (`RM-3`, `RM-5`, `TK-5`). SPEC v0.2 is `Ready`. Then the branch, and T1–T4, each committed after a passing build. Pull request #4 opened, CI green.
- Notes:
  - The planner and the walks passed their first full runs. To check the tests weren't too weak, four deliberate bugs were put into the planner, one at a time: no pull-back after the clock is set back; a repeat a minute early; a snooze's end not a wake-up; a repeat alarm a minute late. Each made tests fail, and each was reverted (checked with `cmp` against a copy).
  - `ktlintFormat` half-wrapped some long lines in the walks: an `if` inside a lambda, and string templates. They were rewritten by hand with small helpers (`exampleWith`, `saving`), as CLAUDE.md says.
- Device state left behind: none. No device was used.

### 2026-11-20 (documents)

- Done: SPEC.md v0.1 and this handover written (`phase-docs`). Plan §5.1: P4 is `Spec draft`.
- Notes: the SPEC's questions Q1–Q3 wait for review.
