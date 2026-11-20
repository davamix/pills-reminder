# P4 — Reminder planner: Specification

| | |
|---|---|
| **Status** | Ready, v0.2 |
| **Phase** | P4, see [Implementation plan §5](../../IMPLEMENTATION_PLAN.md#5-phases) |
| **Depends on** | P2 |
| **Branch** | `phase/p4-reminder-planner` |
| **Handover** | [HANDOVER.md](HANDOVER.md) |

This document holds everything needed to implement the phase. Working on it shouldn't require reading the full specification or plan.

## 1. Goal

A pure Kotlin `ReminderPlanner` in `:core`. Given the stored data, the reminder state and the time now, it works out which reminders the notification shade should hold, which of them alert now, and when the app must wake up next. It also defines the reminder state the store must remember, snoozes and the alerts played, so that a reconcile at any moment never alerts twice and never skips an alert. P5 then only has to apply the plan on Android.

## 2. Requirements covered

The text of each requirement is quoted from [SPECIFICATION.md](../../SPECIFICATION.md) (version v0.6), so this document stands on its own.

| ID | Requirement (quoted) | What this phase does for it |
|---|---|---|
| RM-3 | "A reminder has three actions: **Taken**, **Snooze** and **Skip**. Taken and Skip record the answer (`TK-2`) and remove the reminder. Snooze removes it, and alerts again 10 minutes later. From then on it repeats as in `RM-5`, every 10 minutes, up to 3 times, and it can be snoozed again." | `snooze()` takes the reminder out of the shade. 10 minutes later a new round of alerts starts: an alert, then up to 3 repeats (§4.3.4). Taken and Skip are P2's `markTaken` and `skip`; the planner then leaves the answered take out of the shade (§4.3.5). |
| RM-4 | "Each take has its own reminder, so takes due at the same time are answered separately. Android groups them in the notification shade." | One reminder per Due take, each with its own alerts and record. Takes that fall due at the same moment all alert in the same plan. The grouping is P5's. |
| RM-5 | "A reminder that isn't answered stays in the notification shade, and alerts again every 10 minutes, up to 3 times. After that it stays there silently, until it is answered or the take becomes Missed (`TK-3`). Each repeat comes 10 minutes after the alert before it, so a reminder whose first alert came late, for example after the phone restarted, still repeats 3 times." | Rounds of alerts: the first alert, then up to 3 repeats, each 10 minutes after the alert that played before it, so 4 alerts at most. Then the reminder stays in the plan, silent, until the take is answered or Missed (§4.3.3). |
| RM-7 | "When a take is answered in the app, or becomes Missed, its reminder is removed from the shade." | An answered or Missed take is never in the plan, so the reconcile removes its reminder (§4.3.5). The next wake-up includes the moment the take becomes Missed (§4.3.6). |
| TK-3 | "A take still unanswered one interval after its planned time becomes Missed. With an interval of 8 h, the 08:00 take becomes Missed at 16:00; with an interval of a week, a week later. After an edit (`TR-10`), it becomes Missed as soon as the medicine's next take is due, if that comes sooner, so a medicine never has more than one Due take." | The moment: each Due take's Missed time is a wake-up, so its reminder goes then. The rule itself is P2's (`missedAt`, `nextStateChange`). |
| NF-1 | "**On time:** a reminder alerts within 1 minute of its planned time, also when the phone is idle (Doze), the screen is off, the app was swiped away from Recents, or the phone has restarted, as long as the reminder check (`RM-9`) passes. This is checked on the phone, not only on the emulator." | The timing side: the next wake-up is the exact instant a take falls due, a repeat is due or a snooze ends, with no rounding (§4.3.6). The alarm and the checks on the phone are P5's and P8's. |
| TK-4 | "A medicine's next take can be marked Taken before it is due, from the app, for example before going out. Its reminder is then cancelled. The next take is the medicine's Due take, if it has one, or else its first unanswered take still to come. Only one take of a medicine can be marked ahead of its time: the one after it can be marked early once the marked take's planned time has passed." | The planner side of "its reminder is then cancelled": a take answered early never falls due, so it never alerts, and the next wake-up skips it. |
| TK-5 | "The last answered take of each medicine can be changed later, for example after pressing Taken by mistake: to Taken, to Skipped, or back to unanswered. The Treatment screen shows it. Older takes can't be seen or changed. A take set back to unanswered that is Due again gets its reminder back, which alerts at once, as when the take fell due." | The planner side of the last sentence: a take that is Due again has no record, so it is back in the plan and alerts at once, with a new round (§4.3.5). The rest is P2's and P6's. |
| TR-9 | "Treatments can be created, edited and deleted in the app. Deleting asks for confirmation, and also deletes every take recorded for the treatment." | The planner side: a deleted treatment's takes are gone, so their reminders leave the shade and their records are dropped (§4.3.5). |
| TR-10 | "Editing a treatment that has already started changes only what is still to come. Takes that are due or answered keep their state; takes not yet due are worked out again from the new values." | The planner side: a Due take kept by an edit keeps its reminder and its record, with no new alert. Its Missed moment may come sooner, and the next wake-up follows (§4.3.5). |
| TR-11 | "A treatment can be ended early, for example when the doctor stops it. Its takes not yet due are removed and their reminders cancelled, and it shows as Finished. A take that is due at that moment becomes Missed, and its reminder is removed. A take already marked Taken early (`TK-4`) keeps its answer." | The planner side: an ended treatment has no Due take, so its reminders leave the shade, and nothing of it wakes the app again (§4.3.5). |

The removal of notifications on the device for `TR-9` and `TR-11` stays with P5 (plan §5.3).

### 2.1 Specification terms and example, quoted

From the glossary (§4.1):

| Term | Meaning |
|---|---|
| **Reminder** | The phone's alert for a take that is due. |
| **Due** | A take whose time has come, and which hasn't been answered yet. |
| **Snooze** | Asking a reminder to alert again a few minutes later. |

From the example (§4.4): "Treatment "Example", started on Monday 23 November 2026: Amoxicillin: 1 capsule every 8 h for 7 days, first take Monday 08:00. Atrovent: 3 puffs every 8 h for 5 days, first take Monday 08:00." Amoxicillin has 21 takes, the last on Mon 30 Nov at 00:00, and Atrovent 15, the last on Sat 28 Nov at 00:00, each at 08:00, 16:00 and 00:00. "At 16:00 two reminders alert, "Amoxicillin: 1 capsule" and "Atrovent: 3 puffs", and each is answered on its own (`RM-4`)."

From the state diagram (§4.3): a Due take stays Due when snoozed (`RM-3`), and leaves Due when Taken, Skipped, or Missed ("no answer in time (TK-3), or ended (TR-11)").

### 2.2 The plan's design, quoted

From plan §2.3, "How a reminder works":

> 2. **The reminder planner** (`:core`, P4) is a pure function of the stored data and the time now. It returns a plan:
>    - the takes whose notifications should be in the shade: those Due and not snoozed;
>    - which of them must **alert** now (a sound), because a take has just become due, a repeat is due (`RM-5`), or a snooze has ended (`RM-3`). The others are shown or updated silently;
>    - the notifications to remove: answered, Missed (`TK-3`), or no longer in any treatment;
>    - the **next wake-up**: the earliest moment in the future when the plan would change.
> 3. **The reconcile** (`:app`, P5) applies the plan:
>    - it posts and removes notifications;
>    - it records which alerts have played, so a reconcile a minute later doesn't alert again;
>    - it replaces the single alarm with one at the next wake-up (`D-7`).
>    Reconciles run one at a time, behind a lock in the app's process.

> Because the planner is pure Kotlin, a test can run a whole treatment, minute by minute, over simulated days, and check every alert, repeat and removal. That includes reconciles at random moments in between.

The decisions this phase serves:

- **`D-7`:** "**One alarm at a time.** The app keeps a single exact alarm, set for the next moment something must happen: a take becomes due, a reminder alerts again, a snooze ends, or a take becomes Missed. When it fires, and after every change, a **reconcile** works out what the notification shade should show now, posts or removes notifications, and sets the next alarm." Its reason: "One idempotent path handles every trigger: the alarm, a notification action, a restart, an edit, opening the app. Running it twice changes nothing."
- **`D-9`:** the reconcile also runs after a restart, an app update, a change of time or time zone, and when the app starts.
- **`D-16`:** "After a restart, reminders start again once the phone has been unlocked for the first time. A reminder that came due in between alerts as soon as the phone is unlocked."
- **Plan §2.4:** the store holds "the snoozes, and which alerts have played. P4 defines them, and P5 adds them to P3's format (P3 Q3)."

## 3. Scope

### In scope

- **Reminder state:** the record kept for each Due take that has alerted or is snoozed (`ReminderState`, `TakeReminder`), and the snooze action on it (`RM-3`).
- **`ReminderPlanner`:** from the data, the reminder state and now, the plan: the reminders the shade should hold, which of them alert, the next wake-up, and the reminder state to store once the plan is applied. Plus the removals, given what is in the shade.
- **Rules:** rounds of alerts and repeats (`RM-5`), snoozes (`RM-3`), one reminder per take (`RM-4`), removal (`RM-7`, `TK-3`, and the planner side of `TK-4`, `TR-9`–`TR-11`), and an exact next wake-up (`NF-1`).
- **Time:** daylight saving changes, a change of time zone, and the clock set back.
- **Tests:** unit tests of every rule, and simulated walks: the §4.4 example over several days, and a weekly medicine with no end over several weeks, with reconciles at random moments in between, late alarms, and a fake notification shade.

### Out of scope

| What | Phase |
|---|---|
| Adding the reminder state to `PillsData` and to the JSON format, with its golden test; the repository helper that snoozes | P5 (P4-D6) |
| The reconcile itself: its lock, running the plan inside the store's `update`, posting and removing notifications, the alarm | P5 |
| Notifications: the channel, the sound, the content, grouping, the actions, what tapping one does, what swiping one away does | P5 |
| Whether two reminders that alert at the same moment play one sound or two | P5 |
| What a reconcile does when the stored data can't be read (`StoredData.Unreadable`) | P5 |
| Receivers, `BOOT_COMPLETED`, the first unlock (`D-16`), the debug screen and test schedules (`D-15`) | P5 |
| `NF-1` and `NF-2` on the phone: Doze, restarts, HyperOS | P5, verified in P8 |
| The home screen's refresh each minute (it uses P2's `nextStateChange`) | P7 |

