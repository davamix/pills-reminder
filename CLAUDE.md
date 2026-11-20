# Pills Reminder: guide for Claude Code

An Android app that manages medical treatments and reminds the user when each medicine's next take is due. It runs on one phone, a POCO F3 (Android 13, API 33, Xiaomi HyperOS, portrait, 1080 × 2400). The emulator `PillsReminder_API_36` has the same screen.

**Status:** the specification is agreed (v0.4), and the implementation plan is a draft under review. There is no code yet. P1 adds the build commands, the code structure and the coding conventions to this file.

## Documents

- `docs/SPECIFICATION.md`: what the app does. Requirement IDs look like `TR-3`, `RM-5` or `NF-1`; open questions are `Qn`.
- `docs/IMPLEMENTATION_PLAN.md`: how it is built. Decisions `D-n`, phases `P1`, `P2`, …, skills `SK-n`, questions `PQ-n`, and the phases' status in §5.1.
- `docs/phases/PN-<name>/SPEC.md` and `HANDOVER.md`: one specification and one progress tracker per phase, from the templates in `docs/phases/_templates/`.

When working on a phase, read only its HANDOVER.md and SPEC.md (skill `phase-start`). They quote everything needed. Each HANDOVER.md starts with a prompt to paste into a new session to start or continue that phase. Keep it current.

**Precedence:** specification, then plan, then phase SPEC. Fix the higher document first, then let the change flow down.

### The implementation plan

It follows Workout Timer's plan, because the skills rely on its sections. Keep these when changing it:

- §1 Tech stack, with the technical decisions `D-n` in §1.1.
- §2 Architecture. §3 Security and privacy, including the permissions the app may declare. §4 Testing strategy.
- §5 Phases: §5.1 a table with the columns Phase, Name, **Folder**, Depends on and Status, plus the rules for running phases in parallel; §5.2 a summary per phase, which its SPEC.md expands; §5.3 requirement coverage, each requirement ID to a phase.
- §6 Working method: phase documents, phase lifecycle, git workflow, session routine.
- §7 Claude Code skills. §8 Open questions `PQ-n`, with an "Answered" table. §9 Change log.

Phase statuses: Planned → Spec draft → Ready → In progress → Done.

## Skills

- `doc-review`: apply a review round to the specification or the plan: answered questions, accepted and discarded proposals, a new version with a change-log entry, then commit and push.
- `phase-docs`: write a phase's SPEC.md and HANDOVER.md, apply their review, and change a SPEC.md during implementation.
- `phase-start`: begin or resume a phase (branch, dependencies, status).
- `handover`: end a session. Runs the checks, updates HANDOVER.md, commits and pushes, and opens the pull request when the phase is done.
- `phase-close`: after the user merges a phase's pull request, mark the phase `Done` on `main`.
- `phone`: the phone and the emulator over adb, with helpers: pick the device, start the emulator, install, launch, read the screen, screenshots, logs, instrumented tests that keep the app's data, the app's alarms and notifications, Doze, and the HyperOS pitfalls found on this phone.

## Environment

- `adb` is not on PATH: `D:\Android\Sdk\platform-tools\adb.exe` (Git Bash: `/d/Android/Sdk/platform-tools/adb.exe`). The phone is paired over USB **and** wireless debugging, so `adb devices` usually lists it twice, and the emulator adds another entry. Always pass `-s <serial>`; the `phone` skill picks it.
- Android SDK in `D:\Android\Sdk` (`ANDROID_HOME`): platforms up to `android-37.0`, build-tools up to 37.0.0, emulator 37.1. Android Studio 2026.1 (build 261), whose JDK 25 is `JAVA_HOME`.
- `gh` is authenticated as the repo's owner. Python 3.12 is installed.
- Create and change files with the Write and Edit tools, not shell heredocs. In Workout Timer, a heredoc that wrote a Kotlin file failed to parse in Git Bash, and nothing was written.

## Git

- **The repo is public.** Never commit personal data or secrets: device serial numbers, names, e-mail or postal addresses, phone numbers, real treatments or screenshots that show them, keystores, passwords, `local.properties`. Examples and test data use the example in specification §4.4.
- `main` always builds, once there is code. Each phase is built on its own branch, `phase/pN-<name>`, and merged through a pull request with green CI. The user merges.
- Draft documents (specification, plan, a phase's SPEC.md) are committed and pushed to `main` at every version. That includes a SPEC version made during implementation. Afterwards, merge `main` into the phase branch (skill `handover`).
- Phase status changes in plan §5.1 also go straight to `main`.
- HANDOVER.md updates go on the phase branch. The one exception: after the merge, the closing updates go on `main` (skill `phase-close`).
- Create phase branches with `--no-track` and push them with `-u`, so they never track `main` (skill `phase-start`).
- Tag and release only with the user's go-ahead.
