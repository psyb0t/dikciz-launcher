---
title: "Token permission reference"
description: "Look up every named token grant and exact command, action and resource selector, including broad authority, validation and access errors."
permalink: /token-permissions/
nav_order: 2
parent: "Remote control"
---

# Token permission reference

<!-- Regenerate with make dikciz-permission-docs from installed discovery. -->

Every current remote grant is listed below. Grant IDs are exact and case-sensitive. Existing named grants remain compatible; command grants add explicit operation/action/resource access. HTTP, WebSocket and MCP share discovery and enforcement. Android permissions are separate. See [token setup](remote-access-tokens.md) and the [developer manual](developer-manual.md).

## Scope rules

Grants are additive alternatives. A broad grant is not narrowed by adding a narrower one. owner must be selected alone. command.<type> allows every accepted argument of that command. Suffixes such as .action.stat and .resource.sms require those exact explicit selector values. Combined resource/action suffixes require both. Other required parameters and validation still apply. Invalid direct calls fail before Android permission prompts or effects.

Operation scopes apply across the device, not to a particular file path, package, app ID, session or SDK member unless that operation explicitly enforces such ownership. Explicit launcher UI-action grants can reach Dikciz settings; ui.control remains restricted to other apps. File writes/moves/copies into watched source or configuration can reload executable code. Code/source installation, script/app invocation, generic SDK, shell, intents, configuration changes and credential administration can confer broad owner-equivalent authority. A camera-start grant does not imply image-stream-read or file-read access. Select related result/stop commands separately. Jobs additionally require the nested command's grants and enforce caller ownership of records.

Basic device/control/authentication status, self access information and packaged documentation/tool search remain available to valid authenticated clients. These baseline tools do not grant phone mutations or owner search collections. Local owner bindings do not use a bearer token. Read-only chat independently rejects mutations. Existing events.read receives the shared event feed; granting a journal command does not enable live WebSocket broadcasts.

## Named feature grants

