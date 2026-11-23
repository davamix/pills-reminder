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

In progress. SPEC.md v0.2 is `Ready`: the user agreed with every draft assumption (Q1–Q7). Every phase P1–P7 is merged, and so is the lost-sound fix (pull request #8). T1 is done: every wiring item of SPEC §4.3.1 is in place, and `EndAndDeleteTest` passes at a large font. Next is the end-to-end test (T2).

## Next steps

1. T2: write `EndToEndTest` (SPEC §4.3.2), run it on the emulator and the phone, then its deliberate bugs on the emulator.

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | Done | Every item of SPEC §4.3.1 in place (see "Wiring status" below); no gap to wire. `EndAndDeleteTest` scrolls back to "Active" before asserting it: passes at font scale 1.0 and 1.4 on the emulator. `build` passes (`:app` 257, `:core` 177 unit tests); the instrumented suite passes on the emulator (48 tests). |
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
- The emulator's debug app keeps two notification categories from P5's probes ("Probe reminders" and `reminders-mutant`) and a `files/probe.log`. The release build, installed fresh, has none of them.
- Android 16 may time a notification out after 72 hours (P5); not checked, out of scope (SPEC §3).

## Pending wiring

None left for later phases: P8 is the last. What earlier phases left for P8 is in SPEC §4.3.1.

### Wiring status (T1, 2026-11-23)

Each item of SPEC §4.3.1, read in the code on `main` at `80adaa0`:

| Item | Found |
|---|---|
| The real repository everywhere | `ScreenDependencies.of(container)` passes `container.repository` (`DataStoreTreatmentRepository`) and `container.checkStore` to every screen; `rememberAppDependencies()` is `AppNavHost`'s default. In `app/src/main`, `InMemoryTreatmentRepository` appears only in comments, and `InMemoryCheckStore` only where it is defined (`CheckStore.kt`, for tests). |
| A tapped reminder opens the Take screen | `ReminderIntents.openTake` → `MainActivity.takeOf` → `AppNavHost(openTake)` → `OpenTake`, which navigates to the real `TakeRoute` destination, above the editors when they are open. |
| Answers in the app remove their notification | `Reminders.start()` collects `repository.data` and reconciles after every value (P5-D2). |
| Home shows answers from a notification | `HomeViewModel`'s rows are `combine(deps.repository.data, deps.minuteTicks, marks)`. |
| Minutes in debug builds only | `allowMinutes = BuildConfig.DEBUG` in `ScreenDependencies.of`. (`core.actions.saveTreatment` validates with `allowMinutes = true`, as a safety net: the editor's own validation is the one that refuses minutes.) |
| No placeholder screen | Every route of `Routes.kt` has its real screen in `AppNavHost.kt` and `EditorGraph.kt`. |
| The lost-sound fix | `Reconciler` counts its own posts as in the shade until the shade lists them, or for `POSTED_GRACE`. |
| `CurrentZoneClock` in a running app | Left for A9, on the release build. |

## How to verify

- `./gradlew build`, then the unit test totals (CLAUDE.md, "Commands").
- `source .claude/skills/phone/helpers.sh emulator && gradle installDebug installDebugAndroidTest && instrument`, then the same on the phone.
- From T3: `gradle assembleRelease`, then `apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk`.
- From T5: `ACCEPTANCE.md`, row by row.

## Session log

Newest first.

### 2026-11-23 (2)

- Done: SPEC v0.2 agreed (every draft assumption), the phase marked `In progress`, the branch created. T1: the wiring confirmed, `EndAndDeleteTest` fixed.
- Notes: the phone was on wireless debugging only, and the emulator running.

### 2026-11-23

- Done: SPEC.md v0.1 and this handover written (`phase-docs`), from plan v0.9 and specification v0.11, with what P1–P7's handovers left for P8.
- Notes: the phase hasn't started; its SPEC is waiting for review.
