# Command reference

<!-- Regenerate with make dikciz-command-docs from captured tools/list discovery. -->

This reference contains 128 discovered MCP tools and their complete input schemas. Discovery from the installed build is authoritative. Transport-independent procedures, Android permissions and results are described in the [automation guide](automation-control.md), [documentation index](index.md) and focused task guides. MCP tools use the same ordinary commands as HTTP, WebSocket, HTML and Lua.

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

## dikciz_event_journal_configure

Adjust process-owned replay count, byte and age limits. Defaults are 2048 events, 8 MiB and 60 minutes. Lower limits immediately evict old history; limits reset on process death. Does not change native sensor/location subscriptions.

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

Read process-owned automation-event replay epoch, cursors, retention and memory limits. Reading does not activate native subscriptions.

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

List active SIM subscription IDs, slots and default SMS subscription, using granted phone-state access.

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
