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
    const iconCache = new Map();
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
            const label = document.createElement("span");
            label.textContent = app.label;
            const icon = document.createElement("img");
            icon.width = 40;
            icon.height = 40;
            icon.alt = "";
            icon.hidden = true;
            button.append(icon, label);
            if (!iconCache.has(app.component)) {
                iconCache.set(app.component, dikciz.command("appIcon", {component:app.component, sizePixels:64}));
            }
            iconCache.get(app.component).then(result => {
                if (!button.isConnected) return;
                icon.src = "data:image/png;base64," + result.pngBase64;
                icon.hidden = false;
            }).catch(error => {
                iconCache.delete(app.component);
                button.title = "Icon unavailable: " + error.message;
            });
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
        status.textContent = matches.length ? "Showing " + Math.min(matches.length, MAX_VISIBLE) + " of " + matches.length + ". Tap an app to launch it. Drawer shortcuts are separate home-screen widgets." : "No matching installed apps.";
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
