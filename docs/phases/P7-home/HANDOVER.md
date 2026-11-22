# P7 — Home screen and reminder check: Handover

| | |
|---|---|
| **Status** | Not started |
| **Spec** | [SPEC.md](SPEC.md), version v0.2 |
| **Branch** | `phase/p7-home` |
| **Last session** | 2026-11-22 |

This document tracks the phase as it's built. Update it at the end of every session, so the next one can continue from here.

## Prompt to start or continue

Paste this prompt into a new Claude Code session to start or continue the phase. When the phase is done, replace it with "Phase done: no prompt needed."

```text
Start or continue phase P7 (Home screen and reminder check) of the pills reminder.

1. Use the phase-start skill for P7. Read only docs/phases/P7-home/HANDOVER.md and SPEC.md; they contain everything needed.
2. Carry on from "Next steps" in the handover. Work through the tasks in SPEC.md §7 in order, on branch phase/p7-home. Commit after each task, once the build passes.
3. Follow SPEC.md. If something in it is wrong or unclear, stop and ask me before changing course. Record every decision or deviation in the handover.
4. T1 measures on the phone and the emulator before anything is built: the phone must be connected and unlocked, and I change its settings myself, or agree first. Ask before creating test treatments on the phone (their reminders alert) and put back every setting changed. The emulator is needed for T1, the drawings and E1–E2. Every screen must fit the phone's content area, 392.7 × 796.4 dp.
5. Before the session ends, or once all tasks are done, use the handover skill. When the done criteria pass, open the pull request and wait for me to merge it.
```

## Current state

Not started. SPEC.md v0.2 is `Ready`: the review confirmed every draft assumption (Q1–Q6). Home and Settings are still P1's placeholders.

## Next steps

1. Once the SPEC is `Ready`: create the branch (`phase-start`), then T1, the measurements of SPEC §4.6, with the phone connected and unlocked.

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | To do | Measure first: C1–C6 |
| T2 | To do | Layouts and drawings of Home and Settings |
| T3 | To do | The check: device, logic, store |
| T4 | To do | Home's ViewModel and destination |
| T5 | To do | Settings' ViewModel and destination |
| T6 | To do | UI flow tests, shared test helpers |
| T7 | To do | Drawings again, manual checks |
| T8 | To do | Wrap-up and pull request |

## Decisions and deviations

Anything decided during implementation that isn't in SPEC.md, or that changes it. A change to a requirement goes into SPEC.md or SPECIFICATION.md first.

| Date | Decision | Reason |
|---|---|---|

## Known issues

- From earlier phases: AGP's Gradle 10 deprecation notice and ktlint's `sun.misc.Unsafe` warnings on JDK 25 are harmless (P1).

## Pending wiring

Connections to other phases left for later.

- **P8 (end to end):** the end-to-end test (create a treatment, get its reminder, answer it, see Home change) and the acceptance record, with the settings the reminder check asks for.
- **P8 (release build):** the first launch's check on a fresh install of the release build, and `NF-1` and `NF-2` again with the check passing.

## How to verify

The commands and manual steps that check the current state.

- Not yet: nothing is built.

## Session log

Newest first.

### 2026-11-22

- Done: SPEC.md v0.1 and this handover, from plan v0.7 and specification v0.9, with what P3, P5 and P6 left for P7.
- Review: every draft assumption accepted (Q1–Q6). Specification v0.10 took Q2–Q6 into `RM-9` and §6; plan v0.8 took Q1 into `D-4` and §2.4. SPEC v0.2, `Ready`.
