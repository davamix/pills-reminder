# Helpers for the phone skill, for Git Bash. Load them at the start of every Bash call that uses them, from the repo
# root (shell functions and variables don't carry over between calls):
#
#   source .claude/skills/phone/helpers.sh             # the only device connected
#   source .claude/skills/phone/helpers.sh phone       # the POCO F3, over USB if possible
#   source .claude/skills/phone/helpers.sh emulator    # the running emulator
#   source .claude/skills/phone/helpers.sh none        # no device yet, e.g. before wait_emulator
#
# Each function says what it does above it. SKILL.md lists them under "Helpers".

export MSYS_NO_PATHCONV=1
adb_bin=/d/Android/Sdk/platform-tools/adb.exe
emulator_bin=/d/Android/Sdk/emulator/emulator.exe
avd=PillsReminder_API_36
phone_model=M2012K11AG

# The app's IDs. Assumed until P1 creates the project: if P1 chooses others, change them here and in SKILL.md.
app_id=io.github.davamix.pillsreminder
pkg=$app_id.debug
release_pkg=$app_id
activity=$app_id.MainActivity
runner=$pkg.test/androidx.test.runner.AndroidJUnitRunner
gradle_log=build/gradle.log

# Picks the device to talk to and sets $serial, $device ("phone" or "emulator") and $adb ("adb -s <serial>").
# The phone is usually listed twice, over USB and over wireless debugging; USB is preferred. With no argument, it
# takes the only device connected, and refuses when both the phone and the emulator are.
use_device() {
    local want=${1:-auto} list phone_usb phone_wifi emu
    list=$("$adb_bin" devices -l | tr -d '\r' | awk 'NR > 1 && $2 == "device"')
    phone_usb=$(echo "$list" | grep "model:$phone_model" | grep -v '_adb-tls-connect' | awk '{ print $1 }' | head -1)
    phone_wifi=$(echo "$list" | grep "model:$phone_model" | grep '_adb-tls-connect' | awk '{ print $1 }' | head -1)
    emu=$(echo "$list" | awk '$1 ~ /^emulator-/ { print $1 }' | head -1)
    if [ "$want" = auto ]; then
        if [ -n "$phone_usb$phone_wifi" ] && [ -n "$emu" ]; then
            echo "use_device: both the phone and the emulator are connected; say which: phone or emulator" >&2
            return 1
        elif [ -n "$phone_usb$phone_wifi" ]; then want=phone
        elif [ -n "$emu" ]; then want=emulator
        else
            echo "use_device: no device connected (adb devices lists none as 'device')" >&2
            return 1
        fi
    fi
    case $want in
        phone)
            serial=${phone_usb:-$phone_wifi}
            [ -z "$serial" ] && { echo "use_device: the phone isn't connected" >&2; return 1; }
            [ -z "$phone_usb" ] && echo "use_device: the phone is connected over wireless debugging only" >&2 ;;
        emulator)
            serial=$emu
            [ -z "$serial" ] && { echo "use_device: no emulator is running; start it as SKILL.md says" >&2; return 1; } ;;
        none)
            # Loads the helpers without picking a device, for wait_emulator.
            return 0 ;;
        *)
            echo "use_device: say phone or emulator, not '$want'" >&2
            return 1 ;;
    esac
    device=$want
    adb="$adb_bin -s $serial"
}

# Runs ./gradlew with the given tasks, quietly. The output goes to build/gradle.log, and on failure the errors are
# printed. Returns Gradle's own exit code, so `gradle installDebug && instrument …` stops when the build fails. A pipe
# such as `./gradlew … | grep … | tail` would return the last command's code instead. Gradle installs on every
# connected device unless ANDROID_SERIAL is set, so this sets it to the device picked above.
gradle() {
    mkdir -p build
    ANDROID_SERIAL=$serial ./gradlew "$@" --console=plain -q > "$gradle_log" 2>&1
    local rc=$?
    if [ $rc -ne 0 ]; then
        echo "gradle $* failed (exit $rc). Full output: $gradle_log"
        grep -v -i 'unsafe\|^WARNING\|^\s*$' "$gradle_log" | head -40
    fi
    return $rc
}

