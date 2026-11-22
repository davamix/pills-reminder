# P6 — Treatment management UI: Handover

| | |
|---|---|
| **Status** | In progress |
| **Spec** | [SPEC.md](SPEC.md), version v0.2 |
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
4. The emulator is needed from T2 on, for the drawings and the UI tests, and the phone for the drawings' check, every instrumented run and the manual checks (T8). Use the phone skill for both. P2, P3 and P5 are merged: the screens use the real store, but every test uses fakes (SPEC P6-D2), never the real data. Show me the drawings of T2 and T3 before building on them. Ask me before creating anything on the phone: its reminders will alert.
5. Before the session ends, or once all tasks are done, use the handover skill. When the done criteria pass, open the pull request and wait for me to merge it.
```

## Current state

Started. SPEC v0.2 is `Ready`, with Q1–Q7 answered as drafted. The screens are still P1's placeholders.

## Next steps

1. `T6`: the editors' ViewModel and graph. Drafts of `EditorViewModel`, `SavedEditor.kt`, both editors' screens and `EditorViewModelTest` are in the session's scratchpad (`t6/`), if the session is the same.

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | Done | `ui/common`: `ScreenDependencies` (with `rememberAppDependencies`), `minuteTicks`, `DateTimeText` (with `number()` for counts), `ChangeProblem` with `attempt {}` and `UnreadableMessage`, `ConfirmDialog`; `strings_common.xml`. 15 unit tests; 18 deliberate bugs, all caught (session log). |
| T2 | Done | `TreatmentsContent`, `TreatmentContent`, `TakeContent` with their states and strings; shared `BackButton`, `CommonTexts.kt`, `ShowProblem`, `DateField` and `TimeField`. Every state drawn on the emulator (light and dark at font scale 1, and at the system's font scale 2) and on the phone (light and dark); all fit. Shown to the user, who agreed. |
| T3 | Done | `TreatmentEditorContent`, `MedicineEditorContent` (with `MedicineFieldActions`), their states in `EditorState.kt`, the discard and remove confirmations; `strings_editor.xml`. Drawn like T2's. A number field's error under the narrow field broke words at font scale 2, so each amount's error goes under its whole row. Shown to the user, who agreed. |
| T4 | Done | `TreatmentsViewModel`, `TreatmentViewModel` (End early, Delete, `deleted`), their entry composables taking `ScreenDependencies`, and their destinations in `AppNavHost` (its new `dependencies` parameter). Shared: `LeaveWhen` (navigation driven by a state), `dropUnlessResumedWith` (a resumed-only click that carries a value), `vmTest` and `TestTicks` in the tests. 18 unit tests; 20 deliberate bugs, all caught. `NavigationSmokeTest`'s placeholder walk replaced by one that only reads the data, until T7. |
| T5 | Done | `TakeViewModel` (the actions offered by running P2's pure actions, P6-D5; `keyOf` reads the route), `TakeScreen`, and its destination. 18 unit tests; 15 deliberate bugs, all caught. `OpenTakeTest` passes with the real Take screen (its take doesn't exist, so it shows "This take no longer exists." under the title "Take"). |
| T6 | To do | Editors |
| T7 | To do | Flows and navigation, UI tests |
| T8 | To do | Drawings again, manual checks on the phone and the emulator |
| T9 | To do | Wrap-up, CLAUDE.md, pull request |

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
| 2026-11-22 | Labels before a day take a colon: "Starts: Tomorrow 08:00", "Ended: …", "Next: …", "Last answered: …", "Taken: Yesterday 23:50". UI text, so no SPEC change. | "Today", "Tomorrow" and "Yesterday" are capitalised, which reads wrongly mid-sentence ("Starts Tomorrow"). |

## Known issues

- **For the skills review** (`phase-close`), from T2's drawings: a `LocalDensity` with a larger font scale, set around the content, doesn't reach dialogs, which open in their own window; dialogs drawn that way keep font scale 1. Setting the emulator's own `settings put system font_scale 2.0` scales everything, dialogs and pickers included, with Android 14's non-linear scaling (put it back to `1.0` afterwards). `captureToImage()` of `onAllNodes(isDialog()).onLast()` draws the topmost dialog. The `phone` skill's "Checking a screen in every state" could say both.
- From earlier phases: AGP's Gradle 10 deprecation notice and ktlint's `sun.misc.Unsafe` warnings on JDK 25 are harmless (P1).

## Pending wiring

Connections to other phases left for later.

- **P7 (home):** P1's Home placeholder stays. Its "All treatments" and "New treatment" open P6's screens; its "Sample take" opens a take that doesn't exist, which the Take screen shows as such. P7 replaces it, opens `TreatmentRoute(id)` from a medicine's row, and can reuse `ScreenDependencies`, `minuteTicks`, `DateTimeText` and the messages in `ui/common` (SPEC P6-D2–P6-D4).

## How to verify

The commands and manual steps that check the current state.

- `./gradlew build` (CLAUDE.md, "Commands").

## Session log

Newest first.

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
