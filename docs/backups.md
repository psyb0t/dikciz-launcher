---
title: "Encrypted backup and restore"
description: "Create encrypted portable backups, preview contents and restore selected Dikciz data while preserving destination remote authentication and ports."
permalink: /backups/
nav_order: 1
parent: "Operations"
---

# Encrypted backup and restore

Open **Dikciz settings**, **Backup and restore**. Enter a passphrase of at least 12 characters and create a backup. Archives are stored in /sdcard/Dikciz/backups with the .dikciz-backup extension. Copy an archive somewhere safe with the ordinary file tools.

## What is included

A backup contains home configuration, page and widget documents and state, scripts, reusable HTML templates, themes, fonts, wallpapers, automation configuration, provider profiles and chat transcripts. Provider API keys are omitted unless **Include provider API keys** is selected. Every archive is encrypted, including archives without keys.

Remote passwords, access tokens, control listener settings, logs and unrelated operational databases are excluded. Custom-app runtime records are the explicit exception described below. A restore preserves the destination device's authentication and listener address/ports. Android permissions and special access are device settings and must be granted separately. Package installations are not backed up.

[Custom apps](custom-apps.md) are included with their complete `apps/` source, JSON state, data files and private installation/timer/run records. This includes SOS plan/run state and recordings within the archive limits. [Assistant skills, agents and rules](assistant-library.md) under `assistant/` are also included. Source-only app exports remain separate reusable bundles. An app backup holds up to 8192 runtime records and 4 MiB of runtime JSON in addition to the public-file limits.

Limits are 4096 public files, 2048 UTF-8 bytes per relative path, 32 MiB of public file data, 128 provider profiles, 1000 conversations, 10,000 messages and 64 MiB encoded archive size. Large recordings or conversation histories can exceed those bounds. Backups fail explicitly rather than omitting files.

## Preview before replacing data

Follow the [illustrated backup workflow](screens.md#backups-and-recovery) from archive creation to the restore preview. Creating an archive does not replace the running setup. Preview is a separate validation step; only its Restore confirmation replaces data.

Select or enter an archive path, enter its passphrase and choose **Preview restore**. Preview checks encryption, file paths, checksums, configuration and chat records before showing counts. It stages plaintext privately, inaccessible through the ordinary file commands. Up to four previews can be staged; each expires after 15 minutes.

Restore replaces the current home and chat history. Scripts, custom apps and chat event subscriptions remain disabled unless activation is explicitly selected. Imported pending or claimed app timers always become `needs_review` with reason `backup_restored`, even when activation is selected. Re-arm intended work explicitly after inspecting its state. Active alerts, WebViews, microphone/camera sessions and other native resources are not resumed. Removed bundled-app records are preserved so deleted bundles do not return automatically. Restored queued or running turns become interrupted; no old tool call is replayed. Stop active and queued assistant work first. Provider keys omitted from the archive must be entered again.

Archive format 2 includes apps and assistant files. Use v0.31.4 or newer to restore it; previous APKs cannot read this format. Format 1 archives remain readable and preserve those directories and their runtime records because older archives did not contain them. The restore writes matching public-file and app-runtime rollback data before its checkpoint, replaces app records transactionally, and uses the chat SQLite transaction as its commit marker. Recovery rolls back files and app runtime together if that marker was not committed; otherwise it retains the completed restore. App execution is serialized with snapshots and restore. A backup requested from an app callback fails with `backup_busy` rather than recursively backing up a changing state. On restart, the launcher and background service finish recovery before accepting the home. Other configuration readers stop while the checkpoint exists, preventing starter creation during partial file replacement. Starter identity is included so a restored custom home is preserved on later APK updates. Recovery failure remains an explicit error; it does not load partial configuration.

## Shared commands

| Command | Purpose |
| --- | --- |
| backupCreate | Create an encrypted archive. Requires passphrase; includeApiKeys defaults false. |
| backupsList | List retained archives with pagination. |
| backupPreview | Decrypt and validate path with passphrase. Returns previewId, sha256 and counts. |
| backupRestore | Replace data using previewId, its exact sha256 and confirm=true. activateAutomation defaults false. |

These operations use the shared HTTP, WebSocket, MCP, HTML and Lua command target. Remote clients need owner access or explicit backup operation grants from the [permission reference](token-permissions.md). A backup grant can expose private data, and restore can replace executable configuration. Create, preview and restore are hidden and blocked in read-only assistant mode because they write files or staging data. Listing is read-only. Inspect the installed schemas for exact bounds.

The archive format uses AES-256-GCM with a fresh 16-byte salt and 12-byte nonce, and PBKDF2-HMAC-SHA256 with 600,000 iterations. The passphrase is never saved. Losing it makes the archive unrecoverable.

## On the phone

<img src="{{ "/images/operations/backups.png" | relative_url }}" alt="Backup and restore with a passphrase, optional provider keys and a separate preview action." width="280">
