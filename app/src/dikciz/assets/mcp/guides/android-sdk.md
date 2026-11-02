# Public Android SDK

Prefer dedicated native commands when they cover the task. The SDK bridge adds exact public Android overloads through the same executor on every binding. The pinned catalogue is API 36; discovery is not a permission grant or proof every API runs on this phone.

1. Call dikciz_android_api_discover. Omit className for classes, or set an exact name for members and inherited overloads. query is a literal filter. Follow hasMore with offset plus returned item count.
2. Inspect bridgePermitted, runtimeAvailable, parameters, nullability and permission annotations. Copy the exact member ID. Never guess an overload.
3. Use dikciz_android_api_read for approved reads. Other calls require dikciz_android_api_invoke and a unique idempotencyKey. The arguments array is required, including [].

Example read arguments:

```json
{"className":"android.os.BatteryManager","member":"getIntProperty(int)","target":"service:batterymanager","arguments":[4]}
```

Targets: context, activity, service:NAME or an earlier objectRef. Static methods and constructors omit target. IDs: method(type,...), new(type,...), field:NAME; writable fields use set:NAME. The BatteryManager example reads Android BATTERY_PROPERTY_CAPACITY.

Values accept exact primitives, null, enum names and arrays. Tags: {"objectRef":"ID"}, {"className":"PUBLIC_SDK_CLASS"}, {"long":"SIGNED_DECIMAL"}, {"bytesBase64":"BASE64"}, {"arrayType":"COMPONENT_TYPE","values":[]}. No nested calls or arbitrary object maps. Nullability and integer bounds are validated.

Read-only agents cannot see write tools or mutating/unaudited SDK members. The executor also blocks forged writes and autoRequestPermissions:true with read_only_violation. androidApiRead independently enforces the approved member policy before resolving arguments, targets or permissions. Unknown members default to write, regardless of their names.

Invocation timeout defaults to 30000ms; timeoutMilliseconds accepts 1..300000. thread is auto, main or background. Auto follows SDK thread annotations. A timeout means completion is unknown. Reuse the identical write key to observe its original result without repeating it. Keys are process-owned; use jobStart for durable submission/results. SDK completed means the method returned, not that an asynchronous Android effect finished.

Object handles survive activity recreation but not process death. androidApiObjects pages indexed arrays/lists/maps/text; text offsets are UTF-16 units. Previews mark omitted contents explicitly. At most 256 handles are retained. Unregister SDK listeners and stop hardware sessions, then release handles with androidApiRelease. close:true calls AutoCloseable.close when supported; release alone does not undo effects.

androidApiCallback creates an SDK interface proxy or public non-final callback subclass. Select constructor/arguments when needed. returns maps exact overload IDs to explicit constant non-void results. Poll callback records with androidApiObjects objectRef/afterSequence. There are 256 retained events per callback; inspect droppedEvents and gap. The same records emit custom event android_api_callback. Callbacks never execute model-supplied commands automatically.

Android permissions, foreground rules, optional SDK modules, roles and hardware remain authoritative. Known missing grants use existing opt-in owner review. Reflection, raw files/databases/providers, private app internals and dynamic loading require dedicated protected adapters. bridgePermitted:false states that boundary explicitly. No silent grants or simulated success.
