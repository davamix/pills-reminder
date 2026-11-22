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
   - **On the phone** (`phone` instead of `emulator`) it runs once, at the phone's own settings: light and dark. The phone must be unlocked. It never changes the phone's font scale: ask the user first, and do it by hand (`phone` skill, "HyperOS and Android pitfalls").
   - It removes `probe/` when it ends, also after a failure. If a run is interrupted hard, check that `git status` doesn't list it.
3. **Read the sheets** with Read: `gallery-<screen>-<variant>-<n>.png`, four shots side by side at half size, in the order `gallery-<screen>-names.txt` lists. Magenta is the background around dialogs and popups. Check:
   - nothing cut off or overflowing the frame (the phone's content area, 392.7 × 796.4 dp);
   - no word broken mid-word at font scale 2, especially in narrow fields and side-by-side buttons;
   - colours readable in dark theme;
   - dates and times as the device writes them (12-hour on the emulator, 24-hour on the phone).
4. **Draw on the phone too,** at least once per phase: library texts and pickers follow the phone's language and region (P6 found the date picker in Spanish). Light and dark there are enough.
5. **Show the user** the sheets when the SPEC asks for it: give the full path of the output folder, and name a few sheets to start with.

## Notes

- **Why the system's font scale:** a `LocalDensity` with a larger font scale, set around the content, doesn't reach dialogs, which open in their own window. The system's `font_scale` scales everything, with Android 14's non-linear scaling, as a user would see it.
- **Capturing:** `Capture.DIALOG` draws the topmost dialog (`onAllNodes(isDialog()).onLast()`), `Capture.POPUP` the topmost popup, such as an `ExposedDropdownMenu`. A picker opened from a dialog is the topmost dialog.
- **Sheets, not single shots:** there is no image library on the PC (no PIL), so the harness composes the sheets on the device. Four shots at half size stay readable when Read scales a sheet down.
- **Instrumented test rules apply:** test names without punctuation (CLAUDE.md), and the phone unlocked.
