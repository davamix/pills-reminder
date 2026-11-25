# Pills Reminder: guide for Claude Code

An Android app that manages medical treatments and reminds the user when each medicine's next take is due. It runs on one phone, a POCO F3 (Android 13, API 33, Xiaomi HyperOS, portrait, 1080 × 2400). The emulator `PillsReminder_API_36` has the same screen.

**Status:** the specification (v0.12) and the implementation plan (v0.9) are agreed. P1–P8 are done: version 1.0.0 is released (tag `v1.0.0`, a GitHub release with the signed APK), with its acceptance record in `docs/phases/P8-integration/ACCEPTANCE.md`. The phases' status is in plan §5.1. Built with Kotlin and Jetpack Compose.

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
- `deliberate-bugs`: show that new tests can fail. Each bug of a bugs file, on its own: applied, the tests run, the file put back and checked with `cmp`; it says which tests caught each.
- `screen-gallery`: draw every state of a screen as screenshots: a throwaway gallery of the screen's `…Content` states, run light and dark at font scale 1 and 2 on the emulator, or on the phone; 4-per-sheet PNGs to read; kept out of the repo.

## Commands

| Task | Command |
|---|---|
| Compile, unit tests, Android Lint, ktlint, permission allowlist | `./gradlew build` |
| Fix Kotlin formatting | `./gradlew ktlintFormat` |
| Unit tests only | `./gradlew test` |
| One module's tests, filtered | `./gradlew :core:test --tests '*reminders*'`, with a package or class pattern; in `:app`, `:app:testDebugUnitTest --tests '…'`. Quicker than `build` while working on one package. |
| Install on one device | `gradle installDebug`, from the `phone` skill's helpers, which sets `ANDROID_SERIAL`. A bare `./gradlew installDebug` installs on every connected device. |
| Instrumented tests | `gradle installDebug installDebugAndroidTest && instrument` (`phone` skill). It keeps the app's data. On the phone, unlock it first; `instrument` grants the HyperOS permission the tests need (`allow_test_start`), which every install, update and restart resets. Avoid `connectedDebugAndroidTest`: it uninstalls the app afterwards, which deletes its data. |
| Release build, signed | `gradle assembleRelease` gives `app/build/outputs/apk/release/app-release.apk`, signed when the four `pillsReminder.release.*` properties (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) are in the user-level `~/.gradle/gradle.properties` (P8); without them, as in CI, it is unsigned. Check it with `D:\Android\Sdk\build-tools\37.0.0\apksigner.bat verify --verbose --print-certs <apk>`. |
| Install the release build | `gradle installRelease` (`phone` skill), or `$adb install -r <apk>`. Each update must be signed with the same key, or Android refuses it, and the only way round, uninstalling, deletes every treatment. |

- Gradle runs on Android Studio's JDK; `JAVA_HOME` already points to it.
- Run `ktlintFormat` as its own command, before `build`. In a single call (`./gradlew ktlintFormat build`), the ktlint check can still report violations that the format step has just fixed, and the build fails.
- `ktlintFormat` can't fix every line that's too long. It can leave one half-wrapped, and `build` then fails on ktlint. Wrap such lines by hand, for example by moving part of an expression into a local `val`. The reports in `app/build/reports/ktlint/` (or `core/…`) name the file and line.
- On a line that's too long, `ktlintFormat` can also leave code hard to read: in P4 it split a `${…}` template, and an `if` inside a lambda, across several lines. Don't keep such output: shorten the line instead, with a helper or a local `val`, and format again.
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
  - `time`: `dueAt(zone)` and `CurrentZoneClock`.
  - `reminders` (P4): `ReminderPlanner.plan(data, reminders, now)`, a pure function that returns a `ReminderPlan`. That holds the reminders the shade should hold, which of them alert, the next wake-up, the records to store (`recorded`) and `removals(inShade)`. `ReminderState` has one record per Due take, `Snoozed(until)` or `Alerted(count, lastAt)`, as instants in whole seconds, and `snooze(data, key, now)`. It is `PillsData.reminders` (P5), stored in the format's optional `reminders` list; a user's snooze goes through `core.actions` (`repository.snooze(key, clock)`).
