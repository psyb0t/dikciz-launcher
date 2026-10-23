# Android access by feature

Read automationStatus.deviceAccess.requirements for this package's actual setup, nativeDataStatus for provider grants/columns, and tools/list for implemented commands. A manifest declaration, Lua policy, network connection or permission notification is not a grant. Release/debug packages have independent grants.

- Home/public files need shared-storage access. Other file paths use Android app-UID access.
- Contacts/calendar operations need their declared read/write grants and supported resource fields. Synced-account rules apply.
- SMS history, receive and send have corresponding grants; SIM inventory can need phone-state access. No default SMS role. Calls, history and USSD need their own phone grants and platform eligibility.
- Clipboard reads can be foreground-restricted.
- Notifications/media-session access needs the notification listener; posting and Do Not Disturb access are separate.
- Cross-app UI needs Accessibility. Actual-display capture additionally needs Android11+; secure windows remain protected. Device Administration enables lockDevice.
- Ordinary sensors can work without runtime access; restricted sensors report their own requirement/reason. Health Connect daily steps needs that provider's read consent.
- Location needs appropriate precision/providers, and background access where used. Geocoding can send queries/coordinates to its provider. GNSS observation is not a location fix.
- Camera/microphone need grants, foreground eligibility and exclusive hardware ownership. PCM can continue behind another app under its microphone foreground service. Bounded recording, recognition and video have their documented foreground restrictions.
- TTS needs an installed engine; network-required voices/system recognition can contact a provider. Strict onDevice recognition never falls back.
- Bluetooth needs the applicable scan/connect/location access and enabled radio. Wi-Fi scans need location/services and remain throttled.
- NFC needs enabled hardware, foreground unlocked Dikciz and a compatible presented tag. USB host needs owner approval for the attached peripheral.
- Settings writes need Modify system settings and the allowlist. APK install needs source approval and Android confirmation where required.
- root:false shell runs as the app, not adb shell. Root defaults true and needs approved su. There is no silent fallback or Shizuku backend.

Use requirementId from discovery with requestDeviceAccess or autoRequestPermissions:true on an ordinary command. Inspect permissionRequest; tell the owner to review the phone, check grants again and explicitly retry. Full device access setup opens its own resumable task.

File APIs protect remote-auth.json, .bak/.new and canonical aliases, plus ancestor move/delete. An independent app-UID/root shell is not sandboxed by file-command restrictions. Enabled scripts/widgets have owner command authority; the returned-action policy does not sandbox context.request.

Native commands share all ordinary bindings. Network shellStream push is transport-only; shell sessions, event journal and PCM use shared cursor reads with bounded process memory. Durable jobs persist results and never replay interrupted work. Activity restart preserves the process; APK update/process death loses memory histories.

[Permissions](dikciz://docs/guides/permissions), [Security](dikciz://docs/guides/security), [Hardware](dikciz://docs/guides/hardware). Full feature map: https://github.com/psyb0t/dikciz-launcher/blob/main/docs/android-access.md
