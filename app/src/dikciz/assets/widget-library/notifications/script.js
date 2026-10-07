(() => {
    const ACTION_POST_NOTIFICATION = "postNotification";
    const NOTIFICATION_TEXT = "Sent directly from the HTML widget library.";
    const NOTIFICATION_TITLE = "Dikciz widget test";
    const COMMAND_SCRIPT_LOGS = "scriptLogs";
    const OUTCOME_EXECUTED = "executed";
    const OUTCOME_PERMISSION_DENIED = "android_permission_denied";
    const STATUS_PERMISSION_REQUIRED = "Notification permission is required before Dikciz can post this.";
    const STATUS_SENT = "Notification posted.";
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

    document.getElementById("send").addEventListener("click", async () => {
        status.textContent = "Posting notification.";
        try {
            const result = await window.dikciz.dispatch({
                actions: [{
                    type: ACTION_POST_NOTIFICATION,
                    title: NOTIFICATION_TITLE,
                    text: NOTIFICATION_TEXT,
                }],
            });
            const outcome = result.actions?.[0]?.outcome;
            if (outcome === OUTCOME_EXECUTED) {
                status.textContent = STATUS_SENT;
                return;
            }
            if (outcome === OUTCOME_PERMISSION_DENIED) {
                status.textContent = STATUS_PERMISSION_REQUIRED;
                return;
            }
            status.textContent = `Notification rejected: ${outcome || "unknown"}`;
        } catch (error) {
            status.textContent = `Notification failed: ${error.message}`;
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
