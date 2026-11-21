# P5 — Reminders on Android: Specification

| | |
|---|---|
| **Status** | Ready, v0.4 |
| **Phase** | P5, see [Implementation plan §5](../../IMPLEMENTATION_PLAN.md#5-phases) |
| **Depends on** | P3, P4 |
| **Branch** | `phase/p5-reminders` |
| **Handover** | [HANDOVER.md](HANDOVER.md) |

This document holds everything needed to implement the phase. Working on it shouldn't require reading the full specification or plan.

## 1. Goal

Real reminders on the phone. P4's planner says what the shade should hold and when to wake next. P5 applies that plan on Android:

- a single alarm clock (`setAlarmClock()`, Q5);
- one notification per Due take, with Taken, Snooze and Skip;
- receivers that run the reconcile after the alarm, an action, a restart, an update or a change of time;
- P4's reminder state in P3's store;
- debug tools to schedule test reminders in minutes.

Before relying on the phone's behaviour, P5 measures it, in Doze, after a swipe from Recents and after a restart, with HyperOS's stock settings first. The measurements settle `D-8`, and give P7 the list of HyperOS settings its reminder check must name.

## 2. Requirements covered

The text of each requirement is quoted from [SPECIFICATION.md](../../SPECIFICATION.md) (version v0.8), so this document stands on its own.

| ID | Requirement (quoted) | What this phase does for it |
|---|---|---|
| RM-1 | "When a take is due, the phone alerts with a reminder that names the medicine, the dose and the treatment, for example "Amoxicillin: 1 capsule" and "Example". It also shows the medicine's instructions, if it has any." | The notification's title is "Amoxicillin: 1 capsule", and its text "Example · with food", or only "Example" without instructions (§4.3.4). The text comes from the data at each reconcile, so an edit updates it. |
| RM-2 | "A reminder is a heads-up notification with sound, without vibration. In vibrate mode it vibrates instead of sounding, as Android does with every notification. It alerts whether the app is open or closed, the screen is on or off, the phone is locked, or the phone has restarted since the treatment was saved." | A high-importance channel with the default sound and vibration off (`D-10`); in vibrate mode Android vibrates instead (Q7). The alarm clock and the receivers make it alert with the app closed, the screen off, and after a restart, once the phone is unlocked (`D-16`). Checked on the phone (§6). |
| RM-3 | "A reminder has three actions: **Taken**, **Snooze** and **Skip**. Taken and Skip record the answer (`TK-2`) and remove the reminder. Snooze removes it, and alerts again 10 minutes later. From then on it repeats as in `RM-5`, every 10 minutes, up to 3 times, and it can be snoozed again." | Three action buttons, each an explicit, immutable `PendingIntent` to a receiver that records the answer or the snooze, then runs the reconcile (§4.3.5). The snooze is stored in P4's reminder state (§4.3.7). |
| RM-4 | "Each take has its own reminder, so takes due at the same time are answered separately. Android groups them in the notification shade. Reminders that alert at the same moment may play a single sound." | One notification per take, identified by its take key, in one group with a summary (§4.3.4). Each is posted with sound; on the phone, Android played one sound for two posted together (T2, Q4). |
| RM-5 | "A reminder that isn't answered stays in the notification shade, and alerts again every 10 minutes, up to 3 times. After that it stays there silently, until it is answered or the take becomes Missed (`TK-3`). Each repeat comes 10 minutes after the alert before it, so a reminder whose first alert came late, for example after the phone restarted, still repeats 3 times. Swiping a reminder away doesn't answer or snooze its take: the reminder comes back with its next repeat, or, once its repeats are over, silently the next time the app updates its reminders, for example when the app is opened." | The reconcile posts a reminder with sound when the plan says it alerts, and leaves it in the shade otherwise. A tap doesn't dismiss it. A reminder swiped away is posted again at the next reconcile (Q2). The timing is P4's. |
| RM-6 | "Tapping a reminder opens the app on that take." | The notification opens `MainActivity` on the take's `TakeRoute`. Until P6 is merged, that is the Take placeholder (§4.3.8). |
| RM-7 | "When a take is answered in the app, or becomes Missed, its reminder is removed from the shade." | The reconcile runs after every change of the stored data, so an answer given anywhere in the app removes the reminder (P5-D2). The Missed moment is one of the plan's wake-ups. |
| RM-8 | "Reminders follow the phone's Do Not Disturb and silent mode settings: in either one, a reminder appears without sound." | The channel doesn't bypass Do Not Disturb, and the notifications have no category, since HyperOS lets the *reminder* category through Do Not Disturb (Q6). The sound plays on the notification stream, which follows silent mode. Checked on the phone (T2, §6). |
| RM-9 | "**Reminder check:** the app checks what its reminders depend on: the notification permission, the exact-alarm permission, and Android's battery optimisation for the app. It shows what is missing, each with a button that opens the right settings screen. HyperOS's own settings (Autostart, battery saver), which the app can't read, are listed as steps for you to confirm. The check is shown on first launch and in Settings, and the home screen warns while something is missing." | P5's part: measure which of HyperOS's settings reminders need, and which defaults get in the way (T1, T2), and leave P7 the list. P5 declares the permissions (§4.1). The check itself is P7's. |
| RM-11 | "The lock screen shows a reminder's full content." | Public visibility on each notification; an app can't set the channel's (Q8). Checked on the phone's lock screen (T2). |
| TR-6 | "A medicine can have optional instructions, such as "with food", which its reminder shows." | In the reminder's text (RM-1). |
| TR-9 | "Treatments can be created, edited and deleted in the app. Deleting asks for confirmation, and also deletes every take recorded for the treatment." | The reminder side: the reconcile that follows the delete removes the treatment's reminders. The delete also drops their reminder records in the same write (P5-D8). |
| TR-11 | "A treatment can be ended early, for example when the doctor stops it. Its takes not yet due are removed and their reminders cancelled, and it shows as Finished. A take that is due at that moment becomes Missed, and its reminder is removed. A take already marked Taken early (`TK-4`) keeps its answer." | The reminder side: the reconcile that follows the end removes the reminder of the take that was due, and no alarm is set for the treatment again. |
| TK-2 | "Marking a take Taken records the time it was marked. That time can be corrected afterwards, for example to "taken at 08:20"." | Taken from a reminder calls P2's `markTaken`, which records now, in whole minutes. Correcting the time is P6's. |
| NF-1 | "**On time:** a reminder alerts within 1 minute of its planned time, also when the phone is idle (Doze), the screen is off, the app was swiped away from Recents, or the phone has restarted, as long as the reminder check (`RM-9`) passes. This is checked on the phone, not only on the emulator." | An alarm clock at the plan's next wake-up (`D-8`): T1 found exact alarms held for up to 5 minutes with the screen off, and alarm clocks on time (Q5). Measured first (T1), then checked in the device runs (T8), on the phone. |
| NF-2 | "**Reminders survive events:** they are set again after the phone restarts, after an app update, after a change of time or time zone, and after Android ends the app's process. A *Force stop* from Android's settings cancels every alarm, and Android gives the app no chance to undo that until it is opened again; when it is, it sets them again at once." | Receivers for `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET` and `TIMEZONE_CHANGED` run the reconcile (`D-9`). Alarms survive the process ending. The app's start runs the reconcile, which sets the alarm again after a *Force stop*. |
| NF-5 | "**Battery:** nothing runs in the background between reminders. The app wakes only to alert." | One alarm, set for the next moment the plan changes. No service, no WorkManager, no periodic work. The observer of the data (P5-D2) only waits, and runs nothing until the data changes. |
| NF-6 | "**Compatibility:** runs on Android 13 (API 33) on the target phone, and keeps working if the phone is updated to a later Android version. The lowest version supported is set in the implementation plan." | P5's part (plan §5.3): on the emulator, at API 36, check that `USE_EXACT_ALARM` is granted and the alarm, the notifications and the receivers work. |

`NF-1` and `NF-2` are verified again on the release build in P8.

### 2.1 What the specification and the plan say, quoted

**The target device** (spec §3):

> - **Exact alarms.** A reminder at a precise time needs Android's exact alarms. On Android 13 that permission is granted at install, but the user can revoke it. From Android 14 it is denied by default, so the app must be able to ask for it if the phone is ever updated. The emulator, at API 36, covers that case. The app is never published on Google Play (`NF-9`), so Google Play's rules on declaring that permission don't apply.
> - **HyperOS restricts apps in the background.** For an app installed outside the store, HyperOS turns *Autostart* off and limits its battery use. Either can stop reminders after the app is swiped away or the phone restarts. The app can't change these settings, and can't read Autostart at all, so it guides you to them (`RM-9`). This was seen on this same phone in the owner's earlier project, IAmOk, which also found that a *Force stop* cancels every alarm (`NF-2`).
> - **The emulator can't answer the riskiest question.** It has no HyperOS power management, and its Doze is not a real phone's. Whether reminders arrive on time is checked on the phone (`NF-1`).

The spec's first bullet is about the revocable `SCHEDULE_EXACT_ALARM`. The plan chose `USE_EXACT_ALARM` instead (`D-8`), which is granted at install and can't be revoked. T1 checks it on both devices.

**The reminder** (spec §6, low-fidelity sketch):

```
+------------------------------------------+
| [icon] Pills Reminder · now              |
| Amoxicillin: 1 capsule                   |
| Example · with food                      |
|  [ TAKEN ]     [ SNOOZE ]     [ SKIP ]   |
+------------------------------------------+
```

From the example (spec §4.4): "At 16:00 two reminders alert, "Amoxicillin: 1 capsule" and "Atrovent: 3 puffs", and each is answered on its own (`RM-4`)."

**The plan's design** (plan §2.3, steps 3–6):

> 3. **The reconcile** (`:app`, P5) applies the plan:
>    - it posts and removes notifications;
>    - it records which alerts have played, so a reconcile a minute later doesn't alert again;
>    - it replaces the single alarm with one at the next wake-up (`D-7`).
>    Reconciles run one at a time, behind a lock in the app's process.
> 4. **The alarm fires** → its receiver runs the reconcile.
> 5. **A notification action** (Taken, Snooze, Skip) → its receiver records the answer or the snooze, then runs the reconcile.
> 6. **The app opens, a treatment is edited, the phone restarts, the app is updated, or the time or time zone changes** → the reconcile runs (`D-9`).

**The decisions this phase implements** (plan §1.1):

| ID | Decision (quoted) |
|---|---|
| D-7 | "**One alarm at a time.** The app keeps a single exact alarm, set for the next moment something must happen: a take becomes due, a reminder alerts again, a snooze ends, or a take becomes Missed. When it fires, and after every change, a **reconcile** works out what the notification shade should show now, posts or removes notifications, and sets the next alarm." |
| D-8 | Plan v0.5, from T1: "The alarm is an **alarm clock**, `setAlarmClock()`, with the `USE_EXACT_ALARM` permission. That permission is granted at install and can't be revoked. Google Play restricts it to clock and calendar apps, but this app is never published there (`NF-9`). It shows an alarm icon in the status bar while a reminder is pending, which PQ-2 accepted." Its reason: "with the screen off, HyperOS held the app's `setExactAndAllowWhileIdle()` alarms until a 5-minute grid, up to 4 min 15 s late, outside Doze too, and also with the app exempt from battery optimisation. `setAlarmClock()` alarms came within 0.1 s with stock settings." |
| D-9 | "**Reconcile on every event that can lose an alarm.** Receivers for `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET` and `TIMEZONE_CHANGED`, and the app's start, all run the reconcile. Receivers do their short work with `goAsync()`." "Opening the app after a *Force stop* sets the alarm again. Nothing runs between reminders (`NF-5`)." |
| D-10 | Plan v0.6, from T2: "**One notification channel**, "Reminders": high importance (heads-up), the default notification sound, vibration off, and no Do Not Disturb bypass. Each take has its own notification, public on the lock screen, with **no category**, in one group, with Taken, Snooze and Skip actions. Each action is an explicit, immutable `PendingIntent` to a receiver that isn't exported." "A channel's sound and vibration are set when it is created, so they are right from the first install." |
| D-15 | "**Debug builds can schedule in minutes.** They add a "minutes" unit to the interval, and a debug screen that creates a test schedule and shows the next alarm. Release builds have neither." |
| D-16 | "The data stays in Android's default, credential-encrypted storage. After a restart, reminders start again once the phone has been unlocked for the first time. A reminder that came due in between alerts as soon as the phone is unlocked." |

Plan §2.4, on a stored file that can't be read (plan v0.4, from Q1): "The reminders change nothing: those already in the shade stay, and no new alarm is set (P5)." And: "If a reconcile's write fails, nothing is applied, and an alarm 5 minutes later tries again, for as long as it fails."

Plan §3 (plan v0.4, from Q3): "**Minimal surface.** Only the launcher activity is exported, and, in debug builds only, the debug tools' launcher activity (`D-15`, P5 Q3). The receivers aren't: the system still delivers its own broadcasts to them, and the alarm and the notification actions use explicit, immutable `PendingIntent`s. P5 confirms on the phone that each receiver gets what it needs."

Plan §4, on device runs: "A device run counts only on the phone. The emulator has no HyperOS power management, so a pass there proves nothing about Doze or HyperOS. Each run records the Android and HyperOS versions and whether the power settings were stock."

## 3. Scope

### In scope

- **Measure first** (T1, T2), on the phone with stock HyperOS settings, then with them relaxed, and on the emulator for Android 16: when alarms fire, whether each receiver gets its broadcast, and how HyperOS shows, sounds and groups the notifications (§4.6).
- **Reminder state in the store:** `PillsData.reminders`, in P3's format, with a golden test. The snooze action, and its repository helper (P4's pending wiring).
- **The reconcile:** one at a time; the plan worked out inside the store's `update`; the alarm, the removals and the posts applied after the write (P4 SPEC §4.3.7). It runs after every change of the data, and from the receivers and the app's start.
- **The alarm:** the single alarm clock at the plan's next wake-up (`D-7`, `D-8`).
- **Notifications:** the channel, one notification per Due take with its content and actions, the group, and opening the take on a tap (`D-10`, `RM-6`).
- **Receivers:** the alarm, the notification actions, and the system's restart, update, time and time zone broadcasts (`D-9`).
- **Permissions:** the four reminder permissions of plan §3, in the manifest.
- **Debug tools:** a debug-only screen that creates and removes test treatments in minutes, and shows the next wake-up, the alarm, the reminder state and what reminders depend on. A debug-only log of reminder events, with IDs and times only (`D-15`).
- **Device runs** on the phone for every event of `NF-1` and `NF-2`, with the HyperOS settings recorded.

