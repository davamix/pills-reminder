# Pills Reminder: guide for Claude Code

An Android app that manages medical treatments and reminds the user when each medicine's next take is due. It runs on one phone, a POCO F3 (Android 13, API 33, Xiaomi HyperOS, portrait, 1080 × 2400). The emulator `PillsReminder_API_36` has the same screen.

**Status:** the specification (v0.5) and the implementation plan (v0.3) are agreed. The phases' status is in plan §5.1. Built with Kotlin and Jetpack Compose.

## Documents

- `docs/SPECIFICATION.md`: what the app does. Requirement IDs look like `TR-3`, `RM-5` or `NF-1`; open questions are `Qn`.
- `docs/IMPLEMENTATION_PLAN.md`: how it is built. Decisions `D-n`, phases `P1`, `P2`, …, skills `SK-n`, questions `PQ-n`, and the phases' status in §5.1.
- `docs/phases/PN-<name>/SPEC.md` and `HANDOVER.md`: one specification and one progress tracker per phase, from the templates in `docs/phases/_templates/`.

When working on a phase, read only its HANDOVER.md and SPEC.md (skill `phase-start`). They quote everything needed. Each HANDOVER.md starts with a prompt to paste into a new session to start or continue that phase. Keep it current.

**Precedence:** specification, then plan, then phase SPEC. Fix the higher document first, then let the change flow down.

### The implementation plan

It follows Workout Timer's plan, because the skills rely on its sections. Keep these when changing it:

- §1 Tech stack, with the technical decisions `D-n` in §1.1.
- §2 Architecture. §3 Security and privacy, including the permissions the app may declare. §4 Testing strategy.
- §5 Phases: §5.1 a table with the columns Phase, Name, **Folder**, Depends on and Status, plus the rules for running phases in parallel; §5.2 a summary per phase, which its SPEC.md expands; §5.3 requirement coverage, each requirement ID to a phase.
- §6 Working method: phase documents, phase lifecycle, git workflow, session routine.
- §7 Claude Code skills. §8 Open questions `PQ-n`, with an "Answered" table. §9 Change log.

Phase statuses: Planned → Spec draft → Ready → In progress → Done.

## Skills

- `doc-review`: apply a review round to the specification or the plan: answered questions, accepted and discarded proposals, a new version with a change-log entry, then commit and push.
- `phase-docs`: write a phase's SPEC.md and HANDOVER.md, apply their review, and change a SPEC.md during implementation.
- `phase-start`: begin or resume a phase (branch, dependencies, status).
- `handover`: end a session. Runs the checks, updates HANDOVER.md, commits and pushes, and opens the pull request when the phase is done.
- `phase-close`: after the user merges a phase's pull request, mark the phase `Done` on `main`, then review the skills: propose new skills, or changes to skills and to this file, from what the phase taught (plan `D-17`). The user decides which to make.
- `phone`: the phone and the emulator over adb, with helpers: pick the device, start the emulator, install, launch, read the screen, screenshots, logs, instrumented tests that keep the app's data, the app's alarms and notifications, Doze, and the HyperOS pitfalls found on this phone.

## Commands

| Task | Command |
|---|---|
| Compile, unit tests, Android Lint, ktlint, permission allowlist | `./gradlew build` |
| Fix Kotlin formatting | `./gradlew ktlintFormat` |
| Unit tests only | `./gradlew test` |
| Install on one device | `gradle installDebug`, from the `phone` skill's helpers, which sets `ANDROID_SERIAL`. A bare `./gradlew installDebug` installs on every connected device. |
| Instrumented tests | `gradle installDebug installDebugAndroidTest && instrument` (`phone` skill). It keeps the app's data. On the phone, unlock it first; `instrument` grants the HyperOS permission the tests need (`allow_test_start`), which every install, update and restart resets. Avoid `connectedDebugAndroidTest`: it uninstalls the app afterwards, which deletes its data. |

