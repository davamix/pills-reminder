# P8 — Integration and v1.0: Handover

| | |
|---|---|
| **Status** | In progress |
| **Spec** | [SPEC.md](SPEC.md), version v0.2 |
| **Branch** | `phase/p8-integration` |
| **Last session** | 2026-11-23 |

This document tracks the phase as it's built. Update it at the end of every session, so the next one can continue from here.

## Prompt to start or continue

Paste this prompt into a new Claude Code session to start or continue the phase. When the phase is done, replace it with "Phase done: no prompt needed."

```text
Start or continue phase P8 (Integration and v1.0) of the pills reminder.

1. Use the phase-start skill for P8. Read only docs/phases/P8-integration/HANDOVER.md and SPEC.md; they contain everything needed.
2. Carry on from "Next steps" in the handover. Work through the tasks in SPEC.md §7 in order, on branch phase/p8-integration. Commit after each task, once the build passes.
3. Follow SPEC.md. If something in it is wrong or unclear, stop and ask me before changing course. Record every decision or deviation in the handover.
4. P8 is mostly checks on devices. T1 and T2 need the emulator, and T2 the phone too; T4–T8 need the phone, and me with it for the reminder runs, the restart, the ring modes and the day and night of real use. Ask before changing anything on the phone, and never change its clock or time zone. The keystore and its passwords never go in the repo or in your output (SPEC §4.3.3, §5). ACCEPTANCE.md is public: times and conditions only, no treatment content from real use. Tag and release only after I've merged the pull request and said go (T10).
5. Before the session ends, or once T1–T9 are done, use the handover skill. When the done criteria before the pull request pass, open the pull request and wait for me to merge it.
```

## Current state

In progress. SPEC.md v0.2 is `Ready`: the user agreed with every draft assumption (Q1–Q7). Every phase P1–P7 is merged, and so is the lost-sound fix (pull request #8). The app's wiring is believed complete (SPEC §4.3.1); T1 confirms it.

## Next steps

1. T1. Confirm each item of SPEC §4.3.1 in the code and note it here, then fix `EndAndDeleteTest` (§4.3.6) and run it at font scale 1.0 and 1.4 on the emulator.

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | To do | Wiring check, `EndAndDeleteTest` fix |
| T2 | To do | `EndToEndTest` |
| T3 | To do | Release build, keystore, signing |
| T4 | To do | Measure: reading alert times on the release build |
| T5 | To do | A9 (emulator), A1, A12 (phone); `ACCEPTANCE.md` started |
| T6 | To do | A2, A3, A4, A10 (phone) |
| T7 | To do | A5–A8 (phone) |
| T8 | To do | A11: a day and a night of real use |
| T9 | To do | `ACCEPTANCE.md`, README, CLAUDE.md; pull request |
| T10 | To do | After the merge, with the go-ahead: tag `v1.0.0` and the GitHub release |

## Decisions and deviations

Anything decided during implementation that isn't in SPEC.md, or that changes it. A change to a requirement goes into SPEC.md or SPECIFICATION.md first.

| Date | Decision | Reason |
|---|---|---|

## Known issues

- From earlier phases: AGP's Gradle 10 deprecation notice and ktlint's `sun.misc.Unsafe` warnings on JDK 25 are harmless (P1).
- P6's `EndAndDeleteTest` fails at font scale 1.4 (P7's known issue); T1 fixes it.
- The emulator's debug app keeps two notification categories from P5's probes ("Probe reminders" and `reminders-mutant`) and a `files/probe.log`. The release build, installed fresh, has none of them.
- Android 16 may time a notification out after 72 hours (P5); not checked, out of scope (SPEC §3).

## Pending wiring

None left for later phases: P8 is the last. What earlier phases left for P8 is in SPEC §4.3.1.

## How to verify

- `./gradlew build`, then the unit test totals (CLAUDE.md, "Commands").
- `source .claude/skills/phone/helpers.sh emulator && gradle installDebug installDebugAndroidTest && instrument`, then the same on the phone.
- From T3: `gradle assembleRelease`, then `apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk`.
- From T5: `ACCEPTANCE.md`, row by row.

## Session log

Newest first.

### 2026-11-23

- Done: SPEC.md v0.1 and this handover written (`phase-docs`), from plan v0.9 and specification v0.11, with what P1–P7's handovers left for P8.
- Notes: the phase hasn't started; its SPEC is waiting for review.
