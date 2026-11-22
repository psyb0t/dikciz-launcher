"use strict";

const G = 9.80665;
const TYPE_LINEAR_ACCELERATION = 10;
const DEFAULT_COUNTDOWN_SECONDS = 20;
const MIN_COUNTDOWN_SECONDS = 5;
const MAX_COUNTDOWN_SECONDS = 120;
const SHORT_CALL_MS = 2000;
const DIALER_PACKAGES = ["com.google.android.dialer", "com.android.dialer"];
const DIALER_END_BUTTON = "incall_end_call";
const MAX_GPS_ACCELERATION = 20;
const MAX_GPS_FIX_GAP_MS = 6000;
const EARTH_RADIUS_METERS = 6371000;
const DEGREES_TO_RADIANS = Math.PI / 180;
const MIN_DISPLACEMENT_FRACTION = 0.65;
const MAX_DISPLACEMENT_FRACTION = 1.6;
const MIN_BRAKING_START_KPH = 20;
const MIN_BRAKING_LOSS_KPH = 12;
const MIN_BRAKING_DECELERATION = 3;
const MIN_BRAKING_INTERVAL_MS = 1000;
const MAX_BRAKING_INTERVAL_MS = 4500;
const MAX_GPS_ACCURACY_METERS = 25;
const MIN_BRAKING_MOTION_G = 0.35;
const MOTION_SAVE_INTERVAL_MS = 750;
const KPH_PER_MPS = 3.6;
const MS_PER_SECOND = 1000;
const MAX_SPEED_AGE_MS = 10000;
const DEFAULT_ARM_HOURS = 2;
const MIN_ARM_HOURS = 0.01;
const MAX_ARM_HOURS = 168;
const MS_PER_HOUR = 3600000;
const MAX_MONITOR_LEASE_MS = 2 * MS_PER_HOUR;
const SENSOR_PERIOD_US = 5000;
const SENSOR_HISTORY_MS = 2000;
const SENSOR_DELIVERY_MS = 100;
const NS_PER_MS = 1000000;
const NEXT_STEP_MS = 1000;
const CAPTURE_TIMEOUT_MS = 5000;
const WHATSAPP_PACKAGE = "com.whatsapp";
const DOCUMENTS_PACKAGES = ["com.google.android.documentsui", "com.android.documentsui"];
const DOCUMENT_TITLES = ["Dikciz", "apps", "impact-response", "files"];
const MAX_UI_WAIT_ATTEMPTS = 4;
const PHOTO_DIRECTORY = "/sdcard/Dikciz/apps/impact-response/files";

function request(type, fields) {
    return context.request(Object.assign({}, fields || {}, {type: type}));
}

function required(type, fields) {
    const reply = request(type, fields);
    if (reply.type === "result") return reply.result;
    throw new Error(reply.message || type + " failed");
}

function save(state) {
    state.locationDemand = !!state.armed && (state.settings.speed === true || state.settings.braking === true);
    required("customAppState", {action: "replace", state: state});
}

function done(status, fields) {
    return {status: status, result: fields || {}};
}

function rejected(message) {
    return done("Rejected", {error: {code: "impact_invalid", message: message}});
}

function normalize(value) {
    const address = typeof value === "string" ? value.replace(/[ ().-]/g, "") : "";
    return /^\+[1-9][0-9]{6,14}$/.test(address) ? address : "";
}

function label(value) {
    return typeof value === "string" && value.length <= 120 && !/[\x00-\x1f\x7f]/.test(value)
        ? value.trim() : "";
}

function settings(input) {
    const address = normalize(input.address);
    const threshold = input.threshold;
    if (!address || !Number.isFinite(threshold) || threshold < 1 || threshold > 12) return null;
    const armHours = Object.prototype.hasOwnProperty.call(input, "armHours") ? input.armHours : DEFAULT_ARM_HOURS;
    if (!Number.isFinite(armHours) || armHours < MIN_ARM_HOURS || armHours > MAX_ARM_HOURS) return null;
    const countdownSeconds = Object.prototype.hasOwnProperty.call(input, "countdownSeconds")
        ? input.countdownSeconds : DEFAULT_COUNTDOWN_SECONDS;
    if (!Number.isInteger(countdownSeconds) ||
        countdownSeconds < MIN_COUNTDOWN_SECONDS || countdownSeconds > MAX_COUNTDOWN_SECONDS) return null;
    const sim = input.subscriptionId;
    if (sim != null && (!Number.isInteger(sim) || sim < 0 || sim > 2147483647)) return null;
    const whatsapp = input.whatsapp === true;
    const whatsappName = label(input.whatsappName);
    const storageName = label(input.storageName);
    if (whatsapp && (!input.photos || !whatsappName || !storageName)) return null;
    return {address: address, threshold: threshold, armHours: armHours, countdownSeconds: countdownSeconds, call: input.call === true,
        photos: input.photos === true, speed: input.speed === true, subscriptionId: sim == null ? null : sim,
        whatsapp: whatsapp, whatsappName: whatsappName, storageName: storageName, braking: input.braking === true};
}

