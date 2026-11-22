#!/usr/bin/env bash
# Shows that tests can fail: applies each bug of a bugs file on its own, runs the test command, restores the file and
# checks it with cmp. Run it from the repo root, in Git Bash. See SKILL.md.
#
#   bash .claude/skills/deliberate-bugs/run.sh <bugs file> <output folder> '<test command>'
#
# The test command runs with bash -c, once per bug. It prints one line per bug: caught by <tests>, NOT CAUGHT, or
# failed with no failing test (most likely the bug doesn't compile; its log says). The logs go in the output folder.

bugs_file=$1
out=$2
command=$3
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
if [ -z "$bugs_file" ] || [ -z "$out" ] || [ -z "$command" ]; then
    echo "usage: run.sh <bugs file> <output folder> '<test command>'" >&2
    exit 2
fi
[ -f "$bugs_file" ] || { echo "run.sh: no bugs file at $bugs_file" >&2; exit 2; }
[ -f build.gradle.kts ] || [ -f settings.gradle.kts ] || { echo "run.sh: run it from the repo root" >&2; exit 2; }
mkdir -p "$out"

py() { python -I "$(cygpath -w "$here/apply_bug.py")" "$@"; }
bugs_win=$(cygpath -w "$bugs_file")
list=$(py list "$bugs_win") || exit 2
# Windows' Python ends its lines with a carriage return, which would stick to the file names.
list=$(printf '%s\n' "$list" | tr -d '\r')

current_file=""
restore() {
    if [ -n "$current_file" ]; then
        if cp "$out/original" "$current_file" && cmp -s "$out/original" "$current_file"; then restored=restored; else restored="NOT RESTORED"; fi
        current_file=""
    fi
}
# An interrupted run still puts the file back.
trap 'restore; echo "run.sh: interrupted, $restored"; exit 130' INT TERM

caught=0
total=0
while IFS=$'\t' read -r id file; do
    [ -n "$id" ] || continue
    total=$((total + 1))
    log="$out/$id.log"
    [ -f "$file" ] || { echo "$id: not applied (no file $file)"; continue; }
    cp "$file" "$out/original" || { echo "run.sh: can't back up $file, so $id isn't applied: stopping"; exit 1; }
    current_file=$file
    if ! py apply "$bugs_win" "$id" > "$log" 2>&1; then
        restore
        echo "$id: not applied ($(tail -1 "$log")); $restored"
        [ "$restored" = restored ] || { echo "run.sh: $file NOT RESTORED: stopping; put it back from git"; exit 1; }
        continue
    fi
    marker="$out/.started"
    touch "$marker"
    # No stdin: adb, in an instrumented run, would read the rest of the bug list from it.
    bash -c "$command" >> "$log" 2>&1 < /dev/null
    rc=$?
    restore
    # Failing tests: instrumented ones from the runner's output, JVM ones from JUnit XML written during this run.
    failing=$(grep -E '^[0-9]+\) ' "$log" | sed -E 's/^[0-9]+\) ([^(]+)\(.*\.([A-Za-z0-9_$]+)\)$/\2 > \1/')
    if [ -z "$failing" ]; then
        failing=$(find . -path '*/build/test-results/*' -name '*.xml' -newer "$marker" 2> /dev/null |
            xargs -r grep -h -o '<testcase name="[^"]*" classname="[^"]*"\|<failure' 2> /dev/null |
            grep -B1 '<failure' | grep '<testcase' |
            sed -E 's/<testcase name="([^"]*)" classname="([^"]*\.)?([^".]*)"/\3 > \1/')
    fi
    if grep -q '^OK (' "$log" || { [ $rc -eq 0 ] && ! grep -q 'FAILURES!!!' "$log"; }; then
        echo "$id: NOT CAUGHT; $restored"
    elif [ -n "$failing" ]; then
        caught=$((caught + 1))
        echo "$id: caught by $(echo "$failing" | paste -sd ';' - | sed 's/;/; /g'); $restored"
    else
        echo "$id: failed with no failing test (a compile error? see $log); $restored"
    fi
    # A file left changed would add its bug to every later one.
    [ "$restored" = restored ] || { echo "run.sh: $file NOT RESTORED: stopping; put it back from git"; exit 1; }
done <<< "$list"
trap - INT TERM
rm -f "$out/.started" "$out/original"
echo "caught $caught of $total"
