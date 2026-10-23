(() => {
    const MINUTE_MS = 60000;
    function tick() {
        if (document.hidden || window.selfWidget?.visible === false) return;
        document.getElementById("clock").textContent = new Date().toLocaleTimeString([], {hour:"2-digit", minute:"2-digit"});
        document.getElementById("date").textContent = new Date().toLocaleDateString([], {weekday:"long", month:"short", day:"numeric"});
    }
    tick();
    setInterval(tick, MINUTE_MS);
    window.addEventListener("dikciz-visibility", tick);
    document.addEventListener("visibilitychange", tick);
})();
(() => {
    const REQUEST_TIMEOUT_MS = 15000;
    const MAX_RESPONSE_BYTES = 65536;
    const CITY_LIMIT = 5;
    const LOCATION_MAX_AGE_MS = 300000;
    const WEATHER_MAX_AGE_MS = 15 * 60 * 1000;
    const LOCATION_TIMEOUT_MS = 10000;
    const locateButton = document.getElementById("locate");
    const status = document.getElementById("status");
    const choices = document.getElementById("choices");
    const refreshButton = document.getElementById("refresh");
    const findButton = document.getElementById("find");
    let state = {};
    let busy = false;
    let ready = false;
    const text = (id, value) => { const node = document.getElementById(id); if (node) node.textContent = value; };
    const number = (value, suffix) => Number.isFinite(value) ? Math.round(value) + suffix : "Unavailable";
    const validCity = city => city && typeof city.name === "string" && city.name.length <= 120 && Number.isFinite(city.latitude) && Math.abs(city.latitude) <= 90 && Number.isFinite(city.longitude) && Math.abs(city.longitude) <= 180;
    function setBusy(value) {
        busy = value;
        findButton.disabled = value;
        locateButton.disabled = value;
        refreshButton.disabled = value || !validCity(state.city);
        choices?.querySelectorAll("button").forEach(button => { button.disabled = value; });
    }
    async function request(url) {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
        try {
            const response = await fetch(url, {signal:controller.signal, credentials:"omit", referrerPolicy:"no-referrer"});
            if (!response.ok) throw new Error("Service returned HTTP " + response.status);
            const reader = response.body.getReader();
            const chunks = [];
            let length = 0;
            while (true) {
                const chunk = await reader.read();
                if (chunk.done) break;
                length += chunk.value.byteLength;
                if (length > MAX_RESPONSE_BYTES) {
                    await reader.cancel();
                    throw new Error("Service response is too large.");
                }
                chunks.push(chunk.value);
            }
            const bytes = new Uint8Array(length);
            let offset = 0;
            for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
            const result = JSON.parse(new TextDecoder().decode(bytes));
            if (!result || typeof result !== "object" || Array.isArray(result) || result.error) throw new Error("Invalid service response.");
            return result;
        } finally { clearTimeout(timeout); }
    }
    function render() {
        text("place", validCity(state.city) ? state.city.name : "Choose your city");
        const weather = state.weather || {};
        text("temperature", number(weather.temperature, "°C"));
        text("feels", number(weather.feels, "°C"));
        text("wind", number(weather.wind, ""));
        text("rain", number(weather.rain, "%"));
        text("range", Number.isFinite(weather.low) && Number.isFinite(weather.high) ? Math.round(weather.low) + " / " + Math.round(weather.high) + "°" : "Unavailable");
        text("aqi", number(state.air?.aqi, ""));
        text("weather-status", weather.fetchedAt ? "Saved weather, fetched " + new Date(weather.fetchedAt).toLocaleString() + ". Refresh for current data." : "Weather has not been loaded.");
        text("air-status", state.air?.fetchedAt ? "Saved air data, fetched " + new Date(state.air.fetchedAt).toLocaleString() + ". Lower AQI means cleaner air." : "Air quality has not been loaded.");
        setBusy(busy);
    }
    async function refresh() {
        if (busy || !selfWidget.visible || !validCity(state.city)) return;
        setBusy(true);
        status.textContent = "Fetching weather and air quality...";
        const city = {...state.city};
        let weather = state.weather || {};
        let air = state.air || {};
        const location = "latitude=" + encodeURIComponent(city.latitude) + "&longitude=" + encodeURIComponent(city.longitude);
        try {
            const results = await Promise.allSettled([
                request("https://api.open-meteo.com/v1/forecast?" + location + "&current=temperature_2m,apparent_temperature,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=1&timezone=auto"),
                request("https://air-quality-api.open-meteo.com/v1/air-quality?" + location + "&current=european_aqi&forecast_days=1")
            ]);
            const errors = [];
            if (results[0].status === "fulfilled" && Number.isFinite(results[0].value.current?.temperature_2m)) {
                const data = results[0].value;
                weather = {temperature:data.current.temperature_2m, feels:data.current.apparent_temperature, wind:data.current.wind_speed_10m, high:data.daily?.temperature_2m_max?.[0], low:data.daily?.temperature_2m_min?.[0], rain:data.daily?.precipitation_probability_max?.[0], fetchedAt:Date.now()};
            } else { errors.push("Weather unavailable"); }
            if (results[1].status === "fulfilled" && Number.isFinite(results[1].value.current?.european_aqi)) {
                air = {aqi:results[1].value.current.european_aqi, fetchedAt:Date.now()};
            } else { errors.push("Air quality unavailable"); }
            const nextState = {city, weather, air};
            await selfWidget.patchState(nextState);
            state = nextState;
            render();
            status.textContent = errors.length ? errors.join(". ") + ". Saved readings are shown when available. Try Refresh weather." : "Saved latest readings. Refresh when needed.";
        } catch (error) { status.textContent = "Could not save readings: " + error.message; }
        finally { setBusy(false); }
    }
    document.getElementById("city-search")?.addEventListener("submit", async event => {
        event.preventDefault();
        if (busy) return;
        const query = document.getElementById("city").value.trim();
        if (query.length < 2) { status.textContent = "Enter at least two characters."; return; }
        setBusy(true);
        choices.replaceChildren();
        status.textContent = "Searching cities...";
        try {
            const data = await request("https://geocoding-api.open-meteo.com/v1/search?name=" + encodeURIComponent(query) + "&count=" + CITY_LIMIT + "&language=en&format=json");
            const cities = Array.isArray(data.results) ? data.results.slice(0, CITY_LIMIT) : [];
            for (const result of cities) {
                const city = {name:[result.name, result.admin1, result.country].filter(value => typeof value === "string").join(", ").slice(0,120), latitude:result.latitude, longitude:result.longitude};
                if (!validCity(city)) continue;
                const button = document.createElement("button");
                button.type = "button";
                button.className = "secondary";
                button.textContent = city.name;
                button.addEventListener("click", async () => {
                    if (busy) return;
                    setBusy(true);
                    try {
                        await selfWidget.patchState({city, weather:{}, air:{}});
                        state = {city};
                        choices.replaceChildren();
                        render();
                    } catch (error) {
                        status.textContent = "Could not save city: " + error.message;
                        setBusy(false);
                        return;
                    }
                    setBusy(false);
                    await refresh();
                });
                choices.append(button);
            }
            status.textContent = choices.children.length ? "Choose the matching city." : "No matching cities. Try another name.";
        } catch (error) { status.textContent = "City search unavailable. Check your connection and retry."; }
        finally { setBusy(false); }
    });
    async function locate(askForAccess = false) {
        if (busy || !selfWidget.visible) return;
        setBusy(true);
        status.textContent = "Finding your phone location...";
        try {
            const location = await dikciz.command("locationGet", {
                maxAgeMilliseconds:LOCATION_MAX_AGE_MS, timeoutMilliseconds:LOCATION_TIMEOUT_MS,
                autoRequestPermissions:askForAccess
            });
            if (!location.available) {
                status.textContent = "Location unavailable: " + location.reason + ". Try Use my location again or choose a city.";
                return;
            }
            const city = {
                name:(location.placeName || "Current location (" + location.latitude.toFixed(2) + ", " + location.longitude.toFixed(2) + ")").slice(0,120),
                latitude:location.latitude, longitude:location.longitude, source:"device", locatedAt:Date.now()
            };
            if (!validCity(city)) throw new Error("Phone returned invalid coordinates.");
            await selfWidget.patchState({city, weather:{}, air:{}});
            state = {city};
            choices.replaceChildren();
            render();
        } catch (error) {
            status.textContent = error.permissionRequest || error.code?.startsWith("native_permission_")
                ? "Allow location with Use my location, or choose a city under Weather details and city."
                : "Location unavailable. Choose a city under Weather details and city, or try Use my location again.";
            return;
        } finally { setBusy(false); }
        await refresh();
    }
    locateButton.addEventListener("click", () => locate(true));
    refreshButton?.addEventListener("click", refresh);
    function activate() {
        if (!ready || !window.selfWidget?.visible || busy) return;
        if (!validCity(state.city) || (state.city.source === "device" && Date.now() - (state.city.locatedAt || 0) > LOCATION_MAX_AGE_MS)) {
            locate().catch(error => { status.textContent = "Location failed: " + error.message; });
            return;
        }
        status.textContent = "Saved city and readings. Tap Refresh weather to update.";
        if (!state.weather?.fetchedAt || Date.now() - state.weather.fetchedAt > WEATHER_MAX_AGE_MS) {
            refresh().catch(error => { status.textContent = "Weather refresh failed: " + error.message; });
        }
    }
    window.addEventListener("dikciz-ready", () => {
        state = selfWidget.state || {};
        ready = true;
        render();
        activate();
    });
    window.addEventListener("dikciz-visibility", event => {
        if (event.detail.visible && window.selfWidget) activate();
    });
    window.addEventListener("dikciz-state", event => {
        state = event.detail || {};
        render();
    });
})();
