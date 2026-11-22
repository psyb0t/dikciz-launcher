(() => {
    "use strict";

    const byId = id => document.getElementById(id);
    const fields = ["address", "threshold", "armHours", "countdownSeconds", "braking", "speed", "call", "photos", "whatsapp", "whatsappName", "storageName"];
    let state = {};
    let draftDirty = false;
    let busy = false;

    function render(next) {
        state = next || {};
        const run = state.incident;
        const active = run && ["warning", "alert"].includes(run.phase);
        const options = state.settings;
        if (options && !draftDirty) {
            for (const field of fields) {
                const input = byId(field);
                if (input.type === "checkbox") input.checked = options[field];
                else input.value = options[field] ?? input.defaultValue;
            }
            const sim = options.subscriptionId;
            if (sim != null && !Array.from(byId("sim").options).some(item => item.value === String(sim))) {
                byId("sim").add(new Option("Saved SIM " + sim, String(sim)));
            }
            byId("sim").value = sim == null ? "" : String(sim);
        }
        if (!byId("photos").checked) byId("whatsapp").checked = false;
        byId("whatsapp").disabled = !byId("photos").checked;
        byId("whatsapp-options").hidden = !byId("whatsapp").checked;
        byId("options").disabled = busy || Boolean(state.armed || active);
        const monitorMessages = {
            sensor_feed_stalled: "Disarmed because the motion sensor stopped delivering readings.",
            monitor_unavailable: "Motion monitoring is unavailable. The app is disarmed.",
            expired: "Arming duration ended.",
        };
        byId("monitor-status").textContent = state.armed && state.monitorStatus === "live" ? "Live motion sensor confirmed."
            : monitorMessages[state.monitorStatus] || "";
        const hours = options?.armHours ?? Number(byId("armHours").defaultValue);
        byId("arm").textContent = state.armed ? "Disarm" : "Arm for " + hours + (hours === 1 ? " hour" : " hours");
        byId("arm").disabled = busy || !options || Boolean(active) || (draftDirty && !state.armed);
        byId("incident").hidden = !run;
        if (!run) return;
        byId("report").textContent = run.message;
        byId("close").hidden = !active;
        byId("close").textContent = run.phase === "warning" ? "Cancel alert and keep monitoring" : "Close alert and stop repeats";
        byId("receipt").hidden = !run.sms;
        const outcomes = ["Incident status: " + run.phase];
        for (const key of ["sms", "call", "speech", "failure"]) {
            if (run[key]) outcomes.push(key.toUpperCase() + ": " + run[key]);
        }
        for (const [facing, image] of Object.entries(run.photos || {})) {
            outcomes.push(facing + " camera: " + (image.path || image.state)
                + (image.shareError ? " (copy unavailable: " + image.shareError + ")" : ""));
        }
        const share = run.whatsapp;
        if (share) {
            for (const facing of share.sent) outcomes.push("WhatsApp: " + facing + " photo visible in chat");
            if (share.error) outcomes.push("WhatsApp stopped: " + share.error.replaceAll("_", " "));
            else if (!share.finished) outcomes.push("WhatsApp: " + share.stage);
        }
        byId("outcomes").replaceChildren(...outcomes.map(value => {
            const item = document.createElement("li");
            item.textContent = value;
            return item;
        }));
    }

    async function work(action) {
        if (busy) return;
        busy = true;
        render(state);
        try {
            await action();
        } catch (error) {
            byId("status").textContent = error.message;
        } finally {
            busy = false;
            render(state);
        }
    }

    async function invoke(event, payload = {}, interruptRunning = false) {
        const reply = await dikciz.command("customAppEvent", {event, payload, interruptRunning});
        render(reply.state);
        byId("status").textContent = reply.status || (state.armed ? "Armed" : "Disarmed");
        return reply.result || {};
    }

    byId("settings").addEventListener("input", () => {
        draftDirty = true;
        render(state);
    });
    byId("settings").addEventListener("submit", event => {
        event.preventDefault();
        void work(async () => {
            const result = await invoke("impact.save", {
                address: byId("address").value, threshold: byId("threshold").valueAsNumber,
                armHours: byId("armHours").valueAsNumber, countdownSeconds: byId("countdownSeconds").valueAsNumber,
                braking: byId("braking").checked, speed: byId("speed").checked, call: byId("call").checked, photos: byId("photos").checked,
                whatsapp: byId("whatsapp").checked, whatsappName: byId("whatsappName").value,
                storageName: byId("storageName").value,
                subscriptionId: byId("sim").value ? Number(byId("sim").value) : null,
            });
            if (result.error) throw new Error(result.error.message);
            draftDirty = false;
        });
    });
    byId("arm").addEventListener("click", () => void work(async () => {
        if (state.armed) await invoke("impact.disarm", {}, true);
        else await invoke("impact.arm", {confirm: true});
    }));
    byId("load-sims").addEventListener("click", () => void work(async () => {
        const page = await dikciz.command("simList", {autoRequestPermissions: true});
        const select = byId("sim");
        const selected = select.value;
        select.replaceChildren(new Option("Android default", ""));
        for (const row of page.items) {
            select.add(new Option(row.displayName || "SIM " + row.subscriptionId, String(row.subscriptionId)));
        }
        select.value = selected;
        byId("status").textContent = page.items.length ? "Choose an SMS SIM." : "Android reported no active SIMs.";
    }));
    byId("close").addEventListener("click", () => void work(() => {
        const run = state.incident;
        return invoke(run.phase === "warning" ? "impact.cancel" : "impact.close", {id: run.id}, true);
    }));
    byId("receipt").addEventListener("click", () => void work(() =>
        invoke("impact.receipt", {id: state.incident.id})));
    byId("preview").addEventListener("click", () => void work(async () => {
        const preview = await invoke("impact.preview");
        byId("preview-result").textContent = preview.message;
    }));
    byId("find").addEventListener("click", () => void work(async () => {
        const query = byId("lookup").value.trim();
        if (!query) throw new Error("Enter a contact name.");
        const page = await dikciz.command("contentQuery", {
            resource: "phones", columns: ["display_name", "data1"], query, limit: 20, autoRequestPermissions: true,
        });
        const choices = byId("choices");
        choices.replaceChildren();
        for (const row of page.items) {
            const button = document.createElement("button");
            button.type = "button";
            button.textContent = row.display_name + " · " + row.data1;
            button.addEventListener("click", () => {
                byId("address").value = row.data1;
                byId("whatsappName").value = row.display_name;
                draftDirty = true;
                choices.replaceChildren();
                render(state);
            });
            choices.append(button);
        }
        byId("status").textContent = page.items.length ? "Choose the exact number, then save." : "No matching numbers.";
    }));
    for (const button of document.querySelectorAll("[data-access]")) {
        button.addEventListener("click", () => void work(async () => {
            const reply = await dikciz.command("requestDeviceAccess", {requirementId: button.dataset.access});
            byId("status").textContent = reply.instructions || "Review access in Android.";
        }));
    }
    window.addEventListener("dikciz-state", event => render(event.detail));
    window.addEventListener("dikciz-ready", () => void work(() => invoke("impact.status")));
})();