function describe(at, axes, threshold, speed) {
    const acceleration = Math.sqrt(axes.reduce((sum, axis) => sum + axis * axis, 0));
    return "Possible hard impact at " + new Date(at).toISOString() + " UTC. Phone linear acceleration "
        + acceleration.toFixed(1) + " m/s² (" + (acceleration / G).toFixed(2) + " g, threshold " + threshold
        + " g). X/Y/Z " + axes.map(axis => axis.toFixed(1)).join("/") + " m/s². "
        + (speed ? "GPS speed " + speed.kph.toFixed(1) + " km/h, sample received " + speed.seconds
            + " s before detection." : "Speed unavailable.");
}

function active(run) {
    return run && (run.phase === "warning" || run.phase === "alert");
}

function close(state, phase) {
    stopMotion(state);
    state.armed = null;
    if (active(state.incident)) {
        const run = state.incident;
        run.phase = phase;
        delete run.resumeArm;
        if (run.whatsapp && !run.whatsapp.finished) {
            run.whatsapp.finished = true;
            run.whatsapp.error = run.whatsapp.stage === "verify" ? "send_unverified" : "interrupted";
        }
    }
    save(state);
    required("customAppSchedule", {action: "cancelAll"});
    required("customAppAlert", {action: "close"});
    return done("Disarmed");
}

function cancelWarning(state, input) {
    const run = state.incident;
    const episodeId = input.episodeId || input.id;
    if (!run || run.phase !== "warning" || (episodeId && episodeId !== run.id)) {
        return done("No matching countdown");
    }
    const resume = run.resumeArm;
    close(state, "cancelled");
    const clock = required("customAppRuntime");
    if (!resume || resume.boot !== clock.boot || !Number.isFinite(resume.until) ||
        resume.until <= clock.elapsedMilliseconds) {
        state.monitorStatus = "expired";
        save(state);
        return done("Alert cancelled. Arming duration ended.");
    }

    state.speedFix = null;
    state.motion = null;
    state.armed = {boot: resume.boot, until: resume.until, since: clock.nowMilliseconds, sensorId: resume.sensorId};
    if (!startMotionLease(state, clock)) return done("Alert cancelled. Motion monitoring could not resume.");
    return done("Alert cancelled. Monitoring resumed.");
}

function status(reply, fallback) {
    return reply.type === "result" ? (reply.result.state || fallback) : (reply.code || "failed");
}

function schedule(run, name, delay) {
    const reply = request("customAppSchedule", {
        action: "schedule", timerId: name, event: name, delayMilliseconds: delay, payload: {id: run.id},
    });
    if (reply.type !== "result") run.failure = reply.code || "timer_failed";
    return reply.type === "result";
}

function logStep(run, event, outcome, detail) {
    if (run.logStatus === "log_unavailable") return;
    const reply = request("customAppLog", {level: outcome === "failed" ? "warn" : "info",
        event: event, incidentId: run.id, outcome: outcome, detail: detail || ""});
    if (reply.type !== "result") run.logStatus = "log_unavailable";
}

function show(run, countdownMilliseconds) {
    return request("customAppAlert", {
        action: "show", episodeId: run.id, durationMilliseconds: countdownMilliseconds,
        title: run.braking ? "Hard braking detected" : "Possible impact detected", detail: run.message,
        cancelLabel: "Cancel pending alerts", cancelEvent: "impact.cancel",
        announcement: run.message + " SMS sends in {seconds} seconds.",
    });
}

function plausibleTravel(before, now, gap) {
    const latBefore = before.latitude * DEGREES_TO_RADIANS;
    const latNow = now.latitude * DEGREES_TO_RADIANS;
    const latitudeDelta = latNow - latBefore;
    const longitudeDelta = (now.longitude - before.longitude) * DEGREES_TO_RADIANS;
    const angle = Math.sin(latitudeDelta / 2) ** 2 +
        Math.cos(latBefore) * Math.cos(latNow) * Math.sin(longitudeDelta / 2) ** 2;
    const distance = 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.min(1, angle)));
    const uncertainty = before.accuracy + now.accuracy;
    const speedMean = (before.kph + now.kph) / 2 / KPH_PER_MPS;
    const expected = speedMean * gap / MS_PER_SECOND;
    return distance + uncertainty >= expected * MIN_DISPLACEMENT_FRACTION &&
        distance <= expected * MAX_DISPLACEMENT_FRACTION + uncertainty;
}

