(() => {
    const MAX_APPS = 8, ICON_SIZE = 64, REFRESH_MS = 60000, PULSE_MS = 250, PULSE_COUNT = 8;
    const byId = id => document.getElementById(id);
    let ready = false, busy = false, lastLoaded = 0, flashing = false, torchOwned = false, generation = 0;
    const visible = () => ready && selfWidget.visible && !document.hidden;
    const status = message => { byId("demo-status").textContent = message; };
    const delay = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));
    async function refresh() {
        if (!visible() || busy || Date.now() - lastLoaded < REFRESH_MS) return;
        busy = true;
        try {
            const result = await dikciz.command("appCatalogue");
            const seen = new Set();
            const apps = (Array.isArray(result.apps) ? result.apps : []).filter(app => {
                if (seen.has(app.packageName)) return false;
                seen.add(app.packageName);
                return true;
            }).sort((a, b) => (result.usageAvailable ? b.foregroundMilliseconds - a.foregroundMilliseconds : 0) || a.label.localeCompare(b.label)).slice(0, MAX_APPS);
            const used = result.usageAvailable && apps.some(app => app.foregroundMilliseconds > 0);
            byId("title").textContent = used ? "Most used" : "Your apps";
            byId("count").textContent = used ? "Last 7 days" : "On this phone";
            byId("access").hidden = result.usageAvailable;
            byId("apps").replaceChildren();
            for (const app of apps) {
                const button = document.createElement("button");
                button.type = "button";
                const initial = document.createElement("span");
                initial.className = "initial";
                initial.textContent = app.label.slice(0, 1);
                initial.setAttribute("aria-hidden", "true");
                const label = document.createElement("span");
                label.className = "label";
                label.textContent = app.label;
                button.append(initial, label);
                button.addEventListener("click", async () => {
                    button.disabled = true;
                    try {
                        const launched = await dikciz.command("appAction", {action:"launch", component:app.component});
                        byId("status").textContent = launched.outcome === "launched" ? app.label + " opened." : "Open requested. " + (launched.reason || "Check Android.");
                    } catch (error) { byId("status").textContent = "Could not open app: " + error.message; }
                    finally { button.disabled = false; }
                });
                byId("apps").append(button);
                try {
                    const icon = await dikciz.command("appIcon", {component:app.component, sizePixels:ICON_SIZE});
                    const image = document.createElement("img");
                    image.alt = ""; image.width = image.height = ICON_SIZE;
                    image.src = "data:image/png;base64," + icon.pngBase64;
                    initial.replaceWith(image);
                } catch (error) { button.title = "Icon unavailable: " + error.message; }
                if (!visible()) return;
            }
            byId("status").textContent = used ? "Your most-used apps, ranked on this phone." : result.usageAvailable ? "No recent usage yet. Your apps are ready." : "Allow app history to rank your favourites.";
            lastLoaded = Date.now();
        } catch (error) { byId("status").textContent = "App list failed: " + error.message; }
        finally { busy = false; }
    }
    async function stopFlash() {
        flashing = false; generation++;
        byId("flash").textContent = "ϟ Flash attack";
        if (!torchOwned) return;
        await dikciz.command("torchControl", {action:"off"});
        torchOwned = false;
    }
    byId("flash").addEventListener("click", async () => {
        if (flashing) {
            try { await stopFlash(); status("Torch off requested."); }
            catch (error) { status("Could not switch torch off: " + error.message); }
            return;
        }
        flashing = true;
        const token = ++generation;
        byId("flash").textContent = "Stop flashing";
        status("Requesting torch pulses. Tap again to stop.");
        try {
            for (let pulse = 0; pulse < PULSE_COUNT && flashing && generation === token && visible(); pulse++) {
                await dikciz.command("torchControl", {action:"on", durationMilliseconds:PULSE_MS, autoRequestPermissions:true});
                torchOwned = true;
                await delay(PULSE_MS);
                await dikciz.command("torchControl", {action:"off"});
                torchOwned = false;
                await delay(PULSE_MS);
            }
            if (generation === token) status("Torch pulse requests finished.");
        } catch (error) { status("Flash stopped: " + error.message); }
        finally {
            if (generation === token) {
                try { await stopFlash(); }
                catch (error) { status("Torch cleanup failed: " + error.message); }
            }
        }
    });
    byId("speak").addEventListener("click", async () => {
        byId("speak").disabled = true;
        try {
            status("Speaking through your phone speaker.");
            await dikciz.command("speechSpeak", {text:"It's Dikciz time, bitch!", timeoutMilliseconds:15000});
            status("Dikciz time.");
        } catch (error) { status("Speech failed: " + error.message); }
        finally { byId("speak").disabled = false; }
    });
    byId("access").addEventListener("click", async () => {
        try {
            await dikciz.command("requestDeviceAccess", {requirementId:"usageAccess"});
            lastLoaded = 0;
            byId("status").textContent = "Allow Usage access in Android, then return here.";
        } catch (error) { byId("status").textContent = "Access request failed: " + error.message; }
    });
    function visibilityChanged() {
        if (visible()) { refresh(); return; }
        stopFlash().catch(error => { status("Torch cleanup failed: " + error.message); });
    }
    window.addEventListener("dikciz-ready", () => { ready = true; refresh(); });
    window.addEventListener("dikciz-visibility", visibilityChanged);
    document.addEventListener("visibilitychange", visibilityChanged);
    window.addEventListener("pagehide", visibilityChanged);
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
