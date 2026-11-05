(() => {
    const byId = id => document.getElementById(id);
    let state = {tasks: [], watching: false};
    let busy = false;
    function render() {
        byId("tasks").replaceChildren();
        for (const task of state.tasks) {
            const item = document.createElement("li");
            item.className = task.done ? "done" : "";
            const label = document.createElement("label");
            const checkbox = document.createElement("input");
            checkbox.type = "checkbox"; checkbox.checked = task.done; checkbox.disabled = busy;
            const text = document.createElement("span"); text.textContent = task.title;
            checkbox.addEventListener("change", () => void run("todo.toggle", {id: task.id}));
            label.append(checkbox, text); item.append(label); byId("tasks").append(item);
        }
        byId("count").textContent = state.tasks.filter(task => task.done).length + " / " + state.tasks.length;
        byId("watch").textContent = state.watching ? "Stop watching" : "Watch New York";
        byId("watch").disabled = busy;
        byId("rule").textContent = state.watching ? "GPS rule active. You can close this app." : "Location rule is off";
        if (state.lastAction) byId("status").textContent = state.lastAction;
    }
    async function run(event, payload = {}) {
        if (busy) return;
        busy = true; render();
        try {
            const reply = await dikciz.command("customAppEvent", {event, payload});
            state = reply.state;
            return true;
        } catch (error) {
            state.lastAction = (error.code || "todo_failed") + ": " + error.message;
            return false;
        } finally { busy = false; render(); }
    }
    byId("watch").addEventListener("click", () => void run(state.watching ? "todo.stop" : "todo.watch"));
    byId("add").addEventListener("submit", event => {
        event.preventDefault(); const title = byId("title").value;
        void run("todo.add", {title}).then(saved => { if (saved) byId("title").value = ""; });
    });
    byId("access").addEventListener("click", () => {
        void dikciz.command("requestDeviceAccess", {requirementId: "locationForeground"})
            .then(reply => { byId("status").textContent = reply.instructions || "Review location access in Android."; })
            .catch(error => { byId("status").textContent = error.message; });
    });
    window.addEventListener("dikciz-state", event => { state = event.detail; render(); });
    window.addEventListener("dikciz-ready", () => void run("todo.open"));
})();
