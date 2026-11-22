# P7 — Home screen and reminder check: Specification

| | |
|---|---|
| **Status** | Ready, v0.4 |
| **Phase** | P7, see [Implementation plan §5](../../IMPLEMENTATION_PLAN.md#5-phases) |
| **Depends on** | P2 (done). P3, P5 and P6 are merged too, so Home uses the real store, the reminders follow every answer given on it, and its rows open P6's screens. |
| **Branch** | `phase/p7-home` |
| **Handover** | [HANDOVER.md](HANDOVER.md) |

This document holds everything needed to implement the phase. Working on it shouldn't require reading the full specification or plan.

## 1. Goal

The last two screens, replacing P1's placeholders:

- **Home:** the next take of each medicine, due takes first and highlighted, each with a Taken button; the time left and the progress; marking a next take Taken early; upcoming treatments; an empty state; a message when the stored data can't be read. It follows the clock and the data while open.
- **Settings, with the reminder check:** what reminders depend on (the notification permission, exact alarms, battery optimisation), each with a button to the right settings screen, and on this phone HyperOS's own settings as steps to confirm. The check opens on the first launch, and Home warns while something is missing.

After P7, every screen of spec §6 exists. P8 then wires and accepts the whole app.

## 2. Requirements covered

The text of each requirement is quoted from [SPECIFICATION.md](../../SPECIFICATION.md) (version v0.11), so this document stands on its own. P2 implemented the rules; P7 shows them and calls them.

| ID | Requirement (quoted) | What this phase does for it |
|---|---|---|
| HM-1 | "The home screen shows the next take of each medicine in the active treatments: its date and time, how long until then ("in 2 h 15 min"), the medicine and the dose." | One row per medicine of each Active treatment, from P2's `nextTake(now)` (§4.3.2, §4.3.3). |
| HM-2 | "Due takes come first, highlighted, each with a **Taken** button. The others follow, ordered by time." | A Due section, highlighted, with Taken; then a Next section, by planned time (§4.3.2, §4.3.4). |
| HM-3 | "Each medicine shows its progress, such as "5 of 21" (takes answered out of the total), and its treatment's name." | "Example · 1 of 21", from `answeredCount` and `count`; "No end" for a medicine with no end (§4.3.3). |
| HM-4 | "Upcoming treatments show when they start. With no treatment active or upcoming, the screen says so and offers to create one." | An Upcoming section with each one's first take; the empty state, with New treatment (§4.3.2, §4.3.5). |
| HM-5 | "While the home screen is open, the times left and the states stay current, without leaving and coming back." | The state is worked out again at each whole minute and at each change of the data (§4.3.1). The reminder check is read again each time the screen comes back to the front (§4.3.9). |
| TK-2 | "Marking a take Taken records the time it was marked. That time can be corrected afterwards, for example to "taken at 08:20"." | Taken on a Due row records now, through P2's `markTaken`. Correcting the time stays on P6's Take screen. |
| TK-4 | "A medicine's next take can be marked Taken before it is due, from the app, for example before going out. Its reminder is then cancelled. The next take is the medicine's Due take, if it has one, or else its first unanswered take still to come. Only one take of a medicine can be marked ahead of its time: the one after it can be marked early once the marked take's planned time has passed." | "Taken early" on a Next row whenever P2's `markTaken` would apply, after a confirmation (Q5, §4.3.4). P5's reconcile then never alerts for it. |
| RM-9 | "**Reminder check:** the app checks what its reminders depend on: the notification permission; its Reminders notification category, which must be on and allowed to pop up and sound; the exact-alarm permission; and Android's battery optimisation for the app, which HyperOS's battery saver at "No restrictions" also turns off. It shows what is missing, each with a button that opens the right settings screen, or Android's own dialog for battery optimisation. On HyperOS, the settings the app can't read (Autostart, Floating notifications and Lock screen notifications) are listed as steps for you to confirm. The check is shown on first launch, where Android's dialog asks for the notification permission, and in Settings. The home screen warns while something is missing or a step isn't confirmed." | The check in Settings (§4.3.7–§4.3.9), opened at the first launch (§4.3.10), and Home's warning (§4.3.6). Each button's screen as T1 measured it (§4.3.8, §4.6). |
| NF-7 | "**Phone layout:** portrait only, for a screen about 393 dp wide. Touch targets are at least 48 dp. Text stays usable with the system's larger font sizes." | Both screens fit the phone's content area, with 48 dp targets, and are drawn at font scale 2 (§4.3.11, T2, T7). |
| NF-8 | "**Language and formats:** the UI is in English. All user-facing text lives in resource files, so a Spanish translation can be added later without code changes. Times follow the phone's 24-hour clock setting. Day and month names are in the UI's language, such as "Mon 23 Nov", so a translation brings its own." | Every text in `strings_home.xml` and `strings_settings.xml`, the time left's units included; dates and times through P6's `DateTimeText`. |
| NF-10 | "**Theme:** follows the system's light or dark setting." | P1's theme; every state is drawn in both. |

Also touched, with nothing new to build:

| ID | Requirement (quoted) | What this phase does for it |
|---|---|---|
| SC-5 | "A medicine with no end keeps its schedule until its treatment is ended (`TR-11`) or the medicine is removed from the treatment. Where the app shows a number of takes or a last take (`SC-2`, `HM-3`), it shows "No end" instead." | Home's progress says "No end" in place of the total (§4.3.3). |
| RM-7 | "When a take is answered in the app, or becomes Missed, its reminder is removed from the shade." | P5 reconciles after every change of the data (P5-D2), so Taken on Home removes its reminder. Checked on the phone (M3). |
| TK-1, TK-3 | "Each planned take has a state: Pending (not yet due), Due, Taken, Skipped or Missed (§4.3)." / "A take still unanswered one interval after its planned time becomes Missed. […]" | A row moves from Next to Due at its minute, and goes once Missed, at the next tick (§4.3.1). |

### 2.1 What the specification and the plan say, quoted

Spec §6, the screens this phase builds:

| Screen | Purpose | Main actions |
|---|---|---|
| **Home: next takes** | The next take of each medicine, due takes first (`HM-1` to `HM-5`) | Mark a due take Taken, or a next take Taken early (`TK-4`, with confirmation); open a treatment, new treatment, all treatments, settings |
| **Settings** | The reminder check (`RM-9`) | Open Android's settings, confirm HyperOS's steps, back |

Spec §6's sketch of Home (low fidelity, layout only), on Monday at 16:05: Amoxicillin's 16:00 take is due, and Atrovent's was taken at 16:01.

```
+------------------------------------------+
| Next takes                          [⚙]  |
|                                          |
|  DUE                                     |
|  Amoxicillin · 1 capsule                 |
|  Today 16:00 · 5 min ago    [ Taken ]    |
|  Example · 1 of 21                       |
|                                          |
|  NEXT                                    |
|  Atrovent · 3 puffs                      |
|  Tomorrow 00:00 · in 7 h 55 min          |
|  Example · 2 of 15                       |
|                                          |
|                                          |
|  [ Treatments ]              [ + New ]   |
+------------------------------------------+
```

Spec §4.4, the example the tests use: treatment "Example", started on Monday 23 November 2026.

- Amoxicillin: 1 capsule every 8 h for 7 days, first take Monday 08:00 (21 takes, the last Mon 30 Nov 00:00).
- Atrovent: 3 puffs every 8 h for 5 days, first take Monday 08:00 (15 takes, the last Sat 28 Nov 00:00).

"On Monday at 10:30, with both 08:00 takes marked Taken, the home screen shows:"

| Next take | Medicine | Dose | Progress |
|---|---|---|---|
| Today 16:00 (in 5 h 30 min) | Amoxicillin | 1 capsule | 1 of 21 |
| Today 16:00 (in 5 h 30 min) | Atrovent | 3 puffs | 1 of 15 |

Spec §4.3 rule 7: "A treatment is **Upcoming** until its first take, **Active** while it has takes still to come or unanswered, and **Finished** after that, or once it is ended early (`TR-11`). A treatment with a medicine that has no end stays Active until it is ended."

Spec §3, on this phone: "**HyperOS restricts apps in the background.** For an app installed outside the store, HyperOS turns *Autostart* off and limits its battery use. Either can stop reminders after the app is swiped away or the phone restarts. The app can't change these settings, and can't read Autostart at all, so it guides you to them (`RM-9`)." P5 measured what they really do (§2.2).

Plan §2.4, on a stored file that can't be read: "The home screen shows a message (P7), and the editors refuse to save (P6)." And: "The app offers no repair. The file stays as it is, so that it can be read over adb."

Plan `D-8`: "The alarm is an **alarm clock**, `setAlarmClock()`, with the `USE_EXACT_ALARM` permission. That permission is granted at install and can't be revoked."

Plan §3, the permissions the app may declare, among them "`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: lets the reminder check ask Android to stop optimising the app's battery use (`RM-9`)". P5 declared it already.

Plan §2.3: "The home screen (P7) reads the same schedule and take states as the planner, and works them out again each minute while it is open (`HM-5`)."

Plan §5.2 (v0.9), P7's scope for the check: "the notification permission, asked with Android's dialog at first launch, when the check opens once above Home; afterwards its button opens Android's settings (P7 Q6); the app's Reminders channel: on, and allowed to pop up and sound (P7 Q3); exact alarms; battery optimisation, with Android's own request dialog; on HyperOS, the app's battery saver at "No restrictions" also turns it off, which P5 found (P7 Q2, T1); on a Xiaomi phone, HyperOS's Autostart, Floating notifications and Lock screen notifications, which P5's measurements named, as steps for the user to confirm, each with a button to its settings screen (P7 Q4); its own record, in a second, small file (§2.4, P7 Q1); a warning on the home screen while something is missing or a step isn't confirmed."

Plan §2.4 (v0.8): "**The reminder check's record** is a second, small file beside it, also written by DataStore as JSON: whether the first launch is done, and which HyperOS steps the user has confirmed. It holds no treatment content. A missing or damaged one reads as empty, and the next write replaces it: the check then shows again (P7 Q1)."

P7's done criteria in plan §5.2: "Compose UI tests cover the home screen's order, a due take, a take marked early and the empty state; on the emulator (API 36), the reminder check follows each permission being revoked and granted again; on the phone, each of the check's buttons opens the right screen."

### 2.2 What earlier phases left for P7

From their handovers' "Pending wiring", "Decisions and deviations" and "Known issues":

- **P5, the reminder check** (P5 SPEC §4.6, measured on this phone with stock settings, then relaxed):
  - the notification permission is needed;
  - `USE_EXACT_ALARM` is granted at install, on the phone and the emulator (A1);
  - **battery optimisation and HyperOS's battery saver aren't needed:** alarm clocks were on time with both at their defaults, in every case, "though `RM-9` still asks the check to show battery optimisation" (Q2);
  - **on HyperOS they are linked one way:** the app's battery saver at "No restrictions" made Android treat the app as exempt from battery optimisation (P5 T1, A2 run 3), so `isIgnoringBatteryOptimizations()` reads it. P7's T1 found that Android's dialog sets "No restrictions" too, but HyperOS's default doesn't undo the dialog's exemption (§4.6, C2, Q7);
  - **Autostart** is needed so that reminders come back right after an app update: with it off, HyperOS blocks `MY_PACKAGE_REPLACED`, and the update has removed the notifications. Not for restarts: `BOOT_COMPLETED` came with it off;
  - HyperOS's "Floating notifications" and "Lock screen notifications" for the app are on by default, and "worth listing as steps to confirm, since turning them off would hide the pop-up and the lock screen content";
  - "Once the check gets the notification permission granted, `MainActivity.onStart`'s reconcile posts the reminders the plan holds." That holds after a visit to Android's settings, which stops the activity. Android's permission dialog only pauses it, on both devices (T1, C4), so the check reconciles after the dialog's answer (P7-D6).
  - "Taken on the home screen removes the reminder by itself (P5-D2)."
- **P6, Home:** "P1's Home placeholder stays. Its "All treatments" and "New treatment" open P6's screens; its "Sample take" opens a take that doesn't exist […]. P7 replaces it, opens `TreatmentRoute(id)` from a medicine's row, and can reuse `ScreenDependencies`, `minuteTicks`, `DateTimeText` and the messages in `ui/common` (SPEC P6-D2–P6-D4)."
- **P6's flow tests tap Home's buttons by their texts,** `home_all_treatments` and `home_new_treatment` (`compose.tap(…)`, which finds one node by its text): `CreateTreatmentTest`, `DiscardChangesTest`, `EditStartedTreatmentTest`, `EditorValidationTest`, `EndAndDeleteTest`, `ScreensNavigationTest`, `TakeScreenTest`. Home keeps both as buttons with those texts, each on one node only, so they pass unchanged (§4.3.5).
- **P5's `OpenTakeTest`** opens Settings from Home by the placeholder's "Open settings" text, and asserts Home by `home_title`. P7 changes only how it opens Settings (§4.1).
- **P3, damaged data and failed writes:** "when `repository.data` is `StoredData.Unreadable`, the home screen says so […]. When `update` throws an `IOException`, the screen says the change wasn't saved." P6's `attempt { }`, `ShowProblem` and `UnreadableMessage` do both (§4.3.5).
- **P3, instrumented tests:** "a test that goes through `MainActivity` or `AppContainer` uses the real stored data, on the phone too. Build ViewModels with fakes, and leave the data as it was found." The check's own record is real data too (§4.3.10, §6).
- **P6's decisions that apply here:** the screens get `ScreenDependencies` from `AppNavHost` (P6-D2); states are `combine(repository.data, deps.minuteTicks, …)` with `stateIn(viewModelScope, WhileSubscribed(…))` (P6-D3); dates and times through `DateTimeText` (P6-D4); offer an action only when P2's pure function would apply it now (P6-D5); labels before a day take a colon ("Starts: Tomorrow 08:00"), since "Today", "Tomorrow" and "Yesterday" are capitalised.
- **P6's pitfalls that apply here:** `ExtendedFloatingActionButton(text = …, icon = …)` hides its label from TalkBack and from tests, so use its content overload; a text that is also a button's, such as "Taken", needs `and hasClickAction()` in tests; buttons with text side by side broke a word at font scale 2 (P5, P6), so they go one under the other where that can happen; a `LocalDensity` with a larger font scale doesn't reach dialogs, so draw font scale 2 with the emulator's own setting (`screen-gallery` skill).
- **P2's rules Home relies on:** `nextTake(now)` is the Due take, or else the first unanswered take still to come, or `null`; `canMarkEarly(plannedAt, now)` and the pure `markTaken(key, now)` agree on TK-4; `status()` and `takesOf()` return `null` for an unknown ID; `answeredCount` counts Taken and Skipped, not Missed; `count` and `last` are `null` for no end; `firstTake(treatmentId)` gives an Upcoming treatment's start.
- **HyperOS blocks activities started from the background** (P1). P7 starts activities only from a tap, and Android's permission dialog while the app is on screen (§4.3.10).

## 3. Scope

### In scope

- **Home:** its ViewModel, state and composables, replacing P1's placeholder: the Due, Next and Upcoming sections, Taken, Taken early with a confirmation, the empty state, the unreadable message, the check's warning, and its buttons (all treatments, new treatment, settings). Each row opens its treatment.
- **The reminder check:** what the app reads from the device, the check's logic (what is missing), the HyperOS steps and their confirmations, kept in a small store of their own (Q1), and the intents that open each settings screen (T1).
- **Settings:** its ViewModel, state and composables, replacing P1's placeholder: the check, read again each time the screen comes back to the front.
- **First launch:** Home opens the check once; the check asks for the notification permission with Android's dialog (Q6).
- **Measuring first** (T1): on the phone and the emulator, what each settings intent opens, what the app can read of HyperOS's settings, and what the permission dialog does to the activity.
- **Tests:** unit tests of the check's logic, its store, the time left and both ViewModels, with fakes; Compose UI tests of Home and the check with fakes; every state of both screens drawn; manual checks on the phone and the emulator.

### Out of scope

| What | Phase |
|---|---|
| The rules: next takes, states, TK-4, statuses | P2 (done). P7 changes no rule in `:core`. |
| Correcting the time taken, Skip and Undo (`TK-2`, `TK-5`): on the Take screen, which a row reaches through its treatment | P6 (done) |
| Notifications and the alarm. Taken on Home reaches them through the reconcile on every change (P5-D2). | P5 (done) |
| Changing HyperOS's or Android's settings for the user: the app can't; it opens the screens | — |
| Reading Autostart: the app can't (spec §3). The check asks the user to confirm it. | — |
| A home-screen widget, a history of takes | Not in v1 (spec §2) |
| The end-to-end test, the acceptance record, the release build | P8 |
| The reminder that can lose its sound, found in M3: a race in P5's reconciler (Q10) | Its own fix, in a branch from `main` after P7, before P8 |

## 4. Design

### 4.1 Packages and files owned

P7 owns the packages `check`, `ui.home` and `ui.settings` in `:app`, and their strings files. File names are suggestions; the behaviour in §4.3 is what matters.

```
app/src/main/kotlin/io/github/davamix/pillsreminder/
├── check/                         (new)
│   ├── ReminderCheck.kt           the check's items and their status: a pure function (§4.3.7)
│   ├── ReminderCheckDevice.kt     what the app reads from the device, and the settings screens it opens (§4.3.8)
│   ├── CheckStore.kt              the check's own record: first launch, confirmed steps (Q1); an in-memory store
│   └── DataStoreCheckStore.kt     the record in files/datastore/reminder-check.json
├── ui/home/
│   ├── HomeContent.kt, HomeScreen.kt, HomeViewModel.kt
│   └── TimeLeft.kt                "in 5 h 30 min", "5 min ago": the numbers, worked out on the JVM (§4.3.3)
└── ui/settings/
    └── SettingsContent.kt, SettingsScreen.kt, SettingsViewModel.kt

app/src/main/res/values/
├── strings_home.xml               (P1's file, now P7's)
└── strings_settings.xml           (P1's file, now P7's)

app/src/test/kotlin/io/github/davamix/pillsreminder/check/…       the check's logic and store
app/src/test/kotlin/io/github/davamix/pillsreminder/ui/home/…     HomeViewModelTest, TimeLeftTest
app/src/test/kotlin/io/github/davamix/pillsreminder/ui/settings/… SettingsViewModelTest
app/src/androidTest/kotlin/io/github/davamix/pillsreminder/ui/…   HomeScreenTest, ReminderCheckScreenTest, with fakes
```

**Shared files changed,** with P7's entries only:

- `ui/common/ScreenDependencies.kt` (P6's): three entries, `checkDevice`, `checkStore` and `reconcile` (§4.2); `of(container)` fills them from the container.
- `di/AppContainer.kt`: `checkStore`, the `DataStoreCheckStore` on its file, in a scope that lasts as long as the process, like the treatments' store.
- `navigation/Routes.kt`: `SettingsRoute` becomes `data class SettingsRoute(val firstLaunch: Boolean = false)` (§4.3.10).
- `navigation/AppNavHost.kt`: Home's and Settings' destinations; the placeholder's `sampleTake` goes.
- `ui/common/PlaceholderScreen.kt` and `strings.xml`'s `placeholder_note` (P1's): no screen uses them after P7, so they go.
- `androidTest/…/ui/UiTesting.kt` (P6's): `FakeApp` passes a fake device (everything allowed, not HyperOS), an in-memory check store with the first launch done, and a `reconcile` that counts its calls; each can be set by a test. `FakeApp(unreadable = true)` starts with unreadable data. Two helpers: `quantity(id, count)` for plural texts, and `assertTouchHeightIsAtLeast(min)` (§6).
- `androidTest/…/FirstLaunchDone.kt` (new): the rule for tests through `MainActivity` (§6).
- `androidTest/…/NavigationSmokeTest.kt` (P1's, trimmed in P6): the sample take's test goes; Settings opens from Home's settings button. It starts `MainActivity` with the first launch done (§6).
- `androidTest/…/reminders/OpenTakeTest.kt` (P5's): opens Settings from Home's settings button, by its content description; starts with the first launch done.
- `test/…/ui/ViewModelTesting.kt` (P6's): `deps(…)` gets fakes for the three new entries.
- `CLAUDE.md` (T8): Home, the check and its store, and the first-launch rule for tests through `MainActivity`.

**Not changed:** `:core`, `MainActivity`, the store of treatments, the reminders, and `AndroidManifest.xml`: no new permission, no `<queries>`. Starting another app's activity needs no package visibility; only resolving it first would, so the code tries the intent and falls back (§4.3.8). If a change to one of these turns out to be needed, stop and ask.

### 4.2 Interfaces and data

Signatures are a guide. The state classes hold only what the screens draw; texts are built in the composables from resources.

```kotlin
// check

/** What the reminder check reads from the device (RM-9). */
data class DeviceChecks(
    val notificationsAllowed: Boolean,          // NotificationManager.areNotificationsEnabled()
    val channel: ChannelState,                  // the Reminders channel (Q3)
    val exactAlarms: Boolean,                   // AlarmManager.canScheduleExactAlarms()
    val batteryOptimised: Boolean,              // !PowerManager.isIgnoringBatteryOptimizations(package)
    val hyperOs: Boolean,                       // a Xiaomi phone, so HyperOS's steps apply (Q4, T1)
)
enum class ChannelState { ON, NO_POP_UP, OFF }  // importance HIGH or more (or no channel yet); lower; NONE

enum class HyperOsStep { AUTOSTART, FLOATING_NOTIFICATIONS, LOCK_SCREEN_NOTIFICATIONS }   // Q4, T1
/** Each item of §4.3.7; a HyperOS step carries its [step]. */
enum class CheckItem(val step: HyperOsStep?) {
    NOTIFICATIONS(null), EXACT_ALARMS(null), BATTERY(null),
    AUTOSTART(HyperOsStep.AUTOSTART), FLOATING_NOTIFICATIONS(…), LOCK_SCREEN_NOTIFICATIONS(…),
}
enum class LineStatus { OK, MISSING, TO_CONFIRM, CONFIRMED }
/** Why the Notifications line is missing: each has its own text and opens its own screen (§4.3.7, §4.3.8). */
enum class NotificationsProblem { NOT_ALLOWED, CHANNEL_OFF, NO_POP_UP }
data class CheckLine(val item: CheckItem, val status: LineStatus, val problem: NotificationsProblem? = null)

/** [hyperOs]: the battery line adds its HyperOS sentence (§4.3.7). */
data class ReminderCheck(val lines: List<CheckLine>, val hyperOs: Boolean) {
    /** What Home warns about: lines MISSING or TO_CONFIRM. */
    val toCheck: Int
}

/** The check: Android's items always, HyperOS's steps only on a HyperOS phone, in the order of §4.3.7. */
fun reminderCheck(device: DeviceChecks, record: CheckRecord): ReminderCheck
fun channelStateOf(importance: Int?): ChannelState        // null (no channel yet) ON; NONE OFF; below HIGH NO_POP_UP
fun isHyperOs(manufacturer: String): Boolean              // "Xiaomi", ignoring case (T1, C6)

/** The screens an item's button tries, in order (§4.3.8): pure, so the rules have JVM tests. */
enum class SettingsScreen { APP_NOTIFICATIONS, CHANNEL, APP_DETAILS, BATTERY_DIALOG, BATTERY_ANY, AUTOSTART_LIST }
fun settingsScreens(item: CheckItem, device: DeviceChecks): List<SettingsScreen>

/** Reads the device, and opens the settings screen of an item. Fakes in tests (P7-D2). */
interface ReminderCheckDevice {
    /** What the device says now: a few calls to system services, quick enough for the main thread. */
    fun read(): DeviceChecks
    /** Opens [item]'s settings screen, or Android's dialog for battery optimisation, from [activity] (§4.3.8). */
    fun open(activity: Context, item: CheckItem)
}
class AndroidReminderCheckDevice(context: Context) : ReminderCheckDevice

/** The check's own record (Q1): never treatment content. */
data class CheckRecord(val firstLaunchDone: Boolean = false, val confirmed: Set<HyperOsStep> = emptySet())
interface CheckStore {
    val record: Flow<CheckRecord>
    /** Throws an IOException when the write fails. */
    suspend fun update(transform: (CheckRecord) -> CheckRecord)
}
class InMemoryCheckStore(initial: CheckRecord = CheckRecord()) : CheckStore      // tests, in main like core's fake
class DataStoreCheckStore(dataStore: DataStore<CheckRecord>) : CheckStore {
    companion object { const val FILE_NAME = "reminder-check.json"; fun create(file: File, scope: CoroutineScope): DataStoreCheckStore }
}
```

The record's file: `{"schemaVersion":1,"firstLaunchDone":true,"confirmed":["AUTOSTART","LOCK_SCREEN_NOTIFICATIONS"]}`, the steps by name, sorted. A missing file, a damaged one, another `schemaVersion`, or one that can't be opened reads as the empty record; a step name it doesn't know is left out. A write that fails throws an `IOException`.

```kotlin
// ui/common: P7's entries in P6's ScreenDependencies
data class ScreenDependencies(
    val repository: TreatmentRepository,
    val clock: Clock,
    val allowMinutes: Boolean,
    val minuteTicks: Flow<Unit>,
    val checkDevice: ReminderCheckDevice,
    val checkStore: CheckStore,
    /** Asks the reminders to catch up now: after the notification permission is granted (P7-D6). */
    val reconcile: () -> Unit,
)
```

```kotlin
// ui/home
data class HomeRow(
    val key: TakeKey,
    val treatmentId: TreatmentId,
    val treatment: String,
    val medicine: String,
    val dose: String,
    val plannedAt: LocalDateTime,
    val timeLeft: TimeLeft,
    val answered: Int,
    val count: Long?,                       // null: no end (SC-5)
    val canMarkTaken: Boolean,              // Due: Taken; Next: Taken early (TK-4), from P2's pure markTaken
)
data class UpcomingRow(val id: TreatmentId, val name: String, val startsAt: LocalDateTime, val timeLeft: TimeLeft)
sealed interface HomeData {
    data object Unreadable : HomeData
    data class Ready(val due: List<HomeRow>, val next: List<HomeRow>, val upcoming: List<UpcomingRow>,
                     val today: LocalDate) : HomeData                 // all three empty: the empty state
}
data class HomeState(
    val data: HomeData?,                    // null until the stored data is read
    val toCheck: Int,                       // the check's warning (§4.3.6); 0: no warning, and until the record is read
    val openCheck: Boolean,                 // the first launch: open the check (§4.3.10); false until the record is read
    val problem: ChangeProblem? = null,
)
class HomeViewModel(deps: ScreenDependencies) : ViewModel() {
    val state: StateFlow<HomeState>
    fun markTaken(key: TakeKey)             // Taken, and Taken early once confirmed
    fun refreshCheck()                      // each time the screen is resumed (§4.3.9)
    fun checkOpened()                       // the first launch's check is open: never again
    fun problemShown()
}

/** The time from now to a planned instant, in whole minutes: rounded up when ahead, down when past (§4.3.3). */
data class TimeLeft(val past: Boolean, val days: Long, val hours: Int, val minutes: Int) {
    companion object { fun between(now: Instant, at: Instant): TimeLeft }
}
```

```kotlin
// ui/settings
data class SettingsState(
    val check: ReminderCheck,
    val askNotifications: Boolean,          // the first launch's request, once (§4.3.10)
    val problem: ChangeProblem? = null,     // a confirmation that couldn't be written
)
class SettingsViewModel(deps: ScreenDependencies, firstLaunch: Boolean, saved: SavedStateHandle) : ViewModel() {
    val state: StateFlow<SettingsState?>                    // null until the check's record is read
    fun refresh()                                           // each time the screen is resumed
    fun setConfirmed(step: HyperOsStep, confirmed: Boolean)
    fun notificationsRequested()                            // right before the dialog opens: "asked", in `saved`
    fun notificationsAsked(granted: Boolean)                // the dialog's answer: reads the device, reconciles if granted
    fun problemShown()
}
```

Each ViewModel's factory is in its companion and takes the `ScreenDependencies` and the route's arguments, plus `createSavedStateHandle()` for Settings'. Each screen has an entry composable that takes its ViewModel and a stateless `…Content` composable that draws its state.

### 4.3 Behaviour

#### 4.3.1 States follow the clock, the data and the device

- Home's data part is `combine(repository.data, deps.minuteTicks, …)`, worked out again at each whole minute and at each change of the data, with `stateIn(viewModelScope, WhileSubscribed(…))` so the ticks stop while the screen isn't visible (P6-D3). Now is `ZonedDateTime.now(clock)`, read each time.
- A row moves from Next to Due at its minute, and a Due row goes at the next tick once its take is Missed; that medicine's next take then shows in Next. An answer given from a reminder shows at once, through `repository.data` (HM-5).
- Planned times are compared with now through P2's functions and `dueAt(now.zone)`, never as local times (CLAUDE.md).
- The check's part follows `checkStore.record` and a reading of the device, taken again each time the screen is resumed (§4.3.9).

#### 4.3.2 Home's sections

- **Due** (HM-2): the Due take of each medicine of the Active treatments, highlighted (a filled card, in a container colour of the theme, not the error colour), each with **Taken**. Ordered by planned time, oldest first; then by treatment name, then by the medicine's position in its treatment.
- **Next** (HM-1, HM-2): for every other medicine of the Active treatments, its next take, which is Pending: `nextTake(now)`. Ordered the same way, soonest first. A medicine with nothing left (all its takes answered or Missed, while another medicine keeps the treatment Active) has no row.
- **Upcoming** (HM-4): each Upcoming treatment, with when it starts, `firstTake(id)`: "Example", "Starts: Tomorrow 08:00 · in 21 h 30 min". Soonest first. Its medicines have no rows in Next: HM-1 lists only the Active treatments.
- Finished treatments aren't on Home.
- Each section has a heading ("Due", "Next", "Upcoming") and shows only when it isn't empty.
- **A row opens its treatment** (`TreatmentRoute(id)`, spec §6 "open a treatment"); its buttons do their own action. The row and its buttons are separate touch targets, each at least 48 dp.

#### 4.3.3 A row's content

As in the sketch of §2.1 (texts are suggestions):

| Line | Due row | Next row |
|---|---|---|
| 1 | "Amoxicillin · 1 capsule" (P6's `medicineAndDose`) | the same |
| 2 | "Today 16:00 · 5 min ago" | "Tomorrow 00:00 · in 7 h 55 min" |
| 3 | "Example · 1 of 21" | "Example · 2 of 15" |
| Button | **Taken** | **Taken early**, when P2 allows it (§4.3.4) |

- **The planned time** through `DateTimeText.dateTime(plannedAt, today)`: "Today 16:00", "Mon 30 Nov 00:00".
- **The time left** (`TimeLeft.between(now, planned instant)`): the planned instant comes from `dueAt(now.zone)`, so across a daylight saving change it is the real time left.
  - Ahead, rounded **up** to whole minutes, so a take a few seconds away reads "in 1 min", never "in 0 min": "in 25 min", "in 5 h 30 min", "in 5 h" (no "0 min"); from 24 hours, days and hours, minutes dropped: "in 1 day 7 h", "in 6 days".
  - Past (a Due take), rounded **down**: under a minute "now", then "5 min ago", "1 h 10 min ago", "2 days 3 h ago".
  - Units in `strings_home.xml`, the days a plural, so a translation brings its own.
- **The progress** (HM-3): "Example · 1 of 21", `answeredCount` of `count`; with no end (`SC-5`): "Example · 5 answered · No end". Counts through `DateTimeText.number()`.
- The instructions aren't on Home, to keep rows short; the Treatment and Take screens show them.

#### 4.3.4 Taken and Taken early (TK-2, TK-4)

- **Taken** on a Due row: `repository.markTaken(key, clock)` inside `attempt { }`. P2 records now, in whole minutes; P5 removes the reminder (P5-D2). No confirmation: it is Home's main action (HM-2).
- **Taken early** on a Next row (Q5): shown only when P2's pure `data.markTaken(key, now)` would return non-`null` (P6-D5), which is `canMarkEarly`: the medicine's next take, Pending, with nothing already answered ahead of its time. An outlined button, so the Due rows' filled Taken stays the one that stands out. It asks first, in P6's `ConfirmDialog`: "Mark as taken now?", "Atrovent · 3 puffs, planned for Today 16:00. Its reminder won't alert.", with **Taken early** and **Cancel**. Then the same `markTaken`. The dialog's take is kept through a rebuilt screen (`rememberSaveable`).
- After it, the row shows the medicine's following take, without Taken early: TK-4 allows one take ahead at a time. Once the marked take's planned time has passed, the button comes back for the next one.
- **Refused** (the take changed meanwhile, for example answered from its reminder): "Not changed: this take changed in the meantime." The rows already show the new state. **A failed write:** "Not saved: the change couldn't be written. Try again." **Unreadable meanwhile:** as Unreadable. All through P6's `attempt { }` and `ShowProblem`, in a snackbar; any other exception isn't caught.
- A double tap marks once: the second `markTaken` is refused by P2, and that refusal isn't shown when the first one applied. The ViewModel keeps each take being marked: while the change runs, and, once applied, with the stored data it changed. A take kept is ignored by `markTaken` and its button disabled, until the rows show data other than that; then it follows P2 again, so a take answered and cleared elsewhere can be marked again. The store's flow may show a change only after `update` returns, which "until the data changes" alone wouldn't cover.

#### 4.3.5 Home's frame, empty state and unreadable data

- **Top bar:** the title "Next takes", and a settings icon button (`Icons.Filled.Settings`, content description "Settings"). It and the warning's **Check** both open `SettingsRoute()`.
- **Rows:** Due rows are cards in the theme's `primaryContainer`, with a filled Taken; Next and Upcoming rows are plain cards, Next with an outlined Taken early.
- **All treatments** and **New treatment** (spec §6): a bottom bar with the text button "All treatments", and the extended floating button "New treatment" (the content overload, P6's pitfall) above it, at its end. Two text buttons side by side would break at font scale 2. T2's drawings settle the layout with the user; whatever it becomes, each of the two stays a single button node with its text, `home_all_treatments` and `home_new_treatment`, which P6's flow tests tap.
- The list is a `LazyColumn` with bottom padding, so the floating button never covers its last row.
- **Empty** (HM-4): no treatment Active or Upcoming, Finished ones included: "No treatments active or upcoming." and, under it, "Create one with New treatment." The floating button is the offer, so the text has no second "New treatment" button that tests would find twice.
- **Unreadable** (plan §2.4): P6's `UnreadableMessage` in place of the sections, with no New treatment button (the editors refuse to save). All treatments and Settings stay: the check doesn't depend on the data.
- **Loading** (`data` is `null`): the top bar and the check's warning only.

#### 4.3.6 Home's warning (RM-9)

- While the check has something to check (`toCheck > 0`: a line MISSING, or a HyperOS step TO_CONFIRM, §4.3.7), a card at the top of the list, above Due, in the theme's `errorContainer`: "Reminders may not work: 2 settings to check." with the button **Check**, which opens Settings.
- Until the check's record is read, Home shows no warning and doesn't open the check: with an empty record, every HyperOS step would count for a moment.
- It goes as soon as everything is set or confirmed, the next time Home is resumed or the record changes.
- It shows also with unreadable data, and in the empty state.

#### 4.3.7 The check's items (RM-9)

In this order. Android's items always; HyperOS's steps only on a HyperOS phone (Q4).

| Item | OK when | MISSING says (suggestion) | Button |
|---|---|---|---|
| **Notifications** | allowed, and the Reminders channel `ON` (Q3) | not allowed: "Not allowed: no reminder can appear."; channel `OFF`: "The Reminders category is off: no reminder can appear."; `NO_POP_UP`: "Reminders don't pop up or sound: set the Reminders category to pop up." | **Open settings**: the app's notification settings, or the channel's |
| **Exact alarms** | `canScheduleExactAlarms()` | "Not allowed: reminders may come late." It is granted at install and nothing revokes it (`D-8`, T1 C5), so it should never show. | **Open settings**: the app's details (§4.3.8) |
| **Battery optimisation** | the app is exempt | "On: Android may delay reminders. Turning it off costs no battery: the app runs only to alert." On HyperOS it adds: "This also sets HyperOS's Battery saver to No restrictions." (Q2, Q7) | **Turn off**: Android's own request dialog (§4.3.8) |
| **Autostart** (HyperOS) | confirmed | "Turn Autostart on, so reminders come back right after an app update. Find Pills Reminder in the list." | **Open settings**: HyperOS's Autostart list (§4.3.8), and **Done** |
| **Floating notifications** (HyperOS) | confirmed | "Keep Floating notifications on, so reminders pop up." | **Open settings** and **Done** |
| **Lock screen notifications** (HyperOS) | confirmed | "Keep Lock screen notifications on, so reminders show on the lock screen." | **Open settings** and **Done** |

- **Android's items** are OK or MISSING, from `DeviceChecks`. An OK line says so ("Allowed.", "Off for this app.") and keeps no button, apart from Notifications, whose settings stay useful.
- **HyperOS's steps** are TO_CONFIRM until the user marks them done, then CONFIRMED (Q4). **Done** is a checkbox row ("Done", 48 dp tall); unticking it makes the step TO_CONFIRM again. The record keeps the confirmed steps (Q1).
- **HyperOS's battery saver** isn't a step of its own: its "No restrictions" turns battery optimisation off, which the app reads, and Android's dialog sets it (§2.2, T1 C2). Going back to HyperOS's default afterwards leaves the app exempt, which the line then shows as OK: harmless, since reminders need neither (P5).
- **The category's own "Floating notifications"** in HyperOS is the channel's importance (T1 C3), which the Notifications line reads (`NO_POP_UP`). The app's "Floating notifications" and "Lock screen notifications" can't be read (T1 C3), so they stay steps.
- **Above the lines:** "Reminders depend on these settings." and a summary: "Everything reminders need is set." or "2 settings to check."; under a heading "HyperOS", one line: "The app can't read these. Check each one, then mark it done."
- **Each line** is a card: its status icon (a check for OK and CONFIRMED, a warning for MISSING, an info icon for TO_CONFIRM, each with a content description), its name, its text (in the error colour when MISSING), then its buttons. The Notifications line's text says which problem it has (`NotificationsProblem`).

#### 4.3.8 Opening the settings screens

- `ReminderCheckDevice.open(activity, item)` starts an explicit intent from the screen's activity, from the user's tap only. Each intent carries the app's package (`package:` URI or `EXTRA_APP_PACKAGE`).
- **The intents,** as T1 measured them on the phone and the emulator (§4.6). "The app's details" is `ACTION_APPLICATION_DETAILS_SETTINGS`, which HyperOS redirects to its own App info.

  | Item | First choice | If it fails |
  |---|---|---|
  | Notifications | `Settings.ACTION_APP_NOTIFICATION_SETTINGS`; for a channel `OFF` or `NO_POP_UP`, `ACTION_CHANNEL_NOTIFICATION_SETTINGS` with the channel's ID | the app's details |
  | Exact alarms | the app's details. Not `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`: its "Alarms & reminders" page shows a disabled switch, off, for an app that holds `USE_EXACT_ALARM` (C1) | — |
  | Battery optimisation | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `setPackage("com.android.settings")`: Android's own dialog on both devices. Without the package, HyperOS offers a chooser of its own battery saver pages (C1) | the same intent without the package |
  | Autostart | HyperOS's Autostart list, `com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity` ("Autoinicio en segundo plano"), where the app is one row: HyperOS's App info has no Autostart line, and no per-app Autostart screen can be opened (C1) | the app's details |
  | Floating, Lock screen notifications | `ACTION_APP_NOTIFICATION_SETTINGS`, where HyperOS shows both switches (C1) | the app's details |

- **Falling back:** the code starts the first choice and catches `ActivityNotFoundException` and `SecurityException`, then starts the fallback. It doesn't call `resolveActivity()` first: T1 found it worked for these screens without `<queries>`, but package visibility doesn't promise it.
- **The order to try** is the pure `settingsScreens(item, device)`, which `AndroidReminderCheckDevice.open` turns into intents. Notifications opens the channel's screen only when the permission is granted and the channel isn't `ON`; otherwise the app's notification settings.
- A line's button counts only while the screen is resumed (`dropUnlessResumedWith`), as a click that navigates does.
- Lint's `BatteryLife` warning on `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is suppressed on that line, with a comment: plan §3 allows the permission for this check (`RM-9`).

#### 4.3.9 Reading the check again

- Home and Settings read the device when they are resumed (`LifecycleResumeEffect` calling `refreshCheck()` or `refresh()`), so the check follows a change made in Android's settings, or in Android's dialog, as soon as the user comes back.
- A revoked runtime permission ends the app's process (Android does that); the app then starts again where it was, and reads the device anew.
- The record's flow updates both screens when a step is confirmed.
- A confirmation that can't be written shows P6's "Not saved" message; the box goes back.

#### 4.3.10 First launch (Q6)

- **What counts:** the record's `firstLaunchDone` is `false`: a new install, cleared storage, or the first start of a build with P7 on a phone that had an earlier one.
- **Home opens the check once:** when its state says `openCheck`, it navigates to `SettingsRoute(firstLaunch = true)` above itself, through P6's `LeaveWhen` (navigation driven by a state, once resumed), and calls `checkOpened()`. That marks `firstLaunchDone` in the store, and at once in the ViewModel, so coming back to Home before the write ends doesn't open it again. Back from the check returns to Home.
- If a tapped reminder opens a take first (P5's `OpenTake`, above Home), Home isn't resumed, and the check opens the first time Home is.
- **The notification permission:** the check, opened with `firstLaunch`, asks for it with Android's dialog (`ActivityResultContracts.RequestPermission`) right away, once, when it isn't granted. `askNotifications` comes from the ViewModel, which keeps "asked" in its `SavedStateHandle`, so a rebuilt screen doesn't ask again. The screen launches the dialog through `LeaveWhen(askNotifications)`, once resumed, calling `notificationsRequested()` right before: a screen rebuilt while the dialog is up doesn't open a second one, and the answer still reaches `notificationsAsked(granted)`. Afterwards, the Notifications line's button opens the app's notification settings (`RM-9`): the dialog isn't offered again, since Android stops showing it after two refusals.
- **After the answer,** the ViewModel reads the device again; after a grant it also calls `deps.reconcile()` (P7-D6), so the reminders the plan holds are posted at once.
- The first launch's check is the same screen as Settings, under the same title; the line "Reminders depend on these settings." says why it opened.

#### 4.3.11 Layout

- **The phone's content area is 392.7 × 796.4 dp** (P1, measured; the `phone` skill's "Devices"). Both screens fit it, at font scale 1 and 2, in light and dark theme. The emulator's is 28 dp taller, so a layout that fits there can still overflow on the phone.
- Screens use `Scaffold`, which keeps the content out of the system bars (P1-D4). Home is a `LazyColumn`; Settings a scrolling `Column` or a `LazyColumn`.
- **Touch targets** are at least 48 dp: rows, buttons, icon buttons and Done's checkbox rows. Material 3's buttons are drawn 40 dp tall and take touches over 48 dp, which meets `NF-7`.
- **From font scale 1.3,** a row's button goes under its texts, full width (`STACKED_FONT_SCALE`); below that it sits beside them. T2's drawings settled font scale 2; at the phone's "big" font, 1.4, a button beside the texts left them so narrow that "Amoxicillin · 1 capsule" broke between the number and its unit (T7, M5, Q9). Each line of the check has its texts first and its buttons under them, one under the other.
- **Non-breaking spaces** keep the time left ("in 7 h 55 min") and "No end" on one line (`strings_home.xml`): on the emulator's 12-hour clock, "in 7 h 55 / min" broke in T2.
- **The check's colours:** a MISSING line shows an icon (`Icons.Filled.Warning`) and the error colour for its status word; an OK or CONFIRMED line an icon (`Icons.Filled.CheckCircle`, or `Check`), so the status isn't told by colour alone. Icons only from `material-icons-core`, each with a content description or marked as decoration.

#### 4.3.12 Example

On Mon 23 Nov 2026, Europe/Madrid, with the §4.4 example:

1. **10:30,** both 08:00 takes Taken: no Due section. Next: "Amoxicillin · 1 capsule", "Today 16:00 · in 5 h 30 min", "Example · 1 of 21", with Taken early; Atrovent the same, "Example · 1 of 15". Ties are by the medicine's position: Amoxicillin first.
2. **16:00:** at that tick both rows move to Due, "Today 16:00 · now", each with Taken.
3. **16:01,** Taken on Atrovent: Due keeps Amoxicillin ("1 min ago"); Next: "Atrovent · 3 puffs", "Tomorrow 00:00 · in 7 h 59 min", "Example · 2 of 15", with Taken early.
4. **16:05:** the sketch of §2.1: Amoxicillin "Today 16:00 · 5 min ago", "Example · 1 of 21"; Atrovent "in 7 h 55 min".
5. **16:10,** Taken early on Atrovent, confirmed: Atrovent's row shows "Tomorrow 08:00 · in 15 h 50 min", "Example · 3 of 15", without Taken early. Its 00:00 reminder never alerts.
6. **Tue 24 Nov 00:00:** Amoxicillin's 16:00 take, still unanswered, is Missed, and its 00:00 take Due. Atrovent's 00:00 take is Taken (early), so Atrovent still shows its 08:00 take, now with Taken early again.

On Sun 22 Nov at 10:30, the same treatment saved and not yet started: Upcoming: "Example", "Starts: Tomorrow 08:00 · in 21 h 30 min"; no Due or Next section.

### 4.4 Dependencies

- **No new library.** Material 3, Navigation Compose, Lifecycle (`viewModel`, `SavedStateHandle`, `LifecycleResumeEffect` from `lifecycle-runtime-compose`), Activity Compose (`rememberLauncherForActivityResult`), DataStore (`androidx.datastore:datastore`, P3's) and kotlinx.serialization JSON are already in the build. If `LifecycleResumeEffect` turns out to need a library that isn't declared, use a `DisposableEffect` with a `LifecycleEventObserver` instead. Anything else needs the user's agreement first (plan §3).
- **From P2 (`:core`):** `PillsData.takesOf`, `status`, `firstTake`; `MedicineTakes.nextTake`, `answeredCount`, `count`; the pure `markTaken` to find when Taken early applies, and `repository.markTaken(key, clock)` to apply it; `dueAt`; `TakeKey`, `TakeState`, `TreatmentStatus`.
- **From P3:** `StoredData.Unreadable`, and the `IOException` a failed write throws. Its pattern for a DataStore with a JSON serializer, for the check's store.
- **From P5:** the reconcile on every change (nothing to call for Taken); `Reminders.runAsync` and `ReconcileTrigger.APP_OPEN` for `reconcile` after the permission is granted; `ReminderChannel.ID`, the channel the check reads.
- **From P6:** `ScreenDependencies`, `minuteTicks`, `DateTimeText`, `medicineAndDose`, `ChangeProblem` with `attempt { }`, `ShowProblem`, `UnreadableMessage`, `ConfirmDialog`, `LeaveWhen`, `dropUnlessResumedWith`; `TreatmentRoute`, `TreatmentsRoute`, `TreatmentEditorRoute`; the tests' `vmTest`, `TestTicks`, `FakeApp`, `showApp`, `dateTimeText()`.
- **Fakes in tests:** `InMemoryTreatmentRepository` (`:core`), started `Unreadable` for that case; a repository whose `update` throws an `IOException`; a fake `ReminderCheckDevice` whose readings the test sets and which records what it opened; `InMemoryCheckStore`; a `reconcile` that counts its calls; a clock the test sets; `TestTicks`.

### 4.5 Decisions made in this phase

| ID | Decision | Reason |
|---|---|---|
| P7-D1 | The check's own record (first launch done, confirmed steps) is kept in a second DataStore file, `files/datastore/reminder-check.json`, as JSON. A missing or damaged file reads as an empty record, and the next write replaces it. (Q1) | It isn't treatment data: it must work while the treatments' file can't be read, and losing it only shows the check again. Keeping it apart leaves P3's format, its golden files and its refusal to overwrite untouched. DataStore and kotlinx.serialization are already in the build, and DataStore allows one store per file, so a second file is fine. |
| P7-D2 | What the check reads from the device, and the screens it opens, go through `ReminderCheckDevice` in `ScreenDependencies`; the screens read it again when resumed. | UI tests then run the real navigation graph with fakes (P6-D2), and never open Android's settings or change the device. Android reports no change of these settings to the app, but every change is made in another screen, so the app is resumed afterwards. |
| P7-D3 | HyperOS's steps show only on a HyperOS phone, are confirmed by the user, and count for Home's warning until confirmed. (Q4) | The emulator, and stock Android, have no such settings. The app can't read them (spec §3), so a confirmation is the only way the warning can go. |
| P7-D4 | Home offers Taken for each Due take, and Taken early for a next take whenever P2's pure `markTaken` applies (P6-D5), after a confirmation. (Q5) | The rules stay in `:core`. Marking early is the unusual case, and a slip would cancel a reminder: the confirmation says so. |
| P7-D5 | The first launch opens the check once, above Home, and the check asks for the notification permission with Android's dialog. Afterwards the Notifications line opens Android's settings. (Q6) | `RM-9` and plan §5.2: the permission is asked at first launch, and each line has a button to its settings screen. Android stops showing the dialog after two refusals, so settings always work. |
| P7-D6 | After Android's permission dialog grants notifications, the check asks the reminders to reconcile (`ScreenDependencies.reconcile`). | P5 counts on `MainActivity.onStart`'s reconcile after the permission is granted, but the dialog only pauses the activity, on the phone and the emulator (T1, C4), so `onStart` doesn't run. Reconciling twice changes nothing. |

### 4.6 Measured first

T1 measures on the devices what the check relies on, before the design does. It uses `adb` where the shell can do it (`am start`, `dumpsys`, `appops`, `pm`), and a throwaway probe where only the app can (`phone` skill, "Measuring device behaviour"): a debug-only screen with one button per intent, which logs what each start did, and the readings of `DeviceChecks`. The probe is never committed. Each result records the device, its Android and HyperOS versions, and the settings before and after. Ask the user before changing any setting on the phone, and put it back.

Measured on 2026-11-22: the phone (POCO F3, Android 13, HyperOS `OS1.0.6.0.TKHEUXM`) with the debug app's settings as the user keeps them (Autostart on, the battery saver at its default), and the emulator (API 36) with stock settings. The handover's "Measurements (T1)" has every reading. Every setting changed was put back.

| # | What | Device | Result |
|---|---|---|---|
| C1 | Which screen each intent of §4.3.8 opens, started by the app from a tap; whether Back returns to the app; which fail, and with what | Phone, emulator | Android's intents open their screens on both, and Back returns to the app from each. On the phone, the app's notification page has the **Floating** and **Lock screen notifications** switches. Three differences from the draft: **the battery intent opens a chooser** of HyperOS's battery saver pages, and Android's dialog only with `setPackage("com.android.settings")`; **"Alarms & reminders" shows a disabled switch, off,** for an app with `USE_EXACT_ALARM`, on both devices; **HyperOS's App info has no Autostart line**, and only HyperOS's Autostart list opens (`AutoStartManagementActivity`). HyperOS's components don't exist on the emulator (`ActivityNotFoundException`). |
| C2 | Android's battery dialog: does HyperOS show it; does Allow make the app exempt, and set HyperOS's battery saver to "No restrictions"; and back again from HyperOS's setting (P5 saw one way) | Phone, emulator | The dialog (with the package) shows on both; Allow makes the app exempt, and HyperOS's page then shows "Sin restricciones". **Not back again:** HyperOS's default, chosen afterwards, left the app exempt. Linked one way only (Q7). |
| C3 | Whether HyperOS's "Floating notifications" and "Lock screen notifications" switches change anything the app can read: `areNotificationsEnabled`, the channel's importance or lock-screen visibility, in `dumpsys notification` before and after | Phone | The app's two switches: **nothing readable changed**; they stay steps to confirm. The Reminders category's own Floating notifications switch is the channel's importance (4 ↔ 3), which the Notifications line reads. |
| C4 | Android's notification permission dialog: shown on the phone as on the emulator; whether the activity gets `onStart` after it, or only `onResume`; what a dismissal and a second refusal do | Phone, emulator | Shown on both; **only `onPause` and `onResume`** around it (P7-D6 needed). After two refusals, a request returns at once with no dialog; Back on the dialog is a refusal for the result. |
| C5 | What can be revoked and granted again on the emulator: `pm revoke` and `pm grant` of `POST_NOTIFICATIONS` (ending the process); the channel turned off and on; `dumpsys deviceidle whitelist -` and `+` the app; exact alarms, with `appops` or anything else; and what `DeviceChecks` reads after each | Emulator | Each as expected: the permission (the process ends at the revoke), the channel off (importance 0) and without pop-ups (3), battery optimisation both ways. **Exact alarms can't be revoked:** `appops` `deny` and `ignore` leave `canScheduleExactAlarms()` true. |
| C6 | `Build.MANUFACTURER` and `Build.BRAND` on both devices; a HyperOS property such as `ro.mi.os.version.name` | Phone, emulator | Phone: `Xiaomi`, `POCO`, `OS1.0`. Emulator: `Google`, `google`, none. `Build.MANUFACTURER` equal to "Xiaomi", ignoring case, marks HyperOS. |

## 5. Security and privacy

Plan §3 applies. For this phase:

- **No new permission, library or exported component.** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is already declared for this check (P5). No `<queries>`. The merged manifests stay as they are.
- **Treatment content is shown only on screen:** medicine names, doses and treatment names on Home. P7's code logs nothing. An exception is never shown or logged with its message (P6's `attempt { }`).
- **The check's record** holds two things: whether the first launch is done, and which HyperOS steps are confirmed. No treatment content, no device identifier. It lives in the app's private storage, which Auto Backup and the data extraction rules exclude (`D-12`).
- **Intents** are explicit, carry only the app's package name, and are started only from the user's tap, or for the permission dialog at the first launch with the app on screen.
- **Routes carry IDs and a flag only** (`TreatmentRoute`, `SettingsRoute(firstLaunch)`).
- **Tests never touch the real data** apart from those through `MainActivity` (§6), which set the first launch done for their run and put the record back as they found it, and change no treatment.
- **Manual checks on the phone** use test treatments from the debug tools (`debug-…`), deleted at the end. Ask the user before creating any, since their reminders alert, and before changing any setting (notifications, Autostart, battery, dark theme, font size); put each back.
- **Screenshots** show only the §4.4 example or debug test data, stay in the scratchpad, and are never committed (CLAUDE.md, "The repo is public"). Screenshots of the phone's own settings screens (T1, M1) stay out of the repo too.

## 6. Testing

ViewModel and logic tests use fakes, on the JVM. UI tests run `AppNavHost` with `FakeApp`'s fakes (P6-D2). Clocks are fixed in Europe/Madrid. UI tests build expected texts from the resources and `dateTimeText()`, so they pass with the emulator's 12-hour clock and the phone's 24-hour one.

| Test | Type | Covers |
|---|---|---|
| `TimeLeftTest`: ahead, rounded up ("in 1 min" for 20 s); minutes, hours and minutes, whole hours; days and hours from 24 h; past, rounded down, "now" under a minute; across the hour the clocks go back and forward (Europe/Madrid) | unit | `HM-1`, §4.3.3 |
| `ReminderCheckTest`: each item OK and MISSING; the channel's three states; HyperOS steps only with `hyperOs`, TO_CONFIRM then CONFIRMED; `toCheck` counts MISSING and TO_CONFIRM; the order of §4.3.7 | unit | `RM-9`, Q2–Q4 |
| `DataStoreCheckStoreTest`: an empty record with no file; a round trip; a damaged file reads as empty and the next write replaces it; a restart keeps the record | unit | P7-D1 |
| `HomeViewModelTest`, rows: the §4.3.12 example step by step (10:30, 16:00, 16:01, 16:05, 16:10, Tue 00:00), each by a tick or a change; the order of Due and Next, with ties by treatment and medicine; Upcoming and its start; a medicine with no end ("No end"); a medicine with nothing left; Finished treatments not shown; the empty state; unreadable; no state before the data | unit | `HM-1`–`HM-5`, `SC-5`, `TK-1`, `TK-3` |
| `HomeViewModelTest`, actions: Taken records now in whole minutes; Taken early offered exactly when P2's `markTaken` applies (a next take, not one after a take marked early, and again once that take's time has passed), and applied; refused, failed write and unreadable each give their problem; a double tap marks once | unit | `TK-2`, `TK-4`, P6-D5 |
| `HomeViewModelTest`, the check: `toCheck` from a fake device and record, again after `refreshCheck()`; `openCheck` with the first launch not done, once only, and false after `checkOpened()` even before the store has written | unit | `RM-9`, §4.3.6, §4.3.10 |
| `SettingsViewModelTest`: the lines from the fake device and record; `refresh()` reads the device again; `setConfirmed` both ways, and a failed write's problem; `askNotifications` only at the first launch with notifications not allowed, once, kept through a `SavedStateHandle`; `notificationsAsked(true)` calls `reconcile`, `false` doesn't | unit | `RM-9`, P7-D5, P7-D6 |
| `HomeScreenTest`: the order of Due, Next and Upcoming on the §4.4 example; a due take marked Taken moves to Next; Taken early with its confirmation, Cancel then confirm; the empty state and its New treatment; unreadable; a row opens its treatment; the warning opens Settings | UI | plan's done criteria, `HM-1`–`HM-4`, `TK-4` |
| `ReminderCheckScreenTest`: the lines from the fake device; a change of the fake device shows after the activity is paused and resumed (`activityRule.scenario.moveToState`), on Settings and then on Home (`refresh` on resume); each button calls `open` with its item; Done confirms a step and Home's warning goes; the first launch opens the check from Home, once, and Back returns to Home | UI | `RM-9`, §4.3.9, §4.3.10 |
| Rows and checkbox rows are at least 48 dp tall (`assertHeightIsAtLeast`); buttons take touches over at least 48 dp (`assertTouchHeightIsAtLeast`, from `touchBoundsInRoot`, since they are drawn 40 dp tall) | UI | `NF-7` |
| `NavigationSmokeTest` (trimmed): starts on Home, portrait; Settings opens from Home and Back returns; through `MainActivity` with the first launch done | UI | `NF-7` |
| `OpenTakeTest` (P5's, kept): passes with the real Home, opening Settings by its icon | UI | `RM-6` |
| P6's flow tests: pass unchanged with the real Home | UI | — |
| Galleries (throwaway, T2, T7): every state of both screens, light and dark, font scale 1 and 2, on the phone's content area | drawn | `NF-7`, `NF-10` |

New tests that pass at their first run are shown able to fail, with the `deliberate-bugs` skill (CLAUDE.md).

**Tests through `MainActivity`** (`NavigationSmokeTest`, `OpenTakeTest`) would otherwise open the check on a device whose record is empty, and Android's permission dialog with it. The rule `FirstLaunchDone`, an `ExternalResource` at `@get:Rule(order = 0)`, outside the activity's rule, reads the real record through the container, sets `firstLaunchDone` for the run, and writes back what it found afterwards (P3: leave the data as it was found). With no record before, it writes back the empty one, which means the same as no file.

**Android's permission dialog isn't in a UI test:** a fake device that says notifications aren't allowed would show the real dialog. `SettingsViewModelTest` covers when it is asked; M2 and E2 see it on the devices.

### Manual checks

On **the phone** unless marked. Ask the user before each that creates treatments or changes a setting, and put everything back.

| # | Check | Expected |
|---|---|---|
| M1 | In Settings, tap each line's button, the HyperOS steps' too; change nothing, come back with Back. Then turn the battery optimisation off with Android's dialog and see the line change; put it back. | Each opens the screen T1 settled, where its setting is; Back returns to the check (plan's done criterion). The battery line follows the dialog (`RM-9`). |
| M2 | First launch: remove the check's file only (`run-as … rm files/datastore/reminder-check.json`; the treatments stay), with the notification permission revoked first (the user's agreement); open the app. Allow in the dialog. | The check opens above Home, and Android's dialog shows at once; after Allow, the Notifications line is OK; Back goes to Home, whose warning lists what is left (HyperOS steps unconfirmed). Opening the app again doesn't open the check. |
| M3 | With a debug test treatment in minutes (two medicines, a take due at once, the next a few minutes later): watch Home while open; tap Taken on the due row; Taken early on the other medicine's next take; answer a later reminder from the notification with Home open. Delete the treatment. | The due row is highlighted, and moves to Next after Taken; its reminder goes (`RM-7`); the take marked early never alerts (`alarms` skips it, `TK-4`); the time left changes each minute and a Next row turns Due at its minute without leaving (`HM-5`); the notification's answer shows on Home at once. |
| M4 | Confirm each HyperOS step, then untick one. | Home's warning goes once all are confirmed, and comes back for the unticked one. |
| M5 | Home and Settings in the phone's dark theme, and with its largest font size. | Both fit and stay usable; no word broken in a button (`NF-7`, `NF-10`). |
| E1 | **Emulator** (API 36): revoke and grant again each item T1 found revocable: `POST_NOTIFICATIONS`, the Reminders channel off and on in Android's settings, battery optimisation (`dumpsys deviceidle whitelist` and Android's dialog); exact alarms as T1 found. Look at the check and Home after each. | The check follows each change when the app comes back to the front; Home's warning appears and goes with it (plan's done criterion). No HyperOS step shows. |
| E2 | **Emulator:** as M2, with a test treatment whose take is due: remove the check's file, revoke the notification permission, open the app, Allow. | The dialog shows at first launch; after Allow, the due take's reminder appears at once (P7-D6). |

## 7. Tasks

| ID | Task |
|---|---|
| T1 | **Measure first** (§4.6): C1–C6 on the phone and the emulator, with `adb` and a throwaway probe, with the user for the phone's settings. Record the results in the handover; settle each intent of §4.3.8 and HyperOS's steps. A result that contradicts the draft changes this SPEC first, with the user. Delete the probe. |
| T2 | **Layouts of Home and Settings:** their state types and stateless `…Content` composables, `TimeLeft`, their strings, drawn from fixed states. Draw every state (`screen-gallery` skill): Home loading, empty, upcoming only, the §4.3.12 steps, a long list, no end, days ahead, the warning, unreadable, the Taken early dialog, a problem's snackbar; Settings all OK, each line missing, HyperOS's steps to confirm and confirmed, a stock Android phone. Light and dark, font scale 1 and 2, on the emulator and the phone. Show the user the drawings before T4. |
| T3 | **The check:** `DeviceChecks`, `reminderCheck()`, `CheckRecord` and the stores, `AndroidReminderCheckDevice` with T1's intents and fallbacks; `ScreenDependencies`' entries, `AppContainer.checkStore`, and the fakes in the test helpers. Unit tests (`ReminderCheckTest`, `DataStoreCheckStoreTest`, `TimeLeftTest`). Shown able to fail. |
| T4 | **Home:** `HomeViewModel`, its factory and destination in `AppNavHost`; rows, Taken, Taken early with its confirmation, the warning, the first launch's `openCheck`. Unit tests. Shown able to fail. |
| T5 | **Settings:** `SettingsViewModel`, its destination with `SettingsRoute(firstLaunch)`; the lines, the buttons, Done, reading again on resume, the permission dialog at the first launch and the reconcile after a grant. Unit tests. Shown able to fail. |
| T6 | **Flows:** `HomeScreenTest` and `ReminderCheckScreenTest` with fakes; `FakeApp`'s new fakes; `NavigationSmokeTest` and `OpenTakeTest` with the first-launch rule; `PlaceholderScreen` deleted. Every instrumented test passes on the emulator and the phone. Shown able to fail. |
| T7 | **Checks:** draw every state again from the finished code, light and dark, font scale 1 and 2, on both devices; M1–M5 on the phone and E1–E2 on the emulator, with the user. Record the results in the handover. |
| T8 | **Wrap-up:** every row of §6 and every rule of §4.3 against the tests; deviations folded into this SPEC (`phase-docs`); `CLAUDE.md` (Home, the check and its store, the first-launch rule for tests through `MainActivity`); the merged manifests compared with `main`; `build`; the pull request with CI green. |

## 8. Done criteria

- [ ] All tasks done
- [ ] `./gradlew build` passes locally and in CI, with the unit test totals recorded in the handover
- [ ] Unit tests cover every row of §6, and every new test was shown able to fail (CLAUDE.md)
- [ ] Compose UI tests cover the home screen's order, a due take, a take marked early and the empty state (plan §5.2), and every instrumented test passes on the emulator and the phone
- [ ] On the emulator (API 36), the reminder check follows each permission being revoked and granted again (E1); on the phone, each of the check's buttons opens the right screen (M1)
- [ ] Every state of both screens has been drawn and checked, in light and dark theme and at font scale 2, on the phone's content area
- [ ] M1–M5 pass on the phone, and E1–E2 on the emulator
- [ ] No new permission, library or exported component; the merged manifests are unchanged
- [ ] HANDOVER.md is up to date, with the pending wiring for P8, and the plan's status table is updated

## 9. Open questions

None at the moment. New questions are numbered from Q11.

### Answered

The review confirmed every draft assumption. Q1 went into plan v0.8 (`D-4`, §2.4), and Q2–Q6 into specification v0.10 (`RM-9`, §6), which §2 quotes.

| # | Question | Decision |
|---|---|---|
| Q1 | Where the check keeps what it must remember: that the first launch is done, and which HyperOS steps the user has confirmed. Plan `D-4` said all data is one JSON document; these aren't treatment data. | A second DataStore file, `reminder-check.json`, with the libraries already in the build (P7-D1). A missing or damaged one reads as empty and is replaced: the check then shows again. Not a field of the treatments' document (a format change, blocked while that file can't be read), nor `SharedPreferences`. In plan v0.8 (`D-4`, §2.4). |
| Q2 | Whether battery optimisation counts as missing, so Home warns until it is off, though P5 found reminders on time with it on | It counts, as `RM-9` says: one tap in Android's dialog turns it off, which costs no battery since the app runs only to alert (`NF-5`). On HyperOS, the battery saver's "No restrictions" turns it off too (P5), so there is no separate battery saver step; T1 found the link one way only (C2, Q7). In specification v0.10 and v0.11 (`RM-9`). |
| Q3 | Whether the check also reads the app's Reminders channel, which can be turned off or lowered with the permission granted | Yes, as part of the Notifications line: the channel off, or below high importance, is missing, with a button to the channel's settings (§4.3.7). In specification v0.10 (`RM-9`). |
| Q4 | Which HyperOS steps the check lists, on which phones, and whether Home warns until they are confirmed | Autostart (needed after an update, P5), Floating notifications and Lock screen notifications (on by default, P5), on a Xiaomi phone only (T1, C6). Each is to confirm until the user ticks Done, and Home warns until all are; unticking brings the warning back (P7-D3). In specification v0.10 (`RM-9`). |
| Q5 | How Home marks a next take early (`TK-4`), with `HM-2`'s Taken button for due takes only | An outlined "Taken early" button on each Next row that P2 allows, with a confirmation ("Its reminder won't alert", P7-D4). The Take screen, reached through the treatment, also offers it (P6 Q7). In specification v0.10 (§6). |
| Q6 | What the first launch shows, and how the notification permission is asked | The check opens once, above Home, and asks with Android's dialog at once; Back returns to Home. Afterwards, the Notifications line's button opens Android's settings, rather than the dialog, which Android stops showing after two refusals (P7-D5). A phone updated from an earlier build sees the check once too. In specification v0.10 (`RM-9`). |
| Q7 | Found in T1 (§4.6): on HyperOS the battery intent opens a chooser of HyperOS's battery saver pages, not Android's dialog; "Alarms & reminders" shows a disabled switch, off, for an app with `USE_EXACT_ALARM`; HyperOS's App info has no Autostart line, and no per-app Autostart screen opens; HyperOS's battery saver and Android's battery optimisation are linked one way only (C2) | Decided by the user on 2026-11-22. The battery button names `com.android.settings`, which shows Android's dialog on both devices; the exact-alarm line's button opens the app's details; Autostart's opens HyperOS's Autostart list, and its line says to find the app there (§4.3.7, §4.3.8). `RM-9` now says HyperOS's battery saver at "No restrictions" also turns battery optimisation off, rather than being it: specification v0.11, plan v0.9. |
| Q8 | Found in T2–T6: details the draft left open, recorded in the handover as they were built | Kept as built, and folded in v0.4: the Notifications line's `problem`, `ReminderCheck.hyperOs` and `CheckItem.step` (§4.2); `settingsScreens()` and the record's file (§4.2, §4.3.8); the Taken early dialog's wording (§4.3.4); a double tap ignored until the rows show the change (§4.3.4); no warning and no first launch until the record is read (§4.3.6); `notificationsRequested()` and Settings' `null` state (§4.2, §4.3.10); the cards' colours and the non-breaking spaces (§4.3.5, §4.3.11); buttons checked by their touch height, and the `FirstLaunchDone` rule (§6). |
| Q9 | Found in T7 (M5): at the phone's "big" font, 1.4, a row's texts beside its button broke "Amoxicillin · 1 capsule" between the number and its unit, and the progress took three lines; the button stacked only from 1.5 | Decided by the user on 2026-11-22: the button goes under the texts from font scale 1.3. Drawn again on the phone at 1.4: each line fits (§4.3.11). |
| Q10 | Found in T7 (M3): a reminder that alerted played no sound. After an alert, the reconcile that the stored record starts reads the shade before Android has registered the post, finds the reminder missing, and posts it again silently, which replaces the alerting post (P5's `Reconciler`) | Decided by the user on 2026-11-22: not fixed in P7, whose packages don't include P5's; fixed in its own branch from `main` after P7, with a unit test that reproduces the race and a check on the phone, before P8 (§3). |

## 10. Change log

| Version | Date | Changes |
|---|---|---|
| 0.1 | 2026-11-22 | First draft, from plan v0.7 and specification v0.9 |
| 0.2 | 2026-11-22 | Review: Q1–Q6 answered, confirming every draft assumption. Now quotes specification v0.10, which took Q2–Q6 into `RM-9` and §6, and plan v0.8, which took Q1 into `D-4` and §2.4. Ready. |
| 0.3 | 2026-11-22 | T1's measurements (§4.6, Q7). §4.3.8: the intents as measured; the battery dialog through `com.android.settings`; the exact-alarm line to the app's details; Autostart to HyperOS's Autostart list. §4.3.7: the battery and Autostart lines' texts; the category's Floating notifications is the channel's importance. §2.2 and Q2: HyperOS's battery saver linked one way. P7-D6 confirmed. Now quotes specification v0.11 and plan v0.9. |
| 0.4 | 2026-11-22 | T8: what implementation settled (Q8–Q10). §4.2: `CheckItem.step`, `NotificationsProblem` and `CheckLine.problem`, `ReminderCheck.hyperOs`, `channelStateOf`, `isHyperOs`, `settingsScreens()`, the record's file, `notificationsRequested()`, the states before the record is read. §4.3: the dialog's wording and the double tap (§4.3.4); the cards and where Check leads (§4.3.5, §4.3.6); the line cards and the screens' order (§4.3.7, §4.3.8); the dialog's request and answer (§4.3.10); rows stack from font scale 1.3, non-breaking spaces, buttons' touch height (§4.3.11). §4.1 and §6: `FirstLaunchDone`, `FakeApp(unreadable)`, `assertTouchHeightIsAtLeast`. §3: the lost-sound fix, after P7. |
