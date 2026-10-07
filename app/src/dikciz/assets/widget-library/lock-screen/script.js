(() => {
    const ACTION_LOCK_DEVICE = "lockDevice";
    const COMMAND_SCRIPT_LOGS = "scriptLogs";
    const OUTCOME_DEVICE_ADMIN_INACTIVE = "device_admin_inactive";
    const OUTCOME_EXECUTED = "executed";
    const STATUS_ADMIN_REQUIRED = "Device Administration is required before Dikciz can lock the screen.";
    const STATUS_LOCKED = "Screen locked.";
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

    document.getElementById("lock").addEventListener("click", async () => {
        status.textContent = "Lock requested.";
        try {
            const result = await window.dikciz.dispatch({
                actions: [{ type: ACTION_LOCK_DEVICE }],
            });
            const outcome = result.actions?.[0]?.outcome;
            if (outcome === OUTCOME_EXECUTED) {
                status.textContent = STATUS_LOCKED;
                return;
            }
            if (outcome === OUTCOME_DEVICE_ADMIN_INACTIVE) {
                status.textContent = STATUS_ADMIN_REQUIRED;
                return;
            }
            status.textContent = `Lock rejected: ${outcome || "unknown"}`;
        } catch (error) {
            status.textContent = `Lock failed: ${error.message}`;
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
