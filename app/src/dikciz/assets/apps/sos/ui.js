(() => {
    const SECOND = 1000;
    const MINUTE = 60;
    const ACTIVE = ["armed", "warning", "active"];
    const DEFAULT_MESSAGE = "I need help. Last known location: {location}. Accuracy: {accuracyMeters} m. Fix time: {timestamp}.";
    const byId = id => document.getElementById(id);
    let state = {};
    let contacts = [];
    let busy = false;
    let dirty = false;
    let pendingConfirmation = null;
    const say = text => { byId("status").textContent = text; };
    function isActive() { return ACTIVE.includes(state.run?.state); }
    async function command(type, fields = {}) { return dikciz.command(type, fields); }
    async function event(name, payload = {}) {
        const result = await command("customAppEvent", {event: name, payload, interruptRunning: name === "cancel" || name === "checkIn"});
        if (result.result?.error) throw Object.assign(new Error(result.result.error.message), {code: result.result.error.code});
        state = result.state;
        render();
        return result.result;
    }
    async function perform(action) {
        if (busy) return;
        busy = true; controls();
        try { await action(); }
        catch (error) { say((error.code ? error.code + ": " : "") + error.message); }
        finally { busy = false; controls(); }
    }
    function controls() {
        byId("plan-fields").disabled = busy || isActive();
        for (const id of ["preview", "arm", "trigger"]) byId(id).disabled = busy || !state.plan || (id !== "preview" && isActive());
        for (const id of ["check-in", "cancel"]) { byId(id).hidden = !isActive(); byId(id).disabled = busy; }
    }
    function contactList() {
        byId("contacts").replaceChildren();
        contacts.forEach((contact, index) => {
            const item = document.createElement("li");
            const text = document.createElement("span"); text.textContent = (contact.label || "Contact") + " · " + contact.address;
            const remove = document.createElement("button"); remove.type = "button"; remove.className = "secondary"; remove.textContent = "Remove";
            remove.disabled = isActive(); remove.addEventListener("click", () => { contacts.splice(index, 1); dirty = true; contactList(); });
            item.append(text, document.createElement("br"), remove); byId("contacts").append(item);
        });
    }
    function addContact(label, address) {
        const normalized = address.replace(/[() .-]/g, "");
        if (!/^[+]?[0-9]{3,32}$/.test(normalized)) throw new Error("Enter an exact phone number.");
        if (contacts.length >= 10 || contacts.some(contact => contact.address === normalized)) throw new Error("That number is already selected, or the ten-recipient limit is reached.");
        contacts.push({label: label.trim() || normalized, address: normalized}); dirty = true; contactList();
    }
    function countdown() {
        const run = state.run;
        if (!isActive()) { byId("countdown").textContent = run?.state === "needs_review" ? "Review before restarting." : "You're in control."; return; }
        const seconds = Math.max(0, Math.ceil((run.deadlineAt - Date.now()) / SECOND));
        byId("countdown").textContent = Math.floor(seconds / MINUTE) + ":" + String(seconds % MINUTE).padStart(2, "0");
    }
    function render() {
        byId("run-state").textContent = (state.run?.state || "Not armed").replaceAll("_", " ");
        byId("run-detail").textContent = isActive() ? "Cancel stops future submissions. Already submitted messages cannot be recalled." : "Save a plan, preview its exact text, then choose when to arm it.";
        if (!dirty) {
            const plan = state.plan || {};
            contacts = structuredClone(plan.contacts || []);
            byId("message").value = plan.message || DEFAULT_MESSAGE;
            byId("check-in-minutes").value = (plan.checkInSeconds || 3600) / MINUTE;
            byId("grace-seconds").value = plan.graceSeconds || 60;
            byId("update-minutes").value = (plan.updateSeconds || 600) / MINUTE;
            byId("maximum-updates").value = plan.maximumUpdates || 3;
        }
        contactList(); controls(); countdown();
        const receipts = (state.run?.receipts || []).slice(-10);
        byId("receipts-section").hidden = !receipts.length; byId("receipts").replaceChildren();
        receipts.forEach(receipt => {
            const item = document.createElement("li"); const button = document.createElement("button");
            button.className = "secondary"; button.textContent = "Inspect " + receipt.state;
            button.addEventListener("click", () => perform(async () => { const result = await command("smsStatus", {idempotencyKey: receipt.idempotencyKey}); say(JSON.stringify(result)); }));
            item.append(button); byId("receipts").append(item);
        });
    }
    function confirm(title, detail, action) {
        byId("confirmation-title").textContent = title; byId("confirmation-detail").textContent = detail;
        pendingConfirmation = action; byId("confirmation").showModal();
    }
    byId("confirmation-cancel").addEventListener("click", () => { pendingConfirmation = null; byId("confirmation").close(); });
    byId("confirmation-go").addEventListener("click", () => { const action = pendingConfirmation; pendingConfirmation = null; byId("confirmation").close(); if (action) void perform(action); });
    byId("plan").addEventListener("input", () => { dirty = true; });
    byId("add-contact").addEventListener("click", () => perform(async () => { addContact(byId("contact-label").value, byId("contact-number").value); byId("contact-number").value = ""; }));
    byId("search-contacts").addEventListener("click", () => perform(async () => {
        const query = byId("contact-query").value.trim();
        if (!query) throw new Error("Enter a contact name to search.");
        const result = await command("contentQuery", {resource: "phones", query, columns: ["display_name", "data1"], limit: 50, offset: 0, autoRequestPermissions: true});
        byId("contact-results").replaceChildren();
        for (const contact of result.items) {
            const item = document.createElement("li"); const button = document.createElement("button"); button.type = "button"; button.className = "secondary";
            button.textContent = contact.display_name + " · " + contact.data1;
            button.addEventListener("click", () => perform(async () => { addContact(contact.display_name, contact.data1); })); item.append(button); byId("contact-results").append(item);
        }
        say(result.hasMore ? "Showing the first 50 matches. Narrow the name to find another contact." : result.items.length + " contact number(s) found.");
    }));
    byId("plan").addEventListener("submit", eventObject => { eventObject.preventDefault(); void perform(async () => {
        const plan = {contacts, message: byId("message").value, checkInSeconds: Number(byId("check-in-minutes").value) * MINUTE,
            graceSeconds: Number(byId("grace-seconds").value), updateSeconds: Number(byId("update-minutes").value) * MINUTE, maximumUpdates: Number(byId("maximum-updates").value)};
        if (state.plan?.subscriptionId !== undefined) plan.subscriptionId = state.plan.subscriptionId;
        await event("plan.save", plan); dirty = false; render(); say("Plan saved. Nothing is armed.");
    }); });
    byId("preview").addEventListener("click", () => perform(async () => { const result = await event("plan.preview"); byId("preview-text").hidden = false; byId("preview-text").textContent = result.contacts.map(contact => contact.label + " · " + contact.address).join("\n") + "\n\n" + result.message; say("Preview only. No messages sent."); }));
    byId("arm").addEventListener("click", () => confirm("Arm missed check-in?", "If you miss your check-in, a cancellation countdown runs before SMS updates go to the exact contacts in this plan.", async () => { await event("arm", {confirm: true}); say("Check-in armed."); }));
    byId("trigger").addEventListener("click", () => confirm("Start SOS?", "The audible cancellation countdown starts now. Messages are submitted if you do not cancel it.", async () => { await event("trigger", {confirm: true}); say("SOS countdown requested."); }));
    byId("check-in").addEventListener("click", () => perform(async () => { await event("checkIn"); say("Check-in reset."); }));
    byId("cancel").addEventListener("click", () => perform(async () => { await event("cancel"); say("Future messages cancelled."); }));
    document.querySelectorAll("[data-access]").forEach(button => button.addEventListener("click", () => perform(async () => {
        const result = await command("requestDeviceAccess", {requirementId: button.dataset.access}); say(result.instructions || "Review the access request on your phone.");
    })));
    window.addEventListener("dikciz-state", eventObject => { state = eventObject.detail; render(); });
    window.addEventListener("dikciz-ready", () => { void perform(async () => { state = (await command("customAppState", {action: "read"})).state; render(); say("Ready. Review your plan before arming."); }); });
    setInterval(countdown, SECOND);
})();