- Gradle runs on Android Studio's JDK; `JAVA_HOME` already points to it.
- Run `ktlintFormat` as its own command, before `build`. In a single call (`./gradlew ktlintFormat build`), the ktlint check can still report violations that the format step has just fixed, and the build fails.
- `ktlintFormat` can't fix every line that's too long. It can leave one half-wrapped, and `build` then fails on ktlint. Wrap such lines by hand, for example by moving part of an expression into a local `val`. The reports in `app/build/reports/ktlint/` (or `core/…`) name the file and line.
- On JDK 25, ktlint prints harmless `sun.misc.Unsafe` warnings. To hide them and keep only real output, write the output to a file and filter that (Git Bash):

  ```bash
  mkdir -p build; ./gradlew build --console=plain -q > build/gradle.log 2>&1; echo "exit=$?"; grep -v -i 'unsafe\|^WARNING' build/gradle.log | tail -30
  ```

- **Keep Gradle's exit code.** Don't pipe Gradle into `grep` or `tail` and chain the next step with `&&`: the pipe returns the last command's code, so the next step runs after a failed build. In Workout Timer, `am instrument` then ran an old test APK. `set -o pipefail` doesn't help, because `grep -v` returns 1 when it has nothing left to print. Use the file, as above, or the `phone` skill's `gradle` helper, which does the same.
- `-q` hides the test counts. To total the unit test results per module and test task, after a build:

  ```bash
  for d in */build/test-results/*/; do grep -h -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$d"*.xml 2>/dev/null | awk -F'"' -v d="$d" '{t+=$2;s+=$4;f+=$6;e+=$8} END {if (NR) print d": tests="t" skipped="s" failures="f" errors="e}'; done
  ```

- When unit tests fail, this prints each failing test's class, name and message (first 300 characters), from the JUnit XML:

  ```bash
  grep -h -o '<testcase name="[^"]*" classname="[^"]*"\|<failure message="[^"]\{0,300\}' */build/test-results/*/*.xml | grep -B1 '<failure' | grep -v '^--$' | sed -e 's/^<testcase name="\([^"]*\)" classname="\([^"]*\)"/\2 > \1/' -e 's/^<failure message="/    /' -e 's/&quot;/"/g; s/&lt;/</g; s/&gt;/>/g; s/&amp;/\&/g'
  ```

## Code structure

