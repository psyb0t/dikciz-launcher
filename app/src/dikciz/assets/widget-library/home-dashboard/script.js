(() => {

    window.dikcizPresetRoots ||= new WeakSet();
    window.dikcizPresetWrite ||= (key, value) => selfWidget.item(key).patchState(value);

    const initialize = () => document.querySelectorAll('[data-dikciz-preset="command-deck"]').forEach(root => {
        if (window.dikcizPresetRoots.has(root)) return;
        window.dikcizPresetRoots.add(root);
        const stateKey = root.getAttribute("data-dikciz-item-id");
        const onReady = listener => {
            if (window.selfWidget && window.dikciz) listener();
            else window.addEventListener("dikciz-ready", listener, {once:true});
        };

    const MINUTE_MS = 60000;
    const status = root.querySelector('[data-role="status"]');
    let ready = false;
    function renderClock() {
        const now = new Date();
        const clock = root.querySelector('[data-role="clock"]');
        const date = root.querySelector('[data-role="date"]');
        if (clock) clock.textContent = now.toLocaleTimeString([], {hour:"2-digit", minute:"2-digit"});
        if (date) date.textContent = now.toLocaleDateString([], {weekday:"long", month:"long", day:"numeric"});
    }
    renderClock();
    const timer = setInterval(() => { if (!root.isConnected) { clearInterval(timer); return; } if (!document.hidden) renderClock(); }, MINUTE_MS);
    document.addEventListener("visibilitychange", () => { if (root.isConnected) renderClock(); });
    onReady( () => { ready = true; });
    root.querySelector('[data-role="search"]')?.addEventListener("submit", async event => {
        event.preventDefault();
        const query = root.querySelector('[data-role="query"]').value.trim();
        if (!query || !ready) return;
        try {
            const result = await dikciz.command("intent", {
                intentType:"activity", action:"android.intent.action.VIEW",
                data:"https://www.google.com/search?q=" + encodeURIComponent(query)
            });
            const launch = result.activityLaunches?.[0];
            status.textContent = launch?.verified ? "Browser opened." : "Browser requested. " + (launch?.reason || "Check Android for the result.");
        } catch (error) { status.textContent = "Search unavailable: " + error.message; }
    });

    });
    initialize();
    window.addEventListener("dikciz-block-appended", initialize);
})();
(() => {

    window.dikcizPresetRoots ||= new WeakSet();
    window.dikcizPresetWrite ||= (key, value) => selfWidget.item(key).patchState(value);

    const initialize = () => document.querySelectorAll('[data-dikciz-preset="quick-apps"]').forEach(root => {
        if (window.dikcizPresetRoots.has(root)) return;
        window.dikcizPresetRoots.add(root);
        const stateKey = root.getAttribute("data-dikciz-item-id");
        const onReady = listener => {
            if (window.selfWidget && window.dikciz) listener();
            else window.addEventListener("dikciz-ready", listener, {once:true});
        };

    const MAX_VISIBLE = 6;
    const status = root.querySelector('[data-role="status"]');
    const list = root.querySelector('[data-role="apps"]');
    let apps = [];
    let launching = false;
    function render() {
        if (!list) return;
        list.replaceChildren();
        const query = root.querySelector('[data-role="app-query"]')?.value.trim().toLocaleLowerCase() || "";
        const matches = apps.filter(app => (app.label + " " + app.packageName).toLocaleLowerCase().includes(query));
        matches.slice(0, MAX_VISIBLE).forEach(app => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "secondary";
            button.textContent = app.label;
            button.addEventListener("click", async () => {
                if (launching) return;
                launching = true;
                button.disabled = true;
                status.textContent = "Opening " + app.label + "...";
                try {
                    const result = await dikciz.command("appAction", {action:"launch", component:app.component});
                    const launch = result.activityLaunches?.[0];
                    status.textContent = launch?.verified ? app.label + " opened." : "Launch unverified: " + (launch?.reason || result.outcome || "Check Android.");
                } catch (error) { status.textContent = "Could not open app: " + error.message; }
                finally { launching = false; button.disabled = false; }
            });
            list.append(button);
        });
        status.textContent = matches.length ? "Showing " + Math.min(matches.length, MAX_VISIBLE) + " of " + matches.length + ". Use the drawer to add shortcuts." : "No matching installed apps.";
    }
    root.querySelector('[data-role="app-query"]')?.addEventListener("input", render);
    onReady( async () => {
        try {
            const result = await dikciz.command("appCatalogue");
            apps = Array.isArray(result.apps) ? result.apps : [];
            render();
        } catch (error) { status.textContent = "Apps unavailable: " + error.message; }
    });

    });
    initialize();
    window.addEventListener("dikciz-block-appended", initialize);
})();
(() => {

    window.dikcizPresetRoots ||= new WeakSet();
    window.dikcizPresetWrite ||= (key, value) => selfWidget.item(key).patchState(value);

    const initialize = () => document.querySelectorAll('[data-dikciz-preset="quick-note"]').forEach(root => {
        if (window.dikcizPresetRoots.has(root)) return;
        window.dikcizPresetRoots.add(root);
        const stateKey = root.getAttribute("data-dikciz-item-id");
        const onReady = listener => {
            if (window.selfWidget && window.dikciz) listener();
            else window.addEventListener("dikciz-ready", listener, {once:true});
        };

    const MAX_NOTE_LENGTH = 2000;
    const note = root.querySelector('[data-role="note"]');
    const button = root.querySelector('[data-role="save"]');
    const status = root.querySelector('[data-role="status"]');
    let dirty = false;
    function render(state) {
        if (note && !dirty) note.value = typeof state.text === "string" ? state.text.slice(0, MAX_NOTE_LENGTH) : "";
    }
    note?.addEventListener("input", () => { dirty = true; status.textContent = "Unsaved changes. Tap Save note."; });
    onReady( () => render((selfWidget.itemStates[stateKey] || {})));
    window.addEventListener("dikciz-item-state", event => render(event.detail?.[stateKey] || {}));
    button?.addEventListener("click", async () => {
        const text = note.value.slice(0, MAX_NOTE_LENGTH);
        button.disabled = true;
        try {
            await window.dikcizPresetWrite(stateKey, {text});
            dirty = note.value !== text;
            status.textContent = dirty ? "New changes are not saved yet." : "Note saved on this phone.";
        } catch (error) { status.textContent = "Note not saved: " + error.message; }
        finally { button.disabled = false; }
    });

    });
    initialize();
    window.addEventListener("dikciz-block-appended", initialize);
})();
