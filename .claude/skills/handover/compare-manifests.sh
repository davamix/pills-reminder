#!/usr/bin/env bash
# Compares the permissions and components of the merged debug and release manifests on this branch with those on
# origin/main, and the release runtime dependencies too. main is built in a scratch worktree, so this working copy stays
# on the branch. Run it in Git Bash from the repo root, after the build:
#
#   bash .claude/skills/handover/compare-manifests.sh <scratchpad>
#
# Every difference is a line starting with "<" (only on main) or ">" (new on the branch). It lives in a file, not in
# SKILL.md, because the skill loader replaces $1, $2, … in a skill's text with the words it was invoked with (P2).

sp=${1:?usage: compare-manifests.sh <scratchpad>}
wt="$sp/main-wt"

# Permissions and components of a merged manifest, one "<tag> <name>" per line.
components() {
    tr '\n' ' ' < "$1" |
        grep -o -E '<(uses-permission|permission|activity|activity-alias|service|receiver|provider)[^>]*>' |
        sed -E 's/^<([a-z-]+)[^>]*android:name="([^"]*)".*/\1 \2/' | sort
}

# The merged manifest of variant $2 under the project root $1.
manifest() { find "$1/app/build/intermediates/merged_manifest/$2" -name AndroidManifest.xml 2> /dev/null | head -1; }

build_manifests() {
    ./gradlew :app:processDebugMainManifest :app:processReleaseMainManifest --console=plain -q 2>&1 |
        grep -v -i 'unsafe\|^WARNING'
}

# The release build's runtime libraries, one "group:artifact" per line, without versions (P3). Plan §3 accepts only
# trusted libraries, and what they depend on: list every new one in the handover.
dependencies() {
    ./gradlew :app:dependencies --configuration releaseRuntimeClasspath --console=plain -q 2> /dev/null |
        grep -o -E '[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+:[^ ]+' | awk -F: '{print $1":"$2}' | sort -u
}

# build_manifests ends with grep -v, which returns 1 when the build prints nothing: never chain it with &&.
rm -f "$sp/dependencies-main.txt" "$sp/dependencies-branch.txt"
build_manifests
dependencies > "$sp/dependencies-branch.txt"
git worktree add -q --detach "$wt" origin/main && cp local.properties "$wt/" 2> /dev/null
(cd "$wt" && { build_manifests; dependencies > "$sp/dependencies-main.txt"; })
for v in debug release; do
    a=$(manifest "$wt" $v)
    b=$(manifest . $v)
    if [ -z "$a" ] || [ -z "$b" ]; then
        echo "$v: merged manifest not found"
        continue
    fi
    diff <(components "$a") <(components "$b") && echo "$v: same as main ($(components "$b" | wc -l) entries)"
done
if [ -s "$sp/dependencies-main.txt" ] && [ -s "$sp/dependencies-branch.txt" ]; then
    diff "$sp/dependencies-main.txt" "$sp/dependencies-branch.txt" &&
        echo "release dependencies: same as main ($(wc -l < "$sp/dependencies-branch.txt") libraries)"
else
    echo "release dependencies: not listed"
fi
git worktree remove --force "$wt"
