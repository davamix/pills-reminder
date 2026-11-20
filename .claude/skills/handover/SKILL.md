---
name: handover
description: End-of-session routine for a pills-reminder phase. It runs the checks, updates the phase's HANDOVER.md, and commits and pushes; when the done criteria pass, it opens the pull request and waits for CI. Use when the user says to wrap up, hand over, end the session, or save progress.
---

# Hand over a phase

## Steps

1. **Run the checks.**
   - Run the checks in CLAUDE.md's "Commands" table (P1 adds it): the formatter first, then the full build as a separate command, which covers compilation, unit tests and lint. Total the test results as CLAUDE.md says.
   - **Instrumented tests:** if UI or Android code changed since the last full instrumented run, run every instrumented test, on the emulator and, when it's connected, on the phone. The `phone` skill covers both.
     - **The user may have data in the debug app,** such as treatments made for manual checks: run the whole suite with `gradle installDebug installDebugAndroidTest && instrument` (`phone` skill, helpers). It runs the same tests and keeps the data.
     - **Otherwise,** `./gradlew connectedDebugAndroidTest` works too, with `ANDROID_SERIAL` set to one device. It uninstalls the app when it ends, which deletes that data; afterwards, run `gradle installDebug` to leave the app installed. If unsure, ask first.
     - If only documents changed since a full run passed, don't run it again. Say which commit it ran on, and how.
   - **Merged manifests and dependencies:** if dependencies or the manifest changed, check that the merged manifests gain no permission or component, and list the new runtime libraries in the handover, with "Comparing the merged manifests and dependencies with `main`" below.
   - Note the results exactly. Don't call something passing unless it ran and passed.
2. **Push decisions up.**
   - **Does it need a new SPEC version?** A decision that only settles a point SPEC.md leaves open stays in HANDOVER.md. One that contradicts SPEC.md's text gets a new SPEC version, with a change-log entry. That includes a change to an internal API or behaviour the SPEC describes. New wording for a UI text the SPEC lists as a suggestion isn't a contradiction: the strings file holds the final wording (`phase-docs`, step 5).
   - **Specification or plan first.** If a decision changes the specification or the plan, update those first (`doc-review` skill) and tell the user.
   - **Where SPEC versions go:** to `main`, like every version of these documents (CLAUDE.md, "Git"). Commit them there, from a worktree if this working copy is busy, and push. Then run `git merge main` on the phase branch, so the pull request carries the same SPEC.md as `main`. Commit any pending work on the branch before switching. The full steps are in the `phase-docs` skill, "SPEC change during implementation".
   - **Note it in the handover.** Mention the new version in the decision's row.
3. **Open the pull request, if the phase is complete.** Do this when every done criterion in SPEC.md §8 passes, apart from the ones that need the pull request or the merge: CI, and the plan's status. Open it before writing the handover, so the handover can record the PR number and the CI result.
   - Commit any remaining work on the phase branch, and push it.
   - Write the body (template below) to a file in the session's scratchpad, not in the repository. Then run `gh pr create --base main --head phase/pN-<name> --title "PN: <phase name>" --body-file <file>`.
   - Wait for CI on the commit you just pushed. Base the wait on that commit's SHA, not on the pull request. Right after a push, `gh pr checks` can still show the previous commit's finished run, a false green. Before the new run registers, it can report "no checks", which `--watch` also treats as done.
   - **CI runs only on pull requests and on pushes to `main`** (`.github/workflows/ci.yml`). A push to a phase branch that has no pull request yet starts no run, so there is nothing to wait for. The wait below gives up after 10 minutes without a run, rather than waiting forever.
   - Run this in the background and wait for it to finish:

     ```bash
     sha=$(git rev-parse HEAD); id=""
     for i in $(seq 60); do id=$(gh run list --commit "$sha" --workflow CI --json databaseId -q '.[0].databaseId' 2>/dev/null); [ -n "$id" ] && break; sleep 10; done
     if [ -z "$id" ]; then echo "no CI run for ${sha:0:7} after 10 min: is a pull request open for this branch?"; else gh run watch "$id" --exit-status --interval 20 > /dev/null; echo "CI exit=$? run=$id"; fi
     ```

     `CI exit=0` means it passed. Otherwise, `gh run view <id> --log-failed` shows why.
   - If CI fails, fix the cause on the phase branch, push, and wait again.
