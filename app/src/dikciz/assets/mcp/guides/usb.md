# USB host operations

usbDevices reports attached host peripherals, permissionGranted and interfaces/endpoints. Use returned deviceId, interface array index and endpoint address; reconnecting can change identities.

dikciz_usb_request_access(deviceId) requests the Android owner's USB dialog. Poll permissionGranted, then explicitly retry. It does not grant access silently. Access expires on disconnect.

dikciz_usb_transfer requires deviceId and kind. control requires requestType/request (0..255), value/index (0..65535). bulk requires interfaceIndex and endpointAddress from a bulk endpoint. Direction comes from requestType or endpoint address. Input requires length 0..65536 and refuses dataBase64. Output requires dataBase64, at most 65536 decoded bytes, and refuses length. timeoutMilliseconds defaults to 30000, range 1..120000.

Missing access returns permission_required with usbDevice/deviceId recovery. autoRequestPermissions=true starts owner review. Missing/detached device/interface/endpoint returns native_record_not_found. A busy interface is usb_interface_busy. Other failed/timed-out transfers are usb_transfer_failed. Writes can have partial effects; do not retry automatically.

Completed results report actual bytesTransferred and input dataBase64, or null output data. Connections close and claimed interfaces release after each command. This is Android USB-host access to peripherals, not ADB or fastboot control of the phone.

Same procedures through HTTP, WebSocket, MCP, HTML and Lua.
