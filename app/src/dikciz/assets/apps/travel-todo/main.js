"use strict";

const NEW_YORK = {latitude: 40.7128, longitude: -74.0060, radiusMeters: 20000};
const MAX_TASKS = 20;
const MAX_TITLE = 120;
const ARRIVAL_TASK = "new-york";
const FENCE_SUFFIX = "-nyc";

function ownedFenceId() { return context.scriptId + FENCE_SUFFIX; }

function phone(type, fields) {
    const reply = context.request(Object.assign({type: type}, fields || {}));
    if (reply.type === "error") throw Object.assign(new Error(reply.message), {code: reply.code});
    return reply.result;
}
function initialState() {
    return {tasks: [
        {id: ARRIVAL_TASK, title: "Go to New York", done: false},
        {id: "pack", title: "Pack my bag", done: false},
        {id: "charge", title: "Charge my phone", done: false}
    ], watching: false, nextId: 1, lastAction: "Ready when you are"};
}
function stopWatching(state) {
    const reply = context.request({type: "geofenceDelete", id: ownedFenceId()});
    if (reply.type === "error" && reply.code !== "native_record_not_found") {
        throw Object.assign(new Error(reply.message), {code: reply.code});
    }
    state.watching = false;
}
function handle(event, state) {
    const input = event.payload || {};
    if (event.type === "todo.watch") {
        const id = ownedFenceId();
        phone("geofenceSave", Object.assign({id: id, title: "Travel todo: New York", enabled: true}, NEW_YORK));
        state.fenceId = id;
        state.watching = true;
        state.tasks.find(function (task) { return task.id === ARRIVAL_TASK; }).done = false;
        state.lastAction = "Watching for a GPS fix near New York";
    } else if (event.type === "todo.stop" || event.type === "app.stopped") {
        stopWatching(state);
        state.lastAction = "Location rule stopped";
    } else if (event.type === "geofence.enter") {
        if (!state.watching || input.id !== ownedFenceId()) return;
        stopWatching(state);
        state.tasks.find(function (task) { return task.id === ARRIVAL_TASK; }).done = true;
        state.lastAction = "Arrived in New York. Checked automatically by GPS";
    } else if (event.type === "todo.add") {
        const title = typeof input.title === "string" ? input.title.trim() : "";
        if (!title || title.length > MAX_TITLE || state.tasks.length >= MAX_TASKS) {
            throw Object.assign(new Error("Use 1 to 120 characters, with at most 20 tasks."), {code: "validation_failed"});
        }
        state.tasks.push({id: "task-" + state.nextId++, title: title, done: false});
        state.lastAction = "Task added";
    } else if (event.type === "todo.toggle" || event.type === "example.todo.complete") {
        const task = state.tasks.find(function (item) { return item.id === input.id; });
        if (!task) throw Object.assign(new Error("Choose an existing task ID."), {code: "validation_failed"});
        task.done = event.type === "example.todo.complete" ? true : !task.done;
        if (task.id === ARRIVAL_TASK && task.done) stopWatching(state);
        state.lastAction = event.type === "example.todo.complete" ? "Task checked by a custom event" : "Task updated";
    }
}
function onEvent(event) {
    const state = context.state.tasks ? JSON.parse(JSON.stringify(context.state)) : initialState();
    try {
        handle(event, state);
        return {status: state.lastAction, result: {watching: state.watching}, actions: [{type: "patchState", values: state}]};
    } catch (error) {
        return {result: {error: {code: error.code || "todo_failed", message: error.message || "Task operation failed"}}};
    }
}
