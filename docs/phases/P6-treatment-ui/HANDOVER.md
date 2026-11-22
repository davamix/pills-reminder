# P6 — Treatment management UI: Handover

| | |
|---|---|
| **Status** | In progress: all tasks done; pull request [#6](https://github.com/davamix/pills-reminder/pull/6) is waiting for the user to merge it |
| **Spec** | [SPEC.md](SPEC.md), version v0.3 |
| **Branch** | `phase/p6-treatment-ui` |
| **Last session** | 2026-11-22 |

This document tracks the phase as it's built. Update it at the end of every session, so the next one can continue from here.

## Prompt to start or continue

Paste this prompt into a new Claude Code session to start or continue the phase. When the phase is done, replace it with "Phase done: no prompt needed."

```text
Start or continue phase P6 (Treatment management UI) of the pills reminder.

1. Use the phase-start skill for P6. Read only docs/phases/P6-treatment-ui/HANDOVER.md and SPEC.md; they contain everything needed.
2. Carry on from "Next steps" in the handover. Work through the tasks in SPEC.md §7 in order, on branch phase/p6-treatment-ui. Commit after each task, once the build passes.
3. Follow SPEC.md. If something in it is wrong or unclear, stop and ask me before changing course. Record every decision or deviation in the handover.
4. All tasks are done and pull request #6 is open. If it has been merged, use the phase-close skill instead. If it has review comments, deal with them on the branch. The full instrumented run on the phone, on the final code, still waits for the phone to be unlocked (gradle installDebug installDebugAndroidTest && instrument, phone skill); every test passes on the emulator. Every test uses fakes (SPEC P6-D2), never the real data.
5. Before the session ends, or once all tasks are done, use the handover skill. When the done criteria pass, open the pull request and wait for me to merge it.
```

## Current state

All tasks done; pull request [#6](https://github.com/davamix/pills-reminder/pull/6) is open; CI passed on `74f0aaa` (run 45036844021). The Treatments, Treatment, Take and editor screens replace P1's placeholders, on the real store, with SPEC v0.3. `./gradlew build` passes: unit tests `:app` 196, `:core` 177, none failing; 93 deliberate bugs against P6's new tests, all caught. Every instrumented test passes on the emulator (37); on the phone, the 36 of T7 and the new no-takes test passed, and the full run on the final code waits for the phone to be unlocked. Every state was drawn on both devices; M1–M7 pass on the phone and E1 on the emulator. The merged manifests and release dependencies are the same as `main`'s.

## Next steps

1. Run every instrumented test on the phone, unlocked, on the branch's final code, and note the result here and in the pull request.
2. Deal with any review comments on pull request #6.
3. After the user merges it, the `phase-close` skill: mark P6 `Done` on `main`, tick SPEC §8, and review the skills (see "Known issues" for the items gathered for it).

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | Done | `ui/common`: `ScreenDependencies` (with `rememberAppDependencies`), `minuteTicks`, `DateTimeText` (with `number()` for counts), `ChangeProblem` with `attempt {}` and `UnreadableMessage`, `ConfirmDialog`; `strings_common.xml`. 15 unit tests; 18 deliberate bugs, all caught (session log). |
| T2 | Done | `TreatmentsContent`, `TreatmentContent`, `TakeContent` with their states and strings; shared `BackButton`, `CommonTexts.kt`, `ShowProblem`, `DateField` and `TimeField`. Every state drawn on the emulator (light and dark at font scale 1, and at the system's font scale 2) and on the phone (light and dark); all fit. Shown to the user, who agreed. |
| T3 | Done | `TreatmentEditorContent`, `MedicineEditorContent` (with `MedicineFieldActions`), their states in `EditorState.kt`, the discard and remove confirmations; `strings_editor.xml`. Drawn like T2's. A number field's error under the narrow field broke words at font scale 2, so each amount's error goes under its whole row. Shown to the user, who agreed. |
| T4 | Done | `TreatmentsViewModel`, `TreatmentViewModel` (End early, Delete, `deleted`), their entry composables taking `ScreenDependencies`, and their destinations in `AppNavHost` (its new `dependencies` parameter). Shared: `LeaveWhen` (navigation driven by a state), `dropUnlessResumedWith` (a resumed-only click that carries a value), `vmTest` and `TestTicks` in the tests. 18 unit tests; 20 deliberate bugs, all caught. `NavigationSmokeTest`'s placeholder walk replaced by one that only reads the data, until T7. |
| T5 | Done | `TakeViewModel` (the actions offered by running P2's pure actions, P6-D5; `keyOf` reads the route), `TakeScreen`, and its destination. 18 unit tests; 15 deliberate bugs, all caught. `OpenTakeTest` passes with the real Take screen (its take doesn't exist, so it shows "This take no longer exists." under the title "Take"). |
| T6 | Done | `EditorViewModel` (the draft, the medicine editor's working copy keyed by its back-stack entry, validation, preview, notes, save), `SavedEditor.kt` (the drafts as JSON in the `SavedStateHandle`, P6-D6), both editors' screens, and `EditorGraph.kt`: the nested graph on `TreatmentEditorRoute`, starting at the new `TreatmentFormRoute`. 21 unit tests; 28 deliberate bugs, all caught (one after a test fix). Lint caught `LocalDate.EPOCH`, which needs API 34. |
| T7 | Done | Q3 in `OpenTake` (`AppNavHost`): while the editors are on the back stack, a tapped reminder's take opens on top, replacing a take already there; otherwise right above Home, as P5 did. UI tests with fakes in `androidTest/…/ui/`: `CreateTreatmentTest`, `EditorValidationTest`, `EditStartedTreatmentTest`, `EndAndDeleteTest`, `DiscardChangesTest`, `TakeScreenTest`, `ScreensNavigationTest` (with Q3's two tests), and their helpers `UiTesting.kt` (`FakeApp`, `showApp`, `dateTimeText()`, `pickDay`). `NavigationSmokeTest` keeps only what needs `MainActivity`. 11 deliberate bugs, all caught. Every instrumented test passes on the emulator and the phone (36). |
| T8 | Done | Every state drawn again on both devices. M1–M7 pass on the phone, and E1 on the emulator (results below). M1 found a medicine with no takes shown as "Last take: No end", fixed with a test. |
| T9 | In review | SPEC v0.3 on `main` (decisions folded in, Q8 and Q9), merged into the branch; CLAUDE.md; build and checks; pull request [#6](https://github.com/davamix/pills-reminder/pull/6). |

## Decisions and deviations

Anything decided during implementation that isn't in SPEC.md, or that changes it. A change to a requirement goes into SPEC.md or SPECIFICATION.md first.

| Date | Decision | Reason |
|---|---|---|
| 2026-11-22 | The date and time fields (`DateField`, `TimeField`) are in `ui/common/DateTimeFields.kt`, not `ui/editor/`, with `BackButton.kt` and `CommonTexts.kt` (schedule, state and status texts) beside them. To fold into SPEC §4.1 in T9. | The Take screen's Change time uses the same fields as the medicine editor; the Treatment and Take screens write schedules and states the same way. |
| 2026-11-22 | The date picker writes in the UI's language (`date_locale`) with the phone's region: English names, and on the phone (Spain) weeks from Monday. Material 3 1.4's `DatePickerState(locale, …)`. To fold into SPEC §4.3.11 in T9. | By default it follows the phone's locale, so on the phone it would be in Spanish while the rest of the app is in English (Q5). |
| 2026-11-22 | `androidResources.localeFilters += "en"` in `app/build.gradle.kts` (a shared file: P6's entry). A translation adds its language there. To fold into SPEC §4.1 and §4.3.11 in T9. | On the phone, Material's own texts followed the phone's language: the date picker's title read "Seleccionar fecha" in an English app, and its input mode's labels would too. With only English resources kept, library texts fall back to English (NF-8, Q5). |
| 2026-11-22 | The date picker has the app's own title (the field's label) and headline ("Mon 23 Nov", from `DateTimeText`). To fold into SPEC §4.3.11 in T9. | Its default headline formats with the phone's locale whatever the resources hold: "23 nov 2026" on the phone. |
| 2026-11-22 | The Treatments screen's floating button uses `ExtendedFloatingActionButton`'s content overload, not `text` and `icon`. | Material 3 1.4 wraps that overload's label in `clearAndSetSemantics`, so the button had no name for TalkBack, and tests couldn't find it by its text (seen in `NavigationSmokeTest` with a semantics probe). |
| 2026-11-22 | A deleted treatment's screen shows nothing while it leaves (its state is `null` once `deleted`), not "This treatment no longer exists". Deleting a treatment already gone also leaves. | The Missing state would flash on screen before the screen goes back. |
| 2026-11-22 | ViewModel tests set the main dispatcher before `runTest` and reset it after (`vmTest`), not inside. | `stateIn`'s stop timer is still pending in `viewModelScope` when the test body ends; reset inside `runTest`, Main was gone when it ran, and the tests failed with `CompletionHandlerException`. A lesson for CLAUDE.md's coroutine tests, at the skills review. |
| 2026-11-22 | The medicine editor's working copy belongs to the editor's back-stack entry (`openMedicine(id, entry)`): the same entry keeps it, as after a restart of the process; a new entry starts a fresh copy. To fold into SPEC §4.2 in T9. | A Back with no changes pops the editor without telling the ViewModel, and a working copy left over must not reappear the next time the editor opens; one restored with the back stack must. Navigation restores each entry's ID. |
| 2026-11-22 | The editors' graph is built by `navigation/EditorGraph.kt`, a file of its own, rather than in `AppNavHost.kt`. To fold into SPEC §4.1 in T9. | Two destinations sharing one ViewModel from the graph's entry; `AppNavHost` only calls `editorGraph(…)`. |
| 2026-11-22 | Instrumented test names have no apostrophes either ("reminder's", "can't" failed the dex step). | CLAUDE.md lists commas and parentheses; any ASCII punctuation but `-`, `_` and `$` fails. |
| 2026-11-22 | Fixed the `deliberate-bugs` skill's `run.sh`: the test command now runs with its input from `/dev/null`. Committed on the phase branch with T7, for the skills review to confirm. | With an instrumented command, `adb` read the rest of the bug list from the runner's input, so only the first bug ran (T7: "caught 1 of 1" for 11 bugs). |
| 2026-11-22 | A medicine with no takes at all (its treatment ended before its first take) says "No takes: the treatment ended before the first one." in place of its progress and last take. To fold into SPEC §4.3.4 in T9. | M1: it showed "0 of 0 taken or skipped" and "Last take: No end", but it has no end only in the sense of having nothing. |
| 2026-11-22 | **SPEC v0.3** folds in every row above marked "to fold into SPEC", with Q8 (the pickers and library texts in the UI's language) and Q9 (a medicine with no takes). No change to the specification or the plan. | T9 (`phase-docs`, "SPEC change during implementation"). |
| 2026-11-22 | Labels before a day take a colon: "Starts: Tomorrow 08:00", "Ended: …", "Next: …", "Last answered: …", "Taken: Yesterday 23:50". UI text, so no SPEC change. | "Today", "Tomorrow" and "Yesterday" are capitalised, which reads wrongly mid-sentence ("Starts Tomorrow"). |

## Known issues

- **For the skills review** (`phase-close`), from T2's drawings: a `LocalDensity` with a larger font scale, set around the content, doesn't reach dialogs, which open in their own window; dialogs drawn that way keep font scale 1. Setting the emulator's own `settings put system font_scale 2.0` scales everything, dialogs and pickers included, with Android 14's non-linear scaling (put it back to `1.0` afterwards). `captureToImage()` of `onAllNodes(isDialog()).onLast()` draws the topmost dialog. The `phone` skill's "Checking a screen in every state" could say both.
- **For the skills review**, from T7 and T8: the `deliberate-bugs` skill's `run.sh` gave its test command the bug list as stdin, and `adb` read it, so an instrumented run did only the first bug; fixed on this branch (`< /dev/null`), for the review to confirm and to say in the skill. On the emulator, neither `input swipe` nor `input motionevent` triggers the back gesture, from either edge: a check of the back swipe or its predictive animation needs the user at the emulator's window (started without `-no-window`). The `phone` skill could say so. The time picker's dial numbers are content descriptions (`content-desc="15 hours"`), and the date picker's days have their whole date as text (`Monday, November 23, 2026`), for `node_centre` and `tap_text`.
- From earlier phases: AGP's Gradle 10 deprecation notice and ktlint's `sun.misc.Unsafe` warnings on JDK 25 are harmless (P1).

## Pending wiring

Connections to other phases left for later.

- **P7 (home):** P1's Home placeholder stays. Its "All treatments" and "New treatment" open P6's screens; its "Sample take" opens a take that doesn't exist, which the Take screen shows as such. P7 replaces it, opens `TreatmentRoute(id)` from a medicine's row, and can reuse `ScreenDependencies`, `minuteTicks`, `DateTimeText` and the messages in `ui/common` (SPEC P6-D2–P6-D4).

## Manual checks (T8)

On the phone (POCO F3, Android 13, HyperOS `OS1.0.6.0.TKHEUXM`, Spanish, 24-hour clock), debug build, 2026-11-22, driven over adb with the user for the sound and the taps on reminders. Test treatments only, deleted at the end; no alarm or notification left; dark theme and font scale put back.

| # | Result |
|---|---|
| M1 | Pass, after a fix. "M1 test" created with two medicines, first takes moved to tomorrow 16:00 with the date picker: previews 21 takes (last Mon 30 Nov 08:00) and 15 (last Sat 28 Nov 08:00); the Treatment screen "Upcoming · Starts: Tomorrow 16:00"; the alarm clock set for Mon 23 16:00 at once (P5-D2). Dose edited, saved, back on the Treatment screen. End early, after its confirmation: Finished, the alarm gone. **Found:** each medicine showed "0 of 0 taken or skipped" and "Last take: No end", since it had no takes at all: now "No takes: the treatment ended before the first one.", with a UI test that fails without the fix. Delete, after its confirmation: back on an empty list; no alarm, no notification. |
| M2 | Pass. "M2 test": every 30 minutes (the minutes unit, debug build) for 1 day from today 15:00: 48 takes, the past-first-take note. Saved at 15:48: the 15:30 take's reminder alerted at once (heard by the user). The user's tap opened its Take screen (Due, "Missed if not answered by Today 16:00"). Taken at 15:49 removed the reminder; Undo brought it back, alerting at 15:50:06; Taken again, then Change time to 15:45 with the time picker's minute dial: "Taken at 15:45". |
| M3 | Pass. With the treatment editor open and "M3 test" typed, the user tapped the reminder: the Take screen opened above the editor; Back returned to it with "M3 test". Without an editor, Back from the Take screen went Home. |
| M4 | Pass. Back key and back arrow, treatment and medicine editors: no dialog without changes; "Discard your changes?" with them; Keep editing stays, Discard leaves. |
| M5 | Pass. The "For" field, the lowest number field, stays visible above the number keyboard (the screen shrinks for it). The time picker is a 24-hour dial and fits. |
| M6 | Pass. A new treatment with "M3 test" and a medicine editor with "Survivor"; Home, then `end_process` (`am kill`); relaunched: the medicine editor came back with "Survivor" and asked before discarding; the treatment editor had "M3 test". |
| M7 | Pass. Dark theme (`cmd uimode night yes`) and font scale 1.5: the Treatment screen, the Take screen and the medicine editor (date and time stacked) fit and read well. Put back to night mode off and font scale 1.0. |
| E1 | Pass. On the emulator (API 36, gesture navigation): the Back key asks with changes and leaves without them. The back swipe couldn't be injected over adb (`input swipe` and `input motionevent` from either edge did nothing), so the user checked it at the emulator's window: with no change it animates (predictive back) and leaves; with a change it asks first. |

## How to verify

The commands and manual steps that check the current state.

- `./gradlew build` (CLAUDE.md, "Commands"): unit tests `:app` 196, `:core` 177.
- Every instrumented test: `gradle installDebug installDebugAndroidTest && instrument` (`phone` skill), on the emulator and the unlocked phone; 37 tests. The UI flow tests alone: `instrument ui.CreateTreatmentTest,ui.EditorValidationTest,ui.EditStartedTreatmentTest,ui.EndAndDeleteTest,ui.DiscardChangesTest,ui.TakeScreenTest,ui.ScreensNavigationTest`.
- The merged manifests and dependencies: `bash .claude/skills/handover/compare-manifests.sh <scratchpad>`: same as `main`.
- By hand on the phone, the debug build: Home, All treatments, New treatment; create a treatment, open it, edit, end early, delete (M1); a treatment in minutes with a take due at once, its reminder tapped, answered on the Take screen (M2). The manual checks table above has the rest.

## Session log

Newest first.

### 2026-11-22 (later)

- Done: T4–T9. The ViewModels and destinations of every screen (T4–T6), the UI flow tests with fakes and Q3 (T7), the drawings again and the manual checks (T8), SPEC v0.3, CLAUDE.md and pull request #6 (T9).
- Found and fixed: `LocalDate.EPOCH` needs API 34 (lint, T6); `ExtendedFloatingActionButton`'s `text` overload hides its label (T4); a medicine with no takes shown as "Last take: No end" (M1, T8); the `deliberate-bugs` runner's stdin (T7).
- Pull request [#6](https://github.com/davamix/pills-reminder/pull/6); CI passed on `74f0aaa` (run 45036844021).
- Checks: build passes, `:app` 196 and `:core` 177 unit tests; 37 instrumented tests on the emulator on the final code; on the phone, 36 at `dff0de3` and the new test at `e819390`, the full final run waiting for an unlock; manifests and release dependencies the same as `main`.
- Device state left behind: the phone has no treatments, alarms or notifications from the checks; its dark theme and font scale are back to off and 1.0. The emulator's font scale is back to 1.0; it now runs with its window (started for E1).

### 2026-11-22

- Done: SPEC v0.1 written; reviewed, every draft assumption accepted (Q1–Q7); specification v0.9 took Q1 and Q4–Q7; SPEC v0.2 `Ready`. Branch created. T1.
- T1's deliberate bugs (`deliberate-bugs` skill), 18 of 18 caught:
  - `DateTimeText`: 24-hour times unpadded (`H:mm`), and padded 12-hour times (`hh:mm a`), caught by the 24-hour and 12-hour tests; the hour format swapped, by both; no "Yesterday", by the day names test; "Tomorrow" two days off, and the days counted backwards, by the day names, year's end and planned time tests; no year for later years, and for earlier years, by the other-year test; the date pattern in English whatever the locale, by the locale test; the year pattern ignored, by the other-year test; the time before the day, by the planned time test; no grouping, by the counts test.
  - `minuteTicks`: no tick at once, and a fixed minute's wait, by both tests.
  - `attempt`: a refused change taken as applied; unreadable taken as refused; a failed write taken as unreadable; catching every exception: each by its own test.
- Notes: unit totals after T1: `:app` 139, `:core` 177, none failing. Material 3 is 1.4.0 (Compose BOM 2026.09.00), which has `TimePickerDialog`.
- T2 and T3: every state drawn on the emulator and the phone, and agreed by the user. On the phone, the date picker's title showed in Spanish: fixed with `localeFilters` and the picker's own title and headline.
- T4's deliberate bugs, 20 of 20 caught: `TreatmentsViewModel`: Active sorted case-sensitively, Upcoming latest first, Finished oldest first, by the groups test; no start time, by the groups and rows tests; a last take on an ended row, by the rows test; no ticks, by the tick test; unreadable shown as empty, by the unreadable test. `TreatmentViewModel`: Edit offered once ended, by the End early test; End offered when Finished, by the Finished test; a refused End shown, by the already-ended test; Delete of a gone treatment staying, by its test; Delete leaving on a failure, by the failed-write and unreadable tests; the problem not shown, by the failed-write test; Missing shown while leaving, by the Delete test; the count one short, the next take as the last answered, and no last answered, by the example test and others; no start time, by the Upcoming test; a last take while Active, by the example test; no ticks, by the tick test.
- Unit totals after T4: `:app` 157, `:core` 177, none failing. `NavigationSmokeTest` and `OpenTakeTest` pass on the emulator (13 tests).
- T5's deliberate bugs, 15 of 15 caught: Taken offered only when Due, by the early, last-answered and tick tests; Skip only when Due, by the last-answered test; Undo only for Taken, by the Skipped and older-take tests; Change time and "last answered" for any Taken take, by the older-take test; Skip marking Taken, Undo skipping and Change time a minute off, each by its own test; the problem dropped, by the future-time, failed-write and refused tests; the problem never cleared, by the refused test; the route read as a date only, by the route test; a missing take shown as the next one, by the missing test; unreadable shown as missing, by the unreadable test; no ticks, by the tick test; the medicine's name as the treatment's, by the Due test.
- T6's deliberate bugs, 28 of 28 caught: in `EditorViewModel`, Done hiding the errors, always appending, keeping the editor open; Close keeping the copy; Save ignoring errors, never saying saved, dropping the problem, staying stuck on "saving"; letters kept in amounts, no digit limit; the first take at now, a default interval of 6; the entry ignored (first NOT CAUGHT: the restore test read the state before it caught up; fixed with `advanceUntilIdle()`), any entry keeping the copy; no changes ever, for the treatment and for the medicine; "started" ignoring answers, or the first take; the past note for a started medicine; no preview without a name; never unreadable; nothing saved; the date ignored, the time an hour off; an edit starting empty; always new. In `SavedEditor`: bad saved data crashing; the working copy not saved. Each caught by the test named after its rule.
- Unit totals after T6: `:app` 196, `:core` 177, none failing. `NavigationSmokeTest` and `OpenTakeTest` pass on the emulator with the real editors.
- T7's deliberate bugs, 11 of 11 caught, on the emulator: the take always above Home, and takes stacking over the editor, by Q3's test; a take link opening the next take, by `TakeScreenTest` and the navigation walk; a new treatment's save going back instead of to its screen, and Done staying open, by `CreateTreatmentTest` and others; the treatment editor's Back and the medicine editor's arrow never asking, by `DiscardChangesTest` (and `EditStartedTreatmentTest`); an edit opening a new medicine, by `EditStartedTreatmentTest` and the walk; the picked date converted in Honolulu rather than UTC, by `CreateTreatmentTest` and `TakeScreenTest`; Delete's confirmation doing nothing, by `EndAndDeleteTest`; Change time keeping the old time, by `TakeScreenTest`.
- Instrumented: 36 tests pass on the emulator and the phone. Material's date picker gives each day cell its whole date as text ("Monday, November 23, 2026"); the tests pick a day by its number within a clickable node.
