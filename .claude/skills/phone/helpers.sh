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
    # Some tests assume the normal font size: at 1.4, P6's EndAndDeleteTest scrolled its status out of view (P7 T8).
    # A warning only; gallery.sh sets INSTRUMENT_ANY_FONT, since it changes the font scale on purpose.
    local scale
    scale=$($adb shell settings get system font_scale 2> /dev/null | tr -d '\r')
    if [ -z "$INSTRUMENT_ANY_FONT" ] && [ -n "$scale" ] && [ "$scale" != null ] && [ "$scale" != 1.0 ]; then
        echo "instrument: warning: the font scale is $scale, not 1.0; a test that fails may only assume the normal size."
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

# --- Reminders (P5): the reminder log, alerts, Doze, and the debug screen ---

# The last N lines (default 12) of the reminder log that debug builds keep: triggers, take keys, instants, each alert's
# lateness (late=…ms). Its times are UTC.
reminder_log() {
    $adb shell run-as $pkg tail -n "${1:-12}" files/reminders-log.txt | tr -d '\r'
}

# Waits until the device's clock reaches HHMMSS (local, 24-hour, such as 101730), checking every 10 s. It reads the
# device's clock: the PC's jumps when Windows corrects it. Run a wait of minutes in the background.
wait_until() {
    until [ "$($adb shell date +%H%M%S | tr -d '\r')" -ge "$1" ]; do sleep 10; done
}

# Waits until the reminder log has an alert for a take planned at HH:MM (local, as in the take's key), for at most
# SECONDS on the device's clock (default 420), then prints the log's last LINES lines (default 8). Run it in the
# background. The pattern is quoted twice, because the device's shell reads the `|` again.
wait_alert() {
    local hm=$1 end
    end=$(($($adb shell date +%s | tr -d '\r') + ${2:-420}))
    until $adb shell run-as $pkg grep -q "'alert [0-9-]*T$hm|'" files/reminders-log.txt 2> /dev/null; do
        if [ "$($adb shell date +%s | tr -d '\r')" -ge "$end" ]; then
            echo "wait_alert: no alert for $hm after ${2:-420} s"
            reminder_log "${3:-8}"
            return 1
        fi
        sleep 10
    done
    reminder_log "${3:-8}"
}

# The app's last N notification alerts (default 5), from logcat's events buffer: local time, the notification's tag, and
# whether Android asked for a vibration (buzz), a sound (beep) and the light (blink). beep=1 doesn't prove the sound
# played: the ring mode may mute it. Filtered to the app, since the buffer holds other apps' notifications, with
# personal data.
alert_events() {
    $adb logcat -d -b events -v time | tr -d '\r' | grep 'notification_alert' | grep "|$pkg|" | tail -n "${1:-5}" |
        sed -E "s/^([0-9-]+ [0-9:.]+) .*\|$pkg\|-?[0-9]+\|(.*)\|[0-9]+,([01]),([01]),([01])(,[0-9]+)*\]$/\1 \2 buzz=\3 beep=\4 blink=\5/"
}

# The activity in front, by name only (package/class): which screen a tap opened, without reading the screen itself,
# which may show the user's apps, as HyperOS's Autostart list does (P7 M1).
top_activity() {
    $adb shell dumpsys activity activities | tr -d '\r' | grep -m1 -o 'topResumedActivity=[^ ]* [^ ]* [^ }]*' | awk '{ print $3 }'
}

# One line about the app's reminders now: the device's time, the screen (Awake, or Dozing when it is off), the process,
# the app's alarms, its reminders in the shade (tags take/…, the summary not counted), and the Doze states.
reminder_state() {
    local pid screen alarm_count shade
    pid=$($adb shell pidof $pkg | tr -d '\r')
    screen=$($adb shell dumpsys power | tr -d '\r' | grep -o 'mWakefulness=[A-Za-z]*' | head -1)
    alarm_count=$(alarms | grep -o 'Alarm{[0-9a-f]*' | sort -u | wc -l)
    shade=$($adb shell dumpsys notification --noredact | tr -d '\r' | grep -c "NotificationRecord(.*pkg=$pkg .*tag=take/")
    echo "$($adb shell date +%T | tr -d '\r') ${screen#mWakefulness=} pid=${pid:-none} alarms=$alarm_count shade=$shade $(doze_state)"
}

