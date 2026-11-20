#!/usr/bin/env bash
# Compares the permissions and components of the merged debug and release manifests on this branch with those on
# origin/main, which it builds in a scratch worktree, so this working copy stays on the branch. Run it in Git Bash from
# the repo root, after the build:
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

build_manifests
git worktree add -q --detach "$wt" origin/main && cp local.properties "$wt/" 2> /dev/null
(cd "$wt" && build_manifests)
for v in debug release; do
    a=$(manifest "$wt" $v)
    b=$(manifest . $v)
    if [ -z "$a" ] || [ -z "$b" ]; then
        echo "$v: merged manifest not found"
        continue
    fi
    diff <(components "$a") <(components "$b") && echo "$v: same as main ($(components "$b" | wc -l) entries)"
done
git worktree remove --force "$wt"
