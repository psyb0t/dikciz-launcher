package org.fossify.home.dikciz

import org.json.JSONObject

internal object DikcizAndroidSdkCommands {
    const val DISCOVER = "androidApiDiscover"
    const val READ = "androidApiRead"
    const val INVOKE = "androidApiInvoke"
    const val OBJECTS = "androidApiObjects"
    const val RELEASE = "androidApiRelease"
    const val CALLBACK = "androidApiCallback"
    const val MAXIMUM_ARGUMENTS = 64
    private fun text(description: String, maximum: Int = 256) = JSONObject()
        .put("type", "string").put("maxLength", maximum).put("description", description)
    private val PAGE = mapOf(
        "limit" to JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 200).put("default", 50),
        "offset" to JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 1_000_000).put("default", 0),
    )
    private val CALL = mapOf(
        "className" to text("Exact public className from androidApiDiscover."),
        "member" to text("Exact discovered overload id, including parameter types. Constructors use new(...); fields use field:NAME.", 2048),
        "timeoutMilliseconds" to JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 300_000).put("default", 30_000),
        "thread" to text("auto, main or background. Auto follows SDK MainThread/UiThread annotations.", 16),
        "target" to text("Omit for constructors and static members. Otherwise context, activity, service:NAME or an objectRef from a previous result."),
        "arguments" to JSONObject().put("type", "array").put("maxItems", MAXIMUM_ARGUMENTS).put("items", JSONObject())
            .put("description", "Required ordered arguments, including explicit nulls. Use {objectRef:ID}, {className:SDK_CLASS}, {bytesBase64:BASE64}, {long:DECIMAL} or {arrayType:SDK_TYPE,values:[...]}. No inferred overloads or nested calls."),
    )
    val DEFINITIONS = listOf(
        DikcizNativeCommands.Definition(
            DISCOVER, "dikciz_android_api_discover",
            "Discover pinned public Android SDK classes, exact signatures, permission annotations and runtime availability. No API is invoked. Omit className for a page of classes; supply it for members, including inherited overloads. Read-only callers see only approved read members. Unknown members default to write access. Discovery is a catalogue, not a claim that Android grants every permission or supports every hardware feature.",
            PAGE + mapOf("className" to text("Optional exact class, otherwise list classes."), "query" to text("Case-insensitive literal class/member filter.", 256)),
            readOnly = true,
        ),
        DikcizNativeCommands.Definition(
            READ, "dikciz_android_api_read",
            "Invoke one explicitly approved SDK read operation. Always rejects mutating or unaudited members with read_only_violation before resolving arguments or invoking Android, even outside read-only conversations. Discover the exact member and parameter list first. Permission annotations are advisory; Android remains authoritative.",
            CALL, setOf("className", "member", "arguments"), readOnly = true,
        ),
        DikcizNativeCommands.Definition(
            INVOKE, "dikciz_android_api_invoke",
            "Invoke a public SDK method, constructor or field read/set with writable access. Select an exact discovered member. Set a mutable field with set:NAME and one argument. Android permissions, roles, hardware and foreground restrictions still apply. Raw filesystem, private app storage, reflection and dynamic code APIs are excluded; use the dedicated protected file/shell APIs. Wrap long operations in jobStart. No automatic replay after process death.",
            CALL + mapOf("idempotencyKey" to text("Unique process-scoped key. Same payload returns its original result; changed payload fails. For durable submission use jobStart.", 80)),
            setOf("className", "member", "arguments", "idempotencyKey"), readOnly = false,
        ),
        DikcizNativeCommands.Definition(
            OBJECTS, "dikciz_android_api_objects",
            "Read one page of process-owned SDK object handles, indexed array/list/map/text contents or callback events for objectRef. Poll using afterSequence. Handles and callback events do not survive process restart. Callbacks never execute commands automatically.",
            PAGE + mapOf("objectRef" to text("Optional callback handle."), "afterSequence" to JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 9_007_199_254_740_991L)),
            readOnly = true,
        ),
        DikcizNativeCommands.Definition(
            RELEASE, "dikciz_android_api_release",
            "Release one SDK object handle. close=true explicitly calls AutoCloseable.close when supported. Unregister listeners and stop hardware sessions through their SDK cleanup methods before release. Release does not pretend to undo Android side effects.",
            mapOf("objectRef" to text("Object handle."), "close" to JSONObject().put("type", "boolean").put("default", false)),
            setOf("objectRef"), readOnly = false,
        ),
        DikcizNativeCommands.Definition(
            CALLBACK, "dikciz_android_api_callback",
            "Create a public SDK interface callback that records typed invocations for androidApiObjects. returns maps exact callback overload ids to constant return values; unspecified non-void callbacks are rejected. No scripts run on Binder threads. Register with a writable SDK call and unregister before releasing. Public non-final SDK callback classes use a generated subclass. Constructor defaults to new(); select an exact SDK constructor when arguments are needed.",
            mapOf("className" to CALL.getValue("className"), "returns" to JSONObject().put("type", "object"), "constructor" to text("Exact SDK constructor id, defaults to new().", 2048), "arguments" to CALL.getValue("arguments")),
            setOf("className"), readOnly = false,
        ),
    )
    val TYPES = DEFINITIONS.map { it.type }.toSet()

    fun validate(request: JSONObject) {
        if (request.optString("thread", "auto") !in setOf("auto", "main", "background")) DikcizNativeCommands.invalid("Invalid SDK invocation thread")
        request.optJSONArray("arguments")?.let {
            if (it.length() > MAXIMUM_ARGUMENTS) DikcizNativeCommands.invalid("At most 64 SDK arguments are accepted")
        }
        request.optString("idempotencyKey").let {
            if (request.has("idempotencyKey") && !Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}").matches(it)) {
                DikcizNativeCommands.invalid("Invalid idempotencyKey")
            }
        }
    }
}
