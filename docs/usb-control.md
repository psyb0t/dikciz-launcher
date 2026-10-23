# USB host control

USB commands operate on peripherals attached to the Android phone acting as a USB host. They do not control the phone through its computer-facing ADB connection.

Use usbDevices to discover deviceId, permissionGranted and interface descriptors. Each interface reports its array index, ID, alternate setting, class/subclass and endpoints. Endpoint descriptors include address, direction, transfer type, maximum packet size and interval. Device IDs can change after reconnection.

## Owner access

usbRequestAccess, MCP dikciz_usb_request_access, takes deviceId. It requests Android approval for that attached device and returns user_action_required, or granted if access already exists. Check the phone, review the dialog, then query usbDevices.permissionGranted and explicitly retry. Access expires on disconnect. Android may additionally require camera permission for a video-class USB device.

usbTransfer with missing access returns permission_required and permissionRequest identifying usbDevice and deviceId. autoRequestPermissions=true starts the same owner-review flow. It never silently grants access or retries a transaction.

## Transfer fields

usbTransfer, MCP dikciz_usb_transfer, always requires deviceId and kind.

| Kind | Required fields |
| --- | --- |
| control | requestType (0..255), request (0..255), value (0..65535), index (0..65535). |
| bulk | interfaceIndex (0..255) and endpointAddress (0..255), from the discovered bulk endpoint. |

The requestType direction bit selects control input/output. The endpoint address selects bulk direction. Input requires length from 0 to 65536 and refuses dataBase64. Output requires dataBase64 with at most 65536 decoded bytes and refuses length. Unknown or action-inappropriate fields fail validation before USB work.

timeoutMilliseconds defaults to 30000 and accepts 1..120000. Successful completion returns bytesTransferred, deviceId, state=completed and Base64 input data, or null data for output. Zero transferred bytes is valid. A negative Android return becomes usb_transfer_failed; Android does not distinguish every timeout/detach failure here.

A bulk transfer claims its interface without forcing another driver to release it. A busy interface returns usb_interface_busy. Every opened connection is closed and a claimed interface is released on success or failure. Connections are scoped to one command; use a device-specific protocol appropriate to independent transfers.

Missing/detached identities return native_record_not_found. Missing host service returns native_unavailable. Failed writes can have partial external effects even when no successful byte count was returned. Inspect the peripheral before deciding whether a retry is safe. Android does not expose every USB host operation to ordinary applications, including arbitrary gadget/bootloader control.

These operations share schemas, execution and permission procedures across HTTP, WebSocket, MCP, HTML and Lua. See the [command reference](command-reference.md), [hardware inventory](hardware-controls.md) and [coverage](capability-coverage.md).