# The last N entries (default 20) of Android's idling history, deep Doze and the returns to normal (light Doze left out),
# with the device's local times instead of "-1h2m3s ago". An alarm that fired while the phone never reached deep-idle
# proves nothing about deep Doze.
doze_history() {
    local now z offset
    now=$($adb shell date +%s | tr -d '\r')
    z=$($adb shell date +%z | tr -d '\r')
    offset=$(( (10#${z:1:2} * 3600 + 10#${z:3:2} * 60) * (${z:0:1}1) ))
    $adb shell dumpsys deviceidle | tr -d '\r' | sed -n '/Idling history/,/^  [A-Z]/p' | grep -E 'deep-|normal' |
        tail -n "${1:-20}" | awk -v now="$now" -v off="$offset" '{
            s = $2; sub(/^-/, "", s); t = 0
            if (match(s, /[0-9]+h/)) t += substr(s, RSTART, RLENGTH - 1) * 3600
            if (match(s, /[0-9]+m[0-9]/)) t += substr(s, RSTART, RLENGTH - 2) * 60
            else if (match(s, /[0-9]+m$/)) t += substr(s, RSTART, RLENGTH - 1) * 60
            if (match(s, /[0-9]+s/)) t += substr(s, RSTART, RLENGTH - 1)
            printf "%s %s %s\n", strftime("%H:%M:%S", now - t + off, 1), $1, $3
        }'
}

# Closes the soft keyboard if it is up. A raised keyboard swallows taps on the lower half of the screen; Back with no
# keyboard would leave the activity instead, so it checks first.
keyboard_close() {
    if $adb shell dumpsys input_method | tr -d '\r' | grep -q 'mInputShown=true'; then
        $adb shell input keyevent KEYCODE_BACK
        $adb shell sleep 1
    fi
}

# The centre "x y" of the Nth node (default 1) on screen whose attributes match the grep -E pattern; nothing when no
# node matches (it printed "0 0" before P6, and a tap there lands in the screen's corner).
node_centre() {
    $adb exec-out uiautomator dump /dev/tty 2> /dev/null | grep -o '<node [^>]*>' | grep -E "$1" | sed -n "${2:-1}p" |
        grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | paste -sd' ' - | awk 'NF == 4 { print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}

# Taps the first node whose content description is exactly DESC: an icon button ("Back", "Edit treatment"), or a
# number on Material's time picker dial ("15 hours", "45 minutes"). Compose puts these in content-desc, not text.
tap_desc() {
    local c
    c=$(node_centre "content-desc=\"$1\"")
    [ -z "$c" ] && { echo "tap_desc: '$1' not on screen"; return 1; }
    # shellcheck disable=SC2086
    $adb shell input tap $c
}

# Types TEXT at the end of the Nth text field on screen (from 1, in screen order), after tapping it. Compose text fields
# are EditText nodes whose text is empty until typed; their labels are separate nodes. Spaces go as %s, as `input text`
# needs; avoid quotes and brackets, which the device's shell would read. The keyboard stays up: keyboard_close.
type_field() {
    local c
    c=$(node_centre 'class="android.widget.EditText"' "$1")
    [ -z "$c" ] && { echo "type_field: no text field $1 on screen"; return 1; }
    # shellcheck disable=SC2086
    $adb shell input tap $c
    $adb shell sleep 0.5
    $adb shell input keyevent KEYCODE_MOVE_END
    $adb shell input text "$(printf '%s' "$2" | sed 's/ /%s/g')"
}

# Opens the debug screen (debug builds, "Pills debug"), scrolled to the top, with the keyboard closed.
debug_open() {
    $adb shell am start -n $pkg/$app_id.debug.DebugActivity > /dev/null 2>&1
    $adb shell sleep 2
    keyboard_close
    $adb shell input swipe 540 600 540 2000 200
    $adb shell input swipe 540 600 540 2000 200
    $adb shell sleep 1
}

# Creates a test treatment on the debug screen: every INTERVAL minutes, its first take in FIRST minutes, with any of the
# words two (two medicines), noend and instructions. It deletes the test treatments first. It prints the form as filled,
# then the screen's count and next wake-up. Its first take is FIRST whole minutes after now: create it early in a minute.
debug_create() {
    local interval=$1 first=$2 word two=off noend=off instructions=off
    for word in "${@:3}"; do
        case $word in
            two) two=on ;;
            noend) noend=on ;;
            instructions) instructions=on ;;
            *) echo "debug_create: unknown option '$word'"; return 1 ;;
        esac
    done
    debug_open
    tap_text "Delete test treatments" > /dev/null 2>&1
    $adb shell sleep 1
    debug_field 1 "$interval"
    debug_field 2 "$first"
    debug_switch 1 $two
    debug_switch 2 $noend
    debug_switch 3 $instructions
    $adb exec-out uiautomator dump /dev/tty 2> /dev/null | grep -o '<node [^>]*>' | grep -E 'EditText|checkable="true"' |
        grep -o 'text="[^"]*"\|checked="[a-z]*"' | paste -sd' ' -
    tap_text "Create test treatment"
    $adb shell sleep 2
    texts | grep -o '"Test treatments: [0-9]*"\|"Next wake-up: [^"]*"' | paste -sd' ' -
}

# Types VALUE into the Nth text field of the debug screen, replacing what it held, then closes the keyboard.
debug_field() {
    $adb shell input tap $(node_centre 'class="android.widget.EditText"' "$1")
    $adb shell sleep 1
    $adb shell input keyevent KEYCODE_MOVE_END KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL
    $adb shell input text "$2"
    keyboard_close
}

# Sets the Nth switch of the debug screen (1 two medicines, 2 no end, 3 instructions) to on or off.
debug_switch() {
    local line
    line=$($adb exec-out uiautomator dump /dev/tty 2> /dev/null | grep -o '<node [^>]*>' | grep 'checkable="true"' | sed -n "${1}p")
    if { [ "$2" = on ] && echo "$line" | grep -q 'checked="false"'; } || { [ "$2" = off ] && echo "$line" | grep -q 'checked="true"'; }; then
        $adb shell input tap $(echo "$line" | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | paste -sd' ' - |
            awk '{ print int(($1 + $3) / 2), int(($2 + $4) / 2) }')
        $adb shell sleep 1
    fi
}

use_device "$@"