function observeSpeed(state, clock, sample, eventTime) {
    if ((!state.settings.speed && !state.settings.braking) || sample.provider !== "gps") return done("Watching");
    if (!Number.isFinite(sample.speed) || sample.speed < 0 || !Number.isFinite(sample.accuracy) ||
        sample.accuracy <= 0 || sample.accuracy > MAX_GPS_ACCURACY_METERS ||
        !Number.isFinite(sample.latitude) || Math.abs(sample.latitude) > 90 ||
        !Number.isFinite(sample.longitude) || Math.abs(sample.longitude) > 180) {
        if (state.speedFix) {
            state.speedFix = null;
            save(state);
        }
        return done("Watching");
    }
    const before = state.speedFix;
    const now = {at: clock.elapsedMilliseconds, observedAt: eventTime, kph: sample.speed * KPH_PER_MPS,
        latitude: sample.latitude, longitude: sample.longitude, accuracy: sample.accuracy, confirmed: false};
    if (before) {
        const gap = now.at - before.at;
        const change = Math.abs(now.kph - before.kph) / KPH_PER_MPS / (gap / MS_PER_SECOND);
        now.confirmed = gap > 0 && gap <= MAX_GPS_FIX_GAP_MS && change <= MAX_GPS_ACCELERATION &&
            plausibleTravel(before, now, gap);
    }
    state.speedFix = now;
    if (state.settings.braking && before && before.confirmed && now.confirmed && state.motion) {
        const interval = now.at - before.at;
        const loss = before.kph - now.kph;
        const deceleration = loss / KPH_PER_MPS / (interval / MS_PER_SECOND);
        if (interval >= MIN_BRAKING_INTERVAL_MS && interval <= MAX_BRAKING_INTERVAL_MS &&
            state.motion.at >= before.at - MOTION_SAVE_INTERVAL_MS && state.motion.at <= now.at &&
            before.kph >= MIN_BRAKING_START_KPH && loss >= MIN_BRAKING_LOSS_KPH &&
            deceleration >= MIN_BRAKING_DECELERATION) {
            return beginWarning(state, clock, state.motion.axes, {before: before.kph, after: now.kph,
                seconds: interval / MS_PER_SECOND, deceleration: deceleration});
        }
    }
    save(state);
    return done("Watching");
}

function beginWarning(state, clock, axes, braking, observedAt) {
    const arm = state.armed;
    stopMotion(state);
    const countdownMilliseconds = (state.settings.countdownSeconds || DEFAULT_COUNTDOWN_SECONDS) * MS_PER_SECOND;
    const at = Number.isFinite(observedAt) ? observedAt : clock.nowMilliseconds;
    const age = state.speedFix ? at - state.speedFix.observedAt : -1;
    const speed = state.settings.speed && state.speedFix && state.speedFix.confirmed && age >= 0 && age <= MAX_SPEED_AGE_MS
        ? {kph: state.speedFix.kph, seconds: Math.round(age / MS_PER_SECOND)} : null;
    const message = braking
        ? "Hard braking detected at " + new Date(clock.nowMilliseconds).toISOString() + ". GPS speed fell from "
            + braking.before.toFixed(1) + " to " + braking.after.toFixed(1) + " km/h in " + braking.seconds.toFixed(1)
            + " s. Estimated deceleration " + braking.deceleration.toFixed(1) + " m/s². Phone linear acceleration "
            + Math.hypot.apply(Math, axes).toFixed(1) + " m/s². X/Y/Z "
            + axes.map(axis => axis.toFixed(1)).join("/") + " m/s²."
        : describe(at, axes, state.settings.threshold, speed);
    const run = {
        id: clock.invocationId, boot: clock.boot, phase: "warning", at: at,
        deadline: clock.elapsedMilliseconds + countdownMilliseconds, axes: axes, speed: speed, photos: {},
        resumeArm: {boot: arm.boot, until: arm.until, sensorId: arm.sensorId},
        recipient: state.settings.address, message: message, braking: braking,
    };
    state.incident = run;
    state.armed = null;
    save(state);
    logStep(run, "impact_detected", "started", run.braking ? "hard_braking" : "acceleration");
    const alert = show(run, countdownMilliseconds);
    if (alert.type !== "result") run.failure = alert.code || "alert_failed";
    if (run.failure || !schedule(run, "impact.deliver", countdownMilliseconds)) return close(state, "blocked");
    return done("Cancellation countdown started");
}

