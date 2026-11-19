---
name: doc-review
description: Apply a review round to the pills-reminder specification (docs/SPECIFICATION.md) or implementation plan (docs/IMPLEMENTATION_PLAN.md). It handles answered questions, accepted, changed and discarded proposals, and new requirements, keeps the rest of the document consistent, bumps the version with a change-log entry, then commits and pushes to main. Use when the user answers open questions (Q1, PQ-2, …), accepts or rejects proposals, or asks for a change to the specification or the plan.
---

# Apply a review to the specification or the plan

Input: the user's answers, usually in the conversation ("Q1: yes. Q4: move them. RM-5: discard it. Add …"). Answers about `Qn` or requirement IDs are for the specification. Answers about `PQ-n`, `D-n`, phases or skills are for the plan. A phase's SPEC.md is reviewed with the `phase-docs` skill instead.

## IDs and sources

Both documents follow these rules. Keep them.

- **Specification:** requirement IDs are a group prefix and a number: `TR-3`, `SC-1`, `RM-5`, `TK-2`, `HM-1`, `NF-4`. Questions are `Qn`.
- **Plan:** `D-n` technical decisions, `PN` phases, `SK-n` skills, `PQ-n` questions.
- **IDs are never reused or renumbered.** A new requirement takes the next free number in its group, even when a lower one was discarded. A new question takes the next number after the highest ever used, answered ones included.
- **Source** column of the specification:
  - `Requested`: asked for by the user, in the brief or in a review.
  - `Agreed`: a proposal the user accepted.
  - `Proposed`: a suggestion still waiting for review.

## Steps

1. **Read the document as it is now:** its header (version), its open questions, and the sections the answers touch.
2. **Apply each answer:**
   - **A question answered:** remove it from the open questions. Add it to the answered table (specification §9.1, plan §8 "Answered") as `| Qn | <short topic> | <the decision, naming the IDs it affects> |`. It keeps its number.
   - **A proposal accepted as written:** set its Source to `Agreed`.
   - **A proposal accepted with changes:** rewrite it, and set its Source to `Agreed`.
   - **A proposal discarded:** remove its row, and add it to specification §9.2 "Discarded proposals" as `| ID | <the proposal> | <the reason> |`. If the user still wants it some day, add it to §10 "Future ideas" too.
   - **A draft assumption confirmed:** the requirements that rest on it become `Agreed`.
   - **A new requirement from the user:** the next free ID in its group, Source `Requested`.
   - **A new suggestion of yours** that the answers call for: Source `Proposed`. Say so in the report.
   - **Still unclear, or a new consequence of an answer:** a new question, with the assumption the document uses meanwhile. Don't settle it silently.
3. **Make the rest of the document agree** with each change. In the specification, check:
   - the glossary and the structure (§4.1, §4.2);
   - the rules and the diagram (§4.3);
   - the worked example (§4.4): work its numbers out again;
   - the scope (§2), the screens and sketches (§6), the non-functional requirements (§7) and the future ideas (§10);
   - every place that mentions a changed or discarded ID. Search for it.
4. **Version and change log:**
   - Bump the minor version: v0.1 → v0.2, and v0.9 → v0.10. Version 1.0 is kept for the version that matches the app's first release.
   - Set "Last updated" to today's date.
   - Add a change-log row that names what the round decided, by ID. For example: "First review: Q1–Q6 answered. RM-5 discarded. Added TR-14. New questions Q19 and Q20."
   - The plan's header names the specification version it's based on. If the plan changes in the same round, update it.
5. **Flow down.** Once the plan and the phase documents exist, list what the change affects lower down: plan decisions or phases, a phase SPEC.md (which quotes requirement text and must quote the new version), or code already merged. Change those only if the user agrees, in this session or as a task for a phase.
6. **Commit and push to `main`,** with the message `Specification v0.N: <summary>` or `Plan v0.N: <summary>`. Draft documents are pushed at every version. If this working copy is on a phase branch, don't switch it: use a worktree on `origin/main`, as the `phase-start` skill does.
7. **Report to the user:** the new version, what changed by ID, any new proposals of yours, and the questions still open, each with its draft assumption. When none are left, say what comes next: for the specification, the implementation plan; for the plan, the `phase-docs` skill for P1.

## Writing requirements

- One behaviour per requirement, testable, in the present tense: "The app shows …".
- Numbers rather than "soon" or "a few": "10 minutes later", "within 1 minute".
- UI text quoted from the specification is fixed wording. Elsewhere, UI text is a suggestion.
- Keep the document's structure and headings. Don't reorder rows by ID.