### Out of scope

| What | Phase |
|---|---|
| The reminder check (`RM-9`): asking for the notification permission at first launch, battery optimisation and its dialog, HyperOS's settings as steps, the home screen's warning. P5 gives it the list of settings. | P7 |
| The Take screen that a tapped reminder opens. P5 opens P1's placeholder, on the same route. | P6 |
| Offering the minutes unit in the medicine editor of debug builds (`validate(allowMinutes = BuildConfig.DEBUG)`). The model and the format already have it (P2, P3). | P6 |
| Answering a take in the app (Take screen, home screen). Their reminders go without wiring, through the reconcile on every change (P5-D2). | P6, P7 |
| Changing the planner's rules: rounds, repeats, snoozes, removals, wake-ups | P4 (done) |
| `NF-1` and `NF-2` on the release build; the acceptance record; a day and a night of real use | P8 |

## 4. Design

### 4.1 Packages and files owned

P5 owns the package `io.github.davamix.pillsreminder.reminders` in `:app` (P2-D1 kept the name for it), the debug tools in the debug source set, and the reminder resources. The file names are suggestions; the behaviour in §4.3 and the format in §4.3.7 are what other phases rely on.

```
app/src/main/kotlin/io/github/davamix/pillsreminder/reminders/
├── Reminders.kt               the entry point in AppContainer: start(), reconcile(), answer()
├── Reconciler.kt              the reconcile (§4.3.1), on the interfaces below
├── ReminderShade.kt           the shade as the reconcile sees it, and AndroidReminderShade (§4.3.4)
├── ReminderChannel.kt         the notification channel (D-10)
├── ReminderContent.kt         a reminder's text, from the data (pure)
├── WakeUpAlarm.kt             the single alarm, and AlarmClockWakeUp on AlarmManager (§4.3.3)
├── ReminderIntents.kt         the PendingIntents, and the take key in an intent
├── TakeKeyCodec.kt            a take key as text, and back (pure)
├── ReminderLog.kt             reminder events, logged in debug builds only (§4.3.10)
├── AlarmReceiver.kt
├── ReminderActionReceiver.kt
└── SystemEventReceiver.kt
app/src/main/res/values/strings_reminders.xml
app/src/main/res/drawable/ic_notification.xml     the status-bar icon, monochrome
app/src/debug/AndroidManifest.xml                  the debug tools' activity (Q3)
app/src/debug/kotlin/io/github/davamix/pillsreminder/debug/
├── DebugActivity.kt
├── DebugScreen.kt             DebugScreen and the stateless DebugContent
├── DebugViewModel.kt
└── TestSchedules.kt           the test treatments' drafts (pure)
app/src/debug/res/values/strings_debug.xml
app/src/test/kotlin/io/github/davamix/pillsreminder/reminders/
├── ReconcilerTest.kt, RemindersTest.kt, ReminderContentTest.kt, TakeKeyCodecTest.kt
└── Fakes.kt                   FakeShade, FakeAlarm, a repository whose writes fail
app/src/test/kotlin/io/github/davamix/pillsreminder/data/
└── ReminderFormatTest.kt
app/src/test/resources/data/pills-v1-reminders.json   a new golden file (§4.3.7)
app/src/testDebug/kotlin/io/github/davamix/pillsreminder/debug/TestSchedulesTest.kt
app/src/androidTest/kotlin/io/github/davamix/pillsreminder/reminders/
├── ReminderShadeTest.kt, ReminderChannelTest.kt, WakeUpAlarmTest.kt
└── OpenTakeTest.kt
```

**Shared files** this phase changes, each only with its own entries:

- `core/.../model/PillsData.kt` (P2): the `reminders` field (§4.2).
- `core/.../actions/` (P2): `PillsData.snooze` and the repository's `snooze` helper. `deleteTreatment` and `saveTreatment` drop the reminder records of the medicines they remove (P5-D8). Their tests, in `core/src/test/.../actions/`, get cases for it.
- `app/.../data/PillsDocument.kt`, `PillsJson.kt` (P3): the `reminders` field of the format (§4.3.7). `StoreExample.kt` gets data with reminder records. `pills-v1.json` doesn't change.
- `app/src/main/AndroidManifest.xml` (P1): the four permissions and the three receivers (§4.3.6). Its comment about P5 goes.
- `app/.../di/AppContainer.kt` (P1, P3): `reminders`, and the scope it runs in.
- `app/.../PillsReminderApp.kt` (P1): `container.reminders.start()` in `onCreate`.
- `app/.../MainActivity.kt` (P1): a reconcile in `onStart`, and the take to open from a reminder's intent (§4.3.8).
- `app/.../navigation/AppNavHost.kt` (P1): a parameter with the take to open, and the navigation to it (§4.3.8). Routes don't change.
- `CLAUDE.md`: the `reminders` package, the reconcile on every change, the debug tools, and the notes for tests (T9).

**No new library.** `minSdk` is 33, so the platform's `Notification.Builder`, `NotificationChannel`, `AlarmManager` and `PendingIntent` have everything needed, without AndroidX Core's compat classes (P5-D4). Instrumented tests use `UiAutomation`, already in `androidx.test`, to grant the notification permission and to read `dumpsys alarm`.

### 4.2 Interfaces and data

