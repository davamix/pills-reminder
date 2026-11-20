---
name: phone
description: Work with the POCO F3 phone and the PillsReminder_API_36 emulator over adb for the pills-reminder app, with ready-made Bash helpers (helpers.sh). Pick the device (the phone is listed twice, USB and wireless), start and stop the emulator, build and install, launch, read the text on screen, wait for a text or a log line, tap by text, take screenshots, run instrumented tests without wiping the app's data (and grant the HyperOS permission they need on the phone), list the app's pending alarms and its notifications, check the permissions reminders need, force Doze, end the app's process as Android does, read the app's private files; and the HyperOS pitfalls found on this phone. Use whenever the app needs to run on a device, be checked visually, or have its reminders checked, measured or debugged.
---

# Work with the phone and the emulator

## Devices

**Phone**, read with adb on 2026-11-19:

| | |
|---|---|
| Model | POCO F3 (`M2012K11AG`, codename `alioth`) |
| OS | Android 13, API 33, HyperOS `OS1.0.6.0.TKHEUXM`, security patch 2024-03-01 |
| Screen | 1080 × 2400 px, 440 dpi: about 393 × 873 dp in portrait |
| App content area | **392.7 × 796.4 dp**: below a 29.1 dp status bar (the camera cutout) and above a 47.3 dp navigation bar, because the phone uses **3-button navigation** (`navigation_mode` 0). Measured in P1 T8 with the app drawing edge to edge. Every screen must fit in it: it is the tighter of the two devices. |
| Navigation | 3 buttons: Back is a real button, and the bottom inset is large |
| Language and time | Spanish (`es-ES`), Europe/Madrid, 24-hour clock |
| Connection | USB debugging and wireless debugging, both paired. Either may be the only one connected. |
| Debug app cold start | 0.65 s (`am start -W`, P1 T8) |

It is the user's own daily phone. Ask before anything that changes its settings, restarts it, puts it in Doze, or installs or removes anything other than the app. Leave it as you found it. Read its screen (`texts`, `shot`) only while the app is in front: the rest is the user's private data. **Never write the phone's serial number in a file in the repo**, which is public.

**Emulator** `PillsReminder_API_36`, created on 2026-11-19 for this project:

| | |
|---|---|
| Image | `system-images;android-36;google_apis_playstore;x86_64`: Android 16, API 36, with Google Play. A production image: `adb root` doesn't work. |
| Screen | 1080 × 2400 px, 440 dpi, the same as the phone. Set by hand in `~/.android/avd/PillsReminder_API_36.avd/config.ini`. |
| App content area | 392.7 × 824.7 dp: 24 dp status bar, 24 dp navigation bar, because it uses gesture navigation (`navigation_mode` 2). 28 dp taller than the phone's, so a layout that fits here can still overflow on the phone. |
| Other | 4 GB RAM, hardware keyboard on (type from the PC), WHPX acceleration. Debug app cold start 1.3 s. |

The other AVDs on this PC (`Medium_Phone_API_36.0`, `IAmOk_Watcher_36`) belong to the IAmOk project. Leave them alone.

### Which device for what

| Check | Where |
|---|---|
| Layout, screens, UI tests, quick install-and-run loops | The emulator first, then the phone |
| Permission flows of Android 14 and later: exact alarms denied by default | The emulator (API 36) |
| Reminders on time, Doze, restarts, swiping the app away, HyperOS settings (spec `NF-1`, `NF-2`) | **The phone.** The emulator has no HyperOS power management, and its Doze isn't a real phone's: a pass there says nothing about the phone. |

## adb

`adb` is not on PATH:

- Git Bash: `/d/Android/Sdk/platform-tools/adb.exe`
- PowerShell: `D:\Android\Sdk\platform-tools\adb.exe`

In Git Bash, `export MSYS_NO_PATHCONV=1` before any adb command that takes a device path such as `/sdcard/...`, or Git Bash rewrites it into a Windows path. The helpers do it.

**Always pass `-s <serial>`.** The phone is usually listed twice in `adb devices -l`: once by its serial over USB, and once as `adb-<serial>-<id>._adb-tls-connect._tcp` over wireless debugging. With the emulator running there's a third entry. A bare `adb` then fails with "more than one device", or worse, talks to the wrong one, and an emulator's `dumpsys` looks just like a passing result from the phone. The helpers pick the device and set `$adb` to `adb -s <serial>`, preferring USB for the phone. Wireless debugging survives unplugging the cable, but drops when the phone changes Wi-Fi network or restarts.

