---
name: deliberate-bugs
description: Show that new tests can fail, as CLAUDE.md requires for tests that pass on their first run. Write the bugs to a file, each a small edit to one source file, and run.sh applies them one at a time, runs the given unit or instrumented tests, restores the file (checked with cmp), and prints which tests caught each bug, or NOT CAUGHT. Use after writing tests, before committing a task.
---

# Deliberate bugs

CLAUDE.md asks every new test that passes on its first run to be shown able to fail: one deliberate bug at a time in the code under test, the tests run, the file put back. P4 and P5 did this by hand, about fifty times. This skill does the loop.

## Steps

1. **Pick the bugs.** One per rule the new tests claim to check, each the smallest change that breaks that rule: a condition dropped, a flag swapped, a value off by one, a call removed. A bug that also breaks compilation proves nothing; prefer edits that still build.
2. **Write the bugs file** in the session's scratchpad, never in the repository. The format, with one or more `old`/`new` pairs per bug:

   ```text
   # Lines before the first bug, and blank or # lines between blocks, are ignored.
   === no-clear
   file: app/src/main/kotlin/io/github/davamix/pillsreminder/MainActivity.kt
   --- old
               AppNavHost(openTake = takeToOpen, onTakeOpened = { takeToOpen = null })
   --- new
               AppNavHost(openTake = takeToOpen, onTakeOpened = {})
   === no-pop-up
   file: app/src/main/kotlin/io/github/davamix/pillsreminder/navigation/AppNavHost.kt
   --- old
               popUpTo<HomeRoute>()
   --- new
   ```

   - The ID follows `=== `; `file:` is relative to the repo root.
   - The lines after `--- old` and `--- new`, up to the next marker, are the text, indentation included. An empty `new` deletes the old text, leaving its line blank.
   - Each `old` text must appear exactly once in the file, or the bug isn't applied, and the run says so.
3. **Run it** from the repo root, in Git Bash, with the scratchpad folder for its logs, and the test command in single quotes:

   ```bash
   bash .claude/skills/deliberate-bugs/run.sh <scratchpad>/bugs.txt <scratchpad>/bugs-out './gradlew :app:testDebugUnitTest --tests "*TestSchedulesTest" --console=plain -q'
   ```

   For instrumented tests, load the `phone` helpers inside the command, and install each build:

   ```bash
   bash .claude/skills/deliberate-bugs/run.sh <scratchpad>/bugs.txt <scratchpad>/bugs-out 'source .claude/skills/phone/helpers.sh emulator > /dev/null && gradle installDebug installDebugAndroidTest > /dev/null && instrument reminders.OpenTakeTest'
   ```

   Run it in the background when it takes minutes: each bug is one build and one test run. Instrumented runs belong on the emulator. On the phone the user must keep it unlocked, and every install resets HyperOS's op 10021, which `instrument` grants again.
4. **Read the result.** One line per bug, then the total:
   - `caught by Class > test; …`: the tests that failed.
   - `NOT CAUGHT`: no test noticed. Add or sharpen a test, or explain why the bug can't be seen (P5 T7: dropping a wait for `RESUMED` changed nothing observable).
   - `failed with no failing test`: most likely the bug doesn't compile. Its log, `<output folder>/<ID>.log`, says.
   - `not applied`: its `old` text isn't in the file exactly once.

   Every line ends with `restored`, or `NOT RESTORED`. That should never happen, but if it does, copy the file back from git and say so. An interrupted run also puts back the file it was changing.
5. **Check the working tree.** Run `git status --short`: only your own changes should be there.
6. **Record the bugs in the handover,** in the task's session log: each bug, and the tests that caught it.

## Notes

- **The runner** is [run.sh](run.sh), with [apply_bug.py](apply_bug.py) for the file format. They live in files because the skill loader replaces shell positional parameters in this text (the `handover` skill found it in P2).
- **JVM tests** are read from the JUnit XML that Gradle writes during the run; **instrumented tests** from the runner's numbered failures (`1) name(Class)`). A Gradle run that stops at compilation writes no XML, hence "failed with no failing test".
- **Ktlint** isn't run on the mutated code, so a bug may leave an odd blank line: the file is restored afterwards.