## 4. Design

### 4.1 Packages and files owned

P4 owns the package `io.github.davamix.pillsreminder.core.reminders`, which P2-D1 kept for it. The file names are suggestions; the package and the public API in §4.2 are what P5 relies on.

```
core/src/main/kotlin/io/github/davamix/pillsreminder/core/reminders/
├── ReminderState.kt      ReminderState, TakeReminder, snooze()
└── ReminderPlanner.kt    ReminderPlanner, ReminderPlan, Reminder
core/src/test/kotlin/io/github/davamix/pillsreminder/core/reminders/
├── ReminderStateTest.kt
├── ReminderPlannerTest.kt
├── ReminderWalkTest.kt
└── FakePhone.kt          the walks' harness: the store, the shade, the alarm, and the oracle (§6)
```

**Shared files changed:**

- `CLAUDE.md`, under "Code structure": the `reminders` package and its entry points, in place of "P4 adds `reminders`".

Nothing in `:app` changes. `PillsData` doesn't change either (P4-D6).

### 4.2 Interfaces and data

The names are suggestions; the shapes and the rules are what matter.

```kotlin
package io.github.davamix.pillsreminder.core.reminders

// ReminderState.kt

/**
 * What the store remembers about reminders (plan §2.4): a record for each Due take that has alerted or is snoozed.
 * Pending, answered and Missed takes have none. P5 adds it to PillsData and to the JSON format (P4-D6).
 */
data class ReminderState(
    val takes: Map<TakeKey, TakeReminder> = emptyMap(),
)

/** A Due take's reminder record. Its times are instants in whole seconds (P4-D5). */
sealed interface TakeReminder {
    /** Snoozed (RM-3): out of the shade until [until], when a new round of alerts starts. */
    data class Snoozed(
        val until: Instant,
    ) : TakeReminder

    /** In the shade, having alerted [count] times in its current round (1 to 4), the last at [lastAt] (RM-5). */
    data class Alerted(
        val count: Int,
        val lastAt: Instant,
    ) : TakeReminder
}

/**
 * Snoozes the reminder of the take with this key (RM-3): it leaves the shade, and a new round of alerts starts
 * 10 minutes after now. `null`, changing nothing, when the take isn't Due at [now], or is already snoozed.
 */
fun ReminderState.snooze(
    data: PillsData,
    key: TakeKey,
    now: ZonedDateTime,
): ReminderState?

// ReminderPlanner.kt

/** Works out what the notification shade should hold at a given moment (plan §2.3, D-7). Pure: no clock, no state. */
object ReminderPlanner {
    /** How long a snooze lasts (RM-3). */
    val SNOOZE: Duration = Duration.ofMinutes(10)

    /** The time between two alerts of a round (RM-5). */
    val REPEAT_AFTER: Duration = Duration.ofMinutes(10)

    /** The alerts in a round: the first, then up to 3 repeats (RM-5). */
    const val ALERTS_PER_ROUND: Int = 4

    /** The plan at [now], from the stored data and reminder state (§4.3). */
    fun plan(
        data: PillsData,
        reminders: ReminderState,
        now: ZonedDateTime,
    ): ReminderPlan
}

/** What the shade should hold at a moment, and what to do next (plan §2.3). */
data class ReminderPlan(
    /** One per Due take that isn't snoozed, in order of planned time, then of treatments and medicines in the data. */
    val reminders: List<Reminder>,
    /** The earliest instant after now at which the plan would change by itself; `null` when nothing is left to wake for. */
    val nextWakeUp: Instant?,
    /** The reminder state to store once the plan is applied: its alerts recorded, records no longer needed dropped. */
    val recorded: ReminderState,
) {
    /** The takes whose reminders alert now. */
    val alerts: List<TakeKey> get() = reminders.filter { it.alert }.map { it.key }

    /** Of the reminders in the shade now, those that must go: every one this plan doesn't hold (P4-D2). */
    fun removals(inShade: Set<TakeKey>): Set<TakeKey> = inShade - reminders.map { it.key }.toSet()
}

/** A reminder the shade should hold. [alert]: post it with sound now; otherwise post or update it silently. */
data class Reminder(
    val key: TakeKey,
    val alert: Boolean,
)
```