# Runs instrumented tests and keeps the app's data, unlike connectedDebugAndroidTest. Install both APKs first:
# `gradle installDebug installDebugAndroidTest && instrument …`.
#   instrument                                   the whole suite
#   instrument ui.home.HomeScreenTest[#method][,…] classes or methods, the package prefix optional
#   instrument <class> -e <key> <value>          with arguments for the test
# It passed when the summary ends with "OK (N tests)". On the phone, it first makes sure that HyperOS lets the test
# runner start the app's activities, granting op 10021 to the debug app when an install, an update or a restart has
# reset it (allow_test_start), and checks that the phone isn't locked, because otherwise the run hangs with no output
# (P1 T6, P2, P3). It gives up after INSTRUMENT_TIMEOUT seconds (default 900) and says what to look for, instead of
# waiting forever. It prints nothing until the run ends.
instrument() {
    local spec="" limit=${INSTRUMENT_TIMEOUT:-900}
    if [ "$device" = phone ] && ! $adb shell cmd appops get $pkg 10021 2> /dev/null | grep -q ': allow'; then
        echo "instrument: op 10021 isn't allowed for $pkg (an install, an update or a restart resets it): granting it."
        allow_test_start > /dev/null
        if ! $adb shell cmd appops get $pkg 10021 2> /dev/null | grep -q ': allow'; then
            echo "instrument: op 10021 is still not allowed, so HyperOS would block the test runner. Is $pkg installed?"
            return 1
        fi
    fi
    if [ "$device" = phone ] && $adb shell dumpsys window 2> /dev/null | grep -q 'isKeyguardShowing=true'; then
        echo "instrument: the phone is locked. Ask the user to unlock it, then run this again. While the lock screen"
        echo "  shows, HyperOS refuses the tests' activity ('MIUILOG- Permission Denied Activity KeyguardLocked'), and"
        echo "  the run hangs until the deadline (P2)."
        return 1
    fi
    if [ $# -gt 0 ] && [ "$1" != "-e" ]; then
        spec=$(echo "$1" | tr ',' '\n' | sed "s/^\($app_id\.\)\{0,1\}/$app_id./" | paste -sd, -)
        shift
    fi
    timeout "$limit" $adb shell am instrument -w ${spec:+-e class "$spec"} "$@" "$runner" | tr -d '\r' | grep -v '^$' | grep -v '^\s*at ' | tail -40
    local rc=${PIPESTATUS[0]}
    if [ "$rc" -eq 124 ]; then
        echo "instrument: no result after $limit s. The test runner may still be running on the device."
        echo "  On the phone, look for 'MIUILOG- Permission Denied Activity' in \$adb logcat -d: HyperOS blocked a test's"
        echo "  activity. To end the run, force_stop (it also cancels the debug app's alarms)."
        return 124
    fi
    return 0
}

# Prints every text on screen, on one line. It fails on a screen that never goes idle, such as one with an animation.
texts() {
    $adb exec-out uiautomator dump /dev/tty 2>/dev/null | grep -o 'text="[^"]\+"' | sed 's/^text=//' | tr '\n' ' '
    echo
}

# Waits until PATTERN (a grep pattern) is on screen, for at most SECONDS (default 30), without sleeping: each dump
# takes 1-2 s. Fails with a message on timeout.
wait_for() {
    local end=$((SECONDS + ${2:-30}))
    until $adb exec-out uiautomator dump /dev/tty 2>/dev/null | grep -q "$1"; do
        [ $SECONDS -ge $end ] && { echo "timeout: '$1' not on screen after ${2:-30} s"; return 1; }
    done
    return 0
}

# Taps the node whose text is exactly TEXT. If several match, "last" takes the last one, such as a dialog's button
# over the screen's button of the same name; the default is the first.
tap_text() {
    local bounds
    bounds=$($adb exec-out uiautomator dump /dev/tty 2>/dev/null | grep -o "text=\"$1\"[^>]*bounds=\"[^\"]*\"" |
        if [ "$2" = last ]; then tail -1; else head -1; fi | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | paste -sd' ' -)
    [ -z "$bounds" ] && { echo "tap_text: '$1' not on screen"; return 1; }
    local x1 y1 x2 y2
    read -r x1 y1 x2 y2 <<< "$bounds"
    $adb shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
}

# Opens the app as its launcher icon does: if its task exists, it comes to the front as it was.
launch() {
    $adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$pkg/$activity" > /dev/null 2>&1
}

# Saves a screenshot to FILE, a local path such as one in the scratchpad. Look at it with Read. exec-out keeps the
# bytes as they are, so no file is left on the device.
shot() {
    $adb exec-out screencap -p > "$1" && [ -s "$1" ] || { echo "shot: no image saved"; return 1; }
}

# Force-stops the app. WARNING: this also cancels every alarm the app has set, and Android then delivers nothing to it
# until it is opened again. To end the process the way Android does, keeping the alarms, use end_process.
force_stop() {
    $adb shell am force-stop $pkg
}

# Ends the app's process the way Android does to free memory, keeping its alarms. Home first, so the app is in the
# background. It sends SIGKILL through run-as (debug build), as the low-memory killer does. HyperOS's SELinux policy
# refuses that ("avc: denied { sigkill }" from runas_app, P1 T8), so on the phone it falls back to `am kill`, which ends
# a background app's process without cancelling its alarms. It says which one worked.
end_process() {
    $adb shell input keyevent KEYCODE_HOME
    $adb shell sleep 2
    local pid
    pid=$($adb shell pidof $pkg | tr -d '\r')
    [ -z "$pid" ] && { echo "end_process: the app isn't running"; return 1; }
    $adb shell run-as $pkg kill -9 "$pid" > /dev/null 2>&1
    $adb shell sleep 1
    if [ -z "$($adb shell pidof $pkg | tr -d '\r')" ]; then echo "process $pid ended (SIGKILL)"; return 0; fi
    $adb shell am kill $pkg
    $adb shell sleep 2
    if [ -z "$($adb shell pidof $pkg | tr -d '\r')" ]; then echo "process $pid ended (am kill)"; return 0; fi
    echo "end_process: process $pid still running"
    return 1
}

# Lets the debug app open its activities while in the background, which HyperOS refuses by default. Without it, an
# instrumented test hangs on the phone: the runner starts the app's process in the background, HyperOS logs
# "MIUILOG- Permission Denied Activity", and the test waits forever (P1 T6). It is HyperOS's per-app permission
# "Display pop-up windows while running in the background", op 10021, for the debug app only. An uninstall, an update
# (adb install -r) and a restart of the phone all reset it (P3); instrument grants it again by itself. The user agreed
# to it on 2026-11-20, and to instrument granting it after P3.
allow_test_start() {
    $adb shell cmd appops set $pkg 10021 allow
    echo "MIUIOP(10021) for $pkg: $($adb shell cmd appops get $pkg 10021 | tr -d '\r' | head -1)"
}

# Lists the app's pending alarms from `dumpsys alarm`: one block per alarm, with its type (RTC_WAKEUP, …), its time
# (origWhen), its window (0 for an exact alarm) and the PendingIntent. Nothing printed means no alarm is set.
alarms() {
    $adb shell dumpsys alarm | tr -d '\r' | awk -v p="$pkg" '
        /^ +[A-Z_]+ #[0-9]+: Alarm\{/ { show = index($0, " " p "}") > 0; if (show) print; next }
        !/^      / { show = 0 }
        show { print }'
}

# Lists the app's notifications in the shade, one per line: id, channel, title and text.
notifications() {
    $adb shell dumpsys notification --noredact | tr -d '\r' | awk -v p="$pkg" '
        function flush() {
            if (show) print id " " ch (t != "" ? " title=\"" t "\"" : "") (x != "" ? " text=\"" x "\"" : "")
            show = 0; t = ""; x = ""
        }
        /^ +NotificationRecord\(/ {
            flush()
            if (index($0, "pkg=" p " ") > 0) {
                show = 1
                match($0, /id=-?[0-9]+/); id = substr($0, RSTART, RLENGTH)
                match($0, /channel=[^ ]+/); ch = substr($0, RSTART, RLENGTH)
            }
            next
        }
        show && /^ +android\.title=/ { t = $0; sub(/^ +android\.title=[A-Za-z]+ \(/, "", t); sub(/\)$/, "", t) }
        show && /^ +android\.text=/ { x = $0; sub(/^ +android\.text=[A-Za-z]+ \(/, "", x); sub(/\)$/, "", x) }
        END { flush() }'
}

# Prints what reminders depend on, as Android sees it: the notification permission, the exact-alarm permissions,
# whether the app is exempt from battery optimisation, and its standby bucket (10 active ... 45 restricted). HyperOS's
# Autostart can't be read through adb: ask the user.
reminder_health() {
    echo "permissions:"
    $adb shell dumpsys package $pkg | tr -d '\r' |
        grep -E 'android\.permission\.(POST_NOTIFICATIONS|SCHEDULE_EXACT_ALARM|USE_EXACT_ALARM|RECEIVE_BOOT_COMPLETED): granted' |
        sed -E 's/^ +/  /' | sort -u
    echo "appops SCHEDULE_EXACT_ALARM: $($adb shell cmd appops get $pkg SCHEDULE_EXACT_ALARM | tr -d '\r' | paste -sd';' -)"
    if $adb shell dumpsys deviceidle whitelist | tr -d '\r' | grep -q ",$pkg,"; then
        echo "battery optimisation: exempt"
    else
        echo "battery optimisation: optimised (not exempt)"
    fi
    echo "standby bucket: $($adb shell am get-standby-bucket $pkg | tr -d '\r')"
}

# Puts the device into deep Doze now, as if it had been idle and unplugged for a long time: `battery unplug` lets
# Doze start while USB stays connected. Always undo it with doze_off. On HyperOS, force-idle from a screen-off phone
# can stop short of deep idle (IAmOk): check doze_state.
doze_on() {
    $adb shell dumpsys battery unplug
    $adb shell dumpsys deviceidle force-idle
}

# Undoes doze_on: leaves forced idle and gives the battery state back to the hardware.
doze_off() {
    $adb shell dumpsys deviceidle unforce
    $adb shell dumpsys battery reset
}

# Prints the deep and light Doze states, such as ACTIVE or IDLE.
doze_state() {
    echo "deep=$($adb shell dumpsys deviceidle get deep | tr -d '\r') light=$($adb shell dumpsys deviceidle get light | tr -d '\r')"
}

# Runs COMMAND with the emulator in dark mode, then puts night mode back as it was, even if COMMAND fails. Emulator
# only: on the phone, ask the user to switch it. For example: `with_dark_mode shot <scratchpad>/dark.png`.
with_dark_mode() {
    [ "$device" = emulator ] || { echo "with_dark_mode: emulator only; ask the user to switch the phone"; return 1; }
    local before rc
    before=$($adb shell cmd uimode night | tr -d '\r' | awk '{ print $NF }')
    $adb shell cmd uimode night yes > /dev/null
    $adb shell sleep 2
    "$@"
    rc=$?
    $adb shell cmd uimode night "$before" > /dev/null
    echo "with_dark_mode: night mode back to '$($adb shell cmd uimode night | tr -d '\r' | awk '{ print $NF }')'"
    return $rc
}

# Runs COMMAND with the emulator's display forced into landscape, then puts auto-rotate and the rotation back as they
# were, even if COMMAND fails. Emulator only. For checking that the app stays in portrait (NF-7): for example
# `with_landscape shot <scratchpad>/landscape.png`, then look at the picture.
with_landscape() {
    [ "$device" = emulator ] || { echo "with_landscape: emulator only; ask the user to turn the phone"; return 1; }
    local accel user rc
    accel=$($adb shell settings get system accelerometer_rotation | tr -d '\r')
    user=$($adb shell settings get system user_rotation | tr -d '\r')
    $adb shell settings put system accelerometer_rotation 0
    $adb shell settings put system user_rotation 1
    $adb shell sleep 2
    "$@"
    rc=$?
    $adb shell settings put system user_rotation "$user"
    $adb shell settings put system accelerometer_rotation "$accel"
    echo "with_landscape: accelerometer_rotation back to $($adb shell settings get system accelerometer_rotation | tr -d '\r'), user_rotation to $($adb shell settings get system user_rotation | tr -d '\r')"
    return $rc
}

# Waits until the emulator just started (SKILL.md, "Starting and stopping the emulator") is online, picks it as the
# device, and waits until it has booted, for at most SECONDS each (default 300 and 180). Load the helpers with `none`:
# right after the start the emulator isn't listed yet, so `helpers.sh emulator` would fail. It doesn't use
# `adb -e wait-for-device`, which fails with "more than one emulator" while the phone is on wireless debugging (P2).
#   source .claude/skills/phone/helpers.sh none && wait_emulator
wait_emulator() {
    local end=$((SECONDS + ${1:-300}))
    until "$adb_bin" devices | tr -d '\r' | awk '$1 ~ /^emulator-/ && $2 == "device"' | grep -q .; do
        [ $SECONDS -ge $end ] && { echo "wait_emulator: no emulator online after ${1:-300} s" >&2; return 1; }
        sleep 2
    done
    use_device emulator && wait_boot "${2:-180}"
}

# Waits until the device has finished booting, for at most SECONDS (default 180). For the emulator, or the phone
# after a reboot.
wait_boot() {
    local end=$((SECONDS + ${1:-180}))
    $adb wait-for-device
    until [ "$($adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
        [ $SECONDS -ge $end ] && { echo "timeout: not booted after ${1:-180} s"; return 1; }
        $adb shell sleep 2 2>/dev/null || sleep 2
    done
    return 0
}

# After ./gradlew connectedDebugAndroidTest: the totals from its XML results, then one line per failing test with its
# class and name, and the first 300 characters of its message. No device needed: load the helpers with `none`.
connected_results() {
    local f
    f=$(ls app/build/outputs/androidTest-results/connected/debug/*.xml 2> /dev/null | head -1)
    [ -z "$f" ] && { echo "connected_results: no results under app/build/outputs/androidTest-results" >&2; return 1; }
    grep -o '<testsuites tests="[0-9]*" failures="[0-9]*" errors="[0-9]*" skipped="[0-9]*"' "$f" | sed 's/<testsuites //'
    awk '/<testcase /{match($0,/ name="[^"]*"/); n=substr($0,RSTART+7,RLENGTH-8); match($0,/classname="[^"]*"/); c=substr($0,RSTART+11,RLENGTH-12); sub(/.*\./,"",c)} /<(failure|error)/{l=$0; sub(/.*<(failure|error)[^>]*>/,"",l); if (l ~ /^[[:space:]]*$/) getline l; print c" > "n"\n    "substr(l,1,300)}' "$f"
}

# Waits until a log line from TAG matches REGEX, for at most SECONDS (default 60), without sleeping. Clear the log
# first with `$adb logcat -c`, so an older line doesn't count.
wait_for_log() {
    timeout "${3:-60}" $adb logcat -s "$1:V" -e "$2" -m 1 > /dev/null
    local rc=$?
    [ $rc -ne 0 ] && echo "timeout: no '$2' from $1 after ${3:-60} s"
    return $rc
}

use_device "$@"
