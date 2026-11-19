# Helpers for the phone skill, for Git Bash. Load them at the start of every Bash call that uses them, from the repo
# root (shell functions and variables don't carry over between calls):
#
#   source .claude/skills/phone/helpers.sh             # the only device connected
#   source .claude/skills/phone/helpers.sh phone       # the POCO F3, over USB if possible
#   source .claude/skills/phone/helpers.sh emulator    # the running emulator
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
# It passed when the summary ends with "OK (N tests)".
instrument() {
    local spec=""
    if [ $# -gt 0 ] && [ "$1" != "-e" ]; then
        spec=$(echo "$1" | tr ',' '\n' | sed "s/^\($app_id\.\)\{0,1\}/$app_id./" | paste -sd, -)
        shift
    fi
    $adb shell am instrument -w ${spec:+-e class "$spec"} "$@" "$runner" | tr -d '\r' | grep -v '^$' | grep -v '^\s*at ' | tail -40
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

# Ends the app's process the way Android does to free memory, keeping its alarms. Debug build only (run-as). Home
# first, so the app isn't in the foreground.
end_process() {
    $adb shell input keyevent KEYCODE_HOME
    $adb shell sleep 1
    local pid
    pid=$($adb shell pidof $pkg | tr -d '\r')
    [ -z "$pid" ] && { echo "end_process: the app isn't running"; return 1; }
    $adb shell run-as $pkg kill -9 "$pid"
    $adb shell sleep 1
    if [ -z "$($adb shell pidof $pkg | tr -d '\r')" ]; then echo "process $pid ended"; else echo "process $pid still running"; return 1; fi
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

# Waits until a log line from TAG matches REGEX, for at most SECONDS (default 60), without sleeping. Clear the log
# first with `$adb logcat -c`, so an older line doesn't count.
wait_for_log() {
    timeout "${3:-60}" $adb logcat -s "$1:V" -e "$2" -m 1 > /dev/null
    local rc=$?
    [ $rc -ne 0 ] && echo "timeout: no '$2' from $1 after ${3:-60} s"
    return $rc
}

use_device "$@"
