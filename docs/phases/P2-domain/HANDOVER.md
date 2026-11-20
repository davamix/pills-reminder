# P2 — Domain model and schedule: Handover

| | |
|---|---|
| **Status** | In progress: all tasks done; pull request [#2](https://github.com/davamix/pills-reminder/pull/2) is waiting for the user to merge it |
| **Spec** | [SPEC.md](SPEC.md), version v0.3 |
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
4. All tasks are done, and pull request #2 is open. It may have been merged by now, or have review comments: check with `gh pr view 2 --comments`. Once it is merged, use the phase-close skill. No device is needed.
5. Before the session ends, or once all tasks are done, use the handover skill. When the done criteria pass, open the pull request and wait for me to merge it.
```

## Current state

All seven tasks are done, and pull request [#2](https://github.com/davamix/pills-reminder/pull/2) is open. CI passed on commit `03bb5cc` ([run](https://github.com/davamix/pills-reminder/actions/runs/44783502006)).

- `:core` holds the model, validation, schedule, take states, queries, actions and repository interface of SPEC §4.2, in `io.github.davamix.pillsreminder.core.{model,schedule,actions,time}`. `AppContainer` has `clock` (`CurrentZoneClock`) and `repository` (`InMemoryTreatmentRepository`).
- `./gradlew build` passes locally: 117 unit tests in `:core`, 0 failures; lint, ktlint and the permission allowlist pass.
- The 5 instrumented tests pass on the emulator and on the phone.
- `:core` depends at run time only on `kotlinx-coroutines-core` 1.11.0 and the Kotlin standard library. The merged manifests, debug and release, have the same permissions and components as `main`.
- SPEC.md is v0.3: Q6 and the details of T4 and T5 (see decisions), agreed with the user and on `main`, merged into the branch.

Nothing changes on screen: the app still shows P1's placeholders.

## Next steps

1. Deal with any review comments on pull request #2, on this branch.
2. Once the user has merged it, use the `phase-close` skill on `main`. For its skills review, see "Known issues": two findings for the `phone` and `handover` skills.

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | Done | `api(kotlinx-coroutines-core)` works in the Kotlin JVM module without `java-library`. |
| T2 | Done | `dueAt` behaves as SPEC §4.3.2 assumed at both Madrid changes. |
| T3 | Done | |
| T4 | Done | The §4.4 walk checks, at every minute, that each state change happens exactly when `nextStateChange` predicted it. |
| T5 | Done | |
| T6 | Done | |
| T7 | In review | Pull request #2. The trimming test sits in `SaveTreatmentTest`, not `ValidationTest` (SPEC v0.3 §6). |

## Decisions and deviations

| Date | Decision | Reason |
|---|---|---|
| 2026-11-20 | `PillsData.status(treatmentId, now)` returns `TreatmentStatus?`, `null` for an unknown ID. Agreed with the user: SPEC v0.3, Q6. | A screen can still hold the ID of a treatment that was just deleted. `null`, like `firstTake()`, is safer than an exception. |
| 2026-11-20 | When a medicine has takes up to the cut-off, but its *current* schedule holds none of them (a second edit, before the first edit's new schedule had started), `saveTreatment` still stores that schedule as an `EarlierSchedule` with no takes. In SPEC v0.3 §4.3.6. | The empty window ends at the cut-off, so the new schedule can never add takes in the past. Extending the previous earlier schedule's `until` instead would wrongly give it takes it never had. |
| 2026-11-20 | `endTreatment` also ends a treatment that is already Finished without being ended (all its takes past). It refuses only one already ended. In SPEC v0.3 §4.3.7. | Harmless, and the Treatment screen needn't hide "End early" for a finished treatment that wasn't ended. |
| 2026-11-20 | `MedicineTakes` also exposes `contains(plannedAt)` (`plannedAt in takes`) and its filtered `answers`. In SPEC v0.3 §4.2. `nextAfter`, `latestAtOrBefore` and `missedAt` are `internal`. | The actions (T5) and the invariant checks need them. |
| 2026-11-20 | The instrumented tests also ran on the phone, though SPEC §8 only asks for the emulator. | The phone was connected over wireless debugging, and the `handover` skill runs them on every connected device when Android code changed. |

## Known issues

- **On the phone, an instrumented run hangs if the phone is locked when it starts.** HyperOS refuses the test's activity with `MIUILOG- Permission Denied Activity KeyguardLocked`, and `instrument` waits out its 900 s deadline. Unlocked, the run passed. Proposed for the `phone` skill's review in `phase-close`: `instrument` checks `isKeyguardShowing` first, as it checks op 10021.
- **The `handover` skill's manifest comparison has literal `"all"`** where its `components` and `manifest` functions should use their arguments (`"$1"`, and `"$1/…/$2"`). This session ran it with the arguments. To fix in `phase-close`'s skills review.
- **The phone's debug app had been reinstalled fresh since P1,** which reset HyperOS's op 10021. `allow_test_start` granted it again.
- P1's harmless notices remain: AGP's Gradle 10 deprecation notice, and ktlint's `sun.misc.Unsafe` warnings on JDK 25.

## Pending wiring

- **P3:** `AppContainer.repository` is `InMemoryTreatmentRepository`. P3 replaces it with the DataStore repository. P3's JSON format covers all of `PillsData`: treatments, medicines with their earlier schedules, `endedAt`, and the answers.
- **P4 and P5:** `PillsData` holds no reminder state. P4 defines the snoozes and the alerts played, and how they are stored; plan §2.4 keeps them in the same document as the treatments.
- **P5:** check on the emulator that `CurrentZoneClock` follows a change of time zone while the app is running (*Settings > System > Date & time*; the shell can't change the zone).

## How to verify

```bash
./gradlew ktlintFormat                 # then, as its own command:
./gradlew build                        # compile, :core's unit tests, lint, ktlint, permission allowlist
source .claude/skills/phone/helpers.sh emulator && gradle installDebug installDebugAndroidTest && instrument
```

On the phone, unlock it first, and run `allow_test_start` after a fresh install. Total the unit tests with the one-liner in CLAUDE.md: `core/build/test-results/test/` holds P2's 117.

## Session log

Newest first.

### 2026-11-20 (implementation)

- **Done:** T1–T7, in one session. SPEC v0.3 on `main` (Q6, agreed with the user), merged into the branch. Pull request #2 opened.
- **Checks:** build with 117 unit tests, 0 failures; instrumented tests 5 of 5 on the emulator (commit `5f6a85e`, the last code change) and on the phone (same code); merged manifests the same as `main`.
- **Device state left behind:**
  - Phone: the debug app is reinstalled with P2's build, and op 10021 is allowed for it again, as agreed in P1. `force_stop` ended the hung run; the debug app has no alarms yet, so nothing was lost. Nothing else changed.
  - Emulator: started headless for the smoke test, and stopped. The debug app is installed with P2's build. No settings changed.

### 2026-11-20 (documents)

- Done: SPEC.md v0.1 and this handover written (`phase-docs` skill). The review confirmed Q1–Q5 as drafted. Q1–Q4 went into specification v0.5 (`TK-3`, `TK-4`, `TR-11`, `TR-4`). SPEC.md v0.2 is `Ready`, and so is P2 in plan §5.1.
- Notes: P1's handover had no pending wiring. Nothing in P2 depends on device behaviour that hasn't been measured.