- **`unauthorized`:** ask the user to accept the USB-debugging prompt on the phone.
- **An install fails with `INSTALL_FAILED_USER_RESTRICTED`:** HyperOS's *Install via USB* (developer options) is off, or its prompt on the phone was declined. Ask the user.

## Starting and stopping the emulator

Start it in the background (`run_in_background`), from Git Bash:

```bash
/d/Android/Sdk/emulator/emulator.exe -avd PillsReminder_API_36 -no-snapshot-save -no-boot-anim
```

- Add `-no-window -no-audio` when the user doesn't need to see it.
- `-no-snapshot-save` boots cold each time, so no half-way state carries over. Installed apps and their data are kept: they live on the emulator's disk, not in the snapshot.
- Then wait for it: `source .claude/skills/phone/helpers.sh emulator && wait_boot`.
- Stop it with `$adb emu kill`.
- Only one instance of an AVD can run at a time, unless every instance, the first included, was started with `-read-only` (IAmOk).

## Package names

Set in P1 (P1-D1, P1-D2). If they ever change, change them here and in [helpers.sh](helpers.sh).

- Debug build: `io.github.davamix.pillsreminder.debug`
- Release build: `io.github.davamix.pillsreminder`
- Activity: `io.github.davamix.pillsreminder.MainActivity`
- Instrumented tests (debug): `io.github.davamix.pillsreminder.debug.test`, runner `androidx.test.runner.AndroidJUnitRunner`

## Helpers

[helpers.sh](helpers.sh) holds the Bash functions used most. Load it at the start of every Git Bash call that uses them, from the repo root, naming the device; functions don't carry over between calls:

```bash
source .claude/skills/phone/helpers.sh phone       # or: emulator
```

With no argument it takes the only device connected, and refuses when both the phone and the emulator are. It fails if the device isn't there, so chain the next command with `&&`. It sets `$adb`, `$serial`, `$device`, `$pkg` (the debug package), `$release_pkg`, `$activity` and `$runner`. The helpers read `$pkg` when they run, so `pkg=$release_pkg` points them at the release app; `end_process`, `instrument` and `run-as` work only on the debug app.

| Function | What it does |
|---|---|
| `use_device phone\|emulator` | Switches device within the same call |
| `gradle <tasks>` | Runs `./gradlew` quietly, on the chosen device (it sets `ANDROID_SERIAL`), with the output in `build/gradle.log`. On failure it prints the errors. It returns Gradle's own exit code, so `gradle … && …` stops when the build fails. |
| `instrument [classes] [-e key value]` | Runs instrumented tests and keeps the app's data: the whole suite with no class; or classes and methods separated by commas, with the package prefix optional. On the phone, it first checks `allow_test_start`'s permission and stops if it's missing. It gives up after `INSTRUMENT_TIMEOUT` seconds (default 900) and says what to look for, rather than hanging. |
| `texts` | Every text on screen, on one line |
| `wait_for <pattern> [seconds]` | Waits until a text matching the grep pattern is on screen (default 30 s) |
| `tap_text <text> [last]` | Taps the node with exactly that text; `last` picks the last match |
| `launch` | Opens the app as its launcher icon does |
| `shot <file>` | Saves a screenshot to a local file, to look at with Read |
| `force_stop` | Force-stops the app. **It cancels every alarm the app has set** (see "HyperOS and Android pitfalls"). |
| `end_process` | Ends the app's process as Android does to free memory, keeping its alarms. SIGKILL through `run-as` on the emulator; on the phone, HyperOS refuses that and it falls back to `am kill`. It says which worked. |
| `allow_test_start` | On the phone: lets the debug app open its activities from the background, so instrumented tests don't hang. Run it after each fresh install (see "HyperOS and Android pitfalls"). |
| `alarms` | The app's pending alarms, one block each, from `dumpsys alarm`. Nothing printed means none is set. |
| `notifications` | The app's notifications in the shade: id, channel, title and text |
| `reminder_health` | What reminders depend on, as Android sees it: notification and exact-alarm permissions, battery optimisation, standby bucket |
| `with_dark_mode <command>` | Emulator only: runs the command in dark mode, then puts night mode back as it was, even if the command fails |
| `with_landscape <command>` | Emulator only: runs the command with the display forced into landscape, then puts auto-rotate and the rotation back as they were. For checking that the app stays in portrait. |
| `doze_on` / `doze_off` / `doze_state` | Forces deep Doze while USB stays connected, undoes it, and prints the deep and light states |
| `wait_boot [seconds]` | Waits until the device has finished booting (default 180 s) |
| `wait_for_log <tag> <regex> [seconds]` | Waits until a log line from that tag matches (default 60 s). Clear the log first with `$adb logcat -c`. |

