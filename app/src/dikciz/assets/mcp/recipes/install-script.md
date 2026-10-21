# Install a Lua script

Read `configGet` first and preserve its scripts, automation records and policies. Use a new valid ID, for example `example-events`, and confirm it does not already identify user work.

For an atomic installation, append a script object to the assembled `scripts` array with `id`, `title`, `enabled: true`, `apiVersion: 1`, `source` and initial `state: {}`. Append its policy and subscription objects to the existing `automation.policies` and `automation.scripts` arrays. Submit the complete preserved document with `configReplace`. Never replace an existing automation namespace with only the example.

For direct public-file installation, inspect an existing native-created script's `script.json` and manifest references first. Write `/sdcard/Dikciz/scripts/<id>/main.lua`, its matching metadata and state, and the policy and subscription files under `automation/`. Update the corresponding manifest references without dropping existing IDs. Use base64 file writes and create parents first. Wait for file observers to accept the complete tree.

The native Lua editor creates script source and metadata; it does not create a custom-event policy automatically. Enable the script, policy and subscription. Custom events require `manualTrigger`; returned actions require their policy allowlist and any Android access.

Verify `automationStatus`, read the normalized configuration, trigger the intended event, and inspect final run status, logs and saved state. Source upload success is not execution success.

[Pipeline setup](dikciz://docs/recipes/event-setup). [Lua guide](dikciz://docs/guides/lua).
