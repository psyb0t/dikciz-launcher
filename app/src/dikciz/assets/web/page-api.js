(() => {
    const PORT_MESSAGE = "dikciz-slots";
    const READY_EVENT = "dikciz-ready";
    const AUTOMATION_EVENT = "dikciz-event";
    const STATE_EVENT = "dikciz-state";
    const MAX_PENDING = 32;
    const MAX_MESSAGE_CHARACTERS = 65536;
    const TIMEOUT_MS = 60000;
    const CONTENT_HEIGHT_MESSAGE_TYPE = "htmlContentHeight";
    const pending = new Map();
    let port;
    let lastContentHeight = -1;
    let contentHeightObserver;
    let contentMutationObserver;
    const eventListeners = new Set();
    const selfWidgetDocument = window.dikcizSelfWidget ?? {};
    const pages = Object.freeze(selfWidgetDocument.pages ?? {});
    let currentState = selfWidgetDocument.state ??
        window.dikcizWidgetState?.[selfWidgetDocument.id] ?? {};

    const failure = (code, message) => Object.assign(new Error(message), { code });
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
    const command = (type, fields = {}) => new Promise((resolve, reject) => {
        if (!port) {
            reject(failure("not_ready", "Wait for dikciz-ready before sending commands"));
            return;
        }
        if (pending.size >= MAX_PENDING) {
            reject(failure("busy", "Too many outstanding HTML commands"));
            return;
        }
        const requestId = crypto.randomUUID();
        const payload = JSON.stringify({ ...fields, type, requestId });
        if (payload.length > MAX_MESSAGE_CHARACTERS) {
            reject(failure("validation_failed", "HTML command exceeds 65536 characters"));
            return;
        }
        const timer = setTimeout(() => {
            pending.delete(requestId);
            reject(failure("timeout", "No reply; an already dispatched command may still complete"));
        }, TIMEOUT_MS);
        pending.set(requestId, { resolve, reject, timer });
        try {
            port.postMessage(payload);
        } catch (error) {
            clearTimeout(timer);
            pending.delete(requestId);
            reject(error);
        }
    });

    const dispatch = document => {
        const actions = Array.isArray(document) ? document : document?.actions;
        if (!Array.isArray(actions) || actions.length === 0) {
            return Promise.reject(failure("validation_failed", "dispatch requires a non-empty actions array"));
        }
        return command("automationDispatch", { actions });
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
    const selfWidgetApi = Object.freeze(Object.defineProperties({
        ...selfWidgetDocument,
        get: () => widget(selfWidgetDocument.address),
        patch: values => patchWidget(selfWidgetDocument.address, values),
        patchDom: (selector, values) => patchDom(selfWidgetDocument.address, selector, values),
        patchState: values => dispatch({ actions: [{ type: "patchState", values }] }),
    }, {
        state: { enumerable: true, get: () => currentState },
    }));

    window.selfWidget = selfWidgetApi;
    window.dikciz = Object.freeze({
        version: 4,
        command,
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
            if (response.type === "error") {
                entry.reject(failure(response.code, response.message));
                return;
            }
            entry.resolve(response.result);
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