**All checked.** On 2026-11-19, before the app existed, against system packages: `use_device` (with the phone and the emulator both connected), `texts`, `wait_for`, `tap_text`, `shot`, `notifications`, `reminder_health`, `doze_on`, `doze_off`, `doze_state`, `wait_boot` and `wait_for_log` on the emulator, and `alarms` on both, with the same format on Android 13 (HyperOS) and 16. On 2026-11-20 (P1 T8), with the app, on both devices: `gradle`, `instrument`, `launch`, `force_stop`, `end_process` and `allow_test_start`. The phone was on wireless debugging only. After P1's skills review: `instrument`'s permission check and deadline on the phone, and `with_dark_mode` and `with_landscape` on the emulator.

For example, install the debug build on the emulator, open it and read the screen:

```bash
source .claude/skills/phone/helpers.sh emulator && gradle installDebug && launch && wait_for 'Next takes' 60 && texts
```

**Never pipe Gradle into `grep` or `tail` before `&&`.** The pipe returns the last command's exit code, so `./gradlew … | tail && adb shell am instrument …` runs the old test APK after a failed build. `set -o pipefail` doesn't help either: `grep -v` returns 1 when it has nothing left to print. Use `gradle`, or write the output to a file and check `$?` (a lesson from Workout Timer).

## Common tasks

Gradle finds adb by itself, so the Gradle tasks need no path.

| Task | Command |
|---|---|
| Build and install | `gradle installDebug` |
| Instrumented tests, keeping the app's data | `gradle installDebug installDebugAndroidTest && instrument` runs the whole suite on the installed app, and `instrument <classes>` runs some. It passed when it ends with `OK (N tests)`. Read the summary rather than grepping for `OK`: a filter hides errors such as `no devices/emulators found`. **On the phone, after a fresh install, run `allow_test_start` first**; `instrument` checks it and stops if it's missing. |
| Dark mode and rotation checks | On the emulator, `with_dark_mode shot <file>` and `with_landscape shot <file>`, then look at the pictures. They put the settings back as they were. On the phone, ask the user. |
| Instrumented tests, uninstalling afterwards | `./gradlew connectedDebugAndroidTest`. It runs on **every** connected device unless `ANDROID_SERIAL` is set, and uninstalls the app when it ends, which deletes its treatments. Run `gradle installDebug` again afterwards. **Don't use it on the phone:** the uninstall also resets `allow_test_start`'s permission, so the next run hangs. Results: "Instrumented test results" below. |
| Check the app's alarms | `alarms`. After saving a treatment, there should be an alarm for the next take of each medicine; after it fires, one for the take after. |
| Check the app's notifications | `notifications` |
| Check what reminders need | `reminder_health`. HyperOS's Autostart can't be read through adb: ask the user, or read it in the app's reminder check (spec `RM-9`). |
| Grant or revoke the notification permission | `$adb shell pm grant $pkg android.permission.POST_NOTIFICATIONS`, or `pm revoke`. Fine on the emulator; on the phone, ask first, and put it back. |
| Exact-alarm permission | `$adb shell cmd appops set $pkg SCHEDULE_EXACT_ALARM allow` (or `deny`, or `default`). Same rule: ask before changing it on the phone. |
| Put the device in Doze | `doze_on`, then `doze_state` must say `deep=IDLE`. **Always** run `doze_off` afterwards, from a `trap` in a script so a failure can't leave it on. On the phone, ask first. |
| Simulate Android ending the process | `end_process`, never `force_stop`. Then check that `alarms` still lists the next take. On the phone it uses `am kill`, which IAmOk found unreliable on this phone: `end_process` checks `pidof` and says if the process survived. |
| Time a cold start | `force_stop`, then `$adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $pkg/$activity`, and read `TotalTime`. Keep the action and category, so a later `launch` brings back the same task. |
| Restart the device | Emulator: `$adb reboot`, then `wait_boot`. Phone: ask the user first, and tell them they will need to unlock it. Until the phone is unlocked once after a restart, the app's own storage is locked, and Android sends the app `BOOT_COMPLETED` only after that. Wait with `wait_boot` in the background. |
| Swipe the app away from Recents | Ask the user to do it on the phone. On HyperOS this ends the process but keeps the alarms (IAmOk). |
| Change the time zone or the clock | The shell can't: `cmd time_zone_detector suggest_manual_time_zone` throws a `SecurityException` (no `SUGGEST_MANUAL_TIME_AND_ZONE`), and `adb root` isn't available. On the emulator, drive *Settings > System > Date & time* with `tap_text`, after turning off automatic time and time zone. Put them back afterwards. Never change the phone's clock. Most time logic is tested in unit tests with an injected clock and zone instead. |
| Read the app's private files (debug build only) | `$adb shell run-as $pkg ls -la files`. To copy a file to the PC, use `exec-out`, which keeps the bytes: `$adb exec-out run-as $pkg cat <path> > <local file>`. **Never** `adb shell "run-as … cat … > /sdcard/x"` and then `adb pull`: on this phone it gave a file of zero bytes, while reporting success (IAmOk). Check the size of what you copied. |
| App logs | `$adb logcat -d --pid=$($adb shell pidof -s $pkg) \| tail -200`. Clear the log first with `$adb logcat -c`. |
| Crash logs | `$adb logcat -d -b crash \| tail -100` |
| Battery level | `$adb shell dumpsys battery \| grep -e level -e powered`. Check it before a long run on the phone. |
| Screenshot | `shot <scratchpad>/pr.png`, then Read the PNG. For a picture to publish, use the emulator, with only example data (spec §4.4), and crop out the status bar. Never publish a screenshot of the phone: its status bar shows the user's notifications, and the app may show real treatments. |
| Tap | `tap_text <text>`, or `$adb shell input tap <x> <y>` with pixel coordinates from a screenshot |
| Send the app to the background | `$adb shell input keyevent KEYCODE_HOME` |

