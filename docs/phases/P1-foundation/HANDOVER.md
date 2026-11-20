# P1 — Project foundation: Handover

| | |
|---|---|
| **Status** | In progress: all tasks done; pull request [#1](https://github.com/davamix/pills-reminder/pull/1) is waiting for the user to merge it |
| **Spec** | [SPEC.md](SPEC.md), version v0.2 |
| **Branch** | `phase/p1-foundation` |
| **Last session** | 2026-11-20 |

This document tracks the phase as it's built. Update it at the end of every session, so the next one can continue from here.

## Prompt to start or continue

Paste this prompt into a new Claude Code session to start or continue the phase. When the phase is done, replace it with "Phase done: no prompt needed."

```text
Start or continue phase P1 (Project foundation) of the pills reminder.

1. Use the phase-start skill for P1. Read only docs/phases/P1-foundation/HANDOVER.md and SPEC.md; they contain everything needed.
2. Carry on from "Next steps" in the handover. Work through the tasks in SPEC.md §7 in order, on branch phase/p1-foundation. Commit after each task, once the build passes.
3. Follow SPEC.md. If something in it is wrong or unclear, stop and ask me before changing course. Record every decision or deviation in the handover.
4. All tasks are done, and pull request #1 is open. Check first whether I have merged it (gh pr view 1). If it is merged, use the phase-close skill, which also runs the skills review. If not, deal with any review comments on it. The phone checks left for me are listed in the pull request.
5. Before the session ends, or once all tasks are done, use the handover skill. When the done criteria pass, open the pull request and wait for me to merge it.
```

## Current state

The app skeleton is complete and runs on both devices. Pull request [#1](https://github.com/davamix/pills-reminder/pull/1) is open, and CI passed on it ([run](https://github.com/davamix/pills-reminder/actions/runs/44768461238), 3 min, commit `91b1935`).

- `./gradlew build` passes locally, with 0 lint issues and ktlint clean. There are no unit tests yet.
- The 5 instrumented tests pass on the emulator and on the phone (commit `464a118`; no app code changed after it).
- The app opens in portrait, in light and dark theme, and moves between the seven placeholder screens. Nothing is drawn under the system bars.
- The permission allowlist check fails the build on `INTERNET` and on any permission outside plan §3.
- Every `phone` skill helper works on both devices, and the content area is recorded in the skill.

Left for the user, on the phone: the launcher icon and name, and that turning the phone sideways keeps portrait.

## Next steps

1. If the user has review comments on pull request #1, deal with them on the branch.
2. Once the user has merged it, use the `phase-close` skill: it marks P1 `Done` on `main` and runs the skills review (plan `D-17`). P2 (domain model and schedule) can start after that; its documents don't exist yet (`phase-docs`).

## Tasks

The task IDs come from SPEC.md §7.

| Task | Status | Notes |
|---|---|---|
| T1 | Done | Gradle 9.9.2 wrapper with checksum; newer releases taken: Gradle 9.9.2, lifecycle 2.11.1, kotlinx-serialization 1.13.0. Compose BOM stays at 2026.09.00 (see decisions). `gradlew` executable in git. |
| T2 | Done | `:core` has no sources yet; JUnit Jupiter is set up |
| T3 | Done | |
| T4 | Done | |
| T5 | Done | The allowlist check fails with `INTERNET` and with `CAMERA` added, in both variants, and passes once they're removed. The task itself went in with T3's build file. |
| T6 | Done | 5/5 pass on the emulator and the phone. The phone needed HyperOS's background-start permission (see decisions). |
| T7 | Done | `setup-java` moved to v6.3.0; the other actions were already the newest |
| T8 | Done | All helpers checked on both devices; `end_process` fixed and `allow_test_start` added for the phone; content area measured |
| T9 | Done | |
| T10 | In review | Emulator checks and phone navigation done by the session. The launcher icon and turning the phone sideways are left for the user, in the pull request. |

## Decisions and deviations

| Date | Decision | Reason |
|---|---|---|
| 2026-11-20 | Compose BOM 2026.09.00 (Compose UI 1.12.1), not the newest, 2026.11.01. | Compose 1.13 requires `compileSdk` 37.1, and only the `android-37.0` platform is installed. Moving up needs that platform installed first, and AGP 9.3.3 to accept it. |
| 2026-11-20 | Instrumented test names are sentences **without commas** (refines P1-D7). | D8 accepts spaces at `minSdk` 33, but no ASCII punctuation other than `-`, `_` and `$`: a comma failed the dex step. Recorded in CLAUDE.md. |
| 2026-11-20 | The debug app has HyperOS's "Display pop-up windows while running in the background" permission (op 10021) on the phone, granted over adb with the user's agreement. The `phone` skill's new `allow_test_start` grants it again after a fresh install. | HyperOS refused the test runner's activity start (`MIUILOG- Permission Denied Activity`), and every instrumented run hung, even with the app on screen first. |
| 2026-11-20 | `end_process` falls back to `am kill` on the phone. | SELinux on HyperOS denies SIGKILL from `run-as` to the app (`avc: denied { sigkill }`). `am kill` ended the background process in P1 T8. |
| 2026-11-20 | `res/values/colors.xml` and `res/values-night/colors.xml` added, which weren't in SPEC §4.1. | The window background (no flash at start, light and dark) and the launcher icon's colours. |
| 2026-11-20 | `versionName` 0.1.0, `versionCode` 1. | SPEC.md was silent. P8 sets 1.0.0 for the release. |
| 2026-11-20 | Placeholder buttons say "All treatments" and "Open settings", not "Treatments" and "Settings". | Their texts would otherwise equal the titles of the screens they open, and the smoke test matches texts exactly. Placeholder texts are suggestions. |

## Known issues

- **Gradle 10 deprecation notice:** Gradle prints "Deprecated Gradle features were used… incompatible with Gradle 10". It comes from AGP 9.3.3 itself, not from the build scripts.
- **`sun.misc.Unsafe` warnings:** ktlint prints them on JDK 25. They are harmless; CLAUDE.md shows how to filter them.
- **HyperOS blocks background activity starts** (see decisions). Beyond tests, P5 and P7 should check whether this blocks anything the app starts without a tap, such as opening the app from a notification (the `phone` skill lists it).
- **The phone was on wireless debugging only** this session. USB wasn't used.

## Pending wiring

None.

## How to verify

```bash
./gradlew ktlintFormat                 # then, as its own command:
./gradlew build                        # compile, lint, ktlint, permission allowlist
source .claude/skills/phone/helpers.sh emulator && gradle installDebug installDebugAndroidTest && instrument
source .claude/skills/phone/helpers.sh phone && gradle installDebug installDebugAndroidTest && allow_test_start && instrument
```

Then open "Pills Reminder (debug)" on the phone: it shows in portrait, and every placeholder screen opens from its button and returns with Back.

## Session log

Newest first.

### 2026-11-20

- **Done:** SPEC v0.1 written and reviewed (v0.2, Ready). T1–T9 done, T10 checked by the session apart from the user's two phone checks. Pull request #1 opened.
- **Versions:** AGP 9.3.3, Gradle 9.9.2, Kotlin 2.4.21, Compose BOM 2026.09.00, lifecycle 2.11.1, kotlinx-serialization 1.13.0; `minSdk` 33, `compileSdk` and `targetSdk` 37.
- **Measured:** content area 392.7 × 796.4 dp on the phone (3-button navigation: status bar 29.1 dp, navigation bar 47.3 dp), 392.7 × 824.7 dp on the emulator (gestures). Debug app cold start 0.65 s on the phone, 1.3 s on the emulator.
- **Device state left behind:**
  - Phone: the debug app is installed, with op 10021 allowed for it (agreed with the user). Nothing else changed.
  - Emulator: night mode and auto-rotate were changed for the checks and put back (night mode off, auto-rotate on). The debug app is installed. The emulator is stopped.
- **CI:** passed on pull request #1, commit `91b1935`, in 3 minutes. CI runs on pull requests and on `main` only, so the phase branch's earlier pushes had no run.
