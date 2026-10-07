package org.fossify.home.dikciz

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
internal class DikcizHtmlWidgetView(
    context: Context,
    private var widget: HtmlHomeWidget,
    private val widgetContext: JSONObject,
    private val logger: DikcizLogger,
    private val executeCommand: (JSONObject) -> String,
    private val onContentHeightChanged: (Int) -> Unit,
    private val onWidgetLongClick: () -> Boolean,
    private val onRendererGone: () -> Unit,
) : FrameLayout(context) {
    private val webView = WebView(context)
    private var commandBridge: DikcizWebCommandBridge? = null
    private var currentState = JSONObject(widget.state.toString())
    private var messagePort: WebMessagePort? = null
    private var generation = INITIAL_GENERATION
    private var isReleased = false
    private var loadedDocumentBytes = EMPTY_DOCUMENT_BYTES

    init {
        clipChildren = true
        clipToPadding = true
        setBackgroundColor(Color.TRANSPARENT)
        addView(
            webView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        configureWebView()
        loadWidget()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    private fun configureWebView() {
        webView.apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            isLongClickable = true
            setOnLongClickListener { onWidgetLongClick() }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    logConsoleMessage(message)
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return request.isForMainFrame && request.url.host != LOCAL_HOST
                }

                override fun onPageFinished(view: WebView, url: String) {
                    val uri = Uri.parse(url)
                    if (
                        uri.scheme != HTTPS ||
                        uri.host != LOCAL_HOST ||
                        uri.getQueryParameter(GENERATION_QUERY) != generation.toString()
                    ) {
                        return
                    }
                    connectCommandBridge(view, generation)
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    logger.error(
                        EVENT_RENDERER_GONE,
                        htmlDiagnosticFields(REASON_RENDERER_GONE),
                    )
                    release()
                    onRendererGone()
                    return true
                }
            }
        }
    }

    private fun logConsoleMessage(message: ConsoleMessage) {
        val fields = htmlDiagnosticFields(message.message()).toMutableMap()
        fields[FIELD_CONSOLE_LEVEL] = message.messageLevel().name.lowercase()
        fields[FIELD_LINE_NUMBER] = message.lineNumber()
        fields[FIELD_SOURCE_ID] = message.sourceId()
        when (message.messageLevel()) {
            ConsoleMessage.MessageLevel.ERROR -> logger.error(EVENT_CONSOLE, fields)
            ConsoleMessage.MessageLevel.WARNING -> logger.warn(EVENT_CONSOLE, fields)
            else -> logger.debug(EVENT_CONSOLE, fields)
        }
    }

    private fun htmlDiagnosticFields(reason: String): Map<String, Any> {
        return mapOf(
            FIELD_REASON to reason,
            FIELD_SCRIPT_ID to widget.id,
            FIELD_SCRIPT_KIND to SCRIPT_KIND_HTML,
            FIELD_WIDGET_ID to widget.id,
        )
    }

    private fun loadWidget() {
        generation++
        releaseCommandBridge()
        val document = DikcizHtmlWidgetDocument.create(widget, widgetContext, currentState)
        loadedDocumentBytes = document.toByteArray(StandardCharsets.UTF_8).size
        webView.loadDataWithBaseURL(
            "$ORIGIN/$WIDGET_PATH_SEGMENT/${Uri.encode(widget.id)}?$GENERATION_QUERY=$generation",
            document,
            HTML_MIME,
            CHARSET,
            null,
        )
        logger.info(EVENT_WIDGET_LOADING, mapOf(FIELD_WIDGET_ID to widget.id))
    }

    private fun connectCommandBridge(view: WebView, documentGeneration: Long) {
        val channel = view.createWebMessageChannel()
        messagePort?.close()
        messagePort = channel[0]
        commandBridge?.close()
        commandBridge = DikcizWebCommandBridge(widget.id, executeCommand, logger) { response ->
            if (documentGeneration == generation) {
                channel[0].postMessage(WebMessage(response))
            }
        }
        channel[0].setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
            override fun onMessage(source: WebMessagePort, message: WebMessage) {
                if (documentGeneration != generation) {
                    return
                }
                message.data?.let { payload ->
                    if (consumeContentHeight(payload)) {
                        return
                    }
                    commandBridge?.receive(payload)
                }
            }
        })
        val api = context.assets.open(API_ASSET).bufferedReader().use { reader -> reader.readText() }
        view.evaluateJavascript(api) {
            if (documentGeneration == generation) {
                view.postWebMessage(WebMessage(BRIDGE_MESSAGE, arrayOf(channel[1])), Uri.parse(ORIGIN))
            }
        }
    }

    fun requestContentHeight(): Boolean {
        if (isReleased || messagePort == null) {
            return false
        }
        webView.evaluateJavascript(CONTENT_HEIGHT_REQUEST_JAVASCRIPT, null)
        return true
    }

    fun deliverAutomationEvent(event: JSONObject): Boolean {
        if (isReleased || messagePort == null) {
            return false
        }
        webView.evaluateJavascript(
            EVENT_DELIVERY_JAVASCRIPT_PREFIX + JSONObject.quote(event.toString()) + EVENT_DELIVERY_JAVASCRIPT_SUFFIX,
            null,
        )
        return true
    }

    fun acceptsAutomationEvent(event: DikcizAutomationEvent): Boolean {
        return widget.eventSubscriptions.any { subscription -> subscription.matches(event) }
    }

    fun deliverState(state: JSONObject): Boolean {
        currentState = JSONObject(state.toString())
        if (isReleased || messagePort == null) {
            return false
        }
        webView.evaluateJavascript(
            STATE_DELIVERY_JAVASCRIPT_PREFIX + JSONObject.quote(currentState.toString()) +
                STATE_DELIVERY_JAVASCRIPT_SUFFIX,
            null,
        )
        return true
    }

    fun patchDom(
        selector: String,
        values: JSONObject,
        onComplete: (String) -> Unit,
    ) {
        if (isReleased || messagePort == null) {
            onComplete(DikcizHtmlDomPatchOutcome.TargetNotRendered)
            return
        }
        webView.evaluateJavascript(domPatchJavaScript(selector, values)) { result ->
            onComplete(parseDomPatchOutcome(result))
        }
    }

    /**
     * Adds one saved block to this already-connected document without navigation.
     * The caller persists [updatedWidget] before this method runs, so a later full
     * configuration render reconstructs the same source.
     */
    fun appendBlock(
        updatedWidget: HtmlHomeWidget,
        blockInstanceID: String,
        markedFragment: String,
        onComplete: (String) -> Unit,
    ) {
        if (isReleased || messagePort == null) {
            onComplete(DikcizHtmlDomPatchOutcome.TargetNotRendered)
            return
        }
        webView.evaluateJavascript(
            blockAppendJavaScript(blockInstanceID, markedFragment),
        ) { result ->
            val outcome = parseDomPatchOutcome(result)
            if (outcome == DikcizHtmlDomPatchOutcome.Executed) {
                widget = updatedWidget
                loadedDocumentBytes = DikcizHtmlWidgetDocument.byteCount(updatedWidget, widgetContext)
            }
            onComplete(outcome)
        }
    }

    fun requestRendererCrash(): Boolean {
        if (isReleased) {
            return false
        }
        webView.loadUrl(RENDERER_CRASH_TEST_URL)
        return true
    }

    fun widgetID(): String = widget.id

    fun documentByteCount(): Int = loadedDocumentBytes

    private fun consumeContentHeight(payload: String): Boolean {
        val message = try {
            JSONObject(payload)
        } catch (_: JSONException) {
            return false
        }
        if (message.optString(TYPE_KEY) != CONTENT_HEIGHT_MESSAGE_TYPE) {
            return false
        }
        val heightValue = message.opt(HEIGHT_KEY) as? Number ?: return true
        val height = heightValue.toDouble()
        if (!height.isFinite() || height < MINIMUM_CONTENT_HEIGHT_CSS_PIXELS) {
            return true
        }
        onContentHeightChanged(cssPixelsToViewPixels(height))
        return true
    }

    private fun domPatchJavaScript(selector: String, values: JSONObject): String {
        val patch = JSONObject()
            .put(SELECTOR_KEY, selector)
            .put(VALUES_KEY, JSONObject(values.toString()))
        return """
            (function(serializedPatch) {
                const patch = JSON.parse(serializedPatch);
                const hasOwn = Object.prototype.hasOwnProperty;
                const values = patch.values;
                let target;
                try {
                    target = document.querySelector(patch.selector);
                } catch (error) {
                    return JSON.stringify({ outcome: "${DikcizHtmlDomPatchOutcome.SelectorInvalid}" });
                }
                if (!target) {
                    return JSON.stringify({ outcome: "${DikcizHtmlDomPatchOutcome.SelectorNotFound}" });
                }
                if (hasOwn.call(values, "value") && !("value" in target)) {
                    return JSON.stringify({ outcome: "${DikcizHtmlDomPatchOutcome.TargetPropertyUnavailable}" });
                }
                try {
                    if (hasOwn.call(values, "text")) target.textContent = values.text;
                    if (hasOwn.call(values, "html")) target.innerHTML = values.html;
                    if (hasOwn.call(values, "value")) target.value = values.value;
                    if (hasOwn.call(values, "className")) target.setAttribute("class", values.className);
                    if (hasOwn.call(values, "attributes")) {
                        Object.entries(values.attributes).forEach(([name, value]) => target.setAttribute(name, value));
                    }
                    return JSON.stringify({ outcome: "${DikcizHtmlDomPatchOutcome.Executed}" });
                } catch (error) {
                    return JSON.stringify({ outcome: "${DikcizHtmlDomPatchOutcome.OperationFailed}" });
                }
            })(${JSONObject.quote(patch.toString())});
        """.trimIndent()
    }

    private fun blockAppendJavaScript(
        blockInstanceID: String,
        markedFragment: String,
    ): String {
        val block = JSONObject()
            .put(BLOCK_INSTANCE_ID_KEY, blockInstanceID)
            .put(BLOCK_FRAGMENT_KEY, markedFragment)
        return """
            (function(serializedBlock) {
                const block = JSON.parse(serializedBlock);
                try {
                    const range = document.createRange();
                    range.selectNode(document.body);
                    document.body.append(range.createContextualFragment(block.fragment));
                    window.dispatchEvent(new CustomEvent("$BLOCK_APPEND_EVENT", {
                        detail: { instanceId: block.instanceId },
                    }));
                    requestAnimationFrame(() => requestAnimationFrame(() => {
                        const bottom = Math.max(
                            document.body.scrollHeight,
                            document.documentElement.scrollHeight,
                        );
                        window.scrollTo({ top: bottom, behavior: "$SCROLL_BEHAVIOR_SMOOTH" });
                    }));
                    return JSON.stringify({ outcome: "${DikcizHtmlDomPatchOutcome.Executed}" });
                } catch (error) {
                    return JSON.stringify({ outcome: "${DikcizHtmlDomPatchOutcome.OperationFailed}" });
                }
            })(${JSONObject.quote(block.toString())});
        """.trimIndent()
    }

    private fun parseDomPatchOutcome(result: String?): String {
        if (result == null) {
            return DikcizHtmlDomPatchOutcome.OperationFailed
        }
        val serializedOutcome = try {
            JSONTokener(result).nextValue() as? String
        } catch (_: JSONException) {
            null
        }
        if (serializedOutcome == null) {
            return DikcizHtmlDomPatchOutcome.OperationFailed
        }
        return try {
            JSONObject(serializedOutcome).optString(OUTCOME_KEY)
                .takeIf(DikcizHtmlDomPatchOutcome::isKnown)
                ?: DikcizHtmlDomPatchOutcome.OperationFailed
        } catch (_: JSONException) {
            DikcizHtmlDomPatchOutcome.OperationFailed
        }
    }

    @Suppress("DEPRECATION")
    private fun cssPixelsToViewPixels(height: Double): Int {
        return (height * webView.scale).roundToInt()
    }

    private fun release() {
        if (isReleased) {
            return
        }
        isReleased = true
        generation++
        releaseCommandBridge()
        webView.stopLoading()
        webView.destroy()
    }

    private fun releaseCommandBridge() {
        commandBridge?.close()
        commandBridge = null
        messagePort?.close()
        messagePort = null
    }

    private companion object {
        const val API_ASSET = "web/page-api.js"
        const val BLOCK_APPEND_EVENT = "dikciz-block-appended"
        const val BLOCK_FRAGMENT_KEY = "fragment"
        const val BLOCK_INSTANCE_ID_KEY = "instanceId"
        const val BRIDGE_MESSAGE = "dikciz-slots"
        const val CHARSET = "UTF-8"
        const val CONTENT_HEIGHT_MESSAGE_TYPE = "htmlContentHeight"
        const val CONTENT_HEIGHT_REQUEST_JAVASCRIPT = "window.dikciz?.fitContent?.()"
        const val EMPTY_DOCUMENT_BYTES = 0
        const val EVENT_CONSOLE = "html_widget_console"
        const val EVENT_DELIVERY_JAVASCRIPT_PREFIX = "window.dikciz?.receiveEvent?.("
        const val EVENT_DELIVERY_JAVASCRIPT_SUFFIX = ")"
        const val EVENT_RENDERER_GONE = "html_widget_renderer_gone"
        const val EVENT_WIDGET_LOADING = "html_widget_loading"
        const val FIELD_CONSOLE_LEVEL = "console_level"
        const val FIELD_LINE_NUMBER = "line_number"
        const val FIELD_REASON = "reason"
        const val FIELD_SCRIPT_ID = "script_id"
        const val FIELD_SCRIPT_KIND = "script_kind"
        const val FIELD_SOURCE_ID = "source_id"
        const val FIELD_WIDGET_ID = "widget_id"
        const val GENERATION_QUERY = "generation"
        const val HEIGHT_KEY = "height"
        const val HTML_MIME = "text/html"
        const val HTTPS = "https"
        const val INITIAL_GENERATION = 0L
        const val LOCAL_HOST = "appassets.androidplatform.net"
        const val MINIMUM_CONTENT_HEIGHT_CSS_PIXELS = 0.0
        const val ORIGIN = "https://appassets.androidplatform.net"
        const val OUTCOME_KEY = "outcome"
        const val REASON_RENDERER_GONE = "renderer_gone"
        const val RENDERER_CRASH_TEST_URL = "chrome://crash"
        const val SCROLL_BEHAVIOR_SMOOTH = "smooth"
        const val SCRIPT_KIND_HTML = "html"
        const val STATE_DELIVERY_JAVASCRIPT_PREFIX = "window.dikciz?.receiveState?.("
        const val STATE_DELIVERY_JAVASCRIPT_SUFFIX = ")"
        const val SELECTOR_KEY = "selector"
        const val TYPE_KEY = "type"
        const val VALUES_KEY = "values"
        const val WIDGET_PATH_SEGMENT = "widgets"
    }
}