function photo(run, facing) {
    const cameras = request("cameraList");
    const found = cameras.type === "result" && cameras.result.items.find(item => item.facing === facing);
    if (!found) return {state: cameras.code || "camera_unavailable"};
    const reply = request("cameraCapture", {cameraId: found.cameraId, timeoutMilliseconds: CAPTURE_TIMEOUT_MS});
    if (reply.type !== "result") return {state: reply.code || "capture_failed"};
    const image = reply.result;
    if (image.state !== "saved" || !image.path || !(image.bytes > 0)) return {state: "capture_unverified"};
    const filename = "impact-" + run.id + "-" + facing + ".jpg";
    const directory = request("fileMkdir", {path: PHOTO_DIRECTORY, parents: true});
    if (directory.type !== "result") {
        return {state: "saved", path: image.path, bytes: image.bytes, shareError: directory.code};
    }
    const copy = request("fileManage", {action: "copy", path: image.path, destination: PHOTO_DIRECTORY + "/" + filename});
    if (copy.type !== "result") return {state: "saved", path: image.path, bytes: image.bytes, shareError: copy.code};
    return {state: "saved", path: PHOTO_DIRECTORY + "/" + filename, filename: filename, bytes: image.bytes};
}

function unique(nodes, predicate) {
    const matches = nodes.filter(predicate);
    return matches.length === 1 ? matches[0] : null;
}

function resource(nodes, id) {
    return unique(nodes, node => node.viewIdResourceName === WHATSAPP_PACKAGE + ":id/" + id);
}

function matchesText(node, text) {
    return node && node.text === text;
}

function documentRow(nodes, text) {
    const title = unique(nodes, node => node.text === text && typeof node.viewIdResourceName === "string" &&
        node.viewIdResourceName.endsWith(":id/title"));
    if (!title) return null;
    return nodes.filter(node => node.clickable &&
        (title.nodeId === node.nodeId || title.nodeId.startsWith(node.nodeId + ".")))
        .sort((first, second) => second.nodeId.length - first.nodeId.length)[0];
}

function finishWhatsApp(state, run, error) {
    const share = run.whatsapp;
    share.finished = true;
    if (error) {
        share.error = error;
        share.failedAt = share.stage;
    }
    logStep(run, "whatsapp_finished", error ? "failed" : "completed", error ? share.failedAt + ": " + error : "all_attachments_verified");
    finishDelivery(state, run);
}

function queueWhatsApp(state, run) {
    if (!schedule(run, "impact.whatsapp", NEXT_STEP_MS)) {
        finishWhatsApp(state, run, run.whatsapp.stage === "verify" ? "send_unverified" : "timer_failed");
        return;
    }
    save(state);
}

function startWhatsApp(state, run) {
    const sides = ["front", "back"].filter(facing => run.photos[facing] && run.photos[facing].filename);
    run.whatsapp = {stage: "open", index: 0, sides: sides, sent: [], waits: 0};
    if (!sides.length) return finishWhatsApp(state, run, "no_saved_photos");
    logStep(run, "whatsapp_started", "started", sides.join(","));
    queueWhatsApp(state, run);
}

function waitForWhatsApp(state, run, reason) {
    const share = run.whatsapp;
    share.waits += 1;
    if (share.waits >= MAX_UI_WAIT_ATTEMPTS) {
        return finishWhatsApp(state, run, share.stage === "verify" ? "send_unverified" : reason);
    }
    queueWhatsApp(state, run);
}

