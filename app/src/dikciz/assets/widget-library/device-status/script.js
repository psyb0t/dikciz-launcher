(() => {
    const REFRESH_MS = 30000;
    const BYTES_PER_GIB = 1024 ** 3;
    const PERCENT = 100;
    let ready = false;
    let busy = false;
    const text = (id, value) => { document.getElementById(id).textContent = value; };
    const bytes = value => Number.isFinite(value) && value >= 0 ? (value / BYTES_PER_GIB).toFixed(1) + " GiB" : "Unavailable";
    async function refresh() {
        if (!ready || busy || !selfWidget.visible || document.hidden) return;
        busy = true;
        document.getElementById("refresh").disabled = true;
        try {
            const state = await dikciz.command("deviceState");
            text("battery", Number.isFinite(state.batteryLevel) && state.batteryLevel >= 0 && state.batteryScale > 0 ? Math.round(state.batteryLevel * PERCENT / state.batteryScale) + "%" : "Unavailable");
            text("storage", bytes(state.system?.storage?.availableBytes));
            text("ram", bytes(state.system?.memory?.availableBytes));
            text("network", state.vpn ? "VPN" : state.wifi ? "Wi-Fi" : state.cellular ? "Mobile" : state.networkAvailable ? "Network" : "Offline");
            text("power", (state.chargingSource > 0 ? "Charging" : "On battery") + (state.powerSaveMode ? ". Battery saver on." : ". Battery saver off."));
            text("status", "Updated " + new Date().toLocaleTimeString() + ". Storage is the app-data filesystem. Refreshes every 30 seconds while visible.");
        } catch (error) { text("status", "Device refresh failed: " + error.message + ". Previous values may be stale."); }
        finally { busy = false; document.getElementById("refresh").disabled = false; }
    }
    document.getElementById("refresh").addEventListener("click", refresh);
    window.addEventListener("dikciz-ready", () => { ready = true; refresh(); });
    window.addEventListener("dikciz-visibility", refresh);
    document.addEventListener("visibilitychange", refresh);
    setInterval(refresh, REFRESH_MS);
})();