internal object DikcizHtmlWidgetDocument {
    fun byteCount(widget: HtmlHomeWidget, widgetContext: JSONObject): Int {
        return create(widget, widgetContext).toByteArray(StandardCharsets.UTF_8).size
    }

    fun create(
        widget: HtmlHomeWidget,
        widgetContext: JSONObject,
        state: JSONObject = widget.state,
    ): String {
        val serializedState = state.toString().replace("<", ESCAPED_LESS_THAN)
        val context = widgetContext.toString().replace("<", ESCAPED_LESS_THAN)
        val heightStyle = if (widget.heightMode == HtmlWidgetHeightMode.Content) {
            DOCUMENT_CONTENT_HEIGHT_STYLE
        } else {
            DOCUMENT_FIXED_HEIGHT_STYLE
        }
        return """
            <!doctype html>
            <html>
              <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>html,body{margin:0;width:100%;$heightStyle;background:transparent;color:#e8eaed;font-family:sans-serif}</style>
                <style>${widget.css}</style>
                <style>*{-webkit-user-select:none!important;user-select:none!important;-webkit-touch-callout:none!important}</style>
              </head>
              <body>
                ${widget.html}
                <script>window.dikcizWidgetState={"${widget.id}":$serializedState};window.dikcizSelfWidget=$context;</script>
                <script>document.addEventListener("selectstart",event=>event.preventDefault(),true);document.addEventListener("contextmenu",event=>event.preventDefault(),true);</script>
                <script>${widget.javascript}</script>
              </body>
            </html>
        """.trimIndent()
    }

    private const val DOCUMENT_CONTENT_HEIGHT_STYLE = "min-height:0;"
    private const val DOCUMENT_FIXED_HEIGHT_STYLE = "min-height:100%;"
    private const val ESCAPED_LESS_THAN = "\\u003c"
}

internal object DikcizHtmlDomPatchOutcome {
    const val Executed = "executed"
    const val OperationFailed = "dom_operation_failed"
    const val SelectorInvalid = "selector_invalid"
    const val SelectorNotFound = "selector_not_found"
    const val TargetNotHtml = "target_not_html"
    const val TargetNotRendered = "target_not_rendered"
    const val TargetPropertyUnavailable = "target_property_unavailable"

    fun isKnown(outcome: String): Boolean {
        return outcome in KNOWN_OUTCOMES
    }

    private val KNOWN_OUTCOMES = setOf(
        Executed,
        OperationFailed,
        SelectorInvalid,
        SelectorNotFound,
        TargetPropertyUnavailable,
    )
}
