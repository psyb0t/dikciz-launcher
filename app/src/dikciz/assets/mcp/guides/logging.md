# Logs and diagnosis

Start with `scriptLogs` for bounded, sanitized records and `automationStatus` for script state and Android access. The log projection includes timestamps, event names, script/widget IDs, outcomes, reasons and bounded Lua diagnostics. It does not return raw source, state, event payloads or entire configuration.

Persistent JSON Lines logs live under `/sdcard/Dikciz/logs/` in daily `YYYY-MM-DD.log` files. Configuration controls `logging.level`, `retentionDays` and `maxTotalBytes`. Defaults are `info`, seven days and 16777216 bytes. Use `debug` only when the owner wants more diagnostic detail. Valid configuration changes reload logging settings. Retention also respects the disk budget.

If a script appears inactive, check script enabled state, subscription event, policy enabled state, capability, action allowlist and Android access. Then inspect final run status and action outcomes. A publish acknowledgement or trigger acknowledgement does not prove a run completed.

Use the file API for selected raw log chunks when the safe viewer lacks the needed detail. Downloaded files are not redacted by that viewer. Avoid exposing personal data or credentials. Read only the authorized diagnostic window.

For process and system messages, [stream logcat](dikciz://docs/recipes/stream-logs). Unprivileged Android logcat cannot expose every system or other-app record. Root is optional and must be explicitly authorized. `diagnostics` also reports HTML renderer counts and document budgets.