- **Treatment data** (P2-D4):
  - Change it only through the `core.actions` helpers. They run the rules inside `update`, on the latest data, so an answer given from a notification while an editor is open is never lost.
  - Takes and their states are never stored (plan `D-5`): ask `MedicineTakes` for them.
  - Compare a planned time with now through `dueAt(now.zone)`, never as local times, which go wrong in the hour the clocks go back.
  - `AppContainer` has the `clock`, the `repository`, `reminders` (P5) and `checkStore` (P7).
- `:app` holds the Android code: `data/`, `reminders/`, `check/`, `ui/<feature>/`, `navigation/`, and `di/AppContainer.kt`.
- **The store** (`data`, P3): `AppContainer.repository` is a `DataStoreTreatmentRepository`, which keeps all of `PillsData` in one JSON document, `files/datastore/pills.json` on the device. Its format types (`PillsDocument.kt`) are kept apart from the model.
  - **A field added to `PillsData`**, or to a type in it, goes into the format in the same change, with a test. The store refuses to write data that doesn't read back the same (P3-D3), so a field left out fails at the first write that sets it.
  - **Golden files never change** (`app/src/test/resources/data/pills-v<N>.json`). A change that old files can't be decoded as raises `schemaVersion` and adds a migration (P3 SPEC §4.3.6).
  - **To make a golden file,** write the data's encoding to a file from a throwaway unit test, pretty-print it with `python -I -c` and `json.dumps(…, indent=2, ensure_ascii=False)`, and check it by eye against the format in the SPEC. Then delete the test (P3 T2). The golden test compares JSON trees, so the indentation doesn't matter.
  - `StoredData.Unreadable` means the file is kept as it is and every `update` is refused. Show it; never work around it. `update` can also throw an `IOException` when a write fails.
  - DataStore allows one store per file in the process, so the app stays in one process: no `android:process`.