function nextWhatsAppStep(state, run) {
    const share = run.whatsapp;
    if (!share || share.finished) return;
    const device = request("deviceState");
    if (device.type !== "result" || device.result.keyguardLocked !== false ||
        device.result.screenInteractive !== true) {
        return finishWhatsApp(state, run, share.stage === "verify" ? "send_unverified" : "phone_locked_or_asleep");
    }
    const options = state.settings;
    if (share.stage === "open") {
        const data = "https://wa.me/" + run.recipient.slice(1) + "?text=" + encodeURIComponent(run.message);
        const launchReply = request("intent", {intentType: "activity", action: "android.intent.action.VIEW", data: data});
        if (launchReply.type !== "result") return finishWhatsApp(state, run, launchReply.code || "whatsapp_open_failed");
        share.stage = "chat";
        share.waits = 0;
        return queueWhatsApp(state, run);
    }

    const reply = request("accessibilitySnapshot");
    if (reply.type !== "result") {
        return waitForWhatsApp(state, run, reply.code || "accessibility_unavailable");
    }
    const snapshot = reply.result;
    if (snapshot.truncated && share.stage === "preview") {
        share.window = snapshot.packageName;
        return waitForWhatsApp(state, run, "window_snapshot_truncated");
    }
    const nodes = snapshot.nodes.filter(node => node.visibleToUser && node.enabled &&
        node.packageName === snapshot.packageName);
    const facing = share.sides[share.index];
    const filename = run.photos[facing].filename;
    const directory = [options.storageName].concat(DOCUMENT_TITLES, filename);
    const header = resource(nodes, "conversation_contact_name");
    let target;
    let next;
    let action = "click";
    if (share.stage === "chat" && snapshot.packageName === WHATSAPP_PACKAGE) {
        if (header && !matchesText(header, options.whatsappName)) {
            return finishWhatsApp(state, run, "recipient_mismatch");
        }
        if (matchesText(header, options.whatsappName) && matchesText(resource(nodes, "entry"), run.message)) {
            target = resource(nodes, "input_attach_button");
            next = "attach";
        }
    } else if (share.stage === "attach" && snapshot.packageName === WHATSAPP_PACKAGE) {
        target = resource(nodes, "pickfiletype_document_holder");
        next = "browse";
    } else if (share.stage === "browse" && snapshot.packageName === WHATSAPP_PACKAGE) {
        target = resource(nodes, "browseOtherDocs");
        next = "roots";
    } else if (share.stage === "roots" && DOCUMENTS_PACKAGES.includes(snapshot.packageName)) {
        target = unique(nodes, node => node.contentDescription === "Show roots");
        share.directoryIndex = 0;
        next = "directory";
    } else if (share.stage === "directory" && DOCUMENTS_PACKAGES.includes(snapshot.packageName)) {
        target = documentRow(nodes, directory[share.directoryIndex]);
        next = share.directoryIndex === directory.length - 1 ? "preview" : "directory";
        if (!target && next === "preview") {
            target = unique(nodes, node => node.viewIdResourceName === snapshot.packageName + ":id/option_menu_search" &&
                node.clickable);
            next = "search_input";
        }
    } else if (share.stage === "search_input" && DOCUMENTS_PACKAGES.includes(snapshot.packageName)) {
        target = unique(nodes, node => node.viewIdResourceName === snapshot.packageName + ":id/search_src_text" &&
            node.editable);
        action = "setText";
        next = "search_result";
    } else if (share.stage === "search_result" && DOCUMENTS_PACKAGES.includes(snapshot.packageName)) {
        const search = unique(nodes, node => node.viewIdResourceName === snapshot.packageName + ":id/search_src_text");
        if (matchesText(search, filename)) target = documentRow(nodes, filename);
        next = "preview";
    } else if (share.stage === "preview" && snapshot.packageName === WHATSAPP_PACKAGE) {
        const recipient = unique(nodes, node => node.clickable && node.className === "android.widget.Button" &&
            node.viewIdResourceName === null && node.text === options.whatsappName);
        if (recipient && matchesText(resource(nodes, "document_file_name"), filename) &&
            matchesText(resource(nodes, "caption"), run.message)) {
            const send = resource(nodes, "send");
            if (send && send.contentDescription === "Send") target = send;
            next = "verify";
        }
    } else if (share.stage === "verify" && snapshot.packageName === WHATSAPP_PACKAGE &&
        matchesText(header, options.whatsappName)) {
        const attachment = unique(nodes, node =>
            node.text === filename && node.viewIdResourceName === WHATSAPP_PACKAGE + ":id/title");
        if (attachment) {
            logStep(run, "whatsapp_attachment", "verified", facing);
            share.sent.push(facing);
            share.index += 1;
            if (share.index === share.sides.length) return finishWhatsApp(state, run);
            share.stage = "open";
            share.waits = 0;
            return queueWhatsApp(state, run);
        }
    }
    if (!target || (action === "click" && !target.clickable)) {
        share.window = snapshot.packageName;
        return waitForWhatsApp(state, run, share.stage === "search_result" ? "exact_file_not_found" : "expected_control_missing");
    }

    // Persist before clicking Send. If the result is lost, verification is the next action.
    const sending = next === "verify";
    if (sending) {
        share.stage = next;
        share.waits = 0;
        save(state);
    }
    const fields = {snapshotId: snapshot.snapshotId, nodeId: target.nodeId, action: action};
    if (action === "setText") fields.text = filename;
    const outcome = request("accessibilityAction", fields);
    if (!sending && (outcome.type !== "result" || outcome.result.outcome !== "executed")) {
        return waitForWhatsApp(state, run, outcome.code || "android_click_rejected");
    }
    if (share.stage === "directory" && next === "directory") share.directoryIndex += 1;
    if (!sending) logStep(run, "whatsapp_step", "advanced", share.stage + ":" + next);
    share.stage = next;
    share.waits = 0;
    queueWhatsApp(state, run);
}

function completeDelivery(state, run) {
    run.phase = "completed";
    logStep(run, "impact_finished", "completed", "");
    save(state);
}

