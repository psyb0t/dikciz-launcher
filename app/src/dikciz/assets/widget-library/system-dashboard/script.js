(() => {
    const REFRESH_MS = 5000;
    const BYTES_PER_GIB = 1024 ** 3;
    const PERCENT = 100;
    const byId = id => document.getElementById(id);
    const bytes = value => Number.isFinite(value) && value >= 0 ? (value / BYTES_PER_GIB).toFixed(1) + " GiB" : "Unavailable";
    let busy = false;
    async function refresh() {
        if (busy || document.hidden) return;
        busy = true;
        byId("refresh").disabled = true;
        try {
            const state = await dikciz.command("deviceState");
            const system = state.system;
            if (!system?.memory || !system.storage || !system.cpu || !system.sensors) throw new Error("System metrics are unavailable in this version.");
            byId("ram").textContent = bytes(system.memory.usedBytes) + " / " + bytes(system.memory.totalBytes);
            byId("available").textContent = bytes(system.memory.availableBytes);
            byId("storage").textContent = bytes(system.storage.availableBytes) + " / " + bytes(system.storage.totalBytes);
            byId("battery").textContent = Number.isFinite(state.batteryLevel) && state.batteryLevel >= 0 && state.batteryScale > 0 ? Math.round(state.batteryLevel * PERCENT / state.batteryScale) + "%" : "Unavailable";
            byId("temperature").textContent = Number.isFinite(state.batteryTemperatureTenthsCelsius) && state.batteryTemperatureTenthsCelsius >= 0 ? (state.batteryTemperatureTenthsCelsius / 10).toFixed(1) + " °C" : "Unavailable";
            byId("cpu").textContent = system.cpu.systemAvailable && Number.isFinite(system.cpu.systemPercent) ? system.cpu.systemPercent.toFixed(1) + "%" : "Unavailable (" + (system.cpu.reason || "Android restriction") + ")";
            byId("app-cpu").textContent = system.cpu.appSampleAvailable && Number.isFinite(system.cpu.appPercentOfOneCore) ? system.cpu.appPercentOfOneCore.toFixed(1) + "%" : "Sampling";
            byId("cores").textContent = String(system.cpu.logicalProcessors);
            byId("power").textContent = (state.chargingSource > 0 ? "Plugged in" : "On battery") + (state.powerSaveMode ? ". Battery saver on." : ". Battery saver off.");
            byId("network").textContent = (state.vpn ? "VPN" : state.wifi ? "Wi-Fi" : state.cellular ? "Mobile" : state.networkAvailable ? "Network" : "Offline") + (state.internetValidated ? ". Internet validated." : ". Internet not confirmed.");
            byId("sensor-count").textContent = "Sensor inventory: " + system.sensors.count + (system.sensors.truncated ? " (first 64 shown)" : "");
            byId("sensors").replaceChildren(...system.sensors.items.map(sensor => {
                const item = document.createElement("li");
                item.textContent = sensor.name + " · " + sensor.vendor + " · type " + sensor.type;
                return item;
            }));
            byId("status").textContent = "Updated " + new Date(system.sampledAtMilliseconds).toLocaleTimeString() + ". Refreshes every five seconds while visible.";
        } catch (error) { byId("status").textContent = "System refresh failed: " + error.message + ". Previous values may be stale."; }
        finally { busy = false; byId("refresh").disabled = false; }
    }
    byId("refresh").addEventListener("click", refresh);
    window.addEventListener("dikciz-ready", refresh);
    document.addEventListener("visibilitychange", () => { if (!document.hidden && window.dikciz) refresh(); });
    setInterval(() => { if (window.dikciz) refresh(); }, REFRESH_MS);
})();