- `TakeReminder.Alerted` refuses a `count` outside 1 to `ALERTS_PER_ROUND` with an `IllegalArgumentException` whose message gives only the number.
- From P2, the planner uses `PillsData.takesOf(id)`, `MedicineTakes.nextTake(now)` (the Due take, when it has one), `MedicineTakes.nextStateChange(now)` and `Take.state`. `snooze()` uses `PillsData.take(key, now)`.

### 4.3 Behaviour

#### 4.3.1 Inputs, and the time now

- **Pure:** `plan()` and `snooze()` read no clock and keep no state. `now` is a `ZonedDateTime`, read once by the caller (P2-D3).
- **Instants for comparisons.** Planned times are local date-times; a take is Due once `plannedAt.dueAt(now.zone) <= now.toInstant()`, which P2's take states already do. The planner compares its records' instants with `now.toInstant()`.
- **Whole seconds.** A time written into a record is now, truncated to whole seconds: `now.toInstant().truncatedTo(ChronoUnit.SECONDS)`, called the *record time* below (P4-D5). A snooze ends at the record time plus 10 minutes, and a repeat is due 10 minutes after the record time of the last alert.

#### 4.3.2 Which reminders the shade holds

For each medicine of each treatment, in the order of the data, the planner takes its Due take, if it has one (`nextTake(now)` with state `DUE`). P2 guarantees at most one per medicine.