function finishDelivery(state, run) {
    if (state.settings.call && schedule(run, "impact.call", NEXT_STEP_MS)) {
        save(state);
        return;
    }
    completeDelivery(state, run);
}

function completeCall(state, run, result) {
    run.call = result;
    logStep(run, "short_call_finished", result === "ended" ? "completed" : "failed", result);
    completeDelivery(state, run);
}

function placeShortCall(state, run) {
    const phone = request("callControl", {action: "state"});
    if (phone.type !== "result" || phone.result.inCall !== false) {
        return completeCall(state, run, phone.code || "other_call_in_progress");
    }
    const device = request("deviceState");
    if (device.type !== "result" || device.result.keyguardLocked !== false || !device.result.screenInteractive) {
        return completeCall(state, run, "dialer_screen_unavailable");
    }
    const name = label(state.settings.whatsappName);
    if (name) {
        const contacts = request("contentQuery", {resource: "phones", columns: ["data1", "data4"],
            filter: {display_name: name}, limit: 20});
        if (contacts.type === "result" && !contacts.result.hasMore && contacts.result.items.length &&
            contacts.result.items.every(item => normalize(item.data4 || item.data1) === run.recipient)) {
            run.callContactName = name;
        }
    }
    run.call = "submitting";
    save(state);
    if (!schedule(run, "impact.call.end", SHORT_CALL_MS)) return completeCall(state, run, "hang_up_timer_unavailable");
    const placed = request("callControl", {action: "place", recipient: run.recipient});
    if (placed.type !== "result" || placed.result.state !== "accepted") {
        request("customAppSchedule", {action: "cancel", timerId: "impact.call.end"});
        return completeCall(state, run, placed.code || "call_rejected");
    }
    run.call = "dial_requested";
    save(state);
}

function endShortCall(state, run) {
    const phone = request("callControl", {action: "state"});
    if (phone.type === "result" && phone.result.inCall === false) {
        return completeCall(state, run, "no_active_call");
    }
    const dialer = request("accessibilitySnapshot");
    if (dialer.type !== "result" || dialer.result.truncated || !DIALER_PACKAGES.includes(dialer.result.packageName)) {
        return completeCall(state, run, "hang_up_unverified");
    }
    const screen = dialer.result;
    const nodes = screen.nodes.filter(node => node.packageName === screen.packageName && node.visibleToUser && node.enabled);
    const primaryName = screen.packageName + ":id/contactgrid_contact_name";
    const primaryNumber = screen.packageName + ":id/contactgrid_bottom_text";
    const matchesRecipient = nodes.some(node => {
        const resourceId = node.viewIdResourceName;
        if (resourceId !== primaryName && resourceId !== primaryNumber) return false;
        return normalize(node.text) === run.recipient ||
            (resourceId === primaryName && run.callContactName && node.text === run.callContactName);
    });
    const button = unique(nodes, node => node.clickable &&
        node.viewIdResourceName === screen.packageName + ":id/" + DIALER_END_BUTTON);
    if (!matchesRecipient || !button) {
        logStep(run, "short_call_dialer", "failed", screen.packageName + ":" +
            (matchesRecipient ? "recipient_verified" : "recipient_hidden") + ":" +
            (button ? "end_control_visible" : "end_control_missing"));
        return completeCall(state, run, "hang_up_unverified");
    }
    run.call = "ending";
    save(state);
    const result = request("accessibilityAction", {snapshotId: screen.snapshotId, nodeId: button.nodeId, action: "click"});
    if (result.type !== "result" || result.result.outcome !== "executed") {
        return completeCall(state, run, result.code || "hang_up_unverified");
    }
    if (!schedule(run, "impact.call.verify", NEXT_STEP_MS)) return completeCall(state, run, "hang_up_unverified");
    save(state);
}

function deliver(state, run) {
    run.phase = "alert";
    delete run.resumeArm;
    run.sms = "submitting";
    save(state);
    const message = {
        recipient: run.recipient, body: run.message, idempotencyKey: "impact_" + run.id,
    };
    if (state.settings.subscriptionId != null) message.subscriptionId = state.settings.subscriptionId;
    const sms = request("smsSend", message);
    run.sms = status(sms, "submitted");
    const closed = request("customAppAlert", {action: "close"});
    run.alert = status(closed, "closed");
    logStep(run, "impact_sms", sms.type === "result" ? "submitted" : "failed", run.sms);
    if (state.settings.photos && schedule(run, "impact.front", NEXT_STEP_MS)) save(state);
    else finishDelivery(state, run);
    return done("Incident alert started");
}

