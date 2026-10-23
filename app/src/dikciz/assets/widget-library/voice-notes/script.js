(() => {
    const MAX_DURATION_MS = 300000;
    const POLL_MS = 1000;
    const SECONDS_PER_MINUTE = 60;
    const MAX_RECENT = 5;
    const status = document.getElementById("status");
    const recordButton = document.getElementById("record");
    const stopButton = document.getElementById("stop");
    let ready = false;
    let busy = false;
    let stopRequested = false;
    let session = null;
    let playback = null;
    let lastDuration = 0;
    function controls() {
        recordButton.disabled = !ready || busy || Boolean(session) || Boolean(selfWidget.state.pendingRecording);
        stopButton.disabled = !session;
        document.getElementById("retry").hidden = !selfWidget.state.pendingRecording;
        document.getElementById("stop-playback").hidden = !playback;
    }
    function releaseBusy() {
        busy = false;
        controls();
        if (stopRequested) {
            stopRequested = false;
            void stop();
        }
    }
    function render() {
        const list = document.getElementById("recordings");
        list.replaceChildren();
        for (const note of (Array.isArray(selfWidget.state.recordings) ? selfWidget.state.recordings : []).slice(0, MAX_RECENT)) {
            if (!note || typeof note.name !== "string" || !/^note-[a-f0-9-]+\.m4a$/.test(note.name)) continue;
            const item = document.createElement("li");
            const button = document.createElement("button");
            button.type = "button";
            button.textContent = "Play " + new Date(note.savedAt).toLocaleString();
            button.addEventListener("click", async () => {
                if (busy || session) return;
                busy = true;
                controls();
                try {
                    if (playback) await dikciz.command("audioPlayback", {action:"stop", sessionId:playback});
                    const result = await dikciz.command("audioPlayback", {action:"play", path:selfWidget.storagePath + "/" + note.name});
                    playback = result.sessionId;
                    status.textContent = "Playback requested.";
                } catch (error) { status.textContent = "Playback failed: " + error.message; }
                finally { releaseBusy(); }
            });
            item.append(button);
            list.append(item);
        }
        controls();
    }
    async function save(result) {
        const pending = selfWidget.state.pendingRecording;
        const name = pending?.name || "note-" + crypto.randomUUID() + ".m4a";
        await selfWidget.patchState({...selfWidget.state, sessionId:null, pendingRecording:{path:result.path, name}});
        await dikciz.command("fileMkdir", {path:selfWidget.storagePath, parents:true});
        const destination = selfWidget.storagePath + "/" + name;
        try {
            await dikciz.command("fileManage", {action:"move", path:result.path, destination});
        } catch (error) {
            // A previous move may have completed before its reply or state save was lost.
            if (error.code !== "file_not_found" && error.code !== "file_io_failed") throw error;
            await dikciz.command("fileManage", {action:"stat", path:destination});
        }
        const notes = (Array.isArray(selfWidget.state.recordings) ? selfWidget.state.recordings : []).filter(note => note && note.name !== name);
        notes.unshift({name, savedAt:Date.now(), durationMilliseconds:result.durationMilliseconds || lastDuration});
        await selfWidget.patchState({...selfWidget.state, sessionId:null, pendingRecording:null, recordings:notes});
        status.textContent = "Voice note saved on this phone.";
        render();
    }
    async function observe() {
        if (!session || busy) return;
        busy = true;
        try {
            const result = await dikciz.command("microphoneSession", {action:"status", sessionId:session});
            lastDuration = result.durationMilliseconds || 0;
            const seconds = Math.floor(lastDuration / POLL_MS);
            document.getElementById("elapsed").textContent = Math.floor(seconds / SECONDS_PER_MINUTE) + ":" + String(seconds % SECONDS_PER_MINUTE).padStart(2, "0");
            if (result.state === "recording") return;
            session = null;
            if (result.state === "completed" && result.path) await save(result);
            else {
                await selfWidget.patchState({...selfWidget.state, sessionId:null});
                status.textContent = "Recording ended: " + (result.error || result.reason || result.state);
            }
        } catch (error) {
            if (error.code === "microphone_session_not_found") {
                session = null;
                await selfWidget.patchState({...selfWidget.state, sessionId:null});
            }
            status.textContent = "Recording status needs attention: " + error.message;
        } finally { releaseBusy(); }
    }
    async function stop() {
        if (busy) {
            stopRequested = true;
            return;
        }
        if (!session) return;
        busy = true;
        controls();
        try {
            const result = await dikciz.command("microphoneSession", {action:"stop", sessionId:session});
            session = null;
            if (result.state === "completed" && result.path) await save(result);
            else {
                await selfWidget.patchState({...selfWidget.state, sessionId:null});
                status.textContent = "Recording was not saved: " + (result.error || result.reason || result.state);
            }
        } catch (error) { status.textContent = "Stop or save failed: " + error.message + ". Retry saving if available."; }
        finally { releaseBusy(); }
    }
    recordButton.addEventListener("click", async () => {
        if (busy || session || selfWidget.state.pendingRecording || !selfWidget.visible) return;
        busy = true;
        controls();
        try {
            if (playback) {
                await dikciz.command("audioPlayback", {action:"stop", sessionId:playback});
                playback = null;
            }
            const result = await dikciz.command("microphoneSession", {action:"start", maximumDurationMilliseconds:MAX_DURATION_MS, autoRequestPermissions:true});
            session = result.sessionId;
            await selfWidget.patchState({...selfWidget.state, sessionId:session});
            status.textContent = "Recording. Tap Stop and save.";
        } catch (error) { status.textContent = "Recording needs attention: " + error.message + ". Grant access if requested, then tap Record again."; }
        finally { releaseBusy(); }
    });
    stopButton.addEventListener("click", stop);
    document.getElementById("retry").addEventListener("click", async () => {
        if (busy || !selfWidget.state.pendingRecording) return;
        busy = true;
        controls();
        try { await save(selfWidget.state.pendingRecording); }
        catch (error) { status.textContent = "Save retry failed: " + error.message; }
        finally { releaseBusy(); }
    });
    document.getElementById("stop-playback").addEventListener("click", async () => {
        if (!playback || busy) return;
        busy = true;
        try {
            await dikciz.command("audioPlayback", {action:"stop", sessionId:playback});
            playback = null;
            status.textContent = "Playback stopped.";
        } catch (error) { status.textContent = "Playback stop failed: " + error.message; }
        finally { releaseBusy(); }
    });
    window.addEventListener("dikciz-state", render);
    window.addEventListener("dikciz-ready", () => {
        ready = true;
        session = selfWidget.state.sessionId || null;
        render();
        observe();
    });
    window.addEventListener("dikciz-visibility", event => { if (!event.detail.visible) stop(); else observe(); });
    setInterval(() => {
        if (!ready) return;
        if (session && !selfWidget.visible) stop();
        else if (selfWidget.visible && !document.hidden) observe();
    }, POLL_MS);
})();

(() => {
    const card = document.querySelector(".card");
    const reduced = matchMedia("(prefers-reduced-motion: reduce)");
    const visibility = () => { document.body.dataset.visible = String(!document.hidden && window.selfWidget?.visible !== false); };
    card?.addEventListener("pointermove", event => {
        if (reduced.matches || event.pointerType === "touch") return;
        const bounds = card.getBoundingClientRect();
        card.style.setProperty("--ry", ((event.clientX - bounds.left) / bounds.width * 6 - 3) + "deg");
        card.style.setProperty("--rx", (3 - (event.clientY - bounds.top) / bounds.height * 6) + "deg");
    });
    card?.addEventListener("pointerleave", () => {
        card.style.setProperty("--rx", "0deg");
        card.style.setProperty("--ry", "0deg");
    });
    document.addEventListener("visibilitychange", visibility);
    window.addEventListener("dikciz-visibility", visibility);
    visibility();
})();
