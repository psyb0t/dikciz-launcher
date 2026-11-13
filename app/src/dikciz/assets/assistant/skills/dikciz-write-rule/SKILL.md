---
name: "dikciz-write-rule"
description: "Save or revise persistent owner instructions when the owner asks for an ongoing behavior or a path-scoped rule."
---

# Write an owner rule

1. List and read existing owner rules. Prefer editing the rule that already owns the behavior. Avoid contradictory or duplicate instructions.
2. Use rules/<name>.md for a focused rule. AGENTS.md, CLAUDE.md and SOUL.md at the assistant root apply globally. Optional paths front matter describes scope; it is prompt metadata, not a filesystem permission boundary.
3. Write a concise instruction that describes the behavior, when it applies and how to verify it. Use a skill for a long procedure and a named agent for an isolated specialized conversation. Describe the shape of the requirement without embedding actual passwords, contacts or other private debugging examples.
4. Discover assistantLibraryValidate and assistantLibraryManage schemas. Read the current file and retain its revision. Validate the complete draft, save using that expectedRevision, and read it back. Use an empty revision for a new file. A conflict means reread and reconcile, never force an overwrite.
5. Explain where the rule was saved and its scope. The next model round reloads rules. A rule cannot bypass Android access, read-only mode or tool restrictions, and does not itself create schedules, listeners or native permissions.

Disable an instruction with assistantLibraryManage action setEnabled and enabled false. Deletion removes only that instruction file and preserves its support files. Ask for the specific destructive change when it was not requested.
