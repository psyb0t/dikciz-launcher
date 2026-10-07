(() => {
    const COMMAND_DIAGNOSTICS = "diagnostics";
    const COMMAND_SCRIPT_LOGS = "scriptLogs";
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

    const refresh = async () => {
        try {
            const diagnostics = await window.dikciz.command(COMMAND_DIAGNOSTICS);
            status.textContent = `Dikciz is running. ${Object.keys(diagnostics).length} diagnostic fields available.`;
        } catch (error) {
            status.textContent = `Diagnostics failed: ${error.message}`;
        }
    };
    document.getElementById("refresh").addEventListener("click", refresh);
    document.getElementById("logs").addEventListener("click", async () => {
        try {
            const result = await window.dikciz.command(COMMAND_SCRIPT_LOGS);
            log.textContent = latestLogMessage(result.records);
        } catch (error) {
            log.textContent = `Log query failed: ${error.message}`;
        }
    });
    window.addEventListener("dikciz-ready", refresh);
})();
