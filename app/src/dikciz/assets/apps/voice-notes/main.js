function onEvent(event) {
    if ((event.type === "app.ui.hidden" || event.type === "app.stopped") && context.state.sessionId) {
        const reply = context.request({type: "microphoneSession", action: "stop", sessionId: context.state.sessionId});
        if (reply.type === "error") return {result: {error: {code: reply.code, message: reply.message}}};
        const recording = reply.result;
        const pending = recording.state === "completed" && recording.path ? {
            path: recording.path,
            name: context.state.pendingRecording && context.state.pendingRecording.name || "note-" + context.request({type: "customAppRuntime"}).result.invocationId + ".m4a",
            durationMilliseconds: recording.durationMilliseconds
        } : context.state.pendingRecording || null;
        return {status: "Recording stopped", actions: [{type: "patchState", values: {sessionId: null, pendingRecording: pending}}]};
    }
    return {status: "Ready", actions: []};
}
