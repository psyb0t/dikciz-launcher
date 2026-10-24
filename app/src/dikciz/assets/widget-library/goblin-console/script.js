(() => {
    const WEATHER_INTERVAL_MS = 900000, FETCH_TIMEOUT_MS = 15000, MAX_RESPONSE_BYTES = 65536;
    const PULSE_MS = 250, PULSE_COUNT = 8, AUDIO_SECONDS = 2.4;
    const phrases = {peen:"Peen goes in vageen.", london:"God save the Queen! The goblin has arrived in London.", heat:"It's hotter than Satan's armpit. The goblin is being slow cooked!", speed:"Slow down! Your shit is about to come out of your arse!"};
    const element = id => document.getElementById(id);
    const visible = () => ready && selfWidget.visible && !document.hidden;
    const report = message => { element("status").textContent = message; };
    let ready = false, generation = 0, torchOwned = false, speechOwned = false, flashing = false;
    let audio = null, request = null, weatherBusy = false, state = {}, lastWeatherAttempt = 0;
    async function speak(text) {
        if (!visible() || speechOwned) return;
        const run = generation;
        speechOwned = true;
        report("Speaking at your current media volume...");
        try {
            await dikciz.command("speechSpeak", {text, language:"en-GB", timeoutMilliseconds:20000});
            if (run === generation) report("Spoken. Your phone has opinions.");
        } catch (error) { if (run === generation) report("Speech failed: " + error.message); }
        finally { speechOwned = false; }
    }
    async function stop() {
        generation++; request?.abort();
        const work = [];
        if (torchOwned) work.push(dikciz.command("torchControl", {action:"off"}).then(() => { torchOwned = false; }));
        if (speechOwned) work.push(dikciz.command("speechStop"));
        if (audio) { const current = audio; audio = null; work.push(current.close()); }
        const results = await Promise.allSettled(work);
        report(results.some(result => result.status === "rejected") ? "Stop requested. Some hardware cleanup failed; check permissions and logs." : "Stopped this widget's sounds and lights.");
    }
    async function flash() {
        if (!visible()) return;
        if (flashing) { await stop(); return; }
        flashing = true;
        const run = generation;
        report("Eight torch pulses. Tap Stop to end.");
        try {
            for (let pulse = 0; pulse < PULSE_COUNT && visible() && run === generation; pulse++) {
                torchOwned = true;
                await dikciz.command("torchControl", {action:"on", durationMilliseconds:PULSE_MS, autoRequestPermissions:true});
                if (run !== generation || !visible()) break;
                await new Promise(resolve => setTimeout(resolve, PULSE_MS * 2));
            }
            if (run === generation) report("Flash attack finished.");
        } catch (error) { if (run === generation) report("Torch unavailable: " + error.message + ". Grant access, then tap again."); }
        finally {
            flashing = false;
            if (torchOwned) {
                try { await dikciz.command("torchControl", {action:"off"}); torchOwned = false; }
                catch (error) { console.warn("goblin_torch_cleanup_failed", error.code || "unknown"); }
            }
        }
    }
    async function alien() {
        if (!visible()) return;
        if (audio) { await audio.close(); audio = null; }
        const current = new AudioContext(); audio = current;
        await current.resume();
        const oscillator = current.createOscillator(), gain = current.createGain(), start = current.currentTime;
        oscillator.type = "sine"; oscillator.connect(gain); gain.connect(current.destination);
        gain.gain.setValueAtTime(0, start); gain.gain.linearRampToValueAtTime(0.08, start + 0.03);
        gain.gain.setValueAtTime(0.08, start + AUDIO_SECONDS - 0.1); gain.gain.linearRampToValueAtTime(0, start + AUDIO_SECONDS);
        for (let step = 0; step < 24; step++) oscillator.frequency.setValueAtTime(180 + ((step * 173) % 1100), start + step / 10);
        oscillator.onended = () => {
            if (audio !== current) return;
            audio = null;
            current.close().catch(error => console.warn("goblin_audio_cleanup_failed", error.name));
            report("Alien transmission complete.");
        };
        oscillator.start(start); oscillator.stop(start + AUDIO_SECONDS);
        report("Receiving deeply questionable signals for 2.4 seconds...");
    }
    function renderWeather() {
        element("watch").checked = state.watchWeather === true;
        if (!Number.isFinite(state.temperatureC)) return;
        element("temperature").textContent = state.temperatureC.toFixed(1) + "°C";
        element("weather-time").textContent = "Saved outdoor weather, checked " + new Date(state.fetchedAt).toLocaleString();
    }
    async function weather(askForAccess = false) {
        if (!visible() || weatherBusy) return;
        weatherBusy = true; lastWeatherAttempt = Date.now();
        const run = generation;
        report("Getting your location and outdoor weather...");
        try {
            const location = await dikciz.command("locationGet", {maxAgeMilliseconds:300000, timeoutMilliseconds:10000, autoRequestPermissions:askForAccess});
            if (!location.available) throw new Error("Location unavailable. Allow location, then tap Check outside again.");
            if (!visible() || run !== generation) return;
            if (!Number.isFinite(location.latitude) || Math.abs(location.latitude) > 90 || !Number.isFinite(location.longitude) || Math.abs(location.longitude) > 180) throw new Error("Invalid phone coordinates");
            const controller = new AbortController(); request = controller;
            const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
            let data;
            try {
                const url = "https://api.open-meteo.com/v1/forecast?latitude=" + encodeURIComponent(location.latitude) + "&longitude=" + encodeURIComponent(location.longitude) + "&current=temperature_2m&temperature_unit=celsius&timeformat=unixtime&forecast_days=1";
                const response = await fetch(url, {signal:controller.signal, credentials:"omit", referrerPolicy:"no-referrer"});
                if (!response.ok) throw new Error("Weather HTTP " + response.status);
                const reader = response.body.getReader(), chunks = [];
                let size = 0;
                while (true) {
                    const part = await reader.read(); if (part.done) break;
                    size += part.value.byteLength;
                    if (size > MAX_RESPONSE_BYTES) { await reader.cancel(); throw new Error("Weather response too large"); }
                    chunks.push(part.value);
                }
                const bytes = new Uint8Array(size); let offset = 0;
                for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
                data = JSON.parse(new TextDecoder().decode(bytes));
            } finally { clearTimeout(timer); if (request === controller) request = null; }
            const temperatureC = data.current?.temperature_2m, observedAtMilliseconds = data.current?.time * 1000, now = Date.now();
            if (!Number.isFinite(temperatureC) || temperatureC < -90 || temperatureC > 65 || !Number.isFinite(observedAtMilliseconds) || now - observedAtMilliseconds > 3600000 || observedAtMilliseconds > now + 60000) throw new Error("Weather reading is missing, invalid or stale");
            if (!visible() || run !== generation) return;
            state = {...state, temperatureC, observedAtMilliseconds, fetchedAt:now};
            await selfWidget.patchState(state);
            if (!visible() || run !== generation) return;
            renderWeather();
            await dikciz.emitEvent("goblin.weather", {temperatureC, observedAtMilliseconds});
            report("Outdoor weather updated. Goblin heat reacts only if you enabled its Lua script.");
        } catch (error) { if (run === generation) report("Weather failed: " + error.message); }
        finally { weatherBusy = false; }
    }
    Object.entries(phrases).forEach(([id, phrase]) => element(id).addEventListener("click", () => speak(phrase)));
    element("flash").addEventListener("click", () => flash().catch(error => report("Flash failed: " + error.message)));
    element("alien").addEventListener("click", () => alien().catch(error => report("Audio failed: " + error.message)));
    element("stop").addEventListener("click", () => {
        state = {...state, watchWeather:false}; element("watch").checked = false;
        Promise.allSettled([stop(), selfWidget.patchState(state)]).then(results => {
            if (results.some(result => result.status === "rejected")) report("Stop requested, but state could not be saved.");
        });
    });
    element("weather").addEventListener("click", () => weather(true));
    element("watch").addEventListener("change", async () => {
        state = {...state, watchWeather:element("watch").checked};
        try { await selfWidget.patchState(state); if (state.watchWeather) await weather(true); else report("Weather watch disabled."); }
        catch (error) { report("Could not save weather watch: " + error.message); }
    });
    window.addEventListener("dikciz-ready", () => { ready = true; state = {...selfWidget.state}; renderWeather(); });
    window.addEventListener("dikciz-state", () => { state = {...selfWidget.state}; renderWeather(); });
    const visibilityChanged = () => {
        document.body.dataset.visible = visible() ? "true" : "false";
        if (!visible()) stop().catch(error => console.warn("goblin_hidden_cleanup_failed", error.name));
        else if (state.watchWeather && Date.now() - lastWeatherAttempt >= WEATHER_INTERVAL_MS) weather();
    };
    window.addEventListener("dikciz-visibility", visibilityChanged);
    document.addEventListener("visibilitychange", visibilityChanged);
    window.addEventListener("pagehide", visibilityChanged);
    setInterval(() => { if (visible() && state.watchWeather && Date.now() - lastWeatherAttempt >= WEATHER_INTERVAL_MS) weather(); }, 60000);
})();
