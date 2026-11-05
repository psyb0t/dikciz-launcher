"use strict";

const SECOND = 1000;
const ACTIVE = ["armed", "warning", "active"];
const TIMER = "deadline";
const MAX_CONTACTS = 10;
const MAX_RECEIPTS = 240;
const DEFAULT_MESSAGE = "I need help. Last known location: {location}. Accuracy: {accuracyMeters} m. Fix time: {timestamp}.";

function fail(code, message) {
    const error = new Error(message);
    error.code = code;
    throw error;
}
function phone(type, fields) {
    const reply = context.request(Object.assign({}, fields || {}, {type: type}));
    if (reply.type === "error") fail(reply.code, reply.message || "Phone operation failed");
    return reply.result;
}
function active(run) { return Boolean(run && ACTIVE.indexOf(run.state) >= 0); }
function save(state) { phone("customAppState", {action: "replace", state: state}); }
function clock() { return phone("customAppRuntime"); }
function closeAlert() { phone("customAppAlert", {action: "close"}); }
function bounded(value, fallback, minimum, maximum) {
    if (value === undefined) return fallback;
    if (!Number.isInteger(value) || value < minimum || value > maximum) fail("validation_failed", "A plan timing is outside its allowed range");
    return value;
}
function validatePlan(input) {
    const allowed = ["contacts", "message", "checkInSeconds", "graceSeconds", "updateSeconds", "maximumUpdates", "subscriptionId"];
    if (Object.keys(input).some(function (key) { return allowed.indexOf(key) < 0; })) fail("validation_failed", "Unknown SOS plan field");
    if (!Array.isArray(input.contacts) || !input.contacts.length || input.contacts.length > MAX_CONTACTS) fail("validation_failed", "Choose one through ten exact recipients");
    const seen = {};
    const contacts = input.contacts.map(function (contact) {
        if (!contact || typeof contact !== "object" || Object.keys(contact).some(function (key) { return key !== "address" && key !== "label"; }) ||
            typeof contact.address !== "string" || !/^[+]?[0-9() .-]{3,63}$/.test(contact.address)) fail("validation_failed", "Invalid contact address");
        const address = contact.address.replace(/[() .-]/g, "");
        if (!/^[+]?[0-9]{3,32}$/.test(address) || seen[address]) fail("validation_failed", "Invalid or duplicate SOS address");
        seen[address] = true;
        if (contact.label !== undefined && (typeof contact.label !== "string" || !contact.label.length || contact.label.length > 80)) fail("validation_failed", "Invalid contact label");
        return contact.label === undefined ? {address: address} : {address: address, label: contact.label};
    });
    const message = input.message === undefined ? DEFAULT_MESSAGE : input.message;
    if (typeof message !== "string" || !message.trim() || message.length > 1200) fail("validation_failed", "SOS message must contain one through 1200 characters");
    const plan = {contacts: contacts, message: message, checkInSeconds: bounded(input.checkInSeconds, 3600, 60, 604800),
        graceSeconds: bounded(input.graceSeconds, 60, 5, 3600), updateSeconds: bounded(input.updateSeconds, 600, 60, 86400),
        maximumUpdates: bounded(input.maximumUpdates, 3, 1, 24)};
    if (input.subscriptionId !== undefined) plan.subscriptionId = bounded(input.subscriptionId, 0, 0, 2147483647);
    return plan;
}
function location() {
    const reply = context.request({type: "locationGet", maxAgeMilliseconds: 120000, timeoutMilliseconds: 0});
    const fix = reply.type === "result" && reply.result.available ? reply.result : null;
    return fix && Number.isFinite(fix.accuracyMeters) && fix.accuracyMeters >= 0 && fix.accuracyMeters <= 200 ? fix : null;
}
function message(plan, fix) {
    return plan.message.split("{location}").join(fix ? "https://maps.google.com/?q=" + fix.latitude + "," + fix.longitude : "unavailable")
        .split("{accuracyMeters}").join(fix ? String(Math.floor(fix.accuracyMeters)) : "unknown")
        .split("{timestamp}").join(fix ? new Date(fix.timestampMilliseconds).toISOString() : "unknown");
}
function requireAccess(runtime) {
    const access = runtime.androidAccess;
    if (!access.locationPrecise) fail("native_permission_locationForeground", "Precise location access is required before arming");
    if (!access.notificationPosting || !runtime.notificationsEnabled) fail("sos_notifications_disabled", "Enable Dikciz notifications before arming");
    if (!runtime.urgentChannelEnabled) fail("sos_warning_channel_disabled", "Enable urgent app countdown alerts in Android notification settings");
    if (!access.smsSend) fail("native_permission_sms", "SMS permission is required before arming");
}
function schedule(state, event, seconds) {
    const runtime = clock();
    const duration = Math.max(SECOND, seconds * SECOND);
    state.run.deadlineAt = runtime.nowMilliseconds + duration;
    state.run.deadlineElapsed = runtime.elapsedMilliseconds + duration;
    save(state);
    phone("customAppSchedule", {action: "schedule", timerId: TIMER, delayMilliseconds: duration, event: event,
        payload: {runId: state.run.id, sequence: state.run.updates}});
}
function warning(state) {
    phone("customAppAlert", {action: "show", episodeId: state.run.id + "_" + state.run.updates,
        durationMilliseconds: state.plan.graceSeconds * SECOND, title: "SOS MESSAGE PENDING",
        detail: "An SOS SMS will be sent to " + state.plan.contacts.length + " configured contact(s). Tap Cancel SOS to stop pending messages. A submitted message cannot be recalled.",
        cancelLabel: "Cancel SOS", cancelEvent: "cancel",
        announcement: "SOS alert. A message will be sent to your configured contacts in {seconds} seconds. Tap Cancel SOS to stop it.",
        finalAnnouncement: "SOS message sends in {seconds} seconds. Tap Cancel SOS to stop it."});
}
function cancel(state, reason) {
    const run = state.run || {};
    run.state = "cancelled"; run.updatedAt = clock().nowMilliseconds;
    if (reason) run.reason = reason;
    state.run = run; save(state);
    phone("customAppSchedule", {action: "cancelAll"}); closeAlert();
    return run;
}
function deliver(state) {
    const run = state.run;
    const plan = state.plan;
    closeAlert();
    if (run.updates >= plan.maximumUpdates) { run.state = "completed"; save(state); return run; }
    const fix = location();
    if (!run.pendingBatch) {
        run.pendingBatch = plan.contacts.map(function (contact, index) {
            const item = {recipient: contact.address, body: message(plan, fix), idempotencyKey: "sos_" + run.id + "_" + run.updates + "_" + index};
            if (plan.subscriptionId !== undefined) item.subscriptionId = plan.subscriptionId;
            return item;
        });
        save(state);
    }
    let accepted = 0;
    const receipts = run.receipts || [];
    run.pendingBatch.forEach(function (send) {
        const response = context.request(Object.assign({type: "smsSend"}, send));
        const result = response.type === "result" ? response.result : null;
        const receipt = {idempotencyKey: send.idempotencyKey, state: result ? result.state : "rejected"};
        if (!result) receipt.code = response.code;
        if (result && ["accepted", "sent", "delivered"].indexOf(result.state) >= 0) accepted++;
        receipts.push(receipt);
    });
    run.receipts = receipts.slice(-MAX_RECEIPTS);
    delete run.pendingBatch;
    run.updates++; run.updatedAt = clock().nowMilliseconds;
    run.state = !accepted ? "needs_review" : run.updates >= plan.maximumUpdates ? "completed" : "active";
    save(state);
    if (run.state === "active") schedule(state, "warning.start", Math.max(1, plan.updateSeconds - plan.graceSeconds));
    return run;
}
function handle(event, state) {
    const name = event.type;
    const input = event.payload || {};
    const runtime = clock();
    const run = state.run;
    if (name === "plan.preview") {
        if (!state.plan) fail("sos_plan_missing", "Save an SOS contact plan first");
        const fix = location();
        return {dryRun: true, smsSubmitted: false, contacts: state.plan.contacts, message: message(state.plan, fix), location: fix};
    }
    if (active(run) && (run.boot !== runtime.boot || name === "app.resume")) {
        run.state = "needs_review"; run.reason = run.boot !== runtime.boot ? "device_rebooted" : "interrupted_delivery";
        save(state); phone("customAppSchedule", {action: "cancelAll"}); closeAlert();
    }
    if (name === "app.resume") return run || {};
    if (name === "cancel" || name === "app.stopped") return cancel(state, name === "app.stopped" ? "owner_paused" : null);
    if (name === "plan.save") {
        if (active(run)) fail("sos_active", "Cancel the active check-in before changing its plan");
        state.plan = validatePlan(input); save(state);
        return {state: "saved", armed: false, plan: state.plan};
    }
    if (!state.plan) fail("sos_plan_missing", "Save an SOS contact plan first");
    if (name === "arm" || name === "trigger") {
        if (input.confirm !== true) fail("validation_failed", "Arming or triggering SMS requires confirm=true");
        if (active(run)) fail("sos_active", "Use checkIn or cancel for the current run");
        requireAccess(runtime);
        state.run = {id: runtime.invocationId, state: name === "arm" ? "armed" : "warning", boot: runtime.boot,
            startedAt: runtime.nowMilliseconds, updatedAt: runtime.nowMilliseconds, updates: 0, receipts: []};
        schedule(state, "deadline", name === "arm" ? state.plan.checkInSeconds : state.plan.graceSeconds);
        if (name === "trigger") warning(state);
        return state.run;
    }
    if (name === "checkIn") {
        if (!active(run)) fail("sos_not_armed", "No check-in is armed");
        run.state = "armed"; run.updatedAt = runtime.nowMilliseconds;
        closeAlert(); schedule(state, "deadline", state.plan.checkInSeconds); return run;
    }
    if (name === "deadline" || name === "warning.start") {
        if (!active(run) || input.runId !== run.id || input.sequence !== run.updates) return {state: "stale_timer_ignored"};
        if (run.state === "armed" || name === "warning.start") {
            if (run.state === "armed") run.state = "warning";
            schedule(state, "deadline", state.plan.graceSeconds); warning(state); return run;
        }
        return deliver(state);
    }
    if (name === "status") return {plan: state.plan || null, run: state.run || null};
    fail("validation_failed", "Unknown SOS app event");
}
function onEvent(event) {
    const state = JSON.parse(JSON.stringify(context.state));
    try { return {status: "Handled", result: handle(event, state)}; }
    catch (error) {
        if (active(state.run) && ["native_permission_sms", "native_permission_locationForeground", "sos_active", "validation_failed"].indexOf(error.code) < 0) {
            state.run.state = "needs_review"; state.run.reason = error.code || "app_handler_failed";
            save(state); phone("customAppSchedule", {action: "cancelAll"}); closeAlert();
        }
        return {status: "Rejected", result: {error: {code: error.code || "custom_app_operation_failed", message: error.message || "SOS operation failed"}}};
    }
}
