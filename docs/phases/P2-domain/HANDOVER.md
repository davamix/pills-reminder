# P2 — Domain model and schedule: Handover

| | |
|---|---|
| **Status** | In progress |
| **Spec** | [SPEC.md](SPEC.md), version v0.2 |
| **Branch** | `phase/p2-domain` |
| **Last session** | 2026-11-20 |

This document tracks the phase as it's built. Update it at the end of every session, so the next one can continue from here.

## Prompt to start or continue

Paste this prompt into a new Claude Code session to start or continue the phase. When the phase is done, replace it with "Phase done: no prompt needed."

```text
Start or continue phase P2 (Domain model and schedule) of the pills reminder.

1. Use the phase-start skill for P2. Read only docs/phases/P2-domain/HANDOVER.md and SPEC.md; they contain everything needed.
2. Carry on from "Next steps" in the handover. Work through the tasks in SPEC.md §7 in order, on branch phase/p2-domain. Commit after each task, once the build passes.
3. Follow SPEC.md. If something in it is wrong or unclear, stop and ask me before changing course. Record every decision or deviation in the handover.
4. P2 is pure Kotlin in :core, apart from two AppContainer fields. No device is needed until T7, which runs the existing smoke test on the emulator; the phone isn't needed at all. Test names can't contain ":", so write times as "0800" or "16h00" (SPEC §4.4).
5. Before the session ends, or once all tasks are done, use the handover skill. When the done criteria pass, open the pull request and wait for me to merge it.
```

## Current state

Nothing is built yet. SPEC.md v0.2 is `Ready`: the review confirmed every draft assumption, and the specification is now v0.5.

## Next steps

1. Start T1: `:core`'s dependencies (`kotlinx-coroutines-core` as `api`, `kotlinx-coroutines-test`), the model types and the drafts with their validation, and `ValidationTest`.

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | To do | |
| T2 | To do | |
| T3 | To do | |
| T4 | To do | |
| T5 | To do | |
| T6 | To do | |
| T7 | To do | |

## Decisions and deviations

Anything decided during implementation that isn't in SPEC.md, or that changes it. A change to a requirement goes into SPEC.md or SPECIFICATION.md first.

| Date | Decision | Reason |
|---|---|---|
| 2026-11-20 | `PillsData.status(treatmentId, now)` returns `TreatmentStatus?`, `null` for an unknown ID, where SPEC §4.2 has a non-null `TreatmentStatus`. | A screen can still hold the ID of a treatment that was just deleted. `null`, like `firstTake()`, is safer than an exception. |
| 2026-11-20 | `MedicineTakes` also exposes `contains(plannedAt)` (`plannedAt in takes`) and its filtered `answers`, beyond SPEC §4.2. `nextAfter`, `latestAtOrBefore` and `missedAt` are `internal`. | The actions (T5) and the invariant checks need them. |

## Known issues

None yet.

## Pending wiring

- **P3:** `AppContainer.repository` is `InMemoryTreatmentRepository`. P3 replaces it with the DataStore repository. P3's JSON format covers all of `PillsData`: treatments, medicines with their earlier schedules, `endedAt`, and the answers.
- **P4 and P5:** `PillsData` holds no reminder state. P4 defines the snoozes and the alerts played, and how they are stored; plan §2.4 keeps them in the same document as the treatments.
- **P5:** check on the emulator that `CurrentZoneClock` follows a change of time zone while the app is running (*Settings > System > Date & time*; the shell can't change the zone).

## How to verify

```bash
./gradlew ktlintFormat                 # then, as its own command:
./gradlew build                        # compile, :core and :app unit tests, lint, ktlint, permission allowlist
source .claude/skills/phone/helpers.sh emulator && gradle installDebug installDebugAndroidTest && instrument
```

Then total the unit tests with the one-liner in CLAUDE.md: `core/build/test-results/test/` holds P2's.

## Session log

Newest first.

### 2026-11-20

- Done: SPEC.md v0.1 and this handover written (`phase-docs` skill). The review confirmed Q1–Q5 as drafted. Q1–Q4 went into specification v0.5 (`TK-3`, `TK-4`, `TR-11`, `TR-4`). SPEC.md v0.2 is `Ready`, and so is P2 in plan §5.1.
- Notes: P1's handover had no pending wiring. Nothing in P2 depends on device behaviour that hasn't been measured.
