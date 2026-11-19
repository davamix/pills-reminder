---
name: phase-docs
description: Create the SPEC.md and HANDOVER.md of a pills-reminder phase from the implementation plan and the templates, apply the review of a phase's SPEC.md, and change a SPEC.md during implementation. Use when asked to write, create or prepare a phase's documents or specification, when the user answers a phase SPEC's questions, and when something found while building a phase contradicts its SPEC.md.
---

# Create a phase's documents

Input: a phase ID, e.g. `P3`.

The phases, their folders (`docs/phases/PN-<name>/`) and their dependencies are listed in plan §5.1 (`docs/IMPLEMENTATION_PLAN.md`). If the plan doesn't exist yet, stop: it is written once the specification is agreed.

## Steps

1. **Read the plan.** In `docs/IMPLEMENTATION_PLAN.md`, read the parts that concern this phase:
   - its row in §5.1 (folder, dependencies), its summary in §5.2, and its rows in §5.3 (requirement coverage);
   - the decisions `D-n`, §2 architecture, §3 security and §4 testing.
2. **Quote the requirements.** For every requirement ID the phase covers, copy its exact text from `docs/SPECIFICATION.md`. Note the specification version.
3. **Read the code it builds on.** Read only the merged code this phase uses: the interfaces from its dependencies, plus the shared files it will touch.
4. **Read what earlier phases learned.** A session that implements this phase reads only this phase's documents, so lessons left in other handovers never reach it unless this SPEC carries them.
   - For each phase this one depends on in plan §5.1, and their own dependencies in turn, read three sections of its HANDOVER.md: "Decisions and deviations", "Known issues" and "Pending wiring".
   - **Same layer:** read the same three sections for every `Done` phase that works in the same layer as this one, even when it isn't a dependency. A UI phase reads the other UI phases. Their test helpers, code patterns and platform pitfalls apply here too.
   - Then read "Pending wiring" in the HANDOVER.md of every other phase marked `Done` in plan §5.1, and take the items that name this phase. The plan's dependencies don't list every phase whose code this one wires up.
   - Carry what applies into SPEC.md:
     - pending wiring this phase must complete, or must leave for a later phase;
     - pitfalls that will affect its code or tests;
     - decisions that change how the interfaces it uses behave.
   - Quote or summarise each one, naming the phase it comes from, so the implementing session needn't open that handover.
   - A lesson that applies to every phase belongs in CLAUDE.md instead. If you find one that isn't there yet, propose adding it.
5. **Write SPEC.md.** Copy `docs/phases/_templates/SPEC_TEMPLATE.md` into the phase folder as `SPEC.md` and fill in every section:
   - The document must stand alone. A session that implements the phase should not need the specification or the plan.
   - List the packages and files the phase owns. Name every shared file it changes.
   - Tasks: in order, each one session or less, with IDs `T1`, `T2`, …
   - **Measure first what the documents can't tell.** If the design depends on device or platform behaviour nobody has measured, make an early task that measures it before the design relies on it: when an alarm really fires in Doze, what HyperOS does after a restart or a swipe from Recents, what a permission dialog does. Use a throwaway probe (`phone` skill, "Measuring device behaviour"), and say in §9 what the design assumes until then. In Workout Timer, an unmeasured speech start-up delay only came to light mid-phase and forced a new SPEC version.
   - **Reminders are checked on the phone.** Anything about alarms, notifications, Doze, restarts or HyperOS's settings needs a task and a manual check on the phone, not only on the emulator (`phone` skill, "Which device for what").
   - **Check a screen's layout before relying on it.** For each screen the phase adds or changes:
     - Give the SPEC the measured content area of the phone (`phone` skill, "Devices"; measured in P1). Size the layout and its text against that, not the full screen.
     - Plan an early task that draws every state of the screen as screenshots (`phone` skill, "Checking a screen in every state"). Later tasks that depend on the layout come after it.
   - **UI text is a suggestion.** List a screen's main texts so the review can see them, and say they are suggestions. The phase's strings resource file is the source of the final wording. Changing it during implementation doesn't need a new SPEC version. Only text quoted from the specification is fixed.
   - Every covered requirement needs a test or a manual check in §6.
   - **Instrumented tests in the tasks and done criteria:** write "every instrumented test passes on the emulator and the phone", without naming the command. `connectedDebugAndroidTest` uninstalls the app and deletes the user's treatments, and manual checks often need them. A whole-suite `instrument` run (`phone` skill) checks the same thing and keeps the app's data.
   - Anything unclear goes into §9 as a question, with the assumption the draft uses.
6. **Write HANDOVER.md.** Copy `docs/phases/_templates/HANDOVER_TEMPLATE.md` as `HANDOVER.md`:
   - Status: Not started.
   - Every task listed as To do.
   - Next steps: start with `T1`.
   - **Prompt to start or continue:** fill in the phase ID, name, folder and branch. Write the phase's own notes in point 4, for example whether the phone must be connected, or which fakes to use while a dependency isn't merged.
   - **Pending wiring:** copy the items from step 4 that this phase leaves for later.
7. **Update the plan.** Set the phase's status in plan §5.1 to `Spec draft`. Status changes don't need a new plan version.
8. **Commit and push to `main`.** Use the message `PN spec v0.1: <phase name>`. Draft documents are pushed at every version.
9. **Report to the user.** Summarise the spec and list its open questions for review.

## After the review

- **Apply the review to SPEC.md:** apply the changes, bump its version and add a change-log entry. Move answered questions from §9 to an "Answered" table.
- **Change higher documents first.** If an answer changes or clarifies a requirement, update `docs/SPECIFICATION.md` first, before SPEC.md (`doc-review` skill). That includes settling something the specification left open. Its change-log entry says where the change came from, e.g. "From the PN spec review (PN Q1 and Q2): …". Do the same for the plan if an answer changes it. Then quote the new specification version in SPEC.md.
- **Commit and push** every changed document to `main`.
- **Once the user agrees,** set SPEC.md's status to `Ready`, and plan §5.1 to `Ready`.
- **If the user asked to start the phase,** carry on with the `phase-start` skill in the same session.

## SPEC change during implementation

When something found while building a phase contradicts its SPEC.md, such as a measurement, a platform limit or a design flaw:

1. **Stop and ask the user.** Explain what was found, with numbers if there are any, and propose a change. Don't change course until they agree.
2. **Commit pending work on the phase branch,** so `git status` is clean before switching.
3. **Higher documents first.** If the change affects the specification or the plan, update those first, as in "After the review".
4. **Edit SPEC.md on `main`:** run `git switch main && git pull --ff-only`, or use a worktree if this working copy is busy (`phase-start`, step 3).
   - Bump the version in the header.
   - Apply the change everywhere it shows: scope (§3), files and shared files (§4.1), interfaces (§4.2), behaviour (§4.3), security (§5), tests and manual checks (§6), tasks (§7), done criteria (§8).
   - Record it in §9's "Answered" table as the next question number: the problem, and what was decided.
   - Add a change-log row.
   - New work gets the next free task ID, after the last task that's done. Renumber only tasks that haven't started.
   - Fold in any recorded deviation that the same sections now describe, so the SPEC matches the code.
5. **Commit and push to `main`:** `PN spec vX.Y: <what changed>`.
6. **Merge it into the phase branch:** `git switch phase/pN-<name> && git merge --no-edit main && git push`.
7. **Update HANDOVER.md** on the branch: the SPEC version in its header, the tasks table, and "Known issues" if the change resolves one. Commit it with the next task, or on its own.
