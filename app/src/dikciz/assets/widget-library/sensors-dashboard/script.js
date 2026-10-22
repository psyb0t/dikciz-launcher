(() => {
    const POLL_MS = 1500;
    const SAMPLE_MS = 500;
    const PAGE_SIZE = 200;
    const status = document.getElementById("status");
    const container = document.getElementById("sensors");
    const toggle = document.getElementById("toggle");
    const cards = new Map();
    let timer;
    let busy = false;
    let ready = false;
    let paused = false;
    function render(sensor) {
        let nodes = cards.get(sensor.sensorId);
        if (!nodes) {
            const article = document.createElement("section");
            article.className = "card";
            const title = document.createElement("h2");
            const description = document.createElement("p");
            const values = document.createElement("pre");
            const detail = document.createElement("p");
            const permission = document.createElement("button");
            permission.type = "button";
            permission.textContent = "Review sensor access";
            permission.addEventListener("click", async () => {
                try {
                    const result = await dikciz.command("requestDeviceAccess", {requirementId:permission.dataset.requirementId});
                    status.textContent = result.instructions || "Review access on your phone, then return here.";
                } catch (error) { status.textContent = "Access request failed: " + error.message; }
            });
            article.append(title, description, values, detail, permission);
            container.append(article);
            nodes = {article, title, description, values, detail, permission};
            cards.set(sensor.sensorId, nodes);
        }
        nodes.title.textContent = sensor.name;
        nodes.description.textContent = "ID " + sensor.sensorId + " · " + sensor.stringType + " · " + sensor.vendor;
        nodes.values.textContent = sensor.available
            ? sensor.values.map((value, index) => "[" + index + "] " + (Number.isFinite(value) ? value.toFixed(4) : "unavailable")).join("\n")
            : (sensor.reason || "Unavailable").replaceAll("_", " ");
        nodes.detail.textContent = "Mode " + sensor.reportingMode + " · " + (sensor.available
            ? "Accuracy " + sensor.accuracy + " · sample age " + sensor.ageMilliseconds + " ms"
            : "No reading reported by Android") + " · range " + sensor.maximumRange + " · resolution " + sensor.resolution;
        nodes.permission.dataset.requirementId = sensor.requirementId || "";
        nodes.permission.hidden = !sensor.requirementId;
    }
    async function refresh() {
        clearTimeout(timer);
        if (!ready || busy || paused || document.hidden || !window.dikciz || !window.selfWidget?.visible) return;
        busy = true;
        try {
            const seen = new Set();
            let offset = 0;
            let available = 0;
            let more;
            do {
                const result = await dikciz.command("sensorReadings", {offset, limit:PAGE_SIZE, timeoutMilliseconds:SAMPLE_MS});
                for (const sensor of result.items) {
                    seen.add(sensor.sensorId);
                    render(sensor);
                    if (sensor.available) available++;
                }
                offset += result.items.length;
                more = result.hasMore && result.items.length > 0;
            } while (more && !document.hidden && !paused);
            if (!more) {
                for (const [id, nodes] of cards) {
                    if (seen.has(id)) continue;
                    nodes.article.remove();
                    cards.delete(id);
                }
            }
            status.textContent = offset + " sensors, " + available + " with readings. Updated " + new Date().toLocaleTimeString() +
                ". Values use Android sensor units. Trigger sensors wait for their event.";
        } catch (error) {
            status.textContent = "Sensor sampling failed: " + error.message + ". Previous readings may be stale.";
        } finally {
            busy = false;
            if (!paused && !document.hidden && window.selfWidget?.visible) timer = setTimeout(refresh, POLL_MS);
        }
    }
    toggle.addEventListener("click", () => {
        paused = !paused;
        toggle.textContent = paused ? "Resume" : "Pause";
        clearTimeout(timer);
        if (!paused) refresh();
        else status.textContent = "Sampling paused. Displayed values are from the last sample.";
    });
    window.addEventListener("dikciz-ready", () => { ready = true; refresh(); });
    window.addEventListener("dikciz-visibility", event => {
        clearTimeout(timer);
        if (event.detail.visible) refresh();
    });
    document.addEventListener("visibilitychange", () => {
        clearTimeout(timer);
        if (!document.hidden) refresh();
    });
    window.addEventListener("pagehide", () => { paused = true; clearTimeout(timer); });
})();
