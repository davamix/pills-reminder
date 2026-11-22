# P6 — Treatment management UI: Handover

| | |
|---|---|
| **Status** | Not started |
| **Spec** | [SPEC.md](SPEC.md), version v0.1 |
| **Branch** | `phase/p6-treatment-ui` |
| **Last session** | — |

This document tracks the phase as it's built. Update it at the end of every session, so the next one can continue from here.

## Prompt to start or continue

Paste this prompt into a new Claude Code session to start or continue the phase. When the phase is done, replace it with "Phase done: no prompt needed."

```text
Start or continue phase P6 (Treatment management UI) of the pills reminder.

1. Use the phase-start skill for P6. Read only docs/phases/P6-treatment-ui/HANDOVER.md and SPEC.md; they contain everything needed.
2. Carry on from "Next steps" in the handover. Work through the tasks in SPEC.md §7 in order, on branch phase/p6-treatment-ui. Commit after each task, once the build passes.
3. Follow SPEC.md. If something in it is wrong or unclear, stop and ask me before changing course. Record every decision or deviation in the handover.
4. The emulator is needed from T2 on, for the drawings and the UI tests, and the phone for the drawings' check, every instrumented run and the manual checks (T8). Use the phone skill for both. P2, P3 and P5 are merged: the screens use the real store, but every test uses fakes (SPEC P6-D2), never the real data. Show me the drawings of T2 and T3 before building on them. Ask me before creating anything on the phone: its reminders will alert.
5. Before the session ends, or once all tasks are done, use the handover skill. When the done criteria pass, open the pull request and wait for me to merge it.
```

## Current state

Not started. SPEC v0.1 is a draft, waiting for review. The screens are still P1's placeholders.

## Next steps

1. Once SPEC.md is reviewed and `Ready`, start with `T1`: the shared pieces in `ui/common` and their unit tests.

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | To do | Shared pieces |
| T2 | To do | Layouts of Treatments, Treatment and Take, drawn |
| T3 | To do | Layouts of the editors, drawn |
| T4 | To do | Treatments and Treatment screens |
| T5 | To do | Take screen |
| T6 | To do | Editors |
| T7 | To do | Flows and navigation, UI tests |
| T8 | To do | Drawings again, manual checks on the phone and the emulator |
| T9 | To do | Wrap-up, CLAUDE.md, pull request |

## Decisions and deviations

Anything decided during implementation that isn't in SPEC.md, or that changes it. A change to a requirement goes into SPEC.md or SPECIFICATION.md first.

| Date | Decision | Reason |
|---|---|---|

## Known issues

- From earlier phases: AGP's Gradle 10 deprecation notice and ktlint's `sun.misc.Unsafe` warnings on JDK 25 are harmless (P1).

## Pending wiring

Connections to other phases left for later.

- **P7 (home):** P1's Home placeholder stays. Its "All treatments" and "New treatment" open P6's screens; its "Sample take" opens a take that doesn't exist, which the Take screen shows as such. P7 replaces it, opens `TreatmentRoute(id)` from a medicine's row, and can reuse `ScreenDependencies`, `minuteTicks`, `DateTimeText` and the messages in `ui/common` (SPEC P6-D2–P6-D4).

## How to verify

The commands and manual steps that check the current state.

- `./gradlew build` (CLAUDE.md, "Commands").

## Session log

Newest first.