4. **Update HANDOVER.md** in the phase folder:
   - **Last session:** today's date.
   - **Status:** `In progress`, `Blocked` or `Done`. With a pull request open: `In progress: all tasks done; pull request [#N](<url>) is waiting for the user to merge it`.
   - **Prompt to start or continue:** make sure it works as-is for the next session. Use point 4 for anything that session must know first. Examples: a question waiting for the user, the phone must be connected, or a pull request is open and may have been merged or have review comments.
   - **Current state:** rewrite it so it describes the state now. Don't append to it. Include the PR link and the CI result, if there is a PR.
   - **Next steps:** concrete enough that a new session can start without reading anything else. With a PR open: deal with any review comments, then use the `phase-close` skill after the merge.
   - **Tasks:** update each status. With a PR open, the last task is `In review`.
   - **Decisions and deviations:** add a row for each new decision.
   - **Known issues** and **Pending wiring:** keep them current.
   - **How to verify:** the commands and manual steps that check the current state.
   - **Session log:** add a new entry at the top, saying what was done and anything worth knowing.
   - **Device state left behind:** if the session changed anything on the phone or the emulator (a permission, Doze, a setting), say whether it was put back.
5. **Commit and push** on the phase branch, with a message that summarises the session. With a PR open, this push runs CI again. Wait for it with the commands from step 3. They take the new `HEAD`, so they wait for this push's run, not the earlier one.
6. **Leave the merge to the user**, unless they have said otherwise. Once it's merged, the `phase-close` skill closes the phase on `main`.
7. **Report to the user:** what was done, the check results, the decisions they should review, and the next steps.

## Pull request body

Fill in each part from SPEC.md and HANDOVER.md. Leave out "Left for the reviewer" if there are no manual checks.

```markdown
## Goal

PN of the [implementation plan](docs/IMPLEMENTATION_PLAN.md): <goal, from SPEC.md §1>. Details: [SPEC.md](docs/phases/PN-<name>/SPEC.md) · progress and decisions: [HANDOVER.md](docs/phases/PN-<name>/HANDOVER.md).

## What's in it

- <one bullet per area, naming the requirement IDs it covers>

## Checks

- Build: <result, with the number of unit tests and failures>
- <instrumented tests and device checks, naming the device (emulator or phone), or "No device checks: <reason>">

## Decisions to review

<the decisions from HANDOVER.md that the user should look at, where SPEC.md was silent or was changed; or "None.">

## Left for the reviewer

- [ ] <manual check on the phone>

## Pending wiring

<from HANDOVER.md, or "None.">

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

## Comparing the merged manifests and dependencies with `main`

[compare-manifests.sh](compare-manifests.sh) lists the permissions and components of the debug and release manifests, and the release build's runtime libraries, on the branch and on `main`, and compares them. `main` is built in a scratch worktree, so this working copy stays on the branch. It assumes a Gradle `:app` module, which P1 confirms. Run it in Git Bash from the repo root, after the build, with the session's scratchpad:

```bash
bash .claude/skills/handover/compare-manifests.sh <scratchpad>
```

It prints `debug: same as main (N entries)`, the same for release, and `release dependencies: same as main (N libraries)`, or the differences. The script lives in a file because the skill loader replaces shell positional parameters (a dollar sign and a digit) in this text with the words the skill was invoked with: in P2, the functions' arguments became `"all"` and `"tasks"`.

Every difference is a line starting with `<` (only on `main`) or `>` (new on the branch). A new permission or component must be explained in the handover, or removed. Reminders need a few permissions (notifications, exact alarms, starting after a restart), so the plan lists the expected ones; anything beyond that list is a finding. A changed attribute, such as `exported`, doesn't show here: check that one in the manifest itself. A new library, `group:artifact` without its version, must be listed in the handover: plan §3 accepts only well-known AndroidX, JetBrains and Google libraries, and what they depend on. A version change alone doesn't show.