| Grant | Name | Definition | MCP operations |
| --- | --- | --- | --- |
| `owner` | Full owner access | All operations, including shell, SDK, scripting, configuration, installation and access-token administration. | All operations |
| `sms.read` | Read SMS | Read SMS history and sender/message data through the SMS provider. | [dikciz_content_query](command-reference.md#dikciz_content_query) |
| `sms.send` | Send SMS | Send tracked SMS, read send receipts and select a SIM. Does not grant SMS history access. | [dikciz_sim_list](command-reference.md#dikciz_sim_list), [dikciz_sms_send](command-reference.md#dikciz_sms_send), [dikciz_sms_status](command-reference.md#dikciz_sms_status) |
| `sim.read` | Read SIMs and own numbers | Read SIM subscriptions and own phone numbers when Android permits. | [dikciz_sim_list](command-reference.md#dikciz_sim_list) |
| `contacts.read` | Read contacts | Read contacts, phone numbers, email addresses and contact data. | [dikciz_content_query](command-reference.md#dikciz_content_query) |
| `contacts.write` | Edit contacts | Create, change or delete contact records. Does not grant contact reads. | [dikciz_content_batch](command-reference.md#dikciz_content_batch), [dikciz_content_write](command-reference.md#dikciz_content_write) |
| `calendar.read` | Read calendars | Read calendars, agenda entries and reminders. | [dikciz_content_query](command-reference.md#dikciz_content_query) |
| `calendar.write` | Edit calendars | Create, change or delete calendar records. | [dikciz_content_batch](command-reference.md#dikciz_content_batch), [dikciz_content_write](command-reference.md#dikciz_content_write) |
| `calls.read` | Read calls | Read call history and current call state. | [dikciz_call_control](command-reference.md#dikciz_call_control), [dikciz_content_query](command-reference.md#dikciz_content_query) |
| `calls.control` | Control calls | Read state and place, answer or end calls. | [dikciz_call_control](command-reference.md#dikciz_call_control) |
| `sensors.read` | Read sensors | Read sensor inventory and bounded live samples. | [dikciz_sensor_readings](command-reference.md#dikciz_sensor_readings) |
| `screen.capture` | Capture screenshots | Read screenshots of the launcher and other visible apps. | [dikciz_display_screenshot](command-reference.md#dikciz_display_screenshot), [dikciz_screenshot](command-reference.md#dikciz_screenshot) |
| `ui.read` | Read UI components | Read visible launcher and Accessibility structures, text and semantic IDs. | [dikciz_accessibility_snapshot](command-reference.md#dikciz_accessibility_snapshot), [dikciz_find](command-reference.md#dikciz_find), [dikciz_snapshot](command-reference.md#dikciz_snapshot), [dikciz_ui_dump](command-reference.md#dikciz_ui_dump), [dikciz_wait_for](command-reference.md#dikciz_wait_for) |
| `ui.control` | Read and control other apps | Broad UI authority, including actions such as messaging inside other apps. Does not permit changing Dikciz itself. | [dikciz_accessibility_action](command-reference.md#dikciz_accessibility_action), [dikciz_accessibility_snapshot](command-reference.md#dikciz_accessibility_snapshot), [dikciz_find](command-reference.md#dikciz_find), [dikciz_snapshot](command-reference.md#dikciz_snapshot), [dikciz_ui_dump](command-reference.md#dikciz_ui_dump), [dikciz_wait_for](command-reference.md#dikciz_wait_for) |
| `apps.read` | Read installed apps | Read launchable apps, icons and package metadata. | [dikciz_app_catalogue](command-reference.md#dikciz_app_catalogue), [dikciz_app_icon](command-reference.md#dikciz_app_icon), [dikciz_package_info](command-reference.md#dikciz_package_info) |
| `apps.launch` | Open apps | Read app metadata, open apps and show their Android app-info screen. | [dikciz_app_action](command-reference.md#dikciz_app_action), [dikciz_app_catalogue](command-reference.md#dikciz_app_catalogue), [dikciz_app_icon](command-reference.md#dikciz_app_icon), [dikciz_launch_app](command-reference.md#dikciz_launch_app), [dikciz_package_info](command-reference.md#dikciz_package_info) |
| `device.read` | Read device measurements | Read battery, memory/storage, audio and camera inventories and device state. | [dikciz_audio_devices](command-reference.md#dikciz_audio_devices), [dikciz_camera_capabilities](command-reference.md#dikciz_camera_capabilities), [dikciz_camera_list](command-reference.md#dikciz_camera_list), [dikciz_device_state](command-reference.md#dikciz_device_state) |
| `device.control` | Control device settings | Control torch, vibration, brightness, rotation and screen timeout. | [dikciz_system_setting](command-reference.md#dikciz_system_setting), [dikciz_torch_control](command-reference.md#dikciz_torch_control), [dikciz_vibration_control](command-reference.md#dikciz_vibration_control) |
| `location.read` | Read location | Read permitted fixes, geocoding and satellite observations. | [dikciz_geocode](command-reference.md#dikciz_geocode), [dikciz_gnss_status](command-reference.md#dikciz_gnss_status), [dikciz_location_get](command-reference.md#dikciz_location_get) |
| `notifications.read` | Read notifications | Read notification contents and action metadata. | [dikciz_notifications_list](command-reference.md#dikciz_notifications_list) |
| `notifications.control` | Act on notifications | Read notifications, dismiss, snooze and send inline replies, which can send messages. | [dikciz_notification_manage](command-reference.md#dikciz_notification_manage), [dikciz_notifications_list](command-reference.md#dikciz_notifications_list) |
| `media.read` | Read media | Read media provider metadata and active media sessions. | [dikciz_content_query](command-reference.md#dikciz_content_query), [dikciz_media_sessions_list](command-reference.md#dikciz_media_sessions_list) |
| `files.read` | Read files | Read and search all files Dikciz can access, excluding protected credentials. | [dikciz_file_list](command-reference.md#dikciz_file_list), [dikciz_file_manage](command-reference.md#dikciz_file_manage), [dikciz_file_read](command-reference.md#dikciz_file_read) |
| `logs.read` | Read script logs | Read the sanitized script-log snapshot, which may contain script data. | [dikciz_script_logs](command-reference.md#dikciz_script_logs) |
| `events.read` | Read all automation events | Read dispatched event history and live launcher broadcasts, including payloads from every permitted Android source. | [dikciz_event_journal_read](command-reference.md#dikciz_event_journal_read), [dikciz_event_journal_status](command-reference.md#dikciz_event_journal_status) |
| `search.read` | Read owner search collections | Read explicitly indexed owner text in every custom search collection. | [dikciz_search_query](command-reference.md#dikciz_search_query), [dikciz_search_read](command-reference.md#dikciz_search_read) |
| `search.write` | Edit owner search collections | Upsert, remove or clear custom indexes. Built-in documentation and tools remain protected. | [dikciz_search_index](command-reference.md#dikciz_search_index) |
| `speech.output` | Speak text | Speak text, list TTS voices and stop speech output. | [dikciz_speech_speak](command-reference.md#dikciz_speech_speak), [dikciz_speech_stop](command-reference.md#dikciz_speech_stop), [dikciz_speech_voices](command-reference.md#dikciz_speech_voices) |
| `bluetooth.read` | Read Bluetooth devices | Read paired devices and perform bounded nearby-device scans. | [dikciz_bluetooth_devices](command-reference.md#dikciz_bluetooth_devices), [dikciz_bluetooth_scan](command-reference.md#dikciz_bluetooth_scan) |
| `wifi.read` | Read Wi-Fi networks | Read cached Wi-Fi networks and request a bounded fresh scan. | [dikciz_wifi_scan](command-reference.md#dikciz_wifi_scan), [dikciz_wifi_scan_results](command-reference.md#dikciz_wifi_scan_results) |
| `usb.read` | Read USB inventory | Read attached USB device and interface metadata. | [dikciz_usb_devices](command-reference.md#dikciz_usb_devices) |
| `nfc.read` | Read NFC tags | Read NFC status and bounded presented-tag data. No writes or raw transactions. | [dikciz_nfc_read](command-reference.md#dikciz_nfc_read), [dikciz_nfc_status](command-reference.md#dikciz_nfc_status) |

## Operation index

| Ordinary command | MCP tool and parameters |
| --- | --- |
| [accessibilityAction](#accessibilityaction) | [dikciz_accessibility_action](command-reference.md#dikciz_accessibility_action) |
| [accessibilitySnapshot](#accessibilitysnapshot) | [dikciz_accessibility_snapshot](command-reference.md#dikciz_accessibility_snapshot) |
| [activityCancel](#activitycancel) | [dikciz_activity_cancel](command-reference.md#dikciz_activity_cancel) |
| [activityRead](#activityread) | [dikciz_activity_read](command-reference.md#dikciz_activity_read) |
| [addWidget](#addwidget) | [dikciz_add_widget](command-reference.md#dikciz_add_widget) |
| [androidApiCallback](#androidapicallback) | [dikciz_android_api_callback](command-reference.md#dikciz_android_api_callback) |
| [androidApiDiscover](#androidapidiscover) | [dikciz_android_api_discover](command-reference.md#dikciz_android_api_discover) |
| [androidApiInvoke](#androidapiinvoke) | [dikciz_android_api_invoke](command-reference.md#dikciz_android_api_invoke) |
| [androidApiObjects](#androidapiobjects) | [dikciz_android_api_objects](command-reference.md#dikciz_android_api_objects) |
| [androidApiRead](#androidapiread) | [dikciz_android_api_read](command-reference.md#dikciz_android_api_read) |
| [androidApiRelease](#androidapirelease) | [dikciz_android_api_release](command-reference.md#dikciz_android_api_release) |
| [apkInstall](#apkinstall) | [dikciz_apk_install](command-reference.md#dikciz_apk_install) |
| [apkInstallCancel](#apkinstallcancel) | [dikciz_apk_install_cancel](command-reference.md#dikciz_apk_install_cancel) |
| [apkInstallSet](#apkinstallset) | [dikciz_apk_install_set](command-reference.md#dikciz_apk_install_set) |
| [apkInstallStatus](#apkinstallstatus) | [dikciz_apk_install_status](command-reference.md#dikciz_apk_install_status) |
| [appAction](#appaction) | [dikciz_app_action](command-reference.md#dikciz_app_action) |
| [appCatalogue](#appcatalogue) | [dikciz_app_catalogue](command-reference.md#dikciz_app_catalogue) |
| [appIcon](#appicon) | [dikciz_app_icon](command-reference.md#dikciz_app_icon) |
| [assistantLibrary](#assistantlibrary) | [dikciz_assistant_library](command-reference.md#dikciz_assistant_library) |
| [assistantLibraryManage](#assistantlibrarymanage) | [dikciz_assistant_library_manage](command-reference.md#dikciz_assistant_library_manage) |
| [assistantLibraryValidate](#assistantlibraryvalidate) | [dikciz_assistant_library_validate](command-reference.md#dikciz_assistant_library_validate) |
| [assistantRoutinePreview](#assistantroutinepreview) | [dikciz_assistant_routine_preview](command-reference.md#dikciz_assistant_routine_preview) |
| [assistantRoutineRun](#assistantroutinerun) | [dikciz_assistant_routine_run](command-reference.md#dikciz_assistant_routine_run) |
| [assistantRoutineSubscribe](#assistantroutinesubscribe) | [dikciz_assistant_routine_subscribe](command-reference.md#dikciz_assistant_routine_subscribe) |
| [assistantRoutines](#assistantroutines) | [dikciz_assistant_routines](command-reference.md#dikciz_assistant_routines) |
| [audioDevices](#audiodevices) | [dikciz_audio_devices](command-reference.md#dikciz_audio_devices) |
| [audioPlayback](#audioplayback) | [dikciz_audio_playback](command-reference.md#dikciz_audio_playback) |
| [automationDispatch](#automationdispatch) | [dikciz_automation_dispatch](command-reference.md#dikciz_automation_dispatch) |
| [automationHistory](#automationhistory) | [dikciz_automation_history](command-reference.md#dikciz_automation_history) |
| [automationHistoryEvent](#automationhistoryevent) | [dikciz_automation_history_event](command-reference.md#dikciz_automation_history_event) |
| [automationRuleDelete](#automationruledelete) | [dikciz_automation_rule_delete](command-reference.md#dikciz_automation_rule_delete) |
| [automationRulePreview](#automationrulepreview) | [dikciz_automation_rule_preview](command-reference.md#dikciz_automation_rule_preview) |
| [automationRuleSave](#automationrulesave) | [dikciz_automation_rule_save](command-reference.md#dikciz_automation_rule_save) |
| [automationRulesList](#automationruleslist) | [dikciz_automation_rules_list](command-reference.md#dikciz_automation_rules_list) |
| [automationServiceSync](#automationservicesync) | [dikciz_automation_service_sync](command-reference.md#dikciz_automation_service_sync) |
| [automationSimulate](#automationsimulate) | [dikciz_automation_simulate](command-reference.md#dikciz_automation_simulate) |
| [automationStatus](#automationstatus) | [dikciz_automation_status](command-reference.md#dikciz_automation_status) |
| [automationTrigger](#automationtrigger) | [dikciz_automation_trigger](command-reference.md#dikciz_automation_trigger) |
| [backupCreate](#backupcreate) | [dikciz_backup_create](command-reference.md#dikciz_backup_create) |
| [backupPreview](#backuppreview) | [dikciz_backup_preview](command-reference.md#dikciz_backup_preview) |
| [backupRestore](#backuprestore) | [dikciz_backup_restore](command-reference.md#dikciz_backup_restore) |
| [backupsList](#backupslist) | [dikciz_backups_list](command-reference.md#dikciz_backups_list) |
| [bluetoothDevices](#bluetoothdevices) | [dikciz_bluetooth_devices](command-reference.md#dikciz_bluetooth_devices) |
| [bluetoothExchange](#bluetoothexchange) | [dikciz_bluetooth_exchange](command-reference.md#dikciz_bluetooth_exchange) |
| [bluetoothGatt](#bluetoothgatt) | [dikciz_bluetooth_gatt](command-reference.md#dikciz_bluetooth_gatt) |
| [bluetoothPair](#bluetoothpair) | [dikciz_bluetooth_pair](command-reference.md#dikciz_bluetooth_pair) |
| [bluetoothScan](#bluetoothscan) | [dikciz_bluetooth_scan](command-reference.md#dikciz_bluetooth_scan) |
| [callAudio](#callaudio) | [dikciz_call_audio](command-reference.md#dikciz_call_audio) |
| [callControl](#callcontrol) | [dikciz_call_control](command-reference.md#dikciz_call_control) |
| [cameraCapabilities](#cameracapabilities) | [dikciz_camera_capabilities](command-reference.md#dikciz_camera_capabilities) |
| [cameraCapture](#cameracapture) | [dikciz_camera_capture](command-reference.md#dikciz_camera_capture) |
| [cameraList](#cameralist) | [dikciz_camera_list](command-reference.md#dikciz_camera_list) |
| [cameraVideoRecord](#cameravideorecord) | [dikciz_camera_video_record](command-reference.md#dikciz_camera_video_record) |
| [chatControl](#chatcontrol) | [dikciz_chat](command-reference.md#dikciz_chat) |
| [clipboardGet](#clipboardget) | [dikciz_clipboard_get](command-reference.md#dikciz_clipboard_get) |
| [clipboardSet](#clipboardset) | [dikciz_clipboard_set](command-reference.md#dikciz_clipboard_set) |
| [configGet](#configget) | [dikciz_config_get](command-reference.md#dikciz_config_get) |
| [configReplace](#configreplace) | [dikciz_config_replace](command-reference.md#dikciz_config_replace) |
| [configSeed](#configseed) | [dikciz_config_seed](command-reference.md#dikciz_config_seed) |
| [contentBatch](#contentbatch) | [dikciz_content_batch](command-reference.md#dikciz_content_batch) |
| [contentQuery](#contentquery) | [dikciz_content_query](command-reference.md#dikciz_content_query) |
| [contentWrite](#contentwrite) | [dikciz_content_write](command-reference.md#dikciz_content_write) |
| [controlStatus](#controlstatus) | [dikciz_control_status](command-reference.md#dikciz_control_status) |
| [customAppAlert](#customappalert) | [dikciz_custom_app_alert](command-reference.md#dikciz_custom_app_alert) |
| [customAppControl](#customappcontrol) | [dikciz_custom_app_control](command-reference.md#dikciz_custom_app_control) |
| [customAppEvent](#customappevent) | [dikciz_custom_app_event](command-reference.md#dikciz_custom_app_event) |
| [customAppInstall](#customappinstall) | [dikciz_custom_app_install](command-reference.md#dikciz_custom_app_install) |
| [customAppRead](#customappread) | [dikciz_custom_app_read](command-reference.md#dikciz_custom_app_read) |
| [customAppRuntime](#customappruntime) | [dikciz_custom_app_runtime](command-reference.md#dikciz_custom_app_runtime) |
| [customAppSchedule](#customappschedule) | [dikciz_custom_app_schedule](command-reference.md#dikciz_custom_app_schedule) |
| [customAppState](#customappstate) | [dikciz_custom_app_state](command-reference.md#dikciz_custom_app_state) |
| [customAppsList](#customappslist) | [dikciz_custom_apps_list](command-reference.md#dikciz_custom_apps_list) |
| [deviceConnectionClose](#deviceconnectionclose) | [dikciz_device_connection_close](command-reference.md#dikciz_device_connection_close) |
| [deviceConnectionExchange](#deviceconnectionexchange) | [dikciz_device_connection_exchange](command-reference.md#dikciz_device_connection_exchange) |
| [deviceConnectionOpen](#deviceconnectionopen) | [dikciz_device_connection_open](command-reference.md#dikciz_device_connection_open) |
| [deviceConnectionRead](#deviceconnectionread) | [dikciz_device_connection_read](command-reference.md#dikciz_device_connection_read) |
| [deviceConnectionsList](#deviceconnectionslist) | [dikciz_device_connections_list](command-reference.md#dikciz_device_connections_list) |
| [deviceInfo](#deviceinfo) | [dikciz_device_info](command-reference.md#dikciz_device_info) |
| [deviceState](#devicestate) | [dikciz_device_state](command-reference.md#dikciz_device_state) |
| [diagnostics](#diagnostics) | [dikciz_diagnostics](command-reference.md#dikciz_diagnostics) |
| [displayScreenshot](#displayscreenshot) | [dikciz_display_screenshot](command-reference.md#dikciz_display_screenshot) |
| [documentScan](#documentscan) | [dikciz_document_scan](command-reference.md#dikciz_document_scan) |
| [eventConsumerAck](#eventconsumerack) | [dikciz_event_consumer_ack](command-reference.md#dikciz_event_consumer_ack) |
| [eventConsumerForget](#eventconsumerforget) | [dikciz_event_consumer_forget](command-reference.md#dikciz_event_consumer_forget) |
| [eventConsumerRead](#eventconsumerread) | [dikciz_event_consumer_read](command-reference.md#dikciz_event_consumer_read) |
| [eventConsumersList](#eventconsumerslist) | [dikciz_event_consumers_list](command-reference.md#dikciz_event_consumers_list) |
| [eventJournalConfigure](#eventjournalconfigure) | [dikciz_event_journal_configure](command-reference.md#dikciz_event_journal_configure) |
| [eventJournalRead](#eventjournalread) | [dikciz_event_journal_read](command-reference.md#dikciz_event_journal_read) |
| [eventJournalStatus](#eventjournalstatus) | [dikciz_event_journal_status](command-reference.md#dikciz_event_journal_status) |
| [fileList](#filelist) | [dikciz_file_list](command-reference.md#dikciz_file_list) |
| [fileManage](#filemanage) | [dikciz_file_manage](command-reference.md#dikciz_file_manage) |
| [fileMkdir](#filemkdir) | [dikciz_file_mkdir](command-reference.md#dikciz_file_mkdir) |
| [fileRead](#fileread) | [dikciz_file_read](command-reference.md#dikciz_file_read) |
| [fileWrite](#filewrite) | [dikciz_file_write](command-reference.md#dikciz_file_write) |
| [find](#find) | [dikciz_find](command-reference.md#dikciz_find) |
| [geocode](#geocode) | [dikciz_geocode](command-reference.md#dikciz_geocode) |
| [geofenceDelete](#geofencedelete) | [dikciz_geofence_delete](command-reference.md#dikciz_geofence_delete) |
| [geofenceSave](#geofencesave) | [dikciz_geofence_save](command-reference.md#dikciz_geofence_save) |
| [geofencesList](#geofenceslist) | [dikciz_geofences_list](command-reference.md#dikciz_geofences_list) |
| [gnssStatus](#gnssstatus) | [dikciz_gnss_status](command-reference.md#dikciz_gnss_status) |
| [gridSet](#gridset) | [dikciz_grid_set](command-reference.md#dikciz_grid_set) |
| [healthPermissionsRequest](#healthpermissionsrequest) | [dikciz_health_permissions_request](command-reference.md#dikciz_health_permissions_request) |
| [healthRecordsAggregate](#healthrecordsaggregate) | [dikciz_health_records_aggregate](command-reference.md#dikciz_health_records_aggregate) |
| [healthRecordsCatalogue](#healthrecordscatalogue) | [dikciz_health_records_catalogue](command-reference.md#dikciz_health_records_catalogue) |
| [healthRecordsDelete](#healthrecordsdelete) | [dikciz_health_records_delete](command-reference.md#dikciz_health_records_delete) |
| [healthRecordsRead](#healthrecordsread) | [dikciz_health_records_read](command-reference.md#dikciz_health_records_read) |
| [healthRecordsWrite](#healthrecordswrite) | [dikciz_health_records_write](command-reference.md#dikciz_health_records_write) |
| [homeGet](#homeget) | [dikciz_home_get](command-reference.md#dikciz_home_get) |
| [htmlWidgetRendererCrash](#htmlwidgetrenderercrash) | [dikciz_html_widget_renderer_crash](command-reference.md#dikciz_html_widget_renderer_crash) |
| [imageRecognize](#imagerecognize) | [dikciz_image_recognize](command-reference.md#dikciz_image_recognize) |
| [imageStreamForget](#imagestreamforget) | [dikciz_image_stream_forget](command-reference.md#dikciz_image_stream_forget) |
| [imageStreamRead](#imagestreamread) | [dikciz_image_stream_read](command-reference.md#dikciz_image_stream_read) |
| [imageStreamStart](#imagestreamstart) | [dikciz_image_stream_start](command-reference.md#dikciz_image_stream_start) |
| [imageStreamStop](#imagestreamstop) | [dikciz_image_stream_stop](command-reference.md#dikciz_image_stream_stop) |
| [imageStreamsList](#imagestreamslist) | [dikciz_image_streams_list](command-reference.md#dikciz_image_streams_list) |
| [intent](#intent) | [dikciz_intent](command-reference.md#dikciz_intent) |
| [jobCancel](#jobcancel) | [dikciz_job_cancel](command-reference.md#dikciz_job_cancel) |
| [jobStart](#jobstart) | [dikciz_job_start](command-reference.md#dikciz_job_start) |
| [jobStatus](#jobstatus) | [dikciz_job_status](command-reference.md#dikciz_job_status) |
| [jobsList](#jobslist) | [dikciz_jobs_list](command-reference.md#dikciz_jobs_list) |
| [journeyControl](#journeycontrol) | [dikciz_journey_control](command-reference.md#dikciz_journey_control) |
| [journeyDelete](#journeydelete) | [dikciz_journey_delete](command-reference.md#dikciz_journey_delete) |
| [journeyRead](#journeyread) | [dikciz_journey_read](command-reference.md#dikciz_journey_read) |
| [journeysList](#journeyslist) | [dikciz_journeys_list](command-reference.md#dikciz_journeys_list) |
| [launchApp](#launchapp) | [dikciz_launch_app](command-reference.md#dikciz_launch_app) |
| [launcherHandoff](#launcherhandoff) | [dikciz_launcher_handoff](command-reference.md#dikciz_launcher_handoff) |
| [locationGet](#locationget) | [dikciz_location_get](command-reference.md#dikciz_location_get) |
| [longPress](#longpress) | [dikciz_long_press](command-reference.md#dikciz_long_press) |
| [mediaSessionsList](#mediasessionslist) | [dikciz_media_sessions_list](command-reference.md#dikciz_media_sessions_list) |
| [microphoneRecord](#microphonerecord) | [dikciz_microphone_record](command-reference.md#dikciz_microphone_record) |
| [microphoneSession](#microphonesession) | [dikciz_microphone_session](command-reference.md#dikciz_microphone_session) |
| [microphoneStreamForget](#microphonestreamforget) | [dikciz_microphone_stream_forget](command-reference.md#dikciz_microphone_stream_forget) |
| [microphoneStreamRead](#microphonestreamread) | [dikciz_microphone_stream_read](command-reference.md#dikciz_microphone_stream_read) |
| [microphoneStreamStart](#microphonestreamstart) | [dikciz_microphone_stream_start](command-reference.md#dikciz_microphone_stream_start) |
| [microphoneStreamStop](#microphonestreamstop) | [dikciz_microphone_stream_stop](command-reference.md#dikciz_microphone_stream_stop) |
| [microphoneStreamsList](#microphonestreamslist) | [dikciz_microphone_streams_list](command-reference.md#dikciz_microphone_streams_list) |
| [nativeDataStatus](#nativedatastatus) | [dikciz_native_data_status](command-reference.md#dikciz_native_data_status) |
| [nfcRead](#nfcread) | [dikciz_nfc_read](command-reference.md#dikciz_nfc_read) |
| [nfcStatus](#nfcstatus) | [dikciz_nfc_status](command-reference.md#dikciz_nfc_status) |
| [nfcTransceive](#nfctransceive) | [dikciz_nfc_transceive](command-reference.md#dikciz_nfc_transceive) |
| [nfcWrite](#nfcwrite) | [dikciz_nfc_write](command-reference.md#dikciz_nfc_write) |
| [notificationManage](#notificationmanage) | [dikciz_notification_manage](command-reference.md#dikciz_notification_manage) |
| [notificationsList](#notificationslist) | [dikciz_notifications_list](command-reference.md#dikciz_notifications_list) |
| [openAutomationSetup](#openautomationsetup) | [dikciz_open_automation_setup](command-reference.md#dikciz_open_automation_setup) |
| [packageInfo](#packageinfo) | [dikciz_package_info](command-reference.md#dikciz_package_info) |
| [remoteAccessInfo](#remoteaccessinfo) | [dikciz_remote_access_info](command-reference.md#dikciz_remote_access_info) |
| [remoteAuthDisable](#remoteauthdisable) | [dikciz_remote_auth_disable](command-reference.md#dikciz_remote_auth_disable) |
| [remoteAuthEnable](#remoteauthenable) | [dikciz_remote_auth_enable](command-reference.md#dikciz_remote_auth_enable) |
| [remoteAuthStatus](#remoteauthstatus) | [dikciz_remote_auth_status](command-reference.md#dikciz_remote_auth_status) |
| [remoteTokens](#remotetokens) | [dikciz_remote_tokens](command-reference.md#dikciz_remote_tokens) |
| [requestDeviceAccess](#requestdeviceaccess) | [dikciz_request_device_access](command-reference.md#dikciz_request_device_access) |
| [reset](#reset) | [dikciz_config_reset](command-reference.md#dikciz_config_reset) |
| [resourceControl](#resourcecontrol) | [dikciz_resource_control](command-reference.md#dikciz_resource_control) |
| [resourceUsage](#resourceusage) | [dikciz_resource_usage](command-reference.md#dikciz_resource_usage) |
| [restart](#restart) | [dikciz_restart](command-reference.md#dikciz_restart) |
| [screenshot](#screenshot) | [dikciz_screenshot](command-reference.md#dikciz_screenshot) |
| [scriptLogs](#scriptlogs) | [dikciz_script_logs](command-reference.md#dikciz_script_logs) |
| [scrollBy](#scrollby) | [dikciz_scroll_by](command-reference.md#dikciz_scroll_by) |
| [scrollTo](#scrollto) | [dikciz_scroll_to](command-reference.md#dikciz_scroll_to) |
| [searchCollections](#searchcollections) | [dikciz_search_collections](command-reference.md#dikciz_search_collections) |
| [searchIndex](#searchindex) | [dikciz_search_index](command-reference.md#dikciz_search_index) |
| [searchQuery](#searchquery) | [dikciz_search_query](command-reference.md#dikciz_search_query) |
| [searchRead](#searchread) | [dikciz_search_read](command-reference.md#dikciz_search_read) |
| [selectPage](#selectpage) | [dikciz_select_page](command-reference.md#dikciz_select_page) |
| [sensorReadings](#sensorreadings) | [dikciz_sensor_readings](command-reference.md#dikciz_sensor_readings) |
| [setText](#settext) | [dikciz_set_text](command-reference.md#dikciz_set_text) |
| [shell](#shell) | [dikciz_shell](command-reference.md#dikciz_shell) |
| [shellSessionForget](#shellsessionforget) | [dikciz_shell_session_forget](command-reference.md#dikciz_shell_session_forget) |
| [shellSessionRead](#shellsessionread) | [dikciz_shell_session_read](command-reference.md#dikciz_shell_session_read) |
| [shellSessionStart](#shellsessionstart) | [dikciz_shell_session_start](command-reference.md#dikciz_shell_session_start) |
| [shellSessionStop](#shellsessionstop) | [dikciz_shell_session_stop](command-reference.md#dikciz_shell_session_stop) |
| [shellSessionsList](#shellsessionslist) | [dikciz_shell_sessions_list](command-reference.md#dikciz_shell_sessions_list) |
| [shellStream](#shellstream) | [dikciz_shell_stream](command-reference.md#dikciz_shell_stream) |
| [shellStreamStop](#shellstreamstop) | [dikciz_shell_stream_stop](command-reference.md#dikciz_shell_stream_stop) |
| [simList](#simlist) | [dikciz_sim_list](command-reference.md#dikciz_sim_list) |
| [smsSend](#smssend) | [dikciz_sms_send](command-reference.md#dikciz_sms_send) |
| [smsStatus](#smsstatus) | [dikciz_sms_status](command-reference.md#dikciz_sms_status) |
| [snapshot](#snapshot) | [dikciz_snapshot](command-reference.md#dikciz_snapshot) |
| [sosControl](#soscontrol) | [dikciz_sos_control](command-reference.md#dikciz_sos_control) |
| [sosPlanSave](#sosplansave) | [dikciz_sos_plan_save](command-reference.md#dikciz_sos_plan_save) |
| [sosPreview](#sospreview) | [dikciz_sos_preview](command-reference.md#dikciz_sos_preview) |
| [sosStatus](#sosstatus) | [dikciz_sos_status](command-reference.md#dikciz_sos_status) |
| [speechLanguages](#speechlanguages) | [dikciz_speech_languages](command-reference.md#dikciz_speech_languages) |
| [speechModelDownload](#speechmodeldownload) | [dikciz_speech_model_download](command-reference.md#dikciz_speech_model_download) |
| [speechModelStatus](#speechmodelstatus) | [dikciz_speech_model_status](command-reference.md#dikciz_speech_model_status) |
| [speechRecognitionStatus](#speechrecognitionstatus) | [dikciz_speech_recognition_status](command-reference.md#dikciz_speech_recognition_status) |
| [speechRecognitionStop](#speechrecognitionstop) | [dikciz_speech_recognition_stop](command-reference.md#dikciz_speech_recognition_stop) |
| [speechRecognize](#speechrecognize) | [dikciz_speech_recognize](command-reference.md#dikciz_speech_recognize) |
| [speechSpeak](#speechspeak) | [dikciz_speech_speak](command-reference.md#dikciz_speech_speak) |
| [speechStop](#speechstop) | [dikciz_speech_stop](command-reference.md#dikciz_speech_stop) |
| [speechStreamForget](#speechstreamforget) | [dikciz_speech_stream_forget](command-reference.md#dikciz_speech_stream_forget) |
| [speechStreamRead](#speechstreamread) | [dikciz_speech_stream_read](command-reference.md#dikciz_speech_stream_read) |
| [speechStreamStart](#speechstreamstart) | [dikciz_speech_stream_start](command-reference.md#dikciz_speech_stream_start) |
| [speechStreamStop](#speechstreamstop) | [dikciz_speech_stream_stop](command-reference.md#dikciz_speech_stream_stop) |
| [speechStreamsList](#speechstreamslist) | [dikciz_speech_streams_list](command-reference.md#dikciz_speech_streams_list) |
| [speechSynthesize](#speechsynthesize) | [dikciz_speech_synthesize](command-reference.md#dikciz_speech_synthesize) |
| [speechTranscribeFile](#speechtranscribefile) | [dikciz_speech_transcribe_file](command-reference.md#dikciz_speech_transcribe_file) |
| [speechVoices](#speechvoices) | [dikciz_speech_voices](command-reference.md#dikciz_speech_voices) |
| [systemSetting](#systemsetting) | [dikciz_system_setting](command-reference.md#dikciz_system_setting) |
| [tap](#tap) | [dikciz_tap](command-reference.md#dikciz_tap) |
| [torchControl](#torchcontrol) | [dikciz_torch_control](command-reference.md#dikciz_torch_control) |
| [uiDump](#uidump) | [dikciz_ui_dump](command-reference.md#dikciz_ui_dump) |
| [usbDevices](#usbdevices) | [dikciz_usb_devices](command-reference.md#dikciz_usb_devices) |
| [usbRequestAccess](#usbrequestaccess) | [dikciz_usb_request_access](command-reference.md#dikciz_usb_request_access) |
| [usbTransfer](#usbtransfer) | [dikciz_usb_transfer](command-reference.md#dikciz_usb_transfer) |
| [ussdRequest](#ussdrequest) | [dikciz_ussd_request](command-reference.md#dikciz_ussd_request) |
| [vibrationControl](#vibrationcontrol) | [dikciz_vibration_control](command-reference.md#dikciz_vibration_control) |
| [waitFor](#waitfor) | [dikciz_wait_for](command-reference.md#dikciz_wait_for) |
| [widgetGet](#widgetget) | [dikciz_widget_get](command-reference.md#dikciz_widget_get) |
| [widgetMove](#widgetmove) | [dikciz_widget_move](command-reference.md#dikciz_widget_move) |
| [widgetResize](#widgetresize) | [dikciz_widget_resize](command-reference.md#dikciz_widget_resize) |
| [wifiConnectionStart](#wificonnectionstart) | [dikciz_wifi_connection_start](command-reference.md#dikciz_wifi_connection_start) |
| [wifiConnectionStatus](#wificonnectionstatus) | [dikciz_wifi_connection_status](command-reference.md#dikciz_wifi_connection_status) |
| [wifiConnectionStop](#wificonnectionstop) | [dikciz_wifi_connection_stop](command-reference.md#dikciz_wifi_connection_stop) |
| [wifiConnectionsList](#wificonnectionslist) | [dikciz_wifi_connections_list](command-reference.md#dikciz_wifi_connections_list) |
| [wifiHotspotStart](#wifihotspotstart) | [dikciz_wifi_hotspot_start](command-reference.md#dikciz_wifi_hotspot_start) |
| [wifiHotspotStatus](#wifihotspotstatus) | [dikciz_wifi_hotspot_status](command-reference.md#dikciz_wifi_hotspot_status) |
| [wifiHotspotStop](#wifihotspotstop) | [dikciz_wifi_hotspot_stop](command-reference.md#dikciz_wifi_hotspot_stop) |
| [wifiNetworksConfigure](#wifinetworksconfigure) | [dikciz_wifi_networks_configure](command-reference.md#dikciz_wifi_networks_configure) |
| [wifiScan](#wifiscan) | [dikciz_wifi_scan](command-reference.md#dikciz_wifi_scan) |
| [wifiScanResults](#wifiscanresults) | [dikciz_wifi_scan_results](command-reference.md#dikciz_wifi_scan_results) |

## accessibilityAction

Run one supported action on a node from an unexpired accessibility snapshot.

MCP: [`dikciz_accessibility_action`](command-reference.md#dikciz_accessibility_action). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.accessibilityAction` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.accessibilityAction.action.click` | action=click | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.accessibilityAction.action.longClick` | action=longClick | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.accessibilityAction.action.focus` | action=focus | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.accessibilityAction.action.clearFocus` | action=clearFocus | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.accessibilityAction.action.scrollForward` | action=scrollForward | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.accessibilityAction.action.scrollBackward` | action=scrollBackward | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.accessibilityAction.action.setText` | action=setText | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.accessibilityAction.action.select` | action=select | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## accessibilitySnapshot

Read a bounded snapshot of the active Android accessibility tree. The owner must first enable Dikciz cross-app automation in Android Accessibility settings.

MCP: [`dikciz_accessibility_snapshot`](command-reference.md#dikciz_accessibility_snapshot). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.accessibilitySnapshot` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## activityCancel

Request best-effort thread interruption of one running activity. Does not roll back completed effects or prove Android stopped work. Poll activityRead. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_activity_cancel`](command-reference.md#dikciz_activity_cancel). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.activityCancel` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## activityRead

Read persistent command metadata without arguments or payloads. Requires the matching explicit operation grant for scoped remote clients. Includes actor, request ID, duration and completed/rejected/failed/interrupted/running state. Interrupted outcomes may be unknown. Seven-day retention and bounded pagination.

MCP: [`dikciz_activity_read`](command-reference.md#dikciz_activity_read). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.activityRead` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## addWidget

Place one new top-level item in the first fitting free cells of the selected page grid. Rejects with page_full and writes nothing when no space fits. Use dikciz_widget_move and dikciz_widget_resize to place it afterwards.

MCP: [`dikciz_add_widget`](command-reference.md#dikciz_add_widget). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.addWidget` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## androidApiCallback

Create a public SDK interface callback that records typed invocations for androidApiObjects. returns maps exact callback overload ids to constant return values; unspecified non-void callbacks are rejected. No scripts run on Binder threads. Register with a writable SDK call and unregister before releasing. Public non-final SDK callback classes use a generated subclass. Constructor defaults to new(); select an exact SDK constructor when arguments are needed.

MCP: [`dikciz_android_api_callback`](command-reference.md#dikciz_android_api_callback). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.androidApiCallback` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## androidApiDiscover

Discover pinned public Android SDK classes, exact signatures, permission annotations and runtime availability. No API is invoked. Omit className for a page of classes; supply it for members, including inherited overloads. Read-only callers see only approved read members. Unknown members default to write access. Discovery is a catalogue, not a claim that Android grants every permission or supports every hardware feature.

MCP: [`dikciz_android_api_discover`](command-reference.md#dikciz_android_api_discover). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.androidApiDiscover` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## androidApiInvoke

Invoke a public SDK method, constructor or field read/set with writable access. Select an exact discovered member. Set a mutable field with set:NAME and one argument. Android permissions, roles, hardware and foreground restrictions still apply. Raw filesystem, private app storage, reflection and dynamic code APIs are excluded; use the dedicated protected file/shell APIs. Wrap long operations in jobStart. No automatic replay after process death.

MCP: [`dikciz_android_api_invoke`](command-reference.md#dikciz_android_api_invoke). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.androidApiInvoke` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## androidApiObjects

Read one page of process-owned SDK object handles, indexed array/list/map/text contents or callback events for objectRef. Poll using afterSequence. Handles and callback events do not survive process restart. Callbacks never execute commands automatically.

MCP: [`dikciz_android_api_objects`](command-reference.md#dikciz_android_api_objects). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.androidApiObjects` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## androidApiRead

Invoke one explicitly approved SDK read operation. Always rejects mutating or unaudited members with read_only_violation before resolving arguments or invoking Android, even outside read-only conversations. Discover the exact member and parameter list first. Permission annotations are advisory; Android remains authoritative.

MCP: [`dikciz_android_api_read`](command-reference.md#dikciz_android_api_read). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.androidApiRead` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## androidApiRelease

Release one SDK object handle. close=true explicitly calls AutoCloseable.close when supported. Unregister listeners and stop hardware sessions through their SDK cleanup methods before release. Release does not pretend to undo Android side effects.

MCP: [`dikciz_android_api_release`](command-reference.md#dikciz_android_api_release). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.androidApiRelease` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## apkInstall

Submit one readable standalone APK, up to 512 MiB, to Android PackageInstaller. Android may require unknown-source access and confirmation. Poll dikciz_apk_install_status for the actual outcome.

MCP: [`dikciz_apk_install`](command-reference.md#dikciz_apk_install). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.apkInstall` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## apkInstallCancel

Abandon a still-cancellable Dikciz-owned APK installation session, including staging and pending confirmation. Returns cancellationAccepted=false when terminal or too late. Poll apkInstallStatus for the actual outcome. Never uninstalls an installed package.

MCP: [`dikciz_apk_install_cancel`](command-reference.md#dikciz_apk_install_cancel). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.apkInstallCancel` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## apkInstallSet

Install one base APK followed by its split APKs in one Android PackageInstaller transaction. apks contains 1..32 unique files, at most 512 MiB each and 1 GiB total; optional SHA-256 is checked while staging. The first file must be a base APK. Android validates split names, signatures, versions and compatibility at commit and retains owner confirmation. Poll apkInstallStatus for actual outcome; queued is not installed.

MCP: [`dikciz_apk_install_set`](command-reference.md#dikciz_apk_install_set). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.apkInstallSet` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## apkInstallStatus

Read the actual Android outcome for one installation submitted by Dikciz. Submission or pending confirmation is not successful installation.

MCP: [`dikciz_apk_install_status`](command-reference.md#dikciz_apk_install_status). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.apkInstallStatus` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## appAction

Run one app action on a launchable component: launch, addShortcut, appInfo, uninstall, or forceStop. Force stop needs root and reports root_unavailable otherwise. Uninstall hands off to Android, which asks for its own confirmation.

MCP: [`dikciz_app_action`](command-reference.md#dikciz_app_action). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.appAction` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.appAction.action.launch` | action=launch | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.appAction.action.addShortcut` | action=addShortcut | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.appAction.action.appInfo` | action=appInfo | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.appAction.action.uninstall` | action=uninstall | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.appAction.action.forceStop` | action=forceStop | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## appCatalogue

List the launcher app catalogue with stable semantic IDs, human labels, package names, and components, plus the supported app actions. An optional query filters on label and package name.

MCP: [`dikciz_app_catalogue`](command-reference.md#dikciz_app_catalogue). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.appCatalogue` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## appIcon

Read a launchable activity icon as a bounded PNG. Use a component from appCatalogue. sizePixels defaults to 64 and accepts 16 through 128. Works through the shared HTML and network command API.

MCP: [`dikciz_app_icon`](command-reference.md#dikciz_app_icon). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.appIcon` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## assistantLibrary

Discover owner-authored skills, named agents and rules under /sdcard/Dikciz/assistant. list returns metadata; read returns exact UTF-8 instructions or supporting skill files using a relative path. Instructions reload between chat rounds and cannot raise tool permissions. Supports portable SKILL.md front matter and Markdown agents/rules, including .agents, .claude and .hermes skill directories.

MCP: [`dikciz_assistant_library`](command-reference.md#dikciz_assistant_library). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.assistantLibrary` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.assistantLibrary.action.list` | action=list | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.assistantLibrary.action.inspect` | action=inspect | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.assistantLibrary.action.read` | action=read | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## assistantLibraryManage

Atomically save, enable, disable or delete one assistant instruction. Requires expectedRevision from assistantLibrary read; empty string means create only. save requires text; setEnabled requires enabled. Disabling renames to .disabled. Delete preserves support files. Bundled instructions are immutable; copy to an owner path to customize. Rule and skill catalogs reload between model rounds; named agents reload on their next turn. Instructions cannot elevate permissions.

MCP: [`dikciz_assistant_library_manage`](command-reference.md#dikciz_assistant_library_manage). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.assistantLibraryManage` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.assistantLibraryManage.action.save` | action=save | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.assistantLibraryManage.action.setEnabled` | action=setEnabled | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.assistantLibraryManage.action.delete` | action=delete | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## assistantLibraryValidate

Validate a candidate UTF-8 skill, agent or rule without saving it. Supply a relative assistant path and full text. Validation does not grant permission or run instructions.

MCP: [`dikciz_assistant_library_validate`](command-reference.md#dikciz_assistant_library_validate). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.assistantLibraryValidate` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## assistantRoutinePreview

Read the exact instructions and access mode of a built-in routine without invoking a provider.

MCP: [`dikciz_assistant_routine_preview`](command-reference.md#dikciz_assistant_routine_preview). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.assistantRoutinePreview` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## assistantRoutineRun

Queue a built-in briefing in an existing conversation. Its entire turn is forcibly read-only at discovery and execution regardless of conversation settings. Poll chatControl read/status. Uses the saved provider; requestKey prevents duplicate queueing.

MCP: [`dikciz_assistant_routine_run`](command-reference.md#dikciz_assistant_routine_run). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.assistantRoutineRun` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## assistantRoutineSubscribe

Add or replace one named read-only routine event rule while preserving the conversation's other rules. subscription uses the canonical automation subscription schema. Background reports use the existing chat notifications and Stop control.

MCP: [`dikciz_assistant_routine_subscribe`](command-reference.md#dikciz_assistant_routine_subscribe). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.assistantRoutineSubscribe` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## assistantRoutines

List built-in read-only briefing routines. No provider request or phone mutation.

MCP: [`dikciz_assistant_routines`](command-reference.md#dikciz_assistant_routines). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.assistantRoutines` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## audioDevices

List Android audio input and output devices, sample rates and channel counts.

MCP: [`dikciz_audio_devices`](command-reference.md#dikciz_audio_devices). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.audioDevices` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## audioPlayback

Manage one process-owned local audio playback session. play prepares asynchronously; poll status for playing/completed/failed. Supports pause/resume, seek, volume and stop, owner-selected output, audio focus and a maximum lifetime. Sessions do not survive process death. Does not play URLs or bypass Android routing/focus restrictions.

MCP: [`dikciz_audio_playback`](command-reference.md#dikciz_audio_playback). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.audioPlayback` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.audioPlayback.action.play` | action=play | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.audioPlayback.action.status` | action=status | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.audioPlayback.action.pause` | action=pause | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.audioPlayback.action.resume` | action=resume | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.audioPlayback.action.seek` | action=seek | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.audioPlayback.action.volume` | action=volume | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.audioPlayback.action.stop` | action=stop | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## automationDispatch

Run the same typed action array used by an HTML widget or Lua script.

MCP: [`dikciz_automation_dispatch`](command-reference.md#dikciz_automation_dispatch). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.automationDispatch` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## automationHistory

Browse persistent causal automation metadata with seven-day/20000-record retention. Exact optional kind/name/outcome/traceId/recordId filters; newest first, limit/offset pagination. Links event to handler and command via traceId and parentId. No request arguments or payloads. Payload retention is separate. History starts at installation; it is not an audit of every Android action.

MCP: [`dikciz_automation_history`](command-reference.md#dikciz_automation_history). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.automationHistory` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## automationHistoryEvent

Read a retained event payload by automationHistory event recordId. This grant discloses all event sources; current Android source permissions are rechecked. Expired, omitted or restarted journal payloads fail explicitly.

MCP: [`dikciz_automation_history_event`](command-reference.md#dikciz_automation_history_event). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.automationHistoryEvent` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## automationRuleDelete

Delete one saved visual rule, its Lua script and owned subscription. In-flight work may finish. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_automation_rule_delete`](command-reference.md#dikciz_automation_rule_delete). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.automationRuleDelete` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## automationRulePreview

Compile a visual rule into Lua and simulate it with the optional sample payload. Returns proposed commands without touching Android. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_automation_rule_preview`](command-reference.md#dikciz_automation_rule_preview). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.automationRulePreview` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## automationRuleSave

Validate and save a visual automation rule, Lua source and least-source-capability subscription. Disabled by default. Explicit enabled=true activates future events after reload. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_automation_rule_save`](command-reference.md#dikciz_automation_rule_save). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.automationRuleSave` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## automationRulesList

List saved visual rule definitions. Lua remains editable through the script editor.

MCP: [`dikciz_automation_rules_list`](command-reference.md#dikciz_automation_rules_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.automationRulesList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## automationServiceSync

Re-evaluate the current automation policy and ask the private Android service to reload its public configuration.

MCP: [`dikciz_automation_service_sync`](command-reference.md#dikciz_automation_service_sync). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.automationServiceSync` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## automationSimulate

Simulate current Lua/JavaScript source, scriptId or appId against event {type,payload,source?,timestampMilliseconds?,coalescingKey?} or recorded event recordId. Exactly one source target required. Never executes intercepted commands or returned actions and never saves state. Optional ordered replies [{type,result}] supply fake command results. Returns captured commands, action kinds, sourceRevision and outcome. A sample is untrusted data; replay uses current source, not historical source. This grant can read recorded event payloads from all sources subject to current Android permissions.

MCP: [`dikciz_automation_simulate`](command-reference.md#dikciz_automation_simulate). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.automationSimulate` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.automationSimulate.language.lua` | language=lua | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.automationSimulate.language.javascript` | language=javascript | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## automationStatus

Read typed automation capabilities, event requirements, available sensors, and relevant Android access state.

MCP: [`dikciz_automation_status`](command-reference.md#dikciz_automation_status). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.automationStatus` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## automationTrigger

Queue one fixed manual event for an enabled configured script with a matching policy and subscription.

MCP: [`dikciz_automation_trigger`](command-reference.md#dikciz_automation_trigger). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.automationTrigger` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## backupCreate

Create an AES-GCM encrypted portable home, widget-state, Lua, custom-app files/state/runtime, assistant skills/rules/agents, chat and provider-settings snapshot. Passphrase required. API keys excluded unless explicitly requested. Remote credentials and control ports always remain outside backups. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_backup_create`](command-reference.md#dikciz_backup_create). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.backupCreate` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## backupPreview

Decrypt and validate an archive into protected staging without replacing current state. Returns counts, previewId and sha256 for explicit restore confirmation. Preview expires after 15 minutes. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_backup_preview`](command-reference.md#dikciz_backup_preview). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.backupPreview` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## backupRestore

Replace home and chat from a validated preview, with rollback recovery. Requires idle assistant, previewId, exact sha256 and confirm=true. Future automation remains disabled unless activateAutomation=true. Imported pending or claimed app timers always require review; active alerts and native sessions are not resumed. Remote auth and ports remain.

MCP: [`dikciz_backup_restore`](command-reference.md#dikciz_backup_restore). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.backupRestore` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## backupsList

List encrypted archive paths and metadata without reading contents. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_backups_list`](command-reference.md#dikciz_backups_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.backupsList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## bluetoothDevices

List paired Bluetooth devices with actual bond state. Does not scan, pair or connect.

MCP: [`dikciz_bluetooth_devices`](command-reference.md#dikciz_bluetooth_devices). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.bluetoothDevices` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## bluetoothExchange

Connect one RFCOMM service, optionally write bytes, then read one bounded chunk and close. Requires a device address and service UUID. Secure pairing/encryption defaults on. The deadline closes the owned socket. Errors can follow partial external writes; do not retry automatically.

MCP: [`dikciz_bluetooth_exchange`](command-reference.md#dikciz_bluetooth_exchange). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.bluetoothExchange` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## bluetoothGatt

Connect a selected BLE peer for paginated GATT discovery, characteristic/descriptor read/write or bounded notification observation. Uses an owned connection with a deadline, then disconnects/closes. UUID ambiguity requires discovered instance IDs. Writes may have partial effects. Owner Bluetooth access and peripheral security still apply.

MCP: [`dikciz_bluetooth_gatt`](command-reference.md#dikciz_bluetooth_gatt). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.bluetoothGatt` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.bluetoothGatt.action.discover` | action=discover | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.bluetoothGatt.action.read` | action=read | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.bluetoothGatt.action.write` | action=write | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.bluetoothGatt.action.readDescriptor` | action=readDescriptor | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.bluetoothGatt.action.writeDescriptor` | action=writeDescriptor | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.bluetoothGatt.action.observe` | action=observe | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## bluetoothPair

Ask Android to start pairing with a selected Bluetooth address. Owner PIN/confirmation may be required. submitted is not paired; inspect bluetoothDevices for the final bond.

MCP: [`dikciz_bluetooth_pair`](command-reference.md#dikciz_bluetooth_pair). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.bluetoothPair` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## bluetoothScan

Observe nearby BLE or classic devices during a bounded scan window. Requires Android Bluetooth access, plus location on older Android. Returns actual observed address/name/type/bond/RSSI metadata. Stops only its own scan.

MCP: [`dikciz_bluetooth_scan`](command-reference.md#dikciz_bluetooth_scan). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.bluetoothScan` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.bluetoothScan.mode.ble` | mode=ble | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.bluetoothScan.mode.classic` | mode=classic | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## callAudio

Read cellular call-audio capability status or request record/speak. Ordinary Android installations return call_audio_unsupported for capture and injection. Microphone and device speech are separate operations.

MCP: [`dikciz_call_audio`](command-reference.md#dikciz_call_audio). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.callAudio` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.callAudio.action.status` | action=status | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.callAudio.action.record` | action=record | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.callAudio.action.speak` | action=speak | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## callControl

Native phone operations: state, place, answer or end. Place requires recipient. Android may restrict operations by role or privilege. accepted never claims a connected call.

MCP: [`dikciz_call_control`](command-reference.md#dikciz_call_control). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.callControl` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.callControl.action.state` | action=state | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.callControl.action.place` | action=place | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.callControl.action.answer` | action=answer | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.callControl.action.end` | action=end | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## cameraCapabilities

Read JPEG and recorder sizes, camera frame-rate ranges, digital zoom, exposure range/EV step, sensor orientation and autofocus modes for one camera.

MCP: [`dikciz_camera_capabilities`](command-reference.md#dikciz_camera_capabilities). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.cameraCapabilities` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## cameraCapture

Capture a JPEG with optional quality, orientation, sensor crop zoom, exposure compensation and autofocus controls. Returns written image path and completed Android frame metadata. Read cameraCapabilities first. Android camera access and foreground restrictions apply.

MCP: [`dikciz_camera_capture`](command-reference.md#dikciz_camera_capture). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.cameraCapture` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## cameraList

List Android camera IDs and lens directions without starting capture.

MCP: [`dikciz_camera_list`](command-reference.md#dikciz_camera_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.cameraList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## cameraVideoRecord

Record bounded foreground camera H.264 MP4, optionally with microphone AAC. Returns only after finalization and Android metadata inspection. Shares camera/microphone ownership; incomplete files are removed. No background service or preview. Use jobStart for reconnectable results.

MCP: [`dikciz_camera_video_record`](command-reference.md#dikciz_camera_video_record). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.cameraVideoRecord` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## chatControl

Manage the on-device Dikciz assistant. Actions: status, profiles, profileSave, models, create, list, read, rename, settings, configure, delete, send, task, stop, subscribe, show, hide, fullscreen. A send is queued, not proof of completion. Poll read/status. Profile reads never return API keys. Save a connection without a model, discover its models, then configure each conversation with independent model and token settings. settings reads effective values and modelLimits. Selecting a known model fills context defaults and clamps the reply reserve; explicit token fields win. models exposes known context/output/input ceilings and limitsSource, using provider metadata before the bundled catalog. Unknown models keep manual budgets. configure patches settings and optionally changes profileId. Changing providers retains read-only access and resets other overrides. settings.agent selects an installed named agent from assistantLibrary and reloads its instructions and narrowed tool access each turn. settings.readOnly defaults to false; true hides mutating tools and blocks their execution and permission prompts. subscribe replaces the conversation's event rules; each rule has id, subscription (canonical automation event subscription) and instructions. show/hide/fullscreen control the chat panel independently of the agent. task starts widget creation (task:widget, text required) or a read-only screen explanation (task:explainScreen, optional text), with conversationId and requestKey. Screen tasks hide chat, capture the display and reopen chat. Read the chat MCP guide for fields and examples.

MCP: [`dikciz_chat`](command-reference.md#dikciz_chat). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.chatControl` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.status` | action=status | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.profiles` | action=profiles | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.profileSave` | action=profileSave | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.models` | action=models | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.create` | action=create | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.list` | action=list | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.read` | action=read | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.rename` | action=rename | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.settings` | action=settings | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.configure` | action=configure | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.delete` | action=delete | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.send` | action=send | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.task` | action=task | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.stop` | action=stop | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.subscribe` | action=subscribe | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.show` | action=show | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.hide` | action=hide | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.chatControl.action.fullscreen` | action=fullscreen | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## clipboardGet

Read clipboard text while Android permits the calling app to access it. Background restrictions return available=false.

MCP: [`dikciz_clipboard_get`](command-reference.md#dikciz_clipboard_get). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.clipboardGet` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## clipboardSet

Set plain clipboard text through ClipboardManager.

MCP: [`dikciz_clipboard_set`](command-reference.md#dikciz_clipboard_set). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.clipboardSet` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## configGet

Read the complete validated Dikciz configuration document.

MCP: [`dikciz_config_get`](command-reference.md#dikciz_config_get). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.configGet` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## configReplace

Validate, atomically save, and reload a complete Dikciz configuration document.

MCP: [`dikciz_config_replace`](command-reference.md#dikciz_config_replace). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.configReplace` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## configSeed

Validate, atomically save, and reload a complete Dikciz configuration document before scripted launcher use.

MCP: [`dikciz_config_seed`](command-reference.md#dikciz_config_seed). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.configSeed` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## contentBatch

Apply at most 32 validated writes atomically within one Android provider. Earlier create results may supply allowlisted foreign-key back references. No arbitrary URI or SQL.

MCP: [`dikciz_content_batch`](command-reference.md#dikciz_content_batch). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.contentBatch` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.resource.rawContacts` | resource=rawContacts | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.resource.contactData` | resource=contactData | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.resource.calendarEvents` | resource=calendarEvents | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.resource.calendarReminders` | resource=calendarReminders | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.operation.create` | operation=create | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.operation.update` | operation=update | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.operation.delete` | operation=delete | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.resource.rawContacts.operation.create` | resource=rawContacts, operation=create | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.resource.rawContacts.operation.update` | resource=rawContacts, operation=update | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.resource.rawContacts.operation.delete` | resource=rawContacts, operation=delete | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.resource.contactData.operation.create` | resource=contactData, operation=create | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.resource.contactData.operation.update` | resource=contactData, operation=update | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.resource.contactData.operation.delete` | resource=contactData, operation=delete | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.resource.calendarEvents.operation.create` | resource=calendarEvents, operation=create | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.resource.calendarEvents.operation.update` | resource=calendarEvents, operation=update | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.resource.calendarEvents.operation.delete` | resource=calendarEvents, operation=delete | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.resource.calendarReminders.operation.create` | resource=calendarReminders, operation=create | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.resource.calendarReminders.operation.update` | resource=calendarReminders, operation=update | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentBatch.resource.calendarReminders.operation.delete` | resource=calendarReminders, operation=delete | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## contentQuery

Read one bounded page from an allowlisted Android provider. Filter values use bound arguments. Query is literal substring search. Follow hasMore using offset plus returned item count.

MCP: [`dikciz_content_query`](command-reference.md#dikciz_content_query). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.contentQuery` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentQuery.resource.contacts` | resource=contacts | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentQuery.resource.phones` | resource=phones | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentQuery.resource.emails` | resource=emails | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentQuery.resource.rawContacts` | resource=rawContacts | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentQuery.resource.contactData` | resource=contactData | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentQuery.resource.calendars` | resource=calendars | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentQuery.resource.calendarEvents` | resource=calendarEvents | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentQuery.resource.calendarInstances` | resource=calendarInstances | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentQuery.resource.calendarReminders` | resource=calendarReminders | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentQuery.resource.sms` | resource=sms | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentQuery.resource.callLog` | resource=callLog | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentQuery.resource.images` | resource=images | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentQuery.resource.audio` | resource=audio | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentQuery.resource.videos` | resource=videos | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## contentWrite

Create, update or delete a single Android provider row. Native data status lists writable resources and columns. SMS history stays read-only. Android permissions and provider rules apply.

MCP: [`dikciz_content_write`](command-reference.md#dikciz_content_write). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.contentWrite` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.resource.rawContacts` | resource=rawContacts | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.resource.contactData` | resource=contactData | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.resource.calendarEvents` | resource=calendarEvents | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.resource.calendarReminders` | resource=calendarReminders | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.operation.create` | operation=create | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.operation.update` | operation=update | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.operation.delete` | operation=delete | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.resource.rawContacts.operation.create` | resource=rawContacts, operation=create | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.resource.rawContacts.operation.update` | resource=rawContacts, operation=update | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.resource.rawContacts.operation.delete` | resource=rawContacts, operation=delete | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.resource.contactData.operation.create` | resource=contactData, operation=create | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.resource.contactData.operation.update` | resource=contactData, operation=update | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.resource.contactData.operation.delete` | resource=contactData, operation=delete | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.resource.calendarEvents.operation.create` | resource=calendarEvents, operation=create | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.resource.calendarEvents.operation.update` | resource=calendarEvents, operation=update | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.resource.calendarEvents.operation.delete` | resource=calendarEvents, operation=delete | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.resource.calendarReminders.operation.create` | resource=calendarReminders, operation=create | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.resource.calendarReminders.operation.update` | resource=calendarReminders, operation=update | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.contentWrite.resource.calendarReminders.operation.delete` | resource=calendarReminders, operation=delete | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## controlStatus

Read the complete local command contract, limits, transports, and safe-mode state.

MCP: [`dikciz_control_status`](command-reference.md#dikciz_control_status). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.controlStatus` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## customAppAlert

Show/close a reusable dimmed urgent countdown modal with a notification fallback and cancel button. show needs episodeId, durationMilliseconds, title, detail, cancelLabel, cancelEvent. Optional announcement/finalAnnouncement are spoken via offline TTS, {seconds} is substituted. Cancellation dispatches cancelEvent to this app. Requires enabled notifications and high-priority alert channel. The response reports requested presentation; inspect customAppRead.alert for observed state. Android protected screens may hide overlays.

MCP: [`dikciz_custom_app_alert`](command-reference.md#dikciz_custom_app_alert). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.customAppAlert` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.customAppAlert.action.show` | action=show | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.customAppAlert.action.close` | action=close | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## customAppControl

open starts an app and opens its UI; enable starts it without permission approval; disable stops it and cancels timers and alerts; remove requires confirm=true and deletes only this app's directory; export writes a bounded .dikciz-app.zip; import requires path and confirm=true and installs a new enabled app. Apps inherit Dikciz Android access. Export excludes state and runtime files. To copy an app, install its source with a different manifest.id for an independent instance.

MCP: [`dikciz_custom_app_control`](command-reference.md#dikciz_custom_app_control). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.customAppControl` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.customAppControl.action.open` | action=open | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.customAppControl.action.enable` | action=enable | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.customAppControl.action.disable` | action=disable | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.customAppControl.action.remove` | action=remove | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.customAppControl.action.export` | action=export | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.customAppControl.action.import` | action=import | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## customAppEvent

Synchronously invoke an enabled app's Lua/JS on_event/onEvent handler. event is a name, payload an object. Returns outcome/status/result and current state. A handler result.error with code/message becomes the canonical command error. Shared budgets apply. Calls serialize with timers and cancellation. interruptRunning=true blocks subsequent effects from an already running handler before this event acquires its execution lock. No automatic retry of effects.

MCP: [`dikciz_custom_app_event`](command-reference.md#dikciz_custom_app_event). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.customAppEvent` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## customAppInstall

Install or replace a complete app bundle after confirm=true. manifest has version:1,id,title,description,language (lua/javascript), optional commands (legacy metadata), events (event names), optional locationMonitoring boolean. Sources use html/css/javascript/backgroundSource. New apps start enabled and inherit Dikciz Android access. Replacing requires no pending timers or alert and preserves data and running/stopped state. All entrypoints share validation.

MCP: [`dikciz_custom_app_install`](command-reference.md#dikciz_custom_app_install). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.customAppInstall` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## customAppRead

Read one app manifest, full HTML/CSS/JS, optional Lua/JS background source, state, run status and durable timers. Sources live under /sdcard/Dikciz/apps/<appId>. This read can contain private app data and requires an explicit operation grant for scoped remote clients.

MCP: [`dikciz_custom_app_read`](command-reference.md#dikciz_custom_app_read). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.customAppRead` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## customAppRuntime

Read native clock/boot identity, Android access, notification/channel availability and app service state. Elapsed time is monotonic within boot. Does not grant access or start effects.

MCP: [`dikciz_custom_app_runtime`](command-reference.md#dikciz_custom_app_runtime). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.customAppRuntime` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## customAppSchedule

Schedule/cancel one durable app timer by timerId, or cancelAll. schedule needs delayMilliseconds (1000..604800000), event and optional payload. Same ID replaces only pending work; a unique generation identifies each delivery. Android can delay alarms. A claimed timer is never automatically replayed after a crash; interrupted/rebooted work needs review. Timer cancellation cannot recall submitted effects. Scheduling starts a visible service with Stop.

MCP: [`dikciz_custom_app_schedule`](command-reference.md#dikciz_custom_app_schedule). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.customAppSchedule` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.customAppSchedule.action.schedule` | action=schedule | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.customAppSchedule.action.cancel` | action=cancel | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.customAppSchedule.action.cancelAll` | action=cancelAll | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## customAppState

Read or atomically replace app state.json. replace requires state. Backends and UI share this state. Script patchState actions merge keys. Use customAppRead for a read-only agent. App bindings default to their own appId and may explicitly address another app.

MCP: [`dikciz_custom_app_state`](command-reference.md#dikciz_custom_app_state). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.customAppState` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.customAppState.action.read` | action=read | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.customAppState.action.replace` | action=replace | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## customAppsList

List installed editable Dikciz apps. Follow hasMore; disabled apps cannot execute background code. Apps are independent of Android APKs.

MCP: [`dikciz_custom_apps_list`](command-reference.md#dikciz_custom_apps_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.customAppsList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## deviceConnectionClose

Close a retained device connection and release claimed hardware. Completed effects remain.

MCP: [`dikciz_device_connection_close`](command-reference.md#dikciz_device_connection_close). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.deviceConnectionClose` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## deviceConnectionExchange

One serialized operation on a retained connection. RFCOMM writes optional dataBase64 and reads at most readBytes; a chunk is not a complete message. USB action control/bulk/interrupt uses the claimed interface. NFC action transceive/readNdef/writeNdef. GATT action discover/read/write/subscribe/unsubscribe/notifications, with exact service/characteristic UUIDs. Timeout closes the connection; accepted writes can be partial. All exchange actions are mutating for access control.

MCP: [`dikciz_device_connection_exchange`](command-reference.md#dikciz_device_connection_exchange). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.deviceConnectionExchange` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionExchange.action.control` | action=control | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionExchange.action.bulk` | action=bulk | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionExchange.action.interrupt` | action=interrupt | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionExchange.action.transceive` | action=transceive | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionExchange.action.readNdef` | action=readNdef | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionExchange.action.writeNdef` | action=writeNdef | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionExchange.action.discover` | action=discover | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionExchange.action.read` | action=read | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionExchange.action.write` | action=write | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionExchange.action.subscribe` | action=subscribe | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionExchange.action.unsubscribe` | action=unsubscribe | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionExchange.action.notifications` | action=notifications | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## deviceConnectionOpen

Open a retained rfcomm, gatt, usb or nfc connection for multiple commands. At most eight active; default lifetime five minutes, configurable up to one hour. Process death closes hardware. Bluetooth uses address and serviceUuid for rfcomm; USB requires deviceId and owner-granted access; NFC requires foreground and a presented tag. No payment interpretation or automatic retries.

MCP: [`dikciz_device_connection_open`](command-reference.md#dikciz_device_connection_open). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.deviceConnectionOpen` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.rfcomm` | kind=rfcomm | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.gatt` | kind=gatt | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.usb` | kind=usb | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.nfc` | kind=nfc | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.technology.IsoDep` | technology=IsoDep | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.technology.NfcA` | technology=NfcA | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.technology.NfcB` | technology=NfcB | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.technology.NfcF` | technology=NfcF | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.technology.NfcV` | technology=NfcV | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.technology.Ndef` | technology=Ndef | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.technology.MifareClassic` | technology=MifareClassic | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.technology.MifareUltralight` | technology=MifareUltralight | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.rfcomm.technology.IsoDep` | kind=rfcomm, technology=IsoDep | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.rfcomm.technology.NfcA` | kind=rfcomm, technology=NfcA | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.rfcomm.technology.NfcB` | kind=rfcomm, technology=NfcB | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.rfcomm.technology.NfcF` | kind=rfcomm, technology=NfcF | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.rfcomm.technology.NfcV` | kind=rfcomm, technology=NfcV | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.rfcomm.technology.Ndef` | kind=rfcomm, technology=Ndef | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.rfcomm.technology.MifareClassic` | kind=rfcomm, technology=MifareClassic | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.rfcomm.technology.MifareUltralight` | kind=rfcomm, technology=MifareUltralight | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.gatt.technology.IsoDep` | kind=gatt, technology=IsoDep | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.gatt.technology.NfcA` | kind=gatt, technology=NfcA | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.gatt.technology.NfcB` | kind=gatt, technology=NfcB | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.gatt.technology.NfcF` | kind=gatt, technology=NfcF | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.gatt.technology.NfcV` | kind=gatt, technology=NfcV | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.gatt.technology.Ndef` | kind=gatt, technology=Ndef | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.gatt.technology.MifareClassic` | kind=gatt, technology=MifareClassic | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.gatt.technology.MifareUltralight` | kind=gatt, technology=MifareUltralight | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.usb.technology.IsoDep` | kind=usb, technology=IsoDep | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.usb.technology.NfcA` | kind=usb, technology=NfcA | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.usb.technology.NfcB` | kind=usb, technology=NfcB | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.usb.technology.NfcF` | kind=usb, technology=NfcF | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.usb.technology.NfcV` | kind=usb, technology=NfcV | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.usb.technology.Ndef` | kind=usb, technology=Ndef | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.usb.technology.MifareClassic` | kind=usb, technology=MifareClassic | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.usb.technology.MifareUltralight` | kind=usb, technology=MifareUltralight | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.nfc.technology.IsoDep` | kind=nfc, technology=IsoDep | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.nfc.technology.NfcA` | kind=nfc, technology=NfcA | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.nfc.technology.NfcB` | kind=nfc, technology=NfcB | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.nfc.technology.NfcF` | kind=nfc, technology=NfcF | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.nfc.technology.NfcV` | kind=nfc, technology=NfcV | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.nfc.technology.Ndef` | kind=nfc, technology=Ndef | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.nfc.technology.MifareClassic` | kind=nfc, technology=MifareClassic | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.deviceConnectionOpen.kind.nfc.technology.MifareUltralight` | kind=nfc, technology=MifareUltralight | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## deviceConnectionRead

Read connection lifecycle metadata without opening or transferring data.

MCP: [`dikciz_device_connection_read`](command-reference.md#dikciz_device_connection_read). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.deviceConnectionRead` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## deviceConnectionsList

Read a paginated list of retained connection metadata. Inactive entries expire after five minutes.

MCP: [`dikciz_device_connections_list`](command-reference.md#dikciz_device_connections_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.deviceConnectionsList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## deviceInfo

Read the running package, version, process and launcher instance, plus Android device information.

MCP: [`dikciz_device_info`](command-reference.md#dikciz_device_info). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.deviceInfo` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## deviceState

Read screen, battery, charging, audio, network, RAM, storage, CPU samples and bounded sensor inventory. System CPU may be unavailable under Android restrictions; sensor inventory does not claim live readings.

MCP: [`dikciz_device_state`](command-reference.md#dikciz_device_state). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.deviceState` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## diagnostics

Read local control-plane diagnostics.

MCP: [`dikciz_diagnostics`](command-reference.md#dikciz_diagnostics). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.diagnostics` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## displayScreenshot

Capture the visible Android display, including other apps, through owner-enabled Accessibility on Android 11+. Secure windows and system rate limits still apply.

MCP: [`dikciz_display_screenshot`](command-reference.md#dikciz_display_screenshot). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.displayScreenshot` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## documentScan

Create cleaned JPEG pages and an image PDF from 1..20 accessible images. Optional normalized clockwise corners correct perspective; absent corners retain the full image. This does not claim automatic edge detection or a searchable PDF text layer. Use imageRecognize on the returned pages for OCR. Output is saved in app capture storage; no image upload.

MCP: [`dikciz_document_scan`](command-reference.md#dikciz_document_scan). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.documentScan` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## eventConsumerAck

Persist a named consumer cursor after successful handling. Cursor must not move backwards or beyond the last dispatched event. Does not run actions. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_event_consumer_ack`](command-reference.md#dikciz_event_consumer_ack). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.eventConsumerAck` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## eventConsumerForget

Delete one consumer's acknowledgement. A subsequent read starts at zero and may report an expired history gap. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_event_consumer_forget`](command-reference.md#dikciz_event_consumer_forget). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.eventConsumerForget` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## eventConsumerRead

Read durable events after a named consumer's last acknowledged cursor. Reading never acknowledges. Reconnects and process restarts retain the epoch and cursor. Inspect historyGap before continuing. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_event_consumer_read`](command-reference.md#dikciz_event_consumer_read). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.eventConsumerRead` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## eventConsumersList

List durable named event consumer cursors. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_event_consumers_list`](command-reference.md#dikciz_event_consumers_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.eventConsumersList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## eventJournalConfigure

Adjust durable replay count, byte and age limits. Defaults are 2048 events, 8 MiB and 60 minutes. Lower limits immediately evict old history; limits persist across process death. Does not change native sensor/location subscriptions.

MCP: [`dikciz_event_journal_configure`](command-reference.md#dikciz_event_journal_configure). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.eventJournalConfigure` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## eventJournalRead

Replay actual automation events after cursor in the required journalId epoch. Optional exact eventTypes filters, bounded long polling and pagination work across every ordinary binding. Inspect historyGap/lostEvents and per-event omitted markers. Current Android source access is rechecked before returning retained payloads.

MCP: [`dikciz_event_journal_read`](command-reference.md#dikciz_event_journal_read). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.eventJournalRead` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## eventJournalStatus

Read durable automation-event replay epoch, cursors, retention and memory limits. Reading does not activate native subscriptions.

MCP: [`dikciz_event_journal_status`](command-reference.md#dikciz_event_journal_status). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.eventJournalStatus` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## fileList

List one directory page in filesystem enumeration order. Follow hasMore with offset plus the returned item count; directory changes can alter enumeration order.

MCP: [`dikciz_file_list`](command-reference.md#dikciz_file_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.fileList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## fileManage

Stat, copy one regular file, move without overwrite, explicitly delete, or search accessible files by literal name substring. Recursive deletion is preflighted, bounded and refuses symlinks and credential ancestors. Search does not follow symlinks. Android filesystem access applies.

MCP: [`dikciz_file_manage`](command-reference.md#dikciz_file_manage). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.fileManage` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.fileManage.action.stat` | action=stat | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.fileManage.action.copy` | action=copy | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.fileManage.action.move` | action=move | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.fileManage.action.delete` | action=delete | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.fileManage.action.search` | action=search | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## fileMkdir

Create a directory wherever Dikciz can write. Optionally create missing parents.

MCP: [`dikciz_file_mkdir`](command-reference.md#dikciz_file_mkdir). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.fileMkdir` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## fileRead

Read a bounded binary file chunk as base64. Follow nextOffset until eof is true.

MCP: [`dikciz_file_read`](command-reference.md#dikciz_file_read). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.fileRead` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## fileWrite

Create or explicitly overwrite a file at offset zero, or append a chunk only when offset equals its current size. Writing bytes does not directly execute them; changing watched script, app or configuration files can trigger reload and effects.

MCP: [`dikciz_file_write`](command-reference.md#dikciz_file_write). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.fileWrite` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## find

Find one node by its stable semantic ID.

MCP: [`dikciz_find`](command-reference.md#dikciz_find). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.find` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## geocode

Forward/reverse geocode through the installed Android provider with a bounded wait. The provider may send query/coordinates to its network service. Explicit failure when unavailable. Android 13+.

MCP: [`dikciz_geocode`](command-reference.md#dikciz_geocode). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.geocode` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.geocode.action.forward` | action=forward | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.geocode.action.reverse` | action=reverse | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## geofenceDelete

Remove one saved geofence and stop its future event delivery.

MCP: [`dikciz_geofence_delete`](command-reference.md#dikciz_geofence_delete). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.geofenceDelete` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## geofenceSave

Create/update one real-location geofence. Disabled by default. Enabled monitoring uses a visible foreground service. Emits geofence.enter, geofence.exit and optional geofence.dwell custom events. Accuracy and hysteresis suppress uncertain crossings. Open Dikciz to enable.

MCP: [`dikciz_geofence_save`](command-reference.md#dikciz_geofence_save). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.geofenceSave` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## geofencesList

Read saved geofence states and observed distances, with pagination.

MCP: [`dikciz_geofences_list`](command-reference.md#dikciz_geofences_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.geofencesList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## gnssStatus

Observe one GNSS satellite-status callback during a bounded window. Does not start GPS or invent a fix. Requires fine location; no status is explicit. Registrations are removed after the request.

MCP: [`dikciz_gnss_status`](command-reference.md#dikciz_gnss_status). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.gnssStatus` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## gridSet

Replace the page grid used by every page. Reflow widget rectangles to fit, relocate collisions and remove overflow items in saved order. Return the saved grid and evictedWidgets with pageId/id records. Evicting app shortcuts leaves their apps installed.

MCP: [`dikciz_grid_set`](command-reference.md#dikciz_grid_set). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.gridSet` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## healthPermissionsRequest

Ask the owner to review exact Health Connect record permissions. access is read/write/readWrite. Optional history/background access is separate. No silent grants or automatic retry.

MCP: [`dikciz_health_permissions_request`](command-reference.md#dikciz_health_permissions_request). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.healthPermissionsRequest` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthPermissionsRequest.access.read` | access=read | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthPermissionsRequest.access.write` | access=write | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthPermissionsRequest.access.readWrite` | access=readWrite | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## healthRecordsAggregate

Read the provider's supported aggregate metrics, avoiding duplicate cumulative raw-source counting. Units match the catalogue. Null means no contributing records.

MCP: [`dikciz_health_records_aggregate`](command-reference.md#dikciz_health_records_aggregate). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.healthRecordsAggregate` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsAggregate.kind.steps` | kind=steps | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsAggregate.kind.distance` | kind=distance | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsAggregate.kind.activeCalories` | kind=activeCalories | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsAggregate.kind.totalCalories` | kind=totalCalories | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsAggregate.kind.heartRate` | kind=heartRate | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsAggregate.kind.sleep` | kind=sleep | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsAggregate.kind.exercise` | kind=exercise | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsAggregate.kind.weight` | kind=weight | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsAggregate.kind.height` | kind=height | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsAggregate.kind.hydration` | kind=hydration | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsAggregate.kind.bodyFat` | kind=bodyFat | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsAggregate.kind.bodyTemperature` | kind=bodyTemperature | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsAggregate.kind.bloodPressure` | kind=bloodPressure | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsAggregate.kind.bloodGlucose` | kind=bloodGlucose | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsAggregate.kind.oxygenSaturation` | kind=oxygenSaturation | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsAggregate.kind.floors` | kind=floors | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## healthRecordsCatalogue

Discover 16 supported Health Connect record kinds, canonical value units, aggregate metric names, provider availability and exact read/write grants.

MCP: [`dikciz_health_records_catalogue`](command-reference.md#dikciz_health_records_catalogue). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.healthRecordsCatalogue` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## healthRecordsDelete

Delete only the exact selected record IDs that Health Connect allows this app to delete. Never deletes all history. Requires confirm=true.

MCP: [`dikciz_health_records_delete`](command-reference.md#dikciz_health_records_delete). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.healthRecordsDelete` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsDelete.kind.steps` | kind=steps | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsDelete.kind.distance` | kind=distance | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsDelete.kind.activeCalories` | kind=activeCalories | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsDelete.kind.totalCalories` | kind=totalCalories | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsDelete.kind.heartRate` | kind=heartRate | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsDelete.kind.sleep` | kind=sleep | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsDelete.kind.exercise` | kind=exercise | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsDelete.kind.weight` | kind=weight | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsDelete.kind.height` | kind=height | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsDelete.kind.hydration` | kind=hydration | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsDelete.kind.bodyFat` | kind=bodyFat | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsDelete.kind.bodyTemperature` | kind=bodyTemperature | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsDelete.kind.bloodPressure` | kind=bloodPressure | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsDelete.kind.bloodGlucose` | kind=bloodGlucose | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsDelete.kind.oxygenSaturation` | kind=oxygenSaturation | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsDelete.kind.floors` | kind=floors | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## healthRecordsRead

Read one bounded page of a Health Connect kind in an epoch-millisecond range of at most 366 days. Follow nextPageToken. Android history/background access restrictions apply. Missing grants return a targeted healthPermissionsRequest recovery.

MCP: [`dikciz_health_records_read`](command-reference.md#dikciz_health_records_read). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.healthRecordsRead` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsRead.kind.steps` | kind=steps | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsRead.kind.distance` | kind=distance | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsRead.kind.activeCalories` | kind=activeCalories | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsRead.kind.totalCalories` | kind=totalCalories | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsRead.kind.heartRate` | kind=heartRate | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsRead.kind.sleep` | kind=sleep | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsRead.kind.exercise` | kind=exercise | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsRead.kind.weight` | kind=weight | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsRead.kind.height` | kind=height | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsRead.kind.hydration` | kind=hydration | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsRead.kind.bodyFat` | kind=bodyFat | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsRead.kind.bodyTemperature` | kind=bodyTemperature | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsRead.kind.bloodPressure` | kind=bloodPressure | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsRead.kind.bloodGlucose` | kind=bloodGlucose | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsRead.kind.oxygenSaturation` | kind=oxygenSaturation | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsRead.kind.floors` | kind=floors | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## healthRecordsWrite

Insert/upsert 1..50 manually entered records of one kind. Every record requires stable clientRecordId, values, and either timeMilliseconds or start/endTimeMilliseconds according to the catalogue. Optional nonnegative clientRecordVersion controls replacement. UTC offsets are used. Only this app's origin is written. Heart-rate samples use timeMilliseconds/beatsPerMinute; sleep stages use start/endTimeMilliseconds/stage.

MCP: [`dikciz_health_records_write`](command-reference.md#dikciz_health_records_write). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.healthRecordsWrite` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsWrite.kind.steps` | kind=steps | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsWrite.kind.distance` | kind=distance | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsWrite.kind.activeCalories` | kind=activeCalories | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsWrite.kind.totalCalories` | kind=totalCalories | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsWrite.kind.heartRate` | kind=heartRate | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsWrite.kind.sleep` | kind=sleep | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsWrite.kind.exercise` | kind=exercise | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsWrite.kind.weight` | kind=weight | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsWrite.kind.height` | kind=height | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsWrite.kind.hydration` | kind=hydration | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsWrite.kind.bodyFat` | kind=bodyFat | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsWrite.kind.bodyTemperature` | kind=bodyTemperature | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsWrite.kind.bloodPressure` | kind=bloodPressure | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsWrite.kind.bloodGlucose` | kind=bloodGlucose | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsWrite.kind.oxygenSaturation` | kind=oxygenSaturation | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.healthRecordsWrite.kind.floors` | kind=floors | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## homeGet

Read pages keyed by home alias, stable page ID, and H/V address, with each page's widget map.

MCP: [`dikciz_home_get`](command-reference.md#dikciz_home_get). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.homeGet` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## htmlWidgetRendererCrash

Crash one rendered HTML widget through Android's documented WebView test URL, then report the normal renderer-loss recovery through local logs.

MCP: [`dikciz_html_widget_renderer_crash`](command-reference.md#dikciz_html_widget_renderer_crash). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.htmlWidgetRendererCrash` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## imageRecognize

Read text, barcodes or both from an accessible image using bundled on-device models. Latin text recognition; barcode formats are reported as ML Kit constants. No image upload, link opening or instruction execution. Bounds refer to the processed rotated image. Unsupported/oversized inputs fail explicitly.

MCP: [`dikciz_image_recognize`](command-reference.md#dikciz_image_recognize). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.imageRecognize` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.imageRecognize.mode.text` | mode=text | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.imageRecognize.mode.barcodes` | mode=barcodes | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.imageRecognize.mode.both` | mode=both | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## imageStreamForget

Discard one finished visual stream and its retained images. Stop active capture first.

MCP: [`dikciz_image_stream_forget`](command-reference.md#dikciz_image_stream_forget). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.imageStreamForget` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## imageStreamRead

Read complete bounded JPEG frames by exclusive cursor, with replay loss and actual terminal/hardware-cleanup state. No capture is started. Frames stay in process memory for five minutes after completion. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_image_stream_read`](command-reference.md#dikciz_image_stream_read). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.imageStreamRead` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## imageStreamStart

Start bounded camera JPEG preview or Accessibility display frames with an owner Stop notification. Requires source camera/display and foreground startup. A persistent camera session is reused; display follows Android screenshot limits and secure-window restrictions. Poll imageStreamRead for actual frames/errors. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_image_stream_start`](command-reference.md#dikciz_image_stream_start). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.imageStreamStart` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.imageStreamStart.source.camera` | source=camera | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.imageStreamStart.source.display` | source=display | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## imageStreamStop

Stop one live visual stream, close its camera session and read hardwareReleaseConfirmed. Completed frames remain until forgotten or retention expiry.

MCP: [`dikciz_image_stream_stop`](command-reference.md#dikciz_image_stream_stop). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.imageStreamStop` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## imageStreamsList

List retained visual stream metadata without frame payloads.

MCP: [`dikciz_image_streams_list`](command-reference.md#dikciz_image_streams_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.imageStreamsList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## intent

Dispatch an Android activity or broadcast intent using the launcher app's permissions.

MCP: [`dikciz_intent`](command-reference.md#dikciz_intent). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.intent` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## jobCancel

Cancel queued work or request interruption of running work. Cancellation never rolls back side effects or guarantees that Android stopped an operation. Read jobStatus for the final outcome.

MCP: [`dikciz_job_cancel`](command-reference.md#dikciz_job_cancel). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.jobCancel` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## jobStart

Persist and run one ordinary Dikciz command asynchronously. Required idempotencyKey prevents duplicate submissions. Results survive disconnect and process death; interrupted work is never replayed automatically. Poll jobStatus for the original complete response including permission recovery. Jobs cannot start other jobs or change remote authentication.

MCP: [`dikciz_job_start`](command-reference.md#dikciz_job_start). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.jobStart` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## jobStatus

Read persisted job state and its full original command response. interrupted means outcome unknown after process loss. No automatic retry.

MCP: [`dikciz_job_status`](command-reference.md#dikciz_job_status). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.jobStatus` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## jobsList

List retained job metadata newest first. Includes queued/running/completed/failed/cancelled/interrupted state and cancellation requests; results are read separately with jobStatus.

MCP: [`dikciz_jobs_list`](command-reference.md#dikciz_jobs_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.jobsList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## journeyControl

Start a visible location recording or stop an exact journey ID. One recording, 64 saved journeys and 10000 points per journey. Starting requires location and notifications. Route history persists; it never messages contacts.

MCP: [`dikciz_journey_control`](command-reference.md#dikciz_journey_control). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.journeyControl` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.journeyControl.action.start` | action=start | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.journeyControl.action.stop` | action=stop | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## journeyDelete

Delete a stopped journey and its route points. Requires confirm=true.

MCP: [`dikciz_journey_delete`](command-reference.md#dikciz_journey_delete). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.journeyDelete` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## journeyRead

Read a bounded ordered page of real recorded route points and accuracy. Follow hasMore. No missing location is invented.

MCP: [`dikciz_journey_read`](command-reference.md#dikciz_journey_read). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.journeyRead` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## journeysList

Read paginated recorded journeys and interruption state.

MCP: [`dikciz_journeys_list`](command-reference.md#dikciz_journeys_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.journeysList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## launchApp

Launch an explicit app widget by semantic ID.

MCP: [`dikciz_launch_app`](command-reference.md#dikciz_launch_app). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.launchApp` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## launcherHandoff

Acknowledge, release listener ownership and launch an installed Dikciz release/debug package. Same-package requests do nothing. Verify the target after reconnecting; failed startup retries the original listener configuration.

MCP: [`dikciz_launcher_handoff`](command-reference.md#dikciz_launcher_handoff). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.launcherHandoff` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## locationGet

Read a recent permitted location or wait for a bounded new fix. Returns age, accuracy and source. timeoutMilliseconds=0 reads cache only; unavailable is explicit.

MCP: [`dikciz_location_get`](command-reference.md#dikciz_location_get). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.locationGet` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## longPress

Long press one semantic ID.

MCP: [`dikciz_long_press`](command-reference.md#dikciz_long_press). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.longPress` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## mediaSessionsList

Read current media sessions, playback state and bounded metadata through granted notification-listener access.

MCP: [`dikciz_media_sessions_list`](command-reference.md#dikciz_media_sessions_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.mediaSessionsList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## microphoneRecord

Record microphone audio to an AAC M4A file and return its path for fileRead/download. durationMilliseconds defaults to 3000. Requires microphone access and Android foreground eligibility. This is not cellular call capture.

MCP: [`dikciz_microphone_record`](command-reference.md#dikciz_microphone_record). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.microphoneRecord` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## microphoneSession

Start, inspect, stop or cancel one process-owned microphone recording. Requires foreground microphone access. Saved M4A is reported only after successful finalization. Cancellation/failure removes incomplete output. Shares ownership with microphoneRecord. Not call audio or a background recording service.

MCP: [`dikciz_microphone_session`](command-reference.md#dikciz_microphone_session). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.microphoneSession` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.microphoneSession.action.start` | action=start | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.microphoneSession.action.status` | action=status | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.microphoneSession.action.stop` | action=stop | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.microphoneSession.action.cancel` | action=cancel | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## microphoneStreamForget

Release retained audio and metadata for one terminal stream. Active or unconfirmed hardware cleanup is refused.

MCP: [`dikciz_microphone_stream_forget`](command-reference.md#dikciz_microphone_stream_forget). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.microphoneStreamForget` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## microphoneStreamRead

Read whole PCM chunks and terminal events after cursor. Save nextCursor, drain hasMore and inspect historyGap/lostEvents. Format and monotonic timing metadata accompany actual AudioRecord samples. Optional bounded long polling works across every ordinary binding.

MCP: [`dikciz_microphone_stream_read`](command-reference.md#dikciz_microphone_stream_read). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.microphoneStreamRead` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## microphoneStreamStart

Start actual microphone PCM16 little-endian capture with a dedicated Android foreground service and Stop notification. Start requires foreground eligibility and microphone access; recording can continue in the background. Replay survives client disconnect, not process death. Microphone ownership is shared with recording, recognition and video. No cellular call audio.

MCP: [`dikciz_microphone_stream_start`](command-reference.md#dikciz_microphone_stream_start). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.microphoneStreamStart` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## microphoneStreamStop

Request microphone-stream stop. Poll read/list until terminal and hardwareReleaseConfirmed. Does not discard retained audio.

MCP: [`dikciz_microphone_stream_stop`](command-reference.md#dikciz_microphone_stream_stop). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.microphoneStreamStop` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## microphoneStreamsList

Page process-owned microphone-stream metadata without returning audio. Follow hasMore. Process death loses these records.

MCP: [`dikciz_microphone_streams_list`](command-reference.md#dikciz_microphone_streams_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.microphoneStreamsList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## nativeDataStatus

Discover direct Android data resources, columns, operations and permission state. No UI automation or root.

MCP: [`dikciz_native_data_status`](command-reference.md#dikciz_native_data_status). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.nativeDataStatus` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## nfcRead

Wait for a presented NFC tag and read its NDEF bytes and technology inventory. Open and unlock Dikciz. No tag/hardware/timeout are explicit. Reader mode and tag connections are released.

MCP: [`dikciz_nfc_read`](command-reference.md#dikciz_nfc_read). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.nfcRead` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## nfcStatus

Read NFC adapter availability and enabled state without changing it.

MCP: [`dikciz_nfc_status`](command-reference.md#dikciz_nfc_status). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.nfcStatus` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## nfcTransceive

Perform one raw command/response on a presented tag using an explicit supported technology. Device-specific bytes may modify a tag. Android technology restrictions apply. Connections close after this request.

MCP: [`dikciz_nfc_transceive`](command-reference.md#dikciz_nfc_transceive). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.nfcTransceive` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.nfcTransceive.technology.IsoDep` | technology=IsoDep | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.nfcTransceive.technology.NfcA` | technology=NfcA | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.nfcTransceive.technology.NfcB` | technology=NfcB | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.nfcTransceive.technology.NfcF` | technology=NfcF | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.nfcTransceive.technology.NfcV` | technology=NfcV | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.nfcTransceive.technology.MifareClassic` | technology=MifareClassic | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.nfcTransceive.technology.MifareUltralight` | technology=MifareUltralight | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## nfcWrite

Wait for a tag and write a validated NDEF message. format=true explicitly permits formatting an unformatted compatible tag. Read-back verification is reported. Open/unlock Dikciz and present the intended tag; writes may have partial effects.

MCP: [`dikciz_nfc_write`](command-reference.md#dikciz_nfc_write). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.nfcWrite` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## notificationManage

Dismiss or snooze an active notification, or submit a free-form inline reply through a short-lived action token from notificationsList.actions. Submission does not confirm remote delivery. Android listener access is required.

MCP: [`dikciz_notification_manage`](command-reference.md#dikciz_notification_manage). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.notificationManage` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.notificationManage.action.dismiss` | action=dismiss | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.notificationManage.action.snooze` | action=snooze | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.notificationManage.action.reply` | action=reply | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## notificationsList

Read current notifications directly through the enabled notification listener, including bounded text and short-lived action tokens.

MCP: [`dikciz_notifications_list`](command-reference.md#dikciz_notifications_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.notificationsList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## openAutomationSetup

Open the native Dikciz Automation access sheet so the phone owner can grant the Android access a denied action needs.

MCP: [`dikciz_open_automation_setup`](command-reference.md#dikciz_open_automation_setup). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.openAutomationSetup` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## packageInfo

Read installed version and launch component through Android PackageManager without shell privileges.

MCP: [`dikciz_package_info`](command-reference.md#dikciz_package_info). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.packageInfo` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## remoteAccessInfo

Read the verified caller identity, effective grants, expiresAt, requestsPerMinute and exact smsAddresses restrictions. Restricted SMS reads require filter.address. Local owner bindings need no bearer token. Does not return credentials or verifiers.

MCP: [`dikciz_remote_access_info`](command-reference.md#dikciz_remote_access_info). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.remoteAccessInfo` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## remoteAuthDisable

Allow headerless local-tunnel control while retaining the existing phone-configured verifier.

MCP: [`dikciz_remote_auth_disable`](command-reference.md#dikciz_remote_auth_disable). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.remoteAuthDisable` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## remoteAuthEnable

Require the existing phone-configured remote bearer credential on both remote control planes.

MCP: [`dikciz_remote_auth_enable`](command-reference.md#dikciz_remote_auth_enable). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.remoteAuthEnable` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## remoteAuthStatus

Read remote WebSocket and MCP bearer-auth state without exposing its verifier.

MCP: [`dikciz_remote_auth_status`](command-reference.md#dikciz_remote_auth_status). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.remoteAuthStatus` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## remoteTokens

Remote access-token administration. Explicit command/action grants can delegate these operations. Creating/updating/rotating credentials can confer full owner authority. catalog lists finite permission grants. list returns metadata; create requires name and permissions and returns its generated token once. update requires id and optional name/permissions/enabled/expiry/rate/SMS-address rules; rotate requires id and returns a replacement token once, invalidating the previous secret immediately; revoke requires id. Optional expiresAt is epoch milliseconds (0 means none), requestsPerMinute is 0..10000 (0 means unlimited), and smsAddresses is an exact address allowlist. Restricted SMS reads require filter.address. Authentication must be enabled before creating tokens. Existing tokens, including disabled tokens, prevent disabling or clearing authentication. Local owner bindings, full-owner credentials and explicit remoteTokens operation grants can administer tokens.

MCP: [`dikciz_remote_tokens`](command-reference.md#dikciz_remote_tokens). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.remoteTokens` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.remoteTokens.action.catalog` | action=catalog | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.remoteTokens.action.list` | action=list | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.remoteTokens.action.create` | action=create | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.remoteTokens.action.update` | action=update | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.remoteTokens.action.revoke` | action=revoke | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.remoteTokens.action.rotate` | action=rotate | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## requestDeviceAccess

Ask the phone owner to grant one deviceAccess requirement from automationStatus. Posts a targeted notification and consent prompt. Never grants automatically or retries the blocked operation. Read state and notificationPosted before telling the user to check the phone.

MCP: [`dikciz_request_device_access`](command-reference.md#dikciz_request_device_access). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.requestDeviceAccess` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## reset

Restore the bundled Dikciz configuration while retaining permitted unknown root namespaces.

MCP: [`dikciz_config_reset`](command-reference.md#dikciz_config_reset). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.reset` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## resourceControl

Stop one resource: target script/widget disables future configuration work; conversation clears event rules and requests cancellation; imageStream/speechStream stops capture. Supply target and id, plus pageId for widgets. Legacy scriptId is accepted alone. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_resource_control`](command-reference.md#dikciz_resource_control). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.resourceControl` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.resourceControl.target.script` | target=script | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.resourceControl.target.widget` | target=widget | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.resourceControl.target.conversation` | target=conversation | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.resourceControl.target.imageStream` | target=imageStream | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.resourceControl.target.speechStream` | target=speechStream | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## resourceUsage

Read measured process CPU/RAM/storage, UID network counters and command executor CPU grouped by actor. Explicitly excludes unmeasurable per-widget battery and WebView CPU attribution. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_resource_usage`](command-reference.md#dikciz_resource_usage). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.resourceUsage` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## restart

Acknowledge, then recreate the launcher and listeners without killing the process or changing saved configuration. Reconnect and verify a new instanceId through dikciz_device_info.

MCP: [`dikciz_restart`](command-reference.md#dikciz_restart). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.restart` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## screenshot

Capture a bounded PNG of the launcher.

MCP: [`dikciz_screenshot`](command-reference.md#dikciz_screenshot). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.screenshot` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## scriptLogs

Read the bounded, sanitized script-log snapshot.

MCP: [`dikciz_script_logs`](command-reference.md#dikciz_script_logs). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.scriptLogs` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## scrollBy

Scroll the selected page by an exact pixel delta.

MCP: [`dikciz_scroll_by`](command-reference.md#dikciz_scroll_by). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.scrollBy` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## scrollTo

Scroll the selected page to a semantic ID.

MCP: [`dikciz_scroll_to`](command-reference.md#dikciz_scroll_to). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.scrollTo` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## searchCollections

List local search collections and document counts. docs and tools are protected, rebuilt from the installed APK. Other collections contain only explicitly indexed owner content.

MCP: [`dikciz_search_collections`](command-reference.md#dikciz_search_collections). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.searchCollections` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## searchIndex

Atomically upsert, remove or clear owner search documents. Reserved docs/tools collections cannot be changed. Upsert replaces IDs and accepts at most 32 documents with id, title, text and optional aliases/uri. Remove requires ids; clear removes one collection. Search indexes do not crawl files or private data. Maximum 2000 documents per custom collection, 10000 overall. Mutations are unavailable in read-only mode.

MCP: [`dikciz_search_index`](command-reference.md#dikciz_search_index). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.searchIndex` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.searchIndex.action.upsert` | action=upsert | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.searchIndex.action.remove` | action=remove | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.searchIndex.action.clear` | action=clear | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## searchQuery

Search offline collections with weighted BM25, exact identifier boosts, prefixes, authored aliases and bounded typo correction. No model or network. collection defaults to docs; tools returns current input schemas permitted by trusted turn access. Results include excerpts and IDs for searchRead. Plain text only, no SQL or FTS operators. Follow total/hasMore with limit and offset.

MCP: [`dikciz_search_query`](command-reference.md#dikciz_search_query). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.searchQuery` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## searchRead

Read the full text of one exact search result ID in a collection. docs is the default. tools returns a live contract only when permitted by trusted turn access. Never reads paths or external URLs.

MCP: [`dikciz_search_read`](command-reference.md#dikciz_search_read). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.searchRead` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## selectPage

Select one page by its stable ID.

MCP: [`dikciz_select_page`](command-reference.md#dikciz_select_page). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.selectPage` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## sensorReadings

Read a bounded sample window for all hardware sensors, including multiple sensors of one type. sensorId comes from this inventory. Follow hasMore. Each sensor reports actual values, accuracy, timestamp or an explicit unavailable reason. Registrations stop when the request ends.

MCP: [`dikciz_sensor_readings`](command-reference.md#dikciz_sensor_readings). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.sensorReadings` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## setText

Set an editable text input or text widget by semantic ID.

MCP: [`dikciz_set_text`](command-reference.md#dikciz_set_text). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.setText` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## shell

Run a command through su by default, or as the launcher app user when root is false.

MCP: [`dikciz_shell`](command-reference.md#dikciz_shell). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.shell` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## shellSessionForget

Remove one terminal shell session and its retained output. Refuses active sessions; stop and observe completion first.

MCP: [`dikciz_shell_session_forget`](command-reference.md#dikciz_shell_session_forget). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.shellSessionForget` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## shellSessionRead

Read retained shell events from an integer cursor. Follow nextCursor/hasMore; historyGap/lostEvents exposes overwritten history. Optional waitMilliseconds long-polls for an event or completion. Pages also respect the shared response-byte budget.

MCP: [`dikciz_shell_session_read`](command-reference.md#dikciz_shell_session_read). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.shellSessionRead` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## shellSessionStart

Start a process-owned shell session using the existing shell-stream runner. Read incremental events from any ordinary binding, including HTML/Lua, after transport disconnects. root defaults true; use false for the app sandbox. Output history is bounded and gaps are explicit. Sessions end with the app process.

MCP: [`dikciz_shell_session_start`](command-reference.md#dikciz_shell_session_start). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.shellSessionStart` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## shellSessionStop

Request cancellation of one process-owned shell session. Read until active=false and inspect the terminal event. Cancellation does not roll back side effects.

MCP: [`dikciz_shell_session_stop`](command-reference.md#dikciz_shell_session_stop). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.shellSessionStop` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## shellSessionsList

List process-owned active and retained terminal shell sessions, newest first, without output contents.

MCP: [`dikciz_shell_sessions_list`](command-reference.md#dikciz_shell_sessions_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.shellSessionsList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## shellStream

Stream command output through SSE log notifications. Opt in with params._meta io.modelcontextprotocol/logLevel=info. timeoutMilliseconds=0 runs until cancellation. Root defaults to true; set false for app permissions.

MCP: [`dikciz_shell_stream`](command-reference.md#dikciz_shell_stream). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.shellStream` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## shellStreamStop

Stop a stream owned by the same MCP session.

MCP: [`dikciz_shell_stream_stop`](command-reference.md#dikciz_shell_stream_stop). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.shellStreamStop` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## simList

Read active SIM subscriptions and own phone numbers directly, without opening Settings. Returns subscriptionId, slotIndex, displayName, carrierName, nullable phoneNumber and phoneNumberStatus (available, unavailable, permission_required, access_denied or unsupported), plus defaultSmsSubscriptionId and phoneNumberPermissionGranted. Requires READ_PHONE_STATE; numbers additionally need READ_PHONE_NUMBERS. Missing number permission preserves SIM metadata and returns targeted permissionRequest; autoRequestPermissions opts into owner review. Android may not supply a number. Follow hasMore for pagination.

MCP: [`dikciz_sim_list`](command-reference.md#dikciz_sim_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.simList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## smsSend

Submit an SMS through Android SmsManager. Required idempotencyKey prevents duplicate sends across retries and launcher restarts. Poll smsStatus for sent and delivery outcomes; accepted is not sent. Select a SIM explicitly when there is no default.

MCP: [`dikciz_sms_send`](command-reference.md#dikciz_sms_send). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.smsSend` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## smsStatus

Read the persisted state for an SMS submitted by Dikciz, without returning its recipient or body. Delivery remains unknown until a carrier callback arrives.

MCP: [`dikciz_sms_status`](command-reference.md#dikciz_sms_status). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.smsStatus` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## snapshot

Read the current semantic launcher UI tree.

MCP: [`dikciz_snapshot`](command-reference.md#dikciz_snapshot). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.snapshot` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## sosControl

arm starts missed-check-in monitoring; trigger starts the configured cancellation grace; checkIn resets the deadline; cancel stops future sends. arm/trigger require confirm=true, SMS, precise location and notification access. Foreground start required. Android can delay alarms or stop execution. On reboot, an armed run needs owner review. No emergency service is called.

MCP: [`dikciz_sos_control`](command-reference.md#dikciz_sos_control). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.sosControl` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.sosControl.action.arm` | action=arm | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.sosControl.action.trigger` | action=trigger | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.sosControl.action.checkIn` | action=checkIn | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.sosControl.action.cancel` | action=cancel | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## sosPlanSave

Save an inactive SOS plan with exact recipient phone numbers, message template and bounded check-in/update timings. Does not arm or send. Tokens: {location}, {accuracyMeters}, {timestamp}. Cancel an active run before editing. Contacts are never modified.

MCP: [`dikciz_sos_plan_save`](command-reference.md#dikciz_sos_plan_save). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.sosPlanSave` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## sosPreview

Preview exact contacts, message and available cached location without sending or arming.

MCP: [`dikciz_sos_preview`](command-reference.md#dikciz_sos_preview). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.sosPreview` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## sosStatus

Read the saved SOS plan, durable run state, deadline and idempotency keys for smsStatus delivery inspection. Accepted updates are not confirmed delivery. warning reports observed countdown presentation, unlocked-screen visibility, remainingSeconds, alarmVolume and speechState. Warning audio follows Android volume/DND and never proves the owner heard it.

MCP: [`dikciz_sos_status`](command-reference.md#dikciz_sos_status). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.sosStatus` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## speechLanguages

Query the selected recognition provider for paginated installed, pending, downloadable on-device and online languages. Android 13+. No microphone capture. Provider support and errors are explicit.

MCP: [`dikciz_speech_languages`](command-reference.md#dikciz_speech_languages). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.speechLanguages` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.speechLanguages.mode.onDevice` | mode=onDevice | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.speechLanguages.mode.system` | mode=system | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## speechModelDownload

Explicitly request recognition-model download and wait for available/scheduled/error. May use network data and Android owner confirmation. Android 14+. Scheduled does not mean available. Timeout cannot cancel provider work. Poll speechModelStatus for progress while this request runs.

MCP: [`dikciz_speech_model_download`](command-reference.md#dikciz_speech_model_download). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.speechModelDownload` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.speechModelDownload.mode.onDevice` | mode=onDevice | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.speechModelDownload.mode.system` | mode=system | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## speechModelStatus

Read current/last process-owned model request and provider progress. Does not resume observation after timeout, scheduled callback or process death.

MCP: [`dikciz_speech_model_status`](command-reference.md#dikciz_speech_model_status). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.speechModelStatus` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## speechRecognitionStatus

Discover Android speech recognition and strict on-device availability without recording. Reports whether recognition is active.

MCP: [`dikciz_speech_recognition_status`](command-reference.md#dikciz_speech_recognition_status). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.speechRecognitionStatus` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## speechRecognitionStop

Cancel the active speech recognition request and destroy its Android recognizer. Returns idle when no request is active.

MCP: [`dikciz_speech_recognition_stop`](command-reference.md#dikciz_speech_recognition_stop). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.speechRecognitionStop` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## speechRecognize

Recognize microphone speech and return text, alternatives and confidence. mode defaults to onDevice, which never falls back to a network recognizer. system explicitly allows the installed service to use its network backend. Requires microphone permission and foreground eligibility. Use speechRecognitionStop to cancel.

MCP: [`dikciz_speech_recognize`](command-reference.md#dikciz_speech_recognize). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.speechRecognize` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.speechRecognize.mode.onDevice` | mode=onDevice | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.speechRecognize.mode.system` | mode=system | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## speechSpeak

Speak text through the Android text-to-speech engine and device audio output. Returns only after synthesis completes. Does not inject speech into cellular calls.

MCP: [`dikciz_speech_speak`](command-reference.md#dikciz_speech_speak). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.speechSpeak` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## speechStop

Stop active Dikciz text-to-speech output. The original request returns speech_cancelled. Idle is explicit.

MCP: [`dikciz_speech_stop`](command-reference.md#dikciz_speech_stop). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.speechStop` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## speechStreamForget

Remove one completed speech stream and its replayed transcripts. Active sessions must stop first.

MCP: [`dikciz_speech_stream_forget`](command-reference.md#dikciz_speech_stream_forget). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.speechStreamForget` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## speechStreamRead

Read bounded partial/final speech events with exclusive cursor, historyGap, lostEvents and terminal state. Does not start capture. Retained in memory for five minutes after completion. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_speech_stream_read`](command-reference.md#dikciz_speech_stream_read). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.speechStreamRead` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## speechStreamStart

Start foreground microphone speech recognition with partial-result replay. Requires Android microphone access and a recognition provider. Accepted is not recognized; read state/events. Provider may emit only a final result. No automatic fallback from onDevice to system.

MCP: [`dikciz_speech_stream_start`](command-reference.md#dikciz_speech_stream_start). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.speechStreamStart` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.speechStreamStart.mode.onDevice` | mode=onDevice | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.speechStreamStart.mode.system` | mode=system | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## speechStreamStop

Stop one owned speech session and report actual cleanup state. Does not cancel another session. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_speech_stream_stop`](command-reference.md#dikciz_speech_stream_stop). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.speechStreamStop` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## speechStreamsList

List at most four retained speech streams without transcript payloads.

MCP: [`dikciz_speech_streams_list`](command-reference.md#dikciz_speech_streams_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.speechStreamsList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## speechSynthesize

Synthesize text into a saved WAV audio file without playing it. Shares voice, language, rate, pitch, timeout and cancellation with speechSpeak. Read the saved path through fileRead/download.

MCP: [`dikciz_speech_synthesize`](command-reference.md#dikciz_speech_synthesize). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.speechSynthesize` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## speechTranscribeFile

Upload one audio file to the explicitly selected OpenAI-compatible provider's audio/transcriptions endpoint. Uses its encrypted saved API key. Requires profileId, model and path. Sends file contents outside the phone; never opens the microphone. Provider support and usage charges apply. Requires the matching explicit operation grant for scoped remote clients.

MCP: [`dikciz_speech_transcribe_file`](command-reference.md#dikciz_speech_transcribe_file). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.speechTranscribeFile` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## speechVoices

List voices from the installed Android TTS engine, including language, quality, latency and network requirement. Follow hasMore.

MCP: [`dikciz_speech_voices`](command-reference.md#dikciz_speech_voices). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.speechVoices` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## systemSetting

Read or write an allowlisted system setting. Set requires owner-granted Modify system settings and returns read-back.

MCP: [`dikciz_system_setting`](command-reference.md#dikciz_system_setting). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.systemSetting` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.systemSetting.action.get` | action=get | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.systemSetting.action.set` | action=set | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.systemSetting.setting.brightness` | setting=brightness | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.systemSetting.setting.automaticBrightness` | setting=automaticBrightness | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.systemSetting.setting.automaticRotation` | setting=automaticRotation | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.systemSetting.setting.rotation` | setting=rotation | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.systemSetting.setting.screenTimeoutMilliseconds` | setting=screenTimeoutMilliseconds | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.systemSetting.action.get.setting.brightness` | action=get, setting=brightness | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.systemSetting.action.get.setting.automaticBrightness` | action=get, setting=automaticBrightness | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.systemSetting.action.get.setting.automaticRotation` | action=get, setting=automaticRotation | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.systemSetting.action.get.setting.rotation` | action=get, setting=rotation | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.systemSetting.action.get.setting.screenTimeoutMilliseconds` | action=get, setting=screenTimeoutMilliseconds | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.systemSetting.action.set.setting.brightness` | action=set, setting=brightness | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.systemSetting.action.set.setting.automaticBrightness` | action=set, setting=automaticBrightness | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.systemSetting.action.set.setting.automaticRotation` | action=set, setting=automaticRotation | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.systemSetting.action.set.setting.rotation` | action=set, setting=rotation | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.systemSetting.action.set.setting.screenTimeoutMilliseconds` | action=set, setting=screenTimeoutMilliseconds | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## tap

Tap one actionable semantic ID.

MCP: [`dikciz_tap`](command-reference.md#dikciz_tap). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.tap` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## torchControl

Observe flash-camera torch state through bounded Android callbacks or submit on/off. Unknown and unavailable are explicit. Submission does not claim physical light verification.

MCP: [`dikciz_torch_control`](command-reference.md#dikciz_torch_control). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.torchControl` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.torchControl.action.status` | action=status | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.torchControl.action.on` | action=on | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.torchControl.action.off` | action=off | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## uiDump

Read the current semantic launcher UI tree.

MCP: [`dikciz_ui_dump`](command-reference.md#dikciz_ui_dump). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.uiDump` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## usbDevices

List attached USB host devices and per-device access. Does not request USB permission or transfer bytes.

MCP: [`dikciz_usb_devices`](command-reference.md#dikciz_usb_devices). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.usbDevices` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## usbRequestAccess

Request Android owner approval for one currently attached USB-host device. Poll usbDevices.permissionGranted, then retry explicitly. Access expires when the device disconnects.

MCP: [`dikciz_usb_request_access`](command-reference.md#dikciz_usb_request_access). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.usbRequestAccess` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## usbTransfer

Perform one bounded USB control or bulk transfer using owner-granted access. Use descriptors from usbDevices. Select length for input or dataBase64 for output. Connections and claimed interfaces are always released. Failed writes can have partial effects; do not retry automatically.

MCP: [`dikciz_usb_transfer`](command-reference.md#dikciz_usb_transfer). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.usbTransfer` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.usbTransfer.kind.control` | kind=control | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.usbTransfer.kind.bulk` | kind=bulk | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## ussdRequest

Send one complete numeric star/hash USSD code through Android TelephonyManager and observe its carrier response. Requires CALL_PHONE. Optional subscriptionId defaults to the voice SIM. USSD can change carrier services or incur charges. Timeout/interruption cannot cancel network work; do not repeat an uncertain request automatically. Use jobStart for reconnectable duplicate-protected submission.

MCP: [`dikciz_ussd_request`](command-reference.md#dikciz_ussd_request). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.ussdRequest` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## vibrationControl

Discover vibrator and predefined-effect support; submit a bounded pulse, waveform, predefined effect or cancellation. Android settings may suppress physical vibration.

MCP: [`dikciz_vibration_control`](command-reference.md#dikciz_vibration_control). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.vibrationControl` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.vibrationControl.action.status` | action=status | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.vibrationControl.action.vibrate` | action=vibrate | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.vibrationControl.action.waveform` | action=waveform | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.vibrationControl.action.effect` | action=effect | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.vibrationControl.action.cancel` | action=cancel | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## waitFor

Wait up to a bounded interval for one semantic ID to appear in the launcher snapshot.

MCP: [`dikciz_wait_for`](command-reference.md#dikciz_wait_for). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.waitFor` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## widgetGet

Read one widget by its stable H/V widget address.

MCP: [`dikciz_widget_get`](command-reference.md#dikciz_widget_get). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.widgetGet` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## widgetMove

Move one widget to an explicit grid rectangle. Rejects with grid_collision or grid_bounds and writes nothing.

MCP: [`dikciz_widget_move`](command-reference.md#dikciz_widget_move). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.widgetMove` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## widgetResize

Resize one widget to an explicit grid rectangle. Rejects with grid_collision or grid_bounds and writes nothing.

MCP: [`dikciz_widget_resize`](command-reference.md#dikciz_widget_resize). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.widgetResize` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## wifiConnectionStart

Request an owner-approved local-only Wi-Fi connection and retain its callback. Android may reject or time out. Poll wifiConnectionStatus. A returned networkHandle identifies this process-scoped network; the phone's default internet route is not changed. Start with Dikciz foreground.

MCP: [`dikciz_wifi_connection_start`](command-reference.md#dikciz_wifi_connection_start). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.wifiConnectionStart` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiConnectionStart.security.open` | security=open | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiConnectionStart.security.wpa2` | security=wpa2 | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiConnectionStart.security.wpa3` | security=wpa3 | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## wifiConnectionStatus

Read actual callback state for a network request or Android save approval; does not connect.

MCP: [`dikciz_wifi_connection_status`](command-reference.md#dikciz_wifi_connection_status). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.wifiConnectionStatus` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## wifiConnectionStop

Release the exact local network request; does not remove saved Wi-Fi networks.

MCP: [`dikciz_wifi_connection_stop`](command-reference.md#dikciz_wifi_connection_stop). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.wifiConnectionStop` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## wifiConnectionsList

List retained local network/hotspot requests with pagination. Hotspot credentials require full owner access or this explicit command grant.

MCP: [`dikciz_wifi_connections_list`](command-reference.md#dikciz_wifi_connections_list). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.wifiConnectionsList` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## wifiHotspotStart

Start a bounded Android local-only hotspot while foreground. Android selects SSID/passphrase and can reject the request. Poll wifiHotspotStatus. This does not enable cellular internet tethering, which ordinary apps cannot silently control.

MCP: [`dikciz_wifi_hotspot_start`](command-reference.md#dikciz_wifi_hotspot_start). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.wifiHotspotStart` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## wifiHotspotStatus

Read retained local-only hotspot state and credentials with full owner access or this explicit command grant. Starting requires wifiHotspotStart.

MCP: [`dikciz_wifi_hotspot_status`](command-reference.md#dikciz_wifi_hotspot_status). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.wifiHotspotStatus` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## wifiHotspotStop

Close an exact local-only hotspot reservation.

MCP: [`dikciz_wifi_hotspot_stop`](command-reference.md#dikciz_wifi_hotspot_stop). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.wifiHotspotStop` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## wifiNetworksConfigure

Suggest a Wi-Fi network, remove this app's suggestions by SSID, or ask Android to save a network. Suggestions are not saved networks and never prove a connection. save requires owner approval and exposes its result through wifiConnectionStatus. Credentials are not logged or stored in public configuration.

MCP: [`dikciz_wifi_networks_configure`](command-reference.md#dikciz_wifi_networks_configure). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.wifiNetworksConfigure` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiNetworksConfigure.action.suggest` | action=suggest | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiNetworksConfigure.action.removeSuggestion` | action=removeSuggestion | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiNetworksConfigure.action.save` | action=save | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiNetworksConfigure.security.open` | security=open | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiNetworksConfigure.security.wpa2` | security=wpa2 | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiNetworksConfigure.security.wpa3` | security=wpa3 | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiNetworksConfigure.action.suggest.security.open` | action=suggest, security=open | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiNetworksConfigure.action.suggest.security.wpa2` | action=suggest, security=wpa2 | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiNetworksConfigure.action.suggest.security.wpa3` | action=suggest, security=wpa3 | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiNetworksConfigure.action.removeSuggestion.security.open` | action=removeSuggestion, security=open | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiNetworksConfigure.action.removeSuggestion.security.wpa2` | action=removeSuggestion, security=wpa2 | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiNetworksConfigure.action.removeSuggestion.security.wpa3` | action=removeSuggestion, security=wpa3 | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiNetworksConfigure.action.save.security.open` | action=save, security=open | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiNetworksConfigure.action.save.security.wpa2` | action=save, security=wpa2 | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |
| `command.wifiNetworksConfigure.action.save.security.wpa3` | action=save, security=wpa3 | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## wifiScan

Request an Android Wi-Fi scan, wait for an updated-results broadcast and return the paginated cache with per-result freshness. Android can throttle or reject scans. Completion broadcasts are global, not caller-specific. Timeout releases the receiver but cannot cancel the platform scan. Fine location and enabled location services are required.

MCP: [`dikciz_wifi_scan`](command-reference.md#dikciz_wifi_scan). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.wifiScan` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## wifiScanResults

Read cached Wi-Fi scan results with Android timestamps. Requires location access; does not initiate a scan or connect.

MCP: [`dikciz_wifi_scan_results`](command-reference.md#dikciz_wifi_scan_results). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.

| Grant | Exact selectors | Definition |
| --- | --- | --- |
| `command.wifiScanResults` | All accepted arguments | Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope. |

## Restrictions and errors

Missing grants return remote_permission_denied. Unknown/duplicate grants return validation_failed. Invalid credentials fail authentication. Token expiry, enabled state, rotation, revocation, rolling request limits and exact SMS address restrictions are separate checks; see [token administration](remote-access-tokens.md). A previously accepted effect cannot be recalled by revoking a token.

The picker searches grant IDs, titles and descriptions without dropping checked items. Common feature grants appear first; Show command permissions reveals the operation catalogue. Search also finds command grants directly. Review both the command definition and the Android access it needs before granting it.
