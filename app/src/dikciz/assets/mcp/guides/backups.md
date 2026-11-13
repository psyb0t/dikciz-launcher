# Encrypted backups

Use backupCreate with passphrase of 12 or more characters. includeApiKeys defaults false. All archives are encrypted. Public home assets, scripts, widget state, provider profiles, chat history, apps/ source/state/files/runtime and assistant/ skills/agents/rules are included; control authentication, listener ports, logs and Android grants are excluded.

Use backupsList, then backupPreview with path and passphrase. Read counts and the returned sha256. Restore only the owner-selected preview: backupRestore requires previewId, exact sha256 and confirm=true. It replaces current home and chat. activateAutomation defaults false for scripts, apps and chat event subscriptions. Imported pending/claimed app timers always become needs_review with reason backup_restored; active windows/native sessions are not resumed. Inspect and explicitly re-arm intended work. Format 1 archives preserve apps/assistant because they did not include them. Rollback restores public files and app-runtime records together, using the chat transaction as commit marker. Stop active and queued assistant turns before restore.

Do not replay interrupted tool calls after restore. Keys omitted from a backup need re-entry. Preview expires after 15 minutes and private staging is excluded from file APIs. Limits: 4096 files, 32 MiB public data, 128 profiles, 1000 conversations, 10000 messages, 64 MiB archive.

Commands use owner access. Create, preview and restore are unavailable in read-only mode. The full guide is available through documentation search: backups.
