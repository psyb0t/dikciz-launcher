(() => {
    const REFRESH_MS = 60000;
    const MAX_EVENTS = 3;
    const AGENDA_DAYS = 7;
    const DAY_MS = 86400000;
    const PAGE_SIZE = 50;
    const MAX_PAGES = 10;
    const status = document.getElementById("status");
    let ready = false;
    let busy = false;
    const visible = () => ready && selfWidget.visible && !document.hidden;
    function upcoming(item) {
        if (!Number.isFinite(item.begin) || !Number.isFinite(item.end)) return false;
        if (!item.allDay) return item.end > Date.now();
        const now = new Date();
        const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
        return item.end > today && item.begin < today + AGENDA_DAYS * DAY_MS;
    }
    async function refresh() {
        if (!visible() || busy) return;
        busy = true;
        document.getElementById("refresh").disabled = true;
        document.getElementById("events").replaceChildren();
        try {
            const state = await dikciz.command("deviceState");
            document.getElementById("alarm").textContent = state.nextAlarmAtMilliseconds
                ? "Next alarm: " + new Date(state.nextAlarmAtMilliseconds).toLocaleString()
                : "No next alarm reported by Android.";
        } catch (error) {
            document.getElementById("alarm").textContent = "Alarm unavailable: " + error.message;
        }
        try {
            const access = await dikciz.command("nativeDataStatus");
            const granted = access.resources.some(resource => resource.resource === "calendarInstances" && resource.readGranted);
            document.getElementById("access").hidden = granted;
            if (!granted) {
                status.textContent = "Allow calendar access to show the next seven days. Your calendar stays on this phone.";
                return;
            }
            const events = [];
            let offset = 0;
            let more = false;
            for (let page = 0; page < MAX_PAGES; page++) {
                const result = await dikciz.command("contentQuery", {
                    resource:"calendarInstances", limit:PAGE_SIZE, offset,
                    columns:["event_id", "title", "begin", "end", "allDay", "eventLocation"],
                    filter:{visible:1},
                });
                events.push(...result.items.filter(upcoming));
                more = result.hasMore;
                if (events.length >= MAX_EVENTS || !more || !result.items.length || !visible()) break;
                offset += result.items.length;
            }
            for (const event of events.slice(0, MAX_EVENTS)) {
                const item = document.createElement("li");
                const title = document.createElement("strong");
                title.textContent = event.title || "Untitled event";
                const when = document.createElement("p");
                when.className = "muted";
                when.textContent = event.allDay
                    ? new Date(event.begin).toLocaleDateString([], {timeZone:"UTC", weekday:"short", month:"short", day:"numeric"}) + ", all day"
                    : new Date(event.begin).toLocaleString([], {weekday:"short", month:"short", day:"numeric", hour:"2-digit", minute:"2-digit"});
                item.append(title, when);
                document.getElementById("events").append(item);
            }
            status.textContent = events.length
                ? "Next events, seven-day window. Updated " + new Date().toLocaleTimeString() + (more || events.length > MAX_EVENTS ? ". Open calendar for more." : ".")
                : more ? "Calendar preview limit reached. Open calendar for all events." : "No upcoming events in the next seven days.";
        } catch (error) {
            status.textContent = "Calendar unavailable: " + error.message + ". Try Refresh agenda.";
        } finally {
            busy = false;
            document.getElementById("refresh").disabled = false;
        }
    }
    document.getElementById("access").addEventListener("click", async () => {
        try {
            await dikciz.command("requestDeviceAccess", {requirementId:"calendar"});
            status.textContent = "Review calendar access on your phone, then tap Refresh agenda.";
        } catch (error) { status.textContent = "Permission request failed: " + error.message; }
    });
    async function open(action, data) {
        try {
            const result = await dikciz.command("intent", {intentType:"activity", action, ...(data ? {data} : {})});
            const launch = result.activityLaunches?.[0];
            status.textContent = launch?.verified ? "App opened." : "Open requested. " + (launch?.reason || "Check Android.");
        } catch (error) { status.textContent = "Could not open app: " + error.message; }
    }
    document.getElementById("calendar").addEventListener("click", () => open("android.intent.action.VIEW", "content://com.android.calendar/time/" + Date.now()));
    document.getElementById("alarms").addEventListener("click", () => open("android.intent.action.SHOW_ALARMS"));
    document.getElementById("refresh").addEventListener("click", refresh);
    window.addEventListener("dikciz-ready", () => { ready = true; refresh(); });
    window.addEventListener("dikciz-visibility", refresh);
    document.addEventListener("visibilitychange", refresh);
    setInterval(refresh, REFRESH_MS);
})();
