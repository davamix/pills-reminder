# P3 — Storage: Handover

| | |
|---|---|
| **Status** | In progress |
| **Spec** | [SPEC.md](SPEC.md), version v0.2 |
| **Branch** | `phase/p3-storage` |
| **Last session** | 2026-11-20 |

This document tracks the phase as it's built. Update it at the end of every session, so the next one can continue from here.

## Prompt to start or continue

Paste this prompt into a new Claude Code session to start or continue the phase. When the phase is done, replace it with "Phase done: no prompt needed."

```text
Start or continue phase P3 (Storage) of the pills reminder.

1. Use the phase-start skill for P3. Read only docs/phases/P3-storage/HANDOVER.md and SPEC.md; they contain everything needed.
2. Carry on from "Next steps" in the handover. Work through the tasks in SPEC.md §7 in order, on branch phase/p3-storage. Commit after each task, once the build passes.
3. Follow SPEC.md. If something in it is wrong or unclear, stop and ask me before changing course. Record every decision or deviation in the handover.
4. P2 is merged, so no fakes are needed. T1–T3 need no device. T4 needs the emulator and the phone, unlocked, for the instrumented tests. T5's manual checks run on the emulator, then the phone; the phone's restart (M5) waits for my go-ahead, and I unlock it afterwards. If DataStore doesn't work in :app's JVM unit tests (T1, SPEC Q6), stop and ask.
5. Before the session ends, or once all tasks are done, use the handover skill. When the done criteria pass, open the pull request and wait for me to merge it.
```

## Current state

Started. SPEC.md v0.2 is Ready. The app still keeps its data in memory (`InMemoryTreatmentRepository`), so nothing survives the process.

## Next steps

1. Start with `T1`: add DataStore and kotlinx-serialization-json to `:app`, and check that DataStore works in `:app`'s JVM unit tests (SPEC Q6).

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | Done | DataStore 1.3.0 and kotlinx-serialization-json 1.13.0 added to `:app`, and `buildConfig` turned on. `build` passes, and the merged manifests are the same as `main`'s (debug 7 entries, release 5). New runtime dependencies (release): `androidx.datastore:datastore`, `-android`, `-core`, `-core-android`, `-core-okio`, `-core-okio-jvm` (1.3.0); `androidx.tracing:tracing-android`; `com.squareup.okio:okio` and `okio-jvm` 3.17.0 (SPEC Q5); `org.jetbrains.kotlinx:kotlinx-serialization-json` and `-json-jvm` 1.13.0. Q6: a throwaway JVM test wrote, replaced and read back a file through DataStore on Windows 11, also after reopening it; CI's Linux is checked by the pull request. DataStore's temporary file is `<file>.tmp` (`pills.json.tmp`): it writes it, calls `fd.sync()`, moves it over the file with `Files.move` (`renameTo` below API 26), and deletes a leftover one after a failed write. |
| T2 | To do | |
| T3 | To do | |
| T4 | To do | |
| T5 | To do | |

## Decisions and deviations

Anything decided during implementation that isn't in SPEC.md, or that changes it. A change to a requirement goes into SPEC.md or SPECIFICATION.md first.

| Date | Decision | Reason |
|---|---|---|

## Known issues

None yet. From earlier phases: AGP's Gradle 10 deprecation notice and ktlint's `sun.misc.Unsafe` warnings on JDK 25 are harmless (P1).

## Pending wiring

Connections to other phases left for later, e.g. "uses the in-memory repository; switch to the real one once P3 is merged".

- **P5 (reminder state):** version 1 of the format holds no snoozes or alerts played. When P5 wires P4's reminder state into `PillsData`, it adds it to the format, following SPEC §4.3.6, with a golden test (SPEC Q3).
- **P5 (one process):** DataStore works in one process only. No receiver or other component may set `android:process`.
- **P6 and P7 (damaged data, failed saves):** when `repository.data` is `StoredData.Unreadable`, the home screen says so and the editors refuse to save (SPEC Q1). When `update` throws an `IOException`, the screen says the change wasn't saved. `InMemoryTreatmentRepository(StoredData.Unreadable)` tries the first case in tests.
- **P6, P7 and P8 (instrumented tests):** from P3 on, a test that goes through `MainActivity` or `AppContainer` uses the real stored data, on the phone too. Build ViewModels with fakes, and leave the data as it was found (SPEC §5).

## How to verify

The commands and manual steps that check the current state.

- `./gradlew build` (CLAUDE.md, with the output in a file).
- Instrumented tests, from the `phone` skill: `gradle installDebug installDebugAndroidTest && instrument`.
- The device checks of SPEC §6, one step at a time: `instrument data.StoreDeviceCheck -e storeCheck <seed|verify|churn|remove>`.

## Session log

Newest first.

### 2026-11-20

- Done: SPEC.md v0.1 and this handover written (`phase-docs`).
- Notes: DataStore 1.3.0's Gradle metadata lists Okio as a dependency (SPEC Q5).
