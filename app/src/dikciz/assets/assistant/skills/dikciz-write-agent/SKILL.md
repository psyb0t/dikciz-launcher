---
name: "dikciz-write-agent"
description: "Create or revise a named assistant agent when the owner wants a specialized prompt and isolated conversation."
---

# Write a named phone agent

1. List the owner library and inspect existing agent descriptions. Read the rule and skill guides only if the requested behavior belongs in those surfaces.
2. Discover assistantLibraryValidate, assistantLibraryManage and the exact tool names needed by the agent. Read an existing file before editing and retain its revision.
3. Draft agents/<name>.md under the assistant root with front matter name and a quoted description, followed by the agent's purpose, inputs, procedure, uncertainty handling and completion evidence. Keep the prompt independent of current personal data or debug examples.
4. Set readOnly: true for an inspection-only agent. Optional tools and disallowedTools contain exact dikciz_ MCP tool names. Restrictions only narrow the parent's authority; they never grant it. List only tools that discovery actually returned. Do not use executable harness fields, hooks or external server configuration.
5. Validate, save with the expectedRevision from the current file, and read back the exact text. Resolve revision conflicts by rereading and reconciling changes. Use an empty expectedRevision only for creation.
6. Invoke the saved agent using phone_agent with its name and a bounded task. Inspect the child result and its retained conversation. Verify restrictions with read-only or simulated work. Never claim a successful phone effect without its actual result.

Named agents have isolated persistent histories, inherit provider and budget settings, and cannot widen frozen read-only or parent tool restrictions. A child cannot delegate indefinitely. Do not embed real credentials or private test content in its reusable prompt.