The names are suggestions; the shapes and the rules are what matter.

**In `:core`:**

```kotlin
// core.model, PillsData.kt
data class PillsData(
    val treatments: List<Treatment> = emptyList(),
    val answers: Map<TakeKey, Answer> = emptyMap(),
    /** Snoozes and alerts played (P4). The reconcile stores the plan's `recorded` here. */
    val reminders: ReminderState = ReminderState(),
)

// core.actions
/** Snoozes the take's reminder (RM-3). `null` when P4's `snooze` refuses: the take isn't Due, or is already snoozed. */
fun PillsData.snooze(key: TakeKey, now: ZonedDateTime): PillsData?

/** The same inside `update`, on the latest data. False when refused. */
suspend fun TreatmentRepository.snooze(key: TakeKey, clock: Clock): Boolean
```

`ReminderState` and `TakeReminder` stay in `core.reminders`, where P4 put them (P5-D10).

**In `:app`, package `reminders`:**

```kotlin
/** Why a reconcile runs. Only the debug log uses it. */
enum class ReconcileTrigger { APP_START, APP_OPEN, DATA_CHANGED, ALARM, ACTION, BOOT, PACKAGE_REPLACED, TIME_SET, TIMEZONE_CHANGED, RETRY, DEBUG }

/** The notification shade, as the reconcile sees it. Only the app's own reminders, never other notifications. */
interface ReminderShade {
    /** The keys of the reminders in the shade now. */
    fun shown(): Set<TakeKey>

    /** Posts the reminder, with sound when [alert]; otherwise silently, and only if it is missing or its text changed. */
    fun post(content: ReminderContent, alert: Boolean)

    fun remove(key: TakeKey)

    /** The group's summary: shown while [count] > 0, removed at 0. */
    fun summary(count: Int)
}

/** The single alarm (D-7). */
interface WakeUpAlarm {
    fun set(at: Instant)
    fun cancel()
}

/** What a reminder says (RM-1, TR-6): plain values; the Android side formats them with the strings of strings_reminders.xml. */
data class ReminderContent(
    val key: TakeKey,
    val medicine: String,
    val dose: String,
    val treatment: String,
    /** Empty when the medicine has none. */
    val instructions: String,
    /** When the take fell due: the notification's time. */
    val dueAt: Instant,
)

/** The content of the take's reminder, or `null` when the take's medicine is gone. */
fun PillsData.reminderContent(key: TakeKey, zone: ZoneId): ReminderContent?

/** What a reconcile did. */
sealed interface ReconcileResult {
    data class Applied(val plan: ReminderPlan) : ReconcileResult
    /** The stored data can't be read: nothing was planned or changed (Q1). */
    data object Unreadable : ReconcileResult
    /** The write failed: nothing was applied, and a retry is set (Q1). */
    data object Failed : ReconcileResult
}

/** The reconcile (§4.3.1). One instance per process, in AppContainer. */
class Reconciler(
    private val repository: TreatmentRepository,
    private val clock: Clock,
    private val shade: ReminderShade,
    private val alarm: WakeUpAlarm,
    private val log: ReminderLog,
) {
    suspend fun reconcile(trigger: ReconcileTrigger): ReconcileResult
}

/** The three actions of a reminder (RM-3). */
enum class ReminderAction { TAKEN, SNOOZE, SKIP }

/** What AppContainer holds, and what the receivers, the activity and the debug tools call. */
class Reminders(
    private val repository: TreatmentRepository,
    private val clock: Clock,
    private val reconciler: Reconciler,
    private val scope: CoroutineScope,
    private val log: ReminderLog,
) {
    /** Creates the channel, then reconciles on the first data and after every change (P5-D2). Called once, from the app. */
    fun start()

    suspend fun reconcile(trigger: ReconcileTrigger): ReconcileResult

    /** Records the answer or the snooze through P2's helpers (markTaken, skip, snooze), then reconciles (§4.3.5). */
    suspend fun answer(action: ReminderAction, key: TakeKey)

    /** For receivers: runs [work] in the scope, and calls [done] (the PendingResult's finish) when it ends, however it ends. */
    fun runAsync(done: () -> Unit, work: suspend () -> Unit)
}

/** A take key as text, for notification tags and intents: medicine ID and planned time, `2026-11-23T16:00`. */
object TakeKeyCodec {
    fun encode(key: TakeKey): String
    /** `null` for text that isn't a key the app wrote: a missing part, a time that isn't whole minutes. */
    fun decode(text: String): TakeKey?
}
```

The Android implementations are `AndroidReminderShade(context, tagPrefix = "take")` and `AlarmClockWakeUp(context, identifier = "wake-up")`. Instrumented tests build their own with another prefix and identifier (P5-D11).

### 4.3 Behaviour

#### 4.3.1 The reconcile

```mermaid
sequenceDiagram
    participant T as Trigger (alarm, action, boot, data change…)
    participant R as Reconciler
    participant S as Store (DataStore)
    participant A as AlarmManager
    participant N as Notification shade
    T->>R: reconcile(trigger)
    Note over R: lock; read now once
    R->>S: update { plan(data, data.reminders, now); store plan.recorded }
    S-->>R: data written (or unchanged)
    R->>A: set the alarm at nextWakeUp, or cancel it
    R->>N: remove removals(shown)
    R->>N: post each reminder (sound only when alert)
    R->>N: summary(count)
```

1. **One at a time:** a `Mutex` in the `Reconciler`. The app has one process (P3), so one lock covers every caller.
2. **Now, once:** `ZonedDateTime.now(clock)`, read before the update (P2-D3).
3. **Plan inside the write:** `repository.update { data -> plan(data, data.reminders, now)`, keeping the plan, then `data.copy(reminders = plan.recorded) }`. The update runs on the latest data, so an answer given at the same moment is never lost. A plan that records nothing new writes nothing (DataStore compares).
4. **Then the alarm:** `alarm.set(nextWakeUp)`, or `alarm.cancel()` when it is `null`. The alarm is set before the notifications, so that it is in place even if the process ends halfway (P5-D3).
5. **Then the shade:**
   - remove `plan.removals(shade.shown())`;
   - post each reminder in the plan, in its order, with `reminderContent(key, now.zone)` from the data the update returned: with sound when `alert`, otherwise silently and only if it is missing or its text changed (§4.3.4);
   - `summary(plan.reminders.size)`.
6. **Log** (debug builds only, §4.3.10): the trigger, each alert with its lateness, each removal, and the alarm.

**Failures** (Q1):

