# P8 — Integration and v1.0: Specification

| | |
|---|---|
| **Status** | Draft v0.1 |
| **Phase** | P8, see [Implementation plan §5](../../IMPLEMENTATION_PLAN.md#5-phases) |
| **Depends on** | P5, P6, P7 (all Done), and through them P1–P4 |
| **Branch** | `phase/p8-integration` |
| **Handover** | [HANDOVER.md](HANDOVER.md) |

This document holds everything needed to implement the phase. Working on it shouldn't require reading the full specification or plan.

## 1. Goal

At the end of this phase, the app is version 1.0.0. It has been checked from end to end, by one instrumented test that goes through the real app and by an acceptance run on the phone with a signed release build, including a day and a night of real use. The acceptance record (`ACCEPTANCE.md`) says how each requirement was checked. Once the user has merged the pull request and given the go-ahead, `main` is tagged `v1.0.0`, and a GitHub release has the signed APK attached.

P8 adds no screen and no feature. Its code is one test, a fix to another, and the release build's signing. Anything else it changes is a bug found while checking, fixed with a test of its own (§4.3.7).

## 2. Requirements covered

P8 covers every requirement: it accepts each one on the phone, with the release build. The text of each is quoted from [SPECIFICATION.md](../../SPECIFICATION.md) (version v0.11). "Built in" is plan §5.3's coverage; those phases' tests stay the automated check. "Accepted by" names this phase's acceptance checks (§6.2).

Plan §5.2 names four as P8's own final checks: `NF-1` and `NF-2` on the release build, `NF-3` and `NF-9`.

### 2.1 Treatments and medicines

| ID | Requirement (quoted) | Built in | Accepted by |
|---|---|---|---|
| TR-1 | The app manages several treatments. More than one can be active at the same time. | P2, P6 | A2 |
| TR-2 | A treatment has one or more medicines. | P2, P6 | A2 |
| TR-3 | Each medicine has a name, a dose per take written as free text (such as "1 capsule" or "3 puffs"), an interval in hours, days or weeks ("every 8 hours", "every 2 days", "every week") and a duration: a number of days or weeks ("for 7 days", "for 12 weeks"), or no end. | P2, P6 | A2 |
| TR-4 | Each medicine has its own first take, a date and a time. Medicines in the same treatment can start at different times. The editor suggests the next full hour. The first take can be in the past, for a treatment that started before it was entered. Its takes already past are then Missed, except the latest one while it is still Due (`TK-3`). | P2, P6 | A2 |
| TR-5 | A treatment has a name, and optional notes. | P2, P6 | A2 |
| TR-6 | A medicine can have optional instructions, such as "with food", which its reminder shows. | P2, P6 | A2, A5 |
| TR-7 | The dose is free text, such as "1 capsule", "3 puffs" or "1/3 tablet". There is no list of units, and no limit on the amount. | P2, P6 | A2 |
| TR-8 | Validation: the treatment and each medicine need a name, and a treatment has at least one medicine. The interval is a whole number of hours, days or weeks, and the duration a whole number of days or weeks, each at least 1, with no upper limit; or the duration is set to no end. | P2, P6 | A2 |
| TR-9 | Treatments can be created, edited and deleted in the app. Deleting asks for confirmation, and also deletes every take recorded for the treatment. | P3, P5, P6 | A2, A6 |
| TR-10 | Editing a treatment that has already started changes only what is still to come. Takes that are due or answered keep their state; takes not yet due are worked out again from the new values. | P2, P3, P6 | A2 |
| TR-11 | A treatment can be ended early, for example when the doctor stops it. Its takes not yet due are removed and their reminders cancelled, and it shows as Finished. A take that is due at that moment becomes Missed, and its reminder is removed. A take already marked Taken early (`TK-4`) keeps its answer. A treatment ended early can't be edited any more, only deleted. | P2, P5, P6 | A2, A8 |
| TR-12 | Finished treatments stay in the app until you delete them. | P2, P3, P6 | A2 |
| TR-13 | Leaving the editor with unsaved changes asks for confirmation before discarding them. | P6 | A2 |

### 2.2 Schedule

| ID | Requirement (quoted) | Built in | Accepted by |
|---|---|---|---|
| SC-1 | A medicine is taken once every interval, a number of hours, days or weeks, from its first take, for its duration. | P2 | A2, A5 |
| SC-2 | The app works out every planned take as in §4.3. The editor shows each medicine's number of takes and the date and time of its last take, before saving. | P2, P6 | A2 |
| SC-3 | Planned times are fixed. Taking a dose late or early doesn't move the later takes. | P2 | A3, A4 |
| SC-4 | Planned times are clock times on the phone. When the clocks change for daylight saving, a take planned at 08:00 still alerts at 08:00, so one interval that night is an hour longer or shorter. After a change of time zone, takes keep the same clock times in the new zone. | P2 | A9 (a change of time zone, on the emulator); daylight saving only in P2's unit tests |
| SC-5 | A medicine with no end keeps its schedule until its treatment is ended (`TR-11`) or the medicine is removed from the treatment. Where the app shows a number of takes or a last take (`SC-2`, `HM-3`), it shows "No end" instead. | P2, P6, P7 | A2, A3 |

The scheduling rules of specification §4.3, which SC-2 refers to, and its §4.4 example, are P2's, and P2's unit tests check them. The §4.4 example is the test data of this phase too: treatment "Example", Amoxicillin 1 capsule every 8 h for 7 days (21 takes) and Atrovent 3 puffs every 8 h for 5 days (15 takes), from Monday 23 November 2026 at 08:00.

### 2.3 Reminders

| ID | Requirement (quoted) | Built in | Accepted by |
|---|---|---|---|
| RM-1 | When a take is due, the phone alerts with a reminder that names the medicine, the dose and the treatment, for example "Amoxicillin: 1 capsule" and "Example". It also shows the medicine's instructions, if it has any. | P5 | A5, `EndToEndTest` |
| RM-2 | A reminder is a heads-up notification with sound, without vibration. In vibrate mode it vibrates instead of sounding, as Android does with every notification. It alerts whether the app is open or closed, the screen is on or off, the phone is locked, or the phone has restarted since the treatment was saved. | P5 | A5, A7, A8 |
| RM-3 | A reminder has three actions: **Taken**, **Snooze** and **Skip**. Taken and Skip record the answer (`TK-2`) and remove the reminder. Snooze removes it, and alerts again 10 minutes later. From then on it repeats as in `RM-5`, every 10 minutes, up to 3 times, and it can be snoozed again. | P4, P5 | A6, `EndToEndTest` |
| RM-4 | Each take has its own reminder, so takes due at the same time are answered separately. Android groups them in the notification shade. Reminders that alert at the same moment may play a single sound. | P4, P5 | A5, `EndToEndTest` |
| RM-5 | A reminder that isn't answered stays in the notification shade, and alerts again every 10 minutes, up to 3 times. After that it stays there silently, until it is answered or the take becomes Missed (`TK-3`). Each repeat comes 10 minutes after the alert before it, so a reminder whose first alert came late, for example after the phone restarted, still repeats 3 times. Swiping a reminder away doesn't answer or snooze its take: the reminder comes back with its next repeat, or, once its repeats are over, silently the next time the app updates its reminders, for example when the app is opened. | P4, P5 | A6 |
| RM-6 | Tapping a reminder opens the app on that take. | P5 | A4, `EndToEndTest` |
| RM-7 | When a take is answered in the app, or becomes Missed, its reminder is removed from the shade. | P4, P5 | A6, `EndToEndTest` |
| RM-8 | Reminders follow the phone's Do Not Disturb and silent mode settings: in either one, a reminder appears without sound. | P5 | A7 |
| RM-9 | **Reminder check:** the app checks what its reminders depend on: the notification permission; its Reminders notification category, which must be on and allowed to pop up and sound; the exact-alarm permission; and Android's battery optimisation for the app, which HyperOS's battery saver at "No restrictions" also turns off. It shows what is missing, each with a button that opens the right settings screen, or Android's own dialog for battery optimisation. On HyperOS, the settings the app can't read (Autostart, Floating notifications and Lock screen notifications) are listed as steps for you to confirm. The check is shown on first launch, where Android's dialog asks for the notification permission, and in Settings. The home screen warns while something is missing or a step isn't confirmed. | P5, P7 | A1, A9 |
| RM-11 | The lock screen shows a reminder's full content. | P5 | A5 |

`RM-10` is a discarded proposal: there is nothing to accept.

### 2.4 Takes

| ID | Requirement (quoted) | Built in | Accepted by |
|---|---|---|---|
| TK-1 | Each planned take has a state: Pending (not yet due), Due, Taken, Skipped or Missed (§4.3). | P2, P4 | A3, A4 |
| TK-2 | Marking a take Taken records the time it was marked. That time can be corrected afterwards, for example to "taken at 08:20". | P2, P5, P6, P7 | A4, A6 |
| TK-3 | A take still unanswered one interval after its planned time becomes Missed. With an interval of 8 h, the 08:00 take becomes Missed at 16:00; with an interval of a week, a week later. After an edit (`TR-10`), it becomes Missed as soon as the medicine's next take is due, if that comes sooner, so a medicine never has more than one Due take. | P2, P4 | A6 |
| TK-4 | A medicine's next take can be marked Taken before it is due, from the app, for example before going out. Its reminder is then cancelled. The next take is the medicine's Due take, if it has one, or else its first unanswered take still to come. Only one take of a medicine can be marked ahead of its time: the one after it can be marked early once the marked take's planned time has passed. | P2, P7 | A3 |
| TK-5 | The last answered take of each medicine can be changed later, for example after pressing Taken by mistake: to Taken, to Skipped, or back to unanswered. The Treatment screen shows it. Older takes can't be seen or changed. A take set back to unanswered that is Due again gets its reminder back, which alerts at once, as when the take fell due. | P2, P6 | A4 |

### 2.5 Home screen

| ID | Requirement (quoted) | Built in | Accepted by |
|---|---|---|---|
| HM-1 | The home screen shows the next take of each medicine in the active treatments: its date and time, how long until then ("in 2 h 15 min"), the medicine and the dose. | P7 | A3 |
| HM-2 | Due takes come first, highlighted, each with a **Taken** button. The others follow, ordered by time. | P7 | A3, `EndToEndTest` |
| HM-3 | Each medicine shows its progress, such as "5 of 21" (takes answered out of the total), and its treatment's name. | P7 | A3, `EndToEndTest` |
| HM-4 | Upcoming treatments show when they start. With no treatment active or upcoming, the screen says so and offers to create one. | P7 | A3 |
| HM-5 | While the home screen is open, the times left and the states stay current, without leaving and coming back. | P7 | A3 |

### 2.6 Non-functional requirements

| ID | Requirement (quoted) | Built in | Accepted by |
|---|---|---|---|
| NF-1 | **On time:** a reminder alerts within 1 minute of its planned time, also when the phone is idle (Doze), the screen is off, the app was swiped away from Recents, or the phone has restarted, as long as the reminder check (`RM-9`) passes. This is checked on the phone, not only on the emulator. | P4, P5; **P8 on the release build** | A5, A8, A11 |
| NF-2 | **Reminders survive events:** they are set again after the phone restarts, after an app update, after a change of time or time zone, and after Android ends the app's process. A *Force stop* from Android's settings cancels every alarm, and Android gives the app no chance to undo that until it is opened again; when it is, it sets them again at once. | P5; **P8 on the release build** | A8, A9 |
| NF-3 | **Offline and private:** no network permission, no accounts, no analytics, no ads. Treatment data stays on the phone. Android's cloud backup is turned off. | P1; **P8** | A12 |
| NF-4 | **Data is kept safe:** treatments and takes survive app restarts, phone restarts and app updates. A save interrupted halfway, for example by a flat battery, never loses or damages what was saved before. There is no backup: uninstalling the app deletes everything. | P3 | A8 (restarts and updates); an interrupted save only in P3's tests |
| NF-5 | **Battery:** nothing runs in the background between reminders. The app wakes only to alert. | P5 | A11, A12 |
| NF-6 | **Compatibility:** runs on Android 13 (API 33) on the target phone, and keeps working if the phone is updated to a later Android version. The lowest version supported is set in the implementation plan. | P1, P5 | A1 (the phone, API 33), A9 (the emulator, API 36) |
| NF-7 | **Phone layout:** portrait only, for a screen about 393 dp wide. Touch targets are at least 48 dp. Text stays usable with the system's larger font sizes. | P1, P6, P7 | A10 |
| NF-8 | **Language and formats:** the UI is in English. All user-facing text lives in resource files, so a Spanish translation can be added later without code changes. Times follow the phone's 24-hour clock setting. Day and month names are in the UI's language, such as "Mon 23 Nov", so a translation brings its own. | P1, P6, P7 | A10 |
| NF-9 | **Distribution:** installed as an APK from the development machine (`adb` or Android Studio). It is never published on Google Play. | P1; **P8** | A1 |
| NF-10 | **Theme:** follows the system's light or dark setting. | P1, P6, P7 | A10 |

### 2.7 Plan decisions this phase applies

- **PQ-3 (release signing):** "A new keystore for this app, kept outside the repo. The signed APK is attached to a `v1.0.0` GitHub release, made only with the user's go-ahead (P8)."
- **Plan §3:** "The release keystore and its passwords live outside the repository, in user-level Gradle properties. `.gitignore` already excludes keystore files." "Release builds don't log." "Only the launcher activity is exported."
- **Plan §4:** "A device run counts only on the phone. […] Each run records the Android and HyperOS versions and whether the power settings were stock."
- **Plan `D-16`:** "After a restart, reminders start again once the phone has been unlocked for the first time. A reminder that came due in between alerts as soon as the phone is unlocked."
- **Plan `D-15`:** "Debug builds can schedule in minutes. […] Release builds have neither." The release build's shortest interval is 1 hour, and it has no debug screen and no reminder log.

## 3. Scope

### In scope

- **The wiring, checked (T1):** every pending item that earlier phases left for P8 (§4.3.1), and a search of the main code for any fake or placeholder left in it.
- **A known failing test, fixed (T1):** P6's `EndAndDeleteTest` fails at a large font (P7's known issue, §4.3.6).
- **One end-to-end test (T2):** `EndToEndTest`, through the real app: it creates a treatment, gets its reminders, answers one from the notification and the other on the Take screen it opens, and sees Home change (§4.3.2).
- **Release build (T3):** the version `1.0.0`, a new keystore of its own outside the repo, the signing configuration read from user-level Gradle properties, and checks of the signed APK (§4.3.3).
- **Measure first (T4):** how to read the release build's alert times after hours, since it has no reminder log (§4.3.4).
- **Acceptance (T5–T8):** checks A1–A12 (§6.2) on the release build, on the phone and, where noted, the emulator, including a day and a night of real use with the settings the reminder check asks for. The results go into `ACCEPTANCE.md` (§4.3.5).
- **Documents (T9):** `ACCEPTANCE.md`, the README's status and installation, CLAUDE.md's release commands and status.
- **The release (T10, after the merge, with the user's go-ahead):** the `v1.0.0` tag on `main`, and a GitHub release with the signed APK (§4.3.3).
- **Bugs found while checking:** fixed on the phase branch, each with a test (§4.3.7).

### Out of scope

- New features, screens or texts. Ideas found during the real use go into specification §10 (future ideas), not into v1.0.
- A Spanish translation (spec §10).
- Daylight saving changes on a device. The next one in Europe/Madrid is in March 2027; P2's unit tests cover both changes.
- Android 16's notification timeout (P5's known issue: the emulator's records showed `timeout=PT72H`). The phone runs Android 13, and a reminder that disappears comes back silently at the next reconcile. Not checked: it would take 72 hours.
- An interrupted save on a device (`NF-4`): P3's unit tests cover it.
- Minifying the release build (Q5).
- Google Play, an app bundle, or any other distribution (`NF-9`).
- The skills review after the phase: the `phase-close` skill (plan `D-17`).

## 4. Design

### 4.1 Packages and files owned

P8 owns:

- `app/src/androidTest/kotlin/io/github/davamix/pillsreminder/EndToEndTest.kt` (new), beside `NavigationSmokeTest`, since it goes through `MainActivity` and the container.
- `docs/phases/P8-integration/ACCEPTANCE.md` (new): the acceptance record (Q6).
- Outside the repo: the keystore and the four signing properties (§4.3.3). Nothing about them goes in a commit except the names of the properties.

Shared files it changes, with its own entries only:

- `app/build.gradle.kts`: `versionName = "1.0.0"`, and the release signing configuration.
- `app/src/androidTest/kotlin/io/github/davamix/pillsreminder/ui/EndAndDeleteTest.kt` (P6's): the one-line fix of §4.3.6 (Q7).
- `README.md`: the status, what v1.0 does, how to install it, and links to the release and the acceptance record.
- `CLAUDE.md`: the release build's commands, where its signing properties come from, and the status line.

A bug fix found during acceptance changes the file it's in, with a test; the handover names each (§4.3.7).

No library is added. `EndToEndTest` sends the notification's own `PendingIntent`s rather than tapping the shade, so it needs no UiAutomator (§4.3.2).

### 4.2 Interfaces and data

No new type or interface. The signing configuration, in `app/build.gradle.kts`:

```kotlin
// Release signing (P8, plan PQ-3). The keystore and its passwords are user-level Gradle properties
// (~/.gradle/gradle.properties), never in the repo. Without all four, as in CI, the release build is left unsigned.
val releaseSigning =
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .associateWith { providers.gradleProperty("pillsReminder.release.$it").orNull }

android {
    signingConfigs {
        if (releaseSigning.values.all { it != null }) {
            create("release") {
                storeFile = file(releaseSigning.getValue("storeFile")!!)
                storePassword = releaseSigning.getValue("storePassword")
                keyAlias = releaseSigning.getValue("keyAlias")
                keyPassword = releaseSigning.getValue("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
}
```

The shape is a suggestion. What matters: the properties are named `pillsReminder.release.storeFile`, `…storePassword`, `…keyAlias` and `…keyPassword`; CI, which has none of them, still builds (`./gradlew build` assembles an unsigned release APK, as it does now); and no value is ever printed by the build.

The version: `versionName = "1.0.0"`, `versionCode = 1`, for both build types, as P1 left the code at 1 (P1 handover: "P8 sets 1.0.0 for the release"). The next release raises the code by one.

### 4.3 Behaviour

#### 4.3.1 Wiring status (T1)

Plan §5.2 lists P8's wiring. Every item was done by the phase that provided the real code. T1 confirms each in the code, and in a test where one exists, and records it in the handover:

| Item | Done by | How T1 confirms it |
|---|---|---|
| The real repository everywhere | P3: `AppContainer.repository` is `DataStoreTreatmentRepository`; P6-D2 and P7 pass it to every screen through `ScreenDependencies` | No `InMemoryTreatmentRepository` or `InMemoryCheckStore` in `app/src/main` outside their own files and comments; `ScreenDependencies`' default is the container's |
| A tapped reminder opens the Take screen | P5 (`ReminderIntents.openTake`, `MainActivity.takeOf`), P6 (the real `TakeRoute`, Q3: above the editors when they are open) | `OpenTakeTest`; `EndToEndTest` |
| Answers in the app remove their notification | P5-D2: `Reminders.start()` reconciles after every change of `repository.data` | `RemindersTest`; `EndToEndTest` |
| The home screen shows answers given from a notification | P7: Home's state is `combine(repository.data, …)` | `HomeViewModelTest`; `EndToEndTest` |
| Debug builds offer minutes; release builds don't | P6: `allowMinutes = BuildConfig.DEBUG` | A2 on the release build: no minutes unit |
| No placeholder screen left | P7: "since P7 none is a placeholder" | `Routes.kt` and `AppNavHost.kt` |
| `CurrentZoneClock` follows a change of zone in a running app | P5 (A6, E3) | A9 on the release build |
| The lost-sound fix | Pull request #8, after P7 (P7 SPEC Q10) | A5 and A11 compare each alert the app made with the system's `notification_alert` events |

Pending wiring that names P8 and isn't code: `NF-1` and `NF-2` again on the release build, with the reminder check passing (P5, P7); the first launch's check on a fresh install of the release build (P7); the end-to-end test and the acceptance record (P7). They are T2 and T5–T8.

If T1 finds a gap, it is wired in T1, with a test, and recorded under "Decisions and deviations".

#### 4.3.2 The end-to-end test (T2)

`EndToEndTest`, one JUnit 4 instrumented test, on the debug build, through `MainActivity` and the app's real container: its store, its reminders, its receivers. It is the one test that checks the real path from the editor to the shade and back. Every other UI test uses fakes (P6-D2).

Rules:

- `@get:Rule(order = 0) val firstLaunchDone = FirstLaunchDone()` (P7), then `createAndroidComposeRule<MainActivity>()`.
- **It leaves the data as it found it** (CLAUDE.md). It notes the treatment IDs present before it starts, and an `@After` deletes the treatment it created, through `repository.deleteTreatment` (`core.actions`), even when the test failed. The deletion removes the treatment's answers and, at the next reconcile, its reminder records and notifications (TR-9, P4-D4). If the stored data is unreadable, the test fails at once with a message saying so.
- **It alerts once with sound,** on the phone too (Q1): that is the reminder it checks. CLAUDE.md's "on the phone they post only silent reminders" is for the reminder tests that post through a shade of their own; this test changes the app's real shade, as opening the app does.
- **The real clock.** The test can't wait for a planned time, so it gives the medicines a first take in the past: the latest take is then Due at once, and the earlier ones Missed (`TR-4`). One way: the date picker's yesterday, with the suggested time (the next full hour) and an interval of 8 hours. The takes are then yesterday at H+1, H+9 and H+17, where H is the hour now, and the H+17 one, seven hours ago, is Due until H+1 today. If yesterday is in the previous month, the picker's previous-month button comes first. The expected texts come from the take the data holds, through `dateTimeText()`, so the test passes on the 12-hour emulator and the 24-hour phone. Within 2 minutes of a full hour, the test first waits for the hour to pass, so the Due take doesn't change while it runs.
- **Answering from the notification** sends the notification's own `PendingIntent`s, found in `NotificationManager.activeNotifications`, by the app's tag (`take/<take key>`). The test runs in the app's own process, so it sees the app's notifications. `actions[i].actionIntent.send()` reaches the real `ReminderActionReceiver`, as a tap on the button does; `contentIntent.send()` opens `MainActivity`, as a tap on the reminder does. Tapping the shade itself (system UI) is checked by hand in A4 and A6.

Steps, with the §4.4 example's names (Q1):

1. On Home, New treatment: "Example", with Amoxicillin, 1 capsule, "with food", every 8 hours for 7 days, and Atrovent, 3 puffs, every 8 hours for 5 days, both with the first take of the rule above. Save.
2. **The reminders:** within a few seconds, the shade holds one reminder per take (`RM-4`): titles "Amoxicillin: 1 capsule" and "Atrovent: 3 puffs", texts "Example · with food" and "Example" (`RM-1`), three actions, Taken, Snooze and Skip (`RM-3`), each alerting (`groupAlertBehavior` is `GROUP_ALERT_CHILDREN`, P5 Q8). Home shows both as Due, each with Taken (`HM-2`).
3. **Answered from the notification:** send Amoxicillin's Taken action. Its reminder leaves the shade (`RM-3`); Home shows Amoxicillin's next take, at H+1, with "1 of 21" (`HM-3`), and only Atrovent as Due. The stored answer is Taken, at a time within the test's run (`TK-2`).
4. **Opened from the notification:** send Atrovent's content intent. The Take screen opens on that take, Due (`RM-6`). Press Taken. Its reminder leaves the shade (`RM-7`). Back returns to Home, which has no Due row for the treatment, and Atrovent's "1 of 15".
5. The `@After` deletes the treatment. Nothing of it stays in the shade.

Deliberate bugs (`deliberate-bugs` skill), each to be caught by this test: `Reminders.start()` doesn't observe the data; `ReminderActionReceiver` ignores Taken; `takeOf(intent)` returns `null`; Home's state ignores the data's later values (only its first); the shade posts every reminder silently. A bug in the editors or in the store fails it too, but other tests already catch those.

The test's name is a sentence without punctuation (CLAUDE.md), for example `a treatment created in the app gets reminders that are answered from the notification and the Take screen`.

#### 4.3.3 The release build, its signing and the release (T3, T10)

**The keystore** (Q2), created once, in T3, with the JDK's `keytool` (`"$JAVA_HOME/bin/keytool"`):

- PKCS12, RSA 4096, valid for 30 years, alias `pills-reminder`, distinguished name `CN=Pills Reminder` only: the certificate is inside the public APK, so it holds no personal name.
- Kept outside the repo, under the user's profile, for example `%USERPROFILE%\.android\keystores\pills-reminder-release.jks`.
- The password is generated at random by a script in the scratchpad, passed to `keytool` through an environment variable (`-storepass:env`, `-keypass:env`; a PKCS12 key has the store's password), and written only to the user-level `~/.gradle/gradle.properties`, with the keystore's path and the alias. It is never printed, and never in a command line or a file in the repo. The script first checks that those properties aren't there already, and refuses to overwrite a keystore that exists.
- **The user backs up the keystore and the four properties** (in a password manager, for example), and says so in the handover. Without them, a later version can't be installed over v1.0.0: Android refuses an update signed with another key, and the only way round is to uninstall, which deletes every treatment (`NF-4`).

**The signed APK** is `app/build/outputs/apk/release/app-release.apk`, from `gradle assembleRelease` (`phone` skill). T3 checks, and records in the handover and later in `ACCEPTANCE.md`:

- `apksigner verify --verbose --print-certs` (`D:\Android\Sdk\build-tools\37.0.0\apksigner.bat`): verified, with the v2 or v3 scheme, the certificate's DN and its SHA-256 digest.
- The manifest of the APK itself (`apkanalyzer manifest print`, in `cmdline-tools\latest\bin`): the package `io.github.davamix.pillsreminder`, `versionName` 1.0.0, no `android:debuggable`, `allowBackup="false"` with the data extraction rules, exactly the four permissions of plan §3 plus AndroidX's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, `MainActivity` the only exported component, the three receivers not exported, no `DebugActivity`, no `android:process`.
- The release runtime dependencies (`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`): only AndroidX, JetBrains, Google and their own dependencies, such as Okio (plan §3), and nothing for accounts, analytics or ads (`NF-3`).
- `./gradlew build` still passes without the properties, as CI runs it. CI's run on the pull request shows it; locally, the properties are always there.

**Installing** on a device: `gradle installRelease` (`phone` skill: it sets `ANDROID_SERIAL`), or `$adb install -r app/build/outputs/apk/release/app-release.apk`. The release app (`io.github.davamix.pillsreminder`, "Pills Reminder") installs beside the debug app (`.debug`), each with its own data and reminders.

**The release** (T10, Q4), only after the user has merged the pull request and given the go-ahead:

1. On `main`, at the merge commit: `gradle assembleRelease`, then the checks above again.
2. The APK, renamed `pills-reminder-1.0.0.apk`, and its SHA-256.
3. `git tag -a v1.0.0 -m "Pills Reminder 1.0.0"` on that commit, pushed.
4. `gh release create v1.0.0 pills-reminder-1.0.0.apk --title "Pills Reminder 1.0.0" --notes-file <notes>`. The notes, written in the scratchpad and shown to the user first: what the app does, that it's for personal use on one phone and never on Google Play, how to install it with `adb install`, that its first launch opens the reminder check, the APK's SHA-256 and the certificate's SHA-256 digest, and a link to `ACCEPTANCE.md`.
5. That APK installed on the phone over the accepted one (`adb install -r`): the same key, so the treatments stay.

#### 4.3.4 Reading alert times without the reminder log (T4)

The release build has no reminder log (P5-D9), so the acceptance checks read the system's own records, filtered to the release package (`pkg=$release_pkg` before the helpers, `phone` skill):

- `alert_events`: each alert's time, sound (`beep`) and vibration (`buzz`), from `logcat -b events`. An alert's lateness is its event time minus its planned time, or its repeat time.
- `alarms`: the single alarm clock and its time.
- `notifications`: the reminders in the shade.
- `end_process` works on the release app: `run-as` fails, and it falls back to `am kill` (helpers.sh).

What isn't known (T4 measures it, on the phone, before A11 relies on it): whether `logcat`'s events buffer still holds a night's alerts in the morning, since it wraps. T4 reads the buffer's size and the age of its oldest line (`logcat -b events -g`, and its first line) after a few hours of ordinary use, and looks for other records that last longer: `dumpsys usagestats` (notification interruptions per app), and `dumpsys notification`'s records. The result picks the source for A11, and goes into the handover and the `phone` skill's notes. If nothing lasts a night, the fallback is to read `alert_events` at intervals during the evening and once just before the night's reminder, or to raise the events buffer's size with `logcat -G`, with the user's agreement, putting it back afterwards (§9, assumption A).

#### 4.3.5 The acceptance record

`docs/phases/P8-integration/ACCEPTANCE.md` (Q6), written during T5–T8 and finished in T9, committed on the phase branch. The repo is public: it holds no treatment content from real use, no serial number, no screenshot of the phone (CLAUDE.md, "Git").

Its parts:

1. **Header:** the app version, the commit, the APK's SHA-256 and the certificate's digest; the phone's model, Android and HyperOS versions and security patch; the emulator's image; the dates.
2. **Settings:** the HyperOS and Android settings during the checks, stock or as the reminder check asks for: notifications, the Reminders category, exact alarms, battery optimisation and HyperOS's battery saver, Autostart, Floating and Lock screen notifications. Read with `reminder_health` and `cmd appops get <pkg> 10008` for Autostart, and from the reminder check's own screen.
3. **Automated tests:** the totals of unit tests per module, and of the instrumented suite on the emulator and on the phone, at the commit above.
4. **One row per requirement ID** of §2 (48 rows): its ID, a few words of what it says, the phases whose tests cover it, the acceptance checks that checked it on the phone (or the emulator), the date, and the result: Pass, or Fail with the issue.
5. **One section per acceptance check A1–A12:** the date, the device and build, the steps as run, and what was seen; for alerts, a table of each alert's planned time, event time, lateness, `beep` and `buzz`, and the condition it was checked under.
6. **Known limits:** what was not checked on a device and why (§3, "Out of scope"), and HyperOS's behaviour the user should know: after an update, reminders come back at once only with Autostart on; HyperOS keeps at most 10 notifications per app; it shows no alarm-clock icon.

#### 4.3.6 P6's `EndAndDeleteTest` at a large font (T1)

P7's known issue: "at font scale 1.4, on the phone and on the emulator, it scrolls down to End early and then asserts "Active" is displayed, which has scrolled out of view. It passes at the normal font on both devices. […] The fix is one line: scroll to "Active" before asserting it." T1 makes that fix (Q7), and runs the test at font scale 1.0 and 1.4 on the emulator (`$adb shell settings put system font_scale 1.4`, then back to `1.0`).

#### 4.3.7 Bugs found while checking

A bug found in T1–T9 is fixed on the phase branch, in the file it's in, with a test that fails without the fix (`deliberate-bugs`, or the test run before the fix), and the acceptance check that found it run again. The handover records each: what was seen, the cause, the fix, the test. A fix that changes what the app does, rather than making it do what the specification says, needs a SPEC change first (`phase-docs`, "SPEC change during implementation"), and the specification's or the plan's if a requirement changes.

### 4.4 Dependencies

No library added. Interfaces used, all merged:

- P2's `core.actions` (`repository.deleteTreatment`) and `PillsData`, for `EndToEndTest`'s set-up and clean-up.
- P5's `AndroidReminderShade.TAG_PREFIX` and `TakeKeyCodec`, to find the app's notifications; `ReminderIntents` stays internal.
- P6's and P7's UI test helpers in `androidTest/…/ui/UiTesting.kt`: `type`, `tap`, `tapText`, `pickDay`, `text`, `quantity`, `dateTimeText()`; P7's `FirstLaunchDone`.
- The `phone` skill's helpers, with `pkg=$release_pkg` for the release app; `apksigner`, `apkanalyzer` and `keytool` from the installed SDK and JDK.

## 5. Security and privacy

Plan §3 applies in full. What this phase must watch:

- **The keystore and its passwords** never enter the repo, a commit message, a pull request, a log or this conversation's output (§4.3.3). `.gitignore` already excludes `*.jks` and `*.keystore`; the keystore lives outside the working copy anyway. Before each commit, `git status` and the diff are read for anything signing-related.
- **The certificate is public,** inside the APK on GitHub: its DN is `CN=Pills Reminder`, with no personal name.
- **The release APK is public.** It holds no data: the treatments are entered on the phone. It is built from the tagged commit.
- **`ACCEPTANCE.md`, the README and the release notes are public.** Real use is recorded as times, lateness and conditions, never with a medicine's name, dose, instructions or notes (Q3). No serial number, no screenshot of the phone. Test data uses the §4.4 example.
- **The release build is checked to be closed:** not debuggable (so `run-as` can't read its data), no `INTERNET`, no backup, only the launcher activity exported, no debug activity (§4.3.3, A12). It logs nothing: A12 checks that logcat has no line from the app's tags (`PillsReminders`, `PillsStore`) while it runs.
- **`EndToEndTest` uses the debug app's real data** (CLAUDE.md): it adds one treatment and deletes it, whatever the outcome. It alerts once on the phone (Q1).
- **The phone is the user's own** (`phone` skill): ask before changing its settings, restarting it, putting it in Doze, changing its ring mode, or installing the release build. Never change its clock or time zone: A9's time checks run on the emulator. Read its screen only while the app is in front.
- **During the real use,** the release app holds the user's own treatment, if they choose one (Q3). The session doesn't read its data: it can't (`run-as` refuses a release build), and doesn't read the app's screens unless the user asks.

## 6. Testing

### 6.1 Automated

| Test | Type | Covers |
|---|---|---|
| `EndToEndTest` (new, §4.3.2) | Instrumented, real app and data, emulator and phone | RM-1, RM-3, RM-4, RM-6, RM-7, TK-2, HM-2, HM-3; the wiring of §4.3.1 |
| `EndAndDeleteTest` (P6's, fixed, §4.3.6) | Instrumented, fakes; at font scale 1.0 and 1.4 | TR-9, TR-11, NF-7 |
| Every earlier phase's unit and instrumented tests | Unit, instrumented | Every requirement, as their SPECs list |

Every unit test passes (`./gradlew build`), locally and in CI. Every instrumented test passes on the emulator and the phone, `EndToEndTest` included.

### 6.2 Acceptance checks (manual)

On the **release build**, signed (T3). On the **phone** unless marked "emulator". Each check names the requirements it accepts (§2). The checks with reminders follow the `phone` skill's "Reminder device runs", with `pkg=$release_pkg`; each alert's lateness comes from the source T4 picked. Tell the user each timetable before it starts.

The release build's shortest interval is 1 hour. A test treatment every hour gives, for each take, an alert at the hour and repeats at +10, +20 and +30 minutes, then Missed at the next hour: four alerts an hour, each one checked under a condition of its own. Its data is the §4.4 example's names: "Example", Amoxicillin 1 capsule "with food", Atrovent 3 puffs.

**A1. Install and first launch** (NF-9, NF-6 on API 33, RM-9). With the user's agreement, `adb install` the signed APK on the phone, fresh. `dumpsys package` shows the permissions granted at install (`USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`) and no `INTERNET`. Opening it shows the reminder check above Home, and Android's dialog asks for the notification permission; allowed. The check lists what is still missing: battery optimisation, and HyperOS's three steps. Each line's button opens its screen (`top_activity`); after each change the line turns to set, and each step is confirmed. Back on Home, no warning. `reminder_health` with the release package agrees. Settings shows the same check.

**A2. Treatments and the editors** (TR-1–TR-13, SC-1, SC-2, SC-5). On the phone, with test names:
- Create the §4.4 example, with notes on the treatment and "with food" on Amoxicillin. The medicine editor shows "21 takes" and the last one; the interval offers hours, days and weeks, no minutes (D-15). Dose "1/3 tablet" is accepted (TR-7).
- Validation: an empty name, no medicine, an interval of 0, each with its message (TR-8).
- Leaving either editor with changes asks first (TR-13).
- A second treatment with a medicine every week with no end shows "No end" (SC-5); a third that starts tomorrow is Upcoming (TR-1, HM-4).
- A medicine whose first take was this morning: its past takes are Missed and the latest is Due (TR-4).
- Edit a started treatment: answered takes keep their state, the rest follow the new values (TR-10).
- End one early, with its confirmation: Finished, its Due take Missed, its reminder gone, no Edit (TR-11). It stays in Treatments (TR-12).
- Delete one, with its confirmation (TR-9).

**A3. Home** (HM-1–HM-5, TK-1, TK-4, SC-3, SC-5). Due takes first, highlighted, with Taken; then the next takes by time, with the time left, the dose, the progress and the treatment; "No end"; Upcoming with its start; the empty state, once every treatment is deleted (at the end of A8, or on the emulator). Watch a minute pass with Home open: the times left change (HM-5). Mark a next take Taken early, with its confirmation: its reminder never alerts, and the take after it can't be marked yet (TK-4); the later takes keep their planned times (SC-3).

**A4. A take, opened and corrected** (RM-6, TK-1, TK-2, TK-5, SC-3). Tap a reminder in the shade: the app opens on its take (RM-6), also with an editor open (Back returns to the editor). Taken records the time; change it to an earlier time (TK-2). On the Treatment screen, the last answered take: change it to Skipped, then back to unanswered while Due: its reminder comes back and alerts at once (TK-5).

**A5. Reminders on time and their content** (RM-1, RM-2, RM-4, RM-11, TR-6, SC-1, NF-1). Two takes due at the same minute: two reminders, grouped, each with its own actions (RM-4); a heads-up, with sound, no vibration (RM-2); "Amoxicillin: 1 capsule", "Example · with food" (RM-1, TR-6). Then with the screen off and the phone locked: it alerts, and the lock screen shows the full content (RM-11, RM-2). Each alert within 1 minute of its time (NF-1), and each one the app made has its `notification_alert` event (the lost-sound fix).

**A6. Actions, snooze, repeats and removal** (RM-3, RM-5, RM-7, TK-2, TK-3, TR-9). Snooze: gone, back 10 minutes later, then repeating (RM-3). Unanswered: three repeats 10 minutes apart, then silent in the shade (RM-5). Swiped away: back at its next repeat (RM-5). Taken and Skip from the notification: recorded, removed (RM-3, TK-2). Answered in the app: removed (RM-7). Unanswered until the next take: Missed, removed (TK-3, RM-7). A treatment deleted with a reminder in the shade: its reminder goes (TR-9).

**A7. Ring modes** (RM-8, RM-2). One repeat each, with the phone in the user's hand: Do Not Disturb (appears without sound), Silent mode (without sound), vibrate mode (vibrates instead of sounding). Put the ring mode and Do Not Disturb back as they were.

**A8. Events** (NF-1, NF-2, NF-4, RM-2, TR-11). One condition per alert, with the user: a swipe from Recents; `end_process`; a restart, unlocked after the next alert's time, which then alerts within a minute of the unlock (D-16); an update (`adb install -r` of the same release APK, rebuilt, with a reminder in the shade), which with Autostart on reconciles at once; a *Force stop* from Android's settings: nothing until the app is opened, then the alarm is set again at once (`alarms`) and the Due reminder is back. After the restart and the update, the treatments and answers are all there (NF-4). End the test treatment early at the end (TR-11).

**A9. The emulator** (NF-6 on API 36, NF-2's time and zone, SC-4, RM-9). The release build installed fresh on `PillsReminder_API_36`: the first launch's check, where exact alarms are allowed (`USE_EXACT_ALARM`, granted at install on Android 14 and later too) and there are no HyperOS steps; one reminder on time. Then, with a reminder in the shade, `cmd alarm set-time` an hour ahead and back (`TIME_SET`), and `cmd alarm set-timezone` to another zone and back (`TIMEZONE_CHANGED`): the reminders follow, and the takes keep their clock times in the new zone (SC-4) (`phone` skill, "Common tasks"). Put the emulator's automatic time and zone back.

**A10. Layout, language and theme** (NF-7, NF-8, NF-10). On the phone, each screen once: in portrait with auto-rotate on and the phone turned (it stays portrait); in dark mode; at the phone's large font size (the phone's own setting, about 1.4, with the user's agreement), every text readable and nothing cut off in the 392.7 × 796.4 dp content area; times in 24-hour form; dates such as "Mon 23 Nov"; every text in English, the date picker included, with weeks from Monday. The phone's settings put back afterwards.

**A11. A day and a night of real use** (NF-1, NF-5, RM-2). At least 24 hours, a night included, with the settings the reminder check asks for and the user's own use: they answer reminders as they come. Before it starts, the debug app's test treatments are deleted, so only the release app alerts. Afterwards: every alert's planned time and lateness, from T4's source; the deep-Doze periods (`doze_history`) the night's reminders fell in; one alarm clock at a time (`alarms`); in HyperOS's battery usage, the app's use is negligible (NF-5). The record has times only (Q3).

**A12. Closed and private** (NF-3, NF-5). The APK's manifest and dependencies (§4.3.3); `dumpsys package` on the phone: not debuggable, no `INTERNET`; `run-as io.github.davamix.pillsreminder` refused; the manifest has no service and no job (NF-5); with the release app in use, `logcat` has no line from its tags. No backup: the APK's `allowBackup="false"`, and its data extraction rules exclude everything.

## 7. Tasks

| ID | Task |
|---|---|
| T1 | **Wiring and the failing test.** Confirm each item of §4.3.1 in the code, and record it in the handover; wire any gap, with a test. Fix `EndAndDeleteTest` (§4.3.6), and run it at font scale 1.0 and 1.4 on the emulator. `./gradlew build` passes; the instrumented suite passes on the emulator. |
| T2 | **`EndToEndTest`** (§4.3.2). Run it on the emulator and the phone; then the deliberate bugs of §4.3.2, one at a time, on the emulator, and record which it caught. Every instrumented test passes on the emulator and the phone. |
| T3 | **The release build** (§4.3.3): `versionName` 1.0.0; the keystore and the user-level properties, created by a script that never prints the password; the signing configuration; `gradle assembleRelease`; the APK's signature, manifest and dependencies checked; the user backs up the keystore and the properties. CLAUDE.md's release commands. |
| T4 | **Measure first** (§4.3.4): on the phone, how long the events buffer keeps the app's `notification_alert` lines, and whether `usagestats` or `dumpsys notification` keep alerts longer. Pick A11's source. Record it in the handover. |
| T5 | **The emulator, and the phone's first launch:** A9 on the emulator; A1 and A12 on the phone. Start `ACCEPTANCE.md` (§4.3.5) with the header, the settings and these checks. |
| T6 | **The screens on the phone:** A2, A3, A4 and A10, on the release build. |
| T7 | **Reminders on the phone:** A5, A6, A7 and A8, with an hourly test treatment and a timetable agreed with the user. |
| T8 | **A day and a night of real use:** A11. The session sets it up (the debug app's test treatments deleted, the settings recorded), and reads the results the next day. |
| T9 | **Documents:** `ACCEPTANCE.md` finished, with every row and the automated test totals; the README (status, what v1.0 does, how to install it, the reminder check, links); CLAUDE.md's status. Every instrumented test passes on the emulator and the phone. Then the `handover` skill opens the pull request. |
| T10 | **After the merge, with the user's go-ahead:** the release of §4.3.3, steps 1–5. Then the `phase-close` skill. |

## 8. Done criteria

Before the pull request:

- [ ] T1–T9 done
- [ ] Unit tests and lint pass locally and in CI
- [ ] Every instrumented test passes on the emulator and the phone, `EndToEndTest` included, and `EndAndDeleteTest` at font scale 1.4 too
- [ ] The signed release APK: its signature verified, its manifest and dependencies as §4.3.3 says
- [ ] Every row of `ACCEPTANCE.md` passes, on the release build, on the phone or on the emulator as §6.2 names
- [ ] The day and night of real use recorded, every alert within 1 minute
- [ ] The user has backed up the keystore and its properties
- [ ] README and CLAUDE.md updated; HANDOVER.md up to date, and the plan's status table updated

After the merge (T10):

- [ ] The user's go-ahead
- [ ] `v1.0.0` tagged on `main`, and the GitHub release has the signed APK, its SHA-256 and the certificate's digest
- [ ] That APK installed on the phone over the accepted one, with the treatments kept

## 9. Open questions

| # | Question | Draft assumption |
|---|---|---|
| Q1 | `EndToEndTest` goes through the real app, so it writes to the debug app's real data and its real shade, and it alerts once with sound, on the phone too. | Accepted: it's the one test of the real path, and it deletes its treatment whatever happens. The alternative is to skip it on the phone (`assumeFalse` on HyperOS) and run it only on the emulator. |
| Q2 | Who creates the keystore, where it lives, and what its certificate says. | The session creates it in T3 with a script that writes a random password straight into `~/.gradle/gradle.properties`, never shown: `%USERPROFILE%\.android\keystores\pills-reminder-release.jks`, RSA 4096, 30 years, `CN=Pills Reminder`. The user backs up both. If the user would rather choose and type the password, they run `keytool` themselves, and the session writes only the path and the alias. |
| Q3 | The treatment for the day and night of real use (A11). | The user's choice: one of their own, or the §4.4 example at times that suit them. Either way, `ACCEPTANCE.md` records only times, lateness and conditions, never names, doses or notes. The debug app's test treatments are deleted first, so two apps don't alert. |
| Q4 | When and how v1.0.0 is tagged and released. | After the merge, on `main`'s merge commit, from an APK built there and checked again; `versionCode` 1, `versionName` 1.0.0; the asset `pills-reminder-1.0.0.apk`; the notes shown to the user before publishing (§4.3.3). |
| Q5 | Minify (R8) the release build? | No, as now (`isMinifyEnabled = false`). The APK's size doesn't matter for one phone; R8 would need rules for kotlinx.serialization and the type-safe routes, and the release would no longer run the same code the tests ran. |
| Q6 | Where `ACCEPTANCE.md` lives. | In this phase's folder, `docs/phases/P8-integration/ACCEPTANCE.md`, linked from the README and the release notes. |
| Q7 | Fix P6's `EndAndDeleteTest`, which P7 left as it was. | Yes, in T1: P8 needs every instrumented test to pass, and the user's phone runs at a large font. |
| A | (Assumption, not a question.) T4 measures how to read alert times after a night on the release build. | Until then, the design assumes the events buffer keeps a night of the app's alerts. If not, the fallback of §4.3.4. |

## 10. Change log

| Version | Date | Changes |
|---|---|---|
| 0.1 | 2026-11-23 | First draft, from plan v0.9 (§5.2) and specification v0.11, with what P1–P7's handovers left for P8. |
