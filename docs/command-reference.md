---
title: "Command reference"
description: "Look up every discovered MCP tool and ordinary command, with complete input schemas, parameters, defaults, bounds and language invocations."
permalink: /command-reference/
nav_order: 3
parent: "Remote control"
---

# Command reference

<!-- Regenerate with make dikciz-command-docs from captured tools/list discovery. -->

This reference contains 215 discovered MCP tools and their complete input schemas. Discovery from the installed build is authoritative. Transport-independent procedures, Android permissions and results are described in the [automation guide](automation-control.md), [documentation index](index.md) and focused task guides. MCP tools use the same ordinary commands as HTTP, WebSocket, HTML and Lua. The [developer manual](developer-manual.md) defines runtimes, results and the task-guide index. [JavaScript](javascript-api.md), [Lua](lua-api.md), [typed actions](typed-actions.md) and [token permissions](token-permissions.md) provide the language and authorization contracts.

Schemas include nested fields, required values, defaults and bounds. autoRequestPermissions requests owner review; it never grants access or retries the operation. Inspect returned state and errors before continuing.

## Command index

| Ordinary command | MCP tool |
| --- | --- |
| [`accessibilityAction`](#dikciz_accessibility_action) | [`dikciz_accessibility_action`](#dikciz_accessibility_action) |
| [`accessibilitySnapshot`](#dikciz_accessibility_snapshot) | [`dikciz_accessibility_snapshot`](#dikciz_accessibility_snapshot) |
| [`activityCancel`](#dikciz_activity_cancel) | [`dikciz_activity_cancel`](#dikciz_activity_cancel) |
| [`activityRead`](#dikciz_activity_read) | [`dikciz_activity_read`](#dikciz_activity_read) |
| [`addWidget`](#dikciz_add_widget) | [`dikciz_add_widget`](#dikciz_add_widget) |
| [`androidApiCallback`](#dikciz_android_api_callback) | [`dikciz_android_api_callback`](#dikciz_android_api_callback) |
| [`androidApiDiscover`](#dikciz_android_api_discover) | [`dikciz_android_api_discover`](#dikciz_android_api_discover) |
| [`androidApiInvoke`](#dikciz_android_api_invoke) | [`dikciz_android_api_invoke`](#dikciz_android_api_invoke) |
| [`androidApiObjects`](#dikciz_android_api_objects) | [`dikciz_android_api_objects`](#dikciz_android_api_objects) |
| [`androidApiRead`](#dikciz_android_api_read) | [`dikciz_android_api_read`](#dikciz_android_api_read) |
| [`androidApiRelease`](#dikciz_android_api_release) | [`dikciz_android_api_release`](#dikciz_android_api_release) |
| [`apkInstall`](#dikciz_apk_install) | [`dikciz_apk_install`](#dikciz_apk_install) |
| [`apkInstallCancel`](#dikciz_apk_install_cancel) | [`dikciz_apk_install_cancel`](#dikciz_apk_install_cancel) |
| [`apkInstallSet`](#dikciz_apk_install_set) | [`dikciz_apk_install_set`](#dikciz_apk_install_set) |
| [`apkInstallStatus`](#dikciz_apk_install_status) | [`dikciz_apk_install_status`](#dikciz_apk_install_status) |
| [`appAction`](#dikciz_app_action) | [`dikciz_app_action`](#dikciz_app_action) |
| [`appCatalogue`](#dikciz_app_catalogue) | [`dikciz_app_catalogue`](#dikciz_app_catalogue) |
| [`appIcon`](#dikciz_app_icon) | [`dikciz_app_icon`](#dikciz_app_icon) |
| [`assistantRoutinePreview`](#dikciz_assistant_routine_preview) | [`dikciz_assistant_routine_preview`](#dikciz_assistant_routine_preview) |
| [`assistantRoutineRun`](#dikciz_assistant_routine_run) | [`dikciz_assistant_routine_run`](#dikciz_assistant_routine_run) |
| [`assistantRoutineSubscribe`](#dikciz_assistant_routine_subscribe) | [`dikciz_assistant_routine_subscribe`](#dikciz_assistant_routine_subscribe) |
| [`assistantRoutines`](#dikciz_assistant_routines) | [`dikciz_assistant_routines`](#dikciz_assistant_routines) |
| [`audioDevices`](#dikciz_audio_devices) | [`dikciz_audio_devices`](#dikciz_audio_devices) |
| [`audioPlayback`](#dikciz_audio_playback) | [`dikciz_audio_playback`](#dikciz_audio_playback) |
| [`automationDispatch`](#dikciz_automation_dispatch) | [`dikciz_automation_dispatch`](#dikciz_automation_dispatch) |
| [`automationRuleDelete`](#dikciz_automation_rule_delete) | [`dikciz_automation_rule_delete`](#dikciz_automation_rule_delete) |
| [`automationRulePreview`](#dikciz_automation_rule_preview) | [`dikciz_automation_rule_preview`](#dikciz_automation_rule_preview) |
| [`automationRuleSave`](#dikciz_automation_rule_save) | [`dikciz_automation_rule_save`](#dikciz_automation_rule_save) |
| [`automationRulesList`](#dikciz_automation_rules_list) | [`dikciz_automation_rules_list`](#dikciz_automation_rules_list) |
| [`automationServiceSync`](#dikciz_automation_service_sync) | [`dikciz_automation_service_sync`](#dikciz_automation_service_sync) |
| [`automationStatus`](#dikciz_automation_status) | [`dikciz_automation_status`](#dikciz_automation_status) |
| [`automationTrigger`](#dikciz_automation_trigger) | [`dikciz_automation_trigger`](#dikciz_automation_trigger) |
| [`backupCreate`](#dikciz_backup_create) | [`dikciz_backup_create`](#dikciz_backup_create) |
| [`backupPreview`](#dikciz_backup_preview) | [`dikciz_backup_preview`](#dikciz_backup_preview) |
| [`backupRestore`](#dikciz_backup_restore) | [`dikciz_backup_restore`](#dikciz_backup_restore) |
| [`backupsList`](#dikciz_backups_list) | [`dikciz_backups_list`](#dikciz_backups_list) |
| [`bluetoothDevices`](#dikciz_bluetooth_devices) | [`dikciz_bluetooth_devices`](#dikciz_bluetooth_devices) |
| [`bluetoothExchange`](#dikciz_bluetooth_exchange) | [`dikciz_bluetooth_exchange`](#dikciz_bluetooth_exchange) |
| [`bluetoothGatt`](#dikciz_bluetooth_gatt) | [`dikciz_bluetooth_gatt`](#dikciz_bluetooth_gatt) |
| [`bluetoothPair`](#dikciz_bluetooth_pair) | [`dikciz_bluetooth_pair`](#dikciz_bluetooth_pair) |
| [`bluetoothScan`](#dikciz_bluetooth_scan) | [`dikciz_bluetooth_scan`](#dikciz_bluetooth_scan) |
| [`callAudio`](#dikciz_call_audio) | [`dikciz_call_audio`](#dikciz_call_audio) |
| [`callControl`](#dikciz_call_control) | [`dikciz_call_control`](#dikciz_call_control) |
| [`cameraCapabilities`](#dikciz_camera_capabilities) | [`dikciz_camera_capabilities`](#dikciz_camera_capabilities) |
| [`cameraCapture`](#dikciz_camera_capture) | [`dikciz_camera_capture`](#dikciz_camera_capture) |
| [`cameraList`](#dikciz_camera_list) | [`dikciz_camera_list`](#dikciz_camera_list) |
| [`cameraVideoRecord`](#dikciz_camera_video_record) | [`dikciz_camera_video_record`](#dikciz_camera_video_record) |
| [`chatControl`](#dikciz_chat) | [`dikciz_chat`](#dikciz_chat) |
| [`clipboardGet`](#dikciz_clipboard_get) | [`dikciz_clipboard_get`](#dikciz_clipboard_get) |
| [`clipboardSet`](#dikciz_clipboard_set) | [`dikciz_clipboard_set`](#dikciz_clipboard_set) |
| [`configGet`](#dikciz_config_get) | [`dikciz_config_get`](#dikciz_config_get) |
| [`configReplace`](#dikciz_config_replace) | [`dikciz_config_replace`](#dikciz_config_replace) |
| [`reset`](#dikciz_config_reset) | [`dikciz_config_reset`](#dikciz_config_reset) |
| [`configSeed`](#dikciz_config_seed) | [`dikciz_config_seed`](#dikciz_config_seed) |
| [`contentBatch`](#dikciz_content_batch) | [`dikciz_content_batch`](#dikciz_content_batch) |
| [`contentQuery`](#dikciz_content_query) | [`dikciz_content_query`](#dikciz_content_query) |
| [`contentWrite`](#dikciz_content_write) | [`dikciz_content_write`](#dikciz_content_write) |
| [`controlStatus`](#dikciz_control_status) | [`dikciz_control_status`](#dikciz_control_status) |
| [`customAppAlert`](#dikciz_custom_app_alert) | [`dikciz_custom_app_alert`](#dikciz_custom_app_alert) |
| [`customAppControl`](#dikciz_custom_app_control) | [`dikciz_custom_app_control`](#dikciz_custom_app_control) |
| [`customAppEvent`](#dikciz_custom_app_event) | [`dikciz_custom_app_event`](#dikciz_custom_app_event) |
| [`customAppInstall`](#dikciz_custom_app_install) | [`dikciz_custom_app_install`](#dikciz_custom_app_install) |
| [`customAppRead`](#dikciz_custom_app_read) | [`dikciz_custom_app_read`](#dikciz_custom_app_read) |
| [`customAppRuntime`](#dikciz_custom_app_runtime) | [`dikciz_custom_app_runtime`](#dikciz_custom_app_runtime) |
| [`customAppSchedule`](#dikciz_custom_app_schedule) | [`dikciz_custom_app_schedule`](#dikciz_custom_app_schedule) |
| [`customAppState`](#dikciz_custom_app_state) | [`dikciz_custom_app_state`](#dikciz_custom_app_state) |
| [`customAppsList`](#dikciz_custom_apps_list) | [`dikciz_custom_apps_list`](#dikciz_custom_apps_list) |
| [`deviceConnectionClose`](#dikciz_device_connection_close) | [`dikciz_device_connection_close`](#dikciz_device_connection_close) |
| [`deviceConnectionExchange`](#dikciz_device_connection_exchange) | [`dikciz_device_connection_exchange`](#dikciz_device_connection_exchange) |
| [`deviceConnectionOpen`](#dikciz_device_connection_open) | [`dikciz_device_connection_open`](#dikciz_device_connection_open) |
| [`deviceConnectionRead`](#dikciz_device_connection_read) | [`dikciz_device_connection_read`](#dikciz_device_connection_read) |
| [`deviceConnectionsList`](#dikciz_device_connections_list) | [`dikciz_device_connections_list`](#dikciz_device_connections_list) |
| [`deviceInfo`](#dikciz_device_info) | [`dikciz_device_info`](#dikciz_device_info) |
| [`deviceState`](#dikciz_device_state) | [`dikciz_device_state`](#dikciz_device_state) |
| [`diagnostics`](#dikciz_diagnostics) | [`dikciz_diagnostics`](#dikciz_diagnostics) |
| [`displayScreenshot`](#dikciz_display_screenshot) | [`dikciz_display_screenshot`](#dikciz_display_screenshot) |
| [`documentScan`](#dikciz_document_scan) | [`dikciz_document_scan`](#dikciz_document_scan) |
| [`eventConsumerAck`](#dikciz_event_consumer_ack) | [`dikciz_event_consumer_ack`](#dikciz_event_consumer_ack) |
| [`eventConsumerForget`](#dikciz_event_consumer_forget) | [`dikciz_event_consumer_forget`](#dikciz_event_consumer_forget) |
| [`eventConsumerRead`](#dikciz_event_consumer_read) | [`dikciz_event_consumer_read`](#dikciz_event_consumer_read) |
| [`eventConsumersList`](#dikciz_event_consumers_list) | [`dikciz_event_consumers_list`](#dikciz_event_consumers_list) |
| [`eventJournalConfigure`](#dikciz_event_journal_configure) | [`dikciz_event_journal_configure`](#dikciz_event_journal_configure) |
| [`eventJournalRead`](#dikciz_event_journal_read) | [`dikciz_event_journal_read`](#dikciz_event_journal_read) |
| [`eventJournalStatus`](#dikciz_event_journal_status) | [`dikciz_event_journal_status`](#dikciz_event_journal_status) |
| [`fileList`](#dikciz_file_list) | [`dikciz_file_list`](#dikciz_file_list) |
| [`fileManage`](#dikciz_file_manage) | [`dikciz_file_manage`](#dikciz_file_manage) |
| [`fileMkdir`](#dikciz_file_mkdir) | [`dikciz_file_mkdir`](#dikciz_file_mkdir) |
| [`fileRead`](#dikciz_file_read) | [`dikciz_file_read`](#dikciz_file_read) |
| [`fileWrite`](#dikciz_file_write) | [`dikciz_file_write`](#dikciz_file_write) |
| [`find`](#dikciz_find) | [`dikciz_find`](#dikciz_find) |
| [`geocode`](#dikciz_geocode) | [`dikciz_geocode`](#dikciz_geocode) |
| [`geofenceDelete`](#dikciz_geofence_delete) | [`dikciz_geofence_delete`](#dikciz_geofence_delete) |
| [`geofenceSave`](#dikciz_geofence_save) | [`dikciz_geofence_save`](#dikciz_geofence_save) |
| [`geofencesList`](#dikciz_geofences_list) | [`dikciz_geofences_list`](#dikciz_geofences_list) |
| [`gnssStatus`](#dikciz_gnss_status) | [`dikciz_gnss_status`](#dikciz_gnss_status) |
| [`gridSet`](#dikciz_grid_set) | [`dikciz_grid_set`](#dikciz_grid_set) |
| [`healthPermissionsRequest`](#dikciz_health_permissions_request) | [`dikciz_health_permissions_request`](#dikciz_health_permissions_request) |
| [`healthRecordsAggregate`](#dikciz_health_records_aggregate) | [`dikciz_health_records_aggregate`](#dikciz_health_records_aggregate) |
| [`healthRecordsCatalogue`](#dikciz_health_records_catalogue) | [`dikciz_health_records_catalogue`](#dikciz_health_records_catalogue) |
| [`healthRecordsDelete`](#dikciz_health_records_delete) | [`dikciz_health_records_delete`](#dikciz_health_records_delete) |
| [`healthRecordsRead`](#dikciz_health_records_read) | [`dikciz_health_records_read`](#dikciz_health_records_read) |
| [`healthRecordsWrite`](#dikciz_health_records_write) | [`dikciz_health_records_write`](#dikciz_health_records_write) |
| [`homeGet`](#dikciz_home_get) | [`dikciz_home_get`](#dikciz_home_get) |
| [`htmlWidgetRendererCrash`](#dikciz_html_widget_renderer_crash) | [`dikciz_html_widget_renderer_crash`](#dikciz_html_widget_renderer_crash) |
| [`imageRecognize`](#dikciz_image_recognize) | [`dikciz_image_recognize`](#dikciz_image_recognize) |
| [`imageStreamForget`](#dikciz_image_stream_forget) | [`dikciz_image_stream_forget`](#dikciz_image_stream_forget) |
| [`imageStreamRead`](#dikciz_image_stream_read) | [`dikciz_image_stream_read`](#dikciz_image_stream_read) |
| [`imageStreamStart`](#dikciz_image_stream_start) | [`dikciz_image_stream_start`](#dikciz_image_stream_start) |
| [`imageStreamStop`](#dikciz_image_stream_stop) | [`dikciz_image_stream_stop`](#dikciz_image_stream_stop) |
| [`imageStreamsList`](#dikciz_image_streams_list) | [`dikciz_image_streams_list`](#dikciz_image_streams_list) |
| [`intent`](#dikciz_intent) | [`dikciz_intent`](#dikciz_intent) |
| [`jobCancel`](#dikciz_job_cancel) | [`dikciz_job_cancel`](#dikciz_job_cancel) |
| [`jobStart`](#dikciz_job_start) | [`dikciz_job_start`](#dikciz_job_start) |
| [`jobStatus`](#dikciz_job_status) | [`dikciz_job_status`](#dikciz_job_status) |
| [`jobsList`](#dikciz_jobs_list) | [`dikciz_jobs_list`](#dikciz_jobs_list) |
| [`journeyControl`](#dikciz_journey_control) | [`dikciz_journey_control`](#dikciz_journey_control) |
| [`journeyDelete`](#dikciz_journey_delete) | [`dikciz_journey_delete`](#dikciz_journey_delete) |
| [`journeyRead`](#dikciz_journey_read) | [`dikciz_journey_read`](#dikciz_journey_read) |
| [`journeysList`](#dikciz_journeys_list) | [`dikciz_journeys_list`](#dikciz_journeys_list) |
| [`launchApp`](#dikciz_launch_app) | [`dikciz_launch_app`](#dikciz_launch_app) |
| [`launcherHandoff`](#dikciz_launcher_handoff) | [`dikciz_launcher_handoff`](#dikciz_launcher_handoff) |
| [`locationGet`](#dikciz_location_get) | [`dikciz_location_get`](#dikciz_location_get) |
| [`longPress`](#dikciz_long_press) | [`dikciz_long_press`](#dikciz_long_press) |
| [`mediaSessionsList`](#dikciz_media_sessions_list) | [`dikciz_media_sessions_list`](#dikciz_media_sessions_list) |
| [`microphoneRecord`](#dikciz_microphone_record) | [`dikciz_microphone_record`](#dikciz_microphone_record) |
| [`microphoneSession`](#dikciz_microphone_session) | [`dikciz_microphone_session`](#dikciz_microphone_session) |
| [`microphoneStreamForget`](#dikciz_microphone_stream_forget) | [`dikciz_microphone_stream_forget`](#dikciz_microphone_stream_forget) |
| [`microphoneStreamRead`](#dikciz_microphone_stream_read) | [`dikciz_microphone_stream_read`](#dikciz_microphone_stream_read) |
| [`microphoneStreamStart`](#dikciz_microphone_stream_start) | [`dikciz_microphone_stream_start`](#dikciz_microphone_stream_start) |
| [`microphoneStreamStop`](#dikciz_microphone_stream_stop) | [`dikciz_microphone_stream_stop`](#dikciz_microphone_stream_stop) |
| [`microphoneStreamsList`](#dikciz_microphone_streams_list) | [`dikciz_microphone_streams_list`](#dikciz_microphone_streams_list) |
| [`nativeDataStatus`](#dikciz_native_data_status) | [`dikciz_native_data_status`](#dikciz_native_data_status) |
| [`nfcRead`](#dikciz_nfc_read) | [`dikciz_nfc_read`](#dikciz_nfc_read) |
| [`nfcStatus`](#dikciz_nfc_status) | [`dikciz_nfc_status`](#dikciz_nfc_status) |
| [`nfcTransceive`](#dikciz_nfc_transceive) | [`dikciz_nfc_transceive`](#dikciz_nfc_transceive) |
| [`nfcWrite`](#dikciz_nfc_write) | [`dikciz_nfc_write`](#dikciz_nfc_write) |
| [`notificationManage`](#dikciz_notification_manage) | [`dikciz_notification_manage`](#dikciz_notification_manage) |
| [`notificationsList`](#dikciz_notifications_list) | [`dikciz_notifications_list`](#dikciz_notifications_list) |
| [`openAutomationSetup`](#dikciz_open_automation_setup) | [`dikciz_open_automation_setup`](#dikciz_open_automation_setup) |
| [`packageInfo`](#dikciz_package_info) | [`dikciz_package_info`](#dikciz_package_info) |
| [`remoteAccessInfo`](#dikciz_remote_access_info) | [`dikciz_remote_access_info`](#dikciz_remote_access_info) |
| [`remoteAuthDisable`](#dikciz_remote_auth_disable) | [`dikciz_remote_auth_disable`](#dikciz_remote_auth_disable) |
| [`remoteAuthEnable`](#dikciz_remote_auth_enable) | [`dikciz_remote_auth_enable`](#dikciz_remote_auth_enable) |
| [`remoteAuthStatus`](#dikciz_remote_auth_status) | [`dikciz_remote_auth_status`](#dikciz_remote_auth_status) |
| [`remoteTokens`](#dikciz_remote_tokens) | [`dikciz_remote_tokens`](#dikciz_remote_tokens) |
| [`requestDeviceAccess`](#dikciz_request_device_access) | [`dikciz_request_device_access`](#dikciz_request_device_access) |
| [`resourceControl`](#dikciz_resource_control) | [`dikciz_resource_control`](#dikciz_resource_control) |
| [`resourceUsage`](#dikciz_resource_usage) | [`dikciz_resource_usage`](#dikciz_resource_usage) |
| [`restart`](#dikciz_restart) | [`dikciz_restart`](#dikciz_restart) |
| [`screenshot`](#dikciz_screenshot) | [`dikciz_screenshot`](#dikciz_screenshot) |
| [`scriptLogs`](#dikciz_script_logs) | [`dikciz_script_logs`](#dikciz_script_logs) |
| [`scrollBy`](#dikciz_scroll_by) | [`dikciz_scroll_by`](#dikciz_scroll_by) |
| [`scrollTo`](#dikciz_scroll_to) | [`dikciz_scroll_to`](#dikciz_scroll_to) |
| [`searchCollections`](#dikciz_search_collections) | [`dikciz_search_collections`](#dikciz_search_collections) |
| [`searchIndex`](#dikciz_search_index) | [`dikciz_search_index`](#dikciz_search_index) |
| [`searchQuery`](#dikciz_search_query) | [`dikciz_search_query`](#dikciz_search_query) |
| [`searchRead`](#dikciz_search_read) | [`dikciz_search_read`](#dikciz_search_read) |
| [`selectPage`](#dikciz_select_page) | [`dikciz_select_page`](#dikciz_select_page) |
| [`sensorReadings`](#dikciz_sensor_readings) | [`dikciz_sensor_readings`](#dikciz_sensor_readings) |
| [`setText`](#dikciz_set_text) | [`dikciz_set_text`](#dikciz_set_text) |
| [`shell`](#dikciz_shell) | [`dikciz_shell`](#dikciz_shell) |
| [`shellSessionForget`](#dikciz_shell_session_forget) | [`dikciz_shell_session_forget`](#dikciz_shell_session_forget) |
| [`shellSessionRead`](#dikciz_shell_session_read) | [`dikciz_shell_session_read`](#dikciz_shell_session_read) |
| [`shellSessionStart`](#dikciz_shell_session_start) | [`dikciz_shell_session_start`](#dikciz_shell_session_start) |
| [`shellSessionStop`](#dikciz_shell_session_stop) | [`dikciz_shell_session_stop`](#dikciz_shell_session_stop) |
| [`shellSessionsList`](#dikciz_shell_sessions_list) | [`dikciz_shell_sessions_list`](#dikciz_shell_sessions_list) |
| [`shellStream`](#dikciz_shell_stream) | [`dikciz_shell_stream`](#dikciz_shell_stream) |
| [`shellStreamStop`](#dikciz_shell_stream_stop) | [`dikciz_shell_stream_stop`](#dikciz_shell_stream_stop) |
| [`simList`](#dikciz_sim_list) | [`dikciz_sim_list`](#dikciz_sim_list) |
| [`smsSend`](#dikciz_sms_send) | [`dikciz_sms_send`](#dikciz_sms_send) |
| [`smsStatus`](#dikciz_sms_status) | [`dikciz_sms_status`](#dikciz_sms_status) |
| [`snapshot`](#dikciz_snapshot) | [`dikciz_snapshot`](#dikciz_snapshot) |
| [`sosControl`](#dikciz_sos_control) | [`dikciz_sos_control`](#dikciz_sos_control) |
| [`sosPlanSave`](#dikciz_sos_plan_save) | [`dikciz_sos_plan_save`](#dikciz_sos_plan_save) |
| [`sosPreview`](#dikciz_sos_preview) | [`dikciz_sos_preview`](#dikciz_sos_preview) |
| [`sosStatus`](#dikciz_sos_status) | [`dikciz_sos_status`](#dikciz_sos_status) |
| [`speechLanguages`](#dikciz_speech_languages) | [`dikciz_speech_languages`](#dikciz_speech_languages) |
| [`speechModelDownload`](#dikciz_speech_model_download) | [`dikciz_speech_model_download`](#dikciz_speech_model_download) |
| [`speechModelStatus`](#dikciz_speech_model_status) | [`dikciz_speech_model_status`](#dikciz_speech_model_status) |
| [`speechRecognitionStatus`](#dikciz_speech_recognition_status) | [`dikciz_speech_recognition_status`](#dikciz_speech_recognition_status) |
| [`speechRecognitionStop`](#dikciz_speech_recognition_stop) | [`dikciz_speech_recognition_stop`](#dikciz_speech_recognition_stop) |
| [`speechRecognize`](#dikciz_speech_recognize) | [`dikciz_speech_recognize`](#dikciz_speech_recognize) |
| [`speechSpeak`](#dikciz_speech_speak) | [`dikciz_speech_speak`](#dikciz_speech_speak) |
| [`speechStop`](#dikciz_speech_stop) | [`dikciz_speech_stop`](#dikciz_speech_stop) |
| [`speechStreamForget`](#dikciz_speech_stream_forget) | [`dikciz_speech_stream_forget`](#dikciz_speech_stream_forget) |
| [`speechStreamRead`](#dikciz_speech_stream_read) | [`dikciz_speech_stream_read`](#dikciz_speech_stream_read) |
| [`speechStreamStart`](#dikciz_speech_stream_start) | [`dikciz_speech_stream_start`](#dikciz_speech_stream_start) |
| [`speechStreamStop`](#dikciz_speech_stream_stop) | [`dikciz_speech_stream_stop`](#dikciz_speech_stream_stop) |
| [`speechStreamsList`](#dikciz_speech_streams_list) | [`dikciz_speech_streams_list`](#dikciz_speech_streams_list) |
| [`speechSynthesize`](#dikciz_speech_synthesize) | [`dikciz_speech_synthesize`](#dikciz_speech_synthesize) |
| [`speechTranscribeFile`](#dikciz_speech_transcribe_file) | [`dikciz_speech_transcribe_file`](#dikciz_speech_transcribe_file) |
| [`speechVoices`](#dikciz_speech_voices) | [`dikciz_speech_voices`](#dikciz_speech_voices) |
| [`systemSetting`](#dikciz_system_setting) | [`dikciz_system_setting`](#dikciz_system_setting) |
| [`tap`](#dikciz_tap) | [`dikciz_tap`](#dikciz_tap) |
| [`torchControl`](#dikciz_torch_control) | [`dikciz_torch_control`](#dikciz_torch_control) |
| [`uiDump`](#dikciz_ui_dump) | [`dikciz_ui_dump`](#dikciz_ui_dump) |
| [`usbDevices`](#dikciz_usb_devices) | [`dikciz_usb_devices`](#dikciz_usb_devices) |
| [`usbRequestAccess`](#dikciz_usb_request_access) | [`dikciz_usb_request_access`](#dikciz_usb_request_access) |
| [`usbTransfer`](#dikciz_usb_transfer) | [`dikciz_usb_transfer`](#dikciz_usb_transfer) |
| [`ussdRequest`](#dikciz_ussd_request) | [`dikciz_ussd_request`](#dikciz_ussd_request) |
| [`vibrationControl`](#dikciz_vibration_control) | [`dikciz_vibration_control`](#dikciz_vibration_control) |
| [`waitFor`](#dikciz_wait_for) | [`dikciz_wait_for`](#dikciz_wait_for) |
| [`widgetGet`](#dikciz_widget_get) | [`dikciz_widget_get`](#dikciz_widget_get) |
| [`widgetMove`](#dikciz_widget_move) | [`dikciz_widget_move`](#dikciz_widget_move) |
| [`widgetResize`](#dikciz_widget_resize) | [`dikciz_widget_resize`](#dikciz_widget_resize) |
| [`wifiConnectionStart`](#dikciz_wifi_connection_start) | [`dikciz_wifi_connection_start`](#dikciz_wifi_connection_start) |
| [`wifiConnectionStatus`](#dikciz_wifi_connection_status) | [`dikciz_wifi_connection_status`](#dikciz_wifi_connection_status) |
| [`wifiConnectionStop`](#dikciz_wifi_connection_stop) | [`dikciz_wifi_connection_stop`](#dikciz_wifi_connection_stop) |
| [`wifiConnectionsList`](#dikciz_wifi_connections_list) | [`dikciz_wifi_connections_list`](#dikciz_wifi_connections_list) |
| [`wifiHotspotStart`](#dikciz_wifi_hotspot_start) | [`dikciz_wifi_hotspot_start`](#dikciz_wifi_hotspot_start) |
| [`wifiHotspotStatus`](#dikciz_wifi_hotspot_status) | [`dikciz_wifi_hotspot_status`](#dikciz_wifi_hotspot_status) |
| [`wifiHotspotStop`](#dikciz_wifi_hotspot_stop) | [`dikciz_wifi_hotspot_stop`](#dikciz_wifi_hotspot_stop) |
| [`wifiNetworksConfigure`](#dikciz_wifi_networks_configure) | [`dikciz_wifi_networks_configure`](#dikciz_wifi_networks_configure) |
| [`wifiScan`](#dikciz_wifi_scan) | [`dikciz_wifi_scan`](#dikciz_wifi_scan) |
| [`wifiScanResults`](#dikciz_wifi_scan_results) | [`dikciz_wifi_scan_results`](#dikciz_wifi_scan_results) |

## dikciz_accessibility_action

Run one supported action on a node from an unexpired accessibility snapshot.

Ordinary command: `accessibilityAction`. [Token scope definitions](token-permissions.md#accessibilityaction). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("accessibilityAction", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "accessibilityAction"}))`. Lua: set `request.type = "accessibilityAction"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | Supported node action: click, longClick, focus, clearFocus, scrollForward, scrollBackward, setText, or select. {"enum": ["click", "longClick", "focus", "clearFocus", "scrollForward", "scrollBackward", "setText", "select"], "maximum": 32} |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `nodeId` | Yes | `string` | `Not declared` | Node ID from the same accessibility snapshot. {"maximum": 128} |
| `snapshotId` | Yes | `string` | `Not declared` | Opaque snapshot ID returned by dikciz_accessibility_snapshot. {"maximum": 36} |
| `text` | No | `string` | `Not declared` | Required only for setText. Omit for every other action. {"maximum": 8192} |



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
      "maximum": 32,
      "enum": [
        "click",
        "longClick",
        "focus",
        "clearFocus",
        "scrollForward",
        "scrollBackward",
        "setText",
        "select"
      ]
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

Ordinary command: `accessibilitySnapshot`. [Token scope definitions](token-permissions.md#accessibilitysnapshot). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("accessibilitySnapshot", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "accessibilitySnapshot"}))`. Lua: set `request.type = "accessibilitySnapshot"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Request best-effort thread interruption of one running activity. Does not roll back completed effects or prove Android stopped work. Poll activityRead. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `activityCancel`. [Token scope definitions](token-permissions.md#activitycancel). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("activityCancel", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "activityCancel"}))`. Lua: set `request.type = "activityCancel"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `activityId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Read persistent command metadata without arguments or payloads. Requires the matching explicit operation grant for scoped remote clients. Includes actor, request ID, duration and completed/rejected/failed/interrupted/running state. Interrupted outcomes may be unknown. Seven-day retention and bounded pagination.

Ordinary command: `activityRead`. [Token scope definitions](token-permissions.md#activityread). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("activityRead", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "activityRead"}))`. Lua: set `request.type = "activityRead"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `actor` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 80} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `mutatingOnly` | No | `boolean` | `false` | See the complete schema below. |
| `offset` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |
| `outcome` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 32} |



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

Ordinary command: `addWidget`. [Token scope definitions](token-permissions.md#addwidget). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("addWidget", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "addWidget"}))`. Lua: set `request.type = "addWidget"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `widgetType` | Yes | `string` | `Not declared` | Item type to create: html, appGroup, or scriptDashboard. {"maximum": 256} |



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

Ordinary command: `androidApiCallback`. [Token scope definitions](token-permissions.md#androidapicallback). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("androidApiCallback", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "androidApiCallback"}))`. Lua: set `request.type = "androidApiCallback"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `arguments` | No | `array` | `Not declared` | Required ordered arguments, including explicit nulls. Use {objectRef:ID}, {className:SDK_CLASS}, {bytesBase64:BASE64}, {long:DECIMAL} or {arrayType:SDK_TYPE,values:[...]}. No inferred overloads or nested calls. {"maxItems": 64} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `className` | Yes | `string` | `Not declared` | Exact public className from androidApiDiscover. {"maxLength": 256} |
| `constructor` | No | `string` | `Not declared` | Exact SDK constructor id, defaults to new(). {"maxLength": 2048} |
| `returns` | No | `object` | `Not declared` | See the complete schema below. |



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

Ordinary command: `androidApiDiscover`. [Token scope definitions](token-permissions.md#androidapidiscover). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("androidApiDiscover", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "androidApiDiscover"}))`. Lua: set `request.type = "androidApiDiscover"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `className` | No | `string` | `Not declared` | Optional exact class, otherwise list classes. {"maxLength": 256} |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |
| `query` | No | `string` | `Not declared` | Case-insensitive literal class/member filter. {"maxLength": 256} |



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

Ordinary command: `androidApiInvoke`. [Token scope definitions](token-permissions.md#androidapiinvoke). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("androidApiInvoke", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "androidApiInvoke"}))`. Lua: set `request.type = "androidApiInvoke"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `arguments` | Yes | `array` | `Not declared` | Required ordered arguments, including explicit nulls. Use {objectRef:ID}, {className:SDK_CLASS}, {bytesBase64:BASE64}, {long:DECIMAL} or {arrayType:SDK_TYPE,values:[...]}. No inferred overloads or nested calls. {"maxItems": 64} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `className` | Yes | `string` | `Not declared` | Exact public className from androidApiDiscover. {"maxLength": 256} |
| `idempotencyKey` | Yes | `string` | `Not declared` | Unique process-scoped key. Same payload returns its original result; changed payload fails. For durable submission use jobStart. {"maxLength": 80} |
| `member` | Yes | `string` | `Not declared` | Exact discovered overload id, including parameter types. Constructors use new(...); fields use field:NAME. {"maxLength": 2048} |
| `target` | No | `string` | `Not declared` | Omit for constructors and static members. Otherwise context, activity, service:NAME or an objectRef from a previous result. {"maxLength": 256} |
| `thread` | No | `string` | `Not declared` | auto, main or background. Auto follows SDK MainThread/UiThread annotations. {"maxLength": 16} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 300000} |



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

Ordinary command: `androidApiObjects`. [Token scope definitions](token-permissions.md#androidapiobjects). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("androidApiObjects", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "androidApiObjects"}))`. Lua: set `request.type = "androidApiObjects"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `afterSequence` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 9007199254740991} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `objectRef` | No | `string` | `Not declared` | Optional callback handle. {"maxLength": 256} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `androidApiRead`. [Token scope definitions](token-permissions.md#androidapiread). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("androidApiRead", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "androidApiRead"}))`. Lua: set `request.type = "androidApiRead"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `arguments` | Yes | `array` | `Not declared` | Required ordered arguments, including explicit nulls. Use {objectRef:ID}, {className:SDK_CLASS}, {bytesBase64:BASE64}, {long:DECIMAL} or {arrayType:SDK_TYPE,values:[...]}. No inferred overloads or nested calls. {"maxItems": 64} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `className` | Yes | `string` | `Not declared` | Exact public className from androidApiDiscover. {"maxLength": 256} |
| `member` | Yes | `string` | `Not declared` | Exact discovered overload id, including parameter types. Constructors use new(...); fields use field:NAME. {"maxLength": 2048} |
| `target` | No | `string` | `Not declared` | Omit for constructors and static members. Otherwise context, activity, service:NAME or an objectRef from a previous result. {"maxLength": 256} |
| `thread` | No | `string` | `Not declared` | auto, main or background. Auto follows SDK MainThread/UiThread annotations. {"maxLength": 16} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 300000} |



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

Ordinary command: `androidApiRelease`. [Token scope definitions](token-permissions.md#androidapirelease). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("androidApiRelease", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "androidApiRelease"}))`. Lua: set `request.type = "androidApiRelease"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `close` | No | `boolean` | `false` | See the complete schema below. |
| `objectRef` | Yes | `string` | `Not declared` | Object handle. {"maxLength": 256} |



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

Ordinary command: `apkInstall`. [Token scope definitions](token-permissions.md#apkinstall). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("apkInstall", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "apkInstall"}))`. Lua: set `request.type = "apkInstall"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `path` | Yes | `string` | `Not declared` | Absolute filesystem path, using Android permissions granted to Dikciz. |
| `sha256` | No | `string` | `Not declared` | Optional expected SHA-256 of the complete APK, exactly 64 hex digits. |



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

Ordinary command: `apkInstallCancel`. [Token scope definitions](token-permissions.md#apkinstallcancel). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("apkInstallCancel", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "apkInstallCancel"}))`. Lua: set `request.type = "apkInstallCancel"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `sessionId` | Yes | `integer` | `Not declared` | See the complete schema below. {"minimum": 1, "maximum": 2147483647} |



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

Ordinary command: `apkInstallSet`. [Token scope definitions](token-permissions.md#apkinstallset). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("apkInstallSet", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "apkInstallSet"}))`. Lua: set `request.type = "apkInstallSet"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `apks` | Yes | `array` | `Not declared` | See the complete schema below. {"minItems": 1, "maxItems": 32} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `apkInstallStatus`. [Token scope definitions](token-permissions.md#apkinstallstatus). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("apkInstallStatus", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "apkInstallStatus"}))`. Lua: set `request.type = "apkInstallStatus"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `sessionId` | Yes | `integer` | `Not declared` | Install session ID returned by dikciz_apk_install. |



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

Ordinary command: `appAction`. [Token scope definitions](token-permissions.md#appaction). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("appAction", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "appAction"}))`. Lua: set `request.type = "appAction"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | One of launch, addShortcut, appInfo, uninstall, forceStop. {"enum": ["launch", "addShortcut", "appInfo", "uninstall", "forceStop"], "maximum": 32} |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `component` | Yes | `string` | `Not declared` | Flattened Android component from dikciz_app_catalogue, such as com.example/com.example.MainActivity. {"maximum": 512} |



```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "description": "One of launch, addShortcut, appInfo, uninstall, forceStop.",
      "maximum": 32,
      "enum": [
        "launch",
        "addShortcut",
        "appInfo",
        "uninstall",
        "forceStop"
      ]
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

Ordinary command: `appCatalogue`. [Token scope definitions](token-permissions.md#appcatalogue). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("appCatalogue", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "appCatalogue"}))`. Lua: set `request.type = "appCatalogue"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `query` | No | `string` | `Not declared` | Case-insensitive filter matched against the app label and its package name. {"maximum": 256} |



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

Ordinary command: `appIcon`. [Token scope definitions](token-permissions.md#appicon). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("appIcon", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "appIcon"}))`. Lua: set `request.type = "appIcon"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `component` | Yes | `string` | `Not declared` | Exact launchable component from appCatalogue. {"maxLength": 512} |
| `sizePixels` | No | `integer` | `64` | See the complete schema below. {"minimum": 16, "maximum": 128} |



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

Ordinary command: `assistantRoutinePreview`. [Token scope definitions](token-permissions.md#assistantroutinepreview). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("assistantRoutinePreview", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "assistantRoutinePreview"}))`. Lua: set `request.type = "assistantRoutinePreview"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `routineId` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 32} |



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

Ordinary command: `assistantRoutineRun`. [Token scope definitions](token-permissions.md#assistantroutinerun). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("assistantRoutineRun", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "assistantRoutineRun"}))`. Lua: set `request.type = "assistantRoutineRun"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `conversationId` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 80} |
| `requestKey` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 80} |
| `routineId` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 32} |



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

Ordinary command: `assistantRoutineSubscribe`. [Token scope definitions](token-permissions.md#assistantroutinesubscribe). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("assistantRoutineSubscribe", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "assistantRoutineSubscribe"}))`. Lua: set `request.type = "assistantRoutineSubscribe"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `conversationId` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 80} |
| `routineId` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 32} |
| `ruleId` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 80} |
| `subscription` | Yes | `object` | `Not declared` | See the complete schema below. |



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

Ordinary command: `assistantRoutines`. [Token scope definitions](token-permissions.md#assistantroutines). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("assistantRoutines", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "assistantRoutines"}))`. Lua: set `request.type = "assistantRoutines"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `audioDevices`. [Token scope definitions](token-permissions.md#audiodevices). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("audioDevices", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "audioDevices"}))`. Lua: set `request.type = "audioDevices"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `audioPlayback`. [Token scope definitions](token-permissions.md#audioplayback). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("audioPlayback", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "audioPlayback"}))`. Lua: set `request.type = "audioPlayback"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["play", "status", "pause", "resume", "seek", "volume", "stop"], "maxLength": 16} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `loop` | No | `boolean` | `false` | See the complete schema below. |
| `maximumDurationMilliseconds` | No | `integer` | `3600000` | See the complete schema below. {"minimum": 1, "maximum": 86400000} |
| `outputDeviceId` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 2147483647} |
| `path` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 4096} |
| `positionMilliseconds` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 86400000} |
| `sessionId` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |
| `volumePercent` | No | `integer` | `100` | See the complete schema below. {"minimum": 0, "maximum": 100} |



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

Ordinary command: `automationDispatch`. [Token scope definitions](token-permissions.md#automationdispatch). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("automationDispatch", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "automationDispatch"}))`. Lua: set `request.type = "automationDispatch"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `actions` | Yes | `array` | `Not declared` | Non-empty typed action array shared by Lua and HTML widgets. |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `sourceWidgetAddress` | Yes | `string` | `Not declared` | Stable source HTML widget address in 1H2V-widget-id form. {"maximum": 256} |



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

Delete one saved visual rule, its Lua script and owned subscription. In-flight work may finish. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `automationRuleDelete`. [Token scope definitions](token-permissions.md#automationruledelete). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("automationRuleDelete", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "automationRuleDelete"}))`. Lua: set `request.type = "automationRuleDelete"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `id` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 80} |



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

Compile a visual rule into Lua and simulate it with the optional sample payload. Returns proposed commands without touching Android. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `automationRulePreview`. [Token scope definitions](token-permissions.md#automationrulepreview). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("automationRulePreview", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "automationRulePreview"}))`. Lua: set `request.type = "automationRulePreview"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `rule` | Yes | `object` | `Not declared` | See the complete schema below. |
| `sample` | No | `object` | `Not declared` | See the complete schema below. |



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

Validate and save a visual automation rule, Lua source and least-source-capability subscription. Disabled by default. Explicit enabled=true activates future events after reload. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `automationRuleSave`. [Token scope definitions](token-permissions.md#automationrulesave). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("automationRuleSave", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "automationRuleSave"}))`. Lua: set `request.type = "automationRuleSave"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `rule` | Yes | `object` | `Not declared` | See the complete schema below. |



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

Ordinary command: `automationRulesList`. [Token scope definitions](token-permissions.md#automationruleslist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("automationRulesList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "automationRulesList"}))`. Lua: set `request.type = "automationRulesList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `automationServiceSync`. [Token scope definitions](token-permissions.md#automationservicesync). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("automationServiceSync", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "automationServiceSync"}))`. Lua: set `request.type = "automationServiceSync"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Ordinary command: `automationStatus`. [Token scope definitions](token-permissions.md#automationstatus). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("automationStatus", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "automationStatus"}))`. Lua: set `request.type = "automationStatus"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Ordinary command: `automationTrigger`. [Token scope definitions](token-permissions.md#automationtrigger). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("automationTrigger", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "automationTrigger"}))`. Lua: set `request.type = "automationTrigger"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `scriptId` | Yes | `string` | `Not declared` | Stable configured Dikciz script ID with a manual subscription. {"maximum": 256} |



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

Create an AES-GCM encrypted portable home, widget-state, Lua, chat and provider-settings snapshot. Passphrase required. API keys excluded unless explicitly requested. Remote credentials and control ports always remain outside backups. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `backupCreate`. [Token scope definitions](token-permissions.md#backupcreate). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("backupCreate", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "backupCreate"}))`. Lua: set `request.type = "backupCreate"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `includeApiKeys` | No | `boolean` | `false` | See the complete schema below. |
| `passphrase` | Yes | `string` | `Not declared` | Backup encryption passphrase. Never log or put it in prompts. {"minLength": 12, "maxLength": 1024} |



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

Decrypt and validate an archive into protected staging without replacing current state. Returns counts, previewId and sha256 for explicit restore confirmation. Preview expires after 15 minutes. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `backupPreview`. [Token scope definitions](token-permissions.md#backuppreview). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("backupPreview", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "backupPreview"}))`. Lua: set `request.type = "backupPreview"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `passphrase` | Yes | `string` | `Not declared` | Backup encryption passphrase. Never log or put it in prompts. {"minLength": 12, "maxLength": 1024} |
| `path` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 4096} |



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

Ordinary command: `backupRestore`. [Token scope definitions](token-permissions.md#backuprestore). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("backupRestore", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "backupRestore"}))`. Lua: set `request.type = "backupRestore"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `activateAutomation` | No | `boolean` | `false` | See the complete schema below. |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `confirm` | Yes | `boolean` | `false` | See the complete schema below. |
| `previewId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |
| `sha256` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 64} |



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

List encrypted archive paths and metadata without reading contents. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `backupsList`. [Token scope definitions](token-permissions.md#backupslist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("backupsList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "backupsList"}))`. Lua: set `request.type = "backupsList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `bluetoothDevices`. [Token scope definitions](token-permissions.md#bluetoothdevices). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("bluetoothDevices", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "bluetoothDevices"}))`. Lua: set `request.type = "bluetoothDevices"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `bluetoothExchange`. [Token scope definitions](token-permissions.md#bluetoothexchange). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("bluetoothExchange", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "bluetoothExchange"}))`. Lua: set `request.type = "bluetoothExchange"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `address` | Yes | `string` | `Not declared` | Explicit Bluetooth MAC address. {"maxLength": 17} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `dataBase64` | No | `string` | `Not declared` | Optional Base64 output, at most 65536 decoded bytes. {"maxLength": 87384} |
| `readBytes` | No | `integer` | `4096` | See the complete schema below. {"minimum": 0, "maximum": 65536} |
| `secure` | No | `boolean` | `true` | See the complete schema below. |
| `serviceUuid` | Yes | `string` | `Not declared` | Canonical service UUID. {"maxLength": 36} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |



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

Ordinary command: `bluetoothGatt`. [Token scope definitions](token-permissions.md#bluetoothgatt). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("bluetoothGatt", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "bluetoothGatt"}))`. Lua: set `request.type = "bluetoothGatt"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | discover, read, write, readDescriptor, writeDescriptor or observe. {"enum": ["discover", "read", "write", "readDescriptor", "writeDescriptor", "observe"], "maxLength": 32} |
| `address` | Yes | `string` | `Not declared` | Explicit Bluetooth MAC address. {"maxLength": 17} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `characteristicInstanceId` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 65535} |
| `characteristicUuid` | No | `string` | `Not declared` | Canonical characteristic UUID. {"maxLength": 36} |
| `dataBase64` | No | `string` | `Not declared` | Base64 output, at most 512 decoded bytes. {"maxLength": 684} |
| `descriptorUuid` | No | `string` | `Not declared` | Canonical descriptor UUID. {"maxLength": 36} |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `maximumEvents` | No | `integer` | `1` | See the complete schema below. {"minimum": 1, "maximum": 100} |
| `mtu` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 23, "maximum": 517} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |
| `serviceInstanceId` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 65535} |
| `serviceUuid` | No | `string` | `Not declared` | Canonical service UUID. {"maxLength": 36} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |
| `writeType` | No | `string` | `Not declared` | Characteristic writes: default, noResponse or signed. {"maxLength": 16} |



```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "description": "discover, read, write, readDescriptor, writeDescriptor or observe.",
      "maxLength": 32,
      "enum": [
        "discover",
        "read",
        "write",
        "readDescriptor",
        "writeDescriptor",
        "observe"
      ]
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

Ordinary command: `bluetoothPair`. [Token scope definitions](token-permissions.md#bluetoothpair). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("bluetoothPair", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "bluetoothPair"}))`. Lua: set `request.type = "bluetoothPair"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `address` | Yes | `string` | `Not declared` | Explicit Bluetooth MAC address. {"maxLength": 17} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `bluetoothScan`. [Token scope definitions](token-permissions.md#bluetoothscan). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("bluetoothScan", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "bluetoothScan"}))`. Lua: set `request.type = "bluetoothScan"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `durationMilliseconds` | No | `integer` | `5000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |
| `maximumDevices` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `mode` | No | `string` | `Not declared` | ble (default) or classic. {"enum": ["ble", "classic"], "maxLength": 16} |



```json
{
  "type": "object",
  "properties": {
    "mode": {
      "type": "string",
      "description": "ble (default) or classic.",
      "maxLength": 16,
      "enum": [
        "ble",
        "classic"
      ]
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

Ordinary command: `callAudio`. [Token scope definitions](token-permissions.md#callaudio). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("callAudio", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "callAudio"}))`. Lua: set `request.type = "callAudio"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | status, record or speak. {"enum": ["status", "record", "speak"], "maxLength": 16} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `text` | No | `string` | `Not declared` | Requested call speech. {"maxLength": 2000} |



```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "description": "status, record or speak.",
      "maxLength": 16,
      "enum": [
        "status",
        "record",
        "speak"
      ]
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

Ordinary command: `callControl`. [Token scope definitions](token-permissions.md#callcontrol). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("callControl", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "callControl"}))`. Lua: set `request.type = "callControl"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | state, place, answer or end. {"enum": ["state", "place", "answer", "end"], "maxLength": 16} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `recipient` | No | `string` | `Not declared` | Dialable number for place only. Common formatting separators are normalized. {"maxLength": 64} |



```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "description": "state, place, answer or end.",
      "maxLength": 16,
      "enum": [
        "state",
        "place",
        "answer",
        "end"
      ]
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

Ordinary command: `cameraCapabilities`. [Token scope definitions](token-permissions.md#cameracapabilities). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("cameraCapabilities", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "cameraCapabilities"}))`. Lua: set `request.type = "cameraCapabilities"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `cameraId` | Yes | `string` | `Not declared` | Camera ID from cameraList. {"maxLength": 128} |



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

Ordinary command: `cameraCapture`. [Token scope definitions](token-permissions.md#cameracapture). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("cameraCapture", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "cameraCapture"}))`. Lua: set `request.type = "cameraCapture"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `autofocusMode` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 5} |
| `cameraId` | No | `string` | `Not declared` | Camera ID from cameraList; defaults to the first camera. {"maxLength": 128} |
| `exposureCompensation` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": -1000, "maximum": 1000} |
| `jpegOrientation` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 270} |
| `jpegQuality` | No | `integer` | `95` | See the complete schema below. {"minimum": 1, "maximum": 100} |
| `maxDimension` | No | `integer` | `1280` | See the complete schema below. {"minimum": 320, "maximum": 4096} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |
| `zoomPercent` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 100, "maximum": 10000} |



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

Ordinary command: `cameraList`. [Token scope definitions](token-permissions.md#cameralist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("cameraList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "cameraList"}))`. Lua: set `request.type = "cameraList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `cameraVideoRecord`. [Token scope definitions](token-permissions.md#cameravideorecord). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("cameraVideoRecord", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "cameraVideoRecord"}))`. Lua: set `request.type = "cameraVideoRecord"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `bitRate` | No | `integer` | `4000000` | See the complete schema below. {"minimum": 500000, "maximum": 20000000} |
| `cameraId` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 128} |
| `durationMilliseconds` | No | `integer` | `3000` | See the complete schema below. {"minimum": 1000, "maximum": 120000} |
| `frameRate` | No | `integer` | `30` | See the complete schema below. {"minimum": 5, "maximum": 60} |
| `maxDimension` | No | `integer` | `1280` | See the complete schema below. {"minimum": 320, "maximum": 1920} |
| `orientation` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 270} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 300000} |
| `withAudio` | No | `boolean` | `false` | See the complete schema below. |



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

Ordinary command: `chatControl`. [Token scope definitions](token-permissions.md#chatcontrol). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("chatControl", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "chatControl"}))`. Lua: set `request.type = "chatControl"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["status", "profiles", "profileSave", "models", "create", "list", "read", "rename", "settings", "configure", "delete", "send", "task", "stop", "subscribe", "show", "hide", "fullscreen"], "maxLength": 32} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `conversationId` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 80} |
| `fullScreen` | No | `boolean` | `Not declared` | See the complete schema below. |
| `limit` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |
| `profile` | No | `object` | `Not declared` | See the complete schema below. |
| `profileId` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 80} |
| `requestKey` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 80} |
| `rules` | No | `array` | `Not declared` | See the complete schema below. |
| `settings` | No | `object` | `Not declared` | See the complete schema below. |
| `task` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 32} |
| `text` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 32768} |
| `title` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 256} |



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
      "maxLength": 32,
      "enum": [
        "status",
        "profiles",
        "profileSave",
        "models",
        "create",
        "list",
        "read",
        "rename",
        "settings",
        "configure",
        "delete",
        "send",
        "task",
        "stop",
        "subscribe",
        "show",
        "hide",
        "fullscreen"
      ]
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

Ordinary command: `clipboardGet`. [Token scope definitions](token-permissions.md#clipboardget). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("clipboardGet", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "clipboardGet"}))`. Lua: set `request.type = "clipboardGet"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `clipboardSet`. [Token scope definitions](token-permissions.md#clipboardset). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("clipboardSet", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "clipboardSet"}))`. Lua: set `request.type = "clipboardSet"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `text` | Yes | `string` | `Not declared` | Plain text, including an empty string. {"maxLength": 4096} |



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

Ordinary command: `configGet`. [Token scope definitions](token-permissions.md#configget). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("configGet", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "configGet"}))`. Lua: set `request.type = "configGet"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Ordinary command: `configReplace`. [Token scope definitions](token-permissions.md#configreplace). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("configReplace", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "configReplace"}))`. Lua: set `request.type = "configReplace"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `config` | Yes | `object` | `Not declared` | Complete Dikciz configuration document. |



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

Ordinary command: `reset`. [Token scope definitions](token-permissions.md#reset). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("reset", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "reset"}))`. Lua: set `request.type = "reset"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Ordinary command: `configSeed`. [Token scope definitions](token-permissions.md#configseed). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("configSeed", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "configSeed"}))`. Lua: set `request.type = "configSeed"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `config` | Yes | `object` | `Not declared` | Complete Dikciz configuration document. |



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

Ordinary command: `contentBatch`. [Token scope definitions](token-permissions.md#contentbatch). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("contentBatch", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "contentBatch"}))`. Lua: set `request.type = "contentBatch"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `operations` | Yes | `array` | `Not declared` | See the complete schema below. {"minItems": 1, "maxItems": 32} |



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
            "maxLength": 64,
            "enum": [
              "rawContacts",
              "contactData",
              "calendarEvents",
              "calendarReminders"
            ]
          },
          "operation": {
            "type": "string",
            "description": "create, update or delete. Updates and deletes require one positive row id.",
            "maxLength": 16,
            "enum": [
              "create",
              "update",
              "delete"
            ]
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

Ordinary command: `contentQuery`. [Token scope definitions](token-permissions.md#contentquery). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("contentQuery", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "contentQuery"}))`. Lua: set `request.type = "contentQuery"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `columns` | No | `array` | `Not declared` | See the complete schema below. {"minItems": 1, "maxItems": 32} |
| `filter` | No | `object` | `Not declared` | Exact equality filters on allowlisted columns. Null matches SQL NULL. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |
| `query` | No | `string` | `Not declared` | Literal substring matched against the resource search columns. {"maxLength": 256} |
| `resource` | Yes | `string` | `Not declared` | Resource from nativeDataStatus.resources. {"enum": ["contacts", "phones", "emails", "rawContacts", "contactData", "calendars", "calendarEvents", "calendarInstances", "calendarReminders", "sms", "callLog", "images", "audio", "videos"], "maxLength": 64} |



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
      "maxLength": 64,
      "enum": [
        "contacts",
        "phones",
        "emails",
        "rawContacts",
        "contactData",
        "calendars",
        "calendarEvents",
        "calendarInstances",
        "calendarReminders",
        "sms",
        "callLog",
        "images",
        "audio",
        "videos"
      ]
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

Ordinary command: `contentWrite`. [Token scope definitions](token-permissions.md#contentwrite). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("contentWrite", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "contentWrite"}))`. Lua: set `request.type = "contentWrite"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `id` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 1, "maximum": 2147483647} |
| `operation` | Yes | `string` | `Not declared` | create, update or delete. Updates and deletes require one positive row id. {"enum": ["create", "update", "delete"], "maxLength": 16} |
| `resource` | Yes | `string` | `Not declared` | Resource from nativeDataStatus.resources. {"enum": ["rawContacts", "contactData", "calendarEvents", "calendarReminders"], "maxLength": 64} |
| `values` | No | `object` | `Not declared` | Allowlisted provider columns and primitive values. Unspecified columns remain unchanged. |



```json
{
  "type": "object",
  "properties": {
    "resource": {
      "type": "string",
      "description": "Resource from nativeDataStatus.resources.",
      "maxLength": 64,
      "enum": [
        "rawContacts",
        "contactData",
        "calendarEvents",
        "calendarReminders"
      ]
    },
    "operation": {
      "type": "string",
      "description": "create, update or delete. Updates and deletes require one positive row id.",
      "maxLength": 16,
      "enum": [
        "create",
        "update",
        "delete"
      ]
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

Ordinary command: `controlStatus`. [Token scope definitions](token-permissions.md#controlstatus). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("controlStatus", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "controlStatus"}))`. Lua: set `request.type = "controlStatus"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Ordinary command: `customAppAlert`. [Token scope definitions](token-permissions.md#customappalert). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("customAppAlert", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "customAppAlert"}))`. Lua: set `request.type = "customAppAlert"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["show", "close"], "maxLength": 96} |
| `announcement` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 2000} |
| `appId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 64, "pattern": "[A-Za-z][A-Za-z0-9_-]{0,63}"} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `cancelEvent` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 96, "pattern": "[A-Za-z][A-Za-z0-9_.-]{0,95}"} |
| `cancelLabel` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 80} |
| `detail` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 2000} |
| `durationMilliseconds` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 1000, "maximum": 3600000} |
| `episodeId` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 120} |
| `finalAnnouncement` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 2000} |
| `title` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 120} |



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

Ordinary command: `customAppControl`. [Token scope definitions](token-permissions.md#customappcontrol). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("customAppControl", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "customAppControl"}))`. Lua: set `request.type = "customAppControl"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["open", "enable", "disable", "remove", "export", "import"], "maxLength": 96} |
| `appId` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 64, "pattern": "[A-Za-z][A-Za-z0-9_-]{0,63}"} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `confirm` | No | `boolean` | `Not declared` | See the complete schema below. |
| `path` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 4096} |



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

Ordinary command: `customAppEvent`. [Token scope definitions](token-permissions.md#customappevent). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("customAppEvent", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "customAppEvent"}))`. Lua: set `request.type = "customAppEvent"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `appId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 64, "pattern": "[A-Za-z][A-Za-z0-9_-]{0,63}"} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `event` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 96, "pattern": "[A-Za-z][A-Za-z0-9_.-]{0,95}"} |
| `interruptRunning` | No | `boolean` | `Not declared` | Immediately refuse further mutating host calls from the running handler before queuing this event, for cancellation controls. |
| `payload` | No | `object` | `Not declared` | See the complete schema below. |



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

Ordinary command: `customAppInstall`. [Token scope definitions](token-permissions.md#customappinstall). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("customAppInstall", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "customAppInstall"}))`. Lua: set `request.type = "customAppInstall"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `backgroundSource` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 262144} |
| `confirm` | Yes | `boolean` | `Not declared` | See the complete schema below. |
| `css` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 262144} |
| `html` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 262144} |
| `javascript` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 262144} |
| `manifest` | Yes | `object` | `Not declared` | See the complete schema below. |



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

Read one app manifest, full HTML/CSS/JS, optional Lua/JS background source, state, run status and durable timers. Sources live under /sdcard/Dikciz/apps/<appId>. This read can contain private app data and requires an explicit operation grant for scoped remote clients.

Ordinary command: `customAppRead`. [Token scope definitions](token-permissions.md#customappread). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("customAppRead", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "customAppRead"}))`. Lua: set `request.type = "customAppRead"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `appId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 64, "pattern": "[A-Za-z][A-Za-z0-9_-]{0,63}"} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `customAppRuntime`. [Token scope definitions](token-permissions.md#customappruntime). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("customAppRuntime", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "customAppRuntime"}))`. Lua: set `request.type = "customAppRuntime"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `customAppSchedule`. [Token scope definitions](token-permissions.md#customappschedule). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("customAppSchedule", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "customAppSchedule"}))`. Lua: set `request.type = "customAppSchedule"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["schedule", "cancel", "cancelAll"], "maxLength": 96} |
| `appId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 64, "pattern": "[A-Za-z][A-Za-z0-9_-]{0,63}"} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `delayMilliseconds` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 1000, "maximum": 604800000} |
| `event` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 96, "pattern": "[A-Za-z][A-Za-z0-9_.-]{0,95}"} |
| `payload` | No | `object` | `Not declared` | See the complete schema below. |
| `timerId` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 64, "pattern": "[A-Za-z][A-Za-z0-9_-]{0,63}"} |



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

Ordinary command: `customAppState`. [Token scope definitions](token-permissions.md#customappstate). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("customAppState", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "customAppState"}))`. Lua: set `request.type = "customAppState"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["read", "replace"], "maxLength": 96} |
| `appId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 64, "pattern": "[A-Za-z][A-Za-z0-9_-]{0,63}"} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `state` | No | `object` | `Not declared` | See the complete schema below. |



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

Ordinary command: `customAppsList`. [Token scope definitions](token-permissions.md#customappslist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("customAppsList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "customAppsList"}))`. Lua: set `request.type = "customAppsList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `deviceConnectionClose`. [Token scope definitions](token-permissions.md#deviceconnectionclose). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("deviceConnectionClose", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "deviceConnectionClose"}))`. Lua: set `request.type = "deviceConnectionClose"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `id` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 80} |



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

Ordinary command: `deviceConnectionExchange`. [Token scope definitions](token-permissions.md#deviceconnectionexchange). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("deviceConnectionExchange", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "deviceConnectionExchange"}))`. Lua: set `request.type = "deviceConnectionExchange"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | No | `string` | `Not declared` | See the complete schema below. {"enum": ["control", "bulk", "interrupt", "transceive", "readNdef", "writeNdef", "discover", "read", "write", "subscribe", "unsubscribe", "notifications"], "minLength": 1, "maxLength": 32} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `characteristicInstanceId` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 2147483647} |
| `characteristicUuid` | No | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 40} |
| `cursor` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 9223372036854775807} |
| `dataBase64` | No | `string` | `Not declared` | See the complete schema below. {"minLength": 0, "maxLength": 87384} |
| `endpointAddress` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 255} |
| `id` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 80} |
| `index` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 65535} |
| `readBytes` | No | `integer` | `4096` | See the complete schema below. {"minimum": 0, "maximum": 65536} |
| `request` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 255} |
| `requestType` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 255} |
| `serviceInstanceId` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 2147483647} |
| `serviceUuid` | No | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 40} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |
| `value` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 65535} |



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
      "maxLength": 32,
      "enum": [
        "control",
        "bulk",
        "interrupt",
        "transceive",
        "readNdef",
        "writeNdef",
        "discover",
        "read",
        "write",
        "subscribe",
        "unsubscribe",
        "notifications"
      ]
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

Ordinary command: `deviceConnectionOpen`. [Token scope definitions](token-permissions.md#deviceconnectionopen). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("deviceConnectionOpen", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "deviceConnectionOpen"}))`. Lua: set `request.type = "deviceConnectionOpen"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `address` | No | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 32} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `deviceId` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 2147483647} |
| `interfaceIndex` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 255} |
| `kind` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["rfcomm", "gatt", "usb", "nfc"], "minLength": 1, "maxLength": 16} |
| `maximumDurationMilliseconds` | No | `integer` | `300000` | See the complete schema below. {"minimum": 1000, "maximum": 3600000} |
| `secure` | No | `boolean` | `Not declared` | See the complete schema below. |
| `serviceUuid` | No | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 40} |
| `technology` | No | `string` | `Not declared` | See the complete schema below. {"enum": ["IsoDep", "NfcA", "NfcB", "NfcF", "NfcV", "Ndef", "MifareClassic", "MifareUltralight"], "minLength": 1, "maxLength": 32} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |



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

Ordinary command: `deviceConnectionRead`. [Token scope definitions](token-permissions.md#deviceconnectionread). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("deviceConnectionRead", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "deviceConnectionRead"}))`. Lua: set `request.type = "deviceConnectionRead"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `id` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 80} |



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

Ordinary command: `deviceConnectionsList`. [Token scope definitions](token-permissions.md#deviceconnectionslist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("deviceConnectionsList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "deviceConnectionsList"}))`. Lua: set `request.type = "deviceConnectionsList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `deviceInfo`. [Token scope definitions](token-permissions.md#deviceinfo). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("deviceInfo", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "deviceInfo"}))`. Lua: set `request.type = "deviceInfo"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Ordinary command: `deviceState`. [Token scope definitions](token-permissions.md#devicestate). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("deviceState", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "deviceState"}))`. Lua: set `request.type = "deviceState"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `diagnostics`. [Token scope definitions](token-permissions.md#diagnostics). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("diagnostics", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "diagnostics"}))`. Lua: set `request.type = "diagnostics"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Ordinary command: `displayScreenshot`. [Token scope definitions](token-permissions.md#displayscreenshot). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("displayScreenshot", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "displayScreenshot"}))`. Lua: set `request.type = "displayScreenshot"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Ordinary command: `documentScan`. [Token scope definitions](token-permissions.md#documentscan). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("documentScan", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "documentScan"}))`. Lua: set `request.type = "documentScan"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `grayscale` | No | `boolean` | `true` | See the complete schema below. |
| `maxDimension` | No | `integer` | `2048` | See the complete schema below. {"minimum": 320, "maximum": 4096} |
| `pages` | Yes | `array` | `Not declared` | See the complete schema below. {"minItems": 1, "maxItems": 20} |



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

Persist a named consumer cursor after successful handling. Cursor must not move backwards or beyond the last dispatched event. Does not run actions. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `eventConsumerAck`. [Token scope definitions](token-permissions.md#eventconsumerack). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("eventConsumerAck", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "eventConsumerAck"}))`. Lua: set `request.type = "eventConsumerAck"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `cursor` | Yes | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 9007199254740991} |
| `name` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 80} |



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

Delete one consumer's acknowledgement. A subsequent read starts at zero and may report an expired history gap. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `eventConsumerForget`. [Token scope definitions](token-permissions.md#eventconsumerforget). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("eventConsumerForget", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "eventConsumerForget"}))`. Lua: set `request.type = "eventConsumerForget"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `name` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 80} |



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

Read durable events after a named consumer's last acknowledged cursor. Reading never acknowledges. Reconnects and process restarts retain the epoch and cursor. Inspect historyGap before continuing. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `eventConsumerRead`. [Token scope definitions](token-permissions.md#eventconsumerread). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("eventConsumerRead", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "eventConsumerRead"}))`. Lua: set `request.type = "eventConsumerRead"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `eventTypes` | No | `array` | `Not declared` | See the complete schema below. {"minItems": 1, "maxItems": 32} |
| `limit` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `name` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 80} |
| `waitMilliseconds` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 30000} |



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

List durable named event consumer cursors. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `eventConsumersList`. [Token scope definitions](token-permissions.md#eventconsumerslist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("eventConsumersList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "eventConsumersList"}))`. Lua: set `request.type = "eventConsumersList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `eventJournalConfigure`. [Token scope definitions](token-permissions.md#eventjournalconfigure). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("eventJournalConfigure", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "eventJournalConfigure"}))`. Lua: set `request.type = "eventJournalConfigure"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `bufferBytes` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 65536, "maximum": 33554432} |
| `maximumEvents` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 1, "maximum": 16384} |
| `retentionMinutes` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 1, "maximum": 1440} |



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

Ordinary command: `eventJournalRead`. [Token scope definitions](token-permissions.md#eventjournalread). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("eventJournalRead", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "eventJournalRead"}))`. Lua: set `request.type = "eventJournalRead"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `cursor` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 9007199254740991} |
| `eventTypes` | No | `array` | `Not declared` | See the complete schema below. {"minItems": 1, "maxItems": 32} |
| `journalId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `waitMilliseconds` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 30000} |



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

Ordinary command: `eventJournalStatus`. [Token scope definitions](token-permissions.md#eventjournalstatus). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("eventJournalStatus", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "eventJournalStatus"}))`. Lua: set `request.type = "eventJournalStatus"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `fileList`. [Token scope definitions](token-permissions.md#filelist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("fileList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "fileList"}))`. Lua: set `request.type = "fileList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `limit` | No | `integer` | `Not declared` | Maximum directory entries (1 to 500) or file bytes (1 to 262144). |
| `offset` | No | `integer` | `Not declared` | Nonnegative byte offset, or directory entry offset. Defaults to zero. |
| `path` | Yes | `string` | `Not declared` | Absolute filesystem path, using Android permissions granted to Dikciz. |



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

Ordinary command: `fileManage`. [Token scope definitions](token-permissions.md#filemanage). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("fileManage", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "fileManage"}))`. Lua: set `request.type = "fileManage"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | stat, copy, move, delete or search. {"enum": ["stat", "copy", "move", "delete", "search"], "maxLength": 16} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `destination` | No | `string` | `Not declared` | New absolute path, copy/move only. Existing destinations are never overwritten. {"maxLength": 4096} |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `maximumEntries` | No | `integer` | `10000` | See the complete schema below. {"minimum": 1, "maximum": 100000} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |
| `path` | Yes | `string` | `Not declared` | Absolute source file or directory path. {"maxLength": 4096} |
| `query` | No | `string` | `Not declared` | Literal case-insensitive name substring, search only. {"maxLength": 256} |
| `recursive` | No | `boolean` | `false` | See the complete schema below. |



```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "description": "stat, copy, move, delete or search.",
      "maxLength": 16,
      "enum": [
        "stat",
        "copy",
        "move",
        "delete",
        "search"
      ]
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

Ordinary command: `fileMkdir`. [Token scope definitions](token-permissions.md#filemkdir). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("fileMkdir", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "fileMkdir"}))`. Lua: set `request.type = "fileMkdir"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `parents` | No | `boolean` | `Not declared` | Create missing parent directories. Defaults to false. |
| `path` | Yes | `string` | `Not declared` | Absolute filesystem path, using Android permissions granted to Dikciz. |



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

Ordinary command: `fileRead`. [Token scope definitions](token-permissions.md#fileread). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("fileRead", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "fileRead"}))`. Lua: set `request.type = "fileRead"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `limit` | No | `integer` | `Not declared` | Maximum directory entries (1 to 500) or file bytes (1 to 262144). |
| `offset` | No | `integer` | `Not declared` | Nonnegative byte offset, or directory entry offset. Defaults to zero. |
| `path` | Yes | `string` | `Not declared` | Absolute filesystem path, using Android permissions granted to Dikciz. |



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

Create or explicitly overwrite a file at offset zero, or append a chunk only when offset equals its current size. Writing bytes does not directly execute them; changing watched script, app or configuration files can trigger reload and effects.

Ordinary command: `fileWrite`. [Token scope definitions](token-permissions.md#filewrite). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("fileWrite", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "fileWrite"}))`. Lua: set `request.type = "fileWrite"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `dataBase64` | Yes | `string` | `Not declared` | Strict base64 file bytes, at most 262144 decoded bytes per chunk. |
| `offset` | No | `integer` | `Not declared` | Nonnegative byte offset, or directory entry offset. Defaults to zero. |
| `overwrite` | No | `boolean` | `Not declared` | Allow replacing an existing file at offset zero. Defaults to false. |
| `path` | Yes | `string` | `Not declared` | Absolute filesystem path, using Android permissions granted to Dikciz. |



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

Ordinary command: `find`. [Token scope definitions](token-permissions.md#find). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("find", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "find"}))`. Lua: set `request.type = "find"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `semanticId` | Yes | `string` | `Not declared` | Stable Dikciz semantic ID from dikciz_snapshot. {"maximum": 256} |



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

Ordinary command: `geocode`. [Token scope definitions](token-permissions.md#geocode). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("geocode", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "geocode"}))`. Lua: set `request.type = "geocode"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | forward or reverse. {"enum": ["forward", "reverse"], "maxLength": 16} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `language` | No | `string` | `Not declared` | Optional BCP-47 language. {"maxLength": 64} |
| `latitude` | No | `number` | `Not declared` | See the complete schema below. {"minimum": -90, "maximum": 90} |
| `limit` | No | `integer` | `5` | See the complete schema below. {"minimum": 1, "maximum": 10} |
| `longitude` | No | `number` | `Not declared` | See the complete schema below. {"minimum": -180, "maximum": 180} |
| `query` | No | `string` | `Not declared` | Place/address for forward lookup. {"maxLength": 1024} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |



```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "description": "forward or reverse.",
      "maxLength": 16,
      "enum": [
        "forward",
        "reverse"
      ]
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

Ordinary command: `geofenceDelete`. [Token scope definitions](token-permissions.md#geofencedelete). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("geofenceDelete", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "geofenceDelete"}))`. Lua: set `request.type = "geofenceDelete"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `id` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 80} |



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

Ordinary command: `geofenceSave`. [Token scope definitions](token-permissions.md#geofencesave). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("geofenceSave", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "geofenceSave"}))`. Lua: set `request.type = "geofenceSave"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `dwellMilliseconds` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 3600000} |
| `enabled` | No | `boolean` | `Not declared` | See the complete schema below. |
| `id` | No | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 80} |
| `latitude` | Yes | `number` | `Not declared` | See the complete schema below. {"minimum": -90, "maximum": 90} |
| `longitude` | Yes | `number` | `Not declared` | See the complete schema below. {"minimum": -180, "maximum": 180} |
| `radiusMeters` | Yes | `number` | `Not declared` | See the complete schema below. {"minimum": 100, "maximum": 1000000} |
| `title` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 120} |



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

Ordinary command: `geofencesList`. [Token scope definitions](token-permissions.md#geofenceslist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("geofencesList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "geofencesList"}))`. Lua: set `request.type = "geofencesList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `gnssStatus`. [Token scope definitions](token-permissions.md#gnssstatus). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("gnssStatus", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "gnssStatus"}))`. Lua: set `request.type = "gnssStatus"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |
| `timeoutMilliseconds` | No | `integer` | `1000` | See the complete schema below. {"minimum": 1, "maximum": 5000} |



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

Ordinary command: `gridSet`. [Token scope definitions](token-permissions.md#gridset). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("gridSet", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "gridSet"}))`. Lua: set `request.type = "gridSet"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `columns` | Yes | `integer` | `Not declared` | Grid columns for every page. {"minimum": 1, "maximum": 12} |
| `gapDp` | Yes | `integer` | `Not declared` | Gap between cells in density pixels. {"minimum": 0, "maximum": 64} |
| `outerPaddingDp` | Yes | `integer` | `Not declared` | Padding between the page edge and the grid in density pixels. {"minimum": 0, "maximum": 64} |
| `rows` | Yes | `integer` | `Not declared` | Grid rows for every page. {"minimum": 1, "maximum": 12} |



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

Ordinary command: `healthPermissionsRequest`. [Token scope definitions](token-permissions.md#healthpermissionsrequest). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("healthPermissionsRequest", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "healthPermissionsRequest"}))`. Lua: set `request.type = "healthPermissionsRequest"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `access` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["read", "write", "readWrite"], "minLength": 1, "maxLength": 16} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `includeBackground` | No | `boolean` | `Not declared` | See the complete schema below. |
| `includeHistory` | No | `boolean` | `Not declared` | See the complete schema below. |
| `kinds` | Yes | `array` | `Not declared` | See the complete schema below. {"minItems": 1, "maxItems": 16} |



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

Ordinary command: `healthRecordsAggregate`. [Token scope definitions](token-permissions.md#healthrecordsaggregate). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("healthRecordsAggregate", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "healthRecordsAggregate"}))`. Lua: set `request.type = "healthRecordsAggregate"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `endTimeMilliseconds` | Yes | `integer` | `Not declared` | See the complete schema below. {"minimum": 1, "maximum": 9223372036854775807} |
| `kind` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["steps", "distance", "activeCalories", "totalCalories", "heartRate", "sleep", "exercise", "weight", "height", "hydration", "bodyFat", "bodyTemperature", "bloodPressure", "bloodGlucose", "oxygenSaturation", "floors"], "minLength": 1, "maxLength": 32} |
| `startTimeMilliseconds` | Yes | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 9223372036854775807} |



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

Ordinary command: `healthRecordsCatalogue`. [Token scope definitions](token-permissions.md#healthrecordscatalogue). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("healthRecordsCatalogue", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "healthRecordsCatalogue"}))`. Lua: set `request.type = "healthRecordsCatalogue"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `healthRecordsDelete`. [Token scope definitions](token-permissions.md#healthrecordsdelete). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("healthRecordsDelete", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "healthRecordsDelete"}))`. Lua: set `request.type = "healthRecordsDelete"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `confirm` | Yes | `boolean` | `Not declared` | See the complete schema below. |
| `kind` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["steps", "distance", "activeCalories", "totalCalories", "heartRate", "sleep", "exercise", "weight", "height", "hydration", "bodyFat", "bodyTemperature", "bloodPressure", "bloodGlucose", "oxygenSaturation", "floors"], "minLength": 1, "maxLength": 32} |
| `recordIds` | Yes | `array` | `Not declared` | See the complete schema below. {"minItems": 1, "maxItems": 50} |



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

Ordinary command: `healthRecordsRead`. [Token scope definitions](token-permissions.md#healthrecordsread). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("healthRecordsRead", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "healthRecordsRead"}))`. Lua: set `request.type = "healthRecordsRead"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `endTimeMilliseconds` | Yes | `integer` | `Not declared` | See the complete schema below. {"minimum": 1, "maximum": 9223372036854775807} |
| `kind` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["steps", "distance", "activeCalories", "totalCalories", "heartRate", "sleep", "exercise", "weight", "height", "hydration", "bodyFat", "bodyTemperature", "bloodPressure", "bloodGlucose", "oxygenSaturation", "floors"], "minLength": 1, "maxLength": 32} |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `pageToken` | No | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 4096} |
| `startTimeMilliseconds` | Yes | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 9223372036854775807} |



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

Ordinary command: `healthRecordsWrite`. [Token scope definitions](token-permissions.md#healthrecordswrite). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("healthRecordsWrite", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "healthRecordsWrite"}))`. Lua: set `request.type = "healthRecordsWrite"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `kind` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["steps", "distance", "activeCalories", "totalCalories", "heartRate", "sleep", "exercise", "weight", "height", "hydration", "bodyFat", "bodyTemperature", "bloodPressure", "bloodGlucose", "oxygenSaturation", "floors"], "minLength": 1, "maxLength": 32} |
| `records` | Yes | `array` | `Not declared` | See the complete schema below. {"minItems": 1, "maxItems": 50} |



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

Ordinary command: `homeGet`. [Token scope definitions](token-permissions.md#homeget). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("homeGet", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "homeGet"}))`. Lua: set `request.type = "homeGet"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Ordinary command: `htmlWidgetRendererCrash`. [Token scope definitions](token-permissions.md#htmlwidgetrenderercrash). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("htmlWidgetRendererCrash", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "htmlWidgetRendererCrash"}))`. Lua: set `request.type = "htmlWidgetRendererCrash"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `widgetAddress` | Yes | `string` | `Not declared` | Stable Dikciz widget address in 1H2V-widget-id form. {"maximum": 256} |



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

Ordinary command: `imageRecognize`. [Token scope definitions](token-permissions.md#imagerecognize). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("imageRecognize", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "imageRecognize"}))`. Lua: set `request.type = "imageRecognize"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `maxDimension` | No | `integer` | `2048` | See the complete schema below. {"minimum": 320, "maximum": 4096} |
| `maximumResults` | No | `integer` | `200` | See the complete schema below. {"minimum": 1, "maximum": 1000} |
| `maximumTextCharacters` | No | `integer` | `65536` | See the complete schema below. {"minimum": 1, "maximum": 262144} |
| `mode` | No | `string` | `Not declared` | See the complete schema below. {"enum": ["text", "barcodes", "both"], "minLength": 1, "maxLength": 16} |
| `path` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 4096} |
| `rotationDegrees` | No | `integer` | `0` | See the complete schema below. {"enum": [0, 90, 180, 270], "minimum": 0, "maximum": 270} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |



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

Ordinary command: `imageStreamForget`. [Token scope definitions](token-permissions.md#imagestreamforget). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("imageStreamForget", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "imageStreamForget"}))`. Lua: set `request.type = "imageStreamForget"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `streamId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |



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

Read complete bounded JPEG frames by exclusive cursor, with replay loss and actual terminal/hardware-cleanup state. No capture is started. Frames stay in process memory for five minutes after completion. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `imageStreamRead`. [Token scope definitions](token-permissions.md#imagestreamread). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("imageStreamRead", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "imageStreamRead"}))`. Lua: set `request.type = "imageStreamRead"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `cursor` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 9007199254740991} |
| `limit` | No | `integer` | `1` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `streamId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |
| `waitMilliseconds` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 30000} |



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

Start bounded camera JPEG preview or Accessibility display frames with an owner Stop notification. Requires source camera/display and foreground startup. A persistent camera session is reused; display follows Android screenshot limits and secure-window restrictions. Poll imageStreamRead for actual frames/errors. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `imageStreamStart`. [Token scope definitions](token-permissions.md#imagestreamstart). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("imageStreamStart", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "imageStreamStart"}))`. Lua: set `request.type = "imageStreamStart"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `bufferBytes` | No | `integer` | `1048576` | See the complete schema below. {"minimum": 262144, "maximum": 8388608} |
| `cameraId` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 128} |
| `frameIntervalMilliseconds` | No | `integer` | `1000` | See the complete schema below. {"minimum": 500, "maximum": 10000} |
| `maxDimension` | No | `integer` | `640` | See the complete schema below. {"minimum": 320, "maximum": 1280} |
| `maximumDurationMilliseconds` | No | `integer` | `60000` | See the complete schema below. {"minimum": 1000, "maximum": 3600000} |
| `maximumEvents` | No | `integer` | `16` | See the complete schema below. {"minimum": 1, "maximum": 64} |
| `source` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["camera", "display"], "maxLength": 16} |
| `startupTimeoutMilliseconds` | No | `integer` | `10000` | See the complete schema below. {"minimum": 1000, "maximum": 30000} |



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

Ordinary command: `imageStreamStop`. [Token scope definitions](token-permissions.md#imagestreamstop). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("imageStreamStop", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "imageStreamStop"}))`. Lua: set `request.type = "imageStreamStop"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `streamId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |



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

Ordinary command: `imageStreamsList`. [Token scope definitions](token-permissions.md#imagestreamslist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("imageStreamsList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "imageStreamsList"}))`. Lua: set `request.type = "imageStreamsList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `intent`. [Token scope definitions](token-permissions.md#intent). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("intent", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "intent"}))`. Lua: set `request.type = "intent"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | Android intent action. {"maximum": 2048} |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `data` | No | `string` | `Not declared` | Optional absolute Android intent data URI, such as an HTTPS browser destination. {"maximum": 2048} |
| `intentType` | Yes | `string` | `Not declared` | Intent dispatch mode: activity or broadcast. {"maximum": 32} |



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

Ordinary command: `jobCancel`. [Token scope definitions](token-permissions.md#jobcancel). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("jobCancel", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "jobCancel"}))`. Lua: set `request.type = "jobCancel"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `jobId` | Yes | `string` | `Not declared` | Job ID from jobStart. {"maxLength": 36} |



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

Ordinary command: `jobStart`. [Token scope definitions](token-permissions.md#jobstart). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("jobStart", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "jobStart"}))`. Lua: set `request.type = "jobStart"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `command` | Yes | `object` | `Not declared` | Full ordinary command with type and its fields, optionally autoRequestPermissions. |
| `idempotencyKey` | Yes | `string` | `Not declared` | Unique caller key; changed content with the same key conflicts. {"maxLength": 80} |
| `retentionHours` | No | `integer` | `168` | See the complete schema below. {"minimum": 1, "maximum": 720} |



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

Ordinary command: `jobStatus`. [Token scope definitions](token-permissions.md#jobstatus). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("jobStatus", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "jobStatus"}))`. Lua: set `request.type = "jobStatus"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `jobId` | Yes | `string` | `Not declared` | Job ID from jobStart. {"maxLength": 36} |



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

Ordinary command: `jobsList`. [Token scope definitions](token-permissions.md#jobslist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("jobsList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "jobsList"}))`. Lua: set `request.type = "jobsList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `journeyControl`. [Token scope definitions](token-permissions.md#journeycontrol). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("journeyControl", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "journeyControl"}))`. Lua: set `request.type = "journeyControl"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["start", "stop"], "minLength": 1, "maxLength": 16} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `id` | No | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 80} |
| `sampleIntervalMilliseconds` | No | `integer` | `10000` | See the complete schema below. {"minimum": 1000, "maximum": 60000} |
| `title` | No | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 120} |



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

Ordinary command: `journeyDelete`. [Token scope definitions](token-permissions.md#journeydelete). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("journeyDelete", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "journeyDelete"}))`. Lua: set `request.type = "journeyDelete"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `confirm` | Yes | `boolean` | `Not declared` | See the complete schema below. |
| `id` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 80} |



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

Ordinary command: `journeyRead`. [Token scope definitions](token-permissions.md#journeyread). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("journeyRead", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "journeyRead"}))`. Lua: set `request.type = "journeyRead"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `id` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 80} |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `journeysList`. [Token scope definitions](token-permissions.md#journeyslist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("journeysList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "journeysList"}))`. Lua: set `request.type = "journeysList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `launchApp`. [Token scope definitions](token-permissions.md#launchapp). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("launchApp", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "launchApp"}))`. Lua: set `request.type = "launchApp"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `semanticId` | Yes | `string` | `Not declared` | Stable Dikciz semantic ID from dikciz_snapshot. {"maximum": 256} |



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

Ordinary command: `launcherHandoff`. [Token scope definitions](token-permissions.md#launcherhandoff). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("launcherHandoff", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "launcherHandoff"}))`. Lua: set `request.type = "launcherHandoff"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `packageName` | Yes | `string` | `Not declared` | Installed Android package name. {"maxLength": 255} |



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

Ordinary command: `locationGet`. [Token scope definitions](token-permissions.md#locationget). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("locationGet", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "locationGet"}))`. Lua: set `request.type = "locationGet"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `maxAgeMilliseconds` | No | `integer` | `300000` | See the complete schema below. {"minimum": 0, "maximum": 2147483647} |
| `timeoutMilliseconds` | No | `integer` | `10000` | See the complete schema below. {"minimum": 0, "maximum": 120000} |



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

Ordinary command: `longPress`. [Token scope definitions](token-permissions.md#longpress). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("longPress", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "longPress"}))`. Lua: set `request.type = "longPress"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `semanticId` | Yes | `string` | `Not declared` | Stable Dikciz semantic ID from dikciz_snapshot. {"maximum": 256} |



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

Ordinary command: `mediaSessionsList`. [Token scope definitions](token-permissions.md#mediasessionslist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("mediaSessionsList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "mediaSessionsList"}))`. Lua: set `request.type = "mediaSessionsList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `microphoneRecord`. [Token scope definitions](token-permissions.md#microphonerecord). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("microphoneRecord", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "microphoneRecord"}))`. Lua: set `request.type = "microphoneRecord"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `durationMilliseconds` | No | `integer` | `3000` | See the complete schema below. {"minimum": 1000, "maximum": 120000} |



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

Ordinary command: `microphoneSession`. [Token scope definitions](token-permissions.md#microphonesession). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("microphoneSession", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "microphoneSession"}))`. Lua: set `request.type = "microphoneSession"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["start", "status", "stop", "cancel"], "maxLength": 16} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `bitRate` | No | `integer` | `128000` | See the complete schema below. {"minimum": 8000, "maximum": 320000} |
| `channels` | No | `integer` | `1` | See the complete schema below. {"minimum": 1, "maximum": 2} |
| `inputDeviceId` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 2147483647} |
| `maximumBytes` | No | `integer` | `67108864` | See the complete schema below. {"minimum": 4096, "maximum": 1073741824} |
| `maximumDurationMilliseconds` | No | `integer` | `60000` | See the complete schema below. {"minimum": 1, "maximum": 86400000} |
| `sampleRateHz` | No | `integer` | `44100` | See the complete schema below. {"enum": [8000, 16000, 22050, 44100, 48000]} |
| `sessionId` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |



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

Ordinary command: `microphoneStreamForget`. [Token scope definitions](token-permissions.md#microphonestreamforget). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("microphoneStreamForget", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "microphoneStreamForget"}))`. Lua: set `request.type = "microphoneStreamForget"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `streamId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |



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

Ordinary command: `microphoneStreamRead`. [Token scope definitions](token-permissions.md#microphonestreamread). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("microphoneStreamRead", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "microphoneStreamRead"}))`. Lua: set `request.type = "microphoneStreamRead"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `cursor` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 9007199254740991} |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `streamId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |
| `waitMilliseconds` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 30000} |



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

Ordinary command: `microphoneStreamStart`. [Token scope definitions](token-permissions.md#microphonestreamstart). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("microphoneStreamStart", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "microphoneStreamStart"}))`. Lua: set `request.type = "microphoneStreamStart"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `bufferBytes` | No | `integer` | `2097152` | See the complete schema below. {"minimum": 65536, "maximum": 8388608} |
| `channels` | No | `integer` | `1` | See the complete schema below. {"minimum": 1, "maximum": 2} |
| `chunkMilliseconds` | No | `integer` | `100` | See the complete schema below. {"minimum": 20, "maximum": 1000} |
| `inputDeviceId` | No | `integer` | `Not declared` | Preferred audio input ID from audioDevices; inspect the actual routed input. {"minimum": 0, "maximum": 2147483647} |
| `maximumDurationMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 100, "maximum": 2147483647} |
| `maximumEvents` | No | `integer` | `1024` | See the complete schema below. {"minimum": 1, "maximum": 4096} |
| `retentionMinutes` | No | `integer` | `60` | See the complete schema below. {"minimum": 1, "maximum": 1440} |
| `sampleRateHz` | No | `integer` | `16000` | See the complete schema below. {"minimum": 4000, "maximum": 192000} |
| `startupTimeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |



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

Ordinary command: `microphoneStreamStop`. [Token scope definitions](token-permissions.md#microphonestreamstop). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("microphoneStreamStop", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "microphoneStreamStop"}))`. Lua: set `request.type = "microphoneStreamStop"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `streamId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |



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

Ordinary command: `microphoneStreamsList`. [Token scope definitions](token-permissions.md#microphonestreamslist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("microphoneStreamsList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "microphoneStreamsList"}))`. Lua: set `request.type = "microphoneStreamsList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `nativeDataStatus`. [Token scope definitions](token-permissions.md#nativedatastatus). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("nativeDataStatus", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "nativeDataStatus"}))`. Lua: set `request.type = "nativeDataStatus"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `nfcRead`. [Token scope definitions](token-permissions.md#nfcread). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("nfcRead", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "nfcRead"}))`. Lua: set `request.type = "nfcRead"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `expectedTagIdBase64` | No | `string` | `Not declared` | Optional exact tag ID from nfcRead. Other tags are ignored until the deadline. Tag IDs are not authentication credentials. {"maxLength": 88} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |



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

Ordinary command: `nfcStatus`. [Token scope definitions](token-permissions.md#nfcstatus). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("nfcStatus", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "nfcStatus"}))`. Lua: set `request.type = "nfcStatus"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `nfcTransceive`. [Token scope definitions](token-permissions.md#nfctransceive). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("nfcTransceive", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "nfcTransceive"}))`. Lua: set `request.type = "nfcTransceive"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `dataBase64` | Yes | `string` | `Not declared` | Base64 bytes, at most 32768 decoded bytes. {"maxLength": 43692} |
| `expectedTagIdBase64` | No | `string` | `Not declared` | Optional exact tag ID from nfcRead. Other tags are ignored until the deadline. Tag IDs are not authentication credentials. {"maxLength": 88} |
| `ioTimeoutMilliseconds` | No | `integer` | `5000` | See the complete schema below. {"minimum": 1, "maximum": 60000} |
| `technology` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["IsoDep", "NfcA", "NfcB", "NfcF", "NfcV", "MifareClassic", "MifareUltralight"], "maxLength": 32} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |



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

Ordinary command: `nfcWrite`. [Token scope definitions](token-permissions.md#nfcwrite). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("nfcWrite", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "nfcWrite"}))`. Lua: set `request.type = "nfcWrite"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `dataBase64` | Yes | `string` | `Not declared` | Base64 bytes, at most 32768 decoded bytes. {"maxLength": 43692} |
| `expectedTagIdBase64` | No | `string` | `Not declared` | Optional exact tag ID from nfcRead. Other tags are ignored until the deadline. Tag IDs are not authentication credentials. {"maxLength": 88} |
| `format` | No | `boolean` | `false` | See the complete schema below. |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |



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

Ordinary command: `notificationManage`. [Token scope definitions](token-permissions.md#notificationmanage). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("notificationManage", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "notificationManage"}))`. Lua: set `request.type = "notificationManage"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | dismiss, snooze or reply. {"enum": ["dismiss", "snooze", "reply"], "maxLength": 16} |
| `actionToken` | No | `string` | `Not declared` | Single-use action token supporting inline reply; reply only. {"maxLength": 128} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `durationMilliseconds` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 1000, "maximum": 604800000} |
| `notificationKey` | No | `string` | `Not declared` | Exact key from notificationsList; dismiss/snooze only. {"maxLength": 4096} |
| `text` | No | `string` | `Not declared` | Nonblank reply text; reply only. {"maxLength": 4096} |



```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "description": "dismiss, snooze or reply.",
      "maxLength": 16,
      "enum": [
        "dismiss",
        "snooze",
        "reply"
      ]
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

Ordinary command: `notificationsList`. [Token scope definitions](token-permissions.md#notificationslist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("notificationsList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "notificationsList"}))`. Lua: set `request.type = "notificationsList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `openAutomationSetup`. [Token scope definitions](token-permissions.md#openautomationsetup). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("openAutomationSetup", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "openAutomationSetup"}))`. Lua: set `request.type = "openAutomationSetup"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Ordinary command: `packageInfo`. [Token scope definitions](token-permissions.md#packageinfo). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("packageInfo", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "packageInfo"}))`. Lua: set `request.type = "packageInfo"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `packageName` | Yes | `string` | `Not declared` | Installed Android package name. {"maxLength": 255} |



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

Ordinary command: `remoteAccessInfo`. [Token scope definitions](token-permissions.md#remoteaccessinfo). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("remoteAccessInfo", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "remoteAccessInfo"}))`. Lua: set `request.type = "remoteAccessInfo"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `remoteAuthDisable`. [Token scope definitions](token-permissions.md#remoteauthdisable). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("remoteAuthDisable", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "remoteAuthDisable"}))`. Lua: set `request.type = "remoteAuthDisable"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Ordinary command: `remoteAuthEnable`. [Token scope definitions](token-permissions.md#remoteauthenable). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("remoteAuthEnable", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "remoteAuthEnable"}))`. Lua: set `request.type = "remoteAuthEnable"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Ordinary command: `remoteAuthStatus`. [Token scope definitions](token-permissions.md#remoteauthstatus). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("remoteAuthStatus", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "remoteAuthStatus"}))`. Lua: set `request.type = "remoteAuthStatus"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Remote access-token administration. Explicit command/action grants can delegate these operations. Creating/updating/rotating credentials can confer full owner authority. catalog lists finite permission grants. list returns metadata; create requires name and permissions and returns its generated token once. update requires id and optional name/permissions/enabled/expiry/rate/SMS-address rules; rotate requires id and returns a replacement token once, invalidating the previous secret immediately; revoke requires id. Optional expiresAt is epoch milliseconds (0 means none), requestsPerMinute is 0..10000 (0 means unlimited), and smsAddresses is an exact address allowlist. Restricted SMS reads require filter.address. Authentication must be enabled before creating tokens. Existing tokens, including disabled tokens, prevent disabling or clearing authentication. Local owner bindings, full-owner credentials and explicit remoteTokens operation grants can administer tokens.

Ordinary command: `remoteTokens`. [Token scope definitions](token-permissions.md#remotetokens). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("remoteTokens", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "remoteTokens"}))`. Lua: set `request.type = "remoteTokens"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["catalog", "list", "create", "update", "revoke", "rotate"], "maxLength": 16} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `enabled` | No | `boolean` | `Not declared` | See the complete schema below. |
| `expiresAt` | No | `integer` | `Not declared` | Expiry in epoch milliseconds. Zero means no expiry. {"minimum": 0, "maximum": 9223372036854775807} |
| `id` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 16, "pattern": "^[a-f0-9]{16}$"} |
| `name` | No | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 80} |
| `permissions` | No | `array` | `Not declared` | See the complete schema below. {"minItems": 1, "maxItems": 4096} |
| `requestsPerMinute` | No | `integer` | `Not declared` | Accepted commands per rolling minute across all entrypoints. Zero means unlimited. {"minimum": 0, "maximum": 10000} |
| `smsAddresses` | No | `array` | `Not declared` | Optional exact SMS addresses. Restricted reads require filter.address; sending requires an exact recipient. Empty permits all addresses. {"maxItems": 64} |



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
      "maxItems": 4096,
      "uniqueItems": true,
      "items": {
        "type": "string",
        "maxLength": 256,
        "description": "Exact known grant ID from remoteTokens catalog."
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

Ordinary command: `requestDeviceAccess`. [Token scope definitions](token-permissions.md#requestdeviceaccess). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("requestDeviceAccess", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "requestDeviceAccess"}))`. Lua: set `request.type = "requestDeviceAccess"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `requirementId` | Yes | `string` | `Not declared` | Exact requirement ID from automationStatus.deviceAccess.requirements. {"maxLength": 64} |



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

Stop one resource: target script/widget disables future configuration work; conversation clears event rules and requests cancellation; imageStream/speechStream stops capture. Supply target and id, plus pageId for widgets. Legacy scriptId is accepted alone. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `resourceControl`. [Token scope definitions](token-permissions.md#resourcecontrol). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("resourceControl", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "resourceControl"}))`. Lua: set `request.type = "resourceControl"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `id` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 80} |
| `pageId` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 80} |
| `scriptId` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 80} |
| `target` | No | `string` | `Not declared` | See the complete schema below. {"enum": ["script", "widget", "conversation", "imageStream", "speechStream"]} |



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

Read measured process CPU/RAM/storage, UID network counters and command executor CPU grouped by actor. Explicitly excludes unmeasurable per-widget battery and WebView CPU attribution. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `resourceUsage`. [Token scope definitions](token-permissions.md#resourceusage). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("resourceUsage", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "resourceUsage"}))`. Lua: set `request.type = "resourceUsage"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `restart`. [Token scope definitions](token-permissions.md#restart). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("restart", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "restart"}))`. Lua: set `request.type = "restart"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Ordinary command: `screenshot`. [Token scope definitions](token-permissions.md#screenshot). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("screenshot", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "screenshot"}))`. Lua: set `request.type = "screenshot"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Ordinary command: `scriptLogs`. [Token scope definitions](token-permissions.md#scriptlogs). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("scriptLogs", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "scriptLogs"}))`. Lua: set `request.type = "scriptLogs"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Ordinary command: `scrollBy`. [Token scope definitions](token-permissions.md#scrollby). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("scrollBy", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "scrollBy"}))`. Lua: set `request.type = "scrollBy"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `deltaY` | Yes | `integer` | `Not declared` | Vertical pixel delta. Positive moves down. {"minimum": -10000, "maximum": 10000} |



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

Ordinary command: `scrollTo`. [Token scope definitions](token-permissions.md#scrollto). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("scrollTo", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "scrollTo"}))`. Lua: set `request.type = "scrollTo"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `semanticId` | Yes | `string` | `Not declared` | Stable Dikciz semantic ID from dikciz_snapshot. {"maximum": 256} |



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

Ordinary command: `searchCollections`. [Token scope definitions](token-permissions.md#searchcollections). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("searchCollections", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "searchCollections"}))`. Lua: set `request.type = "searchCollections"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `5` | See the complete schema below. {"minimum": 1, "maximum": 20} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 10000} |



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

Ordinary command: `searchIndex`. [Token scope definitions](token-permissions.md#searchindex). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("searchIndex", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "searchIndex"}))`. Lua: set `request.type = "searchIndex"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["upsert", "remove", "clear"], "maxLength": 16} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `collection` | Yes | `string` | `Not declared` | Lowercase collection name. docs and tools are protected built-ins; other names scope explicitly indexed owner text. {"maxLength": 64, "pattern": "^[a-z][a-z0-9_.-]{0,63}$"} |
| `documents` | No | `array` | `Not declared` | See the complete schema below. {"minItems": 1, "maxItems": 32} |
| `ids` | No | `array` | `Not declared` | See the complete schema below. {"minItems": 1, "maxItems": 32} |



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

Ordinary command: `searchQuery`. [Token scope definitions](token-permissions.md#searchquery). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("searchQuery", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "searchQuery"}))`. Lua: set `request.type = "searchQuery"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `collection` | No | `string` | `"docs"` | Lowercase collection name. docs and tools are protected built-ins; other names scope explicitly indexed owner text. {"maxLength": 64, "pattern": "^[a-z][a-z0-9_.-]{0,63}$"} |
| `fuzzy` | No | `boolean` | `true` | See the complete schema below. |
| `limit` | No | `integer` | `5` | See the complete schema below. {"minimum": 1, "maximum": 20} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 10000} |
| `query` | Yes | `string` | `Not declared` | Plain text task, identifier or keywords, up to 16 normalized terms. {"minLength": 1, "maxLength": 512} |



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

Ordinary command: `searchRead`. [Token scope definitions](token-permissions.md#searchread). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("searchRead", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "searchRead"}))`. Lua: set `request.type = "searchRead"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `collection` | No | `string` | `"docs"` | Lowercase collection name. docs and tools are protected built-ins; other names scope explicitly indexed owner text. {"maxLength": 64, "pattern": "^[a-z][a-z0-9_.-]{0,63}$"} |
| `id` | Yes | `string` | `Not declared` | Exact opaque document ID. It is a database key, never a file path. {"maxLength": 256, "pattern": "^[A-Za-z0-9][A-Za-z0-9_.:/#-]{0,255}$"} |



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

Ordinary command: `selectPage`. [Token scope definitions](token-permissions.md#selectpage). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("selectPage", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "selectPage"}))`. Lua: set `request.type = "selectPage"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `pageId` | Yes | `string` | `Not declared` | Stable Dikciz page ID from dikciz_snapshot. {"maximum": 256} |



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

Ordinary command: `sensorReadings`. [Token scope definitions](token-permissions.md#sensorreadings). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("sensorReadings", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "sensorReadings"}))`. Lua: set `request.type = "sensorReadings"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |
| `sensorId` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 2147483647} |
| `timeoutMilliseconds` | No | `integer` | `1000` | See the complete schema below. {"minimum": 1, "maximum": 5000} |



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

Ordinary command: `setText`. [Token scope definitions](token-permissions.md#settext). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("setText", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "setText"}))`. Lua: set `request.type = "setText"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `semanticId` | Yes | `string` | `Not declared` | Stable Dikciz semantic ID from dikciz_snapshot. {"maximum": 256} |
| `text` | Yes | `string` | `Not declared` | New editable input or text widget value. {"maximum": 8192} |



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

Ordinary command: `shell`. [Token scope definitions](token-permissions.md#shell). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("shell", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "shell"}))`. Lua: set `request.type = "shell"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `command` | Yes | `string` | `Not declared` | Command run by Dikciz. Root is requested by default. {"maximum": 8192} |
| `maxOutputBytes` | No | `integer` | `65536` | Maximum combined stdout and stderr bytes. Defaults to 65536; maximum 1048576. {"minimum": 1, "maximum": 1048576} |
| `root` | No | `boolean` | `Not declared` | Run through su. Defaults to true. False runs as the launcher app user. |
| `timeoutMilliseconds` | No | `integer` | `30000` | Command timeout in milliseconds. Defaults to 30000. {"minimum": 1, "maximum": 2147483647} |



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

Ordinary command: `shellSessionForget`. [Token scope definitions](token-permissions.md#shellsessionforget). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("shellSessionForget", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "shellSessionForget"}))`. Lua: set `request.type = "shellSessionForget"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `streamId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |



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

Ordinary command: `shellSessionRead`. [Token scope definitions](token-permissions.md#shellsessionread). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("shellSessionRead", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "shellSessionRead"}))`. Lua: set `request.type = "shellSessionRead"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `cursor` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 9223372036854775807} |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `streamId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |
| `waitMilliseconds` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 30000} |



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

Ordinary command: `shellSessionStart`. [Token scope definitions](token-permissions.md#shellsessionstart). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("shellSessionStart", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "shellSessionStart"}))`. Lua: set `request.type = "shellSessionStart"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `bufferBytes` | No | `integer` | `1048576` | See the complete schema below. {"minimum": 65536, "maximum": 8388608} |
| `chunkBytes` | No | `integer` | `4096` | See the complete schema below. {"minimum": 1, "maximum": 65536} |
| `command` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 8192} |
| `maximumEvents` | No | `integer` | `1024` | See the complete schema below. {"minimum": 1, "maximum": 4096} |
| `retentionMinutes` | No | `integer` | `60` | See the complete schema below. {"minimum": 1, "maximum": 1440} |
| `root` | No | `boolean` | `true` | See the complete schema below. |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 0, "maximum": 2147483647} |



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

Ordinary command: `shellSessionStop`. [Token scope definitions](token-permissions.md#shellsessionstop). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("shellSessionStop", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "shellSessionStop"}))`. Lua: set `request.type = "shellSessionStop"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `streamId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |



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

Ordinary command: `shellSessionsList`. [Token scope definitions](token-permissions.md#shellsessionslist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("shellSessionsList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "shellSessionsList"}))`. Lua: set `request.type = "shellSessionsList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `shellStream`. [Token scope definitions](token-permissions.md#shellstream). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("shellStream", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "shellStream"}))`. Lua: set `request.type = "shellStream"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `chunkBytes` | No | `integer` | `4096` | Maximum bytes per output chunk. Output is base64 encoded without a total output limit. {"minimum": 1, "maximum": 65536} |
| `command` | Yes | `string` | `Not declared` | Command run by Dikciz. Root is requested by default. {"maximum": 8192} |
| `root` | No | `boolean` | `Not declared` | Run through su. Defaults to true. False runs as the launcher app user. |
| `timeoutMilliseconds` | No | `integer` | `0` | Deadline in milliseconds. Zero, the default, keeps streaming until stopped or disconnected. {"minimum": 0, "maximum": 2147483647} |



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

Ordinary command: `shellStreamStop`. [Token scope definitions](token-permissions.md#shellstreamstop). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("shellStreamStop", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "shellStreamStop"}))`. Lua: set `request.type = "shellStreamStop"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `streamId` | Yes | `string` | `Not declared` | Stream UUID from the started notification. {"maxLength": 36} |



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

Ordinary command: `simList`. [Token scope definitions](token-permissions.md#simlist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("simList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "simList"}))`. Lua: set `request.type = "simList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `smsSend`. [Token scope definitions](token-permissions.md#smssend). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("smsSend", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "smsSend"}))`. Lua: set `request.type = "smsSend"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `body` | Yes | `string` | `Not declared` | SMS body. Android splits multipart messages. {"maxLength": 4096} |
| `idempotencyKey` | Yes | `string` | `Not declared` | Unique caller key. Reusing it with different content fails. Retained until app data is cleared. {"maxLength": 80} |
| `recipient` | Yes | `string` | `Not declared` | Dialable phone number. Spaces, parentheses, dots and hyphens are normalized. {"maxLength": 64} |
| `subscriptionId` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 2147483647} |



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

Ordinary command: `smsStatus`. [Token scope definitions](token-permissions.md#smsstatus). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("smsStatus", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "smsStatus"}))`. Lua: set `request.type = "smsStatus"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `idempotencyKey` | Yes | `string` | `Not declared` | Key from smsSend. {"maxLength": 80} |



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

Ordinary command: `snapshot`. [Token scope definitions](token-permissions.md#snapshot). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("snapshot", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "snapshot"}))`. Lua: set `request.type = "snapshot"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Ordinary command: `sosControl`. [Token scope definitions](token-permissions.md#soscontrol). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("sosControl", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "sosControl"}))`. Lua: set `request.type = "sosControl"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["arm", "trigger", "checkIn", "cancel"], "minLength": 1, "maxLength": 16} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `confirm` | No | `boolean` | `Not declared` | See the complete schema below. |



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

Ordinary command: `sosPlanSave`. [Token scope definitions](token-permissions.md#sosplansave). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("sosPlanSave", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "sosPlanSave"}))`. Lua: set `request.type = "sosPlanSave"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `checkInSeconds` | No | `integer` | `3600` | See the complete schema below. {"minimum": 60, "maximum": 604800} |
| `contacts` | Yes | `array` | `Not declared` | See the complete schema below. {"minItems": 1, "maxItems": 10} |
| `graceSeconds` | No | `integer` | `60` | See the complete schema below. {"minimum": 5, "maximum": 3600} |
| `maximumUpdates` | No | `integer` | `3` | See the complete schema below. {"minimum": 1, "maximum": 24} |
| `message` | No | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 1200} |
| `subscriptionId` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 2147483647} |
| `updateSeconds` | No | `integer` | `600` | See the complete schema below. {"minimum": 60, "maximum": 86400} |



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

Ordinary command: `sosPreview`. [Token scope definitions](token-permissions.md#sospreview). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("sosPreview", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "sosPreview"}))`. Lua: set `request.type = "sosPreview"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `sosStatus`. [Token scope definitions](token-permissions.md#sosstatus). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("sosStatus", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "sosStatus"}))`. Lua: set `request.type = "sosStatus"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `speechLanguages`. [Token scope definitions](token-permissions.md#speechlanguages). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("speechLanguages", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "speechLanguages"}))`. Lua: set `request.type = "speechLanguages"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `language` | No | `string` | `Not declared` | BCP-47 recognition language; inventory can depend on this request. {"maxLength": 64} |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `mode` | No | `string` | `Not declared` | onDevice by default; system explicitly selects the installed system provider. {"enum": ["onDevice", "system"], "maxLength": 16} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |



```json
{
  "type": "object",
  "properties": {
    "mode": {
      "type": "string",
      "maxLength": 16,
      "description": "onDevice by default; system explicitly selects the installed system provider.",
      "enum": [
        "onDevice",
        "system"
      ]
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

Ordinary command: `speechModelDownload`. [Token scope definitions](token-permissions.md#speechmodeldownload). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("speechModelDownload", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "speechModelDownload"}))`. Lua: set `request.type = "speechModelDownload"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `language` | Yes | `string` | `Not declared` | BCP-47 recognition language; inventory can depend on this request. {"maxLength": 64} |
| `mode` | No | `string` | `Not declared` | onDevice by default; system explicitly selects the installed system provider. {"enum": ["onDevice", "system"], "maxLength": 16} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |



```json
{
  "type": "object",
  "properties": {
    "mode": {
      "type": "string",
      "maxLength": 16,
      "description": "onDevice by default; system explicitly selects the installed system provider.",
      "enum": [
        "onDevice",
        "system"
      ]
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

Ordinary command: `speechModelStatus`. [Token scope definitions](token-permissions.md#speechmodelstatus). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("speechModelStatus", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "speechModelStatus"}))`. Lua: set `request.type = "speechModelStatus"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `sessionId` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |



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

Ordinary command: `speechRecognitionStatus`. [Token scope definitions](token-permissions.md#speechrecognitionstatus). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("speechRecognitionStatus", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "speechRecognitionStatus"}))`. Lua: set `request.type = "speechRecognitionStatus"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `speechRecognitionStop`. [Token scope definitions](token-permissions.md#speechrecognitionstop). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("speechRecognitionStop", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "speechRecognitionStop"}))`. Lua: set `request.type = "speechRecognitionStop"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `speechRecognize`. [Token scope definitions](token-permissions.md#speechrecognize). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("speechRecognize", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "speechRecognize"}))`. Lua: set `request.type = "speechRecognize"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `language` | No | `string` | `Not declared` | BCP-47 language tag, for example en-US; defaults to device language. {"maxLength": 64} |
| `mode` | No | `string` | `Not declared` | onDevice or system. {"enum": ["onDevice", "system"], "maxLength": 16} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |



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
      "maxLength": 16,
      "enum": [
        "onDevice",
        "system"
      ]
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

Ordinary command: `speechSpeak`. [Token scope definitions](token-permissions.md#speechspeak). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("speechSpeak", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "speechSpeak"}))`. Lua: set `request.type = "speechSpeak"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `language` | No | `string` | `Not declared` | BCP-47 language; defaults to engine selection. {"maxLength": 64} |
| `pitchPercent` | No | `integer` | `100` | See the complete schema below. {"minimum": 25, "maximum": 400} |
| `ratePercent` | No | `integer` | `100` | See the complete schema below. {"minimum": 25, "maximum": 400} |
| `text` | Yes | `string` | `Not declared` | Text spoken by the phone. {"maxLength": 2000} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |
| `voiceName` | No | `string` | `Not declared` | Exact name from speechVoices. Voice selection takes precedence over language. {"maxLength": 256} |



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

Ordinary command: `speechStop`. [Token scope definitions](token-permissions.md#speechstop). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("speechStop", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "speechStop"}))`. Lua: set `request.type = "speechStop"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `speechStreamForget`. [Token scope definitions](token-permissions.md#speechstreamforget). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("speechStreamForget", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "speechStreamForget"}))`. Lua: set `request.type = "speechStreamForget"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `streamId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |



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

Read bounded partial/final speech events with exclusive cursor, historyGap, lostEvents and terminal state. Does not start capture. Retained in memory for five minutes after completion. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `speechStreamRead`. [Token scope definitions](token-permissions.md#speechstreamread). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("speechStreamRead", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "speechStreamRead"}))`. Lua: set `request.type = "speechStreamRead"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `cursor` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 9007199254740991} |
| `limit` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `streamId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |
| `waitMilliseconds` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 30000} |



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

Ordinary command: `speechStreamStart`. [Token scope definitions](token-permissions.md#speechstreamstart). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("speechStreamStart", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "speechStreamStart"}))`. Lua: set `request.type = "speechStreamStart"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `language` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 64} |
| `mode` | No | `string` | `"onDevice"` | See the complete schema below. {"enum": ["onDevice", "system"], "maxLength": 16} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1000, "maximum": 120000} |



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

Stop one owned speech session and report actual cleanup state. Does not cancel another session. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `speechStreamStop`. [Token scope definitions](token-permissions.md#speechstreamstop). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("speechStreamStop", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "speechStreamStop"}))`. Lua: set `request.type = "speechStreamStop"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `streamId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 36} |



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

Ordinary command: `speechStreamsList`. [Token scope definitions](token-permissions.md#speechstreamslist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("speechStreamsList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "speechStreamsList"}))`. Lua: set `request.type = "speechStreamsList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `speechSynthesize`. [Token scope definitions](token-permissions.md#speechsynthesize). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("speechSynthesize", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "speechSynthesize"}))`. Lua: set `request.type = "speechSynthesize"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `language` | No | `string` | `Not declared` | BCP-47 language; defaults to engine selection. {"maxLength": 64} |
| `pitchPercent` | No | `integer` | `100` | See the complete schema below. {"minimum": 25, "maximum": 400} |
| `ratePercent` | No | `integer` | `100` | See the complete schema below. {"minimum": 25, "maximum": 400} |
| `text` | Yes | `string` | `Not declared` | Text spoken by the phone. {"maxLength": 2000} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |
| `voiceName` | No | `string` | `Not declared` | Exact name from speechVoices. Voice selection takes precedence over language. {"maxLength": 256} |



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

Upload one audio file to the explicitly selected OpenAI-compatible provider's audio/transcriptions endpoint. Uses its encrypted saved API key. Requires profileId, model and path. Sends file contents outside the phone; never opens the microphone. Provider support and usage charges apply. Requires the matching explicit operation grant for scoped remote clients.

Ordinary command: `speechTranscribeFile`. [Token scope definitions](token-permissions.md#speechtranscribefile). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("speechTranscribeFile", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "speechTranscribeFile"}))`. Lua: set `request.type = "speechTranscribeFile"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `language` | No | `string` | `Not declared` | See the complete schema below. {"maxLength": 2} |
| `model` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 256} |
| `path` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 4096} |
| `profileId` | Yes | `string` | `Not declared` | See the complete schema below. {"maxLength": 80} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1000, "maximum": 300000} |



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

Ordinary command: `speechVoices`. [Token scope definitions](token-permissions.md#speechvoices). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("speechVoices", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "speechVoices"}))`. Lua: set `request.type = "speechVoices"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `systemSetting`. [Token scope definitions](token-permissions.md#systemsetting). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("systemSetting", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "systemSetting"}))`. Lua: set `request.type = "systemSetting"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | get or set. {"enum": ["get", "set"], "maxLength": 16} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `setting` | Yes | `string` | `Not declared` | brightness, automaticBrightness, automaticRotation, rotation or screenTimeoutMilliseconds. {"enum": ["brightness", "automaticBrightness", "automaticRotation", "rotation", "screenTimeoutMilliseconds"], "maxLength": 64} |
| `value` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 2147483647} |



```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "description": "get or set.",
      "maxLength": 16,
      "enum": [
        "get",
        "set"
      ]
    },
    "setting": {
      "type": "string",
      "description": "brightness, automaticBrightness, automaticRotation, rotation or screenTimeoutMilliseconds.",
      "maxLength": 64,
      "enum": [
        "brightness",
        "automaticBrightness",
        "automaticRotation",
        "rotation",
        "screenTimeoutMilliseconds"
      ]
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

Ordinary command: `tap`. [Token scope definitions](token-permissions.md#tap). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("tap", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "tap"}))`. Lua: set `request.type = "tap"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `semanticId` | Yes | `string` | `Not declared` | Stable Dikciz semantic ID from dikciz_snapshot. {"maximum": 256} |



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

Ordinary command: `torchControl`. [Token scope definitions](token-permissions.md#torchcontrol). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("torchControl", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "torchControl"}))`. Lua: set `request.type = "torchControl"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | status, on or off. {"enum": ["status", "on", "off"], "maxLength": 16} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `cameraId` | No | `string` | `Not declared` | Flash-capable camera ID; status defaults to all flash cameras. {"maxLength": 128} |
| `durationMilliseconds` | No | `integer` | `Not declared` | On only: automatically request torch off after this duration, independent of WebView timers. {"minimum": 1, "maximum": 5000} |
| `timeoutMilliseconds` | No | `integer` | `1000` | Status callback wait only. {"minimum": 1, "maximum": 5000} |



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
      "description": "status, on or off.",
      "enum": [
        "status",
        "on",
        "off"
      ]
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

Ordinary command: `uiDump`. [Token scope definitions](token-permissions.md#uidump). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("uiDump", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "uiDump"}))`. Lua: set `request.type = "uiDump"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |



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

Ordinary command: `usbDevices`. [Token scope definitions](token-permissions.md#usbdevices). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("usbDevices", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "usbDevices"}))`. Lua: set `request.type = "usbDevices"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `usbRequestAccess`. [Token scope definitions](token-permissions.md#usbrequestaccess). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("usbRequestAccess", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "usbRequestAccess"}))`. Lua: set `request.type = "usbRequestAccess"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `deviceId` | Yes | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 2147483647} |



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

Ordinary command: `usbTransfer`. [Token scope definitions](token-permissions.md#usbtransfer). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("usbTransfer", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "usbTransfer"}))`. Lua: set `request.type = "usbTransfer"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `dataBase64` | No | `string` | `Not declared` | Base64 bytes for an output transfer, at most 65536 decoded bytes. {"maxLength": 87384} |
| `deviceId` | Yes | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 2147483647} |
| `endpointAddress` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 255} |
| `index` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 65535} |
| `interfaceIndex` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 255} |
| `kind` | Yes | `string` | `Not declared` | control or bulk. {"enum": ["control", "bulk"], "maxLength": 16} |
| `length` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 65536} |
| `request` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 255} |
| `requestType` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 255} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |
| `value` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 65535} |



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
      "maxLength": 16,
      "enum": [
        "control",
        "bulk"
      ]
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

Ordinary command: `ussdRequest`. [Token scope definitions](token-permissions.md#ussdrequest). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("ussdRequest", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "ussdRequest"}))`. Lua: set `request.type = "ussdRequest"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `code` | Yes | `string` | `Not declared` | Full USSD code beginning with star/hash and ending with hash; digits, star, hash and plus only. {"minLength": 2, "maxLength": 182} |
| `maximumResponseCharacters` | No | `integer` | `4096` | Maximum returned Unicode code points; responseTruncated reports shortening. {"minimum": 1, "maximum": 65536} |
| `subscriptionId` | No | `integer` | `Not declared` | See the complete schema below. {"minimum": 0, "maximum": 2147483647} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |



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

Ordinary command: `vibrationControl`. [Token scope definitions](token-permissions.md#vibrationcontrol). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("vibrationControl", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "vibrationControl"}))`. Lua: set `request.type = "vibrationControl"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | status, vibrate, waveform, effect or cancel. {"enum": ["status", "vibrate", "waveform", "effect", "cancel"], "maxLength": 16} |
| `amplitude` | No | `integer` | `-1` | See the complete schema below. {"minimum": -1, "maximum": 255} |
| `amplitudes` | No | `array` | `Not declared` | See the complete schema below. {"minItems": 1, "maxItems": 64} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `durationMilliseconds` | No | `integer` | `250` | See the complete schema below. {"minimum": 1, "maximum": 10000} |
| `effect` | No | `string` | `Not declared` | click, doubleClick, heavyClick or tick. Android may use its predefined fallback. {"maxLength": 16} |
| `timingsMilliseconds` | No | `array` | `Not declared` | See the complete schema below. {"minItems": 1, "maxItems": 64} |



```json
{
  "type": "object",
  "properties": {
    "action": {
      "type": "string",
      "maxLength": 16,
      "description": "status, vibrate, waveform, effect or cancel.",
      "enum": [
        "status",
        "vibrate",
        "waveform",
        "effect",
        "cancel"
      ]
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

Ordinary command: `waitFor`. [Token scope definitions](token-permissions.md#waitfor). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("waitFor", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "waitFor"}))`. Lua: set `request.type = "waitFor"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `semanticId` | Yes | `string` | `Not declared` | Stable Dikciz semantic ID from dikciz_snapshot. {"maximum": 256} |
| `timeoutMilliseconds` | Yes | `integer` | `Not declared` | Maximum time to wait for the semantic ID, in milliseconds. {"minimum": 1, "maximum": 5000} |



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

Ordinary command: `widgetGet`. [Token scope definitions](token-permissions.md#widgetget). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("widgetGet", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "widgetGet"}))`. Lua: set `request.type = "widgetGet"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `widgetAddress` | Yes | `string` | `Not declared` | Stable Dikciz widget address in 1H2V-widget-id form. {"maximum": 256} |



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

Ordinary command: `widgetMove`. [Token scope definitions](token-permissions.md#widgetmove). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("widgetMove", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "widgetMove"}))`. Lua: set `request.type = "widgetMove"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `cell` | Yes | `object` | `Not declared` | Grid rectangle with column, row, columnSpan, and rowSpan. |
| `widgetAddress` | Yes | `string` | `Not declared` | Stable Dikciz widget address in 1H2V-widget-id form. {"maximum": 256} |



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

Ordinary command: `widgetResize`. [Token scope definitions](token-permissions.md#widgetresize). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("widgetResize", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "widgetResize"}))`. Lua: set `request.type = "widgetResize"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation. |
| `cell` | Yes | `object` | `Not declared` | Grid rectangle with column, row, columnSpan, and rowSpan. |
| `widgetAddress` | Yes | `string` | `Not declared` | Stable Dikciz widget address in 1H2V-widget-id form. {"maximum": 256} |



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

Ordinary command: `wifiConnectionStart`. [Token scope definitions](token-permissions.md#wificonnectionstart). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("wifiConnectionStart", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "wifiConnectionStart"}))`. Lua: set `request.type = "wifiConnectionStart"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `maximumDurationMilliseconds` | No | `integer` | `300000` | See the complete schema below. {"minimum": 1000, "maximum": 3600000} |
| `passphrase` | No | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 63} |
| `security` | No | `string` | `Not declared` | See the complete schema below. {"enum": ["open", "wpa2", "wpa3"], "minLength": 1, "maxLength": 16} |
| `ssid` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 32} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1000, "maximum": 120000} |



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

Ordinary command: `wifiConnectionStatus`. [Token scope definitions](token-permissions.md#wificonnectionstatus). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("wifiConnectionStatus", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "wifiConnectionStatus"}))`. Lua: set `request.type = "wifiConnectionStatus"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `id` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 80} |



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

Ordinary command: `wifiConnectionStop`. [Token scope definitions](token-permissions.md#wificonnectionstop). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("wifiConnectionStop", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "wifiConnectionStop"}))`. Lua: set `request.type = "wifiConnectionStop"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `id` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 80} |



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

List retained local network/hotspot requests with pagination. Hotspot credentials require full owner access or this explicit command grant.

Ordinary command: `wifiConnectionsList`. [Token scope definitions](token-permissions.md#wificonnectionslist). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("wifiConnectionsList", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "wifiConnectionsList"}))`. Lua: set `request.type = "wifiConnectionsList"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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

Ordinary command: `wifiHotspotStart`. [Token scope definitions](token-permissions.md#wifihotspotstart). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("wifiHotspotStart", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "wifiHotspotStart"}))`. Lua: set `request.type = "wifiHotspotStart"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `maximumDurationMilliseconds` | No | `integer` | `300000` | See the complete schema below. {"minimum": 1000, "maximum": 3600000} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1000, "maximum": 120000} |



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

Read retained local-only hotspot state and credentials with full owner access or this explicit command grant. Starting requires wifiHotspotStart.

Ordinary command: `wifiHotspotStatus`. [Token scope definitions](token-permissions.md#wifihotspotstatus). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("wifiHotspotStatus", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "wifiHotspotStatus"}))`. Lua: set `request.type = "wifiHotspotStatus"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |



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

Ordinary command: `wifiHotspotStop`. [Token scope definitions](token-permissions.md#wifihotspotstop). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("wifiHotspotStop", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "wifiHotspotStop"}))`. Lua: set `request.type = "wifiHotspotStop"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `id` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 80} |



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

Ordinary command: `wifiNetworksConfigure`. [Token scope definitions](token-permissions.md#wifinetworksconfigure). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("wifiNetworksConfigure", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "wifiNetworksConfigure"}))`. Lua: set `request.type = "wifiNetworksConfigure"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `action` | Yes | `string` | `Not declared` | See the complete schema below. {"enum": ["suggest", "removeSuggestion", "save"], "minLength": 1, "maxLength": 32} |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `passphrase` | No | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 63} |
| `security` | No | `string` | `Not declared` | See the complete schema below. {"enum": ["open", "wpa2", "wpa3"], "minLength": 1, "maxLength": 16} |
| `ssid` | Yes | `string` | `Not declared` | See the complete schema below. {"minLength": 1, "maxLength": 32} |



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

Ordinary command: `wifiScan`. [Token scope definitions](token-permissions.md#wifiscan). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("wifiScan", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "wifiScan"}))`. Lua: set `request.type = "wifiScan"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |
| `timeoutMilliseconds` | No | `integer` | `30000` | See the complete schema below. {"minimum": 1, "maximum": 120000} |



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

Ordinary command: `wifiScanResults`. [Token scope definitions](token-permissions.md#wifiscanresults). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("wifiScanResults", fields)`. Headless JavaScript: `context.request(Object.assign({}, fields, {type: "wifiScanResults"}))`. Lua: set `request.type = "wifiScanResults"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

| Parameter | Required | Type | Default | Definition and bounds |
| --- | --- | --- | --- | --- |
| `autoRequestPermissions` | No | `boolean` | `false` | Ask the owner to grant missing Android access. Never grants access or retries automatically. |
| `limit` | No | `integer` | `50` | See the complete schema below. {"minimum": 1, "maximum": 200} |
| `offset` | No | `integer` | `0` | See the complete schema below. {"minimum": 0, "maximum": 1000000} |



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
