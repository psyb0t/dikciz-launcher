---
name: do-with-opus
description: "Delegate an explicitly requested implementation task to Claude Opus, then review, verify, and send correction passes until it meets the stated acceptance criteria."
user-invocable: true
disable-model-invocation: true
metadata:
  author: psyb0t
  version: "1.0"
---

# Do with Opus

Use only when the user explicitly asks to do a task with Opus, Claude, or
`/do-with-opus`. The primary Codex agent remains responsible for scope,
review, verification, and the final answer. Opus is an implementation worker,
not an authority whose claims can be accepted without evidence.

## Start with a bounded work order

1. Inspect the current worktree, applicable `AGENTS.md` files, relevant source,
   and the requested acceptance criteria before prompting Opus.
2. Give Opus one bounded implementation task. Name owned files or modules,
   required public behavior, non-goals, and the narrow verification command.
   State existing user changes that it must preserve.
3. Include the project boundaries in the prompt: no raw Git mutation, no
   upstream Fossify edits outside the documented additive boundary, all Android
   work through the root Makefile and shared VM, no physical device unless the
   user explicitly asks, and no broad suite unless the task warrants it.
4. Tell Opus to use its own planning and review as needed, but to leave all
   edits in this worktree, run only the named focused checks, retain command
   logs, and return changed files, verification, remaining risks, and any
   blocker honestly.

## Run Opus

From the named workspace, use the installed wrapper in one-shot mode:

```bash
claudebox -p "<bounded work order>" --model opus --effort high --output-format stream-json
```

The wrapper automatically continues the workspace session. Keep that session
for follow-up repairs. Use `--resume <session-id>` only when a specific prior
session is required. Do not use the API or MCP surface for this workflow:
their `thinking` parameter does not set Claude effort.

If Opus starts a container for this workspace and it must be stopped, use only
`claudebox stop` for that exact workspace. Never kill Docker containers
directly or touch an unrelated container.

## Review before accepting

1. Read the full Opus output and inspect the exact diff. Do not trust a status
   summary or source-string test as proof.
2. Compare every change against the user's actual request, the active plan,
   architecture boundaries, data migration behavior, error paths, and public
   documentation. Check for scope creep and lost user changes.
3. Run the required focused build, lint, emulator selector, protocol check, or
   documentation validation yourself. Retain the full log and inspect the
   screenshot or UI layout whenever visible behavior changed.
4. Accept the implementation only when all explicit acceptance criteria pass,
   documentation matches the result, and the current shared VM is left in the
   required clean state.

## Correction loop

When the review finds a defect, send Opus the same session a concise correction
order containing:

1. exact file, behavior, or evidence that fails;
2. required outcome and regression case;
3. scope that must remain untouched; and
4. the next narrow verification required.

Repeat review and focused verification after every repair. Stop only when the
acceptance criteria are met, the user changes scope, or a real external blocker
needs a user decision. Report an unverified boundary as unverified. Never call
work complete because Opus says it is complete.
