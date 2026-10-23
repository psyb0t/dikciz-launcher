# Hardware controls

All operations share HTTP, WebSocket, MCP, HTML and Lua validation and Android access. Discover live schemas first.

- torchControl / dikciz_torch_control: required action status/on/off, optional cameraId. Status returns cameraIds and items from Android torch callbacks: enabled/disabled/unavailable, or unknown when none arrives. Optional cameraId filters. timeoutMilliseconds1..5000, default1000, applies only to status. complete means all selected cameras were observed; listeners are removed. On/off requires camera permission and returns submitted with requested enabled. Submission does not prove physical light output.
- vibrationControl / dikciz_vibration_control: required action status/vibrate/waveform/effect/cancel. Status reports available, amplitudeControl and effects with nativeSupport supported/unsupported/unknown. Duration 1..10000 ms, default250. Amplitude -1 (Android default) or1..255, never0. Android settings may suppress vibration. Waveform requires timingsMilliseconds (1..64 integers, 0..10000 each, total1..10000ms), optional matching amplitudes (-1 default, 0 off, 1..255). Without amplitudes timings alternate off/on starting off. No repeats. Effect names click/doubleClick/heavyClick/tick require Android10+; fallbackPossible is explicit. Status support requires Android11+, otherwise unknown. Wrong-action fields fail. Cancel stops Dikciz vibration.
- systemSetting / dikciz_system_setting: action get/set, setting, and value for set only. brightness0..255, automaticBrightness0..1, automaticRotation0..1, rotation0..3, screenTimeoutMilliseconds1..2147483647. Writes require Modify system settings and return stored read-back. Automatic mode or policy can override physical effects. Missing grant uses the common permissionRequest.

Inventories:

- bluetoothDevices / dikciz_bluetooth_devices: paired devices only, address/name/type/bondState. Bluetooth access required on Android12+. No scanning or connection.
- usbDevices / dikciz_usb_devices: attached host devices with IDs, class, interfaces and existing USB access. No permission prompt or transfers.
- wifiScanResults / dikciz_wifi_scan_results: cached SSID/BSSID, frequency, signal, capabilities and timestamp. Requires fine location. Does not start a scan or connect; Android may return an empty cache.
- audioDevices / dikciz_audio_devices: input/output IDs, names, types, sample rates and channels. No routing or playback.
- nfcStatus / dikciz_nfc_status: adapter available/enabled. No tag transactions.
- cameraCapabilities / dikciz_camera_capabilities: required cameraId from cameraList. JPEG sizes, maximum digital zoom, exposure compensation limits and autofocus modes. No capture.

Bluetooth/USB/Wi-Fi/audio accept limit1..200 (default50), offset0..1000000 (default0). Follow hasMore using offset plus returned count. Results include total. Hardware changes can change inventory between calls. No adapter is an explicit unavailable state.

[Permissions](dikciz://docs/guides/permissions), [Hardware](dikciz://docs/guides/hardware), [Speech](dikciz://docs/guides/speech).