- **Reminders** (`reminders`, P5): `AppContainer.reminders` keeps the notification shade and the single alarm in step with the data.
  - Its `Reconciler` applies P4's plan: one reconcile at a time, the plan worked out inside `repository.update` with its records stored, then the alarm clock (`setAlarmClock`, plan `D-8`), the removals, the posts and the group's summary. Running it twice changes nothing.
  - **It reconciles after every change of the data** (P5-D2): `Reminders.start()`, called by `PillsReminderApp`, observes `repository.data`. A phase that changes the data through `core.actions` gets the shade and the alarm updated with nothing to wire: an answer on the Take screen removes its reminder by itself.
  - **Android lists a post only some time after `notify` returns.** An alert stores its record, which starts another reconcile at once; it found the reminder missing and posted it again silently, which replaced the alerting post before its sound played (P7 SPEC Q10). So the `Reconciler` counts what it posted as in the shade until the shade lists it, or for `POSTED_GRACE` (10 s) at most. `FakeShade.lag` reproduces the delay in tests.
  - **The receivers of the system's broadcasts** (the alarm, a restart, an update, a change of time) reconcile with `Reminders.reconcileAndSettle`, which returns 2 s after a reconcile that posted an alert, so the broadcast, and the alarm's wake lock, last until Android has played it. On Android 13, with a notification assistant, the notification service waits 200 ms of awake time before alerting, with no wake lock: with the broadcast finished at once, the phone slept again in deep Doze, and the sound came up to 101 s late (P8). **Time an alert by the system's `notification_alert`, not the app's log, which records the post** (`phone` skill, `alert_events`).
  - The receivers (`Receivers.kt`) and `MainActivity.onStart` reconcile through `Reminders.runAsync`, in the process's scope. A tapped reminder opens `TakeRoute` right above Home (`AppNavHost(openTake, onTakeOpened)`); while the editors are open, above them instead, replacing a take already on top, so Back returns to the editors with their changes (P6 Q3).
  - Notifications have no category, so Do Not Disturb silences them; each is public, for the lock screen; a silent post is a group child with `GROUP_ALERT_SUMMARY`, since the platform has no `setSilent()` (P5 SPEC §4.3.4).
  - On HyperOS (P5 T1, T2, T8): alarm clocks are on time in deep Doze and after a restart (`BOOT_COMPLETED` comes a few seconds after the first unlock, thanks to the receiver's priority 999); with Autostart off, HyperOS blocks `MY_PACKAGE_REPLACED`, so after an update the reminders come back only at the next alarm or when the app opens; it shows no alarm-clock icon; it hides a notification already seen in the shade from the lock screen until its next alert; it keeps at most 10 notifications per app.
  - Debug builds log reminder events, IDs and times only, to logcat (tag `PillsReminders`) and to `files/reminders-log.txt`, with each alert's lateness (`late=`). Device runs read it with `run-as`.
- **The reminder check** (`check`, P7, spec `RM-9`): Settings shows what reminders depend on, and Home warns while something is missing.
  - `reminderCheck(device, record)` is a pure function: Android's items (notifications and the Reminders channel, exact alarms, battery optimisation) from `DeviceChecks`, then, on a Xiaomi phone only, HyperOS's steps (Autostart, Floating and Lock screen notifications), which the app can't read, so the user confirms them.
  - `ReminderCheckDevice` reads the device and opens each item's settings screen from a tap, trying `settingsScreens(item, device)` in order (`AndroidReminderCheckDevice`). It's in `ScreenDependencies` with `checkStore` and `reconcile`, so UI tests use fakes.
  - Its own record (first launch done, confirmed steps) is `files/datastore/reminder-check.json` (`DataStoreCheckStore`, P7-D1), apart from the treatments: it works while they can't be read, and a damaged one only shows the check again.
  - **The first launch** (no record) opens the check once above Home, which asks for the notification permission with Android's dialog and reconciles after a grant: the dialog only pauses the activity, so `onStart`'s reconcile doesn't run (P7-D6).
- **Debug tools** (plan `D-15`, P5), debug builds only, in `app/src/debug/`: a second launcher icon, "Pills debug" (`DebugActivity`). It creates test treatments in minutes (IDs start with `debug-`; one or two medicines, with or without instructions, for a day or with no end), ends and deletes them, and shows the next wake-up, the next alarm clock, the reminder records, the shade, what reminders depend on and the log. Its unit tests are in `app/src/testDebug/`.
- The base package is `io.github.davamix.pillsreminder`. The debug build's application ID adds `.debug` and its name is "Pills Reminder (debug)", so it installs next to a release build.
- **Edge to edge** (P1-D4): `MainActivity` calls `enableEdgeToEdge()` on every Android version, and screens keep their content out of the system bars through `Scaffold`. Every screen must fit the phone's content area, **392.7 × 796.4 dp**, which is smaller than the emulator's (`phone` skill, "Devices").
- **Navigation:** type-safe routes in `navigation/Routes.kt`, one per screen; since P7 none is a placeholder. Home is the start destination; `SettingsRoute(firstLaunch)` is the reminder check. The two editors are a nested graph on `TreatmentEditorRoute` (`navigation/EditorGraph.kt`), whose screens share one `EditorViewModel` from the graph's entry (P6-D1).
  - **A click that navigates** goes through `dropUnlessResumed`, so a double tap can't open a screen twice, or go back twice; `dropUnlessResumedWith` (P6) for a click that carries a value, such as a row's treatment.
  - **Navigation driven by a ViewModel state**, such as leaving the editor after a save, works differently: report it once from a `LaunchedEffect` on that state, after waiting with `lifecycle.currentStateFlow.first { it.isAtLeast(RESUMED) }`, and pass a plain lambda. A screen Android rebuilds after ending the app is composed before its entry is resumed, and `dropUnlessResumed` would silently drop the call (a Workout Timer lesson). `ui/common`'s `LeaveWhen(leave, onLeave)` does this.
- **Screens:** an entry composable that takes its ViewModel, defaulting to `viewModel(factory = …)`, and a stateless `…Content` composable that draws the state, from a state class the ViewModel builds. Since P6 (P6-D2), the factory takes `ScreenDependencies` (repository, clock, `allowMinutes`, minute ticks; and since P7 the check's device, its store and `reconcile`), which `AppNavHost(dependencies = …)` passes down, defaulting to the app's; not the container through `APPLICATION_KEY`. UI tests can then run the real navigation graph with fakes. Tests build the ViewModel directly, with fakes.
  - **States follow the clock:** a screen's state is `combine(repository.data, deps.minuteTicks, …)`, worked out again at each whole minute, `stateIn(viewModelScope, WhileSubscribed(…))` so the ticks stop when the screen isn't visible (P6-D3).
  - **`ui/common`** (P6) holds what the screens share: `DateTimeText` (dates and times as text, P6-D4), `DateField` and `TimeField` (buttons with their pickers), `CommonTexts.kt` (schedules, take states, statuses), `ChangeProblem` with `attempt { }` (unreadable data, a failed write, a refused change, as one message each) and `ShowProblem`, `UnreadableMessage`, `ConfirmDialog`, `BackButton`, `LeaveWhen`.
  - **Changes from a screen** go through `core.actions`' repository helpers inside `attempt { }`; a screen never catches other exceptions. Offer an action only when P2's pure function would apply it now (P6-D5).

## Conventions

- **Text:** all user-facing text lives in resources, one `res/values/strings_<feature>.xml` per feature (plan `D-14`), and texts several screens share in `strings_common.xml`. English only for now: `localeFilters += "en"` in `app/build.gradle.kts` keeps the libraries' own texts in English too (P6 Q8); a translation adds its language there.
- **Dates and times on screen** go through `DateTimeText` (`rememberDateTimeText()`): day and month names in the UI's language from `date_locale`, the hour format from the phone's 24-hour setting (spec `NF-8`, P6 Q5). Material's date picker takes the same locale with the phone's region, and the app's own headline.
- **Permissions:** only those in plan §3. The build's allowlist check fails on any other, and on `INTERNET` (P1-D6). A new one needs a plan change first.
- **Logs** never contain treatment content (names, doses, instructions, notes), only IDs and times (plan §3).
- **Time:** `java.time`, with an injected `Clock`. Logic never reads the system clock itself (plan `D-6`).
- **Versions:** pinned in `gradle/libs.versions.toml`. AGP must stay at 9.3 or lower while Android Studio 2026.1 is the installed IDE. The Compose BOM stays at 2026.09.00: 2026.11.01 (Compose 1.13) needs `compileSdk` 37.1, and only the `android-37.0` platform is installed.
- **Kotlin style:** ktlint, official style (`.editorconfig`). Run `ktlintFormat` before committing.
- **Android 14 APIs:** `minSdk` is 33, and some Java APIs arrived later on Android, such as `LocalDate.EPOCH` (API 34). Lint's `NewApi` check fails the build on them; never suppress it, since the phone, at API 33, would crash (P6 T6).
- **Formatting numbers:** don't call `String.format` without a `Locale`. Lint warns (`DefaultLocale`), and the result would follow the phone's language. Pad with `padStart` instead.
- **Tests:**
  - **Unit tests:** in both modules, JUnit Jupiter, with sentence names in backticks. With AGP 9, `./gradlew build` runs only `:app`'s debug unit tests (`testDebugUnitTest`).
  - **New tests that pass on their first run:** check that they can fail, with one deliberate bug at a time in the code under test: the `deliberate-bugs` skill applies each, runs the tests, puts the file back and checks it with `cmp`. Note each bug, and the tests that caught it, in the handover. P4 and P5 did about fifty this way.
  - **Characters in test names:** names in backticks can't contain `.` `;` `:` `/` `\` `[` `]` `<` `>` on the JVM; the compiler rejects them. Use commas, or "and", instead. Write times as `0800` or `16h00`, not `08:00`.
  - **Instrumented tests** (`app/src/androidTest`): JUnit 4, also with sentence names in backticks (P1-D7). D8 takes spaces because `minSdk` is 33, but **no other ASCII punctuation except `-`, `_` and `$`**: no commas, no parentheses, no apostrophes. A comma failed the dex step in P1 T6, and "reminder's" in P6 T7. To run one of them, run its class: `am instrument -e class Class#method` splits at the spaces.
  - **Instrumented tests and the real data:** `AppContainer.repository` is the app's real file. A test that goes through `MainActivity` or the container reads and writes the data the app really has, on the phone too. Build ViewModels with fakes, and leave the data as it was found. Store tests use their own file in the cache; `StoreDeviceCheck` touches the real data only when run with `-e storeCheck <step>`. UI flow tests run `AppNavHost` with an in-memory repository and a fixed clock: `FakeApp` and `showApp` in `androidTest/…/ui/UiTesting.kt` (P6-D2), with expected dates built by `dateTimeText()`, so they pass on a 12-hour emulator and a 24-hour phone alike. `FakeApp` also has a fake check device (everything set, not HyperOS), an in-memory check record with the first launch done, and counts `reconcile` (P7).
  - **Tests through `MainActivity` and the first launch** (P7): with no check record, Home opens the check, and Android's permission dialog with it. Such a test takes `@get:Rule(order = 0) val firstLaunchDone = FirstLaunchDone()`, outside the activity's rule: it marks the first launch done in the real record for the run and writes back what it found.
  - **Instrumented tests and the reminders:** a test that starts `MainActivity` also reconciles the real data, as opening the app does. Reminder tests post with their own tag prefix (`AndroidReminderShade(context, "test-take")`) and set their alarm with their own action (`…WAKE_UP.TEST`), so they never touch the app's reminders, and remove what they posted. On the phone they post only silent reminders. `ReminderShadeTest` grants `POST_NOTIFICATIONS` through `UiAutomation`.
  - **Coroutine tests:** in `runTest`, `advanceUntilIdle()` ignores work that only `backgroundScope` has scheduled. Wait in the foreground instead, for example with `flow.first { … }`. `advanceTimeBy()` does run background work.
  - **ViewModel tests** (P6): `vmTest { }` in `app/src/test/…/ui/ViewModelTesting.kt` sets the main dispatcher before `runTest` and resets it after; reset inside `runTest`, `stateIn`'s pending stop timer ran on a Main that was gone (`CompletionHandlerException`). Read a state with `state.next { predicate }`: right after a synchronous change, a `stateIn` flow still holds its old value, so wait for the new one by its predicate, or call `advanceUntilIdle()` first. Minute ticks come from `TestTicks`, never an endless delay loop, which `advanceUntilIdle()` would never get past.
    - P7's tests keep the state collected for the whole test, `backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }`, and read `state.value` after `advanceUntilIdle()`. A state that never comes then fails at once, instead of `next { … }` waiting for `runTest`'s timeout: deliberate bugs run much faster (`HomeViewModelTest`).
  - **Compose UI tests:** use the rules from `androidx.compose.ui.test.junit4.v2` (`createComposeRule`, `createAndroidComposeRule`); the v1 rules are deprecated. `pressBack()` comes from Espresso. After an action that starts a coroutine, wait for its result (`waitUntil { … }` or `waitForIdle()`) before asserting. Also, from Workout Timer:
    - A `LazyColumn` item exists only once it's scrolled into composition. Tag the list, and call `performScrollToNode(matcher)` on it before using an item.
    - With the soft keyboard up, Back only closes the keyboard. Call `closeSoftKeyboard()` before `pressBack()`.
    - A click on a node with empty bounds raises no error, and lands at the screen's corner. The window resizes when the keyboard changes, even between `performScrollToNode` and `performClick`, so call `closeSoftKeyboard()` before a click that follows a scroll.
    - `and` and `or` on matchers are members and need no import. `assert(…)` on a node is an extension, imported from `androidx.compose.ui.test.assert`.
    - `captureToImage()` reads the colours and look of a screen in a test.
    - From P6: Material's date picker gives each day cell its whole date as text ("Monday, November 23, 2026", in the picker's locale): pick a day with `hasText("23", substring = true) and hasClickAction()` (`pickDay`). `ExtendedFloatingActionButton(text = …, icon = …)` hides its label from the semantics tree, and from TalkBack; its content overload keeps it. A text that is also a button's, such as "Taken", needs `and hasClickAction()` to find only the button.
    - From P7: Material 3's buttons are drawn 40 dp tall and take touches over 48 dp, so `assertHeightIsAtLeast(48.dp)` fails on them; check a button with `assertTouchHeightIsAtLeast` (`UiTesting.kt`, from `touchBoundsInRoot`), and rows with `assertHeightIsAtLeast`. To test what happens when the app comes back to the front, use `createAndroidComposeRule<ComponentActivity>()` and `activityRule.scenario.moveToState(STARTED)` then `RESUMED`.
  - **JUnit 4 asserts:** `assertNotNull` returns nothing, so its result can't be used. To check a value and then use it, write `val x = checkNotNull(y) { "…" }`.
- **Ownership:** each phase owns its packages. Shared files (the catalog, build files, the manifest, `AppContainer`, `Routes` and `AppNavHost`) only get that phase's own entries.

## Environment

- `adb` is not on PATH: `D:\Android\Sdk\platform-tools\adb.exe` (Git Bash: `/d/Android/Sdk/platform-tools/adb.exe`). The phone is paired over USB **and** wireless debugging, so `adb devices` usually lists it twice, and the emulator adds another entry. Always pass `-s <serial>`; the `phone` skill picks it.
- Android SDK in `D:\Android\Sdk` (`ANDROID_HOME`): platforms up to `android-37.0`, build-tools up to 37.0.0, emulator 37.1. Android Studio 2026.1 (build 261), whose JDK 25 is `JAVA_HOME`.
- `gh` is authenticated as the repo's owner. Python 3.12 is installed.
- **What a library does that its docs don't say** can be read from its classes in the Gradle cache. Unzip the AAR's `classes.jar` from `~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>/` into the scratchpad, then read a class with `"$JAVA_HOME/bin/javap" -c -p -constants <file>.class`. String constants show names and messages; "Exception table" shows what each `catch` handles. P3 found DataStore's temporary file, its `fd.sync()` and when it deletes the file this way.
- Create and change files with the Write and Edit tools, not shell heredocs. In Workout Timer, a heredoc that wrote a Kotlin file failed to parse in Git Bash, and nothing was written. **A script that edits files** goes in a scratchpad file too, written with Write and run with `python -I "$(cygpath -w <file>)"`: in P5, a heredoc's quotes broke the shell, and another lost its backslashes, writing control characters into a script. It writes files back with `newline="\n"` (or as bytes): on Windows, `Path.write_text` turns every line ending into CRLF (P7 T8).

## Git

- **The repo is public.** Never commit personal data or secrets: device serial numbers, names, e-mail or postal addresses, phone numbers, real treatments or screenshots that show them, keystores, passwords, `local.properties`. Examples and test data use the example in specification §4.4.
- **The release keystore** is outside the repo, `%USERPROFILE%\.android\keystores\pills-reminder-release.jks`, and its password only in the user-level `~/.gradle/gradle.properties` (P8). Never print the password, or put it in a command line, a file in the repo or a commit.
- `main` always builds. Each phase is built on its own branch, `phase/pN-<name>`, and merged through a pull request with green CI. The user merges.
- **CI runs only on pull requests and on pushes to `main`.** Pushes to a phase branch before its pull request exists start no run, so don't wait for one (skill `handover`).
- Draft documents (specification, plan, a phase's SPEC.md) are committed and pushed to `main` at every version. That includes a SPEC version made during implementation. Afterwards, merge `main` into the phase branch (skill `handover`).
- Phase status changes in plan §5.1 also go straight to `main`.
- HANDOVER.md updates go on the phase branch. The one exception: after the merge, the closing updates go on `main` (skill `phase-close`).
- Create phase branches with `--no-track` and push them with `-u`, so they never track `main` (skill `phase-start`).
- Tag and release only with the user's go-ahead.
