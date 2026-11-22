# P7 — Home screen and reminder check: Handover

| | |
|---|---|
| **Status** | In progress |
| **Spec** | [SPEC.md](SPEC.md), version v0.3 |
| **Branch** | `phase/p7-home` |
| **Last session** | 2026-11-22 |

This document tracks the phase as it's built. Update it at the end of every session, so the next one can continue from here.

## Prompt to start or continue

Paste this prompt into a new Claude Code session to start or continue the phase. When the phase is done, replace it with "Phase done: no prompt needed."

```text
Start or continue phase P7 (Home screen and reminder check) of the pills reminder.

1. Use the phase-start skill for P7. Read only docs/phases/P7-home/HANDOVER.md and SPEC.md; they contain everything needed.
2. Carry on from "Next steps" in the handover. Work through the tasks in SPEC.md §7 in order, on branch phase/p7-home. Commit after each task, once the build passes.
3. Follow SPEC.md. If something in it is wrong or unclear, stop and ask me before changing course. Record every decision or deviation in the handover.
4. T1's measurements are done. The phone is needed for the drawings (T2, T7) and M1–M5, connected and unlocked; I change its settings myself, or agree first. Ask before creating test treatments on the phone (their reminders alert) and put back every setting changed. The emulator is needed for the drawings and E1–E2. Every screen must fit the phone's content area, 392.7 × 796.4 dp.
5. Before the session ends, or once all tasks are done, use the handover skill. When the done criteria pass, open the pull request and wait for me to merge it.
```

## Current state

In progress. T1–T6 are done: the measurements (SPEC v0.3), the layouts of Home and Settings (agreed by the user), the check's logic, device and record, both screens with their ViewModels and destinations, and their UI tests. The whole instrumented suite passes on both devices.

## Next steps

1. T7: draw every state again from the finished code (`screen-gallery` skill, the shot list below), light and dark, font scale 1 and 2 on the emulator, light and dark on the phone; then M1–M5 on the phone and E1–E2 on the emulator, with the user (SPEC §6, "Manual checks"). Record the results here.