- A Due take is **in the plan** unless its record is `Snoozed` with `until` after now.
- Every other take is out: Pending, Taken, Skipped, Missed, or gone with its treatment or medicine.
- The plan's list is the whole shade. Whatever the shade holds beyond it must go (`removals`, P4-D2).

#### 4.3.3 Alerts, rounds and repeats

A **round** of alerts starts when a take falls due, and again each time a snooze ends. A round has at most 4 alerts: the first, then up to 3 repeats, each 10 minutes after the alert before it (`RM-5`). After the fourth, the reminder stays in the shade silently until the take is answered or Missed.

For each Due take, by its record (after the clock check of §4.3.8):

| Record before | In the plan | Alerts now | Record after |
|---|---|---|---|
| None | Yes | Yes | `Alerted(1, record time)` |
| `Snoozed(until)`, `until` after now | No | No | Unchanged |
| `Snoozed(until)`, `until` at or before now | Yes | Yes | `Alerted(1, record time)` |
| `Alerted(count, lastAt)`, `count` < 4 and `lastAt + 10 min` at or before now | Yes | Yes | `Alerted(count + 1, record time)` |
| `Alerted(count, lastAt)`, otherwise | Yes | No | Unchanged |

Takes that aren't Due get no record: the planner drops theirs (P4-D4).

- **Repeats count from the alert that played** (Q2): an alert that comes late, because the alarm was delayed or the phone was off, still gets its 3 repeats, 10 minutes apart from it.
- **Several alerts missed at once:** however long since the last reconcile, a plan alerts a take at most once. A reconcile that comes 25 minutes after a take fell due alerts once, then repeats from there. Alerts never pile up.
- **The first reconcile after a restart** (`D-16`): a take that fell due while the phone was off or locked has no record, so it alerts at once. One that had alerted before keeps its record, which the store kept, so it comes back silently unless a repeat is due.
- **A take already Missed at the first reconcile after it fell due** never alerts: if the phone was off from before the take fell due until after its Missed time, it never enters the shade.

#### 4.3.4 Snooze

`snooze(data, key, now)` (`RM-3`):

- **Allowed** when the take is Due at now, and its record isn't `Snoozed` with `until` after now. That includes a reminder that has finished its round and is silent: snoozing it starts a new round later.
- **Effect:** its record becomes `Snoozed(record time + 10 min)`. The next plan leaves it out of the shade, and wakes up at `until`. At `until`, a new round starts, with an alert (Q1).
- **Refused** (`null`, nothing changes) when the take doesn't exist, isn't Due (answered, Missed, Pending, or gone), or is already snoozed. A second tap on Snooze can't stretch the snooze.
- **A snooze that would end after the take is Missed** is allowed; the take is Missed first, and its reminder doesn't come back. The medicine's next take has its own reminder.
- Snoozing doesn't change the take's state: it stays Due (P2, §4.3.3).

#### 4.3.5 Removal

A reminder leaves the plan, so the reconcile removes it, and its record is dropped, when its take:

| Becomes | Why | Requirement |
|---|---|---|
| Taken or Skipped, from the reminder or in the app | answered | `RM-3`, `RM-7` |
| Missed | one interval after its planned time, or at the medicine's next take after an edit | `RM-7`, `TK-3` |
| Missed, at once | its treatment was ended | `TR-11` |
| Gone | its treatment was deleted, or its medicine removed (P2-D8) | `TR-9` |
| Pending again | the time zone moved west (§4.3.8) | `SC-4` |