function stopMotion(state) {
    const arm = state.armed;
    if (arm && arm.sessionId) request("sensorStreamStop", {sessionId: arm.sessionId});
    else if (arm && arm.monitorId) request("customAppMotion", {action: "stop"});
}

function startMotionLease(state, clock) {
    const arm = state.armed;
    const remaining = arm.until - clock.elapsedMilliseconds;
    const duration = Math.min(MAX_MONITOR_LEASE_MS, Math.max(MS_PER_SECOND, remaining));
    const stream = request("sensorStreamStart", {
        sensorId: arm.sensorId, samplingPeriodMicroseconds: SENSOR_PERIOD_US,
        historyMilliseconds: SENSOR_HISTORY_MS, durationMilliseconds: duration,
        deliveryIntervalMilliseconds: SENSOR_DELIVERY_MS, background: true,
    });
    if (stream.type !== "result" || !stream.result.sessionId || stream.result.state !== "active") {
        state.armed = null;
        state.monitorStatus = "monitor_unavailable";
        save(state);
        return false;
    }
    arm.sessionId = stream.result.sessionId;
    state.monitorStatus = "starting";
    try {
        save(state);
    } catch (error) {
        stopMotion(state);
        throw error;
    }
    return true;
}

function onSensorSamples(state, input) {
    const arm = state.armed;
    if (!arm || arm.sessionId !== input.sessionId) return done("No matching monitor");
    if (input.lostSamples || input.discardedSamples) {
        state.monitorStatus = "sensor_feed_stalled";
        return close(state, "sensor_samples_lost");
    }
    if (!Array.isArray(input.samples)) return done("No sensor samples");
    let clock;
    let motionUpdated = false;
    for (let sampleIndex = 0; sampleIndex < input.samples.length; sampleIndex++) {
        const sample = input.samples[sampleIndex];
        const axes = sample.values;
        if (!Array.isArray(axes) || axes.length !== 3 || !axes.every(Number.isFinite)) continue;
        const acceleration = Math.hypot(axes[0], axes[1], axes[2]);
        const impact = acceleration >= state.settings.threshold * G;
        const sampledAt = Number(sample.timestampNanoseconds) / NS_PER_MS;
        const brakingMotion = state.settings.braking && acceleration >= MIN_BRAKING_MOTION_G * G &&
            (!state.motion || sampledAt - state.motion.at >= MOTION_SAVE_INTERVAL_MS);
        if ((!impact && !brakingMotion) || !Number.isFinite(sampledAt)) continue;
        if (!clock) clock = required("customAppRuntime");
        if (arm.boot !== clock.boot || clock.elapsedMilliseconds >= arm.until) {
            state.monitorStatus = "expired";
            return close(state, "expired");
        }
        const observed = clock.nowMilliseconds - Math.max(0, clock.elapsedMilliseconds - sampledAt);
        if (observed < arm.since) continue;
        if (impact) {
            state.monitorStatus = "triggered";
            return beginWarning(state, clock, axes, null, Math.round(observed));
        }
        state.motion = {at: sampledAt, axes};
        motionUpdated = true;
    }
    const firstBatch = state.monitorStatus === "starting" && input.samples.length > 0;
    if (firstBatch) state.monitorStatus = "live";
    if (firstBatch || motionUpdated) save(state);
    return done("Watching");
}