To draw again: a gallery like T2's (`screen-gallery` skill) with one `@Test` for `HomeContent` and one for `SettingsContent`, and these shots. Home: loading; empty; empty with the warning; upcoming only (Sun 22 Nov 10:30); Mon 10:30 (two Next rows with Taken early); Mon 16:00 (both Due, "now"); Mon 16:05 (the spec's sketch); Mon 16:10 after Taken early; the warning with Due and Next; a long list (2 Due, 5 Next, 2 Upcoming, one with no end, one 6 days ahead), and its end (`performScrollToIndex`); unreadable with the warning; the Taken early dialog; a problem's snackbar; a take due 2 days ago. Settings: loading; stock Android all OK; battery missing; the phone's first launch (battery missing, steps to confirm), and its end; all set with the steps confirmed, and its end; notifications not allowed; the channel off; no pop-up; everything missing, and its end; a problem's snackbar.

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | Done | C1–C6 on the phone and the emulator, with a throwaway probe (a debug-only activity, deleted, never committed); results in "Measurements (T1)". The clean debug build is installed again on both devices. |
| T2 | Done | `ui/home/HomeContent.kt` (`HomeRow`, `UpcomingRow`, `HomeData`, `HomeState`, `HomeContent`), `ui/home/TimeLeft.kt` (`TimeLeft.between`, `timeLeftText`), `ui/settings/SettingsContent.kt` (`SettingsState`, `SettingsContent`), `check/ReminderCheck.kt` (the check's types), `strings_home.xml`, `strings_settings.xml`. Drawn on the emulator (light, dark, font scale 2) and the phone (light, dark): everything fits, no word broken. Agreed by the user. |
| T3 | Done | `check/ReminderCheck.kt` (`reminderCheck()`, `channelStateOf`, `isHyperOs`, `SettingsScreen`, `settingsScreens()`), `check/ReminderCheckDevice.kt` (`DeviceChecks`, `ReminderCheckDevice`, `AndroidReminderCheckDevice`), `check/CheckStore.kt` (`CheckRecord`, `CheckStore`, `InMemoryCheckStore`), `check/DataStoreCheckStore.kt`; `ScreenDependencies`' `checkDevice`, `checkStore`, `reconcile`; `AppContainer.checkStore`; `FakeCheckDevice` in `ViewModelTesting.kt` and `UiTesting.kt`, `FakeApp`'s record and `reconciles`. Tests: `ReminderCheckTest` (14), `DataStoreCheckStoreTest` (8), `TimeLeftTest` (8). 30 deliberate bugs, all caught (session log). |
| T4 | Done | `ui/home/HomeViewModel.kt` (`HomeViewModel`, its factory, the pure `build(stored, now, disabled)`), `ui/home/HomeScreen.kt` (P1's placeholder replaced: `LifecycleResumeEffect` calls `refreshCheck()`, `LeaveWhen(openCheck)` calls `checkOpened()` and opens the check); `SettingsRoute(firstLaunch)` in `Routes.kt`; Home's destination in `AppNavHost` (a row opens `TreatmentRoute`, the settings button and the warning's Check open `SettingsRoute()`, the first launch `SettingsRoute(firstLaunch = true)`), `sampleTake` gone; `home_open_settings` and `home_sample_take` gone, so `NavigationSmokeTest` lost its sample take step and it and `OpenTakeTest` open Settings by its icon. `HomeViewModelTest` (17). 30 deliberate bugs, 29 caught, the other not observable (session log). Settings' destination is still the placeholder (T5). |
| T5 | Done | `ui/settings/SettingsViewModel.kt` (`SettingsViewModel(deps, firstLaunch, saved)`, its factory with `createSavedStateHandle()`), `ui/settings/SettingsScreen.kt` (P1's placeholder replaced: `LifecycleResumeEffect` calls `refresh()`; at the first launch, `LeaveWhen(askNotifications)` calls `notificationsRequested()` and launches Android's dialog through `rememberLauncherForActivityResult(RequestPermission)`, whose answer goes to `notificationsAsked(granted)`; each button calls `checkDevice.open(activity, item)` through `dropUnlessResumedWith`); Settings' destination in `AppNavHost` with `SettingsRoute(firstLaunch)`. `SettingsViewModelTest` (9). 16 deliberate bugs, all caught (session log). The first launch checked on the emulator, with Android's real dialog. |
| T6 | Done | `androidTest/…/ui/HomeScreenTest.kt` (7) and `ReminderCheckScreenTest.kt` (4), with `FakeApp`; `FakeApp(unreadable = …)`, `quantity()` and `assertTouchHeightIsAtLeast()` in `UiTesting.kt`; `androidTest/…/FirstLaunchDone.kt`, the rule for `NavigationSmokeTest` and `OpenTakeTest`; `ui/common/PlaceholderScreen.kt` and `placeholder_note` deleted. The whole instrumented suite, 48 tests, passes on the emulator and the phone. Deliberate bugs in the session log. |
| T7 | Doing | Drawn again from the finished code: the emulator (light, dark, font scale 2) and the phone (light, dark), every state as in T2, nothing cut off, no word broken. E1 and E2 passed on the emulator (session log). M1–M5 on the phone: to do, with the user. |
| T8 | To do | Wrap-up and pull request |

## Decisions and deviations

Anything decided during implementation that isn't in SPEC.md, or that changes it. A change to a requirement goes into SPEC.md or SPECIFICATION.md first.

| Date | Decision | Reason |
|---|---|---|
| 2026-11-22 | **SPEC v0.3** (Q7), agreed by the user: the battery button names `com.android.settings`; the exact-alarm line's button opens the app's details; Autostart's opens HyperOS's Autostart list. Specification v0.11 and plan v0.9: HyperOS's battery saver at "No restrictions" also turns battery optimisation off, rather than being the same setting. | T1, C1 and C2 ("Measurements (T1)"). |
| 2026-11-22 | T2's types, where SPEC §4.2 differs: `CheckLine(item, status, problem: NotificationsProblem?)`, the reason the Notifications line is missing (`NOT_ALLOWED`, `CHANNEL_OFF`, `NO_POP_UP`), rather than `channel: ChannelState?`; `ReminderCheck` also has `hyperOs`, which the battery line's HyperOS sentence needs; `CheckItem` carries its `step`. To fold into the SPEC in T8. | A permission refused and a channel off show different texts and open different screens; one field says which. |
| 2026-11-22 | T2's layout, where SPEC §4.3 leaves it open: a row's button goes under its texts, full width, from font scale 1.5 (`STACKED_FONT_SCALE`); the time left and "No end" are kept on one line by non-breaking spaces in `strings_home.xml`; Due rows are cards in `primaryContainer`, Next and Upcoming rows plain cards, the warning a card in `errorContainer`; the check's lines are cards with a status icon (check, warning, info for a step to confirm), their text in the error colour when missing, their buttons under them. To fold into the SPEC in T8. | The drawings: at font scale 1 on the emulator (12-hour), "in 7 h 55 min" broke after "55"; side by side at font scale 2, a row's button would leave its text too narrow. |
| 2026-11-22 | T3's shapes, where SPEC §4.1 and §4.2 are silent: the screens each line opens are a pure function, `settingsScreens(item, device)`, returning `SettingsScreen`s in the order to try, which `AndroidReminderCheckDevice` turns into intents; the Notifications line opens the channel's screen only when the permission is granted and the channel is wrong. `channelStateOf(importance)` and `isHyperOs(manufacturer)` are pure functions too. The record's file holds `schemaVersion` 1; another version, a damaged file or one that can't be opened reads as empty, and step names it doesn't know are left out. `FakeCheckDevice` is written twice, in the unit tests' and the instrumented tests' helpers, which share no source set. To fold into the SPEC in T8. | The rules then have JVM tests: an `Intent` can't be built in a JVM unit test. |
| 2026-11-22 | T4's details, where SPEC §4.3 is silent: until the check's record is read, Home shows no warning and doesn't open the check (with an empty record, every HyperOS step would count for a moment); a double tap marks once because each take being marked is kept with the data it changed, and its Taken stays disabled until the rows show other data, then follows P2 again (so a take answered and cleared elsewhere can be marked again); the warning's Check and the settings button both open `SettingsRoute()`. To fold into the SPEC in T8. | The SPEC's "disabled from the tap until the data changes", made exact: the store's flow may show the change after `update` returns. |
| 2026-11-22 | T5's details, where SPEC §4.2 is silent: `SettingsViewModel` has `notificationsRequested()`, called right before the dialog is launched, which keeps "asked" in the `SavedStateHandle`; `notificationsAsked(granted)` keeps it too, reads the device again and reconciles when granted. So a screen rebuilt while the dialog is up doesn't ask again, and the answer still arrives. The state is `null` until the check's record is read. A line's button counts only while the screen is resumed (`dropUnlessResumedWith`). To fold into the SPEC in T8. | SPEC §4.3.10: "a rebuilt screen doesn't ask again". If "asked" were kept only from the answer, a screen rebuilt with the dialog up would launch a second one. |
| 2026-11-22 | T6's tests check buttons by their touch height (`assertTouchHeightIsAtLeast`, from `touchBoundsInRoot`), and rows and Done's checkbox rows by their drawn height (`assertHeightIsAtLeast`). The first-launch rule is `FirstLaunchDone`, an `ExternalResource` at `order = 0`, outside the activity's rule. To fold into the SPEC (§6) and CLAUDE.md in T8. | Material 3's buttons are drawn 40 dp tall and take touches over 48 dp (`minimumInteractiveComponentSize`), which meets NF-7; `assertHeightIsAtLeast` read 40 dp. |
| 2026-11-22 | The Taken early confirmation has a title, "Mark as taken now?", and the text "Atrovent · 3 puffs, planned for Today 16:00. Its reminder won't alert." UI text, so no SPEC change; SPEC §4.3.4 quotes the draft's wording, to update in T8. | "Mark Atrovent's take at Today 4:00 PM as taken now?" read awkwardly ("at Today"). |

## Measurements (T1)

2026-11-22. **Phone:** POCO F3, Android 13, HyperOS `OS1.0.6.0.TKHEUXM` (`ro.mi.os.version.name` `OS1.0`, `ro.miui.ui.version.name` `V816`), Spanish, wireless debugging; the debug app with Autostart on (op 10008 `allow`, as the user keeps it), the battery saver at its default, the notification permission granted. **Emulator:** `PillsReminder_API_36`, Android 16, API 36, stock settings. The probe was a debug-only activity with one button per intent, logging each start (and `resolveActivity()`), the activity's lifecycle and every reading of `DeviceChecks`; adb tapped its buttons, read the screen that opened and pressed Back. On the phone, only the screens about the app itself were read, not the lists of all apps. Every setting changed was put back, and checked.

| # | Result |
|---|---|
| C1, emulator | Every Android intent opens its screen for the app, and Back returns to the app: `ACTION_APP_NOTIFICATION_SETTINGS` (the app's notifications, with its categories), `ACTION_CHANNEL_NOTIFICATION_SETTINGS` (the Reminders category: Show notifications, Pop on screen, Sound, Vibration), `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` ("Alarms & reminders" for the app), `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (Android's dialog, "Let app always run in background?", Deny and Allow), `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (every app's battery use), `ACTION_APPLICATION_DETAILS_SETTINGS` (App info). HyperOS's components: `ActivityNotFoundException`. |
| C1, phone | `ACTION_APP_NOTIFICATION_SETTINGS`: HyperOS's page for the app, with **"Notificaciones flotantes"** and **"Notificaciones en la Pantalla de bloqueo"**, each a switch, besides Show notifications, Sound and Vibration. `ACTION_CHANNEL_NOTIFICATION_SETTINGS`: the Reminders category, with its own Notificaciones flotantes switch and "En la Pantalla de bloqueo" (a choice). `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`: "Alarmas y recordatorios" for the app. **`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` opens a chooser,** "Completar acción utilizando", with "Detalles de la batería" (`com.miui.securitycenter`'s `PowerDetailActivity`) and "Batería y rendimiento" (`com.miui.powerkeeper`'s `HiddenAppsConfigActivity`): both are the app's battery saver page ("Sin restricciones", "Ahorro de batería (recomendado)", …), not Android's dialog. **With `setPackage("com.android.settings")`, the same intent opens Android's dialog**, "¿Permitir que la aplicación se ejecute siempre en segundo plano?", DENEGAR and PERMITIR. `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`: HyperOS's battery saver list of every app. `ACTION_APPLICATION_DETAILS_SETTINGS` is redirected to HyperOS's own App info (`com.miui.appmanager.ApplicationsDetailsActivity`), which has **no Autostart line**, scrolled to its end; its "Otros permisos" has only home-screen shortcuts, show on the lock screen, and pop-up windows from the background. **Autostart:** `com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity` opens HyperOS's Autostart list, titled "Autoinicio en segundo plano", where the app is one row: the same screen the user reaches from Settings > Apps > Manage apps. No per-app Autostart screen can be opened (`AutoStartDetailManagementActivity`: `ActivityNotFoundException`; no other exported activity of the package mentions Autostart). Back returned to the app from every screen. `resolveActivity()` found every system screen without `<queries>`. |
| C1, exact alarms | On both devices the "Alarms & reminders" page shows its switch **off and disabled** (`checked=false enabled=false`), though `canScheduleExactAlarms()` is true through `USE_EXACT_ALARM`: the page is about `SCHEDULE_EXACT_ALARM`, which the app doesn't declare. Opening it from the check would mislead. |
| C2, phone | Android's dialog (with `setPackage`), PERMITIR: the app is exempt (`isIgnoringBatteryOptimizations` true, standby bucket 5), and HyperOS's battery saver page then shows **"Sin restricciones"** selected. Choosing "Ahorro de batería (recomendado)" again on that page, and also "Sin restricciones" then "Ahorro" once more, **left the app exempt**: HyperOS doesn't undo the dialog's exemption, though P5 saw its own "Sin restricciones" undone by going back to the default. Put back with `dumpsys deviceidle whitelist -<package>`: optimised, bucket 10, the page at "Ahorro de batería (recomendado)". So: HyperOS's "Sin restricciones" makes the app exempt (P5), and the dialog sets "Sin restricciones", but they aren't one setting both ways. |
| C3, phone | The app's **"Notificaciones flotantes" and "Notificaciones en la Pantalla de bloqueo"**, each turned off, read, and on again: **nothing the app can read changed**: `areNotificationsEnabled()`, `NotificationManager.getImportance()`, the channel's importance and lock-screen visibility, the secure settings `lock_screen_show_notifications` and `lock_screen_allow_private_notifications`, and the app's and channel's lines in `dumpsys notification`. They stay steps to confirm. **The category's own "Notificaciones flotantes" is the channel's importance:** off, 4 → 3; on, back to 4. The Notifications line reads it (`NO_POP_UP`). |
| C4 | Android's dialog shows on both devices (the phone's is `com.google.android.permissioncontroller`'s `GrantPermissionsActivity`, "¿Permitir que Pills Reminder (debug) te envíe notificaciones?", PERMITIR and NO PERMITIR). **The activity gets only `onPause` and `onResume` around it, never `onStop` and `onStart`**, on both: P5's `onStart` reconcile wouldn't run after a grant, so P7-D6's reconcile is needed. On the emulator: a first refusal gives `rationale=true`; a second, `rationale=false`; a third request returns `granted=false` at once, with no dialog. Back on the dialog returns `granted=false`. On the phone, one request, granted; the permission's flags were put back as found (`USER_SET` cleared). |
| C5, emulator | `pm revoke POST_NOTIFICATIONS` ends the process; the reading is then `notif=false`; `pm grant` brings it back. The category's "Pop on screen" off gives importance 3 (`NO_POP_UP`), "Show notifications" off gives 0 (`OFF`); both back on, 4. `dumpsys deviceidle whitelist +` and `-` the app: exempt, then optimised. **Exact alarms can't be revoked:** `cmd appops set … SCHEDULE_EXACT_ALARM deny` and `ignore` leave `canScheduleExactAlarms()` true, and the settings page's switch is disabled; its MISSING line is tested with fakes only. Everything put back. |
| C6 | Phone: `Build.MANUFACTURER` `Xiaomi`, `Build.BRAND` `POCO`, `ro.mi.os.version.name` `OS1.0`. Emulator: `Google`, `google`, no HyperOS property. `Build.MANUFACTURER` equal to "Xiaomi", ignoring case, marks HyperOS. |

**What this settles for SPEC §4.3.8** (now in SPEC v0.3):

| Item | Intent | Fallback |
|---|---|---|
| Notifications, permission | `ACTION_APP_NOTIFICATION_SETTINGS` | the app's details |
| Notifications, channel `OFF` or `NO_POP_UP` | `ACTION_CHANNEL_NOTIFICATION_SETTINGS` | the app's notifications |
| Exact alarms (never missing in practice) | the app's details, `ACTION_APPLICATION_DETAILS_SETTINGS`: "Alarms & reminders" shows a disabled switch that is off | — |
| Battery optimisation | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `setPackage("com.android.settings")`: Android's own dialog, on both devices | the same intent without the package (on HyperOS, the app's battery saver page, through a chooser) |
| Autostart (HyperOS) | `AutoStartManagementActivity`, HyperOS's Autostart list; the line says to find the app in it | the app's details |
| Floating and Lock screen notifications (HyperOS) | `ACTION_APP_NOTIFICATION_SETTINGS`: both switches are there | the app's details |

## Known issues

- From earlier phases: AGP's Gradle 10 deprecation notice and ktlint's `sun.misc.Unsafe` warnings on JDK 25 are harmless (P1).
- The emulator's debug app keeps two notification categories from earlier probes, "Probe reminders" (`probe_reminders`) and `reminders-mutant`, and a `files/probe.log` dated 21 Nov (P5). They show in its notification settings; the check reads only the Reminders category, so they change nothing. Uninstalling the debug app on the emulator would remove them (its data holds no treatment). On the phone those categories are deleted.

## Pending wiring

Connections to other phases left for later.

- **P8 (end to end):** the end-to-end test (create a treatment, get its reminder, answer it, see Home change) and the acceptance record, with the settings the reminder check asks for.
- **P8 (release build):** the first launch's check on a fresh install of the release build, and `NF-1` and `NF-2` again with the check passing.

## How to verify

The commands and manual steps that check the current state.

- `./gradlew build` (unit tests, Lint, ktlint) and `./gradlew :app:compileDebugAndroidTestKotlin`.
- On the emulator: `gradle installDebug` and `launch` (`phone` skill). With no check record and the notification permission revoked, the check opens above Home with Android's dialog; after Allow, the Notifications line is OK and the reminder log shows a reconcile (`app_open`); Back shows Home, with the warning while battery optimisation is on.

## Session log

Newest first.

### 2026-11-22 (T7, part 1)

- Drawings from the finished code, with T2's gallery: the emulator, light and dark at font scale 1, and at font scale 2; the phone, light and dark (24-hour times). Every state of both screens as T2 drew them: everything fits the content area, no word broken, readable in dark theme.
- **E1 (emulator), passed.** From notifications granted, the category at importance 4, battery optimisation on (1 setting to check):
  - `pm revoke` the permission, then open the app: Home "2 settings to check", the check "Not allowed: no reminder can appear."; `pm grant`, then back to the front: "Allowed.", Home 1.
  - The Reminders category off with its switch in the app's notification settings (opened by the check's button): importance 0, "The Reminders category is off…", and the button then opens the category's own page; on again, importance 4.
  - Its "Pop on screen" off: importance 3, "Reminders don't pop up or sound…", Home 2; on again: importance 4, `mUserLockedFields` 4 as found, "Allowed.".
  - Battery: `dumpsys deviceidle whitelist +` then back to the front: "Off for this app.", "Everything reminders need is set."; `-`: missing again; Turn off, Android's dialog, Allow: OK, and Home's warning gone. Put back with `whitelist -`.
  - Exact alarms: `appops … SCHEDULE_EXACT_ALARM deny` changes nothing, the line stays "Allowed." (T1, C5); set back to `default`.
  - No HyperOS step on the emulator.
- **E2 (emulator), passed.** The permission revoked, the check's file removed, a debug test treatment (every 10 minutes, first take a minute later): its alert at 21:53:00 ran 8 ms late but posted nothing, the permission being revoked. Opening the app opened the check above Home and Android's dialog at once; after Allow, a reconcile (`app_open`) at the grant posted the take's reminder (silently, since it had already alerted), and the shade showed "1 take due" and the take. Home showed it under Due, "Today 9:53 PM · now", with Taken; Taken on Home moved it to Next ("in 10 min", Taken early) and took its reminder off the shade (RM-7). Test treatment deleted: no alarm, no notification, the data's hash as before.
- Emulator state afterwards: as found (the permission granted with its flags as before, the category at importance 4, battery optimised, the appop at default), and its check record with the first launch done.

### 2026-11-22 (T6)

- Done: T6, the UI tests, the first-launch rule, the placeholder deleted.
- Found: Material 3's buttons measure 40 dp with `assertHeightIsAtLeast`; their touch area is 48 dp. The tests read `touchBoundsInRoot` for buttons (decisions table).
- The whole instrumented suite, 48 tests (P6's 37 and the 11 new ones), passes on the emulator and on the phone (79.8 s there). Afterwards on each: the treatments' file unchanged (same hash), no alarm, no notification; the check record as found (the emulator's `firstLaunchDone` true; the phone had none, and the rule's write-back, an empty record, was removed with `run-as … rm`).
- T6's deliberate bugs, on the emulator, 14 of 14 caught: Home or Settings not reading the device on resume, by the resume test; the first launch not opening the check, or not recorded (the check opened again), by the first launch test; the warning's Check doing nothing, by the warning, resume and Done tests; a row opening nothing, by the row test; every button opening Notifications, by the buttons test; Done ignored, by the Done test; Taken early without asking, a confirmation that marks nothing, a Cancel that marks, by the Taken early test; Taken disabled, by the Taken test; New treatment shown with unreadable data, by the unreadable test; the rule not marking the first launch done, by `NavigationSmokeTest` (with the emulator's record removed first).
- Device state: the emulator and the phone have this build; neither has a check record now (the next launch of the debug app opens the check). The phone's settings untouched.

### 2026-11-22 (T5)

- Done: T5, Settings' ViewModel, screen and destination.
- T5's deliberate bugs, 16 of 16 caught: the device not read again, by the refresh and grant tests; the record ignored, by the lines and confirm tests; unticking that adds, does nothing, or a tick that replaces the other steps, by the confirm test; a failed write not said, or its problem never cleared, by the failed write test; the dialog asked at every launch, with notifications allowed, or again after asking, by the first launch tests; "asked" not kept from the request or from the answer, or kept outside the `SavedStateHandle`, by the rebuilt-screen and refusal tests; no reconcile after a grant, or one after a refusal, by the grant and refusal tests; the device not read after the answer, by the grant test.
- On the emulator, the first launch from start to end: with the check's file removed and `POST_NOTIFICATIONS` revoked, opening the app opened the check above Home and Android's dialog at once; after Allow, Notifications "Allowed.", the record written, and a reconcile in the reminder log at the grant (P7-D6). Turn off opened Android's battery dialog ("Let app always run in background?", left with Back), Open settings the app's notification settings; Back returned to the check each time, then to Home with "1 setting to check.". Put back: the permission granted, its `USER_SET` flag cleared, as found.
- Unit totals: `:app` 252, `:core` 177, none failing.

### 2026-11-22 (T4)

- Done: T4, Home's ViewModel, screen and destination.
- T4's deliberate bugs, 29 of 30 caught: every row in Next, by six tests; no order by time, the treatment name compared case-sensitively, the medicines' order reversed, Upcoming unsorted, by the order and Upcoming tests (the order also by the example); Upcoming treatments given rows, or swapped with Finished ones, by the Upcoming and Finished tests; planned instants in UTC, by the example, no-end and Taken tests; Taken early always offered, by the example; the marks ignored, applied marks not disabling Taken, marks never cleaned, no guard against a second tap, by the double tap test; a failed write keeping its mark, by the failed write test; no answered count, no count, the wrong today, no ticks, by the example and others; no state until the data is read, by its test; no problem set, or never cleared, by the problem tests; the device not read again, by the warning test; an empty record before the real one is read, or waiting for the record, by the record-not-read test; only MISSING lines counted, by the warning and confirmed-step tests; the local "opened" flag ignored, the record's `firstLaunchDone` ignored, not written, or its write error thrown, by the first launch tests.
- Not caught: Finished treatments given rows (`finished-as-active`). It can't be seen: P2's status is Finished only when no medicine has a next take, or after an early end, which makes every take before the end Missed at the end; so `nextTake` gives no row either way.
- Unit totals: `:app` 243, `:core` 177, none failing.
- Device state: the emulator has this build, and its check record now exists (`firstLaunchDone` true, from the run above; E2 removes it). The phone is unchanged since T1.

### 2026-11-22 (T2, T3)

- Done: T2, the layouts, drawn on both devices and agreed by the user; T3, the check.
- T2's drawings found: the time left broke mid-unit on the emulator's 12-hour clock ("in 7 h 55 / min"), fixed with non-breaking spaces; the Taken early dialog's text read awkwardly, now a title and a short line.
- T3's deliberate bugs, 30 of 30 caught: in `reminderCheck`, the permission ignored, the channel off or without pop-ups taken as OK, the channel off reported as no pop-up, exact alarms always OK, battery inverted, HyperOS's steps on every phone, never confirmed, the `hyperOs` flag lost, steps to confirm not counted, each by its own test; `channelStateOf` with no channel as off, or default importance as on, by the importance test; `isHyperOs` case-sensitive, by its test; `settingsScreens` opening the channel's screen with the permission refused, exact alarms to the wrong screen, the battery chooser first, Autostart without its fallback, by the two screens tests; the store writing no defaults, ignoring the version, failing on an unknown step, throwing on a damaged file or on one it can't open, writing no steps or no first launch, and updating from a stale record, each by its own test; `TimeLeft` rounding ahead down or past up, on time counted as ahead, hours not within the day, no days, by the rounding, on time and days tests.
- Unit totals: `:app` 226, `:core` 177, none failing.

### 2026-11-22 (later)

- Done: the branch; T1, C1–C6 on the phone and the emulator ("Measurements (T1)"), with a throwaway probe, deleted.
- Found: on HyperOS, the battery intent opens a chooser of HyperOS's battery saver pages unless it names `com.android.settings`; the "Alarms & reminders" page shows a disabled, off switch for an app with `USE_EXACT_ALARM`; HyperOS's App info has no Autostart line, and only the Autostart list can be opened; the app's Floating and Lock screen notification switches can't be read; HyperOS's battery saver doesn't undo the dialog's exemption; the permission dialog only pauses the activity (P7-D6 needed).
- SPEC v0.3 with T1's results (Q7), specification v0.11 and plan v0.9, all agreed by the user, on `main`, merged into the branch.
- Device state left: the phone and the emulator have the clean debug build of `main` (no probe), no test treatment, no alarm or notification from T1. The phone's settings for the debug app are as found: notifications granted (flags as before), battery optimisation on and HyperOS's battery saver at its default, Autostart on, the app's and the category's notification switches on. The emulator's are as found too (notifications granted, channel at importance 4, not exempt); it runs headless.

### 2026-11-22

- Done: SPEC.md v0.1 and this handover, from plan v0.7 and specification v0.9, with what P3, P5 and P6 left for P7.
- Review: every draft assumption accepted (Q1–Q6). Specification v0.10 took Q2–Q6 into `RM-9` and §6; plan v0.8 took Q1 into `D-4` and §2.4. SPEC v0.2, `Ready`.
