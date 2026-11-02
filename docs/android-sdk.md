# Public Android SDK bridge

The shared Android SDK bridge supplements the dedicated [native commands](native-device-control.md). `androidApiDiscover` indexes the pinned API 36 public SDK, including exact overloads, positional types, nullability, permission annotations and inheritance. A catalogue entry does not grant permissions or prove that a method exists on the running Android version. Inspect `bridgePermitted` and `runtimeAvailable` before execution. Hidden and private APIs are outside this contract.

MCP, HTTP, WebSocket, HTML and Lua execute these commands through the same native target. MCP names use `dikciz_android_api_` followed by `discover`, `read`, `invoke`, `objects`, `callback` or `release`. The [command reference](command-reference.md) records request schemas. The packaged guide is `dikciz://docs/guides/android-sdk`.

## Discover before calling

Omit `className` to list classes, or supply an exact name to list its public members and inherited overloads. `query` is a literal case-insensitive filter. Lists use `offset`, `limit` and `hasMore`; the default page is 50, the maximum 200. Continue with offset plus the number of returned items. Method IDs include erased positional parameter types, such as `getPhoneNumber(int)`. Constructors use `new(...)`; field reads use `field:NAME`. A mutable field is set with `set:NAME` through the writable invocation command.

```json
{"type":"androidApiDiscover","className":"android.os.BatteryManager","query":"getIntProperty"}
```

```json
{"type":"androidApiRead","className":"android.os.BatteryManager","member":"getIntProperty(int)","target":"service:batterymanager","arguments":[4]}
```

Battery property 4 is Android BATTERY_PROPERTY_CAPACITY. Prefer discovering constants over assuming other numeric SDK values. Existing `deviceState` provides a higher-level battery result.

Instance targets are `context`, `activity`, `service:NAME`, or an `objectRef` returned by an earlier call. Use the public SDK class name, even when Android returns a hidden implementation subclass. Static members and constructors reject a target. `arguments` is always required, including an empty array for no arguments. No overload is guessed.

Primitive values, explicit nulls, enum names and arrays are accepted according to the exact parameter type. Object tags are `{"objectRef":"ID"}`, `{"className":"PUBLIC_SDK_CLASS"}`, `{"long":"SIGNED_DECIMAL"}`, `{"bytesBase64":"BASE64"}` and `{"arrayType":"COMPONENT_TYPE","values":[...]}`. Nested calls and arbitrary object maps are rejected. Null is rejected for primitives and explicitly non-null parameters. Integral narrowing checks overflow. Results outside JavaScript integer precision use the decimal long tag.

## Read-only access

The conversation access flag is executor metadata, not an argument supplied by the model. Every call rechecks saved access, so enabling read-only access during a running turn blocks subsequent writes immediately. A settings patch containing only readOnly:true is accepted during an active turn; other settings changes require stopping it first. Read-only agents see only approved read tools. SDK discovery also removes mutating and unaudited members. `androidApiRead` independently requires its exact member to be approved, even for a writable caller. A write sent through that command returns `read_only_violation` before resolving its target, arguments or permissions. Setters, writable invocations, callback creation and handle release are hidden and blocked in read-only conversations. `autoRequestPermissions: true` is also rejected in read-only mode.

New members default to write access. The explicit policy is [read-policy.json](../app/src/dikciz/sdk/read-policy.json). Names beginning with `get`, `is` or `read` do not imply safety. Approved constructors create data-only output holders, such as ActivityManager.MemoryInfo. Reading an SDK output holder can update that in-memory holder without changing phone configuration.

## Invocation and outcomes

`androidApiInvoke` requires an `idempotencyKey`. Identical requests reuse the first invocation; changed arguments with the same key fail. Pre-dispatch validation and annotated missing-permission failures do not reserve a key. Android rejections and completed attempts remain cached. Keys remain in memory until process exit, with a 1024-entry capacity. They survive activity recreation. Use [command jobs](command-jobs.md) when durable submission/results are required. Neither layer automatically repeats an interrupted Android side effect after process death.

The wait timeout defaults to 30000 milliseconds and accepts 1 through 300000. `thread` is `auto`, `main` or `background`. Auto follows SDK UiThread/MainThread annotations, which are not present on every platform method. At most two background SDK workers execute concurrently, with a bounded queue. A timeout reports unknown completion. Android operations already running cannot be rolled back; retrying the identical write key observes the original operation. An SDK `completed` state means the method returned. It does not turn an asynchronous submission into a verified external effect.

SDK exceptions become stable access-denied, unsupported, argument-rejected, unavailable or failed outcomes. Exception text and argument values stay out of logs. Permission annotations describe declared requirements; Android is authoritative about runtime grants, AppOps, roles, foreground eligibility and privileged permissions. Known permission families use the same opt-in owner review as dedicated commands. No API grants access silently.

## Handles, callbacks and cleanup

Nonprimitive results receive process-owned handles. There are at most 256 live handles. They survive launcher activity recreation, but not process death or APK replacement. Release unused handles with `androidApiRelease`. `close: true` explicitly calls AutoCloseable.close when supported. Unregister listeners, stop capture and close sessions through the corresponding SDK methods before releasing their handles. Releasing a reference does not undo Android side effects.

Inline list/array previews contain at most 16 elements; longer text includes an explicit preview marker. `androidApiObjects` pages complete indexed array/list/map or text contents by objectRef. Text offsets count UTF-16 code units. Large bytes remain available through indexed reads. List contents may change between calls; this is not a transactional snapshot.

`androidApiCallback` creates a public SDK interface proxy or a generated subclass of a public non-final SDK callback class. Select its exact constructor and arguments when `new()` is insufficient. `returns` must provide explicit constant values for non-void callback overloads. Callback methods record events, never execute supplied commands. Read them with `androidApiObjects`, `objectRef` and `afterSequence`. Each callback retains at most 256 events; `droppedEvents` and `gap` report loss. The automation bus also receives the custom event `android_api_callback`, with objectRef, overload, sequence and arguments. Unregister before release.

## Protected boundaries

The generic bridge excludes reflection, dynamic class loading, raw filesystem/database/content-provider access and launcher internals. Protected credential paths return android_api_protected_path without suggesting that an Android permission grant can bypass the restriction. Use the existing protected file, provider, shell and HTML commands for those tasks. Public Context/Activity access is restricted to selected service, permission and resource getters. SDK class discovery still describes excluded APIs honestly through `bridgePermitted: false`. Android version, optional modules, hardware, permissions and callback-constructor restrictions can prevent execution. This bridge does not claim unrestricted Android access or complete device testing of every catalogue member.

## Source and dependency verification

[source.json](../app/src/dikciz/sdk/source.json) pins the official AOSP public signatures by commit and SHA-256. `make dikciz-android-sdk` regenerates deterministic indexed assets; `ANDROID_SDK_SOURCE=relative/path/android.txt` uses a supplied source file while enforcing the same checksum. `make dikciz-android-sdk-test` checks parsing, exact types, policy entries, checksums, offsets and repeatability. Do not edit generated assets by hand.

Callback subclasses use LinkedIn DexMaker 2.28.6 and its repackaged Dalvik DX 9.0.0_r3 dependency, both Apache-2.0. [Pinned dependency metadata](../gradle/dikciz-sdk-dependencies.json) records the downloaded artifacts and hashes. Dikciz debug, release and minified builds verify the JAR checksums before compiling. SDK signatures come from the [AOSP SDK repository](https://android.googlesource.com/platform/prebuilts/sdk/).