- `:core` is pure Kotlin: the model, the schedule, the take states and the reminder planner. Never add Android dependencies to it (plan `D-2`). Its packages, under `io.github.davamix.pillsreminder.core`:
  - `model`: the treatment types, the editor's drafts and their validation (`TR-8`), `PillsData`, `TreatmentRepository` and `InMemoryTreatmentRepository`.
  - `schedule`: `PlannedTakes` (one schedule), `MedicineTakes` (a medicine's takes and states, `nextTake`, `canMarkEarly`, `lastAnswered`, `nextStateChange`), and `PillsData.takesOf(id)`, `take(key, now)` and `status(id, now)`.
  - `actions`: every change to the data, as pure functions on `PillsData` and as suspend helpers on the repository (`repository.markTaken(key, clock)`, …).
  - `time`: `dueAt(zone)` and `CurrentZoneClock`. P4 adds `reminders`.
- **Treatment data** (P2-D4):
  - Change it only through the `core.actions` helpers. They run the rules inside `update`, on the latest data, so an answer given from a notification while an editor is open is never lost.
  - Takes and their states are never stored (plan `D-5`): ask `MedicineTakes` for them.
  - Compare a planned time with now through `dueAt(now.zone)`, never as local times, which go wrong in the hour the clocks go back.
  - `AppContainer` has the `clock` and the `repository`.
- `:app` holds the Android code: `data/`, `reminders/`, `ui/<feature>/`, `navigation/`, and `di/AppContainer.kt`.
- **The store** (`data`, P3): `AppContainer.repository` is a `DataStoreTreatmentRepository`, which keeps all of `PillsData` in one JSON document, `files/datastore/pills.json` on the device. Its format types (`PillsDocument.kt`) are kept apart from the model.
  - **A field added to `PillsData`**, or to a type in it, goes into the format in the same change, with a test. The store refuses to write data that doesn't read back the same (P3-D3), so a field left out fails at the first write that sets it.
  - **Golden files never change** (`app/src/test/resources/data/pills-v<N>.json`). A change that old files can't be decoded as raises `schemaVersion` and adds a migration (P3 SPEC §4.3.6).
  - **To make a golden file,** write the data's encoding to a file from a throwaway unit test, pretty-print it with `python -I -c` and `json.dumps(…, indent=2, ensure_ascii=False)`, and check it by eye against the format in the SPEC. Then delete the test (P3 T2). The golden test compares JSON trees, so the indentation doesn't matter.
  - `StoredData.Unreadable` means the file is kept as it is and every `update` is refused. Show it; never work around it. `update` can also throw an `IOException` when a write fails.
  - DataStore allows one store per file in the process, so the app stays in one process: no `android:process`.
- The base package is `io.github.davamix.pillsreminder`. The debug build's application ID adds `.debug` and its name is "Pills Reminder (debug)", so it installs next to a release build.
- **Edge to edge** (P1-D4): `MainActivity` calls `enableEdgeToEdge()` on every Android version, and screens keep their content out of the system bars through `Scaffold`. Every screen must fit the phone's content area, **392.7 × 796.4 dp**, which is smaller than the emulator's (`phone` skill, "Devices").
- **Navigation:** type-safe routes in `navigation/Routes.kt`, one per screen. The phase that replaces a placeholder owns that screen's route.
  - **A click that navigates** goes through `dropUnlessResumed`, so a double tap can't open a screen twice, or go back twice.
  - **Navigation driven by a ViewModel state**, such as leaving the editor after a save, works differently: report it once from a `LaunchedEffect` on that state, after waiting with `lifecycle.currentStateFlow.first { it.isAtLeast(RESUMED) }`, and pass a plain lambda. A screen Android rebuilds after ending the app is composed before its entry is resumed, and `dropUnlessResumed` would silently drop the call (a Workout Timer lesson).
- **Screens** (Workout Timer's pattern, unless a phase SPEC says otherwise): an entry composable that takes its ViewModel, defaulting to `viewModel(factory = …)`, and a stateless `…Content` composable that draws the state. The factory, in the ViewModel's companion, gets the container with `(this[APPLICATION_KEY] as PillsReminderApp).container`. Tests build the ViewModel directly, with fakes.

## Conventions

- **Text:** all user-facing text lives in resources, one `res/values/strings_<feature>.xml` per feature (plan `D-14`). English only for now.
- **Permissions:** only those in plan §3. The build's allowlist check fails on any other, and on `INTERNET` (P1-D6). A new one needs a plan change first.
- **Logs** never contain treatment content (names, doses, instructions, notes), only IDs and times (plan §3).
- **Time:** `java.time`, with an injected `Clock`. Logic never reads the system clock itself (plan `D-6`).
- **Versions:** pinned in `gradle/libs.versions.toml`. AGP must stay at 9.3 or lower while Android Studio 2026.1 is the installed IDE. The Compose BOM stays at 2026.09.00: 2026.11.01 (Compose 1.13) needs `compileSdk` 37.1, and only the `android-37.0` platform is installed.
- **Kotlin style:** ktlint, official style (`.editorconfig`). Run `ktlintFormat` before committing.
- **Formatting numbers:** don't call `String.format` without a `Locale`. Lint warns (`DefaultLocale`), and the result would follow the phone's language. Pad with `padStart` instead.
- **Tests:**
  - **Unit tests:** in both modules, JUnit Jupiter, with sentence names in backticks. With AGP 9, `./gradlew build` runs only `:app`'s debug unit tests (`testDebugUnitTest`).
  - **Characters in test names:** names in backticks can't contain `.` `;` `:` `/` `\` `[` `]` `<` `>` on the JVM; the compiler rejects them. Use commas, or "and", instead. Write times as `0800` or `16h00`, not `08:00`.
  - **Instrumented tests** (`app/src/androidTest`): JUnit 4, also with sentence names in backticks (P1-D7). D8 takes spaces because `minSdk` is 33, but **no other ASCII punctuation except `-`, `_` and `$`**: no commas, no parentheses. A comma failed the dex step in P1 T6. To run one of them, run its class: `am instrument -e class Class#method` splits at the spaces.
  - **Instrumented tests and the real data:** `AppContainer.repository` is the app's real file. A test that goes through `MainActivity` or the container reads and writes the data the app really has, on the phone too. Build ViewModels with fakes, and leave the data as it was found. Store tests use their own file in the cache; `StoreDeviceCheck` touches the real data only when run with `-e storeCheck <step>`.
  - **Coroutine tests:** in `runTest`, `advanceUntilIdle()` ignores work that only `backgroundScope` has scheduled. Wait in the foreground instead, for example with `flow.first { … }`. `advanceTimeBy()` does run background work.
  - **Compose UI tests:** use the rules from `androidx.compose.ui.test.junit4.v2` (`createComposeRule`, `createAndroidComposeRule`); the v1 rules are deprecated. `pressBack()` comes from Espresso. After an action that starts a coroutine, wait for its result (`waitUntil { … }` or `waitForIdle()`) before asserting. Also, from Workout Timer:
    - A `LazyColumn` item exists only once it's scrolled into composition. Tag the list, and call `performScrollToNode(matcher)` on it before using an item.
    - With the soft keyboard up, Back only closes the keyboard. Call `closeSoftKeyboard()` before `pressBack()`.
    - A click on a node with empty bounds raises no error, and lands at the screen's corner. The window resizes when the keyboard changes, even between `performScrollToNode` and `performClick`, so call `closeSoftKeyboard()` before a click that follows a scroll.
    - `and` and `or` on matchers are members and need no import. `assert(…)` on a node is an extension, imported from `androidx.compose.ui.test.assert`.
    - `captureToImage()` reads the colours and look of a screen in a test.
  - **JUnit 4 asserts:** `assertNotNull` returns nothing, so its result can't be used. To check a value and then use it, write `val x = checkNotNull(y) { "…" }`.
- **Ownership:** each phase owns its packages. Shared files (the catalog, build files, the manifest, `AppContainer`, `Routes` and `AppNavHost`) only get that phase's own entries.

## Environment

- `adb` is not on PATH: `D:\Android\Sdk\platform-tools\adb.exe` (Git Bash: `/d/Android/Sdk/platform-tools/adb.exe`). The phone is paired over USB **and** wireless debugging, so `adb devices` usually lists it twice, and the emulator adds another entry. Always pass `-s <serial>`; the `phone` skill picks it.
- Android SDK in `D:\Android\Sdk` (`ANDROID_HOME`): platforms up to `android-37.0`, build-tools up to 37.0.0, emulator 37.1. Android Studio 2026.1 (build 261), whose JDK 25 is `JAVA_HOME`.
- `gh` is authenticated as the repo's owner. Python 3.12 is installed.
- **What a library does that its docs don't say** can be read from its classes in the Gradle cache. Unzip the AAR's `classes.jar` from `~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>/` into the scratchpad, then read a class with `"$JAVA_HOME/bin/javap" -c -p -constants <file>.class`. String constants show names and messages; "Exception table" shows what each `catch` handles. P3 found DataStore's temporary file, its `fd.sync()` and when it deletes the file this way.
- Create and change files with the Write and Edit tools, not shell heredocs. In Workout Timer, a heredoc that wrote a Kotlin file failed to parse in Git Bash, and nothing was written.

## Git

- **The repo is public.** Never commit personal data or secrets: device serial numbers, names, e-mail or postal addresses, phone numbers, real treatments or screenshots that show them, keystores, passwords, `local.properties`. Examples and test data use the example in specification §4.4.
- `main` always builds. Each phase is built on its own branch, `phase/pN-<name>`, and merged through a pull request with green CI. The user merges.
- **CI runs only on pull requests and on pushes to `main`.** Pushes to a phase branch before its pull request exists start no run, so don't wait for one (skill `handover`).
- Draft documents (specification, plan, a phase's SPEC.md) are committed and pushed to `main` at every version. That includes a SPEC version made during implementation. Afterwards, merge `main` into the phase branch (skill `handover`).
- Phase status changes in plan §5.1 also go straight to `main`.
- HANDOVER.md updates go on the phase branch. The one exception: after the merge, the closing updates go on `main` (skill `phase-close`).
- Create phase branches with `--no-track` and push them with `-u`, so they never track `main` (skill `phase-start`).
- Tag and release only with the user's go-ahead.