## Instrumented test results

After `./gradlew connectedDebugAndroidTest`, read the results from the XML instead of the Gradle output. The totals, then one line per failing test with its class, name and the first 300 characters of its message:

```bash
f=$(ls app/build/outputs/androidTest-results/connected/debug/*.xml | head -1)
grep -o '<testsuites tests="[0-9]*" failures="[0-9]*" errors="[0-9]*" skipped="[0-9]*"' "$f" | sed 's/<testsuites //'
awk '/<testcase /{match($0,/ name="[^"]*"/); n=substr($0,RSTART+7,RLENGTH-8); match($0,/classname="[^"]*"/); c=substr($0,RSTART+11,RLENGTH-12); sub(/.*\./,"",c)} /<(failure|error)/{l=$0; sub(/.*<(failure|error)[^>]*>/,"",l); if (l ~ /^[[:space:]]*$/) getline l; print c" > "n"\n    "substr(l,1,300)}' "$f"
```

The path assumes an `:app` module; P1 confirms it. `instrument` prints its own summary instead: one line per class with a dot per test, then each failure with its message, and `OK (N tests)` or `FAILURES!!!`.

## Checking a screen in every state

To see how a screen looks in each of its states, draw them in a throwaway test rather than driving the app to each one. In Workout Timer this caught a layout that didn't fit, and a text colour bug, before any manual check. This assumes Jetpack Compose; adapt it if the plan chooses otherwise.

1. Write a JUnit 4 test in `app/src/androidTest/kotlin/io/github/davamix/pillsreminder/probe/<Name>Gallery.kt` with `createComposeRule()`. Hold the state in a `mutableStateOf`, and set the content once, inside the app's theme.
2. For each state: set it, call `compose.waitForIdle()`, then save `compose.onRoot().captureToImage().asAndroidBitmap()` as a PNG in `targetContext.cacheDir`.
3. Run it with `gradle installDebug installDebugAndroidTest && instrument probe.<Name>Gallery`.
4. Copy each file with `$adb exec-out run-as $pkg cat cache/<file>.png > <scratchpad>/<file>.png`, and look at it with Read.
5. Delete the test: it never goes in a commit.

Draw each state in light and dark theme, and at the largest system font size the spec asks for (`NF-7`).

## Measuring device behaviour

When a design depends on how the phone behaves (when an alarm really fires in Doze, what happens after a restart or a swipe from Recents, what HyperOS blocks), measure it with a throwaway probe before relying on it:

