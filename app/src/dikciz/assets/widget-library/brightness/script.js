(() => {
    const COMMAND_INTENT = "intent";
    const COMMAND_SCRIPT_LOGS = "scriptLogs";
    const DISPLAY_SETTINGS_ACTION = "android.settings.DISPLAY_SETTINGS";
    const INTENT_TYPE_ACTIVITY = "activity";
    const STATUS_OPENED = "Android display settings opened.";
    const status = document.getElementById("status");
    const log = document.getElementById("log");

    const latestLogMessage = records => {
        const latest = Array.isArray(records) ? records[0] : undefined;
        if (!latest) return "No launcher log records yet.";
        const level = typeof latest.level === "string" ? latest.level : "info";
        const event = typeof latest.event === "string" ? latest.event : "unknown event";
        const source = typeof latest.scriptId === "string" ? latest.scriptId : "dikciz";
        return `Latest ${level}: ${event} (${source})`;
    };

    document.getElementById("open").addEventListener("click", async () => {
        try {
            await window.dikciz.command(COMMAND_INTENT, {
                intentType: INTENT_TYPE_ACTIVITY,
                action: DISPLAY_SETTINGS_ACTION,
            });
            status.textContent = STATUS_OPENED;
        } catch (error) {
            status.textContent = `Settings failed: ${error.message}`;
        }
    });

    document.getElementById("logs").addEventListener("click", async () => {
        try {
            const result = await window.dikciz.command(COMMAND_SCRIPT_LOGS);
            log.textContent = latestLogMessage(result.records);
        } catch (error) {
            log.textContent = `Log query failed: ${error.message}`;
        }
    });
})();
