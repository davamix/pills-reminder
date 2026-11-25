---
name: screen-gallery
description: Draw every state of a pills-reminder screen as screenshots, to check its layout before building on it and again before a phase ends. Copy GalleryHarness.kt's pattern into a throwaway gallery file in the scratchpad, one Shot per state (dialogs and dropdowns too), and gallery.sh runs it on the emulator (light and dark, then at the system's font scale 2) or the phone, pulls 4-per-sheet PNGs to read, and keeps the files out of the repo. Use whenever a SPEC asks to draw a screen in every state.
---

# Draw every state of a screen

A phase that adds or changes a screen draws each of its states before relying on the layout (`phase-docs`, step 5), and again from the finished code. Drawing beats driving the app to each state: in Workout Timer it caught a layout that didn't fit and a text colour bug; in P6 it caught an error message that broke words at font scale 2, and, on the phone, a date picker in Spanish.

The gallery is throwaway: never committed. [gallery.sh](gallery.sh) copies it into the source tree for a run and removes it afterwards, so it can't break `./gradlew build` (its ktlint check) or slip into a commit.

## Steps

1. **Write the gallery** in the session's scratchpad, as `<Name>Gallery.kt`, in package `io.github.davamix.pillsreminder.probe`:
   - one class extending `GalleryHarness` ([GalleryHarness.kt](GalleryHarness.kt); gallery.sh copies it next to yours);
   - one `@Test` per screen, calling `draw("<screen>", listOf(Shot(…), …))`;
   - one `Shot` per state, drawing the screen's stateless `…Content` composable from a fixed state, with no ViewModel:

     ```kotlin
     class EditorGallery : GalleryHarness() {
         @Test
         fun editor() =
             draw(
                 "editor",
                 listOf(
                     Shot("new") { TreatmentEditorContent(emptyState, {}, {}, {}, {}, {}, {}, {}) },
                     Shot("discard", Capture.DIALOG, action = { click("Back") }) { TreatmentEditorContent(changed, …) },
                     Shot("unit menu", Capture.POPUP, action = { click("hours") }) { MedicineEditorContent(…) },
                 ),
             )
     }
     ```

   - **States to draw:** every state the screen's state class has (loading, unreadable, missing, empty, full), each dialog and dropdown open, long texts, a scrolled list's end (`action = { compose.onNodeWithTag(LIST).performScrollToNode(hasText(…)) }`), and a snackbar message.
   - **Data:** the spec's §4.4 example only (the repo is public, and the sheets could be shared).
   - **Times:** don't hard-code "16:05" when a test clicks a time: the emulator writes "4:05 PM". Look for either.
2. **Run it,** from the repo root, in Git Bash:

   ```bash
   bash .claude/skills/screen-gallery/gallery.sh emulator <scratchpad>/EditorGallery.kt <scratchpad>/gallery-emu
   ```

   - **On the emulator** it runs twice: at font scale 1, as `light-1` and `dark-1` sheets; then with the system's font scale at 2.0, as `large` sheets. It puts the font scale back afterwards.
   - **An extra font scale,** as a fourth argument, adds a run in between on the emulator, as `light-<scale>` and `dark-<scale>` sheets: `… <scratchpad>/gallery-emu 1.4`. Use 1.4, the phone's "big" font, whenever a layout changes at some font scale (a button that moves under its text, a row that wraps): in P7, rows that stacked their button from 1.5 looked right at 1 and 2, and at 1.4 broke "1 capsule" between its number and its unit.
   - **On the phone** (`phone` instead of `emulator`) it runs once, at the phone's own settings: light and dark, named after its font scale (`light-1`, or `light-1.4` at its "big" font). The phone must be unlocked. It never changes the phone's font scale: ask the user to set it by hand (`phone` skill, "HyperOS and Android pitfalls"), then run it.
   - It deletes the sheets an earlier run left in the app's cache before drawing, so the output folder holds only this run's (in P7 it held P6's too). It removes `probe/` when it ends, also after a failure. If a run is interrupted hard, check that `git status` doesn't list it.
3. **Read the sheets** with Read: `gallery-<screen>-<variant>-<n>.png`, four shots side by side at half size, in the order `gallery-<screen>-names.txt` lists. Magenta is the background around dialogs and popups. Check:
   - nothing cut off or overflowing the frame (the phone's content area, 392.7 × 796.4 dp);
   - no word broken mid-word at font scale 2, especially in narrow fields and side-by-side buttons, nor a value split from its unit at an in-between scale;
   - colours readable in dark theme;
   - dates and times as the device writes them (12-hour on the emulator, 24-hour on the phone).
4. **Draw on the phone too,** at least once per phase: library texts and pickers follow the phone's language and region (P6 found the date picker in Spanish). Light and dark there are enough.
5. **Show the user** the sheets when the SPEC asks for it: give the full path of the output folder, and name a few sheets to start with.

## Screenshots to publish

For the README or a release (P8 T9), the pictures are taken in the app itself, not drawn by the gallery, and they go into the public repo:

1. **The emulator only, never the phone,** with only the specification's §4.4 example as data (`phone` skill, "Common tasks"). Created in the release app through its screens, with first takes this morning so a take is Due.
2. **As the specification shows it:** the 24-hour clock (`$adb shell settings put system time_12_24 24`); read the setting first, and put it back afterwards (it was unset: `settings delete system time_12_24`). Dark theme for one picture at most, with `cmd uimode night yes`, put back with a `trap`.
3. **Take them** with `shot`, while the app is in front. A reminder in the shade: `$adb shell cmd statusbar expand-notifications`, tap its expand arrow so its actions show, `shot`, then `cmd statusbar collapse`. A heads-up covers the app bar for a few seconds after an alert: wait, or use `KEYCODE_BACK`.
4. **Crop and scale** with [Crop.java](Crop.java), from the JDK, which needs no image library: `"$JAVA_HOME/bin/java" Crop.java <in.png> <out.png> <top> <bottom> <width>`, with Windows paths (`cygpath -w`). On the emulator the status bar and the gesture bar are 66 px each (24 dp): screens at `66 66 360`. A reminder: its card's rows only, found in the screenshot, such as `970 980 540`.
5. **Look at each one** before committing it: no status bar, nothing but example data. They live in `docs/images/`, about 15–50 KB each.

## Notes

- **Why the system's font scale:** a `LocalDensity` with a larger font scale, set around the content, doesn't reach dialogs, which open in their own window. The system's `font_scale` scales everything, with Android 14's non-linear scaling, as a user would see it.
- **Capturing:** `Capture.DIALOG` draws the topmost dialog (`onAllNodes(isDialog()).onLast()`), `Capture.POPUP` the topmost popup, such as an `ExposedDropdownMenu`. A picker opened from a dialog is the topmost dialog.
- **Sheets, not single shots:** there is no image library on the PC (no PIL), so the harness composes the sheets on the device. Four shots at half size stay readable when Read scales a sheet down.
- **Instrumented test rules apply:** test names without punctuation (CLAUDE.md), and the phone unlocked.