1. Write a JUnit 4 test in `app/src/androidTest/kotlin/io/github/davamix/pillsreminder/probe/<Name>Probe.kt`, or a debug-only screen. It logs what it measures with `Log.i("<Name>Probe", …)`, with both `System.currentTimeMillis()` and `SystemClock.elapsedRealtime()` for times.
   - A probe can't measure its own process ending: it runs inside that process. For alarms that must survive the process, set them from the probe, end the process with `end_process`, and read what the app's receiver logs when the alarm fires.
   - While the probe waits, adb can act on the device, and the user can act on the phone. Mark each step in the same log with `$adb shell log -p i -t <Name>Probe '"MARK <step>"'`.
2. Run `gradle installDebug installDebugAndroidTest`. Clear the log with `$adb logcat -c`, then run the probe with `instrument probe.<Name>Probe`, in the background if it waits.
3. Wait for its lines with `wait_for_log <Name>Probe '<regex>'` rather than sleeping, and read the results with `$adb logcat -d -s <Name>Probe:I`. The log wraps: for a run of hours, have the app append its measurements to a file in its own storage instead, and copy it with `exec-out`.
4. Repeat each measurement 2–3 times, and record the device, its Android and HyperOS versions, and whether its power settings were stock.
5. Record the numbers in the handover, or in the SPEC if the design depends on them. Then delete the probe: it never goes in a commit.

## HyperOS and Android pitfalls

Found on this same phone by the IAmOk project ([device matrix](https://github.com/davamix/IAmOk/blob/main/docs/testing/device-matrix.md)):

- **Autostart is off** by default for an app installed outside the store. Without it, HyperOS may not let an alarm or a broadcast start the app after it is swiped away or the phone restarts. It is the most common reason alarm apps stop working on Xiaomi phones. adb can't read it: the user checks it in the app's settings in HyperOS.
- **Battery saver** per app is restricted by default. "No restrictions" is the permissive setting.
- **Test with stock settings first, and record the result.** Only then relax the settings and repeat. The difference is the finding: it says what the app's reminder check (`RM-9`) must ask the user to change.
- **`am force-stop` cancels every alarm,** and Android delivers nothing to the app until it is opened again. Never use it to stand in for "Android ended the app". `end_process` does that.
- **HyperOS blocks activities started from the background.** It logs `MIUILOG- Permission Denied Activity` and starts nothing, with no error to the caller. An instrumented test then hangs: the test runner restarts the app's process in the background, and its activity never opens, even with the app on screen first. The fix is HyperOS's per-app permission "Display pop-up windows while running in the background" (op 10021), for the debug app only: `allow_test_start`. Uninstalling the app resets it. The same block may matter in P5 and P7, wherever the app starts an activity that the user didn't just tap (P1 T6).
- **`run-as` can't signal the app's process** on this phone: SELinux denies it (`avc: denied { sigkill }` from `runas_app` to `untrusted_app`), and toybox's `kill` only says "unknown pid". `end_process` falls back to `am kill`, which worked in P1 T8 once the app was in the background.
- **`am kill` doesn't always end the process** on this phone (IAmOk: it once survived six attempts). Check with `pidof`, as `end_process` does.
- **Swiping the app away** from Recents ends its process, but doesn't cancel its alarms or clear its notifications.
- **`dumpsys deviceidle force-idle` can stop short of deep idle** when the screen is off, because HyperOS holds the phone in its own dozing state. Check `doze_state`, and the "Idling history" in `$adb shell dumpsys deviceidle`. An alarm that fires on a phone that never idled proves nothing.
- **MIUI optimisation** (developer options) changes permission and notification behaviour. Leave it as it is.

## Notes

- **Waiting:** a foreground `sleep` is blocked. Wait for a state with `wait_for`, `wait_for_log` or `wait_boot`; a short pause between adb steps can run on the device, with `$adb shell sleep 2`.
- **Removing files on the device:** `run-as … sh -c 'rm …'` is refused by Claude Code's safety check, because it can't read the script. Leave throwaway files in the app's cache; uninstalling removes them.
- **`am instrument` output** has `\r\n` line endings and ends with blank lines. `instrument` cleans it up; by hand, pipe it through `tr -d '\r' | grep -v '^$'`.
- **A posted test notification** from the shell (`cmd notification post`) can't be removed by the shell. Post test notifications only on the emulator.
