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