function onEvent(event) {
    const state = JSON.parse(JSON.stringify(context.state || {}));
    const input = event.payload || {};
    const run = state.incident;
    if (event.type === "impact.status") {
        return done(run && run.phase === "warning" ? "Cancellation countdown active" : state.armed ? "Armed" : "Disarmed");
    }
    if (event.type === "impact.cancel" || (event.type === "impact.close" && run && run.phase === "warning")) {
        return cancelWarning(state, input);
    }
    if (["impact.close", "impact.disarm", "app.stopped", "app.resume"].includes(event.type)) {
        return close(state, event.type === "app.resume" ? "interrupted" : "closed");
    }
    if (event.type === "location" && (!state.armed || (!state.settings.speed && !state.settings.braking))) return done("Watching");
    if (event.type === "app.sensor.samples") return onSensorSamples(state, input);
    const clock = required("customAppRuntime");
    if (event.type === "impact.save") {
        if (state.armed || active(run)) return rejected("Disarm or close the current alert before editing.");
        const next = settings(input);
        if (!next) return rejected("Enter a valid recipient, threshold, arming duration from 0.01 to 168 hours and whole-second countdown from 5 to 120 seconds. WhatsApp also needs photos, the exact chat name and storage name.");
        state.settings = next;
        save(state);
        return done("Settings saved");
    }
    if (event.type === "impact.preview") {
        return done("Preview only", {message: describe(clock.nowMilliseconds, [4 * G, 0, 0], 4, null)});
    }
    if (event.type === "impact.arm") {
        const options = settings(state.settings || {});
        if (state.armed) return rejected("Disarm before arming again.");
        if (input.confirm !== true || !options || active(run)) {
            return rejected("Save a recipient and confirm arming after closing any current alert.");
        }
        if (!clock.androidAccess.smsSend || !clock.notificationsEnabled || !clock.urgentChannelEnabled) {
            return rejected("Enable SMS and the urgent Dikciz notification channel first.");
        }
        if ((state.settings.whatsapp || state.settings.call) && !clock.androidAccess.accessibility) {
            return rejected("Enable Dikciz cross-app accessibility for WhatsApp or short calls.");
        }
        if (state.settings.call && !clock.androidAccess.phoneStateRead) {
            return rejected("Enable Android phone access for short calls.");
        }
        const readings = request("sensorReadings", {limit: 200});
        const sensors = readings.type === "result" ? readings.result.items.filter(item =>
            item.type === TYPE_LINEAR_ACCELERATION && item.available) : [];
        const sensor = sensors.find(item => item.wakeUp) || sensors[0];
        if (!sensor) {
            return rejected("The linear acceleration sensor is unavailable.");
        }
        if (state.settings.braking && !clock.androidAccess.locationPrecise) {
            return rejected("Enable precise Android location for hard-braking detection.");
        }
        state.speedFix = null;
        state.motion = null;
        state.settings = options;
        const start = required("customAppRuntime");
        const duration = Math.round(options.armHours * MS_PER_HOUR);
        state.armed = {boot: start.boot, until: start.elapsedMilliseconds + duration, since: start.nowMilliseconds, sensorId: sensor.sensorId};
        if (!startMotionLease(state, start)) {
            return rejected("Could not start a live motion sensor feed.");
        }
        return done("Armed. Waiting for raw sensor samples.");
    }
    if (event.type === "app.sensor.stopped") {
        const arm = state.armed;
        if (!arm || arm.sessionId !== input.sessionId || arm.boot !== clock.boot) return done("No matching monitor");
        if (input.state === "expired" && clock.elapsedMilliseconds < arm.until) {
            if (startMotionLease(state, clock)) return done("Motion monitoring renewed.");
            return rejected("Disarmed because the motion monitor could not renew.");
        }
        state.monitorStatus = input.state === "expired" ? "expired" : "sensor_feed_stalled";
        return close(state, state.monitorStatus);
    }
    if (event.type === "location") {
        if (state.armed.boot !== clock.boot || clock.elapsedMilliseconds >= state.armed.until) {
            state.monitorStatus = "expired";
            return close(state, "expired");
        }
        if (state.armed.since && Number.isFinite(event.timestampMilliseconds) &&
            event.timestampMilliseconds < state.armed.since) return done("Watching");
        return observeSpeed(state, clock, input,
            Number.isFinite(event.timestampMilliseconds) ? event.timestampMilliseconds : clock.nowMilliseconds);
    }
    if (!run || input.id !== run.id || run.boot !== clock.boot) return done("No matching incident");
    if (event.type === "impact.receipt" && run.sms) {
        run.sms = status(request("smsStatus", {idempotencyKey: "impact_" + run.id}), run.sms);
        save(state);
    }
    if (event.type === "impact.deliver" && run.phase === "warning" && clock.elapsedMilliseconds >= run.deadline) {
        return deliver(state, run);
    }
    if (run.phase !== "alert") return done("Alert closed");
    if (state.settings.photos && (event.type === "impact.front" || event.type === "impact.back")) {
        const facing = event.type === "impact.front" ? "front" : "back";
        if (run.photos[facing]) return done("Capture already attempted");
        run.photos[facing] = {state: "capturing"};
        save(state);
        run.photos[facing] = photo(run, facing);
        logStep(run, "impact_photo", run.photos[facing].filename ? "saved" : "failed",
            facing + ":" + (run.photos[facing].shareError || run.photos[facing].state));
        if (facing === "front" && schedule(run, "impact.back", NEXT_STEP_MS)) save(state);
        else if (state.settings.whatsapp) startWhatsApp(state, run);
        else finishDelivery(state, run);
    }
    if (event.type === "impact.whatsapp") nextWhatsAppStep(state, run);
    if (state.settings.call && event.type === "impact.call" && !run.call) placeShortCall(state, run);
    if (event.type === "impact.call.end" && run.call === "dial_requested") endShortCall(state, run);
    if (event.type === "impact.call.verify" && run.call === "ending") {
        const phone = request("callControl", {action: "state"});
        completeCall(state, run, phone.type === "result" && phone.result.inCall === false ? "ended" : "hang_up_unverified");
    }
    return done("Incident recorded");
}
