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

In progress. T1 is done ("Measurements (T1)"), and its results are in SPEC v0.3 (Q7). T2's layouts are built: `HomeContent` and `SettingsContent` with their state types, `TimeLeft`, the check's types (`check/ReminderCheck.kt`) and both strings files, drawn in every state on the emulator and the phone. They wait for the user's agreement. The app still shows P1's placeholders: no ViewModel or destination yet.

## Next steps

1. The user looks at T2's drawings (in the session's scratchpad, not kept: draw them again with the gallery below if needed) and agrees, or asks for changes.
2. T3: the check's logic (`reminderCheck()`), `DeviceChecks` and `AndroidReminderCheckDevice` with SPEC v0.3's intents, `CheckRecord` and its stores, `ScreenDependencies`' entries and `AppContainer.checkStore`, the fakes; unit tests (`ReminderCheckTest`, `DataStoreCheckStoreTest`, `TimeLeftTest`), shown able to fail.

To draw again: a gallery like T2's (`screen-gallery` skill) with one `@Test` for `HomeContent` and one for `SettingsContent`, and these shots. Home: loading; empty; empty with the warning; upcoming only (Sun 22 Nov 10:30); Mon 10:30 (two Next rows with Taken early); Mon 16:00 (both Due, "now"); Mon 16:05 (the spec's sketch); Mon 16:10 after Taken early; the warning with Due and Next; a long list (2 Due, 5 Next, 2 Upcoming, one with no end, one 6 days ahead), and its end (`performScrollToIndex`); unreadable with the warning; the Taken early dialog; a problem's snackbar; a take due 2 days ago. Settings: loading; stock Android all OK; battery missing; the phone's first launch (battery missing, steps to confirm), and its end; all set with the steps confirmed, and its end; notifications not allowed; the channel off; no pop-up; everything missing, and its end; a problem's snackbar.

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | Done | C1–C6 on the phone and the emulator, with a throwaway probe (a debug-only activity, deleted, never committed); results in "Measurements (T1)". The clean debug build is installed again on both devices. |
| T2 | Doing | `ui/home/HomeContent.kt` (`HomeRow`, `UpcomingRow`, `HomeData`, `HomeState`, `HomeContent`), `ui/home/TimeLeft.kt` (`TimeLeft.between`, `timeLeftText`), `ui/settings/SettingsContent.kt` (`SettingsState`, `SettingsContent`), `check/ReminderCheck.kt` (the check's types), `strings_home.xml`, `strings_settings.xml`. Drawn on the emulator (light, dark, font scale 2) and the phone (light, dark): everything fits, no word broken. Waiting for the user's agreement. |
| T3 | To do | The check: device, logic, store |
| T4 | To do | Home's ViewModel and destination |
| T5 | To do | Settings' ViewModel and destination |
| T6 | To do | UI flow tests, shared test helpers |
| T7 | To do | Drawings again, manual checks |
| T8 | To do | Wrap-up and pull request |

## Decisions and deviations

Anything decided during implementation that isn't in SPEC.md, or that changes it. A change to a requirement goes into SPEC.md or SPECIFICATION.md first.

| Date | Decision | Reason |
|---|---|---|
| 2026-11-22 | **SPEC v0.3** (Q7), agreed by the user: the battery button names `com.android.settings`; the exact-alarm line's button opens the app's details; Autostart's opens HyperOS's Autostart list. Specification v0.11 and plan v0.9: HyperOS's battery saver at "No restrictions" also turns battery optimisation off, rather than being the same setting. | T1, C1 and C2 ("Measurements (T1)"). |
| 2026-11-22 | T2's types, where SPEC §4.2 differs: `CheckLine(item, status, problem: NotificationsProblem?)`, the reason the Notifications line is missing (`NOT_ALLOWED`, `CHANNEL_OFF`, `NO_POP_UP`), rather than `channel: ChannelState?`; `ReminderCheck` also has `hyperOs`, which the battery line's HyperOS sentence needs; `CheckItem` carries its `step`. To fold into the SPEC in T8. | A permission refused and a channel off show different texts and open different screens; one field says which. |
| 2026-11-22 | T2's layout, where SPEC §4.3 leaves it open: a row's button goes under its texts, full width, from font scale 1.5 (`STACKED_FONT_SCALE`); the time left and "No end" are kept on one line by non-breaking spaces in `strings_home.xml`; Due rows are cards in `primaryContainer`, Next and Upcoming rows plain cards, the warning a card in `errorContainer`; the check's lines are cards with a status icon (check, warning, info for a step to confirm), their text in the error colour when missing, their buttons under them. To fold into the SPEC in T8. | The drawings: at font scale 1 on the emulator (12-hour), "in 7 h 55 min" broke after "55"; side by side at font scale 2, a row's button would leave its text too narrow. |
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

## Pending wiring

Connections to other phases left for later.

- **P8 (end to end):** the end-to-end test (create a treatment, get its reminder, answer it, see Home change) and the acceptance record, with the settings the reminder check asks for.
- **P8 (release build):** the first launch's check on a fresh install of the release build, and `NF-1` and `NF-2` again with the check passing.

## How to verify

The commands and manual steps that check the current state.

- Not yet: nothing is built.

## Session log

Newest first.

### 2026-11-22 (later)

- Done: the branch; T1, C1–C6 on the phone and the emulator ("Measurements (T1)"), with a throwaway probe, deleted.
- Found: on HyperOS, the battery intent opens a chooser of HyperOS's battery saver pages unless it names `com.android.settings`; the "Alarms & reminders" page shows a disabled, off switch for an app with `USE_EXACT_ALARM`; HyperOS's App info has no Autostart line, and only the Autostart list can be opened; the app's Floating and Lock screen notification switches can't be read; HyperOS's battery saver doesn't undo the dialog's exemption; the permission dialog only pauses the activity (P7-D6 needed).
- SPEC v0.3 with T1's results (Q7), specification v0.11 and plan v0.9, all agreed by the user, on `main`, merged into the branch.
- Device state left: the phone and the emulator have the clean debug build of `main` (no probe), no test treatment, no alarm or notification from T1. The phone's settings for the debug app are as found: notifications granted (flags as before), battery optimisation on and HyperOS's battery saver at its default, Autostart on, the app's and the category's notification switches on. The emulator's are as found too (notifications granted, channel at importance 4, not exempt); it runs headless.

### 2026-11-22

- Done: SPEC.md v0.1 and this handover, from plan v0.7 and specification v0.9, with what P3, P5 and P6 left for P7.
- Review: every draft assumption accepted (Q1–Q6). Specification v0.10 took Q2–Q6 into `RM-9` and §6; plan v0.8 took Q1 into `D-4` and §2.4. SPEC v0.2, `Ready`.
