# Command reference

<!-- Regenerate with make dikciz-command-docs from captured tools/list discovery. -->

This reference contains 215 discovered MCP tools and their complete input schemas. Discovery from the installed build is authoritative. Transport-independent procedures, Android permissions and results are described in the [automation guide](automation-control.md), [documentation index](index.md) and focused task guides. MCP tools use the same ordinary commands as HTTP, WebSocket, HTML and Lua.

Schemas include nested fields, required values, defaults and bounds. autoRequestPermissions requests owner review; it never grants access or retries the operation. Inspect returned state and errors before continuing.

## dikciz_accessibility_action

Run one supported action on a node from an unexpired accessibility snapshot.

```json
{
  "type": "object",
  "properties": {
    "snapshotId": {
      "type": "string",
      "description": "Opaque snapshot ID returned by dikciz_accessibility_snapshot.",
      "maximum": 36
    },
    "nodeId": {
      "type": "string",
      "description": "Node ID from the same accessibility snapshot.",
      "maximum": 128
    },
    "action": {
      "type": "string",
      "description": "Supported node action: click, longClick, focus, clearFocus, scrollForward, scrollBackward, setText, or select.",
      "maximum": 32
    },
    "text": {
      "type": "string",
      "description": "Required only for setText. Omit for every other action.",
      "maximum": 8192
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "snapshotId",
    "nodeId",
    "action"
  ]
}
```

## dikciz_accessibility_snapshot

Read a bounded snapshot of the active Android accessibility tree. The owner must first enable Dikciz cross-app automation in Android Accessibility settings.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_activity_cancel

Request best-effort thread interruption of one running activity. Does not roll back completed effects or prove Android stopped work. Poll activityRead. Owner only.

```json
{
  "type": "object",
  "properties": {
    "activityId": {
      "type": "string",
      "maxLength": 36
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "activityId"
  ],
  "additionalProperties": false
}
```

## dikciz_activity_read

