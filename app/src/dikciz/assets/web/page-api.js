(() => {
    const PORT_MESSAGE = "dikciz-slots";
    const READY_EVENT = "dikciz-ready";
    const AUTOMATION_EVENT = "dikciz-event";
    const STATE_EVENT = "dikciz-state";
    const MAX_PENDING = 32;
    const MAX_MESSAGE_BYTES = 1048576;
    const TIMEOUT_MS = 60000;
    const RESPONSE_GRACE_MS = 5000;
    const MAX_RESPONSE_TIMEOUT_MS = 2147483647;
    const RESPONSE_RECEIVED_TYPE = "commandResponseReceived";
    const CONTENT_HEIGHT_MESSAGE_TYPE = "htmlContentHeight";
    const pending = new Map();
    let port;
    let lastContentHeight = -1;
    let contentHeightObserver;
    let contentMutationObserver;
    const eventListeners = new Set();
    const selfWidgetDocument = window.dikcizSelfWidget ?? {};
    const pages = Object.freeze(selfWidgetDocument.pages ?? {});
    let currentItemStates = selfWidgetDocument.itemStates ?? {};
    let itemWriteQueue = Promise.resolve();
    const ITEM_STATE_EVENT = "dikciz-item-state";
    const ITEM_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_.-]{0,79}$/;
    const ITEM_FILE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_.-]{0,119}$/;
    let currentState = selfWidgetDocument.state ??
        window.dikcizWidgetState?.[selfWidgetDocument.id] ?? {};

    const failure = (code, message, response) => Object.assign(new Error(message), {
        code,
        ...(response ? {
            requestId: response.requestId,
            revision: response.revision,
            permissionRequest: response.permissionRequest,
            response,
        } : {}),
    });
    const contentHeight = () => Math.ceil(Math.max(
        document.documentElement.scrollHeight,
        document.body?.scrollHeight ?? 0,
    ));
    const reportContentHeight = () => {
        if (!port) return;
        const height = contentHeight();
        if (!Number.isSafeInteger(height) || height < 0 || height === lastContentHeight) return;
        lastContentHeight = height;
        port.postMessage(JSON.stringify({ type: CONTENT_HEIGHT_MESSAGE_TYPE, height }));
    };
    const scheduleContentHeightReport = () => requestAnimationFrame(reportContentHeight);
    const observeContentHeight = () => {
        if (!document.body || contentMutationObserver) return;
        contentMutationObserver = new MutationObserver(scheduleContentHeightReport);
        contentMutationObserver.observe(document.body, {
            childList: true,
            characterData: true,
            subtree: true,
        });
        if ("ResizeObserver" in window) {
            contentHeightObserver = new ResizeObserver(scheduleContentHeightReport);
            contentHeightObserver.observe(document.documentElement);
            contentHeightObserver.observe(document.body);
        }
        reportContentHeight();
    };
    const request = (document, options = {}) => new Promise((resolve, reject) => {
        if (!document || typeof document !== "object" || Array.isArray(document)) {
            reject(failure("validation_failed", "Command must be a JSON object"));
            return;
        }
        if (!port) {
            reject(failure("not_ready", "Wait for dikciz-ready before sending commands"));
            return;
        }
        if (pending.size >= MAX_PENDING) {
            reject(failure("busy", "Too many outstanding HTML commands"));
            return;
        }
        const requestId = crypto.randomUUID();
        const payload = JSON.stringify({ ...document, requestId });
        if (new TextEncoder().encode(payload).byteLength > MAX_MESSAGE_BYTES) {
            reject(failure("validation_failed", "HTML command exceeds the control request size limit"));
            return;
        }
        const operationTimeout = Number.isFinite(document.timeoutMilliseconds) ? document.timeoutMilliseconds : 0;
        const timeout = options.responseTimeoutMilliseconds ?? Math.max(TIMEOUT_MS, operationTimeout + RESPONSE_GRACE_MS);
        if (!Number.isSafeInteger(timeout) || timeout < 0 || timeout > MAX_RESPONSE_TIMEOUT_MS) {
            reject(failure("validation_failed", "Invalid responseTimeoutMilliseconds"));
            return;
        }
        const timer = timeout === 0 ? null : setTimeout(() => {
            pending.delete(requestId);
            reject(failure("timeout", "No reply; an already dispatched command may still complete"));
        }, timeout);
        pending.set(requestId, { resolve, reject, timer });
        try {
            port.postMessage(payload);
        } catch (error) {
            clearTimeout(timer);
            pending.delete(requestId);
            reject(error);
        }
    });
    const command = (type, fields = {}, options = {}) => request({ ...fields, type }, options).then(response => {
        if (response.type === "error") throw failure(response.code, response.message, response);
        return response.result;
    });

    const dispatch = document => {
        const actions = Array.isArray(document) ? document : document?.actions;
        if (!Array.isArray(actions) || actions.length === 0) {
            return Promise.reject(failure("validation_failed", "dispatch requires a non-empty actions array"));
        }
        const fields = { actions };
        if (!Array.isArray(document) && Object.prototype.hasOwnProperty.call(document, "autoRequestPermissions")) {
            fields.autoRequestPermissions = document.autoRequestPermissions;
        }
        return command("automationDispatch", fields);
    };
    const emitEvent = (event, payload = {}, coalescingKey) => {
        const action = { type: "emitEvent", event, payload };
        if (coalescingKey !== undefined) action.coalescingKey = coalescingKey;
        return dispatch({ actions: [action] });
    };
    const patchWidget = (address, values) => dispatch({
        actions: [{ type: "patchWidget", widgetAddress: address, values }],
    });
    const patchWidgetState = (address, values) => patchWidget(address, { state: values });
    const patchDom = (address, selector, values) => dispatch({
        actions: [{ type: "patchDom", widgetAddress: address, selector, values }],
    });
    const widget = address => command("widgetGet", { widgetAddress: address });
    const home = () => command("homeGet");
    const onEvent = listener => {
        if (typeof listener !== "function") {
            throw new TypeError("dikciz.onEvent requires a function");
        }
        eventListeners.add(listener);
        return () => eventListeners.delete(listener);
    };
    const receiveEvent = serializedEvent => {
        const event = JSON.parse(serializedEvent);
        for (const listener of eventListeners) listener(event);
        window.dispatchEvent(new CustomEvent(AUTOMATION_EVENT, { detail: event }));
    };
    const receiveState = serializedState => {
        currentState = JSON.parse(serializedState);
        window.dikcizWidgetState = {
            ...(window.dikcizWidgetState ?? {}),
            [selfWidgetDocument.id]: currentState,
        };
        window.dispatchEvent(new CustomEvent(STATE_EVENT, { detail: currentState }));
    };
    const receiveItemStates = serializedStates => {
        currentItemStates = JSON.parse(serializedStates);
        window.dispatchEvent(new CustomEvent(ITEM_STATE_EVENT, {detail:currentItemStates}));
    };
    const item = id => {
        if (typeof id !== "string" || !ITEM_ID_PATTERN.test(id)) throw failure("validation_failed", "Invalid HTML item instance ID");
        const storagePath = `${selfWidgetDocument.storagePath}/items/${id}`;
        const filePath = name => {
            if (typeof name !== "string" || !ITEM_FILE_PATTERN.test(name)) throw failure("validation_failed", "Item file name must be a basename");
            return `${storagePath}/${name}`;
        };
        return Object.freeze(Object.defineProperties({
            id,
            storagePath,
            getState: async () => (await selfWidgetApi.get()).itemStates?.[id] ?? {},
            patchState: values => {
                const update = async () => {
                    const saved = await selfWidgetApi.get();
                    return selfWidgetApi.patch({itemStates:{...saved.itemStates, [id]:values}});
                };
                const pendingWrite = itemWriteQueue.then(update, update);
                itemWriteQueue = pendingWrite;
                return pendingWrite;
            },
            readFile: (name, options = {}) => command("fileRead", {...options, path:filePath(name)}),
            writeFile: async (name, dataBase64, options = {}) => {
                if (name === "state.json") throw failure("validation_failed", "Use patchState for state.json");
                const path = filePath(name);
                const saved = await selfWidgetApi.get();
                if (!Object.prototype.hasOwnProperty.call(saved.itemStates ?? {}, id)) await item(id).patchState({});
                await command("fileMkdir", {path:storagePath, parents:true});
                return command("fileWrite", {overwrite:true, ...options, path, dataBase64});
            },
        }, {state:{enumerable:true, get:() => currentItemStates[id] ?? {}}}));
    };
    const selfWidgetApi = Object.freeze(Object.defineProperties({
        ...selfWidgetDocument,
        get: () => widget(selfWidgetDocument.address),
        item,
        itemFor: element => {
            const id = element?.closest?.("[data-dikciz-item-id]")?.getAttribute("data-dikciz-item-id");
            return item(id);
        },
        patch: values => patchWidget(selfWidgetDocument.address, values),
        patchDom: (selector, values) => patchDom(selfWidgetDocument.address, selector, values),
        patchState: values => dispatch({ actions: [{ type: "patchState", values }] }),
    }, {
        state: { enumerable: true, get: () => currentState },
        itemStates: { enumerable: true, get: () => currentItemStates },
    }));

    window.selfWidget = selfWidgetApi;
    window.dikciz = Object.freeze({
        version: 4,
        command,
        request,
        dispatch,
        emitEvent,
        fitContent: () => {
            lastContentHeight = -1;
            reportContentHeight();
        },
        home,
        onEvent,
        patchWidget,
        patchWidgetState,
        patchDom,
        pages: () => pages,
        receiveEvent,
        receiveState,
        receiveItemStates,
        selfWidget: selfWidgetApi,
        widget,
    });
    window.addEventListener("message", event => {
        if (event.data !== PORT_MESSAGE || !event.ports.length) return;
        port = event.ports[0];
        port.addEventListener("message", message => {
            const response = JSON.parse(message.data);
            const entry = pending.get(response.requestId);
            if (!entry) return;
            pending.delete(response.requestId);
            clearTimeout(entry.timer);
            entry.resolve(response);
            // Promise handlers must consume the reply before a lifecycle action destroys this document.
            setTimeout(() => {
                if (!port) return;
                try {
                    port.postMessage(JSON.stringify({ type: RESPONSE_RECEIVED_TYPE, requestId: response.requestId }));
                } catch (error) {
                    console.warn("Dikciz response acknowledgement failed", error.name);
                }
            }, 0);
        });
        port.start();
        observeContentHeight();
        window.dispatchEvent(new Event(READY_EVENT));
    });
    window.addEventListener("pagehide", () => {
        port = undefined;
        for (const entry of pending.values()) {
            clearTimeout(entry.timer);
            entry.reject(failure("page_closed", "The HTML page was closed"));
        }
        pending.clear();
        contentHeightObserver?.disconnect();
        contentMutationObserver?.disconnect();
        contentHeightObserver = undefined;
        contentMutationObserver = undefined;
    });
})();
