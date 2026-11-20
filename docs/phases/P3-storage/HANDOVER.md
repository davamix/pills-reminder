# P3 — Storage: Handover

| | |
|---|---|
| **Status** | In progress: all tasks done; pull request [#3](https://github.com/davamix/pills-reminder/pull/3) is waiting for the user to merge it |
| **Spec** | [SPEC.md](SPEC.md), version v0.3 |
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
4. Every task is done, and pull request #3 is open. It may have been merged since, or have review comments: check with `gh pr view 3`. If it's merged, use the phase-close skill instead. If there are comments, deal with them on the branch, then run the handover skill again. No device is needed unless a comment changes Android code: then rerun the instrumented tests on the emulator and the phone (unlocked, and `allow_test_start` first).
5. Before the session ends, or once all tasks are done, use the handover skill. When the done criteria pass, open the pull request and wait for me to merge it.
```

## Current state

Every task is done. Pull request [#3](https://github.com/davamix/pills-reminder/pull/3) is open, with CI green on `cd22c40` ([run](https://github.com/davamix/pills-reminder/actions/runs/44795885512)).

- **What's built:** the app keeps its treatments and answers in `files/datastore/pills.json`, through `DataStoreTreatmentRepository`, which `AppContainer.repository` now is. The JSON format, version 1, has its own types and a golden file. A file the app can't read is kept as it is, never written over, and reported as `StoredData.Unreadable`. Every write is checked to read back the same.
- **Checks:**
  - `./gradlew build` passes: 60 unit tests in `:app` and 117 in `:core`, 0 failures; lint, ktlint and the permission allowlist pass.
  - The 8 instrumented tests pass on the emulator and on the phone, on the code of `bfd03fa`. Only documents changed after that.
  - The manual checks pass: M1–M7 on the emulator, M1–M6 on the phone (see "How to verify").
  - The merged manifests are the same as `main`'s.
- **Documents:** SPEC.md is v0.3, on `main` and merged into the branch; plan v0.3. `CLAUDE.md` describes the store.

Nothing changes on screen: the app still shows P1's placeholders.

## Next steps

1. Wait for the user to merge pull request #3. Deal with any review comments on the branch first.
2. After the merge, use the `phase-close` skill. Its skills review should take up the op 10021 finding in "Known issues".

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | Done | DataStore 1.3.0 and kotlinx-serialization-json 1.13.0 added to `:app`, and `buildConfig` turned on. `build` passes, and the merged manifests are the same as `main`'s (debug 7 entries, release 5). New runtime dependencies (release): `androidx.datastore:datastore`, `-android`, `-core`, `-core-android`, `-core-okio`, `-core-okio-jvm` (1.3.0); `androidx.tracing:tracing-android`; `com.squareup.okio:okio` and `okio-jvm` 3.17.0 (SPEC Q5); `org.jetbrains.kotlinx:kotlinx-serialization-json` and `-json-jvm` 1.13.0. Q6: a throwaway JVM test wrote, replaced and read back a file through DataStore on Windows 11, also after reopening it; the store's unit tests then passed in CI on Linux too (pull request #3). DataStore's temporary file is `<file>.tmp` (`pills.json.tmp`): it writes it, calls `fd.sync()`, moves it over the file with `Files.move` (`renameTo` below API 26), and deletes a leftover one after a failed write. |
| T2 | Done | `PillsDocument.kt` (format types), `PillsJson.kt` (`encodePillsData`, `decodePillsData`, `Decoded`). `StoreExample` and the golden `pills-v1.json`, generated once from `StoreExample.full`, pretty-printed and checked against SPEC §4.3.1. `PillsJsonTest` (9) and `DamagedDataTest` (36 damaged inputs, plus 3). A treatment's `endedAt` is written before its `medicines`. Times are read only in the exact form written (`2026-11-23T08:00`), so `08:00:00` is refused too. The checks of step 4 have fixed reasons ("map: two answers for the same take"); other failures give the exception's class name. A quoted number would decode as a number, so the "wrong type" case is a name given as a number. |
| T3 | Done | `PillsDataSerializer` (with the write check) and `DataStoreTreatmentRepository` with `create()`. `DataStoreTreatmentRepositoryTest` (12) on real files in a `@TempDir`, each "restart" cancelling the store's scope before opening a new one. DataStore's temporary file is `pills.json.tmp` (T1). See the decisions on `onUnreadable` and on the empty temporary file. |
| T4 | Done | `AppContainer.repository` is the store on `files/datastore/pills.json`, with a scope for the process's life and the debug-only log (tag `PillsStore`). `DataStoreOnDeviceTest` (2, on a file in the cache), `StoreDeviceCheck` (skipped unless `-e storeCheck <step>`), and `DeviceExample` (spec §4.4 for instrumented tests). All 8 instrumented tests pass on the emulator and on the phone (`OK (8 tests)`, the check skipped), and the suite leaves no data file behind. The phone's op 10021 had been reset again, so `allow_test_start` granted it. `CLAUDE.md`: the store, the format rules, real data in instrumented tests, and the status line (specification v0.5, plan v0.3). |
| T5 | In review | M1–M7 pass on the emulator, and M1–M6 on the phone, restarted over adb with the user's go-ahead. Results in "How to verify". Every row of SPEC §6 checked against the tests: each is covered. Pull request [#3](https://github.com/davamix/pills-reminder/pull/3), CI green. |

## Decisions and deviations

Anything decided during implementation that isn't in SPEC.md, or that changes it. A change to a requirement goes into SPEC.md or SPECIFICATION.md first.

| Date | Decision | Reason |
|---|---|---|
| 2026-11-20 | `DataStoreTreatmentRepository`'s constructor also takes `onUnreadable`, beside the `DataStore`. `create()` passes the same function to it and to the serializer. In SPEC v0.3 (Q7). | When the file exists but can't be opened, DataStore throws before the serializer runs. The repository's `data` turns that `IOException` into `Unreadable` (SPEC §4.3.4), and reports it too, as `read: <class>`. |
| 2026-11-20 | After the write check refuses (an `IllegalStateException`, SPEC §4.3.3), an **empty** `pills.json.tmp` stays next to the file until the next write replaces it. The test checks that it's empty and that the next update succeeds. In SPEC v0.3 (Q8). | DataStore deletes its temporary file only in its `IOException` handler (read in its bytecode). The check throws before the first byte, so nothing is lost, and the data file is untouched. Throwing an `IOException` instead would hide a bug as a disk error. |
| 2026-11-20 | Times are read only in the exact form written (`2026-11-23T08:00`): `08:00:00` and a zone are refused, like seconds. In SPEC v0.3 (§4.3.2). | Strict reading (P3-D2): the app never writes another form, so another form means the file wasn't written by it. |
| 2026-11-20 | `StoreDeviceCheck` and `DataStoreOnDeviceTest` use `DeviceExample`, a copy of the §4.4 example in `androidTest`. | Instrumented tests can't see the unit tests' `StoreExample` (P3-D5). |

## Known issues

- **On the phone, an app update and a restart also reset HyperOS's op 10021,** not only an uninstall, as the `phone` skill says. After `gradle installDebug` (an `adb install -r`, M3), and again after the phone's restart (M5), `instrument` stopped with "Run allow_test_start first", and the run worked after it. Proposed for the `phone` skill's review in `phase-close`: say that every install and every restart resets it, or have `instrument` grant it itself on the debug app.
- From earlier phases: AGP's Gradle 10 deprecation notice and ktlint's `sun.misc.Unsafe` warnings on JDK 25 are harmless (P1).

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
- The device checks of SPEC §6, one step at a time: `instrument data.StoreDeviceCheck -e storeCheck <seed|verify|churn|remove>`. A skipped run also says `OK (1 test)`: check `$adb logcat -d -s StoreDeviceCheck:I` for "step … passed". The file's hash: `$adb exec-out run-as $pkg cat files/datastore/pills.json | sha256sum`.

**Manual check results** (SPEC §6), 2026-11-20:

| # | Emulator (Android 16, API 36) | Phone (Android 13, HyperOS OS1.0.6.0.TKHEUXM, stock power settings) |
|---|---|---|
| M1 seed, verify | Pass. 721 bytes, hash `8bf5da69…` | Pass. 721 bytes, the same hash: the encoding is deterministic |
| M2 close (`force_stop`), verify | Pass, hash unchanged | Pass, hash unchanged |
| M3 update (`gradle installDebug`), verify | Pass, hash unchanged (`lastUpdateTime` changed) | Pass, hash unchanged, after `allow_test_start` (see known issues) |
| M4 killed during `churn`, ×3 | Pass. Killed at write 100+, 200+, 312; each `verify` read the last note written | Pass. Killed at writes 109, 217 and 312; each `verify` read the last note written |
| M5 restart, verify | Pass (`adb reboot`), hash unchanged (`4df822d5…`) | Pass (`adb reboot`, with the user's go-ahead; the user unlocked it and reconnected wireless debugging). Hash unchanged (`4df822d5…`, 730 bytes), and `verify` read "churn 312", after `allow_test_start` (see known issues) |
| M6 remove | Pass: the file is `{"schemaVersion":1,"treatments":[],"answers":[]}` | Pass: the same. The debug app's data is empty again, as found |
| M7 uninstall, reinstall, suite | Pass: `OK (8 tests)`, no data file afterwards | Not run on the phone (SPEC §6) |

## Session log

Newest first.

### 2026-11-20 (implementation)

- Done:
  - The review: all six draft assumptions agreed. Plan v0.3 (Q1, Q3, Q5) and SPEC v0.2 `Ready` on `main`. P3 marked `In progress`, and `phase/p3-storage` created.
  - T1–T5 all done, then SPEC v0.3 on `main`, merged into the branch.
  - Pull request [#3](https://github.com/davamix/pills-reminder/pull/3) opened, with CI green on `cd22c40`.
- Notes:
  - DataStore's internals were read from its bytecode in the Gradle cache: the temporary file's name, `fd.sync()`, `Files.move`, and the clean-up only after an `IOException`.
  - The golden file was generated once from `StoreExample.full` with a throwaway test, pretty-printed with Python, and checked by eye against SPEC §4.3.1.
- Device state left behind:
  - The phone was restarted once, with the user's go-ahead. The user unlocked it and reconnected wireless debugging.
  - Its debug app holds an empty data file (`{"schemaVersion":1,"treatments":[],"answers":[]}`), and op 10021 is allowed again (`allow_test_start`). Nothing else was changed.
  - The emulator was started without a window, and stopped at the end. Its debug app was reinstalled fresh (M7) and holds no data file.

### 2026-11-20 (spec)

- Done: SPEC.md v0.1 and this handover written (`phase-docs`).
- Notes: DataStore 1.3.0's Gradle metadata lists Okio as a dependency (SPEC Q5).