Read persistent command metadata without arguments or payloads. Owner only. Includes actor, request ID, duration and completed/rejected/failed/interrupted/running state. Interrupted outcomes may be unknown. Seven-day retention and bounded pagination.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000
    },
    "actor": {
      "type": "string",
      "maxLength": 80
    },
    "outcome": {
      "type": "string",
      "maxLength": 32
    },
    "mutatingOnly": {
      "type": "boolean",
      "default": false
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_add_widget

Place one new top-level item in the first fitting free cells of the selected page grid. Rejects with page_full and writes nothing when no space fits. Use dikciz_widget_move and dikciz_widget_resize to place it afterwards.

```json
{
  "type": "object",
  "properties": {
    "widgetType": {
      "type": "string",
      "description": "Item type to create: html, appGroup, or scriptDashboard.",
      "maximum": 256
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "widgetType"
  ]
}
```

## dikciz_android_api_callback

Create a public SDK interface callback that records typed invocations for androidApiObjects. returns maps exact callback overload ids to constant return values; unspecified non-void callbacks are rejected. No scripts run on Binder threads. Register with a writable SDK call and unregister before releasing. Public non-final SDK callback classes use a generated subclass. Constructor defaults to new(); select an exact SDK constructor when arguments are needed.

```json
{
  "type": "object",
  "properties": {
    "className": {
      "type": "string",
      "maxLength": 256,
      "description": "Exact public className from androidApiDiscover."
    },
    "returns": {
      "type": "object"
    },
    "constructor": {
      "type": "string",
      "maxLength": 2048,
      "description": "Exact SDK constructor id, defaults to new()."
    },
    "arguments": {
      "type": "array",
      "maxItems": 64,
      "items": {},
      "description": "Required ordered arguments, including explicit nulls. Use {objectRef:ID}, {className:SDK_CLASS}, {bytesBase64:BASE64}, {long:DECIMAL} or {arrayType:SDK_TYPE,values:[...]}. No inferred overloads or nested calls."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "className"
  ],
  "additionalProperties": false
}
```

## dikciz_android_api_discover

Discover pinned public Android SDK classes, exact signatures, permission annotations and runtime availability. No API is invoked. Omit className for a page of classes; supply it for members, including inherited overloads. Read-only callers see only approved read members. Unknown members default to write access. Discovery is a catalogue, not a claim that Android grants every permission or supports every hardware feature.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "className": {
      "type": "string",
      "maxLength": 256,
      "description": "Optional exact class, otherwise list classes."
    },
    "query": {
      "type": "string",
      "maxLength": 256,
      "description": "Case-insensitive literal class/member filter."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_android_api_invoke

Invoke a public SDK method, constructor or field read/set with writable access. Select an exact discovered member. Set a mutable field with set:NAME and one argument. Android permissions, roles, hardware and foreground restrictions still apply. Raw filesystem, private app storage, reflection and dynamic code APIs are excluded; use the dedicated protected file/shell APIs. Wrap long operations in jobStart. No automatic replay after process death.

```json
{
  "type": "object",
  "properties": {
    "className": {
      "type": "string",
      "maxLength": 256,
      "description": "Exact public className from androidApiDiscover."
    },
    "member": {
      "type": "string",
      "maxLength": 2048,
      "description": "Exact discovered overload id, including parameter types. Constructors use new(...); fields use field:NAME."
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 300000,
      "default": 30000
    },
    "thread": {
      "type": "string",
      "maxLength": 16,
      "description": "auto, main or background. Auto follows SDK MainThread/UiThread annotations."
    },
    "target": {
      "type": "string",
      "maxLength": 256,
      "description": "Omit for constructors and static members. Otherwise context, activity, service:NAME or an objectRef from a previous result."
    },
    "arguments": {
      "type": "array",
      "maxItems": 64,
      "items": {},
      "description": "Required ordered arguments, including explicit nulls. Use {objectRef:ID}, {className:SDK_CLASS}, {bytesBase64:BASE64}, {long:DECIMAL} or {arrayType:SDK_TYPE,values:[...]}. No inferred overloads or nested calls."
    },
    "idempotencyKey": {
      "type": "string",
      "maxLength": 80,
      "description": "Unique process-scoped key. Same payload returns its original result; changed payload fails. For durable submission use jobStart."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "className",
    "member",
    "arguments",
    "idempotencyKey"
  ],
  "additionalProperties": false
}
```

## dikciz_android_api_objects

Read one page of process-owned SDK object handles, indexed array/list/map/text contents or callback events for objectRef. Poll using afterSequence. Handles and callback events do not survive process restart. Callbacks never execute commands automatically.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "objectRef": {
      "type": "string",
      "maxLength": 256,
      "description": "Optional callback handle."
    },
    "afterSequence": {
      "type": "integer",
      "minimum": 0,
      "maximum": 9007199254740991
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_android_api_read

Invoke one explicitly approved SDK read operation. Always rejects mutating or unaudited members with read_only_violation before resolving arguments or invoking Android, even outside read-only conversations. Discover the exact member and parameter list first. Permission annotations are advisory; Android remains authoritative.

```json
{
  "type": "object",
  "properties": {
    "className": {
      "type": "string",
      "maxLength": 256,
      "description": "Exact public className from androidApiDiscover."
    },
    "member": {
      "type": "string",
      "maxLength": 2048,
      "description": "Exact discovered overload id, including parameter types. Constructors use new(...); fields use field:NAME."
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 300000,
      "default": 30000
    },
    "thread": {
      "type": "string",
      "maxLength": 16,
      "description": "auto, main or background. Auto follows SDK MainThread/UiThread annotations."
    },
    "target": {
      "type": "string",
      "maxLength": 256,
      "description": "Omit for constructors and static members. Otherwise context, activity, service:NAME or an objectRef from a previous result."
    },
    "arguments": {
      "type": "array",
      "maxItems": 64,
      "items": {},
      "description": "Required ordered arguments, including explicit nulls. Use {objectRef:ID}, {className:SDK_CLASS}, {bytesBase64:BASE64}, {long:DECIMAL} or {arrayType:SDK_TYPE,values:[...]}. No inferred overloads or nested calls."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "className",
    "member",
    "arguments"
  ],
  "additionalProperties": false
}
```

## dikciz_android_api_release

Release one SDK object handle. close=true explicitly calls AutoCloseable.close when supported. Unregister listeners and stop hardware sessions through their SDK cleanup methods before release. Release does not pretend to undo Android side effects.

```json
{
  "type": "object",
  "properties": {
    "objectRef": {
      "type": "string",
      "maxLength": 256,
      "description": "Object handle."
    },
    "close": {
      "type": "boolean",
      "default": false
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "objectRef"
  ],
  "additionalProperties": false
}
```

## dikciz_apk_install

Submit one readable standalone APK, up to 512 MiB, to Android PackageInstaller. Android may require unknown-source access and confirmation. Poll dikciz_apk_install_status for the actual outcome.

```json
{
  "type": "object",
  "properties": {
    "path": {
      "type": "string",
      "description": "Absolute filesystem path, using Android permissions granted to Dikciz."
    },
    "sha256": {
      "type": "string",
      "description": "Optional expected SHA-256 of the complete APK, exactly 64 hex digits."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "path"
  ]
}
```

## dikciz_apk_install_cancel

Abandon a still-cancellable Dikciz-owned APK installation session, including staging and pending confirmation. Returns cancellationAccepted=false when terminal or too late. Poll apkInstallStatus for the actual outcome. Never uninstalls an installed package.

```json
{
  "type": "object",
  "properties": {
    "sessionId": {
      "type": "integer",
      "minimum": 1,
      "maximum": 2147483647
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "sessionId"
  ],
  "additionalProperties": false
}
```

## dikciz_apk_install_set

Install one base APK followed by its split APKs in one Android PackageInstaller transaction. apks contains 1..32 unique files, at most 512 MiB each and 1 GiB total; optional SHA-256 is checked while staging. The first file must be a base APK. Android validates split names, signatures, versions and compatibility at commit and retains owner confirmation. Poll apkInstallStatus for actual outcome; queued is not installed.

```json
{
  "type": "object",
  "properties": {
    "apks": {
      "type": "array",
      "minItems": 1,
      "maxItems": 32,
      "items": {
        "type": "object",
        "properties": {
          "path": {
            "type": "string",
            "minLength": 1,
            "maxLength": 4096
          },
          "sha256": {
            "type": "string",
            "minLength": 1,
            "maxLength": 64
          }
        },
        "required": [
          "path"
        ],
        "additionalProperties": false
      }
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "apks"
  ],
  "additionalProperties": false
}
```

## dikciz_apk_install_status

Read the actual Android outcome for one installation submitted by Dikciz. Submission or pending confirmation is not successful installation.

```json
{
  "type": "object",
  "properties": {
    "sessionId": {
      "type": "integer",
      "description": "Install session ID returned by dikciz_apk_install."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "sessionId"
  ]
}
```

## dikciz_app_action

Run one app action on a launchable component: launch, addShortcut, appInfo, uninstall, or forceStop. Force stop needs root and reports root_unavailable otherwise. Uninstall hands off to Android, which asks for its own confirmation.

```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "description": "One of launch, addShortcut, appInfo, uninstall, forceStop.",
      "maximum": 32
    },
    "component": {
      "type": "string",
      "description": "Flattened Android component from dikciz_app_catalogue, such as com.example/com.example.MainActivity.",
      "maximum": 512
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "action",
    "component"
  ]
}
```

## dikciz_app_catalogue

List the launcher app catalogue with stable semantic IDs, human labels, package names, and components, plus the supported app actions. An optional query filters on label and package name.

```json
{
  "type": "object",
  "properties": {
    "query": {
      "type": "string",
      "description": "Case-insensitive filter matched against the app label and its package name.",
      "maximum": 256
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_app_icon

Read a launchable activity icon as a bounded PNG. Use a component from appCatalogue. sizePixels defaults to 64 and accepts 16 through 128. Works through the shared HTML and network command API.

```json
{
  "type": "object",
  "properties": {
    "component": {
      "type": "string",
      "description": "Exact launchable component from appCatalogue.",
      "maxLength": 512
    },
    "sizePixels": {
      "type": "integer",
      "minimum": 16,
      "maximum": 128,
      "default": 64
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "component"
  ],
  "additionalProperties": false
}
```

## dikciz_assistant_routine_preview

Read the exact instructions and access mode of a built-in routine without invoking a provider.

```json
{
  "type": "object",
  "properties": {
    "routineId": {
      "type": "string",
      "minLength": 1,
      "maxLength": 32
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "routineId"
  ],
  "additionalProperties": false
}
```

## dikciz_assistant_routine_run

Queue a built-in briefing in an existing conversation. Its entire turn is forcibly read-only at discovery and execution regardless of conversation settings. Poll chatControl read/status. Uses the saved provider; requestKey prevents duplicate queueing.

```json
{
  "type": "object",
  "properties": {
    "routineId": {
      "type": "string",
      "minLength": 1,
      "maxLength": 32
    },
    "conversationId": {
      "type": "string",
      "minLength": 1,
      "maxLength": 80
    },
    "requestKey": {
      "type": "string",
      "minLength": 1,
      "maxLength": 80
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "routineId",
    "conversationId",
    "requestKey"
  ],
  "additionalProperties": false
}
```

## dikciz_assistant_routine_subscribe

Add or replace one named read-only routine event rule while preserving the conversation's other rules. subscription uses the canonical automation subscription schema. Background reports use the existing chat notifications and Stop control.

```json
{
  "type": "object",
  "properties": {
    "routineId": {
      "type": "string",
      "minLength": 1,
      "maxLength": 32
    },
    "conversationId": {
      "type": "string",
      "minLength": 1,
      "maxLength": 80
    },
    "ruleId": {
      "type": "string",
      "minLength": 1,
      "maxLength": 80
    },
    "subscription": {
      "type": "object"
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "routineId",
    "conversationId",
    "ruleId",
    "subscription"
  ],
  "additionalProperties": false
}
```

## dikciz_assistant_routines

List built-in read-only briefing routines. No provider request or phone mutation.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_audio_devices

List Android audio input and output devices, sample rates and channel counts.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_audio_playback

Manage one process-owned local audio playback session. play prepares asynchronously; poll status for playing/completed/failed. Supports pause/resume, seek, volume and stop, owner-selected output, audio focus and a maximum lifetime. Sessions do not survive process death. Does not play URLs or bypass Android routing/focus restrictions.

```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "enum": [
        "play",
        "status",
        "pause",
        "resume",
        "seek",
        "volume",
        "stop"
      ],
      "maxLength": 16
    },
    "sessionId": {
      "type": "string",
      "maxLength": 36
    },
    "path": {
      "type": "string",
      "maxLength": 4096
    },
    "volumePercent": {
      "type": "integer",
      "minimum": 0,
      "maximum": 100,
      "default": 100
    },
    "positionMilliseconds": {
      "type": "integer",
      "minimum": 0,
      "maximum": 86400000
    },
    "outputDeviceId": {
      "type": "integer",
      "minimum": 0,
      "maximum": 2147483647
    },
    "maximumDurationMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 86400000,
      "default": 3600000
    },
    "loop": {
      "type": "boolean",
      "default": false
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "action"
  ],
  "additionalProperties": false
}
```

## dikciz_automation_dispatch

Run the same typed action array used by an HTML widget or Lua script.

```json
{
  "type": "object",
  "properties": {
    "sourceWidgetAddress": {
      "type": "string",
      "description": "Stable source HTML widget address in 1H2V-widget-id form.",
      "maximum": 256
    },
    "actions": {
      "type": "array",
      "description": "Non-empty typed action array shared by Lua and HTML widgets."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "sourceWidgetAddress",
    "actions"
  ]
}
```

## dikciz_automation_rule_delete

Delete one saved visual rule, its Lua script and owned subscription. In-flight work may finish. Owner only.

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "maxLength": 80
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "id"
  ],
  "additionalProperties": false
}
```

## dikciz_automation_rule_preview

Compile a visual rule into Lua and simulate it with the optional sample payload. Returns proposed commands without touching Android. Owner only.

```json
{
  "type": "object",
  "properties": {
    "rule": {
      "type": "object",
      "additionalProperties": false,
      "required": [
        "title",
        "event",
        "commands"
      ],
      "properties": {
        "id": {
          "type": "string",
          "maxLength": 80,
          "description": "Omit to create. Existing visual rule ID to update."
        },
        "title": {
          "type": "string",
          "maxLength": 120,
          "minLength": 1
        },
        "enabled": {
          "type": "boolean",
          "default": false
        },
        "event": {
          "type": "string",
          "maxLength": 32,
          "enum": [
            "custom",
            "time",
            "battery",
            "charging",
            "alarm",
            "location",
            "sensor"
          ]
        },
        "customEventName": {
          "type": "string",
          "maxLength": 128,
          "description": "Required for event=custom."
        },
        "minimumIntervalMilliseconds": {
          "type": "integer",
          "minimum": 1000,
          "maximum": 86400000,
          "default": 60000
        },
        "alarmIntervalMilliseconds": {
          "type": "integer",
          "minimum": 1000,
          "maximum": 86400000,
          "default": 60000
        },
        "sensorType": {
          "type": "integer",
          "minimum": 1,
          "maximum": 2147483647,
          "description": "Required for event=sensor. Android sensor type, not inventory ID."
        },
        "conditionField": {
          "type": "string",
          "maxLength": 64,
          "description": "One top-level event payload key. Required unless conditionOperator=always."
        },
        "conditionOperator": {
          "type": "string",
          "maxLength": 16,
          "enum": [
            "always",
            "eq",
            "gt",
            "gte",
            "lt",
            "lte",
            "ne"
          ],
          "default": "always"
        },
        "conditionValue": {
          "oneOf": [
            {
              "type": "number"
            },
            {
              "type": "string",
              "maxLength": 120
            },
            {
              "type": "boolean"
            }
          ]
        },
        "commands": {
          "type": "array",
          "minItems": 1,
          "maxItems": 8,
          "items": {
            "oneOf": [
              {
                "type": "object",
                "additionalProperties": false,
                "properties": {
                  "text": {
                    "type": "string",
                    "maxLength": 2000
                  },
                  "type": {
                    "type": "string",
                    "maxLength": 64,
                    "const": "speechSpeak"
                  }
                },
                "required": [
                  "type",
                  "text"
                ]
              },
              {
                "type": "object",
                "additionalProperties": false,
                "properties": {
                  "durationMilliseconds": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 5000,
                    "description": "On only: automatically request torch off after this duration, independent of WebView timers."
                  },
                  "action": {
                    "type": "string",
                    "maxLength": 16,
                    "description": "status, on or off."
                  },
                  "cameraId": {
                    "type": "string",
                    "maxLength": 128,
                    "description": "Flash-capable camera ID; status defaults to all flash cameras."
                  },
                  "timeoutMilliseconds": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 5000,
                    "default": 1000,
                    "description": "Status callback wait only."
                  },
                  "type": {
                    "type": "string",
                    "maxLength": 64,
                    "const": "torchControl"
                  }
                },
                "required": [
                  "type",
                  "action"
                ]
              },
              {
                "type": "object",
                "additionalProperties": false,
                "properties": {
                  "action": {
                    "type": "string",
                    "maxLength": 16,
                    "description": "status, vibrate, waveform, effect or cancel."
                  },
                  "durationMilliseconds": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 10000,
                    "default": 250
                  },
                  "amplitude": {
                    "type": "integer",
                    "minimum": -1,
                    "maximum": 255,
                    "default": -1
                  },
                  "timingsMilliseconds": {
                    "type": "array",
                    "items": {
                      "type": "integer",
                      "minimum": 0,
                      "maximum": 10000
                    },
                    "minItems": 1,
                    "maxItems": 64
                  },
                  "amplitudes": {
                    "type": "array",
                    "items": {
                      "type": "integer",
                      "minimum": -1,
                      "maximum": 255
                    },
                    "minItems": 1,
                    "maxItems": 64
                  },
                  "effect": {
                    "type": "string",
                    "maxLength": 16,
                    "description": "click, doubleClick, heavyClick or tick. Android may use its predefined fallback."
                  },
                  "type": {
                    "type": "string",
                    "maxLength": 64,
                    "const": "vibrationControl"
                  }
                },
                "required": [
                  "type",
                  "action"
                ]
              }
            ]
          }
        }
      }
    },
    "sample": {
      "type": "object"
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "rule"
  ],
  "additionalProperties": false
}
```

## dikciz_automation_rule_save

Validate and save a visual automation rule, Lua source and least-source-capability subscription. Disabled by default. Explicit enabled=true activates future events after reload. Owner only.

```json
{
  "type": "object",
  "properties": {
    "rule": {
      "type": "object",
      "additionalProperties": false,
      "required": [
        "title",
        "event",
        "commands"
      ],
      "properties": {
        "id": {
          "type": "string",
          "maxLength": 80,
          "description": "Omit to create. Existing visual rule ID to update."
        },
        "title": {
          "type": "string",
          "maxLength": 120,
          "minLength": 1
        },
        "enabled": {
          "type": "boolean",
          "default": false
        },
        "event": {
          "type": "string",
          "maxLength": 32,
          "enum": [
            "custom",
            "time",
            "battery",
            "charging",
            "alarm",
            "location",
            "sensor"
          ]
        },
        "customEventName": {
          "type": "string",
          "maxLength": 128,
          "description": "Required for event=custom."
        },
        "minimumIntervalMilliseconds": {
          "type": "integer",
          "minimum": 1000,
          "maximum": 86400000,
          "default": 60000
        },
        "alarmIntervalMilliseconds": {
          "type": "integer",
          "minimum": 1000,
          "maximum": 86400000,
          "default": 60000
        },
        "sensorType": {
          "type": "integer",
          "minimum": 1,
          "maximum": 2147483647,
          "description": "Required for event=sensor. Android sensor type, not inventory ID."
        },
        "conditionField": {
          "type": "string",
          "maxLength": 64,
          "description": "One top-level event payload key. Required unless conditionOperator=always."
        },
        "conditionOperator": {
          "type": "string",
          "maxLength": 16,
          "enum": [
            "always",
            "eq",
            "gt",
            "gte",
            "lt",
            "lte",
            "ne"
          ],
          "default": "always"
        },
        "conditionValue": {
          "oneOf": [
            {
              "type": "number"
            },
            {
              "type": "string",
              "maxLength": 120
            },
            {
              "type": "boolean"
            }
          ]
        },
        "commands": {
          "type": "array",
          "minItems": 1,
          "maxItems": 8,
          "items": {
            "oneOf": [
              {
                "type": "object",
                "additionalProperties": false,
                "properties": {
                  "text": {
                    "type": "string",
                    "maxLength": 2000
                  },
                  "type": {
                    "type": "string",
                    "maxLength": 64,
                    "const": "speechSpeak"
                  }
                },
                "required": [
                  "type",
                  "text"
                ]
              },
              {
                "type": "object",
                "additionalProperties": false,
                "properties": {
                  "durationMilliseconds": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 5000,
                    "description": "On only: automatically request torch off after this duration, independent of WebView timers."
                  },
                  "action": {
                    "type": "string",
                    "maxLength": 16,
                    "description": "status, on or off."
                  },
                  "cameraId": {
                    "type": "string",
                    "maxLength": 128,
                    "description": "Flash-capable camera ID; status defaults to all flash cameras."
                  },
                  "timeoutMilliseconds": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 5000,
                    "default": 1000,
                    "description": "Status callback wait only."
                  },
                  "type": {
                    "type": "string",
                    "maxLength": 64,
                    "const": "torchControl"
                  }
                },
                "required": [
                  "type",
                  "action"
                ]
              },
              {
                "type": "object",
                "additionalProperties": false,
                "properties": {
                  "action": {
                    "type": "string",
                    "maxLength": 16,
                    "description": "status, vibrate, waveform, effect or cancel."
                  },
                  "durationMilliseconds": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 10000,
                    "default": 250
                  },
                  "amplitude": {
                    "type": "integer",
                    "minimum": -1,
                    "maximum": 255,
                    "default": -1
                  },
                  "timingsMilliseconds": {
                    "type": "array",
                    "items": {
                      "type": "integer",
                      "minimum": 0,
                      "maximum": 10000
                    },
                    "minItems": 1,
                    "maxItems": 64
                  },
                  "amplitudes": {
                    "type": "array",
                    "items": {
                      "type": "integer",
                      "minimum": -1,
                      "maximum": 255
                    },
                    "minItems": 1,
                    "maxItems": 64
                  },
                  "effect": {
                    "type": "string",
                    "maxLength": 16,
                    "description": "click, doubleClick, heavyClick or tick. Android may use its predefined fallback."
                  },
                  "type": {
                    "type": "string",
                    "maxLength": 64,
                    "const": "vibrationControl"
                  }
                },
                "required": [
                  "type",
                  "action"
                ]
              }
            ]
          }
        }
      }
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "rule"
  ],
  "additionalProperties": false
}
```

## dikciz_automation_rules_list

List saved visual rule definitions. Lua remains editable through the script editor.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_automation_service_sync

Re-evaluate the current automation policy and ask the private Android service to reload its public configuration.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_automation_status

Read typed automation capabilities, event requirements, available sensors, and relevant Android access state.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_automation_trigger

Queue one fixed manual event for an enabled configured script with a matching policy and subscription.

```json
{
  "type": "object",
  "properties": {
    "scriptId": {
      "type": "string",
      "description": "Stable configured Dikciz script ID with a manual subscription.",
      "maximum": 256
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "scriptId"
  ]
}
```

## dikciz_backup_create

Create an AES-GCM encrypted portable home, widget-state, Lua, chat and provider-settings snapshot. Passphrase required. API keys excluded unless explicitly requested. Remote credentials and control ports always remain outside backups. Owner only.

```json
{
  "type": "object",
  "properties": {
    "passphrase": {
      "type": "string",
      "maxLength": 1024,
      "minLength": 12,
      "description": "Backup encryption passphrase. Never log or put it in prompts."
    },
    "includeApiKeys": {
      "type": "boolean",
      "default": false
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "passphrase"
  ],
  "additionalProperties": false
}
```

## dikciz_backup_preview

Decrypt and validate an archive into protected staging without replacing current state. Returns counts, previewId and sha256 for explicit restore confirmation. Preview expires after 15 minutes. Owner only.

```json
{
  "type": "object",
  "properties": {
    "path": {
      "type": "string",
      "maxLength": 4096
    },
    "passphrase": {
      "type": "string",
      "maxLength": 1024,
      "minLength": 12,
      "description": "Backup encryption passphrase. Never log or put it in prompts."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "path",
    "passphrase"
  ],
  "additionalProperties": false
}
```

## dikciz_backup_restore

Replace home and chat from a validated preview, with rollback recovery. Requires idle assistant, previewId, exact sha256 and confirm=true. Future automation remains disabled unless activateAutomation=true. Remote auth and ports remain.

```json
{
  "type": "object",
  "properties": {
    "previewId": {
      "type": "string",
      "maxLength": 36
    },
    "sha256": {
      "type": "string",
      "maxLength": 64
    },
    "confirm": {
      "type": "boolean",
      "default": false
    },
    "activateAutomation": {
      "type": "boolean",
      "default": false
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "previewId",
    "sha256",
    "confirm"
  ],
  "additionalProperties": false
}
```

## dikciz_backups_list

List encrypted archive paths and metadata without reading contents. Owner only.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_bluetooth_devices

List paired Bluetooth devices with actual bond state. Does not scan, pair or connect.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_bluetooth_exchange

Connect one RFCOMM service, optionally write bytes, then read one bounded chunk and close. Requires a device address and service UUID. Secure pairing/encryption defaults on. The deadline closes the owned socket. Errors can follow partial external writes; do not retry automatically.

```json
{
  "type": "object",
  "properties": {
    "address": {
      "type": "string",
      "description": "Explicit Bluetooth MAC address.",
      "maxLength": 17
    },
    "serviceUuid": {
      "type": "string",
      "description": "Canonical service UUID.",
      "maxLength": 36
    },
    "dataBase64": {
      "type": "string",
      "description": "Optional Base64 output, at most 65536 decoded bytes.",
      "maxLength": 87384
    },
    "readBytes": {
      "type": "integer",
      "minimum": 0,
      "maximum": 65536,
      "default": 4096
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "secure": {
      "type": "boolean",
      "default": true
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "address",
    "serviceUuid"
  ],
  "additionalProperties": false
}
```

## dikciz_bluetooth_gatt

Connect a selected BLE peer for paginated GATT discovery, characteristic/descriptor read/write or bounded notification observation. Uses an owned connection with a deadline, then disconnects/closes. UUID ambiguity requires discovered instance IDs. Writes may have partial effects. Owner Bluetooth access and peripheral security still apply.

```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "description": "discover, read, write, readDescriptor, writeDescriptor or observe.",
      "maxLength": 32
    },
    "address": {
      "type": "string",
      "description": "Explicit Bluetooth MAC address.",
      "maxLength": 17
    },
    "serviceUuid": {
      "type": "string",
      "description": "Canonical service UUID.",
      "maxLength": 36
    },
    "serviceInstanceId": {
      "type": "integer",
      "minimum": 0,
      "maximum": 65535
    },
    "characteristicUuid": {
      "type": "string",
      "description": "Canonical characteristic UUID.",
      "maxLength": 36
    },
    "characteristicInstanceId": {
      "type": "integer",
      "minimum": 0,
      "maximum": 65535
    },
    "descriptorUuid": {
      "type": "string",
      "description": "Canonical descriptor UUID.",
      "maxLength": 36
    },
    "dataBase64": {
      "type": "string",
      "description": "Base64 output, at most 512 decoded bytes.",
      "maxLength": 684
    },
    "writeType": {
      "type": "string",
      "description": "Characteristic writes: default, noResponse or signed.",
      "maxLength": 16
    },
    "maximumEvents": {
      "type": "integer",
      "minimum": 1,
      "maximum": 100,
      "default": 1
    },
    "mtu": {
      "type": "integer",
      "minimum": 23,
      "maximum": 517
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "action",
    "address"
  ],
  "additionalProperties": false
}
```

## dikciz_bluetooth_pair

Ask Android to start pairing with a selected Bluetooth address. Owner PIN/confirmation may be required. submitted is not paired; inspect bluetoothDevices for the final bond.

```json
{
  "type": "object",
  "properties": {
    "address": {
      "type": "string",
      "description": "Explicit Bluetooth MAC address.",
      "maxLength": 17
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "address"
  ],
  "additionalProperties": false
}
```

## dikciz_bluetooth_scan

Observe nearby BLE or classic devices during a bounded scan window. Requires Android Bluetooth access, plus location on older Android. Returns actual observed address/name/type/bond/RSSI metadata. Stops only its own scan.

```json
{
  "type": "object",
  "properties": {
    "mode": {
      "type": "string",
      "description": "ble (default) or classic.",
      "maxLength": 16
    },
    "durationMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 5000
    },
    "maximumDevices": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_call_audio

Read cellular call-audio capability status or request record/speak. Ordinary Android installations return call_audio_unsupported for capture and injection. Microphone and device speech are separate operations.

```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "description": "status, record or speak.",
      "maxLength": 16
    },
    "text": {
      "type": "string",
      "description": "Requested call speech.",
      "maxLength": 2000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "action"
  ],
  "additionalProperties": false
}
```

## dikciz_call_control

Native phone operations: state, place, answer or end. Place requires recipient. Android may restrict operations by role or privilege. accepted never claims a connected call.

```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "description": "state, place, answer or end.",
      "maxLength": 16
    },
    "recipient": {
      "type": "string",
      "description": "Dialable number for place only. Common formatting separators are normalized.",
      "maxLength": 64
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "action"
  ],
  "additionalProperties": false
}
```

## dikciz_camera_capabilities

Read JPEG and recorder sizes, camera frame-rate ranges, digital zoom, exposure range/EV step, sensor orientation and autofocus modes for one camera.

```json
{
  "type": "object",
  "properties": {
    "cameraId": {
      "type": "string",
      "description": "Camera ID from cameraList.",
      "maxLength": 128
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "cameraId"
  ],
  "additionalProperties": false
}
```

## dikciz_camera_capture

Capture a JPEG with optional quality, orientation, sensor crop zoom, exposure compensation and autofocus controls. Returns written image path and completed Android frame metadata. Read cameraCapabilities first. Android camera access and foreground restrictions apply.

```json
{
  "type": "object",
  "properties": {
    "cameraId": {
      "type": "string",
      "description": "Camera ID from cameraList; defaults to the first camera.",
      "maxLength": 128
    },
    "maxDimension": {
      "type": "integer",
      "minimum": 320,
      "maximum": 4096,
      "default": 1280
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "jpegQuality": {
      "type": "integer",
      "minimum": 1,
      "maximum": 100,
      "default": 95
    },
    "jpegOrientation": {
      "type": "integer",
      "minimum": 0,
      "maximum": 270
    },
    "zoomPercent": {
      "type": "integer",
      "minimum": 100,
      "maximum": 10000
    },
    "exposureCompensation": {
      "type": "integer",
      "minimum": -1000,
      "maximum": 1000
    },
    "autofocusMode": {
      "type": "integer",
      "minimum": 0,
      "maximum": 5
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_camera_list

List Android camera IDs and lens directions without starting capture.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_camera_video_record

Record bounded foreground camera H.264 MP4, optionally with microphone AAC. Returns only after finalization and Android metadata inspection. Shares camera/microphone ownership; incomplete files are removed. No background service or preview. Use jobStart for reconnectable results.

```json
{
  "type": "object",
  "properties": {
    "cameraId": {
      "type": "string",
      "maxLength": 128
    },
    "durationMilliseconds": {
      "type": "integer",
      "minimum": 1000,
      "maximum": 120000,
      "default": 3000
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 300000,
      "default": 30000
    },
    "maxDimension": {
      "type": "integer",
      "minimum": 320,
      "maximum": 1920,
      "default": 1280
    },
    "frameRate": {
      "type": "integer",
      "minimum": 5,
      "maximum": 60,
      "default": 30
    },
    "bitRate": {
      "type": "integer",
      "minimum": 500000,
      "maximum": 20000000,
      "default": 4000000
    },
    "orientation": {
      "type": "integer",
      "minimum": 0,
      "maximum": 270,
      "default": 0
    },
    "withAudio": {
      "type": "boolean",
      "default": false
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_chat

Manage the on-device Dikciz assistant. Actions: status, profiles, profileSave, models, create, list, read, rename, settings, configure, delete, send, task, stop, subscribe, show, hide, fullscreen. A send is queued, not proof of completion. Poll read/status. Profile reads never return API keys. Save a connection without a model, discover its models, then configure each conversation with independent model and token settings. settings reads effective values and modelLimits. Selecting a known model fills context defaults and clamps the reply reserve; explicit token fields win. models exposes known context/output/input ceilings and limitsSource, using provider metadata before the bundled catalog. Unknown models keep manual budgets. configure patches settings and optionally changes profileId. Changing providers retains read-only access and resets other overrides. settings.readOnly defaults to false; true hides mutating tools and blocks their execution and permission prompts. subscribe replaces the conversation's event rules; each rule has id, subscription (canonical automation event subscription) and instructions. show/hide/fullscreen control the chat panel independently of the agent. task starts widget creation (task:widget, text required) or a read-only screen explanation (task:explainScreen, optional text), with conversationId and requestKey. Screen tasks hide chat, capture the display and reopen chat. Read the chat MCP guide for fields and examples.

```json
{
  "type": "object",
  "properties": {
    "task": {
      "type": "string",
      "maxLength": 32
    },
    "action": {
      "type": "string",
      "maxLength": 32
    },
    "conversationId": {
      "type": "string",
      "maxLength": 80
    },
    "profileId": {
      "type": "string",
      "maxLength": 80
    },
    "title": {
      "type": "string",
      "maxLength": 256
    },
    "text": {
      "type": "string",
      "maxLength": 32768
    },
    "requestKey": {
      "type": "string",
      "maxLength": 80
    },
    "profile": {
      "type": "object"
    },
    "settings": {
      "type": "object"
    },
    "rules": {
      "type": "array"
    },
    "fullScreen": {
      "type": "boolean"
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "action"
  ],
  "additionalProperties": false
}
```

## dikciz_clipboard_get

Read clipboard text while Android permits the calling app to access it. Background restrictions return available=false.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_clipboard_set

Set plain clipboard text through ClipboardManager.

```json
{
  "type": "object",
  "properties": {
    "text": {
      "type": "string",
      "description": "Plain text, including an empty string.",
      "maxLength": 4096
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "text"
  ],
  "additionalProperties": false
}
```

## dikciz_config_get

Read the complete validated Dikciz configuration document.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_config_replace

Validate, atomically save, and reload a complete Dikciz configuration document.

```json
{
  "type": "object",
  "properties": {
    "config": {
      "type": "object",
      "description": "Complete Dikciz configuration document."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "config"
  ]
}
```

## dikciz_config_reset

Restore the bundled Dikciz configuration while retaining permitted unknown root namespaces.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_config_seed

Validate, atomically save, and reload a complete Dikciz configuration document before scripted launcher use.

```json
{
  "type": "object",
  "properties": {
    "config": {
      "type": "object",
      "description": "Complete Dikciz configuration document."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "config"
  ]
}
```

## dikciz_content_batch

Apply at most 32 validated writes atomically within one Android provider. Earlier create results may supply allowlisted foreign-key back references. No arbitrary URI or SQL.

```json
{
  "type": "object",
  "properties": {
    "operations": {
      "type": "array",
      "minItems": 1,
      "maxItems": 32,
      "items": {
        "type": "object",
        "properties": {
          "resource": {
            "type": "string",
            "description": "Resource from nativeDataStatus.resources.",
            "maxLength": 64
          },
          "operation": {
            "type": "string",
            "description": "create, update or delete. Updates and deletes require one positive row id.",
            "maxLength": 16
          },
          "id": {
            "type": "integer",
            "minimum": 1,
            "maximum": 2147483647
          },
          "values": {
            "type": "object",
            "description": "Allowlisted provider columns and primitive values. Unspecified columns remain unchanged."
          },
          "backReferences": {
            "type": "object",
            "description": "Batch-only foreign-key column to earlier create-operation index, within the same provider."
          }
        },
        "required": [
          "resource",
          "operation"
        ],
        "additionalProperties": false
      }
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "operations"
  ],
  "additionalProperties": false
}
```

## dikciz_content_query

Read one bounded page from an allowlisted Android provider. Filter values use bound arguments. Query is literal substring search. Follow hasMore using offset plus returned item count.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "resource": {
      "type": "string",
      "description": "Resource from nativeDataStatus.resources.",
      "maxLength": 64
    },
    "query": {
      "type": "string",
      "description": "Literal substring matched against the resource search columns.",
      "maxLength": 256
    },
    "filter": {
      "type": "object",
      "description": "Exact equality filters on allowlisted columns. Null matches SQL NULL."
    },
    "columns": {
      "type": "array",
      "maxItems": 32,
      "minItems": 1,
      "items": {
        "type": "string",
        "description": "Allowlisted column.",
        "maxLength": 64
      }
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "resource"
  ],
  "additionalProperties": false
}
```

## dikciz_content_write

Create, update or delete a single Android provider row. Native data status lists writable resources and columns. SMS history stays read-only. Android permissions and provider rules apply.

```json
{
  "type": "object",
  "properties": {
    "resource": {
      "type": "string",
      "description": "Resource from nativeDataStatus.resources.",
      "maxLength": 64
    },
    "operation": {
      "type": "string",
      "description": "create, update or delete. Updates and deletes require one positive row id.",
      "maxLength": 16
    },
    "id": {
      "type": "integer",
      "minimum": 1,
      "maximum": 2147483647
    },
    "values": {
      "type": "object",
      "description": "Allowlisted provider columns and primitive values. Unspecified columns remain unchanged."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "resource",
    "operation"
  ],
  "additionalProperties": false
}
```

## dikciz_control_status

Read the complete local command contract, limits, transports, and safe-mode state.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_custom_app_alert

Show/close a reusable dimmed urgent countdown modal with a notification fallback and cancel button. show needs episodeId, durationMilliseconds, title, detail, cancelLabel, cancelEvent. Optional announcement/finalAnnouncement are spoken via offline TTS, {seconds} is substituted. Cancellation dispatches cancelEvent to this app. Requires enabled notifications and high-priority alert channel. The response reports requested presentation; inspect customAppRead.alert for observed state. Android protected screens may hide overlays.

```json
{
  "type": "object",
  "properties": {
    "appId": {
      "type": "string",
      "maxLength": 64,
      "pattern": "[A-Za-z][A-Za-z0-9_-]{0,63}"
    },
    "action": {
      "type": "string",
      "maxLength": 96,
      "enum": [
        "show",
        "close"
      ]
    },
    "episodeId": {
      "type": "string",
      "maxLength": 120
    },
    "durationMilliseconds": {
      "type": "integer",
      "minimum": 1000,
      "maximum": 3600000
    },
    "title": {
      "type": "string",
      "maxLength": 120
    },
    "detail": {
      "type": "string",
      "maxLength": 2000
    },
    "cancelLabel": {
      "type": "string",
      "maxLength": 80
    },
    "cancelEvent": {
      "type": "string",
      "maxLength": 96,
      "pattern": "[A-Za-z][A-Za-z0-9_.-]{0,95}"
    },
    "announcement": {
      "type": "string",
      "maxLength": 2000
    },
    "finalAnnouncement": {
      "type": "string",
      "maxLength": 2000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "appId",
    "action"
  ],
  "additionalProperties": false
}
```

## dikciz_custom_app_control

open starts an app and opens its UI; enable starts it without permission approval; disable stops it and cancels timers and alerts; remove requires confirm=true and deletes only this app's directory; export writes a bounded .dikciz-app.zip; import requires path and confirm=true and installs a new enabled app. Apps inherit Dikciz Android access. Export excludes state and runtime files. To copy an app, install its source with a different manifest.id for an independent instance.

```json
{
  "type": "object",
  "properties": {
    "appId": {
      "type": "string",
      "maxLength": 64,
      "pattern": "[A-Za-z][A-Za-z0-9_-]{0,63}"
    },
    "action": {
      "type": "string",
      "maxLength": 96,
      "enum": [
        "open",
        "enable",
        "disable",
        "remove",
        "export",
        "import"
      ]
    },
    "path": {
      "type": "string",
      "maxLength": 4096
    },
    "confirm": {
      "type": "boolean"
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "action"
  ],
  "additionalProperties": false
}
```

## dikciz_custom_app_event

Synchronously invoke an enabled app's Lua/JS on_event/onEvent handler. event is a name, payload an object. Returns outcome/status/result and current state. A handler result.error with code/message becomes the canonical command error. Shared budgets apply. Calls serialize with timers and cancellation. interruptRunning=true blocks subsequent effects from an already running handler before this event acquires its execution lock. No automatic retry of effects.

```json
{
  "type": "object",
  "properties": {
    "appId": {
      "type": "string",
      "maxLength": 64,
      "pattern": "[A-Za-z][A-Za-z0-9_-]{0,63}"
    },
    "event": {
      "type": "string",
      "maxLength": 96,
      "pattern": "[A-Za-z][A-Za-z0-9_.-]{0,95}"
    },
    "payload": {
      "type": "object"
    },
    "interruptRunning": {
      "type": "boolean",
      "description": "Immediately refuse further mutating host calls from the running handler before queuing this event, for cancellation controls."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "appId",
    "event"
  ],
  "additionalProperties": false
}
```

## dikciz_custom_app_install

Install or replace a complete app bundle after confirm=true. manifest has version:1,id,title,description,language (lua/javascript), optional commands (legacy metadata), events (event names), optional locationMonitoring boolean. Sources use html/css/javascript/backgroundSource. New apps start enabled and inherit Dikciz Android access. Replacing requires no pending timers or alert and preserves data and running/stopped state. All entrypoints share validation.

```json
{
  "type": "object",
  "properties": {
    "manifest": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "version": {
          "type": "integer",
          "minimum": 1,
          "maximum": 1
        },
        "id": {
          "type": "string",
          "maxLength": 64,
          "pattern": "[A-Za-z][A-Za-z0-9_-]{0,63}"
        },
        "title": {
          "type": "string",
          "maxLength": 120,
          "minLength": 1
        },
        "description": {
          "type": "string",
          "maxLength": 2000,
          "minLength": 1
        },
        "language": {
          "type": "string",
          "maxLength": 96,
          "enum": [
            "lua",
            "javascript"
          ]
        },
        "commands": {
          "description": "Optional legacy metadata; apps inherit Dikciz Android access without command restrictions.",
          "type": "array",
          "maxItems": 128,
          "uniqueItems": true,
          "items": {
            "type": "string",
            "maxLength": 96,
            "pattern": "[A-Za-z][A-Za-z0-9_.-]{0,95}"
          }
        },
        "events": {
          "type": "array",
          "maxItems": 128,
          "uniqueItems": true,
          "items": {
            "type": "string",
            "maxLength": 96,
            "pattern": "[A-Za-z][A-Za-z0-9_.-]{0,95}"
          }
        },
        "eventSubscriptions": {
          "type": "array",
          "maxItems": 128,
          "items": {
            "type": "object"
          },
          "description": "Canonical event subscription objects, as in HTML widget eventSubscriptions. Use event, minimumIntervalMilliseconds and event-specific sensorTypes/samplingPeriodMicroseconds/locationPrecision/alarmIntervalMilliseconds/healthRefreshIntervalMilliseconds/packageNames. See the event recipe."
        },
        "locationMonitoring": {
          "type": "boolean"
        }
      },
      "required": [
        "version",
        "id",
        "title",
        "description",
        "language",
        "events"
      ]
    },
    "html": {
      "type": "string",
      "maxLength": 262144
    },
    "css": {
      "type": "string",
      "maxLength": 262144
    },
    "javascript": {
      "type": "string",
      "maxLength": 262144
    },
    "backgroundSource": {
      "type": "string",
      "maxLength": 262144
    },
    "confirm": {
      "type": "boolean"
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "manifest",
    "html",
    "confirm"
  ],
  "additionalProperties": false
}
```

## dikciz_custom_app_read

Read one app manifest, full HTML/CSS/JS, optional Lua/JS background source, state, run status and durable timers. Sources live under /sdcard/Dikciz/apps/<appId>. This owner-only read can contain private app data.

```json
{
  "type": "object",
  "properties": {
    "appId": {
      "type": "string",
      "maxLength": 64,
      "pattern": "[A-Za-z][A-Za-z0-9_-]{0,63}"
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "appId"
  ],
  "additionalProperties": false
}
```

## dikciz_custom_app_runtime

Read native clock/boot identity, Android access, notification/channel availability and app service state. Elapsed time is monotonic within boot. Does not grant access or start effects.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_custom_app_schedule

Schedule/cancel one durable app timer by timerId, or cancelAll. schedule needs delayMilliseconds (1000..604800000), event and optional payload. Same ID replaces only pending work; a unique generation identifies each delivery. Android can delay alarms. A claimed timer is never automatically replayed after a crash; interrupted/rebooted work needs review. Timer cancellation cannot recall submitted effects. Scheduling starts a visible service with Stop.

```json
{
  "type": "object",
  "properties": {
    "appId": {
      "type": "string",
      "maxLength": 64,
      "pattern": "[A-Za-z][A-Za-z0-9_-]{0,63}"
    },
    "action": {
      "type": "string",
      "maxLength": 96,
      "enum": [
        "schedule",
        "cancel",
        "cancelAll"
      ]
    },
    "timerId": {
      "type": "string",
      "maxLength": 64,
      "pattern": "[A-Za-z][A-Za-z0-9_-]{0,63}"
    },
    "delayMilliseconds": {
      "type": "integer",
      "minimum": 1000,
      "maximum": 604800000
    },
    "event": {
      "type": "string",
      "maxLength": 96,
      "pattern": "[A-Za-z][A-Za-z0-9_.-]{0,95}"
    },
    "payload": {
      "type": "object"
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "appId",
    "action"
  ],
  "additionalProperties": false
}
```

## dikciz_custom_app_state

Read or atomically replace app state.json. replace requires state. Backends and UI share this state. Script patchState actions merge keys. Use customAppRead for a read-only agent. App bindings default to their own appId and may explicitly address another app.

```json
{
  "type": "object",
  "properties": {
    "appId": {
      "type": "string",
      "maxLength": 64,
      "pattern": "[A-Za-z][A-Za-z0-9_-]{0,63}"
    },
    "action": {
      "type": "string",
      "maxLength": 96,
      "enum": [
        "read",
        "replace"
      ]
    },
    "state": {
      "type": "object"
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "appId",
    "action"
  ],
  "additionalProperties": false
}
```

## dikciz_custom_apps_list

List installed editable Dikciz apps. Follow hasMore; disabled apps cannot execute background code. Apps are independent of Android APKs.

```json
{
  "type": "object",
  "properties": {
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_device_connection_close

Close a retained device connection and release claimed hardware. Completed effects remain.

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "minLength": 1,
      "maxLength": 80
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "id"
  ],
  "additionalProperties": false
}
```

## dikciz_device_connection_exchange

One serialized operation on a retained connection. RFCOMM writes optional dataBase64 and reads at most readBytes; a chunk is not a complete message. USB action control/bulk/interrupt uses the claimed interface. NFC action transceive/readNdef/writeNdef. GATT action discover/read/write/subscribe/unsubscribe/notifications, with exact service/characteristic UUIDs. Timeout closes the connection; accepted writes can be partial. All exchange actions are mutating for access control.

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "minLength": 1,
      "maxLength": 80
    },
    "action": {
      "type": "string",
      "minLength": 1,
      "maxLength": 32
    },
    "dataBase64": {
      "type": "string",
      "minLength": 0,
      "maxLength": 87384
    },
    "readBytes": {
      "type": "integer",
      "minimum": 0,
      "maximum": 65536,
      "default": 4096
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "endpointAddress": {
      "type": "integer",
      "minimum": 0,
      "maximum": 255
    },
    "requestType": {
      "type": "integer",
      "minimum": 0,
      "maximum": 255
    },
    "request": {
      "type": "integer",
      "minimum": 0,
      "maximum": 255
    },
    "value": {
      "type": "integer",
      "minimum": 0,
      "maximum": 65535
    },
    "index": {
      "type": "integer",
      "minimum": 0,
      "maximum": 65535
    },
    "serviceUuid": {
      "type": "string",
      "minLength": 1,
      "maxLength": 40
    },
    "characteristicUuid": {
      "type": "string",
      "minLength": 1,
      "maxLength": 40
    },
    "serviceInstanceId": {
      "type": "integer",
      "minimum": 0,
      "maximum": 2147483647
    },
    "characteristicInstanceId": {
      "type": "integer",
      "minimum": 0,
      "maximum": 2147483647
    },
    "cursor": {
      "type": "integer",
      "minimum": 0,
      "maximum": 9223372036854775807,
      "default": 0
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "id"
  ],
  "additionalProperties": false
}
```

## dikciz_device_connection_open

Open a retained rfcomm, gatt, usb or nfc connection for multiple commands. At most eight active; default lifetime five minutes, configurable up to one hour. Process death closes hardware. Bluetooth uses address and serviceUuid for rfcomm; USB requires deviceId and owner-granted access; NFC requires foreground and a presented tag. No payment interpretation or automatic retries.

```json
{
  "type": "object",
  "properties": {
    "kind": {
      "type": "string",
      "minLength": 1,
      "maxLength": 16,
      "enum": [
        "rfcomm",
        "gatt",
        "usb",
        "nfc"
      ]
    },
    "address": {
      "type": "string",
      "minLength": 1,
      "maxLength": 32
    },
    "serviceUuid": {
      "type": "string",
      "minLength": 1,
      "maxLength": 40
    },
    "secure": {
      "type": "boolean"
    },
    "deviceId": {
      "type": "integer",
      "minimum": 0,
      "maximum": 2147483647
    },
    "interfaceIndex": {
      "type": "integer",
      "minimum": 0,
      "maximum": 255
    },
    "technology": {
      "type": "string",
      "minLength": 1,
      "maxLength": 32,
      "enum": [
        "IsoDep",
        "NfcA",
        "NfcB",
        "NfcF",
        "NfcV",
        "Ndef",
        "MifareClassic",
        "MifareUltralight"
      ]
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "maximumDurationMilliseconds": {
      "type": "integer",
      "minimum": 1000,
      "maximum": 3600000,
      "default": 300000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "kind"
  ],
  "additionalProperties": false
}
```

## dikciz_device_connection_read

Read connection lifecycle metadata without opening or transferring data.

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "minLength": 1,
      "maxLength": 80
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "id"
  ],
  "additionalProperties": false
}
```

## dikciz_device_connections_list

Read a paginated list of retained connection metadata. Inactive entries expire after five minutes.

```json
{
  "type": "object",
  "properties": {
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_device_info

Read the running package, version, process and launcher instance, plus Android device information.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_device_state

Read screen, battery, charging, audio, network, RAM, storage, CPU samples and bounded sensor inventory. System CPU may be unavailable under Android restrictions; sensor inventory does not claim live readings.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_diagnostics

Read local control-plane diagnostics.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_display_screenshot

Capture the visible Android display, including other apps, through owner-enabled Accessibility on Android 11+. Secure windows and system rate limits still apply.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_document_scan

Create cleaned JPEG pages and an image PDF from 1..20 accessible images. Optional normalized clockwise corners correct perspective; absent corners retain the full image. This does not claim automatic edge detection or a searchable PDF text layer. Use imageRecognize on the returned pages for OCR. Output is saved in app capture storage; no image upload.

```json
{
  "type": "object",
  "properties": {
    "pages": {
      "type": "array",
      "minItems": 1,
      "maxItems": 20,
      "items": {
        "type": "object",
        "properties": {
          "path": {
            "type": "string",
            "minLength": 1,
            "maxLength": 4096
          },
          "corners": {
            "type": "array",
            "minItems": 4,
            "maxItems": 4,
            "items": {
              "type": "object",
              "properties": {
                "x": {
                  "type": "number",
                  "minimum": 0,
                  "maximum": 1
                },
                "y": {
                  "type": "number",
                  "minimum": 0,
                  "maximum": 1
                }
              },
              "required": [
                "x",
                "y"
              ],
              "additionalProperties": false
            }
          }
        },
        "required": [
          "path"
        ],
        "additionalProperties": false
      }
    },
    "grayscale": {
      "type": "boolean",
      "default": true
    },
    "maxDimension": {
      "type": "integer",
      "minimum": 320,
      "maximum": 4096,
      "default": 2048
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "pages"
  ],
  "additionalProperties": false
}
```

## dikciz_event_consumer_ack

Persist a named consumer cursor after successful handling. Cursor must not move backwards or beyond the last dispatched event. Does not run actions. Owner only.

```json
{
  "type": "object",
  "properties": {
    "name": {
      "type": "string",
      "maxLength": 80
    },
    "cursor": {
      "type": "integer",
      "minimum": 0,
      "maximum": 9007199254740991
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "name",
    "cursor"
  ],
  "additionalProperties": false
}
```

## dikciz_event_consumer_forget

Delete one consumer's acknowledgement. A subsequent read starts at zero and may report an expired history gap. Owner only.

```json
{
  "type": "object",
  "properties": {
    "name": {
      "type": "string",
      "maxLength": 80
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "name"
  ],
  "additionalProperties": false
}
```

## dikciz_event_consumer_read

Read durable events after a named consumer's last acknowledged cursor. Reading never acknowledges. Reconnects and process restarts retain the epoch and cursor. Inspect historyGap before continuing. Owner only.

```json
{
  "type": "object",
  "properties": {
    "name": {
      "type": "string",
      "maxLength": 80
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200
    },
    "waitMilliseconds": {
      "type": "integer",
      "minimum": 0,
      "maximum": 30000
    },
    "eventTypes": {
      "type": "array",
      "minItems": 1,
      "maxItems": 32,
      "uniqueItems": true,
      "items": {
        "type": "string",
        "maxLength": 128
      }
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "name"
  ],
  "additionalProperties": false
}
```

## dikciz_event_consumers_list

List durable named event consumer cursors. Owner only.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_event_journal_configure

Adjust durable replay count, byte and age limits. Defaults are 2048 events, 8 MiB and 60 minutes. Lower limits immediately evict old history; limits persist across process death. Does not change native sensor/location subscriptions.

```json
{
  "type": "object",
  "properties": {
    "maximumEvents": {
      "type": "integer",
      "minimum": 1,
      "maximum": 16384
    },
    "bufferBytes": {
      "type": "integer",
      "minimum": 65536,
      "maximum": 33554432
    },
    "retentionMinutes": {
      "type": "integer",
      "minimum": 1,
      "maximum": 1440
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_event_journal_read

Replay actual automation events after cursor in the required journalId epoch. Optional exact eventTypes filters, bounded long polling and pagination work across every ordinary binding. Inspect historyGap/lostEvents and per-event omitted markers. Current Android source access is rechecked before returning retained payloads.

```json
{
  "type": "object",
  "properties": {
    "journalId": {
      "type": "string",
      "maxLength": 36
    },
    "cursor": {
      "type": "integer",
      "minimum": 0,
      "maximum": 9007199254740991,
      "default": 0
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "waitMilliseconds": {
      "type": "integer",
      "minimum": 0,
      "maximum": 30000,
      "default": 0
    },
    "eventTypes": {
      "type": "array",
      "minItems": 1,
      "maxItems": 32,
      "uniqueItems": true,
      "items": {
        "type": "string",
        "maxLength": 128
      }
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "journalId"
  ],
  "additionalProperties": false
}
```

## dikciz_event_journal_status

Read durable automation-event replay epoch, cursors, retention and memory limits. Reading does not activate native subscriptions.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_file_list

List one directory page in filesystem enumeration order. Follow hasMore with offset plus the returned item count; directory changes can alter enumeration order.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "description": "Maximum directory entries (1 to 500) or file bytes (1 to 262144)."
    },
    "offset": {
      "type": "integer",
      "description": "Nonnegative byte offset, or directory entry offset. Defaults to zero."
    },
    "path": {
      "type": "string",
      "description": "Absolute filesystem path, using Android permissions granted to Dikciz."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "path"
  ]
}
```

## dikciz_file_manage

Stat, copy one regular file, move without overwrite, explicitly delete, or search accessible files by literal name substring. Recursive deletion is preflighted, bounded and refuses symlinks and credential ancestors. Search does not follow symlinks. Android filesystem access applies.

```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "description": "stat, copy, move, delete or search.",
      "maxLength": 16
    },
    "path": {
      "type": "string",
      "description": "Absolute source file or directory path.",
      "maxLength": 4096
    },
    "destination": {
      "type": "string",
      "description": "New absolute path, copy/move only. Existing destinations are never overwritten.",
      "maxLength": 4096
    },
    "recursive": {
      "type": "boolean",
      "default": false
    },
    "query": {
      "type": "string",
      "description": "Literal case-insensitive name substring, search only.",
      "maxLength": 256
    },
    "maximumEntries": {
      "type": "integer",
      "minimum": 1,
      "maximum": 100000,
      "default": 10000
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "action",
    "path"
  ],
  "additionalProperties": false
}
```

## dikciz_file_mkdir

Create a directory wherever Dikciz can write. Optionally create missing parents.

```json
{
  "type": "object",
  "properties": {
    "parents": {
      "type": "boolean",
      "description": "Create missing parent directories. Defaults to false."
    },
    "path": {
      "type": "string",
      "description": "Absolute filesystem path, using Android permissions granted to Dikciz."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "path"
  ]
}
```

## dikciz_file_read

Read a bounded binary file chunk as base64. Follow nextOffset until eof is true.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "description": "Maximum directory entries (1 to 500) or file bytes (1 to 262144)."
    },
    "offset": {
      "type": "integer",
      "description": "Nonnegative byte offset, or directory entry offset. Defaults to zero."
    },
    "path": {
      "type": "string",
      "description": "Absolute filesystem path, using Android permissions granted to Dikciz."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "path"
  ]
}
```

## dikciz_file_write

Create or explicitly overwrite a file at offset zero, or append a chunk only when offset equals its current size. Never executes uploaded content.

```json
{
  "type": "object",
  "properties": {
    "dataBase64": {
      "type": "string",
      "description": "Strict base64 file bytes, at most 262144 decoded bytes per chunk."
    },
    "offset": {
      "type": "integer",
      "description": "Nonnegative byte offset, or directory entry offset. Defaults to zero."
    },
    "overwrite": {
      "type": "boolean",
      "description": "Allow replacing an existing file at offset zero. Defaults to false."
    },
    "path": {
      "type": "string",
      "description": "Absolute filesystem path, using Android permissions granted to Dikciz."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "dataBase64",
    "path"
  ]
}
```

## dikciz_find

Find one node by its stable semantic ID.

```json
{
  "type": "object",
  "properties": {
    "semanticId": {
      "type": "string",
      "description": "Stable Dikciz semantic ID from dikciz_snapshot.",
      "maximum": 256
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "semanticId"
  ]
}
```

## dikciz_geocode

Forward/reverse geocode through the installed Android provider with a bounded wait. The provider may send query/coordinates to its network service. Explicit failure when unavailable. Android 13+.

```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "description": "forward or reverse.",
      "maxLength": 16
    },
    "query": {
      "type": "string",
      "description": "Place/address for forward lookup.",
      "maxLength": 1024
    },
    "latitude": {
      "type": "number",
      "minimum": -90,
      "maximum": 90
    },
    "longitude": {
      "type": "number",
      "minimum": -180,
      "maximum": 180
    },
    "language": {
      "type": "string",
      "description": "Optional BCP-47 language.",
      "maxLength": 64
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 10,
      "default": 5
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "action"
  ],
  "additionalProperties": false
}
```

## dikciz_geofence_delete

Remove one saved geofence and stop its future event delivery.

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "minLength": 1,
      "maxLength": 80
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "id"
  ],
  "additionalProperties": false
}
```

## dikciz_geofence_save

Create/update one real-location geofence. Disabled by default. Enabled monitoring uses a visible foreground service. Emits geofence.enter, geofence.exit and optional geofence.dwell custom events. Accuracy and hysteresis suppress uncertain crossings. Open Dikciz to enable.

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "minLength": 1,
      "maxLength": 80
    },
    "title": {
      "type": "string",
      "minLength": 1,
      "maxLength": 120
    },
    "latitude": {
      "type": "number",
      "minimum": -90,
      "maximum": 90
    },
    "longitude": {
      "type": "number",
      "minimum": -180,
      "maximum": 180
    },
    "radiusMeters": {
      "type": "number",
      "minimum": 100,
      "maximum": 1000000
    },
    "enabled": {
      "type": "boolean"
    },
    "dwellMilliseconds": {
      "type": "integer",
      "minimum": 0,
      "maximum": 3600000,
      "default": 0
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "title",
    "latitude",
    "longitude",
    "radiusMeters"
  ],
  "additionalProperties": false
}
```

## dikciz_geofences_list

Read saved geofence states and observed distances, with pagination.

```json
{
  "type": "object",
  "properties": {
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_gnss_status

Observe one GNSS satellite-status callback during a bounded window. Does not start GPS or invent a fix. Requires fine location; no status is explicit. Registrations are removed after the request.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 5000,
      "default": 1000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_grid_set

Replace the page grid used by every page. Reflow widget rectangles to fit, relocate collisions and remove overflow items in saved order. Return the saved grid and evictedWidgets with pageId/id records. Evicting app shortcuts leaves their apps installed.

```json
{
  "type": "object",
  "properties": {
    "columns": {
      "type": "integer",
      "description": "Grid columns for every page.",
      "minimum": 1,
      "maximum": 12
    },
    "rows": {
      "type": "integer",
      "description": "Grid rows for every page.",
      "minimum": 1,
      "maximum": 12
    },
    "gapDp": {
      "type": "integer",
      "description": "Gap between cells in density pixels.",
      "minimum": 0,
      "maximum": 64
    },
    "outerPaddingDp": {
      "type": "integer",
      "description": "Padding between the page edge and the grid in density pixels.",
      "minimum": 0,
      "maximum": 64
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "columns",
    "rows",
    "gapDp",
    "outerPaddingDp"
  ]
}
```

## dikciz_health_permissions_request

Ask the owner to review exact Health Connect record permissions. access is read/write/readWrite. Optional history/background access is separate. No silent grants or automatic retry.

```json
{
  "type": "object",
  "properties": {
    "kinds": {
      "type": "array",
      "minItems": 1,
      "maxItems": 16,
      "uniqueItems": true,
      "items": {
        "type": "string",
        "minLength": 1,
        "maxLength": 32,
        "enum": [
          "steps",
          "distance",
          "activeCalories",
          "totalCalories",
          "heartRate",
          "sleep",
          "exercise",
          "weight",
          "height",
          "hydration",
          "bodyFat",
          "bodyTemperature",
          "bloodPressure",
          "bloodGlucose",
          "oxygenSaturation",
          "floors"
        ]
      }
    },
    "access": {
      "type": "string",
      "minLength": 1,
      "maxLength": 16,
      "enum": [
        "read",
        "write",
        "readWrite"
      ]
    },
    "includeBackground": {
      "type": "boolean"
    },
    "includeHistory": {
      "type": "boolean"
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "kinds",
    "access"
  ],
  "additionalProperties": false
}
```

## dikciz_health_records_aggregate

Read the provider's supported aggregate metrics, avoiding duplicate cumulative raw-source counting. Units match the catalogue. Null means no contributing records.

```json
{
  "type": "object",
  "properties": {
    "kind": {
      "type": "string",
      "minLength": 1,
      "maxLength": 32,
      "enum": [
        "steps",
        "distance",
        "activeCalories",
        "totalCalories",
        "heartRate",
        "sleep",
        "exercise",
        "weight",
        "height",
        "hydration",
        "bodyFat",
        "bodyTemperature",
        "bloodPressure",
        "bloodGlucose",
        "oxygenSaturation",
        "floors"
      ]
    },
    "startTimeMilliseconds": {
      "type": "integer",
      "minimum": 0,
      "maximum": 9223372036854775807
    },
    "endTimeMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 9223372036854775807
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "kind",
    "startTimeMilliseconds",
    "endTimeMilliseconds"
  ],
  "additionalProperties": false
}
```

## dikciz_health_records_catalogue

Discover 16 supported Health Connect record kinds, canonical value units, aggregate metric names, provider availability and exact read/write grants.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_health_records_delete

Delete only the exact selected record IDs that Health Connect allows this app to delete. Never deletes all history. Requires confirm=true.

```json
{
  "type": "object",
  "properties": {
    "kind": {
      "type": "string",
      "minLength": 1,
      "maxLength": 32,
      "enum": [
        "steps",
        "distance",
        "activeCalories",
        "totalCalories",
        "heartRate",
        "sleep",
        "exercise",
        "weight",
        "height",
        "hydration",
        "bodyFat",
        "bodyTemperature",
        "bloodPressure",
        "bloodGlucose",
        "oxygenSaturation",
        "floors"
      ]
    },
    "recordIds": {
      "type": "array",
      "minItems": 1,
      "maxItems": 50,
      "uniqueItems": true,
      "items": {
        "type": "string",
        "minLength": 1,
        "maxLength": 256
      }
    },
    "confirm": {
      "type": "boolean"
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "kind",
    "recordIds",
    "confirm"
  ],
  "additionalProperties": false
}
```

## dikciz_health_records_read

Read one bounded page of a Health Connect kind in an epoch-millisecond range of at most 366 days. Follow nextPageToken. Android history/background access restrictions apply. Missing grants return a targeted healthPermissionsRequest recovery.

```json
{
  "type": "object",
  "properties": {
    "kind": {
      "type": "string",
      "minLength": 1,
      "maxLength": 32,
      "enum": [
        "steps",
        "distance",
        "activeCalories",
        "totalCalories",
        "heartRate",
        "sleep",
        "exercise",
        "weight",
        "height",
        "hydration",
        "bodyFat",
        "bodyTemperature",
        "bloodPressure",
        "bloodGlucose",
        "oxygenSaturation",
        "floors"
      ]
    },
    "startTimeMilliseconds": {
      "type": "integer",
      "minimum": 0,
      "maximum": 9223372036854775807
    },
    "endTimeMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 9223372036854775807
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "pageToken": {
      "type": "string",
      "minLength": 1,
      "maxLength": 4096
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "kind",
    "startTimeMilliseconds",
    "endTimeMilliseconds"
  ],
  "additionalProperties": false
}
```

## dikciz_health_records_write

Insert/upsert 1..50 manually entered records of one kind. Every record requires stable clientRecordId, values, and either timeMilliseconds or start/endTimeMilliseconds according to the catalogue. Optional nonnegative clientRecordVersion controls replacement. UTC offsets are used. Only this app's origin is written. Heart-rate samples use timeMilliseconds/beatsPerMinute; sleep stages use start/endTimeMilliseconds/stage.

```json
{
  "type": "object",
  "properties": {
    "kind": {
      "type": "string",
      "minLength": 1,
      "maxLength": 32,
      "enum": [
        "steps",
        "distance",
        "activeCalories",
        "totalCalories",
        "heartRate",
        "sleep",
        "exercise",
        "weight",
        "height",
        "hydration",
        "bodyFat",
        "bodyTemperature",
        "bloodPressure",
        "bloodGlucose",
        "oxygenSaturation",
        "floors"
      ]
    },
    "records": {
      "type": "array",
      "minItems": 1,
      "maxItems": 50,
      "items": {
        "type": "object",
        "properties": {
          "clientRecordId": {
            "type": "string",
            "minLength": 1,
            "maxLength": 80
          },
          "clientRecordVersion": {
            "type": "integer",
            "minimum": 0,
            "maximum": 9223372036854775807
          },
          "timeMilliseconds": {
            "type": "integer",
            "minimum": 0,
            "maximum": 9223372036854775807
          },
          "startTimeMilliseconds": {
            "type": "integer",
            "minimum": 0,
            "maximum": 9223372036854775807
          },
          "endTimeMilliseconds": {
            "type": "integer",
            "minimum": 1,
            "maximum": 9223372036854775807
          },
          "values": {
            "type": "object"
          }
        },
        "required": [
          "clientRecordId",
          "values"
        ],
        "additionalProperties": false
      }
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "kind",
    "records"
  ],
  "additionalProperties": false
}
```

## dikciz_home_get

Read pages keyed by home alias, stable page ID, and H/V address, with each page's widget map.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_html_widget_renderer_crash

Crash one rendered HTML widget through Android's documented WebView test URL, then report the normal renderer-loss recovery through local logs.

```json
{
  "type": "object",
  "properties": {
    "widgetAddress": {
      "type": "string",
      "description": "Stable Dikciz widget address in 1H2V-widget-id form.",
      "maximum": 256
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "widgetAddress"
  ]
}
```

## dikciz_image_recognize

Read text, barcodes or both from an accessible image using bundled on-device models. Latin text recognition; barcode formats are reported as ML Kit constants. No image upload, link opening or instruction execution. Bounds refer to the processed rotated image. Unsupported/oversized inputs fail explicitly.

```json
{
  "type": "object",
  "properties": {
    "path": {
      "type": "string",
      "minLength": 1,
      "maxLength": 4096
    },
    "mode": {
      "type": "string",
      "minLength": 1,
      "maxLength": 16,
      "enum": [
        "text",
        "barcodes",
        "both"
      ]
    },
    "rotationDegrees": {
      "type": "integer",
      "minimum": 0,
      "maximum": 270,
      "default": 0,
      "enum": [
        0,
        90,
        180,
        270
      ]
    },
    "maxDimension": {
      "type": "integer",
      "minimum": 320,
      "maximum": 4096,
      "default": 2048
    },
    "maximumResults": {
      "type": "integer",
      "minimum": 1,
      "maximum": 1000,
      "default": 200
    },
    "maximumTextCharacters": {
      "type": "integer",
      "minimum": 1,
      "maximum": 262144,
      "default": 65536
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "path"
  ],
  "additionalProperties": false
}
```

## dikciz_image_stream_forget

Discard one finished visual stream and its retained images. Stop active capture first.

```json
{
  "type": "object",
  "properties": {
    "streamId": {
      "type": "string",
      "maxLength": 36
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "streamId"
  ],
  "additionalProperties": false
}
```

## dikciz_image_stream_read

Read complete bounded JPEG frames by exclusive cursor, with replay loss and actual terminal/hardware-cleanup state. No capture is started. Frames stay in process memory for five minutes after completion. Owner only.

```json
{
  "type": "object",
  "properties": {
    "streamId": {
      "type": "string",
      "maxLength": 36
    },
    "cursor": {
      "type": "integer",
      "minimum": 0,
      "maximum": 9007199254740991
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 1
    },
    "waitMilliseconds": {
      "type": "integer",
      "minimum": 0,
      "maximum": 30000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "streamId"
  ],
  "additionalProperties": false
}
```

## dikciz_image_stream_start

Start bounded camera JPEG preview or Accessibility display frames with an owner Stop notification. Requires source camera/display and foreground startup. A persistent camera session is reused; display follows Android screenshot limits and secure-window restrictions. Poll imageStreamRead for actual frames/errors. Owner only.

```json
{
  "type": "object",
  "properties": {
    "source": {
      "type": "string",
      "maxLength": 16,
      "enum": [
        "camera",
        "display"
      ]
    },
    "cameraId": {
      "type": "string",
      "maxLength": 128
    },
    "maxDimension": {
      "type": "integer",
      "minimum": 320,
      "maximum": 1280,
      "default": 640
    },
    "frameIntervalMilliseconds": {
      "type": "integer",
      "minimum": 500,
      "maximum": 10000,
      "default": 1000
    },
    "maximumDurationMilliseconds": {
      "type": "integer",
      "minimum": 1000,
      "maximum": 3600000,
      "default": 60000
    },
    "bufferBytes": {
      "type": "integer",
      "minimum": 262144,
      "maximum": 8388608,
      "default": 1048576
    },
    "maximumEvents": {
      "type": "integer",
      "minimum": 1,
      "maximum": 64,
      "default": 16
    },
    "startupTimeoutMilliseconds": {
      "type": "integer",
      "minimum": 1000,
      "maximum": 30000,
      "default": 10000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "source"
  ],
  "additionalProperties": false
}
```

## dikciz_image_stream_stop

Stop one live visual stream, close its camera session and read hardwareReleaseConfirmed. Completed frames remain until forgotten or retention expiry.

```json
{
  "type": "object",
  "properties": {
    "streamId": {
      "type": "string",
      "maxLength": 36
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "streamId"
  ],
  "additionalProperties": false
}
```

## dikciz_image_streams_list

List retained visual stream metadata without frame payloads.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_intent

Dispatch an Android activity or broadcast intent using the launcher app's permissions.

```json
{
  "type": "object",
  "properties": {
    "intentType": {
      "type": "string",
      "description": "Intent dispatch mode: activity or broadcast.",
      "maximum": 32
    },
    "action": {
      "type": "string",
      "description": "Android intent action.",
      "maximum": 2048
    },
    "data": {
      "type": "string",
      "description": "Optional absolute Android intent data URI, such as an HTTPS browser destination.",
      "maximum": 2048
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "intentType",
    "action"
  ]
}
```

## dikciz_job_cancel

Cancel queued work or request interruption of running work. Cancellation never rolls back side effects or guarantees that Android stopped an operation. Read jobStatus for the final outcome.

```json
{
  "type": "object",
  "properties": {
    "jobId": {
      "type": "string",
      "description": "Job ID from jobStart.",
      "maxLength": 36
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "jobId"
  ],
  "additionalProperties": false
}
```

## dikciz_job_start

Persist and run one ordinary Dikciz command asynchronously. Required idempotencyKey prevents duplicate submissions. Results survive disconnect and process death; interrupted work is never replayed automatically. Poll jobStatus for the original complete response including permission recovery. Jobs cannot start other jobs or change remote authentication.

```json
{
  "type": "object",
  "properties": {
    "command": {
      "type": "object",
      "description": "Full ordinary command with type and its fields, optionally autoRequestPermissions."
    },
    "idempotencyKey": {
      "type": "string",
      "description": "Unique caller key; changed content with the same key conflicts.",
      "maxLength": 80
    },
    "retentionHours": {
      "type": "integer",
      "minimum": 1,
      "maximum": 720,
      "default": 168
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "command",
    "idempotencyKey"
  ],
  "additionalProperties": false
}
```

## dikciz_job_status

Read persisted job state and its full original command response. interrupted means outcome unknown after process loss. No automatic retry.

```json
{
  "type": "object",
  "properties": {
    "jobId": {
      "type": "string",
      "description": "Job ID from jobStart.",
      "maxLength": 36
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "jobId"
  ],
  "additionalProperties": false
}
```

## dikciz_jobs_list

List retained job metadata newest first. Includes queued/running/completed/failed/cancelled/interrupted state and cancellation requests; results are read separately with jobStatus.

```json
{
  "type": "object",
  "properties": {
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_journey_control

Start a visible location recording or stop an exact journey ID. One recording, 64 saved journeys and 10000 points per journey. Starting requires location and notifications. Route history persists; it never messages contacts.

```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "minLength": 1,
      "maxLength": 16,
      "enum": [
        "start",
        "stop"
      ]
    },
    "id": {
      "type": "string",
      "minLength": 1,
      "maxLength": 80
    },
    "title": {
      "type": "string",
      "minLength": 1,
      "maxLength": 120
    },
    "sampleIntervalMilliseconds": {
      "type": "integer",
      "minimum": 1000,
      "maximum": 60000,
      "default": 10000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "action"
  ],
  "additionalProperties": false
}
```

## dikciz_journey_delete

Delete a stopped journey and its route points. Requires confirm=true.

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "minLength": 1,
      "maxLength": 80
    },
    "confirm": {
      "type": "boolean"
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "id",
    "confirm"
  ],
  "additionalProperties": false
}
```

## dikciz_journey_read

Read a bounded ordered page of real recorded route points and accuracy. Follow hasMore. No missing location is invented.

```json
{
  "type": "object",
  "properties": {
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "id": {
      "type": "string",
      "minLength": 1,
      "maxLength": 80
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "id"
  ],
  "additionalProperties": false
}
```

## dikciz_journeys_list

Read paginated recorded journeys and interruption state.

```json
{
  "type": "object",
  "properties": {
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_launch_app

Launch an explicit app widget by semantic ID.

```json
{
  "type": "object",
  "properties": {
    "semanticId": {
      "type": "string",
      "description": "Stable Dikciz semantic ID from dikciz_snapshot.",
      "maximum": 256
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "semanticId"
  ]
}
```

## dikciz_launcher_handoff

Acknowledge, release listener ownership and launch an installed Dikciz release/debug package. Same-package requests do nothing. Verify the target after reconnecting; failed startup retries the original listener configuration.

```json
{
  "type": "object",
  "properties": {
    "packageName": {
      "type": "string",
      "description": "Installed Android package name.",
      "maxLength": 255
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "packageName"
  ]
}
```

## dikciz_location_get

Read a recent permitted location or wait for a bounded new fix. Returns age, accuracy and source. timeoutMilliseconds=0 reads cache only; unavailable is explicit.

```json
{
  "type": "object",
  "properties": {
    "maxAgeMilliseconds": {
      "type": "integer",
      "minimum": 0,
      "maximum": 2147483647,
      "default": 300000
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 0,
      "maximum": 120000,
      "default": 10000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_long_press

Long press one semantic ID.

```json
{
  "type": "object",
  "properties": {
    "semanticId": {
      "type": "string",
      "description": "Stable Dikciz semantic ID from dikciz_snapshot.",
      "maximum": 256
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "semanticId"
  ]
}
```

## dikciz_media_sessions_list

Read current media sessions, playback state and bounded metadata through granted notification-listener access.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_microphone_record

Record microphone audio to an AAC M4A file and return its path for fileRead/download. durationMilliseconds defaults to 3000. Requires microphone access and Android foreground eligibility. This is not cellular call capture.

```json
{
  "type": "object",
  "properties": {
    "durationMilliseconds": {
      "type": "integer",
      "minimum": 1000,
      "maximum": 120000,
      "default": 3000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_microphone_session

Start, inspect, stop or cancel one process-owned microphone recording. Requires foreground microphone access. Saved M4A is reported only after successful finalization. Cancellation/failure removes incomplete output. Shares ownership with microphoneRecord. Not call audio or a background recording service.

```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "enum": [
        "start",
        "status",
        "stop",
        "cancel"
      ],
      "maxLength": 16
    },
    "sessionId": {
      "type": "string",
      "maxLength": 36
    },
    "maximumDurationMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 86400000,
      "default": 60000
    },
    "maximumBytes": {
      "type": "integer",
      "minimum": 4096,
      "maximum": 1073741824,
      "default": 67108864
    },
    "inputDeviceId": {
      "type": "integer",
      "minimum": 0,
      "maximum": 2147483647
    },
    "sampleRateHz": {
      "type": "integer",
      "enum": [
        8000,
        16000,
        22050,
        44100,
        48000
      ],
      "default": 44100
    },
    "bitRate": {
      "type": "integer",
      "minimum": 8000,
      "maximum": 320000,
      "default": 128000
    },
    "channels": {
      "type": "integer",
      "minimum": 1,
      "maximum": 2,
      "default": 1
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "action"
  ],
  "additionalProperties": false
}
```

## dikciz_microphone_stream_forget

Release retained audio and metadata for one terminal stream. Active or unconfirmed hardware cleanup is refused.

```json
{
  "type": "object",
  "properties": {
    "streamId": {
      "type": "string",
      "maxLength": 36
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "streamId"
  ],
  "additionalProperties": false
}
```

## dikciz_microphone_stream_read

Read whole PCM chunks and terminal events after cursor. Save nextCursor, drain hasMore and inspect historyGap/lostEvents. Format and monotonic timing metadata accompany actual AudioRecord samples. Optional bounded long polling works across every ordinary binding.

```json
{
  "type": "object",
  "properties": {
    "streamId": {
      "type": "string",
      "maxLength": 36
    },
    "cursor": {
      "type": "integer",
      "minimum": 0,
      "maximum": 9007199254740991,
      "default": 0
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "waitMilliseconds": {
      "type": "integer",
      "minimum": 0,
      "maximum": 30000,
      "default": 0
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "streamId"
  ],
  "additionalProperties": false
}
```

## dikciz_microphone_stream_start

Start actual microphone PCM16 little-endian capture with a dedicated Android foreground service and Stop notification. Start requires foreground eligibility and microphone access; recording can continue in the background. Replay survives client disconnect, not process death. Microphone ownership is shared with recording, recognition and video. No cellular call audio.

```json
{
  "type": "object",
  "properties": {
    "sampleRateHz": {
      "type": "integer",
      "minimum": 4000,
      "maximum": 192000,
      "default": 16000
    },
    "channels": {
      "type": "integer",
      "minimum": 1,
      "maximum": 2,
      "default": 1
    },
    "chunkMilliseconds": {
      "type": "integer",
      "minimum": 20,
      "maximum": 1000,
      "default": 100
    },
    "maximumDurationMilliseconds": {
      "type": "integer",
      "minimum": 100,
      "maximum": 2147483647,
      "default": 30000
    },
    "startupTimeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "bufferBytes": {
      "type": "integer",
      "minimum": 65536,
      "maximum": 8388608,
      "default": 2097152
    },
    "maximumEvents": {
      "type": "integer",
      "minimum": 1,
      "maximum": 4096,
      "default": 1024
    },
    "retentionMinutes": {
      "type": "integer",
      "minimum": 1,
      "maximum": 1440,
      "default": 60
    },
    "inputDeviceId": {
      "type": "integer",
      "minimum": 0,
      "maximum": 2147483647,
      "description": "Preferred audio input ID from audioDevices; inspect the actual routed input."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_microphone_stream_stop

Request microphone-stream stop. Poll read/list until terminal and hardwareReleaseConfirmed. Does not discard retained audio.

```json
{
  "type": "object",
  "properties": {
    "streamId": {
      "type": "string",
      "maxLength": 36
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "streamId"
  ],
  "additionalProperties": false
}
```

## dikciz_microphone_streams_list

Page process-owned microphone-stream metadata without returning audio. Follow hasMore. Process death loses these records.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_native_data_status

Discover direct Android data resources, columns, operations and permission state. No UI automation or root.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_nfc_read

Wait for a presented NFC tag and read its NDEF bytes and technology inventory. Open and unlock Dikciz. No tag/hardware/timeout are explicit. Reader mode and tag connections are released.

```json
{
  "type": "object",
  "properties": {
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "expectedTagIdBase64": {
      "type": "string",
      "maxLength": 88,
      "description": "Optional exact tag ID from nfcRead. Other tags are ignored until the deadline. Tag IDs are not authentication credentials."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_nfc_status

Read NFC adapter availability and enabled state without changing it.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_nfc_transceive

Perform one raw command/response on a presented tag using an explicit supported technology. Device-specific bytes may modify a tag. Android technology restrictions apply. Connections close after this request.

```json
{
  "type": "object",
  "properties": {
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "expectedTagIdBase64": {
      "type": "string",
      "maxLength": 88,
      "description": "Optional exact tag ID from nfcRead. Other tags are ignored until the deadline. Tag IDs are not authentication credentials."
    },
    "dataBase64": {
      "type": "string",
      "maxLength": 43692,
      "description": "Base64 bytes, at most 32768 decoded bytes."
    },
    "technology": {
      "type": "string",
      "enum": [
        "IsoDep",
        "NfcA",
        "NfcB",
        "NfcF",
        "NfcV",
        "MifareClassic",
        "MifareUltralight"
      ],
      "maxLength": 32
    },
    "ioTimeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 60000,
      "default": 5000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "technology",
    "dataBase64"
  ],
  "additionalProperties": false
}
```

## dikciz_nfc_write

Wait for a tag and write a validated NDEF message. format=true explicitly permits formatting an unformatted compatible tag. Read-back verification is reported. Open/unlock Dikciz and present the intended tag; writes may have partial effects.

```json
{
  "type": "object",
  "properties": {
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "expectedTagIdBase64": {
      "type": "string",
      "maxLength": 88,
      "description": "Optional exact tag ID from nfcRead. Other tags are ignored until the deadline. Tag IDs are not authentication credentials."
    },
    "dataBase64": {
      "type": "string",
      "maxLength": 43692,
      "description": "Base64 bytes, at most 32768 decoded bytes."
    },
    "format": {
      "type": "boolean",
      "default": false
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "dataBase64"
  ],
  "additionalProperties": false
}
```

## dikciz_notification_manage

Dismiss or snooze an active notification, or submit a free-form inline reply through a short-lived action token from notificationsList.actions. Submission does not confirm remote delivery. Android listener access is required.

```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "description": "dismiss, snooze or reply.",
      "maxLength": 16
    },
    "notificationKey": {
      "type": "string",
      "description": "Exact key from notificationsList; dismiss/snooze only.",
      "maxLength": 4096
    },
    "actionToken": {
      "type": "string",
      "description": "Single-use action token supporting inline reply; reply only.",
      "maxLength": 128
    },
    "text": {
      "type": "string",
      "description": "Nonblank reply text; reply only.",
      "maxLength": 4096
    },
    "durationMilliseconds": {
      "type": "integer",
      "minimum": 1000,
      "maximum": 604800000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "action"
  ],
  "additionalProperties": false
}
```

## dikciz_notifications_list

Read current notifications directly through the enabled notification listener, including bounded text and short-lived action tokens.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_open_automation_setup

Open the native Dikciz Automation access sheet so the phone owner can grant the Android access a denied action needs.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_package_info

Read installed version and launch component through Android PackageManager without shell privileges.

```json
{
  "type": "object",
  "properties": {
    "packageName": {
      "type": "string",
      "description": "Installed Android package name.",
      "maxLength": 255
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "packageName"
  ]
}
```

## dikciz_remote_access_info

Read the verified caller identity, effective grants, expiresAt, requestsPerMinute and exact smsAddresses restrictions. Restricted SMS reads require filter.address. Local owner bindings need no bearer token. Does not return credentials or verifiers.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_remote_auth_disable

Allow headerless local-tunnel control while retaining the existing phone-configured verifier.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_remote_auth_enable

Require the existing phone-configured remote bearer credential on both remote control planes.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_remote_auth_status

Read remote WebSocket and MCP bearer-auth state without exposing its verifier.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_remote_tokens

Owner-only remote access-token administration. catalog lists finite permission grants. list returns metadata; create requires name and permissions and returns its generated token once. update requires id and optional name/permissions/enabled/expiry/rate/SMS-address rules; rotate requires id and returns a replacement token once, invalidating the previous secret immediately; revoke requires id. Optional expiresAt is epoch milliseconds (0 means none), requestsPerMinute is 0..10000 (0 means unlimited), and smsAddresses is an exact address allowlist. Restricted SMS reads require filter.address. Authentication must be enabled before creating tokens. Existing tokens, including disabled tokens, prevent disabling or clearing authentication. Local owner bindings and full-owner credentials can administer tokens. Scoped credentials cannot.

```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "maxLength": 16,
      "enum": [
        "catalog",
        "list",
        "create",
        "update",
        "revoke",
        "rotate"
      ]
    },
    "id": {
      "type": "string",
      "maxLength": 16,
      "pattern": "^[a-f0-9]{16}$"
    },
    "name": {
      "type": "string",
      "maxLength": 80,
      "minLength": 1
    },
    "permissions": {
      "type": "array",
      "minItems": 1,
      "maxItems": 32,
      "uniqueItems": true,
      "items": {
        "type": "string",
        "maxLength": 64,
        "enum": [
          "apps.launch",
          "apps.read",
          "bluetooth.read",
          "calendar.read",
          "calendar.write",
          "calls.control",
          "calls.read",
          "contacts.read",
          "contacts.write",
          "device.control",
          "device.read",
          "events.read",
          "files.read",
          "location.read",
          "logs.read",
          "media.read",
          "nfc.read",
          "notifications.control",
          "notifications.read",
          "owner",
          "screen.capture",
          "search.read",
          "search.write",
          "sensors.read",
          "sim.read",
          "sms.read",
          "sms.send",
          "speech.output",
          "ui.control",
          "ui.read",
          "usb.read",
          "wifi.read"
        ]
      }
    },
    "enabled": {
      "type": "boolean"
    },
    "expiresAt": {
      "type": "integer",
      "minimum": 0,
      "maximum": 9223372036854775807,
      "description": "Expiry in epoch milliseconds. Zero means no expiry."
    },
    "requestsPerMinute": {
      "type": "integer",
      "minimum": 0,
      "maximum": 10000,
      "description": "Accepted commands per rolling minute across all entrypoints. Zero means unlimited."
    },
    "smsAddresses": {
      "type": "array",
      "maxItems": 64,
      "uniqueItems": true,
      "items": {
        "type": "string",
        "maxLength": 64
      },
      "description": "Optional exact SMS addresses. Restricted reads require filter.address; sending requires an exact recipient. Empty permits all addresses."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "action"
  ],
  "additionalProperties": false
}
```

## dikciz_request_device_access

Ask the phone owner to grant one deviceAccess requirement from automationStatus. Posts a targeted notification and consent prompt. Never grants automatically or retries the blocked operation. Read state and notificationPosted before telling the user to check the phone.

```json
{
  "type": "object",
  "properties": {
    "requirementId": {
      "type": "string",
      "description": "Exact requirement ID from automationStatus.deviceAccess.requirements.",
      "maxLength": 64
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "requirementId"
  ]
}
```

## dikciz_resource_control

Stop one resource: target script/widget disables future configuration work; conversation clears event rules and requests cancellation; imageStream/speechStream stops capture. Supply target and id, plus pageId for widgets. Legacy scriptId is accepted alone. Owner only.

```json
{
  "type": "object",
  "properties": {
    "scriptId": {
      "type": "string",
      "maxLength": 80
    },
    "id": {
      "type": "string",
      "maxLength": 80
    },
    "pageId": {
      "type": "string",
      "maxLength": 80
    },
    "target": {
      "type": "string",
      "enum": [
        "script",
        "widget",
        "conversation",
        "imageStream",
        "speechStream"
      ]
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_resource_usage

Read measured process CPU/RAM/storage, UID network counters and command executor CPU grouped by actor. Explicitly excludes unmeasurable per-widget battery and WebView CPU attribution. Owner only.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_restart

Acknowledge, then recreate the launcher and listeners without killing the process or changing saved configuration. Reconnect and verify a new instanceId through dikciz_device_info.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_screenshot

Capture a bounded PNG of the launcher.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_script_logs

Read the bounded, sanitized script-log snapshot.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_scroll_by

Scroll the selected page by an exact pixel delta.

```json
{
  "type": "object",
  "properties": {
    "deltaY": {
      "type": "integer",
      "description": "Vertical pixel delta. Positive moves down.",
      "minimum": -10000,
      "maximum": 10000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "deltaY"
  ]
}
```

## dikciz_scroll_to

Scroll the selected page to a semantic ID.

```json
{
  "type": "object",
  "properties": {
    "semanticId": {
      "type": "string",
      "description": "Stable Dikciz semantic ID from dikciz_snapshot.",
      "maximum": 256
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "semanticId"
  ]
}
```

## dikciz_search_collections

List local search collections and document counts. docs and tools are protected, rebuilt from the installed APK. Other collections contain only explicitly indexed owner content.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 20,
      "default": 5
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 10000,
      "default": 0
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_search_index

Atomically upsert, remove or clear owner search documents. Reserved docs/tools collections cannot be changed. Upsert replaces IDs and accepts at most 32 documents with id, title, text and optional aliases/uri. Remove requires ids; clear removes one collection. Search indexes do not crawl files or private data. Maximum 2000 documents per custom collection, 10000 overall. Mutations are unavailable in read-only mode.

```json
{
  "type": "object",
  "properties": {
    "collection": {
      "type": "string",
      "maxLength": 64,
      "pattern": "^[a-z][a-z0-9_.-]{0,63}$",
      "description": "Lowercase collection name. docs and tools are protected built-ins; other names scope explicitly indexed owner text."
    },
    "action": {
      "type": "string",
      "maxLength": 16,
      "enum": [
        "upsert",
        "remove",
        "clear"
      ]
    },
    "documents": {
      "type": "array",
      "minItems": 1,
      "maxItems": 32,
      "items": {
        "type": "object",
        "properties": {
          "id": {
            "type": "string",
            "maxLength": 256,
            "pattern": "^[A-Za-z0-9][A-Za-z0-9_.:/#-]{0,255}$",
            "description": "Exact opaque document ID. It is a database key, never a file path."
          },
          "title": {
            "type": "string",
            "maxLength": 512,
            "minLength": 1,
            "description": "Human-readable document title."
          },
          "text": {
            "type": "string",
            "maxLength": 32768,
            "minLength": 1,
            "description": "Nonempty searchable document text."
          },
          "aliases": {
            "type": "string",
            "maxLength": 8192,
            "description": "Optional synonyms and alternative task descriptions."
          },
          "uri": {
            "type": "string",
            "maxLength": 1024,
            "description": "Optional source reference, never fetched automatically."
          }
        },
        "required": [
          "id",
          "title",
          "text"
        ],
        "additionalProperties": false
      }
    },
    "ids": {
      "type": "array",
      "minItems": 1,
      "maxItems": 32,
      "items": {
        "type": "string",
        "maxLength": 256,
        "pattern": "^[A-Za-z0-9][A-Za-z0-9_.:/#-]{0,255}$",
        "description": "Exact opaque document ID. It is a database key, never a file path."
      }
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "collection",
    "action"
  ],
  "additionalProperties": false
}
```

## dikciz_search_query

Search offline collections with weighted BM25, exact identifier boosts, prefixes, authored aliases and bounded typo correction. No model or network. collection defaults to docs; tools returns current input schemas permitted by trusted turn access. Results include excerpts and IDs for searchRead. Plain text only, no SQL or FTS operators. Follow total/hasMore with limit and offset.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 20,
      "default": 5
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 10000,
      "default": 0
    },
    "collection": {
      "type": "string",
      "maxLength": 64,
      "pattern": "^[a-z][a-z0-9_.-]{0,63}$",
      "description": "Lowercase collection name. docs and tools are protected built-ins; other names scope explicitly indexed owner text.",
      "default": "docs"
    },
    "query": {
      "type": "string",
      "maxLength": 512,
      "minLength": 1,
      "description": "Plain text task, identifier or keywords, up to 16 normalized terms."
    },
    "fuzzy": {
      "type": "boolean",
      "default": true
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "query"
  ],
  "additionalProperties": false
}
```

## dikciz_search_read

Read the full text of one exact search result ID in a collection. docs is the default. tools returns a live contract only when permitted by trusted turn access. Never reads paths or external URLs.

```json
{
  "type": "object",
  "properties": {
    "collection": {
      "type": "string",
      "maxLength": 64,
      "pattern": "^[a-z][a-z0-9_.-]{0,63}$",
      "description": "Lowercase collection name. docs and tools are protected built-ins; other names scope explicitly indexed owner text.",
      "default": "docs"
    },
    "id": {
      "type": "string",
      "maxLength": 256,
      "pattern": "^[A-Za-z0-9][A-Za-z0-9_.:/#-]{0,255}$",
      "description": "Exact opaque document ID. It is a database key, never a file path."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "id"
  ],
  "additionalProperties": false
}
```

## dikciz_select_page

Select one page by its stable ID.

```json
{
  "type": "object",
  "properties": {
    "pageId": {
      "type": "string",
      "description": "Stable Dikciz page ID from dikciz_snapshot.",
      "maximum": 256
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "pageId"
  ]
}
```

## dikciz_sensor_readings

Read a bounded sample window for all hardware sensors, including multiple sensors of one type. sensorId comes from this inventory. Follow hasMore. Each sensor reports actual values, accuracy, timestamp or an explicit unavailable reason. Registrations stop when the request ends.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "sensorId": {
      "type": "integer",
      "minimum": 0,
      "maximum": 2147483647
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 5000,
      "default": 1000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_set_text

Set an editable text input or text widget by semantic ID.

```json
{
  "type": "object",
  "properties": {
    "semanticId": {
      "type": "string",
      "description": "Stable Dikciz semantic ID from dikciz_snapshot.",
      "maximum": 256
    },
    "text": {
      "type": "string",
      "description": "New editable input or text widget value.",
      "maximum": 8192
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "semanticId",
    "text"
  ]
}
```

## dikciz_shell

Run a command through su by default, or as the launcher app user when root is false.

```json
{
  "type": "object",
  "properties": {
    "command": {
      "type": "string",
      "description": "Command run by Dikciz. Root is requested by default.",
      "maximum": 8192
    },
    "root": {
      "type": "boolean",
      "description": "Run through su. Defaults to true. False runs as the launcher app user."
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "description": "Command timeout in milliseconds. Defaults to 30000.",
      "minimum": 1,
      "maximum": 2147483647,
      "default": 30000
    },
    "maxOutputBytes": {
      "type": "integer",
      "description": "Maximum combined stdout and stderr bytes. Defaults to 65536; maximum 1048576.",
      "minimum": 1,
      "maximum": 1048576,
      "default": 65536
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "command"
  ]
}
```

## dikciz_shell_session_forget

Remove one terminal shell session and its retained output. Refuses active sessions; stop and observe completion first.

```json
{
  "type": "object",
  "properties": {
    "streamId": {
      "type": "string",
      "maxLength": 36
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "streamId"
  ],
  "additionalProperties": false
}
```

## dikciz_shell_session_read

Read retained shell events from an integer cursor. Follow nextCursor/hasMore; historyGap/lostEvents exposes overwritten history. Optional waitMilliseconds long-polls for an event or completion. Pages also respect the shared response-byte budget.

```json
{
  "type": "object",
  "properties": {
    "streamId": {
      "type": "string",
      "maxLength": 36
    },
    "cursor": {
      "type": "integer",
      "minimum": 0,
      "maximum": 9223372036854775807,
      "default": 0
    },
    "waitMilliseconds": {
      "type": "integer",
      "minimum": 0,
      "maximum": 30000,
      "default": 0
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "streamId"
  ],
  "additionalProperties": false
}
```

## dikciz_shell_session_start

Start a process-owned shell session using the existing shell-stream runner. Read incremental events from any ordinary binding, including HTML/Lua, after transport disconnects. root defaults true; use false for the app sandbox. Output history is bounded and gaps are explicit. Sessions end with the app process.

```json
{
  "type": "object",
  "properties": {
    "command": {
      "type": "string",
      "maxLength": 8192
    },
    "root": {
      "type": "boolean",
      "default": true
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 0,
      "maximum": 2147483647,
      "default": 30000
    },
    "chunkBytes": {
      "type": "integer",
      "minimum": 1,
      "maximum": 65536,
      "default": 4096
    },
    "bufferBytes": {
      "type": "integer",
      "minimum": 65536,
      "maximum": 8388608,
      "default": 1048576
    },
    "maximumEvents": {
      "type": "integer",
      "minimum": 1,
      "maximum": 4096,
      "default": 1024
    },
    "retentionMinutes": {
      "type": "integer",
      "minimum": 1,
      "maximum": 1440,
      "default": 60
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "command"
  ],
  "additionalProperties": false
}
```

## dikciz_shell_session_stop

Request cancellation of one process-owned shell session. Read until active=false and inspect the terminal event. Cancellation does not roll back side effects.

```json
{
  "type": "object",
  "properties": {
    "streamId": {
      "type": "string",
      "maxLength": 36
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "streamId"
  ],
  "additionalProperties": false
}
```

## dikciz_shell_sessions_list

List process-owned active and retained terminal shell sessions, newest first, without output contents.

```json
{
  "type": "object",
  "properties": {
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_shell_stream

Stream command output through SSE log notifications. Opt in with params._meta io.modelcontextprotocol/logLevel=info. timeoutMilliseconds=0 runs until cancellation. Root defaults to true; set false for app permissions.

```json
{
  "type": "object",
  "properties": {
    "command": {
      "type": "string",
      "description": "Command run by Dikciz. Root is requested by default.",
      "maximum": 8192
    },
    "root": {
      "type": "boolean",
      "description": "Run through su. Defaults to true. False runs as the launcher app user."
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "description": "Deadline in milliseconds. Zero, the default, keeps streaming until stopped or disconnected.",
      "minimum": 0,
      "maximum": 2147483647,
      "default": 0
    },
    "chunkBytes": {
      "type": "integer",
      "description": "Maximum bytes per output chunk. Output is base64 encoded without a total output limit.",
      "minimum": 1,
      "maximum": 65536,
      "default": 4096
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "command"
  ]
}
```

## dikciz_shell_stream_stop

Stop a stream owned by the same MCP session.

```json
{
  "type": "object",
  "properties": {
    "streamId": {
      "type": "string",
      "description": "Stream UUID from the started notification.",
      "maxLength": 36
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "streamId"
  ]
}
```

## dikciz_sim_list

Read active SIM subscriptions and own phone numbers directly, without opening Settings. Returns subscriptionId, slotIndex, displayName, carrierName, nullable phoneNumber and phoneNumberStatus (available, unavailable, permission_required, access_denied or unsupported), plus defaultSmsSubscriptionId and phoneNumberPermissionGranted. Requires READ_PHONE_STATE; numbers additionally need READ_PHONE_NUMBERS. Missing number permission preserves SIM metadata and returns targeted permissionRequest; autoRequestPermissions opts into owner review. Android may not supply a number. Follow hasMore for pagination.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_sms_send

Submit an SMS through Android SmsManager. Required idempotencyKey prevents duplicate sends across retries and launcher restarts. Poll smsStatus for sent and delivery outcomes; accepted is not sent. Select a SIM explicitly when there is no default.

```json
{
  "type": "object",
  "properties": {
    "idempotencyKey": {
      "type": "string",
      "description": "Unique caller key. Reusing it with different content fails. Retained until app data is cleared.",
      "maxLength": 80
    },
    "recipient": {
      "type": "string",
      "description": "Dialable phone number. Spaces, parentheses, dots and hyphens are normalized.",
      "maxLength": 64
    },
    "body": {
      "type": "string",
      "description": "SMS body. Android splits multipart messages.",
      "maxLength": 4096
    },
    "subscriptionId": {
      "type": "integer",
      "minimum": 0,
      "maximum": 2147483647
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "idempotencyKey",
    "recipient",
    "body"
  ],
  "additionalProperties": false
}
```

## dikciz_sms_status

Read the persisted state for an SMS submitted by Dikciz, without returning its recipient or body. Delivery remains unknown until a carrier callback arrives.

```json
{
  "type": "object",
  "properties": {
    "idempotencyKey": {
      "type": "string",
      "description": "Key from smsSend.",
      "maxLength": 80
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "idempotencyKey"
  ],
  "additionalProperties": false
}
```

## dikciz_snapshot

Read the current semantic launcher UI tree.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_sos_control

arm starts missed-check-in monitoring; trigger starts the configured cancellation grace; checkIn resets the deadline; cancel stops future sends. arm/trigger require confirm=true, SMS, precise location and notification access. Foreground start required. Android can delay alarms or stop execution. On reboot, an armed run needs owner review. No emergency service is called.

```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "minLength": 1,
      "maxLength": 16,
      "enum": [
        "arm",
        "trigger",
        "checkIn",
        "cancel"
      ]
    },
    "confirm": {
      "type": "boolean"
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "action"
  ],
  "additionalProperties": false
}
```

## dikciz_sos_plan_save

Save an inactive SOS plan with exact recipient phone numbers, message template and bounded check-in/update timings. Does not arm or send. Tokens: {location}, {accuracyMeters}, {timestamp}. Cancel an active run before editing. Contacts are never modified.

```json
{
  "type": "object",
  "properties": {
    "contacts": {
      "type": "array",
      "minItems": 1,
      "maxItems": 10,
      "items": {
        "type": "object",
        "properties": {
          "address": {
            "type": "string",
            "minLength": 1,
            "maxLength": 64
          },
          "label": {
            "type": "string",
            "minLength": 1,
            "maxLength": 80
          }
        },
        "required": [
          "address"
        ],
        "additionalProperties": false
      }
    },
    "message": {
      "type": "string",
      "minLength": 1,
      "maxLength": 1200
    },
    "checkInSeconds": {
      "type": "integer",
      "minimum": 60,
      "maximum": 604800,
      "default": 3600
    },
    "graceSeconds": {
      "type": "integer",
      "minimum": 5,
      "maximum": 3600,
      "default": 60
    },
    "updateSeconds": {
      "type": "integer",
      "minimum": 60,
      "maximum": 86400,
      "default": 600
    },
    "maximumUpdates": {
      "type": "integer",
      "minimum": 1,
      "maximum": 24,
      "default": 3
    },
    "subscriptionId": {
      "type": "integer",
      "minimum": 0,
      "maximum": 2147483647
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "contacts"
  ],
  "additionalProperties": false
}
```

## dikciz_sos_preview

Preview exact contacts, message and available cached location without sending or arming.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_sos_status

Read the saved SOS plan, durable run state, deadline and idempotency keys for smsStatus delivery inspection. Accepted updates are not confirmed delivery. warning reports observed countdown presentation, unlocked-screen visibility, remainingSeconds, alarmVolume and speechState. Warning audio follows Android volume/DND and never proves the owner heard it.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_speech_languages

Query the selected recognition provider for paginated installed, pending, downloadable on-device and online languages. Android 13+. No microphone capture. Provider support and errors are explicit.

```json
{
  "type": "object",
  "properties": {
    "mode": {
      "type": "string",
      "maxLength": 16,
      "description": "onDevice by default; system explicitly selects the installed system provider."
    },
    "language": {
      "type": "string",
      "maxLength": 64,
      "description": "BCP-47 recognition language; inventory can depend on this request."
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_speech_model_download

Explicitly request recognition-model download and wait for available/scheduled/error. May use network data and Android owner confirmation. Android 14+. Scheduled does not mean available. Timeout cannot cancel provider work. Poll speechModelStatus for progress while this request runs.

```json
{
  "type": "object",
  "properties": {
    "mode": {
      "type": "string",
      "maxLength": 16,
      "description": "onDevice by default; system explicitly selects the installed system provider."
    },
    "language": {
      "type": "string",
      "maxLength": 64,
      "description": "BCP-47 recognition language; inventory can depend on this request."
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "language"
  ],
  "additionalProperties": false
}
```

## dikciz_speech_model_status

Read current/last process-owned model request and provider progress. Does not resume observation after timeout, scheduled callback or process death.

```json
{
  "type": "object",
  "properties": {
    "sessionId": {
      "type": "string",
      "maxLength": 36
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_speech_recognition_status

Discover Android speech recognition and strict on-device availability without recording. Reports whether recognition is active.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_speech_recognition_stop

Cancel the active speech recognition request and destroy its Android recognizer. Returns idle when no request is active.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_speech_recognize

Recognize microphone speech and return text, alternatives and confidence. mode defaults to onDevice, which never falls back to a network recognizer. system explicitly allows the installed service to use its network backend. Requires microphone permission and foreground eligibility. Use speechRecognitionStop to cancel.

```json
{
  "type": "object",
  "properties": {
    "language": {
      "type": "string",
      "description": "BCP-47 language tag, for example en-US; defaults to device language.",
      "maxLength": 64
    },
    "mode": {
      "type": "string",
      "description": "onDevice or system.",
      "maxLength": 16
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_speech_speak

Speak text through the Android text-to-speech engine and device audio output. Returns only after synthesis completes. Does not inject speech into cellular calls.

```json
{
  "type": "object",
  "properties": {
    "text": {
      "type": "string",
      "description": "Text spoken by the phone.",
      "maxLength": 2000
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "language": {
      "type": "string",
      "description": "BCP-47 language; defaults to engine selection.",
      "maxLength": 64
    },
    "voiceName": {
      "type": "string",
      "description": "Exact name from speechVoices. Voice selection takes precedence over language.",
      "maxLength": 256
    },
    "ratePercent": {
      "type": "integer",
      "minimum": 25,
      "maximum": 400,
      "default": 100
    },
    "pitchPercent": {
      "type": "integer",
      "minimum": 25,
      "maximum": 400,
      "default": 100
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "text"
  ],
  "additionalProperties": false
}
```

## dikciz_speech_stop

Stop active Dikciz text-to-speech output. The original request returns speech_cancelled. Idle is explicit.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_speech_stream_forget

Remove one completed speech stream and its replayed transcripts. Active sessions must stop first.

```json
{
  "type": "object",
  "properties": {
    "streamId": {
      "type": "string",
      "maxLength": 36
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "streamId"
  ],
  "additionalProperties": false
}
```

## dikciz_speech_stream_read

Read bounded partial/final speech events with exclusive cursor, historyGap, lostEvents and terminal state. Does not start capture. Retained in memory for five minutes after completion. Owner only.

```json
{
  "type": "object",
  "properties": {
    "streamId": {
      "type": "string",
      "maxLength": 36
    },
    "cursor": {
      "type": "integer",
      "minimum": 0,
      "maximum": 9007199254740991
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200
    },
    "waitMilliseconds": {
      "type": "integer",
      "minimum": 0,
      "maximum": 30000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "streamId"
  ],
  "additionalProperties": false
}
```

## dikciz_speech_stream_start

Start foreground microphone speech recognition with partial-result replay. Requires Android microphone access and a recognition provider. Accepted is not recognized; read state/events. Provider may emit only a final result. No automatic fallback from onDevice to system.

```json
{
  "type": "object",
  "properties": {
    "mode": {
      "type": "string",
      "maxLength": 16,
      "enum": [
        "onDevice",
        "system"
      ],
      "default": "onDevice"
    },
    "language": {
      "type": "string",
      "maxLength": 64
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1000,
      "maximum": 120000,
      "default": 30000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_speech_stream_stop

Stop one owned speech session and report actual cleanup state. Does not cancel another session. Owner only.

```json
{
  "type": "object",
  "properties": {
    "streamId": {
      "type": "string",
      "maxLength": 36
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "streamId"
  ],
  "additionalProperties": false
}
```

## dikciz_speech_streams_list

List at most four retained speech streams without transcript payloads.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_speech_synthesize

Synthesize text into a saved WAV audio file without playing it. Shares voice, language, rate, pitch, timeout and cancellation with speechSpeak. Read the saved path through fileRead/download.

```json
{
  "type": "object",
  "properties": {
    "text": {
      "type": "string",
      "description": "Text spoken by the phone.",
      "maxLength": 2000
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "language": {
      "type": "string",
      "description": "BCP-47 language; defaults to engine selection.",
      "maxLength": 64
    },
    "voiceName": {
      "type": "string",
      "description": "Exact name from speechVoices. Voice selection takes precedence over language.",
      "maxLength": 256
    },
    "ratePercent": {
      "type": "integer",
      "minimum": 25,
      "maximum": 400,
      "default": 100
    },
    "pitchPercent": {
      "type": "integer",
      "minimum": 25,
      "maximum": 400,
      "default": 100
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "text"
  ],
  "additionalProperties": false
}
```

## dikciz_speech_transcribe_file

Upload one audio file to the explicitly selected OpenAI-compatible provider's audio/transcriptions endpoint. Uses its encrypted saved API key. Requires profileId, model and path. Sends file contents outside the phone; never opens the microphone. Provider support and usage charges apply. Owner only.

```json
{
  "type": "object",
  "properties": {
    "path": {
      "type": "string",
      "maxLength": 4096
    },
    "profileId": {
      "type": "string",
      "maxLength": 80
    },
    "model": {
      "type": "string",
      "maxLength": 256
    },
    "language": {
      "type": "string",
      "maxLength": 2
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1000,
      "maximum": 300000,
      "default": 30000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "path",
    "profileId",
    "model"
  ],
  "additionalProperties": false
}
```

## dikciz_speech_voices

List voices from the installed Android TTS engine, including language, quality, latency and network requirement. Follow hasMore.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_system_setting

Read or write an allowlisted system setting. Set requires owner-granted Modify system settings and returns read-back.

```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "description": "get or set.",
      "maxLength": 16
    },
    "setting": {
      "type": "string",
      "description": "brightness, automaticBrightness, automaticRotation, rotation or screenTimeoutMilliseconds.",
      "maxLength": 64
    },
    "value": {
      "type": "integer",
      "minimum": 0,
      "maximum": 2147483647
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "action",
    "setting"
  ],
  "additionalProperties": false
}
```

## dikciz_tap

Tap one actionable semantic ID.

```json
{
  "type": "object",
  "properties": {
    "semanticId": {
      "type": "string",
      "description": "Stable Dikciz semantic ID from dikciz_snapshot.",
      "maximum": 256
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "semanticId"
  ]
}
```

## dikciz_torch_control

Observe flash-camera torch state through bounded Android callbacks or submit on/off. Unknown and unavailable are explicit. Submission does not claim physical light verification.

```json
{
  "type": "object",
  "properties": {
    "durationMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 5000,
      "description": "On only: automatically request torch off after this duration, independent of WebView timers."
    },
    "action": {
      "type": "string",
      "maxLength": 16,
      "description": "status, on or off."
    },
    "cameraId": {
      "type": "string",
      "maxLength": 128,
      "description": "Flash-capable camera ID; status defaults to all flash cameras."
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 5000,
      "default": 1000,
      "description": "Status callback wait only."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "action"
  ],
  "additionalProperties": false
}
```

## dikciz_ui_dump

Read the current semantic launcher UI tree.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false
}
```

## dikciz_usb_devices

List attached USB host devices and per-device access. Does not request USB permission or transfer bytes.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_usb_request_access

Request Android owner approval for one currently attached USB-host device. Poll usbDevices.permissionGranted, then retry explicitly. Access expires when the device disconnects.

```json
{
  "type": "object",
  "properties": {
    "deviceId": {
      "type": "integer",
      "minimum": 0,
      "maximum": 2147483647
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "deviceId"
  ],
  "additionalProperties": false
}
```

## dikciz_usb_transfer

Perform one bounded USB control or bulk transfer using owner-granted access. Use descriptors from usbDevices. Select length for input or dataBase64 for output. Connections and claimed interfaces are always released. Failed writes can have partial effects; do not retry automatically.

```json
{
  "type": "object",
  "properties": {
    "deviceId": {
      "type": "integer",
      "minimum": 0,
      "maximum": 2147483647
    },
    "kind": {
      "type": "string",
      "description": "control or bulk.",
      "maxLength": 16
    },
    "interfaceIndex": {
      "type": "integer",
      "minimum": 0,
      "maximum": 255
    },
    "endpointAddress": {
      "type": "integer",
      "minimum": 0,
      "maximum": 255
    },
    "requestType": {
      "type": "integer",
      "minimum": 0,
      "maximum": 255
    },
    "request": {
      "type": "integer",
      "minimum": 0,
      "maximum": 255
    },
    "value": {
      "type": "integer",
      "minimum": 0,
      "maximum": 65535
    },
    "index": {
      "type": "integer",
      "minimum": 0,
      "maximum": 65535
    },
    "length": {
      "type": "integer",
      "minimum": 0,
      "maximum": 65536
    },
    "dataBase64": {
      "type": "string",
      "description": "Base64 bytes for an output transfer, at most 65536 decoded bytes.",
      "maxLength": 87384
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "deviceId",
    "kind"
  ],
  "additionalProperties": false
}
```

## dikciz_ussd_request

Send one complete numeric star/hash USSD code through Android TelephonyManager and observe its carrier response. Requires CALL_PHONE. Optional subscriptionId defaults to the voice SIM. USSD can change carrier services or incur charges. Timeout/interruption cannot cancel network work; do not repeat an uncertain request automatically. Use jobStart for reconnectable duplicate-protected submission.

```json
{
  "type": "object",
  "properties": {
    "code": {
      "type": "string",
      "minLength": 2,
      "maxLength": 182,
      "description": "Full USSD code beginning with star/hash and ending with hash; digits, star, hash and plus only."
    },
    "subscriptionId": {
      "type": "integer",
      "minimum": 0,
      "maximum": 2147483647
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "maximumResponseCharacters": {
      "type": "integer",
      "minimum": 1,
      "maximum": 65536,
      "default": 4096,
      "description": "Maximum returned Unicode code points; responseTruncated reports shortening."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "code"
  ],
  "additionalProperties": false
}
```

## dikciz_vibration_control

Discover vibrator and predefined-effect support; submit a bounded pulse, waveform, predefined effect or cancellation. Android settings may suppress physical vibration.

```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "maxLength": 16,
      "description": "status, vibrate, waveform, effect or cancel."
    },
    "durationMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 10000,
      "default": 250
    },
    "amplitude": {
      "type": "integer",
      "minimum": -1,
      "maximum": 255,
      "default": -1
    },
    "timingsMilliseconds": {
      "type": "array",
      "items": {
        "type": "integer",
        "minimum": 0,
        "maximum": 10000
      },
      "minItems": 1,
      "maxItems": 64
    },
    "amplitudes": {
      "type": "array",
      "items": {
        "type": "integer",
        "minimum": -1,
        "maximum": 255
      },
      "minItems": 1,
      "maxItems": 64
    },
    "effect": {
      "type": "string",
      "maxLength": 16,
      "description": "click, doubleClick, heavyClick or tick. Android may use its predefined fallback."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "action"
  ],
  "additionalProperties": false
}
```

## dikciz_wait_for

Wait up to a bounded interval for one semantic ID to appear in the launcher snapshot.

```json
{
  "type": "object",
  "properties": {
    "semanticId": {
      "type": "string",
      "description": "Stable Dikciz semantic ID from dikciz_snapshot.",
      "maximum": 256
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "description": "Maximum time to wait for the semantic ID, in milliseconds.",
      "minimum": 1,
      "maximum": 5000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "semanticId",
    "timeoutMilliseconds"
  ]
}
```

## dikciz_widget_get

Read one widget by its stable H/V widget address.

```json
{
  "type": "object",
  "properties": {
    "widgetAddress": {
      "type": "string",
      "description": "Stable Dikciz widget address in 1H2V-widget-id form.",
      "maximum": 256
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "widgetAddress"
  ]
}
```

## dikciz_widget_move

Move one widget to an explicit grid rectangle. Rejects with grid_collision or grid_bounds and writes nothing.

```json
{
  "type": "object",
  "properties": {
    "widgetAddress": {
      "type": "string",
      "description": "Stable Dikciz widget address in 1H2V-widget-id form.",
      "maximum": 256
    },
    "cell": {
      "type": "object",
      "description": "Grid rectangle with column, row, columnSpan, and rowSpan."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "widgetAddress",
    "cell"
  ]
}
```

## dikciz_widget_resize

Resize one widget to an explicit grid rectangle. Rejects with grid_collision or grid_bounds and writes nothing.

```json
{
  "type": "object",
  "properties": {
    "widgetAddress": {
      "type": "string",
      "description": "Stable Dikciz widget address in 1H2V-widget-id form.",
      "maximum": 256
    },
    "cell": {
      "type": "object",
      "description": "Grid rectangle with column, row, columnSpan, and rowSpan."
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "description": "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.",
      "default": false
    }
  },
  "additionalProperties": false,
  "required": [
    "widgetAddress",
    "cell"
  ]
}
```

## dikciz_wifi_connection_start

Request an owner-approved local-only Wi-Fi connection and retain its callback. Android may reject or time out. Poll wifiConnectionStatus. A returned networkHandle identifies this process-scoped network; the phone's default internet route is not changed. Start with Dikciz foreground.

```json
{
  "type": "object",
  "properties": {
    "ssid": {
      "type": "string",
      "minLength": 1,
      "maxLength": 32
    },
    "security": {
      "type": "string",
      "minLength": 1,
      "maxLength": 16,
      "enum": [
        "open",
        "wpa2",
        "wpa3"
      ]
    },
    "passphrase": {
      "type": "string",
      "minLength": 1,
      "maxLength": 63
    },
    "maximumDurationMilliseconds": {
      "type": "integer",
      "minimum": 1000,
      "maximum": 3600000,
      "default": 300000
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1000,
      "maximum": 120000,
      "default": 30000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "ssid"
  ],
  "additionalProperties": false
}
```

## dikciz_wifi_connection_status

Read actual callback state for a network request or Android save approval; does not connect.

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "minLength": 1,
      "maxLength": 80
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "id"
  ],
  "additionalProperties": false
}
```

## dikciz_wifi_connection_stop

Release the exact local network request; does not remove saved Wi-Fi networks.

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "minLength": 1,
      "maxLength": 80
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "id"
  ],
  "additionalProperties": false
}
```

## dikciz_wifi_connections_list

List retained local network/hotspot requests with pagination. Hotspot credentials are owner-only.

```json
{
  "type": "object",
  "properties": {
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_wifi_hotspot_start

Start a bounded Android local-only hotspot while foreground. Android selects SSID/passphrase and can reject the request. Poll wifiHotspotStatus. This does not enable cellular internet tethering, which ordinary apps cannot silently control.

```json
{
  "type": "object",
  "properties": {
    "maximumDurationMilliseconds": {
      "type": "integer",
      "minimum": 1000,
      "maximum": 3600000,
      "default": 300000
    },
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1000,
      "maximum": 120000,
      "default": 30000
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_wifi_hotspot_status

Read retained local-only hotspot state and owner credentials. Starting requires wifiHotspotStart.

```json
{
  "type": "object",
  "properties": {
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_wifi_hotspot_stop

Close an exact local-only hotspot reservation.

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "minLength": 1,
      "maxLength": 80
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "id"
  ],
  "additionalProperties": false
}
```

## dikciz_wifi_networks_configure

Suggest a Wi-Fi network, remove this app's suggestions by SSID, or ask Android to save a network. Suggestions are not saved networks and never prove a connection. save requires owner approval and exposes its result through wifiConnectionStatus. Credentials are not logged or stored in public configuration.

```json
{
  "type": "object",
  "properties": {
    "ssid": {
      "type": "string",
      "minLength": 1,
      "maxLength": 32
    },
    "security": {
      "type": "string",
      "minLength": 1,
      "maxLength": 16,
      "enum": [
        "open",
        "wpa2",
        "wpa3"
      ]
    },
    "passphrase": {
      "type": "string",
      "minLength": 1,
      "maxLength": 63
    },
    "action": {
      "type": "string",
      "minLength": 1,
      "maxLength": 32,
      "enum": [
        "suggest",
        "removeSuggestion",
        "save"
      ]
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [
    "action",
    "ssid"
  ],
  "additionalProperties": false
}
```

## dikciz_wifi_scan

Request an Android Wi-Fi scan, wait for an updated-results broadcast and return the paginated cache with per-result freshness. Android can throttle or reject scans. Completion broadcasts are global, not caller-specific. Timeout releases the receiver but cannot cancel the platform scan. Fine location and enabled location services are required.

```json
{
  "type": "object",
  "properties": {
    "timeoutMilliseconds": {
      "type": "integer",
      "minimum": 1,
      "maximum": 120000,
      "default": 30000
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## dikciz_wifi_scan_results

Read cached Wi-Fi scan results with Android timestamps. Requires location access; does not initiate a scan or connect.

```json
{
  "type": "object",
  "properties": {
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    },
    "offset": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000000,
      "default": 0
    },
    "autoRequestPermissions": {
      "type": "boolean",
      "default": false,
      "description": "Ask the owner to grant missing Android access. Never grants access or retries automatically."
    }
  },
  "required": [],
  "additionalProperties": false
}
```
