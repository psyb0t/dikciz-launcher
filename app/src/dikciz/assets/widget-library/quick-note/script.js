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
