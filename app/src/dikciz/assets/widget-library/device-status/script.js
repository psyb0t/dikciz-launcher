(() => {
    const REFRESH_MS = 5000;
    const BYTES_PER_GIB = 1024 ** 3;
    const PERCENT = 100;
    const HISTORY_LIMIT = 36;
    const GRAPH_WIDTH = 300;
    const GRAPH_HEIGHT = 60;
    const MINIMUM_GRAPH_PERCENT = 10;
    const KILOHERTZ_PER_GIGAHERTZ = 1000000;
    const KILOHERTZ_PER_MEGAHERTZ = 1000;
    const byId = id => document.getElementById(id);
    const bytes = value => Number.isFinite(value) && value >= 0 ? (value / BYTES_PER_GIB).toFixed(1) + " GiB" : "Not reported";
    const text = (id, value) => { byId(id).textContent = value; };
    const ratio = (part, whole) => Number.isFinite(part) && whole > 0 ? Math.max(0, Math.min(PERCENT, part * PERCENT / whole)) : null;
    const meter = (id, percent) => { byId(id).style.width = (percent ?? 0) + "%"; };
    let ready = false;
    let busy = false;
    let history = [];
    async function refresh() {
        if (!ready || busy || document.hidden || !selfWidget.visible) return;
        busy = true;
        byId("refresh").disabled = true;
        try {
            const state = await dikciz.command("deviceState");
            const system = state.system;
            if (!system?.memory || !system.storage || !system.cpu) throw new Error("Device metrics were not reported.");
            const charge = ratio(state.batteryLevel, state.batteryScale);
            text("battery", charge === null || state.batteryLevel < 0 ? "..." : Math.round(charge) + "%");
            if (charge !== null && state.batteryLevel >= 0) {
                byId("battery-gauge").style.setProperty("--charge", charge / PERCENT * 360 + "deg");
                byId("battery-gauge").setAttribute("aria-valuenow", String(Math.round(charge)));
            } else {
                byId("battery-gauge").removeAttribute("aria-valuenow");
            }
            text("temperature", Number.isFinite(state.batteryTemperatureTenthsCelsius) && state.batteryTemperatureTenthsCelsius !== -1 ? (state.batteryTemperatureTenthsCelsius / 10).toFixed(1) + " °C" : "Temperature hidden");
            text("voltage", Number.isFinite(state.batteryVoltageMillivolts) && state.batteryVoltageMillivolts > 0 ? (state.batteryVoltageMillivolts / 1000).toFixed(2) + " V · Battery voltage" : "Voltage not reported");
            text("power", (state.chargingSource > 0 ? "Plugged in" : "On battery") + (state.powerSaveMode ? " · Saver on" : ""));
            text("network", (state.vpn ? "VPN" : state.wifi ? "Wi-Fi" : state.cellular ? "Mobile" : state.networkAvailable ? "Connected" : "Offline") + (state.internetValidated ? " · Online" : ""));
            text("ram", bytes(system.memory.usedBytes));
            text("available", "of " + bytes(system.memory.totalBytes) + " · " + bytes(system.memory.availableBytes) + " free");
            meter("ram-meter", ratio(system.memory.usedBytes, system.memory.totalBytes));
            text("storage", bytes(system.storage.availableBytes));
            text("storage-total", "free of " + bytes(system.storage.totalBytes));
            meter("storage-meter", ratio(system.storage.totalBytes - system.storage.availableBytes, system.storage.totalBytes));
            const cpu = system.cpu;
            const percent = cpu.appSampleAvailable && Number.isFinite(cpu.appPercentOfCapacity) ? cpu.appPercentOfCapacity : null;
            text("app-cpu", percent === null ? "Sampling" : percent.toFixed(1) + "%");
            if (percent !== null) {
                history.push(percent);
                history = history.slice(-HISTORY_LIMIT);
                const scale = Math.min(PERCENT, Math.max(MINIMUM_GRAPH_PERCENT, Math.ceil(Math.max(...history) / MINIMUM_GRAPH_PERCENT) * MINIMUM_GRAPH_PERCENT));
                const columns = Math.max(1, history.length - 1);
                const points = history.map((value, index) => (index * GRAPH_WIDTH / columns).toFixed(1) + "," + (GRAPH_HEIGHT - value * GRAPH_HEIGHT / scale).toFixed(1));
                byId("cpu-history").setAttribute("points", points.join(" "));
                text("cpu-scale", "0 to " + scale + "% capacity");
            }
            text("cores", cpu.logicalProcessors + " available processors");
            text("cpu", cpu.systemAvailable && Number.isFinite(cpu.systemPercent) ? "System " + cpu.systemPercent.toFixed(1) + "%" : cpu.reason === "warming_up" ? "Sampling system" : "OS protected");
            text("cpu-note", cpu.systemAvailable ? "Real per-core load where exposed. Clocks are not load percentages." : cpu.reason === "warming_up" ? "Collecting the next CPU sample." : "Android protects whole-phone CPU load. App permissions cannot unlock it. Dikciz CPU history is measured normally.");
            const coreNodes = (Array.isArray(cpu.cores) ? cpu.cores : []).map(core => {
                const item = document.createElement("div");
                item.className = "core";
                const name = document.createElement("strong");
                name.textContent = "CPU " + core.id;
                const value = document.createElement("span");
                const frequency = Number.isFinite(core.frequencyKilohertz) && core.frequencyKilohertz > 0;
                value.textContent = core.online === false ? "Offline" : core.usageAvailable && Number.isFinite(core.usagePercent) ? core.usagePercent.toFixed(0) + "% load" : frequency ? (core.frequencyKilohertz >= KILOHERTZ_PER_GIGAHERTZ ? (core.frequencyKilohertz / KILOHERTZ_PER_GIGAHERTZ).toFixed(2) + " GHz" : (core.frequencyKilohertz / KILOHERTZ_PER_MEGAHERTZ).toFixed(1) + " MHz") : "Protected";
                item.append(name, value);
                const fraction = core.usageAvailable ? core.usagePercent : frequency ? ratio(core.frequencyKilohertz, core.maximumFrequencyKilohertz) : null;
                if (Number.isFinite(fraction)) {
                    const track = document.createElement("div");
                    track.className = "meter";
                    const fill = document.createElement("i");
                    fill.style.width = Math.max(0, Math.min(PERCENT, fraction)) + "%";
                    track.append(fill);
                    item.append(track);
                }
                return item;
            });
            byId("core-grid").replaceChildren(...coreNodes);
            const sensors = system.sensors;
            if (sensors) {
                text("sensor-count", "Device details · " + sensors.count + " sensors");
                byId("sensors").replaceChildren(...sensors.items.map(sensor => {
                    const item = document.createElement("li");
                    item.textContent = sensor.name + " · " + sensor.vendor;
                    return item;
                }));
            }
            text("status", "Updated " + new Date(system.sampledAtMilliseconds).toLocaleTimeString() + " · Live while this page is visible");
        } catch (error) { text("status", "Device refresh failed: " + error.message + ". Previous readings may be stale."); }
        finally { busy = false; byId("refresh").disabled = false; }
    }
    byId("refresh").addEventListener("click", refresh);
    window.addEventListener("dikciz-ready", () => { ready = true; refresh(); });
    window.addEventListener("dikciz-visibility", refresh);
    document.addEventListener("visibilitychange", refresh);
    setInterval(refresh, REFRESH_MS);
})();

(() => {
    const card = document.querySelector(".card");
    const reduced = matchMedia("(prefers-reduced-motion: reduce)");
    const visibility = () => { document.body.dataset.visible = String(!document.hidden && window.selfWidget?.visible !== false); };
    card?.addEventListener("pointermove", event => {
        if (reduced.matches || event.pointerType === "touch") return;
        const bounds = card.getBoundingClientRect();
        card.style.setProperty("--ry", ((event.clientX - bounds.left) / bounds.width * 6 - 3) + "deg");
        card.style.setProperty("--rx", (3 - (event.clientY - bounds.top) / bounds.height * 6) + "deg");
    });
    card?.addEventListener("pointerleave", () => {
        card.style.setProperty("--rx", "0deg");
        card.style.setProperty("--ry", "0deg");
    });
    document.addEventListener("visibilitychange", visibility);
    window.addEventListener("dikciz-visibility", visibility);
    visibility();
})();
