#!/usr/bin/env bash
# Draws every state of some screens with a throwaway gallery test, and pulls the sheets. Run it from the repo root, in
# Git Bash. See SKILL.md.
#
#   bash .claude/skills/screen-gallery/gallery.sh <emulator|phone> <gallery .kt file> <output folder> [extra font scale]
#
# It copies GalleryHarness.kt and the gallery file into app/src/androidTest/…/probe/, installs, deletes the sheets an
# earlier run left in the app's cache, runs the gallery at the device's font scale, and on the emulator again at the
# extra font scale if one is given (such as 1.4, the phone's "big" font) and at 2.0, each the system's (put back
# afterwards). It pulls the sheets into the output folder, and removes the probe folder, also when something fails. On
# the phone it never changes the font scale: that is the user's setting.

device=$1
gallery=$2
out=$3
extra=$4
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
probe=app/src/androidTest/kotlin/io/github/davamix/pillsreminder/probe
if [ -z "$device" ] || [ -z "$gallery" ] || [ -z "$out" ]; then
    echo "usage: gallery.sh <emulator|phone> <gallery .kt file> <output folder>" >&2
    exit 2
fi
[ -f "$gallery" ] || { echo "gallery.sh: no gallery file at $gallery" >&2; exit 2; }
[ -f settings.gradle.kts ] || { echo "gallery.sh: run it from the repo root" >&2; exit 2; }
[ -e "$probe" ] && { echo "gallery.sh: $probe already exists; move it out first" >&2; exit 2; }
class=$(grep -o -m1 'class [A-Za-z0-9_]* *: *GalleryHarness' "$gallery" | sed 's/class \([A-Za-z0-9_]*\).*/\1/')
[ -n "$class" ] || { echo "gallery.sh: the gallery file has no class extending GalleryHarness" >&2; exit 2; }

# shellcheck source=/dev/null
source .claude/skills/phone/helpers.sh "$device" > /dev/null || exit 1
# It changes the font scale on purpose: no warning from instrument about it.
export INSTRUMENT_ANY_FONT=1

cleanup() { rm -rf "$probe"; }
restore_font() { [ "$device" = emulator ] && $adb shell settings put system font_scale "$font" > /dev/null; }
trap 'restore_font; cleanup' EXIT

mkdir -p "$probe" "$out"
cp "$here/GalleryHarness.kt" "$probe/" && cp "$gallery" "$probe/"
font=$($adb shell settings get system font_scale | tr -d '\r')
[ "$font" = null ] && font=1.0

gradle installDebug installDebugAndroidTest || exit 1
# The app's cache keeps every earlier run's sheets, other screens' too: without this, P7's output folders held P6's.
$adb shell "run-as $pkg sh -c 'rm -f cache/gallery-*'"
INSTRUMENT_TIMEOUT=900 instrument "probe.$class" || exit 1
if [ "$device" = emulator ]; then
    for scale in $extra 2.0; do
        $adb shell settings put system font_scale "$scale"
        $adb shell sleep 2
        INSTRUMENT_TIMEOUT=900 instrument "probe.$class" || exit 1
    done
elif [ -n "$extra" ]; then
    echo "gallery.sh: the extra font scale is for the emulator; on the phone, the user sets the font size by hand"
fi

for f in $($adb shell run-as "$pkg" ls cache | tr -d '\r' | grep '^gallery-'); do
    $adb exec-out run-as "$pkg" cat "cache/$f" > "$out/$f"
done
echo "gallery.sh: $(ls "$out" | grep -c '\.png$') sheets in $out"
ls "$out"
