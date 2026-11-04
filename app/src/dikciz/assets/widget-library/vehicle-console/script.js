(() => {
    const POLL_MS = 5000, TIMEOUT_MS = 5000, MAX_RESPONSE = 65536;
    const SERVICE_UUID = "00001101-0000-1000-8000-00805f9b34fb";
    const node = id => document.getElementById(id);
    const report = text => { node("status").textContent = text; };
    let ready = false, id = null, busy = false, generation = 0, polling = false;
    const visible = () => ready && selfWidget.visible && !document.hidden;
    function render() {
        const state = selfWidget.state || {};
        node("rpm").textContent = Number.isFinite(state.rpm) ? Math.round(state.rpm) : "...";
        node("coolant").textContent = Number.isFinite(state.coolant) ? Math.round(state.coolant) + "°" : "...";
        node("voltage").textContent = Number.isFinite(state.voltage) ? state.voltage.toFixed(1) + "V" : "...";
        node("stop").disabled = !id;
        node("connect").disabled = !!id || busy;
    }
    async function close() {
        generation++;
        const current = id; id = null; render();
        if (current) await dikciz.command("deviceConnectionClose", {id:current});
        report("Disconnected. Saved readings keep their original time.");
    }
    async function adapters() {
        if (!ready || busy) return;
        busy = true; render();
        try {
            const response = await dikciz.command("bluetoothDevices", {autoRequestPermissions:true});
            node("adapter").replaceChildren(new Option("Choose an adapter", ""));
            for (const device of response.items || []) {
                node("adapter").add(new Option(device.name || device.address, device.address));
            }
            node("adapter").value = selfWidget.state?.address || "";
            report(response.items?.length ? "Choose your paired ELM327 adapter." : "No paired devices. Pair the adapter in Android Bluetooth settings first.");
        } catch (error) { report("Bluetooth access: " + error.message); }
        finally { busy = false; render(); }
    }
    async function command(text, run) {
        let result = "", first = true;
        while (result.length < MAX_RESPONSE) {
            if (run !== generation || !id || !visible()) throw new Error("Reading stopped");
            const fields = {id, readBytes:1024, timeoutMilliseconds:TIMEOUT_MS};
            if (first) fields.dataBase64 = btoa(text + "\r");
            const response = await dikciz.command("deviceConnectionExchange", fields);
            first = false;
            result += atob(response.dataBase64 || "");
            const prompt = result.indexOf(">");
            if (prompt >= 0) return result.slice(0, prompt);
            if (!response.bytesRead) throw new Error("Adapter returned no complete response");
        }
        throw new Error("Adapter response exceeds the read bound");
    }
    function pid(text, code, bytes) {
        const matches = [...text.toUpperCase().replace(/\s/g, "").matchAll(new RegExp("41" + code + "([0-9A-F]{" + (bytes * 2) + "})", "g"))];
        if (!matches.length) return null;
        const values = [...new Set(matches.map(match => parseInt(match[1], 16)))];
        if (values.length !== 1) throw new Error("Multiple ECUs returned different readings");
        return values[0];
    }
    async function poll(run) {
        if (!visible() || !id || busy || polling) return;
        polling = true;
        try {
            const rpm = pid(await command("010C", run), "0C", 2);
            const temperature = pid(await command("0105", run), "05", 1);
            const voltage = (await command("ATRV", run)).match(/([0-9]+(?:\.[0-9]+)?)\s*V/i);
            if (run !== generation) return;
            const state = {address:node("adapter").value, rpm:rpm === null ? null : rpm / 4,
                coolant:temperature === null ? null : temperature - 40, voltage:voltage ? Number(voltage[1]) : null, fetchedAt:Date.now()};
            await selfWidget.patchState(state);
            render();
            report("Read " + new Date(state.fetchedAt).toLocaleTimeString() + ". Missing values mean the adapter or vehicle did not supply them.");
        } catch (error) {
            report("Read stopped: " + error.message);
            try { await close(); } catch (cleanup) { console.warn("vehicle_close_failed", cleanup.code || cleanup.name); }
            report("Read stopped: " + error.message);
        } finally { polling = false; }
    }
    node("connect").addEventListener("click", async () => {
        if (!visible() || busy || id) return;
        const address = node("adapter").value;
        if (!address) { report("Choose a paired adapter first."); return; }
        busy = true; render(); const run = ++generation;
        try {
            const result = await dikciz.command("deviceConnectionOpen", {kind:"rfcomm", address, serviceUuid:SERVICE_UUID,
                maximumDurationMilliseconds:3600000, autoRequestPermissions:true});
            id = result.id;
            if (run !== generation || !visible()) { await close(); return; }
            for (const setup of ["ATZ", "ATE0", "ATL0", "ATS1"]) {
                const reply = await command(setup, run);
                if (/\?|ERROR|UNABLE TO CONNECT|STOPPED/i.test(reply)) throw new Error("Adapter rejected initialization");
            }
            report("Adapter ready. Reading live data.");
        } catch (error) {
            try { await close(); } catch (cleanup) { console.warn("vehicle_close_failed", cleanup.code || cleanup.name); }
            report("Could not connect: " + error.message);
        } finally { busy = false; render(); if (id) poll(generation).catch(error => report(error.message)); }
    });
    node("refresh").addEventListener("click", () => adapters().catch(error => report(error.message)));
    node("stop").addEventListener("click", () => close().catch(error => report("Disconnect failed: " + error.message)));
    window.addEventListener("dikciz-ready", () => { ready = true; render(); });
    window.addEventListener("dikciz-state", render);
    const visibility = () => { if (!visible() && id) close().catch(error => console.warn("vehicle_hidden_cleanup_failed", error.code || error.name)); };
    window.addEventListener("dikciz-visibility", visibility);
    document.addEventListener("visibilitychange", visibility);
    window.addEventListener("pagehide", visibility);
    setInterval(() => { if (visible() && id) poll(generation).catch(error => report(error.message)); }, POLL_MS);
})();