- **A snoozed reminder** also leaves the shade, but keeps its record, and comes back when the snooze ends (§4.3.4).
- **An edit** (`TR-10`) keeps a Due take as it is (P2's cut-off is at least now), so its reminder stays, with its record and without a new alert. P5 updates its text silently if the name or dose changed. Its Missed moment may come sooner, and the next wake-up follows.
- **A take marked Taken early** (`TK-4`) never falls due, so it never alerts.
- **Undoing an answer** (`TK-5`) while the take is still within its interval makes it Due again, with no record, so it alerts at once, as a take that has just fallen due (Q3).

#### 4.3.6 The next wake-up

`nextWakeUp` is the earliest instant **after now** among:

- each medicine's `nextStateChange(now)` (P2): its first unanswered take still to come falls due, or its Due take becomes Missed;
- for each record after the plan, `Snoozed(until)`: `until`;
- for each record after the plan, `Alerted(count, lastAt)` with `count` < 4: `lastAt + 10 min`.

It is `null` when none of these exists: no treatment, or every take answered or Missed.

- **Exact** (`NF-1`): a take falls due at `plannedAt.dueAt(now.zone)`, to the millisecond, with no rounding. A reconcile at that instant finds the take Due and alerts.
- **Nothing in between:** for any moment after now and before `nextWakeUp`, with no change to the data, a plan on `recorded` holds the same reminders, alerts nothing, gives the same `nextWakeUp`, and records the same state. So a reconcile at a random moment changes nothing, and running one twice changes nothing (`D-7`).
- A repeat or a snooze end at or after the take's Missed time is harmless: the Missed time comes first, and the plan then has no record for that take.

#### 4.3.7 How P5 applies a plan

P5 owns the reconcile; this is what the planner is built for.

1. Read now once. Inside the store's `update`, on the latest data, work out the plan and store `recorded` as the reminder state. The transform may run again, so keep the plan of its last run.
2. Once the write has succeeded: remove `removals(keys of the app's notifications in the shade)`, post every reminder in the plan (with sound when `alert` is true, silently otherwise), and set the single alarm at `nextWakeUp`, or cancel it when it is `null`.

A plan applied in full never alerts twice and never skips an alert. A reconcile cut off halfway, by the process ending between steps, is P5's to handle.

#### 4.3.8 Time changes

- **Daylight saving.** Planned times stay clock times, so a take alerts at its planned clock time (`SC-4`). In the gap, a take planned at 02:30 on 29 March 2026 falls due, and alerts, at 03:30. Repeats and snoozes are 10 real minutes: on 25 October 2026, a take that falls due at 02:30 CEST repeats at 02:40 and 02:50 CEST, and then at 02:00 CET, the clock having gone back.
- **A change of time zone** keeps the planned clock times, and records keep their instants (P4-D5), so a snooze or a repeat still comes 10 real minutes later.
  - Moving east, takes fall due sooner on the real clock. A Due take may become Missed at once, and leaves the shade.
  - Moving west, a Due take can turn Pending again until the local clock catches up (P2). Its reminder leaves the shade and its record goes. When it falls due again on the new clock, it alerts again, with a new round.
- **The clock set back,** by hand (`TIME_SET`, `D-9`): records can then lie in the future. Before the table of §4.3.3, the planner pulls them back, so that no snooze or repeat waits more than 10 minutes:
  - `Snoozed(until)` with `until` later than the record time plus 10 minutes becomes `Snoozed(record time + 10 min)`;
  - `Alerted(count, lastAt)` with `lastAt` after now becomes `Alerted(count, record time)`.
  - The change is part of `recorded`, so it is stored at the next reconcile.
- **The clock set forward** needs nothing: snoozes and repeats fall due at once, and alert once each.

#### 4.3.9 Example

The §4.4 example on Monday 23 November 2026, in Europe/Madrid, with no answers to start with. A reconcile runs at each alarm and after each action.

| Time | Event | Shade after | Alerts | Amoxicillin's record | Next wake-up |
|---|---|---|---|---|---|
| 07:00 | The app opens | — | — | — | 08:00 |
| 08:00 | Alarm: both 08:00 takes fall due | Amoxicillin, Atrovent | Both (`RM-4`) | `Alerted(1, 08:00)` | 08:10 |
| 08:02 | Atrovent: Taken, from its reminder | Amoxicillin | — | Unchanged | 08:10 |
| 08:10 | Alarm: repeat | Amoxicillin | Amoxicillin | `Alerted(2, 08:10)` | 08:20 |
| 08:13 | The app opens | Amoxicillin | — | Unchanged | 08:20 |
| 08:20 | Alarm: repeat | Amoxicillin | Amoxicillin | `Alerted(3, 08:20)` | 08:30 |
| 08:30 | Alarm: last repeat | Amoxicillin | Amoxicillin | `Alerted(4, 08:30)` | 16:00 |
| 08:45 | Amoxicillin: Snooze | — | — | `Snoozed(08:55)` | 08:55 |
| 08:55 | Alarm: the snooze ends, a new round | Amoxicillin | Amoxicillin | `Alerted(1, 08:55)` | 09:05 |
| 09:05–09:25 | Alarms: three repeats | Amoxicillin | Amoxicillin, each time | `Alerted(4, 09:25)` at the end | 09:15, 09:25, then 16:00 |
| 16:00 | Alarm: the 08:00 take is Missed; both 16:00 takes fall due | Amoxicillin 16:00, Atrovent 16:00 | Both | The 08:00 record dropped; `Alerted(1, 16:00)` for the 16:00 take | 16:10 |

If the alarm at 08:00 had been delayed until 08:25 by Doze, Amoxicillin would have alerted at 08:25, then at 08:35, 08:45 and 08:55 (Q2).

#### 4.3.10 Properties

Unit tests check these at every step of the walks:

- Every reminder in a plan is a Due take, and every Due take that isn't snoozed is in it.
- `recorded` holds records only for Due takes, at most one per medicine.
- A take alerts only while Due, at most 4 times in a round, and never twice within 10 minutes of the same round.
- `nextWakeUp` is after now. A plan at any moment before it, with no change to the data, changes nothing (§4.3.6), and a plan on its own `recorded` at the same now alerts nothing and records the same state.
- With exact alarms, every alert of the oracle (§6) plays once, at its instant, and every reminder leaves the shade at the first reconcile at or after its take stops being Due.

### 4.4 Dependencies

- **Libraries:** none new. `:core` keeps `kotlinx-coroutines-core` as `api` and its test libraries; `java.time` for `Instant`, `Duration` and `ChronoUnit`.
- **`:core` must not depend** on Android, nor on kotlinx-serialization (plan `D-2`, P2-D2).
- **From P2 (merged):**
  - the model: `PillsData`, `TakeKey`, `MedicineId`, `Treatment`, `Medicine`;
  - the schedule: `PillsData.takesOf(id)`, `PillsData.take(key, now)`, `MedicineTakes.nextTake(now)`, `nextStateChange(now)`, `Take.state`, `Take.missedAt`, `dueAt(zone)`;
  - in tests, the actions to answer, edit, end and delete (`markTaken`, `skip`, `clearAnswer`, `saveTreatment`, `endTreatment`, `deleteTreatment`), and the test helpers `ExampleData.kt` (`Example`, `nov()`, `at()`, `nowAt()`, `MADRID`, `schedule()`) and `Invariants.kt` (`assertInvariants`).

**From earlier phases** (their handovers):

- **P2's pending wiring for P4:** "`PillsData` holds no reminder state. P4 defines the snoozes and the alerts played, and how they are stored; plan §2.4 keeps them in the same document as the treatments." P4 defines them here (§4.2, P4-D5, P4-D6) and leaves their place in `PillsData` and the format to P5, with a suggested shape (§4.5).
- **P2's guarantees the planner relies on:** at most one Due take per medicine; a snoozed take stays Due; `missedAt` is the earliest of one interval after the take, the medicine's next take and the treatment's end; `nextStateChange` is exact and skips answered takes. P2-D8: "Removing a medicine from a treatment deletes it and its answers. … P4 removes any reminder it had."
- **P2, time zones:** after a change of zone westwards, a Due take can turn Pending again, and a Missed one Due, until the local clock catches up. Answers don't change. §4.3.8 says what that does to reminders.
- **P2, debug intervals in minutes:** a take in a daylight saving gap can fall due at the same moment as the next take, or after it. Such a take is Missed as soon as it is due and never alerts. Intervals of an hour or more keep their order in Europe/Madrid.
- **P3's rule for the format** (CLAUDE.md): "A field added to `PillsData`, or to a type in it, goes into the format in the same change, with a test. The store refuses to write data that doesn't read back the same (P3-D3), so a field left out fails at the first write that sets it." That is why P4 keeps the reminder state out of `PillsData` (P4-D6).
- **P3's pending wiring for P5,** which the plan names here: the format's version 1 has no reminder state, and P5 adds it following P3's §4.3.6. A new optional field that version-1 files still decode as keeps version 1, with a golden test of the new field.

**Pitfalls that apply here** (from CLAUDE.md and P2):

- **Test names can't contain `:`** (nor `.` `;` `/` `\` `[` `]` `<` `>`). Write times as `0800` or `16h00`: `` `the 0800 reminder repeats at 0810, 0820 and 0830` ``.
- **Model classes' `toString()` includes treatment content.** Never put a `Treatment` or `Medicine` into an exception message or an assertion message; name IDs and times.
- **Random moments are seeded:** `kotlin.random.Random(seed)`, with the seed in every assertion message, so a failure can be run again.
- **ktlint:** run `./gradlew ktlintFormat` as its own command, before `build`. Wrap by hand any long line it leaves half-wrapped. The reports are in `core/build/reports/ktlint/`.
- **Exit codes:** write Gradle's output to a file and filter that (CLAUDE.md); never pipe Gradle into `grep` before chaining the next step.
- **`./gradlew build` runs `:core:test`.** The test totals one-liner in CLAUDE.md includes `core/build/test-results/test/`.

### 4.5 Decisions made in this phase

| ID | Decision | Reason |
|---|---|---|
| P4-D1 | The package is `io.github.davamix.pillsreminder.core.reminders`. `ReminderPlanner` is an `object` with one pure function, `plan()`. | P2-D1 kept the package for P4. Nothing in the planner needs state or an instance. |
| P4-D2 | The plan lists every reminder the shade should hold. The removals are whatever the shade holds beyond them, given its keys (`removals(inShade)`). | A reminder of a deleted treatment, a removed medicine or a lost record can't be listed from the data, because its take is gone. Making the shade equal the plan removes them all, keeps the reconcile idempotent (`D-7`), and still gives plan §2.3 its list of removals. |
| P4-D3 | Repeats are counted per round, from the alert that played (`Alerted(count, lastAt)`). A snooze starts a new round when it ends. | A delayed first alert still gets its 3 repeats, and alerts never pile up after a gap (Q1, Q2). |
| P4-D4 | Only Due takes have records, at most one per medicine. The planner drops a record once its take is answered, Missed, Pending again or gone. | The state stays small, and nothing has to clean it up but the planner. Deleting a treatment or removing a medicine needs no change to P2's actions. |
| P4-D5 | The records' times are instants in whole seconds, not local date-times. | They measure real time, 10 minutes. A change of zone or of daylight saving mustn't stretch or shorten a snooze. Whole seconds give the format one form, as whole minutes do for local times (P2-D3), and are far finer than `NF-1`'s minute. |
| P4-D6 | `ReminderState` is its own type, passed to the planner beside `PillsData`. P5 adds it to `PillsData`, to the format and to the repository helpers, in one change. Suggested shape: `PillsData.reminders: ReminderState = ReminderState()`, and in JSON a list sorted like the answers: `{ "medicineId", "plannedAt", "snoozedUntil" }` or `{ "medicineId", "plannedAt", "alerts", "lastAlertAt" }`, times as `2026-11-23T07:00:00Z`. | CLAUDE.md requires a field of `PillsData` to go into the format in the same change, and the plan gives the format to P5 (plan §2.4, P3 Q3). P4 stays in `:core`, and can run in parallel with P6 and P7. |
| P4-D7 | Records that lie in the future after the clock is set back are pulled back to now (§4.3.8). | Otherwise, setting the clock back an hour would stretch a snooze or a repeat to 70 minutes. |

## 5. Security and privacy

Plan §3 applies. P4 stores nothing itself, shows nothing, and has no Android code.

- **Reminder records hold no treatment content:** only take keys (medicine IDs and planned times) and instants. That is all P5 will store for them.
- **No logs.** `:core` doesn't log.
- **Exception messages** name only numbers, IDs and times, never names, doses, instructions or notes. A model object's `toString()` includes them, so one is never put into a message, in code or in tests.
- **Test data** uses spec §4.4 ("Example", Amoxicillin, Atrovent) and made-up medicines with made-up IDs. The repo is public: no real treatments.
- **No new permissions, no new libraries.** The permission allowlist check keeps running in `build`.

## 6. Testing

Every test is a JUnit Jupiter unit test in `:core`, with explicit times and zones, mostly Europe/Madrid. Daylight saving in Europe/Madrid in 2026: Sunday 29 March, 02:00 → 03:00; Sunday 25 October, 03:00 → 02:00.

**The walks' harness** (`FakePhone.kt`) plays the part of P5 and the phone:

- It holds the data, the reminder state, the shade (a set of take keys), and the alarm (`nextWakeUp`).
- `reconcile(now)` applies a plan as in §4.3.7: removes `removals(shade)`, sets the shade to the plan's keys, logs each alert and removal with its instant, stores `recorded` and sets the alarm. After each one, it checks the properties of §4.3.10, including a second plan at the same now.
- A walk runs from a start to an end. Its events are the alarm (exact, or late by a seeded random delay), reconciles at seeded random moments, and a script of actions, each followed by a reconcile: Taken, Skip or Snooze from a reminder, Taken early or an undo in the app, an edit, an end, a delete, a change of zone.
- **The oracle** works out the expected alerts and removals on its own, from the schedule and the script, without the planner: each round starts when the take falls due or a snooze ends, and alerts at its start and every 10 minutes after, 4 times at most, while the take is Due and not snoozed. The reminder leaves at the first of the answer, the snooze and the Missed time.

| Test | Covers |
|---|---|
| `ReminderStateTest`: `snooze` on a Due take, a silent one after its round, one whose snooze has ended but not yet been reconciled; refused on a Pending, Taken, Skipped, Missed or unknown take, and on one already snoozed; `Snoozed.until` is the record time plus 10 minutes, in whole seconds; `Alerted` refuses a count of 0 or 5, with only the number in the message | `RM-3`, P4-D5 |
| `ReminderPlannerTest`, the table of §4.3.3: each row, one by one; the §4.3.9 timeline, step by step; a late first alert at 08:25 repeats at 08:35, 08:45 and 08:55; a reconcile 3 hours late alerts once; the alert after a snooze; a snooze ending at the Missed time doesn't alert | `RM-3`, `RM-5`, `D-16` |
| `ReminderPlannerTest`, the shade: two takes at the same time each have a reminder and both alert; takes of two treatments at once; a first take in the past: only the latest take, Due, alerts, once; order of the reminders; `removals` with a key from a deleted treatment and one from a removed medicine | `RM-4`, `TR-4`, P4-D2 |
| `ReminderPlannerTest`, removal: Taken and Skipped from a reminder; answered in the app; Missed one interval later, with a wake-up at that instant; Missed sooner after an edit; an edit while in the shade keeps the reminder without a new alert; the treatment ended (the Due take leaves, no wake-up for it again); deleted; a medicine removed; a take marked Taken early never alerts and isn't a wake-up; an undo makes the take alert again | `RM-7`, `TK-3`, `TK-4`, `TK-5`, `TR-9`–`TR-11` |
| `ReminderPlannerTest`, the next wake-up: the exact instant of the first take, to the millisecond; a repeat and a snooze end; the Missed time of a silent reminder; `null` with no treatment, and once every take is past; a plan on `recorded` at the same now and at moments before `nextWakeUp` changes nothing | `NF-1`, `D-7` |
| `ReminderPlannerTest`, time: a take in the 29 March gap alerts at 03:30; repeats 10 real minutes apart through 25 October's repeated hour; a zone change east (Europe/Athens) and west (Europe/London) while a reminder is in the shade, and a snooze across one; the clock set back an hour with a snooze and with a repeat pending; the clock set forward | `SC-4`, P4-D5, P4-D7 |
| `ReminderWalkTest`, §4.4 over days: from Sun 22 Nov to Wed 2 Dec, with exact alarms, a script that answers some takes from the reminder after a seeded delay, snoozes some first, and leaves others to be Missed, and 300 reconciles at random moments; several seeds. Every alert matches the oracle, once, at its instant, and every removal is on time. | `RM-3`–`RM-5`, `RM-7`, `TK-3`, the plan's done criteria |
| `ReminderWalkTest`, a weekly medicine with no end: first take Mon 12 Oct 2026 08:00, walked to Mon 30 Nov, across 25 October, some takes ignored, answered or snoozed, with reconciles at random moments; and a second run in which the treatment is ended mid-walk. As above. | `RM-5`, `TK-3`, `SC-4`, `SC-5`, `TR-11`, the plan's done criteria |
| `ReminderWalkTest`, minute by minute: the §4.4 example with no answers, reconciled every minute with no alarm, gives the same alerts and removals as the walk driven by the alarm | `D-7` |
| `ReminderWalkTest`, late alarms: each alarm late by a seeded random delay of up to 15 minutes. The properties of §4.3.10 hold, every first alert plays at the first reconcile at or after its take falls due, and every round has 4 alerts unless it ends first | `RM-5`, `NF-1` (the planner side) |
| `ReminderWalkTest`, events: an edit, a delete, a medicine removed, the treatment ended and a change of zone, each while a reminder is in the shade | `TR-9`–`TR-11`, `SC-4` |

**Manual checks:** none on a device. P4 is pure Kotlin, with no screen, alarm or notification, so neither the layout checks nor the phone checks apply. Its timing is checked on the phone in P5, where the reconcile and the alarm are. The phone isn't needed in this phase.

**Instrumented tests:** none needed. Nothing in `:app` changes.

## 7. Tasks

| ID | Task |
|---|---|
| T1 | Reminder state: `ReminderState`, `TakeReminder`, `snooze()`, and `ReminderPlanner`'s constants. `ReminderStateTest`. |
| T2 | The planner: `ReminderPlanner.plan()`, `ReminderPlan`, `Reminder` and `removals`, with the clock check of §4.3.8. `ReminderPlannerTest`, starting with the §4.3.9 timeline. |
| T3 | The walks: `FakePhone`, its oracle and the property checks of §4.3.10. `ReminderWalkTest`: §4.4 over days, the weekly medicine over weeks, minute by minute, late alarms, and the events. |
| T4 | Wrap-up: check every row of §6 and every rule of §4.3 against the tests; KDoc on the public API; `CLAUDE.md` (the `reminders` package and its entry points); `build`; the pull request with CI green. |

## 8. Done criteria

- [ ] All tasks done
- [ ] `./gradlew build` passes locally and in CI, with `:core`'s tests among them (the totals recorded in the handover)
- [ ] Unit tests cover every rule of §4.3 and every row of §6, including the plan's list for P4:
  - [ ] the §4.4 example over several simulated days, and a weekly medicine with no end over several weeks, with reconciles at random moments in between;
  - [ ] every alert happens once, at the right minute, and every removal on time;
  - [ ] several takes at the same time, a snooze, three repeats ten minutes apart, a take that becomes Missed, and a treatment edited, ended or deleted while a reminder is in the shade.
- [ ] `:core` depends on nothing but Kotlin, kotlinx-coroutines and its test libraries, and `:app` is unchanged
- [ ] HANDOVER.md is up to date, with the pending wiring for P5, and the plan's status table is updated

## 9. Open questions

None at the moment. New questions are numbered from Q4.

### Answered

The review confirmed the three draft assumptions. They went into specification v0.6 (`RM-3`, `RM-5`, `TK-5`), which §2 now quotes.

| # | Question | Decision |
|---|---|---|
| Q1 | What happens after a snooze ends | A new round: the reminder alerts, then repeats every 10 minutes, up to 3 times, as when the take fell due. It can be snoozed again, until the take is Missed. (`RM-3`, §4.3.4) |
| Q2 | When the repeats come after a late first alert | 10 minutes after the alert that played: a reminder that first alerted at 08:25 repeats at 08:35, 08:45 and 08:55. (`RM-5`, §4.3.3, P4-D3) |
| Q3 | Whether a take Due again after an undo alerts | Yes, at once, as a take that has just fallen due, with its round of repeats. (`TK-5`, §4.3.5) |

## 10. Change log

| Version | Date | Changes |
|---|---|---|
| 0.1 | 2026-11-20 | First draft |
| 0.2 | 2026-11-20 | Review: Q1–Q3 answered, confirming the draft assumptions. They went into specification v0.6 (`RM-3`, `RM-5`, `TK-5`), which §2 now quotes; §2 adds `TK-5`. Ready. |
