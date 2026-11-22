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

1. Draw T2's and T3's states on the phone (unlocked), and show the user both sets of drawings before T4. The throwaway gallery is kept outside the repo, in the session's scratchpad (`ScreensGallery.kt`); a new session writes it again from the `phone` skill.

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | Done | `ui/common`: `ScreenDependencies` (with `rememberAppDependencies`), `minuteTicks`, `DateTimeText` (with `number()` for counts), `ChangeProblem` with `attempt {}` and `UnreadableMessage`, `ConfirmDialog`; `strings_common.xml`. 15 unit tests; 18 deliberate bugs, all caught (session log). |
| T2 | Doing | `TreatmentsContent`, `TreatmentContent`, `TakeContent` with their states and strings; shared `BackButton`, `CommonTexts.kt`, `ShowProblem`, `DateField` and `TimeField`. Every state drawn on the emulator: light and dark at font scale 1, and at the system's font scale 2; all fit. Left: the same drawings on the phone (it was locked), then shown to the user with T3's. |
| T3 | Doing | `TreatmentEditorContent`, `MedicineEditorContent` (with `MedicineFieldActions`), their states in `EditorState.kt`, the discard and remove confirmations; `strings_editor.xml`. Every state drawn on the emulator, light and dark at font scale 1 and at the system's 2: a number field's error under the narrow field broke words at font scale 2, so each amount's error now goes under its whole row. Left: the phone's drawings, then shown to the user with T2's. |
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
| 2026-11-22 | The date and time fields (`DateField`, `TimeField`) are in `ui/common/DateTimeFields.kt`, not `ui/editor/`, with `BackButton.kt` and `CommonTexts.kt` (schedule, state and status texts) beside them. To fold into SPEC §4.1 in T9. | The Take screen's Change time uses the same fields as the medicine editor; the Treatment and Take screens write schedules and states the same way. |
| 2026-11-22 | The date picker writes in the UI's language (`date_locale`) with the phone's region: English names, and on the phone (Spain) weeks from Monday. Material 3 1.4's `DatePickerState(locale, …)`. To fold into SPEC §4.3.11 in T9. | By default it follows the phone's locale, so on the phone it would be in Spanish while the rest of the app is in English (Q5). |
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
