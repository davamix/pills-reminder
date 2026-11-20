# P2 — Domain model and schedule: Handover

| | |
|---|---|
| **Status** | Done. Merged in pull request [#2](https://github.com/davamix/pills-reminder/pull/2) on 2026-11-20. |
| **Spec** | [SPEC.md](SPEC.md), version v0.3 |
| **Branch** | `phase/p2-domain` |
| **Last session** | 2026-11-20 |

This document tracks the phase as it's built. Update it at the end of every session, so the next one can continue from here.

## Prompt to start or continue

Phase done: no prompt needed.

## Current state

Merged into `main` in pull request [#2](https://github.com/davamix/pills-reminder/pull/2) (merge commit `7ee5a20`), with CI green on the branch's last commit `870d6c2` ([run](https://github.com/davamix/pills-reminder/actions/runs/44783886295)). What was built:

- `:core` holds the model, validation, schedule, take states, queries, actions and repository interface of SPEC §4.2, in `io.github.davamix.pillsreminder.core.{model,schedule,actions,time}`. `AppContainer` has `clock` (`CurrentZoneClock`) and `repository` (`InMemoryTreatmentRepository`).
- `./gradlew build` passes locally: 117 unit tests in `:core`, 0 failures; lint, ktlint and the permission allowlist pass.
- The 5 instrumented tests pass on the emulator and on the phone.
- `:core` depends at run time only on `kotlinx-coroutines-core` 1.11.0 and the Kotlin standard library. The merged manifests, debug and release, have the same permissions and components as `main`.
- SPEC.md is v0.3: Q6 and the details of T4 and T5 (see decisions), agreed with the user and on `main`, merged into the branch.

Nothing changes on screen: the app still shows P1's placeholders.

## Next steps

None for P2. These phases can start now, in parallel, since P2 was their only dependency: P3 (storage), P4 (reminder planner), P6 (treatment management UI) and P7 (home screen and reminder check). None has a SPEC.md or HANDOVER.md yet; the `phase-docs` skill writes them.

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
| T7 | Done | Pull request #2, merged. The trimming test sits in `SaveTreatmentTest`, not `ValidationTest` (SPEC v0.3 §6). |

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

### 2026-11-20 (close)

- The user merged pull request #2. P2 is closed: `Done` in plan §5.1, and its done criteria ticked in SPEC.md §8.
- **Skills review** (plan `D-17`), all four proposals agreed by the user:
  - `phone`: `instrument` refuses to start on a locked phone, instead of hanging for 900 s (HyperOS's `KeyguardLocked`).
  - `phone`: new `wait_emulator`, loaded with `helpers.sh none`. It waits for a just-started emulator without `adb -e`, which fails while the phone is on wireless debugging.
  - `handover`: the manifest comparison moved to `compare-manifests.sh`. The cause of the `"all"` in the known issues was the skill loader, which replaces positional parameters in a SKILL.md with the skill's arguments. The `phone` skill's results one-liner had the same exposure, so it moved into `helpers.sh` as `connected_results`.
  - `CLAUDE.md`: unlock the phone before an instrumented run.

### 2026-11-20 (implementation)

- **Done:** T1–T7, in one session. SPEC v0.3 on `main` (Q6, agreed with the user), merged into the branch. Pull request #2 opened.
- **Checks:** build with 117 unit tests, 0 failures; instrumented tests 5 of 5 on the emulator (commit `5f6a85e`, the last code change) and on the phone (same code); merged manifests the same as `main`.
- **Device state left behind:**
  - Phone: the debug app is reinstalled with P2's build, and op 10021 is allowed for it again, as agreed in P1. `force_stop` ended the hung run; the debug app has no alarms yet, so nothing was lost. Nothing else changed.
  - Emulator: started headless for the smoke test, and stopped. The debug app is installed with P2's build. No settings changed.

### 2026-11-20 (documents)

- Done: SPEC.md v0.1 and this handover written (`phase-docs` skill). The review confirmed Q1–Q5 as drafted. Q1–Q4 went into specification v0.5 (`TK-3`, `TK-4`, `TR-11`, `TR-4`). SPEC.md v0.2 is `Ready`, and so is P2 in plan §5.1.
- Notes: P1's handover had no pending wiring. Nothing in P2 depends on device behaviour that hasn't been measured.