- **The data can't be read** (`UnreadableDataException`): nothing is planned. The shade and the alarm stay as they are. The result is `Unreadable`, and debug builds log it. The home screen says the data can't be read (P7).
- **The write fails** (`IOException`, or the write check's `IllegalStateException`, P3-D3): nothing is applied, because the plan's alerts weren't recorded and would play again. The alarm is set 5 minutes later, to try again (`RETRY`). The result is `Failed`.
- **Any other exception** is a bug: logged by class name only, never its message, and treated as a failed write. A receiver must always reach `finish()`.
- **The process ends halfway**, after the write and before the posts: the alerts are recorded but weren't shown. The alarm is already set, and the next reconcile posts the reminders silently (P4 SPEC §4.3.3). Receivers run at foreground priority while they work, so this is unlikely; P5-D3 accepts it.

**Idempotent:** a second reconcile at the same moment finds the same plan, writes nothing, posts nothing with sound, and sets the same alarm (P4 SPEC §4.3.6).

#### 4.3.2 When it runs

| Event | Who calls it | Trigger |
|---|---|---|
| The process starts (the app opens, or the system starts it for a receiver) | `Reminders.start()`, on the first value of `repository.data` | `APP_START` |
| Any change of the stored data: an answer, an edit, an end or a delete in the app, a test treatment from the debug screen, the reconcile's own write | `Reminders.start()`'s observer (P5-D2) | `DATA_CHANGED` |
| The app comes to the front | `MainActivity.onStart` | `APP_OPEN` |
| The alarm fires | `AlarmReceiver` | `ALARM` |
| Taken, Snooze or Skip on a reminder | `ReminderActionReceiver`, after the answer | `ACTION` |
| The phone restarted, once it was first unlocked (`D-16`) | `SystemEventReceiver` | `BOOT` |
| The app was updated | `SystemEventReceiver` | `PACKAGE_REPLACED` |
| The time or the time zone changed | `SystemEventReceiver` | `TIME_SET`, `TIMEZONE_CHANGED` |
| A failed write's retry | `AlarmReceiver` | `RETRY` |
| "Reconcile now" on the debug screen | `DebugViewModel` | `DEBUG` |

- **The observer** collects `repository.data` in the container's scope. It reconciles on its first value and after each change. Its own write is a change, so one more reconcile follows, which finds nothing to do and writes nothing. It runs nothing while the data doesn't change (`NF-5`).
- **Receivers wait for their own reconcile** before `finish()`, so the process stays alive until the shade and the alarm are right. The observer's reconcile that may follow finds nothing to do.
- **After a *Force stop*,** nothing runs until the app is opened. Opening it starts the process, and `APP_START` sets the alarm again (`NF-2`).
- **After the notification permission is granted,** for example by P7's check, `APP_OPEN` posts the reminders the plan holds, silently.
- **After an app update,** Android has removed the app's notifications: installing force-stops the app, and so does a *Force stop* (T2). `PACKAGE_REPLACED` posts them again, silently. With Autostart off, HyperOS blocks that broadcast ("process is not permitted to auto start", T1), so they come back only at the next alarm or when the app is opened. Autostart is on P7's list (Q10).
- **After a restart,** `BOOT_COMPLETED` came 59 s after the unlock, behind 215 other receivers, and 8.7 s after it with the intent filter's priority at 999 (§4.3.6, Q9). Autostart off didn't block it.

#### 4.3.3 The alarm

- **One alarm:** `AlarmClockWakeUp` holds one `PendingIntent`: explicit, to `AlarmReceiver`, with the action `…action.WAKE_UP`, its identifier (`Intent.setIdentifier`), and `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`. Setting it again replaces it; `cancel()` cancels it.
- **An alarm clock** (`D-8`, Q5): `setAlarmClock(AlarmClockInfo(at.toEpochMilli(), openApp), pendingIntent)`. Its time is on the wall clock, which is what the plan's instants are on; a change of time also reconciles (`TIME_SET`). `openApp` is an immutable `PendingIntent` to `MainActivity`, which Android opens when the user taps the next alarm in quick settings.
- **Why not `setExactAndAllowWhileIdle()`:** with HyperOS's stock settings and the screen off, T1 found it held until a 5-minute grid, up to 4 min 15 s late, outside Doze and also with the app exempt from battery optimisation. Alarm clocks came within 0.1 s (Q5).
- **The status bar** shows an alarm icon while the alarm is set, so while any take is still to come; quick settings and the lock screen may show its time. PQ-2 accepted it.
- **Without the exact-alarm permission** (`canScheduleExactAlarms()` false, which `USE_EXACT_ALARM` should never allow; alarm clocks need it too): `setAndAllowWhileIdle`, so reminders still come, late, rather than the receiver crashing on a `SecurityException`. Debug builds log it. P7's check shows the permission as missing.
- **A time in the past** is set as it is: Android fires it within seconds (about 5 s on the phone, T1).
- **Restarts** clear every alarm; `BOOT` sets it again. An update keeps it (T1); `PACKAGE_REPLACED` sets it again anyway, when HyperOS lets the broadcast through (Q10).
- **Doze** (T1): Android doesn't enter deep Doze in the 30 minutes before an alarm clock (`min_time_to_alarm`), and leaves deep Doze about 5 minutes before one. Alarm clocks were on time in light and deep Doze, after the process ended and after a swipe from Recents, with stock settings.

#### 4.3.4 Notifications

**The channel** (`D-10`), created by `Reminders.start()` on every start, which changes nothing once it exists:

| Setting | Value |
|---|---|
| ID, name | `reminders`, "Reminders" |
| Importance | `IMPORTANCE_HIGH`: heads-up (`RM-2`) |
| Sound | The default notification sound, on the notification stream, which follows silent mode (`RM-8`) |
| Vibration | Off: `enableVibration(false)` (`RM-2`). In vibrate mode Android vibrates instead of sounding all the same, with its fallback vibration (`RM-2`, Q7). |
| Do Not Disturb | No bypass (`RM-8`) |

- **The lock screen** isn't the channel's to set: Android ignores an app's `lockscreenVisibility` and keeps `VISIBILITY_NO_OVERRIDE` (T2). Each notification is public itself (below), which the lock screen follows (`RM-11`, Q8).
- Android keeps a channel's sound, vibration and importance as first created, and the user's changes to them. Changing them later needs a new channel ID, with the old channel deleted.

**One notification per Due take** (`RM-4`):

| Part | Value |
|---|---|
| Identity | Tag `take/<encoded key>` (`TakeKeyCodec`), ID 1. `shown()` reads the tags of the app's active notifications, and ignores any tag it can't decode. |
| Title | `"%1$s: %2$s"`, medicine and dose: "Amoxicillin: 1 capsule" (`RM-1`) |
| Text | `"%1$s · %2$s"`, treatment and instructions: "Example · with food"; or only the treatment, "Example", without instructions (`TR-6`). Treatment notes aren't shown. |
| Time | `setWhen(dueAt)`, shown: the time the take fell due, such as 16:00, not when it was posted (P5-D7). |
| Category | **None.** HyperOS's Do Not Disturb lets the *reminder* category sound, and shows no setting to change it; without a category, Do Not Disturb holds the sound and the pop-up back, and the reminder stays in the shade (`RM-8`, Q6). |
| Visibility | `VISIBILITY_PUBLIC`: the full content on the lock screen (`RM-11`, Q8) |
| Group | One group for all reminders, with a summary (P5-D6). The group alert behaviour chooses between alert and silent (below). |
| Actions | **Taken**, **Snooze**, **Skip**: explicit, immutable broadcast `PendingIntent`s to `ReminderActionReceiver`, one per take and action (§4.3.5). They work from the lock screen without unlocking (P5-D7). |
| Tap | Opens the take (`RM-6`, §4.3.8). `setAutoCancel(false)`: the reminder stays until it is answered or Missed (`RM-5`). |
| Small icon | `ic_notification`, a monochrome pill |
| Fingerprint | A hash of the title, text and time, kept in the notification's extras |

**Alert or silent:**

- **Alert:** `setGroupAlertBehavior(GROUP_ALERT_CHILDREN)` and `setOnlyAlertOnce(false)`: it sounds and pops up, also as an update of a reminder already in the shade, as for a repeat (T2: a repeat sounded again and popped up).
- **Silent:** `setGroupAlertBehavior(GROUP_ALERT_SUMMARY)`: Android mutes a group child that only its summary may alert for, with no sound, no vibration and no pop-up. The platform has no `setSilent()`; that is AndroidX's `NotificationCompat`, which does the same (Q8). Posted only when the reminder isn't in the shade, or its fingerprint changed (an edit of the name or dose). Otherwise it isn't posted at all (P5-D5). Android drops updates above about 5 a second per app, which needless reposts could reach, and an alert must never be dropped.

**The summary:** tag `summary`, with `GROUP_ALERT_CHILDREN`, so it never alerts; posted while the plan holds at least one reminder, and again only when the count changes; removed when it holds none. Its text says how many takes are due; tapping it opens the app.

**HyperOS keeps at most 10 notifications per app,** the summary included, and drops the oldest (T2). A medicine has at most one Due take, so only 10 or more medicines due at once would reach it.

**Swiping a reminder away** (Q2): it doesn't answer or snooze the take. The take is still Due, so the next reconcile posts it again: with sound if a repeat is due then, silently otherwise.

**Two reminders that alert at once** (Q4): both are posted, each with sound, and both pop up. On the phone, Android played one sound for the two (T2), which `RM-4` allows.

**The ring modes** (T2, Q7): normal mode sounds; HyperOS's Silent mode shows the reminder with no sound and no vibration; vibrate mode vibrates instead of sounding, as Android does with every notification; Do Not Disturb shows it in the shade only (`RM-2`, `RM-8`).

**Without the notification permission,** posts are dropped by Android. The reconcile still records the alerts. P7's check shows the permission as missing.

#### 4.3.5 Answering from a reminder

`ReminderActionReceiver` gets an explicit intent with an action (`…action.TAKEN`, `…SNOOZE` or `…SKIP`) and the take key, as text in an extra and in the intent's identifier, so each take's `PendingIntent`s are distinct.

1. Decode the key. An intent without a valid key is ignored, and debug builds log it.
2. `Reminders.answer(action, key)`, in `runAsync`:
   - **Taken:** `repository.markTaken(key, clock)`, which records now, in whole minutes (`TK-2`).
   - **Skip:** `repository.skip(key, clock)`.
   - **Snooze:** `repository.snooze(key, clock)` (`RM-3`).
3. Then `reconcile(ACTION)`, whatever the answer gave. The reminder leaves the shade: answered, snoozed, or stale.

**Refused answers:** P2's and P4's rules decide. A reminder that is still in the shade for a take that is no longer Due (Missed while the shade was out of date, or a second tap) gets nothing recorded, and the reconcile removes it. A second tap on Snooze can't stretch the snooze (P4 SPEC §4.3.4). Debug builds log `refused`.

#### 4.3.6 The manifest

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<!-- Used by P7's reminder check (RM-9). -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<receiver android:name=".reminders.AlarmReceiver" android:exported="false" />
<receiver android:name=".reminders.ReminderActionReceiver" android:exported="false" />
<receiver android:name=".reminders.SystemEventReceiver" android:exported="false">
    <!-- BOOT_COMPLETED goes to every app's receivers in turn, in priority order: 999 put the app near the front (Q9). -->
    <intent-filter android:priority="999">
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
        <action android:name="android.intent.action.TIME_SET" />
        <action android:name="android.intent.action.TIMEZONE_CHANGED" />
    </intent-filter>
</receiver>
```

- **No receiver is exported** (plan §3). The system delivers its own broadcasts to them: T1 saw `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` on the phone, and `TIME_SET`, `TIMEZONE_CHANGED` and `MY_PACKAGE_REPLACED` on the emulator. The alarm and the actions reach theirs through explicit `PendingIntent`s, from the lock screen too (T2).
- **The priority** (Q9) only orders this app's receiver among the other apps' receivers of the same broadcast. 999 is the highest an app may use; 1000 and above are the system's.
- **No `android:process`**: DataStore works in one process only (P3).
- **No `directBootAware`**: the data is in credential-encrypted storage, so nothing runs before the first unlock (`D-16`). `BOOT_COMPLETED` comes after it.
- Every receiver calls `goAsync()` and finishes when its work ends (`Reminders.runAsync`). The work is short: one read and one write of a small file.
- The permissions are all in P1's allowlist (P1-D6), so the build's check doesn't change. P5 declares all four, since plan §5.1 gives it the reminder permissions; P7 uses the fourth.

#### 4.3.7 Reminder state in the store

`PillsData.reminders` is P4's `ReminderState`: a record for each Due take that has alerted or is snoozed. The format keeps version 1, with a new optional field, as P3's rules for the format allow (P3 SPEC §4.3.6, P5-D1):

```json
{
  "schemaVersion": 1,
  "treatments": [ … ],
  "answers": [ … ],
  "reminders": [
    { "medicineId": "amoxicillin", "plannedAt": "2026-11-23T16:00", "alerts": 2, "lastAlertAt": "2026-11-23T15:10:00Z" },
    { "medicineId": "atrovent", "plannedAt": "2026-11-23T16:00", "snoozedUntil": "2026-11-23T15:15:00Z" }
  ]
}
```

| Field | Value |
|---|---|
| `reminders` | Last, after `answers`. Left out when there are no records, so data without any is written exactly as before. Sorted by `medicineId`, then `plannedAt`, like the answers. |
| `medicineId`, `plannedAt` | The take key, as in `answers`. |
| `snoozedUntil` | `TakeReminder.Snoozed(until)` |
| `alerts`, `lastAlertAt` | `TakeReminder.Alerted(count, lastAt)`, `alerts` from 1 to 4 |
| Instants | UTC, in whole seconds, as `Instant.toString()` writes them: `2026-11-23T15:10:00Z` (P4-D5). |

**Reading** is strict, as for the rest of the file (P3-D2). The mapping refuses:

- a record with both kinds, or neither; `alerts` without `lastAlertAt`, or the other way round;
- `alerts` outside 1 to 4 (P4's `Alerted` refuses it);
- an instant not written exactly as above: without seconds, with a fraction, or with an offset other than `Z`;
- two records for the same take.

Records whose take is no longer Due, or no longer exists, are kept as they are. The planner drops them at the next reconcile (P4-D4).

**Golden files:** `pills-v1.json` doesn't change, still decodes, now with no records, and is still written the same. A new golden file, `pills-v1-reminders.json`, holds data with both kinds of record, and is checked both ways, as P3 checks `pills-v1.json`.

**An older build** of the app, from before P5, reads a file with `reminders` as Unreadable (an unknown field), and doesn't write over it. Installing an older build over a newer one isn't supported.

#### 4.3.8 Opening a take

- The content intent is an explicit `PendingIntent.getActivity` to `MainActivity`: immutable, `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP`, with the take key in extras, and the key as its identifier.
- `MainActivity` reads the take from the intent in `onCreate` (only when `savedInstanceState` is `null`, so a recreated activity doesn't open it twice) and in `onNewIntent`. It passes it to `AppNavHost(openTake = TakeRoute(medicineId, plannedAt), onTakeOpened = …)`.
- `AppNavHost` navigates to it once, from a `LaunchedEffect` on the value, after waiting with `lifecycle.currentStateFlow.first { it.isAtLeast(RESUMED) }`, then calls `onTakeOpened()` to clear it (CLAUDE.md, "Navigation driven by a ViewModel state"). Back returns to Home.
- The Take screen is P1's placeholder until P6 replaces it. The route and its arguments don't change.
- HyperOS blocks activities started from the background (P1). A tap on a notification is the user's own action, so it should be allowed; T2 checks it, also with the process ended and from the lock screen.

#### 4.3.9 Debug tools

Debug builds only (`D-15`). Release builds contain none of this code: it lives in `app/src/debug/`.

- **Where:** `DebugActivity`, with its own launcher icon, "Pills debug", declared in `app/src/debug/AndroidManifest.xml` (Q3). The release manifest doesn't change.
- **Test treatments:** a treatment named "Test", with one or two medicines, "Test A" and "Test B", "1 tablet" each, built with the minutes unit and `validate(allowMinutes = true)`. Its ID starts with `debug-`, so the screen ends or deletes only its own treatments. Settings: every N minutes (default 45, so a round of 4 alerts at 0, 10, 20 and 30 minutes fits before the take is Missed at 45), first take in M minutes (default 2), one or two medicines (two fall due together, `RM-4`), for 1 day or with no end.
- **It shows:** the plan's next wake-up; whether the alarm's `PendingIntent` exists (`FLAG_NO_CREATE`); the reminder records; the reminders in the shade; what reminders depend on: notifications allowed, `canScheduleExactAlarms()`, battery optimisation, the channel's importance; and the last lines of the reminder log.
- **Buttons:** Create test treatment; End test treatments (`TR-11`); Delete test treatments (`TR-9`); Reconcile now; Allow notifications (the runtime request, while it isn't granted); Clear log.
- It changes the data only through P2's helpers, so the observer reconciles after each change.
- It must fit the phone's content area, **392.7 × 796.4 dp**, scrolling where it must. It is drawn in every state in T6, in light and dark theme and at the largest font size.

Suggested texts (the file `strings_debug.xml` is the source of the final wording): "Debug tools", "Every %d minutes", "First take in %d minutes", "Two medicines", "No end", "Create test treatment", "End test treatments", "Delete test treatments", "Reconcile now", "Next wake-up: %s", "Alarm: set" / "Alarm: none", "Notifications: allowed" / "not allowed", "Exact alarms: allowed" / "not allowed", "Battery optimisation: on" / "off", "Allow notifications", "Clear log".

#### 4.3.10 Logs

- **Debug builds only** (plan §3): `ReminderLog` writes to logcat, tag `PillsReminders`, and appends to `files/reminders-log.txt` in the app's private storage, so a run of hours can be read with `exec-out` (`phone` skill, "Measuring device behaviour"). The file is trimmed to its newer half when it passes 1 MB. In release builds it does nothing.
- **IDs and times only,** never names, doses, instructions or notes. A line holds the trigger, a take key, instants, and counts. For example: `alert <medicineId> 2026-11-23T16:00 due=… at=… late=850ms`, `remove …`, `alarm 2026-11-23T15:10:00Z`, `action snooze … refused`, `unreadable`, `failed IOException`.
- **The lateness** of each alert is the moment of the post minus the moment its take fell due, its snooze ended, or its repeat was due. The device runs read it from the file.

#### 4.3.11 Example

The §4.4 example on the phone, Monday 23 November 2026 (P4 SPEC §4.3.9 has the plan's side):

| Time | Event | What the phone does |
|---|---|---|
| 07:00 | The app opens | `APP_START`: no reminder; the alarm at 08:00. |
| 08:00 | The alarm | Two notifications, "Amoxicillin: 1 capsule" and "Atrovent: 3 puffs", each "Example", in one group, with sound and heads-up; the alarm at 08:10. |
| 08:02 | Taken on Atrovent's, from the lock screen | `markTaken`, then the reconcile: Atrovent's goes; Amoxicillin's stays, silent. |
| 08:10, 08:20, 08:30 | The alarm | Amoxicillin's sounds again each time; after 08:30, the alarm at 16:00. |
| 08:45 | Snooze | It leaves the shade; the alarm at 08:55, when it sounds again, with a new round. |
| 12:00 | The phone restarts, and is unlocked at 12:05 | `BOOT` at 12:05: Amoxicillin's 08:00 reminder comes back silently (its record was kept); the alarm at 16:00. |
| 16:00 | The alarm | The 08:00 take is Missed: its reminder goes. Both 16:00 reminders appear with sound. |

### 4.4 Dependencies

- **Libraries:** none new (P5-D4).
- **From P2 (merged):** `PillsData`, `TakeKey`, `MedicineId`, `TreatmentRepository`, `UnreadableDataException`, the actions (`markTaken`, `skip`, `saveTreatment`, `endTreatment`, `deleteTreatment`) and their repository helpers, `PillsData.medicine(id)`, `dueAt(zone)`, `CurrentZoneClock`, `IntervalUnit.MINUTES`, `TreatmentDraft`, `MedicineDraft`, `validate(allowMinutes)`.
- **From P3 (merged):** `DataStoreTreatmentRepository` as `AppContainer.repository`; the format types and mapping, `encodePillsData`, `decodePillsData`; `StoreExample` and the golden test.
- **From P4 (merged):** `ReminderPlanner.plan(data, reminders, now)`, `ReminderPlan` (`reminders`, `alerts`, `nextWakeUp`, `recorded`, `removals(inShade)`), `Reminder`, `ReminderState`, `TakeReminder`, `ReminderState.snooze(data, key, now)`.
- **From P1 (merged):** the manifest, the permission allowlist check, `AppContainer`, `PillsReminderApp`, `MainActivity`, `AppNavHost`, `TakeRoute(medicineId, plannedAt)`, the placeholders.

**From earlier phases** (their handovers), carried here so this phase needn't open them:

- **P4's pending wiring for P5:**
  - "add `reminders: ReminderState` to `PillsData`, and to the JSON format in the same change, with a golden test. … Then add `PillsData.snooze(key, now)` and a repository helper that runs it inside `update`, like P2's actions." → T3.
  - "apply plans as SPEC §4.3.7 says: work out the plan inside `update` on the latest data and store `recorded`, then remove `removals(…)` of the app's notifications in the shade, post the plan's reminders (with sound only when `alert` is true), and set or cancel the single alarm at `nextWakeUp`. Run a reconcile after every change, including a snooze." → §4.3.1, T4. P5 sets the alarm before the shade (P5-D3).
  - "a deleted treatment's or removed medicine's records go at the next reconcile (P4-D4). If P5 wants them gone in the same write, `deleteTreatment` and `saveTreatment` can drop them." → P5-D8.
  - "what swiping a reminder away does … and whether reminders that alert at the same moment play one sound (`RM-4`)." → Q2, Q4, T2.
- **P4's guarantees:** a plan applied in full never alerts twice and never skips an alert; a second plan at the same now changes nothing; `nextWakeUp` is exact, to the millisecond; records hold instants in whole seconds, and `TakeReminder` refuses any other; "A reconcile cut off halfway, by the process ending between steps, is P5's to handle" (§4.3.1).
- **P3's pending wiring for P5:** "version 1 of the format holds no snoozes or alerts played. When P5 wires P4's reminder state into `PillsData`, it adds it to the format, following SPEC §4.3.6, with a golden test." And: "DataStore works in one process only. No receiver or other component may set `android:process`."
- **P3's rules for the format** (CLAUDE.md): a field added to `PillsData` goes into the format in the same change, with a test, because the write check refuses data that doesn't read back the same (P3-D3). Golden files never change. Strict reading (P3-D2). kotlinx.serialization's messages quote the JSON: never log one.
- **P2's pending wiring for P5:** "check on the emulator that `CurrentZoneClock` follows a change of time zone while the app is running (*Settings > System > Date & time*; the shell can't change the zone)." → E3 in §6.
- **P1's known issue:** "HyperOS blocks background activity starts. … P5 and P7 should check whether this blocks anything the app starts without a tap, such as opening the app from a notification." → T2. P5 starts no activity without a tap.

**Pitfalls that apply here** (from CLAUDE.md, the `phone` skill and earlier phases):

- **The phone is the user's own.** Ask before changing a setting, forcing Doze, restarting it, granting a permission or installing anything but the app, and put it back. Never change its clock or time zone: time and zone changes are checked on the emulator. Read its screen only while the app is in front. Never publish a screenshot of it.
- **`force_stop` cancels every alarm.** To stand in for Android ending the process, use `end_process`, and check with `pidof` that it really ended: `am kill` doesn't always (IAmOk).
- **Forced Doze can stop short** of deep idle on HyperOS. Check `doze_state` and the idling history; an alarm on a phone that never idled proves nothing. Always `doze_off` afterwards, from a `trap`.
- **Instrumented tests:** run them with `instrument`, never `connectedDebugAndroidTest`, which uninstalls the app and deletes its data. On the phone, unlock it first; `instrument` grants op 10021 again after an install, an update or a restart. Names: sentences with no punctuation but `-`, `_` and `$`. Run a single test by its class.
- **Instrumented tests and the real data:** the app's own reconcile runs in the test process, on the real data (`APP_START`). Tests use their own shade prefix and alarm identifier (P5-D11), a fake or cache-file repository, and leave the app's data and alarm as they were. A test through `MainActivity` runs the real `APP_OPEN` reconcile, as opening the app does.
- **Test notifications on the phone** go into the user's shade: post them silently, remove them at the end, and use only the §4.4 example. The shell can't remove a notification; the app can.
- **`android.util.Log` doesn't work in JVM unit tests:** the reconcile takes a `ReminderLog`, and only the Android implementation calls `Log`.
- **Coroutine tests:** `advanceUntilIdle()` ignores work only `backgroundScope` scheduled; wait with `flow.first { … }`. The observer's tests run it in a scope the test controls.
- **Model objects' `toString()` holds treatment content:** never put one in a log, an exception message or an assertion message.
- **Navigation from a state** (the take to open) waits for `RESUMED` in a `LaunchedEffect`, and doesn't use `dropUnlessResumed`, which would drop it on a recreated screen (CLAUDE.md).
- **New tests that pass at once:** check that they can fail, with one deliberate bug in a copy of the code, and note it in the handover (CLAUDE.md).
- **ktlint and Gradle's exit code:** `ktlintFormat` as its own command before `build`; write Gradle's output to a file, never pipe it before `&&`.

### 4.5 Decisions made in this phase

| ID | Decision | Reason |
|---|---|---|
| P5-D1 | `PillsData.reminders` is stored in format version 1, as an optional `reminders` list, left out when empty. A new golden file holds it; `pills-v1.json` doesn't change. | P3 SPEC §4.3.6: a change that version-1 files still decode as keeps version 1, with a golden test of the new field. Leaving it out when empty keeps data without records byte for byte as before. |
| P5-D2 | The reconcile runs on every change of the stored data, from an observer of `repository.data` started with the app. Receivers also call it and wait for it; `MainActivity.onStart` calls it too. Screens never need to. | Plan §2.2 draws the store's changes feeding the reconcile. An answer, an edit, an end or a delete from any screen then removes or updates its reminder (`RM-7`, `TR-9`, `TR-11`) with nothing for P6, P7 or P8 to wire. The observer only waits between changes (`NF-5`). |
| P5-D3 | A reconcile writes, then sets the alarm, then removes, then posts. | P4's order (write first) keeps alerts from playing twice. Setting the alarm right after the write means that a process ended halfway still wakes up again, and shows the reminders silently. |
| P5-D4 | The platform's notification and alarm APIs, with no AndroidX Core compat classes. | `minSdk` 33 has what is needed (`Intent.setIdentifier`, channels, group alert behaviour, `setAlarmClock`). The platform has no `setSilent()`; a silent post is a group child with `GROUP_ALERT_SUMMARY`, as `NotificationCompat` does it (Q8). No new library to vet (plan §3). |
| P5-D5 | A silent reminder is posted only when it is missing from the shade or its text changed (a fingerprint in its extras). | Android drops updates above about 5 a second per app. Needless reposts at every reconcile could make it drop one that alerts. |
| P5-D6 | One group for every reminder, with a summary. A reminder that alerts has `GROUP_ALERT_CHILDREN`, a silent one `GROUP_ALERT_SUMMARY`; the summary has `GROUP_ALERT_CHILDREN`, so it never alerts. | `RM-4`: Android groups them. Android 13 groups a single app's notifications by itself only from four on. The group alert behaviour is also how a post is made silent on the platform (Q8). |
| P5-D7 | A reminder shows the time its take fell due. It stays after a tap. Its actions work from the lock screen without unlocking. | The planned time is what the user needs to see, also after a restart or a late alarm. `RM-5`: it stays until answered. `RM-11` shows the full content there, and answering from it is the natural next step; Android's default for broadcast actions. |
| P5-D8 | `deleteTreatment` and `saveTreatment` drop the reminder records of the medicines they remove, in the same write. | `TR-9` deletes everything recorded for the treatment; the answers already go in the same write. The planner would drop them at the next reconcile anyway (P4-D4), but the file then never holds them. |
| P5-D9 | Debug builds log reminder events, IDs and times only, to logcat and to a file in private storage. | The device runs need each alert's lateness over hours, longer than logcat keeps. Release builds don't log (plan §3). |
| P5-D10 | `PillsData.reminders` uses P4's `ReminderState`, left in `core.reminders`. | `core.model` then refers to one type of `core.reminders`, as P4-D6 proposed. Moving P4's types would change its merged files for no gain in behaviour. |
| P5-D11 | `AndroidReminderShade` and `AlarmClockWakeUp` take a tag prefix and an alarm identifier. Instrumented tests use their own. | The app's own reconcile runs in the test process. With their own prefix and identifier, it never removes a test's notifications, and a test never replaces the app's real alarm. |

### 4.6 Measured first

T1 and T2 measure on the devices what the design relies on, with throwaway probes (`phone` skill, "Measuring device behaviour"): a debug-only receiver and probe tests that set alarms and post notifications, and log times to logcat and to a file. The probes are never committed. Each measurement is repeated 2–3 times and records the device, its Android and HyperOS versions, and the power settings.

**On the phone, stock settings first** (Autostart off, battery saver as HyperOS sets it), **then relaxed** (Autostart on, battery saver "No restrictions", and whatever the stock runs show is needed). The difference is the list for P7's check (`RM-9`).

Measured on 2026-11-20 and 21. The handover records every run, with its times.

| # | What | Device | Result |
|---|---|---|---|
| A1 | `USE_EXACT_ALARM` granted, and `canScheduleExactAlarms()` true | Phone, emulator | Yes on both. |
| A2 | An alarm 2–3 minutes ahead: with the screen off; in forced deep Doze; after a swipe from Recents (by the user); after the process ended (`end_process`) | Phone | `setExactAndAllowWhileIdle()` with the screen off: **held until a 5-minute grid**, up to 4 min 15 s late, also with the battery saver at "No restrictions" (Q5). Alarm clocks, stock settings: on time in every case, 22 ms to 0.9 s late. Deep Doze can only be entered more than 30 minutes before an alarm clock: one 45 minutes ahead brought the phone out of deep Doze 5 minutes early and fired 41 ms late. |
| A3 | Alarm clocks 10 minutes apart, for a night, unplugged | Phone | All 60 on time, 25 ms to 1.7 s late, in light Doze. Alarm clocks that close together keep the phone out of deep Doze; a night with reminders hours apart is part of T8 (M7). |
| A4 | A restart: `BOOT_COMPLETED` reaches a receiver that isn't exported, after the first unlock, and how soon | Phone | With Autostart off: 59 s after the unlock, behind 215 other receivers; with the intent filter's priority at 999, **8.7 s** (Q9). |
| A5 | `adb install -r`: `MY_PACKAGE_REPLACED`; whether the alarm and the notifications survive | Phone, emulator | The alarm survives. The notifications don't: all are removed (T2). `MY_PACKAGE_REPLACED` is **blocked by HyperOS with Autostart off**, and arrives with it on, and on the emulator (Q10). |
| A6 | `TIME_SET` and `TIMEZONE_CHANGED` reach the receiver; `CurrentZoneClock` follows the new zone in the running process (P2's pending check) | Emulator | Both arrive in the running process, and `CurrentZoneClock` follows. The shell can change the emulator's time and zone with `cmd alarm set-time` and `set-timezone`. |
| A7 | A *Force stop* cancels the alarm | Phone | Yes; the app then gets no broadcast until it is opened. |
| A8 | Only if A2 or A3 are late: the same with `setAlarmClock()` | Phone | Three alarm clocks with the screen off: 45–53 ms late (Q5). |
| N1 | HyperOS's defaults for the debug app's notifications | Phone | Floating (heads-up) and lock screen notifications on, sound on; the channel's vibration off. |
| N2 | Sound and vibration in each ring mode and in Do Not Disturb | Phone | Normal: sound. HyperOS's Silent mode: a pop-up, no sound. Vibrate mode: **a vibration** instead of the sound (Q7). Do Not Disturb: **sound**, no pop-up, with the *reminder* category, which HyperOS lets through; none of it without a category (Q6). |
| N3 | A repeat: an update with sound of a reminder in the shade; a silent update | Phone, emulator | The repeat sounds and pops up again; the silent post (`GROUP_ALERT_SUMMARY`) does nothing (Q8). |
| N4 | Two reminders posted together in the group | Phone, emulator | Both pop up; one sound (Q4). |
| N5 | A tap opens `MainActivity`: from the lock screen, and with the process ended | Phone | It opens; from the lock screen after the unlock. HyperOS doesn't block it. |
| N6 | Taken, Snooze and Skip reach the receiver: from the lock screen without unlocking | Phone | Yes, with the phone locked. |
| N7 | Swiping a reminder away | Phone | Android reports it (`REASON_CANCEL`); a silent repost brings it back, silently (Q2). |

Also found: HyperOS keeps at most 10 notifications per app (§4.3.4), and the app can't set a channel's lock-screen visibility (Q8).

**For P7's reminder check** (`RM-9`), on this phone: the notification permission is needed; `USE_EXACT_ALARM` is granted at install; battery optimisation and HyperOS's battery saver aren't needed, since alarm clocks were on time with both at their defaults; **Autostart** is needed so that reminders come back right after an app update; HyperOS's "Floating notifications" and "Lock screen notifications" for the app are on by default, and worth listing as steps to confirm.

A2, A8, N2 and N3 contradicted the SPEC, which changed first, with the user (`phase-docs`, "SPEC change during implementation"): Q5 to Q10.

## 5. Security and privacy

Plan §3 applies. Treatments are health data, and in this phase they reach the notification shade and the lock screen.

- **Permissions:** exactly the four of plan §3: `POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. P1's allowlist check keeps running in `build`, and still fails on `INTERNET`.
- **Components:** three receivers, none exported. The alarm and the actions use explicit, immutable `PendingIntent`s, each to a component of the app; the activity's too. A receiver checks the intent's action and decodes its key, and ignores anything else. No `android:process`.
- **Debug only:** `DebugActivity` is exported in debug builds, as a launcher activity must be (Q3). It is never in a release build: the handover's manifest comparison checks that the release manifest gains only the permissions and the three receivers.
- **The alarm clock** (Q5): the status bar shows an alarm icon, and quick settings and the lock screen may show the next alarm's time. Only the time, never what the reminder is for. Tapping it opens the app, on Home.
- **What the shade shows:** the medicine, the dose, the treatment's name and the instructions; never the notes. On the lock screen too, as agreed (`RM-11`, Q16 of the specification). Answering from the lock screen needs no unlock (P5-D7).
- **What is stored:** the reminder records, which hold only take keys and instants, in the same file as the rest (`files/datastore/pills.json`). Nothing is stored elsewhere, except debug builds' log.
- **Logs:** debug builds only, IDs and times only (§4.3.10). The log file stays in the app's private storage, covered by P1's backup exclusions. Exception messages are never logged: only class names.
- **Test data:** the §4.4 example and the debug screen's "Test" treatment, with made-up IDs. The repo is public: no real treatments, no phone serial number. Screenshots to publish come from the emulator only.
- **The phone:** measurements and device runs change its settings only with the user's agreement, and put them back, apart from the settings the user decides to keep for reminders.

## 6. Testing

Unit tests are JUnit Jupiter. `:core`'s run in `./gradlew build`; `:app`'s too (`testDebugUnitTest`), including `testDebug` ones. Instrumented tests are JUnit 4, run with `instrument` on the emulator and the phone.

| Test | Type | Covers |
|---|---|---|
| `:core` actions: `PillsData.snooze` on a Due take, refused on an answered one or one already snoozed; the repository helper inside `update`; `deleteTreatment` drops its medicines' records; `saveTreatment` drops a removed medicine's record and keeps the others | Unit (`:core`) | `RM-3`, `TR-9`, P5-D8 |
| `ReminderFormatTest`: records of both kinds read back unchanged; `pills-v1-reminders.json` decodes to its data and is written as it holds it; `pills-v1.json` still decodes, with no records, and is still written the same; data without records has no `reminders` field; records sorted; refused: both kinds, neither, `alerts` without `lastAlertAt`, `alerts` 0 or 5, an instant without seconds, with a fraction or with `+01:00`, two records for one take; the reason never holds content | Unit | P5-D1, `NF-4` |
| `TakeKeyCodecTest`: a key and back; refused: a missing part, a bad time, seconds | Unit | `RM-6`, §4.3.4 |
| `ReminderContentTest`: title and text with and without instructions; the due instant in Europe/Madrid; `null` for a removed medicine | Unit | `RM-1`, `TR-6` |
| `ReconcilerTest`, with `InMemoryTreatmentRepository`, a fixed clock, `FakeShade` and `FakeAlarm`: the §4.3.11 timeline; the order write, alarm, removals, posts; two reconciles at the same now: the second writes, posts and alerts nothing; a silent reminder isn't reposted unless missing or edited; an edited name is reposted silently; a reminder swiped away (gone from the fake shade) comes back silently; removals of a deleted treatment's and an ended treatment's reminders, and of a key the plan doesn't hold; the alarm cancelled when nothing is left; the summary's count | Unit | `D-7`, `RM-1`–`RM-5`, `RM-7`, `TR-9`, `TR-11`, P5-D3, P5-D5 |
| `ReconcilerTest`, failures: Unreadable changes nothing; a failed write applies nothing and sets the retry 5 minutes later; any other exception is reported by class only; a write that changes nothing still applies the plan | Unit | Q1, §4.3.1 |
| `RemindersTest`: `answer` Taken records now in whole minutes and removes the reminder; Skip; Snooze, and the reminder back 10 minutes later; a refused answer still reconciles; the observer reconciles on the first data and after a change made through the repository (an answer in the app removes the reminder); its own write leads to one more reconcile that writes nothing | Unit | `RM-3`, `RM-7`, `TK-2`, P5-D2 |
| `TestSchedulesTest` (debug): the test treatment's drafts validate with minutes allowed; their first take, interval and IDs | Unit (`testDebug`) | `D-15` |
| `ReminderChannelTest`: the channel exists with high importance, the default sound, no vibration and no Do Not Disturb bypass | Instrumented | `RM-2`, `RM-8`, `D-10` |
| `ReminderShadeTest`, with its own prefix and the permission granted through `UiAutomation`: a posted reminder's channel, no category, public visibility, group, group alert behaviour (children for an alert, summary for a silent post), time, title and text, three actions with their labels, and its content intent; `shown()` lists it and ignores other tags; `remove` removes it; a silent post of an unchanged reminder doesn't repost it; the summary; everything removed at the end | Instrumented | `RM-1`, `RM-3`, `RM-4`, `RM-11` |
| `WakeUpAlarmTest`, with its own identifier: `set` puts one alarm clock at the instant, with its show intent to `MainActivity`, in `dumpsys alarm`; setting again replaces it; `cancel` removes it | Instrumented | `D-7`, `D-8` |
| `OpenTakeTest`: `MainActivity` started with a reminder's content intent opens the Take screen; Back goes Home; a recreated activity doesn't open it again | Instrumented | `RM-6` |

### Manual checks

**Device runs on the phone** (T8), with the debug screen's test treatment. Each run records the time of each alert from the log file (its lateness), the Android and HyperOS versions, and the power settings: stock first, then relaxed as T1 and T2 found. `NF-1` passes when every alert is within 1 minute, with the settings relaxed.

| # | Steps | Expected | Covers |
|---|---|---|---|
| M1 | Two medicines due together, the app open, then closed | Both reminders alert at the planned minute, heads-up, with sound and no vibration, grouped; title "Test A: 1 tablet", text "Test"; then with instructions | `RM-1`, `RM-2`, `RM-4`, `TR-6` |
| M2 | Leave one unanswered | It sounds again at +10, +20 and +30 minutes, then stays silent, and goes when the take is Missed | `RM-5`, `RM-7`, `TK-3` |
| M3 | Taken, Skip and Snooze, from the shade and from the lock screen | Taken and Skip remove it, and the debug screen shows the answer, Taken with the minute it was pressed; Snooze removes it, and it alerts again 10 minutes later with a new round | `RM-3`, `TK-2` |
| M4 | Tap a reminder: app in front, in the background, process ended, phone locked | The app opens on the Take screen | `RM-6` |
| M5 | Look at a reminder on the lock screen | Its full content | `RM-11` |
| M6 | Do Not Disturb on; then HyperOS's Silent mode; then vibrate mode | Do Not Disturb: in the shade, no sound, no pop-up. Silent mode: a pop-up, no sound. Vibrate mode: a vibration instead of the sound, and a pop-up. Never a vibration in normal mode | `RM-8`, `RM-2` |
| M7 | Screen off, then forced deep Doze (`doze_on` right after a reminder more than 30 minutes ahead is set; `doze_state` deep IDLE), then a night in natural Doze, unplugged, with reminders hours apart (every 3 hours), checking the idling history for `deep-idle` | Every alert within 1 minute | `NF-1` |
| M8 | The user swipes the app away from Recents | The next reminder on time | `NF-1` |
| M9 | `end_process`, checked with `pidof` | `alarms` still lists the alarm; the next reminder on time | `NF-2` |
| M10 | Restart the phone before a take is due, unlock after it fell due | It alerts within a minute of the unlock (`BOOT_COMPLETED` came 8.7 s after it in T1); the alarm is set again (`alarms`) | `NF-1`, `NF-2`, `D-16` |
| M11 | `gradle installDebug` (an update) between two takes, with a reminder in the shade; with Autostart on, then off | The alarm survives; the next reminder on time. The reminder in the shade goes at the update, and comes back silently at once with Autostart on; with it off, at the next alarm or when the app is opened | `NF-2` |
| M12 | *Force stop* from Android's settings, then open the app | No alarm until it opens; then the alarm at once (`alarms`) | `NF-2` |
| M13 | End, then delete, a test treatment with a reminder in the shade (debug screen) | Its reminder goes at once; no alarm left for it | `TR-11`, `TR-9` |
| M14 | Between two reminders, and after every take is answered | `alarms` lists exactly one alarm clock, at the next wake-up, and the status bar shows its icon; none, and no icon, when nothing is left. Tapping the next alarm in quick settings opens the app. No service runs (`dumpsys activity services`) | `NF-5`, `D-7` |

**On the emulator** (API 36):

| # | Steps | Expected | Covers |
|---|---|---|---|
| E1 | `reminder_health`; a test treatment; its reminder; its actions; a restart | `USE_EXACT_ALARM` granted, exact alarms allowed; everything works as on the phone | `NF-6` |
| E2 | Change the time by hand (*Settings > System > Date & time*), with a reminder pending | The log shows `TIME_SET`; the alarm follows the plan | `NF-2` |
| E3 | Change the time zone by hand, with the app running and a reminder pending | The log shows `TIMEZONE_CHANGED`; the take keeps its clock time in the new zone; `CurrentZoneClock` follows (P2's pending check) | `NF-2`, `SC-4` |
| E4 | The notification and the debug screen, light and dark, at the largest font | Readable; the screen fits; screenshots for the handover, with the example data only | `NF-7`, `NF-10` |

Put every setting back as it was afterwards: automatic time and zone on the emulator; on the phone, Do Not Disturb, the ringer mode, Doze, and the HyperOS settings the user didn't choose to keep.

## 7. Tasks

| ID | Task |
|---|---|
| T1 | **Measure alarms and broadcasts** (§4.6, A1–A8): a throwaway probe in the debug source set; the phone with stock settings, then relaxed; the emulator for A1 and A6. Record the numbers in the handover. If anything contradicts the SPEC, stop and ask. Delete the probe. Needs the user for the swipe, the restart and the night unplugged. The night in natural deep Doze, with reminders hours apart, moved to T8 (M7). |
| T2 | **Measure notifications** (§4.6, N1–N7), the same way, on the phone and the emulator. Record HyperOS's defaults and the settings P7's check must name. Report T1 and T2 to the user, with what they mean for `D-8`, Q2 and Q4. |
| T3 | **Reminder state in the store:** `PillsData.reminders`; the format's `reminders` field, its mapping and checks, and `pills-v1-reminders.json`; `PillsData.snooze` and its repository helper; `deleteTreatment` and `saveTreatment` drop records (P5-D8). Unit tests in both modules. |
| T4 | **The reconcile on the JVM:** `Reconciler`, `ReminderShade`, `WakeUpAlarm`, `ReminderContent`, `TakeKeyCodec`, `ReminderLog`, and `Reminders` with its observer and `answer`. Unit tests with fakes. |
| T5 | **Android:** `ReminderChannel`, `AndroidReminderShade`, `AlarmClockWakeUp`, `ReminderIntents`, the three receivers, the manifest, `AppContainer`, `PillsReminderApp`, `MainActivity.onStart`, the debug log's Android side. Instrumented tests: channel, shade, alarm. A first reminder seen on the emulator, by a treatment saved from a throwaway test or adb. |
| T6 | **Debug tools:** `DebugActivity`, its screen and ViewModel, `TestSchedules`, the debug manifest and strings. Draw every state of the screen (`phone` skill, "Checking a screen in every state"), light and dark, at the largest font, on the phone's content area. `TestSchedulesTest`. |
| T7 | **Opening a take** (`RM-6`): the content intent, `MainActivity` and `AppNavHost`. `OpenTakeTest`. |
| T8 | **Device runs:** M1–M14 on the phone, stock settings then relaxed, and E1–E4 on the emulator. Every instrumented test passes on the emulator and the phone. Record everything in the handover. It may span a night. |
| T9 | **Wrap-up:** every row of §6 and every rule of §4.3 against the tests; KDoc on the public API; `CLAUDE.md` (the `reminders` package, the reconcile on every change, `PillsData.reminders`, the debug tools, test prefixes); the manifest comparison; `build`; the pull request with CI green. |

## 8. Done criteria

- [ ] All tasks done
- [ ] `./gradlew build` passes locally and in CI, with the unit test totals recorded in the handover
- [ ] Unit tests cover every row of §6, and every new test was shown able to fail (CLAUDE.md)
- [ ] Every instrumented test passes on the emulator and the phone
- [ ] T1 and T2's measurements are recorded, `D-8` is settled, and the handover lists the HyperOS settings P7's check must name
- [ ] The device runs M1–M14 pass on the phone with the settings relaxed, recorded with the Android and HyperOS versions, the settings, and each alert's lateness; the stock runs are recorded too
- [ ] E1–E4 pass on the emulator
- [ ] The merged release manifest adds exactly the four permissions and the three receivers, none exported, and nothing has `android:process`; the debug manifest adds only `DebugActivity`; no new library
- [ ] HANDOVER.md is up to date, with the pending wiring for P6, P7 and P8, and the plan's status table is updated

## 9. Open questions

None at the moment. New questions are numbered from Q11.

### Answered

The review confirmed the four draft assumptions: Q2 and Q4 went into specification v0.7 (`RM-4`, `RM-5`), and Q1 and Q3 into plan v0.4 (§2.4, §3). Q5 to Q10 came from T1 and T2's measurements: they went into plan v0.5 and v0.6 (`D-8`, `D-10`) and specification v0.8 (`RM-2`), which §2 quotes.

| # | Question | Decision |
|---|---|---|
| Q1 | What a reconcile does when it can't run: the stored data can't be read, or the write fails | **Unreadable:** nothing changes on the phone. Reminders already in the shade stay, and their actions do nothing; no new alarm is set. P7's home screen says the data can't be read. **A failed write:** nothing is applied, and an alarm 5 minutes later tries again, for as long as it fails. (Plan §2.4, §4.3.1) |
| Q2 | What swiping a reminder away does | Nothing to the take: it isn't answered or snoozed, and stays Due. The reminder comes back at the next reconcile: with sound if a repeat is due then, silently otherwise. (`RM-5`, §4.3.4) |
| Q3 | How to open the debug screen in debug builds | A second launcher icon, "Pills debug", for a `DebugActivity` declared only in the debug manifest, and exported there, as launcher activities are. The release build has nothing of it. (Plan §3, §4.3.9) |
| Q4 | Two reminders that alert at the same moment | Both appear, each in the group, each posted with sound; Android may play one sound or two, and either is fine. T2 records what the phone does. (`RM-4`, §4.3.4) |
| Q5 | Found in T1: with the screen off and HyperOS's stock settings, `setExactAndAllowWhileIdle()` alarms were held until a 5-minute grid on `elapsedRealtime`, up to 4 min 15 s late, outside Doze too, and still with the battery saver at "No restrictions". Three `setAlarmClock()` alarms in the same conditions were 45–53 ms late. | The alarm is an alarm clock, `D-8`'s fallback, which PQ-2 accepted with its status-bar icon. The user agreed on 2026-11-20. Plan v0.5 (`D-8`); §4.3.3, §4.6, §5, §6. The rest of T1 measures alarm clocks. |
| Q6 | Found in T2: HyperOS's Do Not Disturb lets notifications of the *reminder* category sound (`allowReminders=true`), and its settings show no list of exceptions to change it. Without a category, a reminder made no sound and didn't pop up in Do Not Disturb, and stayed in the shade. | The notifications have **no category**, so they are silent in Do Not Disturb, as `RM-8` says. Decided by the user on 2026-11-21. Plan v0.6 (`D-10`); §4.3.4, §6. |
| Q7 | Found in T2: in vibrate mode, Android vibrates instead of playing a notification's sound when its channel has no vibration of its own. A channel vibration of 1 ms didn't stop it: the user still felt one. `RM-2` and the specification's Q20 said reminders never vibrate. | **In vibrate mode a reminder vibrates instead of sounding,** as Android does; it still pops up. Decided by the user on 2026-11-21. Specification v0.8 (`RM-2`, Q20); §4.3.4, §6 (M6). |
| Q8 | Found in T2: the platform has no `Notification.Builder.setSilent()` (it is AndroidX's `NotificationCompat`), and Android ignores an app's channel lock-screen visibility (it stays `VISIBILITY_NO_OVERRIDE`). | A silent post is a group child with `GROUP_ALERT_SUMMARY`, as `NotificationCompat` does it: no sound, no vibration, no pop-up (T2). Each notification is `VISIBILITY_PUBLIC`, which the lock screen showed in full. §4.3.4, P5-D4, P5-D6, §6. |
| Q9 | Found in T1: after a restart, `BOOT_COMPLETED` came 59 s after the unlock, behind 215 other receivers. | `android:priority="999"` on the receiver's intent filter: 8.7 s after the unlock. §4.3.2, §4.3.6. |
| Q10 | Found in T1 and T2: an update removes the app's notifications, and with Autostart off HyperOS blocks `MY_PACKAGE_REPLACED` ("not permitted to auto start"). The alarm survives the update. | Nothing in the app changes: `PACKAGE_REPLACED` posts the reminders again when it arrives, and the next alarm or opening the app does otherwise. Autostart goes on P7's list. §4.3.2, §4.3.3, §4.6, §6 (M11). |

## 10. Change log

| Version | Date | Changes |
|---|---|---|
| 0.1 | 2026-11-20 | First draft |
| 0.2 | 2026-11-20 | Review: Q1–Q4 answered, confirming the draft assumptions. Q2 and Q4 went into specification v0.7 (`RM-4`, `RM-5`), and Q1 and Q3 into plan v0.4 (§2.4, §3); §2 quotes them. Ready. |
| 0.3 | 2026-11-20 | From T1 (Q5): the alarm is an alarm clock, `setAlarmClock()`, because HyperOS held exact alarms for up to 5 minutes with the screen off. Plan v0.5 (`D-8`), which §2.1 quotes. §1, §2 (`RM-2`, `NF-1`), §3, §4.1–§4.3.3 (`AlarmClockWakeUp`), §4.6 (A1, A2, A8 results), §5 (the status-bar icon), §6 (`WakeUpAlarmTest`, M14). |
| 0.4 | 2026-11-21 | From T1 and T2 (Q6–Q10), with the user. No category, so Do Not Disturb silences reminders (Q6; plan v0.6, `D-10`). In vibrate mode a reminder vibrates (Q7; specification v0.8, `RM-2`, which §2 quotes). Silent posts by group alert behaviour, and public visibility on each notification, not the channel (Q8). The boot receiver at priority 999 (Q9). Updates remove the notifications, and HyperOS blocks `MY_PACKAGE_REPLACED` with Autostart off (Q10). §4.6 holds the results and P7's list; M6, M7, M10, M11; T1's natural deep-Doze night moved to T8. |
