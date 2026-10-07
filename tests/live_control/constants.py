"""Release UI, ADB, WebSocket, and MCP tests on the isolated Dikciz emulator."""

from __future__ import annotations

import argparse
import copy
import hashlib
import importlib.util
import json
import os
import re
import shutil
import socket
import struct
import time
import zipfile
import zlib
from collections.abc import Callable, Generator
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path
from types import ModuleType
from typing import Any
from xml.etree import ElementTree

import pytest

from conftest import ARTIFACT_DIRECTORY, LAB_ROOT, run_device_operation


AUTOMATION_HOST = "127.0.0.1"
AUTOMATION_PORT = int(os.environ.get("ANDROID_LAB_FORWARD_HOST_PORT", "19001"))
APP_WIDGET_HOST_VIEW_CLASS_NAME = "android.appwidget.AppWidgetHostView"
APP_WIDGET_BIND_ACTIVITY_COMPONENT = "com.android.settings/.AllowBindAppWidgetActivity"
APP_WIDGET_BIND_APPROVAL_ARTIFACT_NAME = "app-widget-bind-approval.png"
APP_WIDGET_BIND_APPROVAL_RESOURCE_ID = "android:id/button1"
APP_WIDGET_BIND_APPROVAL_TIMEOUT_SECONDS = 10
APP_WIDGET_BIND_APPLICATION_LABEL = "Dikciz"
APP_WIDGET_CONFIGURATION_ARTIFACT_NAME = "app-widget-provider-configuration.png"
APP_WIDGET_CONFIGURATION_TIMEOUT_SECONDS = 10
APP_SHORTCUT_CONTENT_DESCRIPTION_FORMAT = "{} app shortcut, {}"
APP_SHORTCUT_DEFAULT_COLUMN_SPAN = 1
APP_SHORTCUT_DEFAULT_ROW_SPAN = 1
APP_SHORTCUT_DEFAULT_STYLE_LABEL = "Icon with label"
APPEARANCE_ARTIFACT_NAME = "appearance-editor.png"
APPEARANCE_BORDER_RADIUS_LABEL = "Corner radius"
APPEARANCE_COLOR_PICKER_ARTIFACT_NAME = "appearance-color-picker.png"
APPEARANCE_DIALOG_ARTIFACT_NAME = "appearance-editor-dialog.png"
APPEARANCE_FONT_PICKER_ARTIFACT_NAME = "appearance-font-picker.png"
APPEARANCE_WIDGET_ACTIONS_ARTIFACT_NAME = "appearance-widget-actions.png"
APPEARANCE_CHOOSE_COLOR_DESCRIPTION = "Choose Color"
APPEARANCE_CHOOSE_FONT_DESCRIPTION = "Choose font. Current Inherited"
APPEARANCE_EDITOR_DESCRIPTION = "Appearance editor"
APPEARANCE_EDITOR_SCROLL_ATTEMPTS = 3
APPEARANCE_EDITOR_SCROLL_INSET_PIXELS = 96
APPEARANCE_FIELD_BACKGROUND_COLOR = "background-color"
APPEARANCE_FIELD_BACKGROUND_OPACITY = "background-opacity"
APPEARANCE_INCREASE_OPACITY_DESCRIPTION = "Increase Opacity"
APPEARANCE_INCREASE_BORDER_RADIUS_DESCRIPTION = "Increase Corner radius"
APPEARANCE_WIDGET_BACKGROUND_COLOR = "#173A5E"
APPEARANCE_WIDGET_BACKGROUND_OPACITY = 0.05
APPEARANCE_WIDGET_BORDER_RADIUS_DP = 18
APPEARANCE_WIDGET_EDITOR_BORDER_RADIUS_DP = 1
APPEARANCE_WIDGET_DEFAULT_BACKGROUND_COLOR = "#0F1F2F"
APPEARANCE_WIDGET_MARGIN_BOTTOM_DP = 2
APPEARANCE_WIDGET_MARGIN_LEFT_DP = 8
APPEARANCE_WIDGET_MARGIN_RIGHT_DP = 4
APPEARANCE_WIDGET_MARGIN_TOP_DP = 6
APPEARANCE_WIDGET_SCOPE_PREFIX = "appearance:widget:welcome"
APPEARANCE_WIDGET_DEFAULT_SCOPE_PREFIX = "appearance:defaults:widget"
APPEARANCE_WIDGET_ACTION_SEMANTIC_ID = "widget:welcome:appearance"
APPEARANCE_COMIC_NEUE_DESCRIPTION = "Use font Bundled: Comic Neue"
APPEARANCE_USE_WIDGET_COLOR_DESCRIPTION = f"Use {APPEARANCE_WIDGET_BACKGROUND_COLOR}"
ADB_STYLE_LOCK_ARTIFACT_NAME = "adb-style-lock-reload.png"
COMMAND_APP_ENTRY_PREFIX = "command:entry:app:"
COMMAND_NO_RESULTS_LABEL = "Nothing found."
# The page menu entry reads as a verb (dikciz_page_menu_commands_verb); the
# sheet it opens is the one titled "Apps and commands".
COMMAND_PAGE_MENU_LABEL = "Search apps and commands"
COMMAND_SETTINGS_PACKAGE_QUERY = "com.android.settings"
COMMAND_SEARCH_DESCRIPTION = "Search pages and apps"
COMMAND_SEARCH_SEMANTIC_ID = "command:search"
COMMAND_SELECT_NOTES_SEMANTIC_ID = "command:entry:select-page:notes"
COMMAND_APP_ICON_ARTIFACT_NAME = "apps-and-commands-settings-icon.png"
COMMAND_APP_ENTRY_MAXIMUM_HEIGHT_DP = 64
COMMAND_SETTINGS_SEMANTIC_ID = "command:entry:settings"
COMMAND_SHORTCUT_ARTIFACT_NAME = "settings-shortcut.png"
COMMAND_UPPERCASE_SETTINGS_QUERY = "SETTINGS"
COMMAND_REDACTED_ADD_SHORTCUT_SEMANTIC_ID = "command:entry:app:add-shortcut"
CHROME_CATEGORY_LABEL = "Chrome"
CHROME_PROVIDER_LABEL = "Chrome search"
CHROME_PROVIDER_PICKER_LABEL = f"{CHROME_CATEGORY_LABEL}, {CHROME_PROVIDER_LABEL}"
CHROME_PROVIDER_PACKAGE = "com.android.chrome"
CHROME_SEARCH_MOVED_ARTIFACT_NAME = "chrome-search-provider-moved.png"
BUNDLED_FONT_IDS = (
    "inter",
    "jetbrains-mono",
    "space-grotesk",
    "comic-neue",
)
CONFIG_ROOT_ARTIFACT_NAME = "dikciz-pytest-config.json"
CONFIG_DEVICE_PATH = "/sdcard/Dikciz/config.json"
CONFIG_HOME_PAGE_DEVICE_PATH = "/sdcard/Dikciz/pages/home/page.json"
CONFIG_HOME_PAGE_ARTIFACT_NAME = "dikciz-pytest-home-page.json"
CONFIG_WELCOME_WIDGET_DEVICE_PATH = "/sdcard/Dikciz/pages/home/widgets/welcome.json"
LUA_SCRIPT_API_VERSION = 1
CONFIG_WELCOME_WIDGET_ARTIFACT_NAME = "dikciz-pytest-welcome-widget.json"
DIALOG_CLOSE_SEMANTIC_ID = "dialog:close"
LUA_RESET_BUNDLED_SCRIPT_ID = "lua-example"
LUA_RESET_BUNDLED_SCRIPT_STATE = {
    "message": "This card reads public state. Tap it to return Home.",
}
LUA_RESET_BUNDLED_SCRIPT_TITLE = "Lua page hint"
LUA_RESET_COLLISION_SOURCE = 'return { status = "Custom replacement" }'
LUA_RESET_USER_SCRIPT_ID = "reset-user-script"
LUA_RESET_USER_SCRIPT_SOURCE = 'return { status = "Keep this script" }'
LUA_RESET_USER_SCRIPT_STATE = {"message": "Keep this script state"}
LUA_RESET_USER_SCRIPT_TITLE = "Reset user script"
LUA_MANAGER_ARTIFACT_NAME = "lua-script-manager-rejected.png"
LUA_MANAGER_RENDER_DIAGNOSTICS = (
    "Rendering was cancelled",
    "Source ran too long",
    "Source used too many instructions",
    "Source could not run",
    "Source returned an unsupported result",
    "Source used too much memory",
    "Source needs attention",
)
LUA_ARCHIVE_ARTIFACT_NAME = "dikciz-pytest-script.dikciz-script.zip"
LUA_ARCHIVE_DEVICE_PATH = "/sdcard/Dikciz/scripts/imports/dikciz-pytest-script.dikciz-script.zip"
LUA_ARCHIVE_EXPORTED_ARTIFACT_NAME = "dikciz-pytest-script-export.dikciz-script.zip"
LUA_ARCHIVE_EXPORTED_DEVICE_PATH = "/sdcard/Dikciz/scripts/exports/archive-fixture-1.dikciz-script.zip"
LUA_ARCHIVE_EXPORT_ARTIFACT_NAME = "lua-script-archive-export.png"
LUA_ARCHIVE_FILE_SEMANTIC_ID = "lua-script:import-file:dikciz-pytest-script.dikciz-script.zip"
LUA_ARCHIVE_ID = "archive-fixture"
LUA_ARCHIVE_SCRIPT_DIRECTORY = "/sdcard/Dikciz/scripts/archive-fixture"
LUA_ARCHIVE_IMPORT_CONFIRM_SEMANTIC_ID = "lua-script:import-confirm"
LUA_ARCHIVE_IMPORT_KEEP_SEMANTIC_ID = "lua-script:import-keep"
LUA_ARCHIVE_IMPORT_REPLACE_SEMANTIC_ID = "lua-script:import-replace"
LUA_ARCHIVE_IMPORT_SEMANTIC_ID = "lua-script:import"
LUA_ARCHIVE_EXPORT_SEMANTIC_ID = "lua-script:export"
LUA_ARCHIVE_KIND = "dikciz-lua-script"
LUA_ARCHIVE_MANIFEST_FILE_NAME = "archive.json"
LUA_ARCHIVE_METADATA_FILE_NAME = "script.json"
LUA_ARCHIVE_SOURCE_FILE_NAME = "main.lua"
LUA_ARCHIVE_STATE_FILE_NAME = "state.json"
LUA_ARCHIVE_REQUIRED_FILES = (
    LUA_ARCHIVE_MANIFEST_FILE_NAME,
    LUA_ARCHIVE_METADATA_FILE_NAME,
    LUA_ARCHIVE_SOURCE_FILE_NAME,
    LUA_ARCHIVE_STATE_FILE_NAME,
)
LUA_ARCHIVE_SOURCE = 'return { status = "Archive initial text" }'
LUA_ARCHIVE_REPLACEMENT_SOURCE = 'return { status = "Archive replacement text" }'
LUA_ARCHIVE_STATE = {"message": "Archive state"}
LUA_ARCHIVE_TITLE = "Archive fixture"
LUA_ARCHIVE_TOAST_SETTLE_SECONDS = 4
LUA_MANAGER_SCRIPT_INITIAL_TEXT = "Native manager initial text"
LUA_MANAGER_SCRIPT_RECOVERED_TEXT = "Native manager recovered text"
LUA_MANAGER_SCRIPT_STATE = {"message": "Native manager state"}
LUA_MANAGER_SCRIPT_TITLE = "Native manager script"
LUA_MANAGER_SCRIPT_WIDGET_ID = "native-manager-card"
LUA_MANAGER_SCRIPT_WIDGET_SEMANTIC_ID = f"widget:{LUA_MANAGER_SCRIPT_WIDGET_ID}"
LUA_MANAGER_CREATE_SEMANTIC_ID = "lua-script:create"
LUA_MANAGER_CLOSE_SEMANTIC_ID = "lua-script:close"
LUA_MANAGER_DELETE_CONFIRM_SEMANTIC_ID = "lua-script:delete-confirm"
LUA_MANAGER_DELETE_SEMANTIC_ID = "lua-script:delete"
LUA_MANAGER_ENABLED_SEMANTIC_ID = "lua-script:enabled"
LUA_MANAGER_SAVE_SEMANTIC_ID = "lua-script:save"
LUA_MANAGER_SOURCE_SEMANTIC_ID = "lua-script:source"
LUA_MANAGER_STATE_SEMANTIC_ID = "lua-script:state"
LUA_MANAGER_TITLE_SEMANTIC_ID = "lua-script:title"
LUA_MANAGER_SETTINGS_SEMANTIC_ID = "settings:scripts"
LUA_DELETE_CONFIRMATION_ARTIFACT_NAME = "lua-script-delete-confirmation.png"
LUA_DELETE_OVERLAY_SCRIPT_ID = "dialog-delete-fixture"
LUA_DELETE_OVERLAY_SCRIPT_SOURCE = 'return { status = "Delete fixture" }'
LUA_DELETE_OVERLAY_SCRIPT_STATE = {"message": "Delete fixture"}
LUA_DELETE_OVERLAY_SCRIPT_TITLE = "Delete fixture"
LUA_MANAGER_SCRIPT_SOURCE = 'return { status = "Native manager initial text" }'
LUA_MANAGER_SCRIPT_RECOVERED_SOURCE = 'return { status = "Native manager recovered text" }'
LUA_INSTRUCTION_LIMIT_SOURCE = "while true do end"
AUTOMATION_LIVE_POLICY_ID = "automation-live-policy"
AUTOMATION_LIVE_SCRIPT_ID = "automation-live"
AUTOMATION_LIVE_STATE_KEY = "level"
AUTOMATION_LIVE_POLICY_DEVICE_PATH = (
    f"/sdcard/Dikciz/automation/policies/{AUTOMATION_LIVE_POLICY_ID}.json"
)
AUTOMATION_LIVE_SCRIPT_ARTIFACT_NAME = "dikciz-pytest-automation-live.json"
AUTOMATION_LIVE_SCRIPT_DEVICE_PATH = (
    f"/sdcard/Dikciz/automation/scripts/{AUTOMATION_LIVE_SCRIPT_ID}.json"
)
AUTOMATION_COLD_INVALID_CONFIG_ARTIFACT_NAME = "dikciz-pytest-automation-cold-invalid.json"
AUTOMATION_COLD_VALID_CONFIG_ARTIFACT_NAME = "dikciz-pytest-automation-cold-valid-config.json"
AUTOMATION_COLD_VALID_POLICY_ARTIFACT_NAME = "dikciz-pytest-automation-cold-valid-policy.json"
AUTOMATION_COLD_VALID_SCRIPT_ARTIFACT_NAME = "dikciz-pytest-automation-cold-valid-script.json"
AUTOMATION_COLD_VALID_STATE_ARTIFACT_NAME = "dikciz-pytest-automation-cold-valid-state.json"
AUTOMATION_LIVE_STATE_DEVICE_PATH = f"/sdcard/Dikciz/scripts/{AUTOMATION_LIVE_SCRIPT_ID}/state.json"
AUTOMATION_BATTERY_EVENT = "battery"
AUTOMATION_COLD_VALID_BATTERY_COALESCING_KEY = "automation-cold-valid-battery"
AUTOMATION_LIVE_SOURCE = (
    'function on_event(event)\n'
    '  if event.type == "battery" then\n'
    '    return { type = "patchState", values = { level = event.payload.level } }\n'
    '  end\n'
    'end'
)
AUTOMATION_LIVE_INITIAL_STATE = {AUTOMATION_LIVE_STATE_KEY: "waiting"}
AUTOMATION_LIVE_BATTERY_LEVEL = 42
AUTOMATION_COLD_VALID_BATTERY_LEVEL = 43
AUTOMATION_RELOAD_SOURCE = (
    'function on_event(event)\n'
    '  if event.type == "battery" then\n'
    '    return { type = "patchState", values = { batteryLevel = event.payload.level } }\n'
    '  end\n'
    '  if event.type == "charging" then\n'
    '    return { type = "patchState", values = { charging = event.payload.charging } }\n'
    '  end\n'
    'end'
)
AUTOMATION_RELOAD_BATTERY_LEVEL = 37
AUTOMATION_RELOAD_INITIAL_STATE = {"batteryLevel": "waiting", "charging": "waiting"}
AUTOMATION_SENSOR_ACCELERATION_NAME = "acceleration"
AUTOMATION_SENSOR_ACCELERATION_FIRST_VALUE = 1.25
AUTOMATION_SENSOR_ACCELERATION_RECONFIGURATION_COALESCING_KEY = (
    "automation-sensor-acceleration-reconfiguration"
)
AUTOMATION_SENSOR_ACCELERATION_TYPE = 1
AUTOMATION_SENSOR_DELIVERY_INTERVAL_MILLISECONDS = 250
AUTOMATION_SENSOR_GYROSCOPE_NAME = "gyroscope"
AUTOMATION_SENSOR_GYROSCOPE_FIRST_VALUE = 4.25
AUTOMATION_SENSOR_GYROSCOPE_RECONFIGURATION_COALESCING_KEY = (
    "automation-sensor-gyroscope-reconfiguration"
)
AUTOMATION_SENSOR_GYROSCOPE_TYPE = 4
AUTOMATION_SENSOR_GYROSCOPE_VALUES = "4.25:5.5:6.75"
AUTOMATION_SENSOR_INITIAL_STATE = {
    "latitude": "waiting",
    "longitude": "waiting",
    "sensorVectorInjected": False,
}
AUTOMATION_SENSOR_LOCATION_SOURCE = (
    'function on_event(event)\n'
    '  if event.type == "sensor" then\n'
    '    local values = event.payload.values\n'
    '    if math.abs(values[1] - 1.25) < 0.00001 and\n'
    '        math.abs(values[2] - 2.5) < 0.00001 and\n'
    '        math.abs(values[3] - 3.75) < 0.00001 then\n'
    '      return { type = "patchState", values = { sensorVectorInjected = true } }\n'
    '    end\n'
    '  end\n'
    '  if event.type == "location" then\n'
    '    return { type = "patchState", values = {\n'
    '      latitude = event.payload.latitude, longitude = event.payload.longitude,\n'
    '      altitudeVisible = event.payload.altitude ~= nil, bearingVisible = event.payload.bearing ~= nil,\n'
    '      speedVisible = event.payload.speed ~= nil\n'
    '    } }\n'
    '  end\n'
    'end'
)
AUTOMATION_SENSOR_VALUES = "1.25:2.5:3.75"
AUTOMATION_SENSOR_RECONFIGURATION_CAPABILITIES = ["sensors"]
AUTOMATION_SENSOR_RECONFIGURATION_EVENT = "sensor"
AUTOMATION_SENSOR_RECONFIGURATION_INITIAL_STATE = {"gyroscopeSeen": False}
AUTOMATION_SENSOR_RECONFIGURATION_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "sensor" then return end\n'
    '  local values = event.payload.values\n'
    f'  if event.payload.sensorType == {AUTOMATION_SENSOR_ACCELERATION_TYPE} and\n'
    f'      math.abs(values[1] - {AUTOMATION_SENSOR_ACCELERATION_FIRST_VALUE}) < 0.00001 then\n'
    '    return { type = "patchState", values = { accelerationSeen = true } }\n'
    '  end\n'
    f'  if event.payload.sensorType == {AUTOMATION_SENSOR_GYROSCOPE_TYPE} and\n'
    f'      math.abs(values[1] - {AUTOMATION_SENSOR_GYROSCOPE_FIRST_VALUE}) < 0.00001 then\n'
    '    return { type = "patchState", values = { gyroscopeSeen = true } }\n'
    '  end\n'
    'end'
)
AUTOMATION_SENSOR_RESTORED_VALUES = "0:0:0"
AUTOMATION_LOCATION_APPROXIMATE_LATITUDE = 46.7712
AUTOMATION_LOCATION_APPROXIMATE_LONGITUDE = 23.5926
AUTOMATION_COARSE_LOCATION_CAPABILITIES = ["locationApproximate", "locationPrecise"]
AUTOMATION_COARSE_LOCATION_APPROXIMATE_COALESCING_KEY = "automation-location-coarse-approximate"
AUTOMATION_COARSE_LOCATION_APPROXIMATE_STATE_KEY = "approximateReceived"
AUTOMATION_COARSE_LOCATION_PRECISE_COALESCING_KEY = "automation-location-coarse-precise"
AUTOMATION_COARSE_LOCATION_PRECISE_STATE_KEY = "preciseReceived"
AUTOMATION_COARSE_LOCATION_INITIAL_STATE = {
    AUTOMATION_COARSE_LOCATION_APPROXIMATE_STATE_KEY: False,
    AUTOMATION_COARSE_LOCATION_PRECISE_STATE_KEY: False,
    "latitude": "waiting",
    "longitude": "waiting",
}
AUTOMATION_COARSE_LOCATION_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "location" then return end\n'
    '  if event.payload.altitude == nil then\n'
    '    return { type = "patchState", values = {\n'
    f'      {AUTOMATION_COARSE_LOCATION_APPROXIMATE_STATE_KEY} = true,\n'
    '      latitude = event.payload.latitude, longitude = event.payload.longitude\n'
    '    } }\n'
    '  end\n'
    f'  return {{ type = "patchState", values = {{ {AUTOMATION_COARSE_LOCATION_PRECISE_STATE_KEY} = true }} }}\n'
    'end'
)
AUTOMATION_LOCATION_COORDINATE_TOLERANCE = 0.00001
AUTOMATION_LOCATION_EVENT_RETRY_SECONDS = 1.25
AUTOMATION_LOCATION_PERMISSION_MISSING_REASON = "permission_missing"
AUTOMATION_LOCATION_PRECISE_LATITUDE = 46.1234
AUTOMATION_LOCATION_PRECISE_LONGITUDE = 23.9876
AUTOMATION_LOCATION_RECEIVED_EVENT = "automation_location_received"
AUTOMATION_LOCATION_REGISTRATION_SKIPPED_EVENT = "automation_location_registration_skipped"
AUTOMATION_MEDIA_SESSION_ACTIONS = ["patchState", "mediaControl"]
AUTOMATION_MEDIA_SESSION_CAPABILITIES = [
    "mediaSessionsMetadata",
    "mediaSessionsContent",
]
AUTOMATION_MEDIA_SESSION_COALESCING_KEY = "automation-media-session"
AUTOMATION_MEDIA_SESSION_CONTROL_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "mediaSession" then return end\n'
    '  local contentVisible = event.payload.title ~= nil and event.payload.artist ~= nil\n'
    '  if event.payload.playbackState == 2 then\n'
    '    return {\n'
    '      { type = "patchState", values = { contentVisible = contentVisible, playing = false } },\n'
    '      { type = "mediaControl", packageName = "eu.psyb0t.dikciz.fixture", command = "play" }\n'
    '    }\n'
    '  end\n'
    '  return { type = "patchState", values = { contentVisible = contentVisible, playing = true } }\n'
    'end'
)
AUTOMATION_MEDIA_SESSION_INITIAL_STATE = {"contentVisible": "waiting", "playing": "waiting"}
AUTOMATION_MEDIA_SESSION_COMMAND_INTERVAL_MILLISECONDS = 60_000
AUTOMATION_MEDIA_SESSION_METADATA_CAPABILITIES = ["mediaSessionsMetadata"]
AUTOMATION_MEDIA_SESSION_METADATA_SOURCE = (
    'function on_event(event)\n'
    '  if event.type == "mediaSession" then\n'
    '    return { type = "patchState", values = { contentVisible = event.payload.title ~= nil } }\n'
    '  end\n'
    'end'
)
AUTOMATION_MEDIA_SESSION_STATUS_TIMEOUT_SECONDS = 10
AUTOMATION_MEDIA_SESSION_TEST_ACTIVITY = ".FixtureMediaSessionActivity"
AUTOMATION_PATCH_STATE_ACTIONS = ["patchState"]
AUTOMATION_NOTIFICATION_INITIAL_STATE = {"hasText": "waiting", "hasTitle": "waiting"}
AUTOMATION_NOTIFICATION_CAPABILITIES = ["notificationsMetadata"]
AUTOMATION_NOTIFICATION_COALESCING_KEY = "automation-notification-posted"
AUTOMATION_NOTIFICATION_DISABLED_STATE = "waiting"
AUTOMATION_NOTIFICATION_EVENT = "notificationPosted"
AUTOMATION_NOTIFICATION_SOURCE = (
    'function on_event(event)\n'
    '  if event.type == "notificationPosted" then\n'
    '    return { type = "patchState", values = {\n'
    '      hasTitle = event.payload.title ~= nil, hasText = event.payload.text ~= nil\n'
    '    } }\n'
    '  end\n'
    'end'
)
AUTOMATION_NOTIFICATION_SOURCE_PACKAGE = "com.android.shell"
AUTOMATION_NOTIFICATION_TEXT = "automation-private-text"
AUTOMATION_NOTIFICATION_TITLE = "automation-private-title"
AUTOMATION_NOTIFICATION_UNAVAILABLE_TAG = "automation-listener-disabled"
AUTOMATION_SCREEN_CAPABILITIES = ["screen"]
AUTOMATION_SCREEN_COALESCING_KEY = "automation-screen"
AUTOMATION_SCREEN_EVENT = "screen"
AUTOMATION_SCREEN_INITIAL_STATE = {"screenOn": "waiting"}
AUTOMATION_SCREEN_RATE_LIMIT_MILLISECONDS = 60_000
AUTOMATION_SCREEN_SOURCE = (
    'function on_event(event)\n'
    '  if event.type == "screen" then\n'
    '    return { type = "patchState", values = { screenOn = event.payload.screenOn } }\n'
    '  end\n'
    'end'
)
AUTOMATION_DENIED_SCREEN_SOURCE = (
    'function on_event(event)\n'
    '  if event.type == "screen" then\n'
    '    return { type = "selectPage", pageId = "notes" }\n'
    '  end\n'
    'end'
)
AUTOMATION_SCREEN_WAKE_KEYCODE = 224
AUTOMATION_SCREEN_TOGGLE_KEYCODE = 26
CONFIG_INVALID_ARTIFACT_NAME = "dikciz-pytest-invalid-config.json"
CONFIG_INVALID_CONTENT = "{invalid"
CONFIGURATION_RELOAD_REJECTED_EVENT = "configuration_reload_rejected"
CONFIGURATION_RELOAD_SETTLE_SECONDS = 1.0
CONFIGURATION_SAVE_TIMEOUT_SECONDS = 10
DEFAULT_THEME_WIDGET_MARGIN_DP = 4
THEME_ARTIFACT_NAME = "pytest-violet.json"
THEME_CUSTOM_ID = "pytest-violet"
THEME_CUSTOM_TITLE = "Pytest Violet"
THEME_DEVICE_DIRECTORY = "/sdcard/Dikciz/themes"
THEME_WALLPAPER_ARTIFACT_NAME = "pytest-wallpaper.png"
THEME_WALLPAPER_COLOR = bytes((43, 89, 151, 255))
THEME_WALLPAPER_PNG_BIT_DEPTH = 8
THEME_WALLPAPER_PNG_COLOR_TYPE_RGBA = 6
THEME_WALLPAPER_PNG_COMPRESSION_METHOD = 0
THEME_WALLPAPER_PNG_CRC_MASK = 0xFFFFFFFF
THEME_WALLPAPER_PNG_FILTER_METHOD = 0
THEME_WALLPAPER_PNG_FILTER_NONE = b"\x00"
THEME_WALLPAPER_PNG_INTERLACE_METHOD = 0
THEME_WALLPAPER_PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
THEME_WALLPAPER_DEVICE_DIRECTORY = "/sdcard/Dikciz/wallpapers"
THEME_WALLPAPER_HEIGHT = 32
THEME_WALLPAPER_WIDTH = 32
THEME_INVALID_ARTIFACT_NAME = "pytest-invalid.json"
THEME_INVALID_ID = "pytest-invalid"
THEME_INVALID_TITLE = "Pytest invalid theme"
THEME_REBEL_COLOR = "#3A0A52"
THEME_SCREENSHOT_ARTIFACT_NAME = "theme-pytest-violet.png"
THEME_SETTINGS_SEMANTIC_ID = "settings:themes"
THEME_SEMANTIC_ID = f"theme:{THEME_CUSTOM_ID}"
CONTROL_PLANE_RESTART_TIMEOUT_SECONDS = 10
CONTROL_PLANE_RETRY_INTERVAL_SECONDS = 0.1
DELETE_ACTION_SEMANTIC_ID = "widget:welcome:delete"
DESKCLOCK_CATEGORY_LABEL = "Clock"
DESKCLOCK_APP_PACKAGE = "com.google.android.deskclock"
DESKCLOCK_BACK_NAVIGATION_ARTIFACT_NAME = "deskclock-back-navigation.png"
DESKCLOCK_PROVIDER_AUTOMATION_ACTION_ARTIFACT_NAME = "deskclock-provider-automation-action.png"
DESKCLOCK_CONFIGURATION_FACE_DESCRIPTION_PREFIX = "Clock face "
DESKCLOCK_PROVIDER_LABEL = "Analog"
DESKCLOCK_LIVE_RESIZE_ARTIFACT_NAME = "deskclock-provider-live-resize.png"
DESKCLOCK_TWO_AXIS_TARGET_ARTIFACT_NAME = "deskclock-two-axis-target.png"
WIDGET_PICKER_APPLICATION_DESCRIPTION = "Browse {} widgets"
WIDGET_PICKER_ENTRY_DESCRIPTION = "Add {} widget"
WIDGET_PICKER_PROVIDER_ENTRY_DESCRIPTION = "Add {} widget from {}"
WIDGET_PICKER_PROVIDER_PREVIEW_ARTIFACT_NAME = "widget-picker-deskclock-preview.png"
WIDGET_PICKER_PROVIDER_PREVIEW_DESCRIPTION = "Preview of {} widget"
DESKCLOCK_LIVE_RESIZE_UI_DUMP_ARTIFACT_NAME = "deskclock-provider-live-resize.xml"
DESKCLOCK_RESIZED_ARTIFACT_NAME = "deskclock-provider-resized.png"
DESKCLOCK_RECONFIGURED_ARTIFACT_NAME = "deskclock-provider-reconfigured.png"
CONFIGURABLE_PROVIDER_FIXTURE_APPLICATION_LABEL = "Configurable widget fixture"
CONFIGURABLE_PROVIDER_FIXTURE_ACTIONS_ARTIFACT_NAME = "configurable-provider-actions.png"
CONFIGURABLE_PROVIDER_FIXTURE_APK_ENVIRONMENT = "DIKCIZ_TEST_CONFIGURABLE_PROVIDER_APK"
CONFIGURABLE_PROVIDER_FIXTURE_APK_PATH = (
    "tests/configurable-widget-provider/app/build/outputs/apk/debug/app-debug.apk"
)
CONFIGURABLE_PROVIDER_FIXTURE_CANCEL_LABEL = "Cancel"
CONFIGURABLE_PROVIDER_FIXTURE_CONFIGURATION_ARTIFACT_NAME = "configurable-provider-configuration.png"
CONFIGURABLE_PROVIDER_FIXTURE_EXPECTED_PACKAGE = "eu.psyb0t.dikciz.fixture"
CONFIGURABLE_PROVIDER_FIXTURE_PACKAGE_ENVIRONMENT = "DIKCIZ_TEST_CONFIGURABLE_PROVIDER_PACKAGE"
CONFIGURABLE_PROVIDER_FIXTURE_PRIMARY_ACTION_RESOURCE_ID_SUFFIX = ":id/fixture_primary_action"
CONFIGURABLE_PROVIDER_FIXTURE_PRIMARY_STATUS = "Fixture primary action completed"
CONFIGURABLE_PROVIDER_FIXTURE_PROVIDER_LABEL = "Configurable fixture"
CONFIGURABLE_PROVIDER_FIXTURE_SAVE_LABEL = "Save fixture widget"
CONFIGURABLE_PROVIDER_FIXTURE_SECONDARY_ACTION_RESOURCE_ID_SUFFIX = ":id/fixture_secondary_action"
CONFIGURABLE_PROVIDER_FIXTURE_SECONDARY_STATUS = "Fixture secondary action completed"
CONFIGURABLE_PROVIDER_FIXTURE_TEST_NAME = "test_real_configurable_provider_cancel_and_reconfiguration_preserve_identity"
CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_ACTIONS_ARTIFACT_NAME = "configurable-provider-media-actions.png"
CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_NEXT_ACTION_RESOURCE_ID_SUFFIX = ":id/fixture_media_next_action"
CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_PAUSED_TRACK_ONE_STATUS = "Fixture media paused, track 1"
CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_PAUSED_TRACK_TWO_STATUS = "Fixture media paused, track 2"
CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_PLAYING_TRACK_ONE_STATUS = "Fixture media playing, track 1"
CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_PLAYING_TRACK_TWO_STATUS = "Fixture media playing, track 2"
CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_PLAY_PAUSE_ACTION_RESOURCE_ID_SUFFIX = (
    ":id/fixture_media_play_pause_action"
)
CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_PREVIOUS_ACTION_RESOURCE_ID_SUFFIX = (
    ":id/fixture_media_previous_action"
)
CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_ACTION_RESOURCE_ID_SUFFIXES = (
    CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_PREVIOUS_ACTION_RESOURCE_ID_SUFFIX,
    CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_PLAY_PAUSE_ACTION_RESOURCE_ID_SUFFIX,
    CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_NEXT_ACTION_RESOURCE_ID_SUFFIX,
)
CONFIGURABLE_PROVIDER_FIXTURE_WEATHER_RECOVERED_STATUS = "Fixture weather recovered, 18 C"
CONFIGURABLE_PROVIDER_FIXTURE_WEATHER_REFRESH_ACTION_RESOURCE_ID_SUFFIX = (
    ":id/fixture_weather_refresh_action"
)
CONFIGURABLE_PROVIDER_FIXTURE_WEATHER_UNAVAILABLE_STATUS = "Fixture weather is unavailable"
CONFIGURABLE_PROVIDER_RECONFIGURED_ARTIFACT_NAME = "configurable-provider-reconfigured.png"
HOME_DESTINATION_NOTES_ARTIFACT_NAME = "home-destination-notes-selected.png"
DEVICE_DENSITY_PATTERN = re.compile(r"(?:Physical|Override) density: (?P<density>\d+)")
DEVICE_INPUT_SPACE_ESCAPE = "%s"
DEVICE_NIGHT_MODE_PATTERN = re.compile(r"Night mode: (?P<mode>yes|no|auto)")
DEVICE_SETTING_ABSENT_VALUE = "null"
DEVICE_SETTING_ACCELEROMETER_ROTATION = "accelerometer_rotation"
DEVICE_SETTING_FONT_SCALE = "font_scale"
DEVICE_SETTING_USER_ROTATION = "user_rotation"
DEVICE_SETTING_VALUE_PATTERN = re.compile(r"(?:[0-9]+(?:\.[0-9]+)?|null)")
DEVICE_SETTINGS_SYSTEM_NAMESPACE = "system"
DEVICE_UI_MODE_NIGHT_COMMAND = "cmd uimode night"
DEVICE_UI_MODE_NIGHT_SET_COMMAND = "cmd uimode night {}"
DEVICE_UI_MODE_NIGHT_VALUES = frozenset({"yes", "no", "auto"})
DIRECT_BOTTOM_EDGE_DRAG_DURATION_MILLISECONDS = 750
DIRECT_BOTTOM_EDGE_INSET_PIXELS = 6
DIRECT_HELD_LONG_PRESS_MILLISECONDS = 30_000
DIRECT_HELD_PAGE_SWIPE_MILLISECONDS = 1_000
DIRECT_LONG_PRESS_MILLISECONDS = 700
DIRECT_PAGE_SELECTION_VISIBILITY_SECONDS = 0.75
DIRECT_SWIPE_DURATION_MILLISECONDS = 350
DIRECT_TEXT_INPUT_SETTLE_SECONDS = 0.2
VERTICAL_PAGE_SWIPE_DISTANCE_DP = 64
DEVICE_AUTOMATION_PORT = 19001
DEVICE_MCP_PORT = 19002
DIKCIZ_DEBUG_ACTIVITY = "org.fossify.home.dikciz.DikcizHomeActivity"
DIKCIZ_DEBUG_APK_PATH = "app/build/outputs/apk/dikciz/debug/launcher-16-dikciz-debug.apk"
DIKCIZ_DEBUG_PACKAGE = "eu.psyb0t.dikciz.launcher.debug"
SETTINGS_ACTIVITY_COMPONENT = "com.android.settings/.Settings"
AUTOMATION_NOTIFICATION_COMPONENT = (
    f"{DIKCIZ_DEBUG_PACKAGE}/org.fossify.home.dikciz.DikcizNotificationListenerService"
)
AUTOMATION_SERVICE_COMPONENT = (
    f"{DIKCIZ_DEBUG_PACKAGE}/org.fossify.home.dikciz.DikcizAutomationService"
)
AUTOMATION_SERVICE_CONFIGURATION_REJECTED_EVENT = "automation_service_configuration_rejected"
AUTOMATION_SERVICE_QUERY_COMMAND = f"dumpsys activity services {DIKCIZ_DEBUG_PACKAGE}"
AUTOMATION_SERVICE_SYNC_RESULT_KEY = "automationServiceSynced"
EVENT_LOG_ARTIFACT_NAME = "dikciz-event-log.jsonl"
CONFIGURATION_FILE_CHANGED_EVENT = "configuration_file_changed"
AUDIT_LOGGING_SECTION = "logging"
AUDIT_LOGGING_LEVEL_KEY = "level"
AUDIT_DEBUG_LOG_LEVEL = "debug"
AUDIT_CONFIGURATION_ARTIFACT_NAME = "audit-config.json"
DEVICE_CONFIGURATION_PATH = "/sdcard/Dikciz/config.json"
LIVE_TEST_DIRECTORY = Path(__file__).resolve().parent
TESTS_DIRECTORY = LIVE_TEST_DIRECTORY.parent
FIXTURE_CONFIGURATION_FILE = TESTS_DIRECTORY / "fixtures" / "dikciz-ui-fixture.json"
FIXTURE_CONFIGURATION_VERSION = 4
FIXTURE_ACTIVITY_PAGE_ID = "activity"
FIXTURE_HOME_PAGE_ID = "home"
FIXTURE_NOTES_CARD_TEXT = "Fixture notes card"
FIXTURE_NOTES_PAGE_ID = "notes"
PAGE_MENU_ACTION_ARTIFACT_NAME = "page-menu-semantic-actions.png"
PAGE_MENU_ACTION_PREFIX = "page:menu:"
PAGE_MENU_ACTION_ROLE = "button"
PAGE_MENU_ACTIONS_FOR_WIDGET_PAGE = (
    "widgets",
    "set-home",
    "lock",
    "widget-locks",
    "commands",
    "rename",
    "settings",
)
PAGE_MENU_NOTES_ACTION_SEMANTIC_IDS = tuple(
    f"{PAGE_MENU_ACTION_PREFIX}{FIXTURE_NOTES_PAGE_ID}:{action}"
    for action in PAGE_MENU_ACTIONS_FOR_WIDGET_PAGE
)
PAGE_MENU_NOTES_SET_HOME_SEMANTIC_ID = (
    f"{PAGE_MENU_ACTION_PREFIX}{FIXTURE_NOTES_PAGE_ID}:set-home"
)
AUTOMATION_ACTION_ALARM_COMPONENT = (
    f"{DIKCIZ_DEBUG_PACKAGE}/org.fossify.home.dikciz.DikcizAutomationAlarmReceiver"
)
AUTOMATION_ACTION_ALARM_EVENT = "org.fossify.home.dikciz.AUTOMATION_ALARM"
AUTOMATION_ALARM_MINIMUM_INTERVAL_MILLISECONDS = 60_000
AUTOMATION_ACTION_ALARM_COALESCING_KEY = "automation-actions-alarm"
AUTOMATION_ACTION_BATTERY_COALESCING_KEY = "automation-actions-battery"
AUTOMATION_ACTION_BATTERY_LEVEL = AUTOMATION_LIVE_BATTERY_LEVEL
AUTOMATION_ACTION_BATTERY_STATE_KEY = "batteryAction"
AUTOMATION_ACTION_CAPABILITIES = ["battery", "alarm"]
AUTOMATION_ACTION_COMPLETED_EVENT = "automation_action_completed"
AUTOMATION_ACTION_EXPLICIT_BROADCAST_STATE_KEY = "explicitBroadcastDelivered"
AUTOMATION_ACTION_HTML_WIDGET_ID = "focus"
AUTOMATION_ACTION_HTML_WIDGET_ADDRESS = f"1H1V-{AUTOMATION_ACTION_HTML_WIDGET_ID}"
AUTOMATION_ACTION_NOTIFICATION_TEXT = "Automation action notification"
AUTOMATION_ACTION_NOTIFICATION_TITLE = "Dikciz automation action"
AUTOMATION_ACTION_SOURCE = (
    'function on_event(event)\n'
    f'  if event.type == "battery" and event.payload.level == {AUTOMATION_ACTION_BATTERY_LEVEL} '
    f'and context.state.{AUTOMATION_ACTION_BATTERY_STATE_KEY} ~= true then\n'
    '    return {\n'
    f'      {{ type = "selectPage", pageId = "{FIXTURE_NOTES_PAGE_ID}" }},\n'
    f'      {{ type = "patchWidget", widgetAddress = "{AUTOMATION_ACTION_HTML_WIDGET_ADDRESS}", '
    'values = { state = { checked = true } } },\n'
    f'      {{ type = "patchState", values = {{ {AUTOMATION_ACTION_BATTERY_STATE_KEY} = true }} }},\n'
    f'      {{ type = "launchApp", component = "{SETTINGS_ACTIVITY_COMPONENT}" }},\n'
    f'      {{ type = "postNotification", title = "{AUTOMATION_ACTION_NOTIFICATION_TITLE}", '
    f'text = "{AUTOMATION_ACTION_NOTIFICATION_TEXT}" }},\n'
    f'      {{ type = "explicitIntent", intentType = "broadcast", '
    f'component = "{AUTOMATION_ACTION_ALARM_COMPONENT}", '
    f'action = "{AUTOMATION_ACTION_ALARM_EVENT}" }}\n'
    '    }\n'
    '  end\n'
    '  if event.type == "alarm" then\n'
    f'    return {{ type = "patchState", values = {{ {AUTOMATION_ACTION_EXPLICIT_BROADCAST_STATE_KEY} = true }} }}\n'
    '  end\n'
    'end'
)
AUTOMATION_ACTION_INITIAL_STATE = {
    AUTOMATION_ACTION_BATTERY_STATE_KEY: False,
    AUTOMATION_ACTION_EXPLICIT_BROADCAST_STATE_KEY: False,
}
AUTOMATION_ACTIONS = [
    "selectPage",
    "patchWidget",
    "patchState",
    "launchApp",
    "postNotification",
    "explicitIntent",
]
AUTOMATION_ALARM_CAPABILITIES = ["alarm"]
AUTOMATION_ALARM_COALESCING_KEY = "automation-alarm"
AUTOMATION_ALARM_DELIVERY_STATE_KEY = "delivery"
AUTOMATION_ALARM_EVENT = "alarm"
AUTOMATION_ALARM_FIRST_DELIVERY = "first"
AUTOMATION_ALARM_INITIAL_STATE = {AUTOMATION_ALARM_DELIVERY_STATE_KEY: "waiting"}
AUTOMATION_ALARM_RESCHEDULED_DELIVERY = "rescheduled"
AUTOMATION_ALARM_SOURCE = (
    'function on_event(event)\n'
    f'  if event.type == "{AUTOMATION_ALARM_EVENT}" then\n'
    f'    return {{ type = "patchState", values = {{ {AUTOMATION_ALARM_DELIVERY_STATE_KEY} = "'
    f'{AUTOMATION_ALARM_FIRST_DELIVERY}" }} }}\n'
    '  end\n'
    'end'
)
AUTOMATION_ALARM_RESCHEDULED_SOURCE = (
    'function on_event(event)\n'
    f'  if event.type == "{AUTOMATION_ALARM_EVENT}" then\n'
    f'    return {{ type = "patchState", values = {{ {AUTOMATION_ALARM_DELIVERY_STATE_KEY} = "'
    f'{AUTOMATION_ALARM_RESCHEDULED_DELIVERY}" }} }}\n'
    '  end\n'
    'end'
)
AUTOMATION_ALARM_TIMEOUT_SECONDS = 150
FIXTURE_THIRD_COLUMN_PAGE_ID = "third-column"
FIXTURE_THIRD_COLUMN_PAGE_TITLE = "Third column"
FIXTURE_THIRD_ROW_PAGE_ID = "third-row"
FIXTURE_THIRD_ROW_PAGE_TITLE = "Third row"
LAUNCHER_CONTROL_OPEN_DESCRIPTION = "Open Dikciz launcher controls"
LAUNCHER_CONTROL_APP_DRAWER_LABEL = "Open app drawer"
LAUNCHER_CONTROL_ADD_TO_PAGE_LABEL = "Add to this page"
LAUNCHER_CONTROL_MANAGE_PAGE_LABEL = "Manage this page"
LAUNCHER_CONTROL_SETTINGS_LABEL = "Open Dikciz settings"
LAUNCHER_CONTROL_SHEET_TITLE = "Launcher controls"
HOME_GESTURE_TRAVEL_PIXELS = 700
HOME_GESTURE_DURATION_MILLISECONDS = 260
HOME_CLEAR_TASK_COMMAND = (
    "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME "
    "--activity-clear-task"
)
ANDROID_SETTINGS_LABEL_QUERY = "Settings"
APP_DRAWER_UNMATCHABLE_QUERY = "zzzznotanapp"
SHORTCUT_PAGE_ID = "shortcut-target"
SHORTCUT_PAGE_TITLE = "Shortcut target"
SHORTCUT_PAGE_COLUMN = 2
APP_ACTION_TYPE_UNINSTALL = "uninstall"
APP_ACTION_FORCE_STOP_LABEL = "Force stop"
APP_ACTION_LAUNCH_LABEL = "Launch app"
APP_ACTIONS_OPEN_ATTEMPTS = 3
APP_ACTION_FORCE_STOP_SEMANTIC_SUFFIX = "force-stop"
APP_ACTION_INVALID_COMPONENT = "not a component"
VALIDATION_FAILED_ERROR_CODE = "validation_failed"
APP_WIDGET_TYPE = "app"
# Rejected home chrome: a raw page coordinate such as "H1/2" or "V 1 / 2".
HOME_CHROME_COORDINATE_PATTERN = re.compile(r"\b[HV]\s*\d+\s*/\s*\d+\b")
DIKCIZ_AUTOMATION_BUTTON_TEXT = "Manage Android automation access"
DIALOG_SCROLL_ATTEMPTS = 8
DIALOG_SCROLL_TRAVEL_PIXELS = 600
DIALOG_SCROLL_DURATION_MILLISECONDS = 220
WIDGET_MOVE_TRAVEL_PIXELS = 320
WIDGET_MOVE_FREE_ROW_OFFSET = 2
WIDGET_MOVE_DURATION_MILLISECONDS = 600
# Pages are addressed by their 1H1V coordinate everywhere in the product, so every
# surface that prints a page name prints that address beside it.
FIRST_SCRIPT_PAGE_COORDINATE = 1
PAGE_MENU_TITLE_FORMAT = "Page: {} ({})"
PAGE_SEARCH_ENTRY_FORMAT = "{} ({})"
PAGE_SEARCH_OPEN_DESCRIPTION = "Search Dikciz pages"
PAGE_SEARCH_SHEET_TITLE = "Go to page"
PAGE_SEARCH_EMPTY_LABEL = "No page matches that search."
PAGE_SEARCH_SEMANTIC_ID = "launcher:page-search"
PAGE_SEARCH_QUERY_SEMANTIC_ID = "launcher:page-search:query"
PAGE_SEARCH_EMPTY_SEMANTIC_ID = "launcher:page-search:empty"
PAGE_SEARCH_PAGE_SEMANTIC_PREFIX = "launcher:page-search:page:"
PAGE_SEARCH_UNMATCHABLE_QUERY = "zzzznotapage"
PAGE_MENU_SECTION_ADD = "Add to this page"
PAGE_MENU_SECTION_PAGE = "Manage this page"
PAGE_MENU_SECTION_LAUNCHER = "Launcher"
PAGE_MENU_ADD_WIDGET_LABEL = "Add a widget"
PAGE_MENU_OPEN_SETTINGS_LABEL = "Open Dikciz settings"
SETTINGS_FIRST_CONTROL_TEXT = "Appearance defaults"
SETTINGS_LAST_CONTROL_TEXT = "Restart in safe mode"
AUTOMATION_LAST_CONTROL_TEXT = "Open app permission settings"
STARTER_ANDROID_SETTINGS_TITLE = "Android Settings"
STARTER_RECOVERY_SETUP_LABEL = "Set up access"
STARTER_RECOVERY_RETRY_LABEL = "Retry"
STARTER_NOTIFY_LABEL = "Send notification"
APP_DRAWER_SEMANTIC_ID = "drawer"
APP_DRAWER_SEARCH_DESCRIPTION = "Search apps by name or package"
APP_DRAWER_SEARCH_SEMANTIC_ID = "drawer:search"
APP_DRAWER_EMPTY_SEMANTIC_ID = "drawer:empty"
APP_DRAWER_CLOSE_SEMANTIC_ID = "drawer:close"
APP_DRAWER_APP_PREFIX = "drawer:app:"
APP_DRAWER_APP_ACTIONS_PREFIX = "drawer:app-actions:"
APP_ACTION_SEMANTIC_PREFIX = "app-action:"
APP_ACTION_CONFIRM_SEMANTIC_ID = "app-action:confirm"
APP_ACTION_ROOT_APP_INFO_SEMANTIC_ID = "app-action:root-app-info"
APP_ACTION_ADD_SHORTCUT_LABEL = "Add shortcut"
APP_DRAWER_SETTINGS_ACTIONS_DESCRIPTION = "More actions for Settings"
LAUNCHER_CONTROL_SEMANTIC_ID = "launcher:controls"
LAUNCHER_CONTROL_APP_DRAWER_SEMANTIC_ID = "launcher:controls:app-drawer"
LAUNCHER_CONTROL_ADD_TO_PAGE_SEMANTIC_ID = "launcher:controls:add-to-page"
LAUNCHER_CONTROL_MANAGE_PAGE_SEMANTIC_ID = "launcher:controls:manage-page"
LAUNCHER_CONTROL_SETTINGS_SEMANTIC_ID = "launcher:controls:dikciz-settings"
STARTUP_DISMISS_SEMANTIC_ID = "startup:dismiss"
ANDROID_SETTINGS_PACKAGE = "com.android.settings"
ANDROID_SETTINGS_COMPONENT = "com.android.settings/com.android.settings.Settings"
APP_ACTION_OUTCOME_APP_INFO_OPENED = "app_info_opened"
APP_ACTION_OUTCOME_LAUNCHED = "launched"
APP_ACTION_OUTCOME_ROOT_UNAVAILABLE = "root_unavailable"
APP_ACTION_OUTCOME_SHORTCUT_ADDED = "shortcut_added"
APP_ACTION_TYPE_APP_INFO = "appInfo"
APP_ACTION_TYPE_ADD_SHORTCUT = "addShortcut"
APP_ACTION_TYPE_FORCE_STOP = "forceStop"
APP_ACTION_TYPE_LAUNCH = "launch"
APP_CATALOGUE_COMMAND = "appCatalogue"
APP_ACTION_COMMAND = "appAction"
OPEN_AUTOMATION_SETUP_COMMAND = "openAutomationSetup"
DIKCIZ_SETTINGS_TITLE = "Dikciz settings"
DIKCIZ_AUTOMATION_TITLE_TEXT = "Android automation"
WIDGET_MOVE_ACTION_LABEL = "Move"
APP_DRAWER_TITLE = "Apps"
AUTOMATION_SETTINGS_CLOSE_SEMANTIC_ID = "settings:automation:close"
STARTER_NOTES_PAGE_ID = "notes"
STARTER_HOME_PAGE_ID = "home"
STARTER_HOME_WIDGET_ID = "command-deck"
STARTER_HOME_WIDGET_TITLE = "Dikciz control deck"
FIXTURE_WIDGET_IDS = ("welcome", "focus", "fixture-detail")
FIXTURE_WIDGET_TITLES = {
    "welcome": "Dikciz",
    "focus": "Focus mode",
    "fixture-detail": "Fixture detail",
}
DIKCIZ_HOME_ROLE_REMOVE_COMMAND = (
    f"cmd role remove-role-holder --user 0 android.app.role.HOME {DIKCIZ_DEBUG_PACKAGE}"
)
FONT_SCALE_BASELINE = "1.0"
FONT_SCALE_ENLARGED = "1.3"
LANDSCAPE_USER_ROTATION = "1"
HORIZONTAL_PAGE_INDICATOR_DESCRIPTION_PREFIX = "Horizontal pages. Page "
HORIZONTAL_PAGE_INDICATOR_SEMANTIC_ID = "page:navigation:horizontal"
PAGE_INDICATOR_DOT_GAP_DP = 14
PAGE_INDICATOR_SELECTED_TITLE_FRAGMENT = ": {}."
PAGE_INDICATOR_THICKNESS_DP = 32
DEVICE_UI_TIMEOUT_SECONDS = 10
WEBSOCKET_EVENT_COMMAND_COMPLETED = "automation_command_completed"
WEBSOCKET_EVENT_OUTCOME_REJECTED = "rejected"
WEBSOCKET_EVENT_OUTCOME_SUCCEEDED = "succeeded"
WEBSOCKET_EVENT_REJECTED_CODE = "validation_failed"
WEBSOCKET_EVENT_SCREEN_HOME = "home"
WEBSOCKET_EVENT_UI_RENDERED = "automation_ui_rendered"
WEBSOCKET_EVENT_UNKNOWN_PAGE_ID = "missing-page"
PAGE_SCROLL_RESOURCE_ID = "eu.psyb0t.dikciz.launcher.debug:id/dikciz_page_scroll"
PAGE_SCROLL_SEMANTIC_ID = "pageScroll"
WAIT_FOR_TIMEOUT_MILLISECONDS = 500
HANDLE_INSET_PIXELS = 18
KEYCODE_BACK = 4
KEYCODE_DELETE = 67
KEYCODE_HOME = 3
KEYCODE_OVERVIEW = 187
LIFECYCLE_PERSISTED_TITLE = "Survives process death"
NAVIGATION_BAR_HEIGHT_PIXELS = 126
NAVIGATION_HOME_X_NUMERATOR = 1
NAVIGATION_HOME_X_DENOMINATOR = 2
NAVIGATION_OVERVIEW_X_NUMERATOR = 3
NAVIGATION_OVERVIEW_X_DENOMINATOR = 4
PRIVATE_VNC_HOST = "emulator"
PRIVATE_VNC_PORT = 5900
RFB_CLIENT_SHARED = b"\x01"
RFB_KEY_DOWN = 1
RFB_KEY_EVENT_TYPE = 4
RFB_POINTER_BUTTON_LEFT = 1
RFB_POINTER_EVENT_TYPE = 5
RFB_POINTER_NO_BUTTON = 0
RFB_PROTOCOL_VERSION = b"RFB 003.008\n"
RFB_SECURITY_NONE = 1
RFB_SERVER_INITIALIZATION_BYTES = 24
RFB_TIMEOUT_SECONDS = 10
RFB_POINTER_CLICK_HOLD_SECONDS = 0.05
RFB_XK_ESCAPE = 0xFF1B
RECENTS_ACTIVITY_CLASS_NAME = "com.android.quickstep.RecentsActivity"
RECENTS_PANEL_RESOURCE_ID_SUFFIX = ":id/overview_panel"
MCP_PORT = int(os.environ.get("ANDROID_LAB_MCP_HOST_PORT", "19002"))
MCP_REMOVED_WIDGET_CONFIG_GET_REQUEST_ID = 440
MCP_REMOVED_WIDGET_CONFIG_REPLACE_REQUEST_ID = 441
MCP_SEED_RESET_CONFIG_GET_REQUEST_ID = 450
MCP_SEED_RESET_REQUEST_ID = 451
MCP_SEED_RESET_WAIT_REQUEST_ID = 452
MCP_SEED_RESET_RESET_REQUEST_ID = 453
MCP_CASE_INSENSITIVE_CONFIG_GET_REQUEST_ID = 460
MCP_CASE_INSENSITIVE_WIDGET_REPLACE_REQUEST_ID = 461
MCP_CASE_INSENSITIVE_PAGE_REPLACE_REQUEST_ID = 462
MCP_PROVIDER_AUTOMATION_SNAPSHOT_REQUEST_ID = 470
MCP_WIDGET_REFERENCE_CONFIG_GET_REQUEST_ID = 442
MCP_WIDGET_REFERENCE_CONFIG_REPLACE_REQUEST_ID = 443
MCP_STYLE_LOCK_CONFIG_GET_REQUEST_ID = 430
MCP_STYLE_LOCK_CONFIG_REPLACE_REQUEST_ID = 431
MCP_STYLE_LOCK_CONFIG_RESTORE_REQUEST_ID = 432
MAXIMUM_STYLE_DIMENSION_DP = 512
DIRECT_DRAG_PIXELS = 72
NATIVE_GRID_COLUMNS = 4
NATIVE_GRID_ROWS = 6
NATIVE_GRID_GAP_DP = 8
NATIVE_GRID_OUTER_PADDING_DP = 12
WIDGET_CELL_FIELDS = ("column", "row", "columnSpan", "rowSpan")
LOG_DIRECTORY = "/sdcard/Dikciz/logs"
LOG_FORBIDDEN_VALUES = (
    "Fixture notes card",
    "Pushed through ADB",
    AUTOMATION_NOTIFICATION_TEXT,
    AUTOMATION_NOTIFICATION_TITLE,
)
LOG_RECORD_REQUIRED_FIELDS = frozenset({"component", "event", "level", "timestamp"})
WIDGET_BIND_EVENTS = frozenset(
    {
        "widget_bind_approval_requested",
        "widget_bind_failed",
        "widget_bind_rejected",
    },
)
LOG_FOCUSED_REQUIRED_EVENTS = frozenset(
    {
        "activity_created",
        "activity_started",
        "automation_command_completed",
        "automation_command_received",
        "automation_configuration_replaced",
        "configuration_file_changed",
        "configuration_reload_completed",
        "home_rendered",
        "logging_configured",
    },
)
LOG_FULL_SUITE_REQUIRED_EVENTS = frozenset(
    {
        "activity_created",
        "activity_started",
        "app_catalogue_loaded",
        "app_shortcut_created",
        "app_shortcut_retargeted",
        "app_widget_edited",
        "automation_command_completed",
        "automation_command_received",
        "automation_configuration_replaced",
        "automation_long_pressed",
        "automation_shell_completed",
        "automation_shell_started",
        "automation_tapped",
        "automation_text_set",
        "configuration_file_changed",
        "configuration_reload_completed",
        "configuration_reload_started",
        "configuration_save_completed",
        "configuration_save_started",
        "command_sheet_filtered",
        "command_sheet_opened",
        "home_rendered",
        "logging_configured",
        "mcp_tool_completed",
        "mcp_tool_received",
        "page_indicator_interaction",
        "page_created",
        "page_deleted",
        "page_menu_opened",
        "page_moved",
        "page_selected",
        "page_selection_started",
        "provider_widget_created",
        "provider_widget_edited",
        "provider_widget_host_created",
        "provider_widget_ids_removed",
        "provider_widget_reconfiguration_completed",
        "provider_widget_reconfiguration_requested",
        "provider_widget_size_reported",
        "widget_deleted",
        "widget_edit_actions_shown",
        "widget_insertion_revealed",
        "widget_moved",
        "widget_move_started",
        "widget_picker_opened",
        "widget_resize_filled",
        "widget_resized",
        "widget_resize_started",
        "widget_resize_handle_selected",
        "widget_resize_pointer_updated",
        "theme_applied",
        "theme_catalogue_loaded",
        "theme_catalogue_rejected",
    },
)
NATIVE_WIDGET_EDIT_LABEL = "Edit"
NATIVE_APP_EDITED_TITLE = "Pinned settings"
NATIVE_APP_EDIT_WIDGET_ID = "native-app-edit"
NATIVE_APP_EDIT_CELL = {"column": 0, "row": 2, "columnSpan": 1, "rowSpan": 1}
NATIVE_APP_RETARGET_LABEL = DESKCLOCK_CATEGORY_LABEL
NATIVE_APP_SHORTCUT_DISPLAY_STYLE_CASES = (
    (
        "icon_label",
        "iconLabel",
        "Icon with label",
        "native-app-shortcut-icon-label.png",
    ),
    (
        "text_button",
        "button",
        "Text button",
        "native-app-shortcut-button.png",
    ),
    (
        "icon_label_button",
        "buttonIconLabel",
        "Icon and label button",
        "native-app-shortcut-icon-label-button.png",
    ),
)
# The rail edge creates at the extremity of the whole home, so the new page lands past
# the last existing column or row rather than beside the selected page.
PAGE_CREATE_DELETE_CASES = (
    pytest.param("horizontal", {"column": 2, "row": 0}, id="horizontal"),
    pytest.param("vertical", {"column": 0, "row": 2}, id="vertical"),
)
NATIVE_APP_WIDGET_TITLE_INPUT_DESCRIPTION = "App shortcut title"
NATIVE_PROVIDER_EDITED_TITLE = "Edited clock"
NATIVE_PROVIDER_WIDGET_TITLE_INPUT_DESCRIPTION = "Android widget title"
NATIVE_PROVIDER_RECONFIGURE_LABEL = "Reconfigure"
UNAVAILABLE_PROVIDER_APP_WIDGET_ID = 1
UNAVAILABLE_PROVIDER_CELL = {"column": 0, "row": 1, "columnSpan": 4, "rowSpan": 1}
UNAVAILABLE_PROVIDER_COMPONENT = "org.example.unavailable/.Provider"
UNAVAILABLE_PROVIDER_ID = "unavailable-provider"
UNAVAILABLE_PROVIDER_LOCKED = False
UNAVAILABLE_PROVIDER_PLACEHOLDER = "Widget is unavailable"
UNAVAILABLE_PROVIDER_TITLE = "Unavailable provider"
UNAVAILABLE_PROVIDER_ARTIFACT_NAME = "unavailable-provider.png"
NATIVE_WIDGET_PICKER_ENTRY_DESCRIPTION = "Add {label} widget"
WIDGET_PICKER_APPS_CATEGORY_LABEL = "Apps"
WIDGET_PICKER_DIKCIZ_CATEGORY_LABEL = "Dikciz"
WIDGET_PICKER_LEGACY_DEBUG_CATEGORY_LABEL = "Launcher_debug"
PAGE_MENU_DELETE_LABEL = "Delete this page"
PAGE_MENU_SET_HOME_LABEL = "Make this the home page"
PAGE_MENU_WIDGET_LOCKS_LABEL = "Change widget locks"
PHYSICAL_BOUNDS_TOLERANCE_PIXELS = 2
SYSTEM_CONFIGURATION_ARTIFACT_NAME = "system-configuration-changes.png"
SYSTEM_ROTATION_LOCKED = "0"
# Matches the launcher's WIDGET_TOP_EDGE_MOVE_GESTURE_HEIGHT_DP and the 24Dp
# grab strip the configuration and user guide documents.
TOP_EDGE_MOVE_GESTURE_HEIGHT_DP = 24
SEEDED_NOTES_WIDGET_ID = "notes-intro"
SETTINGS_APP_LABEL = "Settings"
TEXT_WIDGET_CANCELLED_TITLE = "Cancelled title"
TEXT_WIDGET_CANCELLED_VALUE = "Cancelled body"
SETTINGS_HOME_TITLE_SEMANTIC_ID = "settings:page:home:title"
SETTINGS_NOTES_TITLE_SEMANTIC_ID = "settings:page:notes:title"
SETTINGS_RESET_CONFIRM_SEMANTIC_ID = "settings:reset-confirm"
SETTINGS_RESET_SEMANTIC_ID = "settings:reset"
SETTINGS_SAFE_MODE_SEMANTIC_ID = "settings:safe-mode"
SETTINGS_SAVE_PAGES_SEMANTIC_ID = "settings:save-pages"
SETTINGS_WIDGET_APPEARANCE_SEMANTIC_ID = "settings:appearance:widget"
SAFE_MODE_EXIT_SEMANTIC_ID = "safeMode:exit"
SAFE_MODE_MESSAGE = "Safe mode uses the bundled home. Your config stays untouched."
SAFE_MODE_RESET_SEMANTIC_ID = "safeMode:reset"
SETTINGS_RENAMED_PAGE_TITLE = "Renamed through settings"
TEST_HARNESS_NAMESPACE = "testHarness"
TEST_HARNESS_NAMESPACE_VALUE = {"retained": "yes"}
VERTICAL_PAGE_INDICATOR_DESCRIPTION_PREFIX = "Vertical pages. Page "
VERTICAL_PAGE_INDICATOR_SEMANTIC_ID = "page:navigation:vertical"
PROVIDER_AUTOMATION_HOST_SEMANTIC_SUFFIX = ":provider"
PROVIDER_AUTOMATION_ROLE = "appWidget"
PROVIDER_RENDER_TIMEOUT_SECONDS = 15
RESIZE_ACTION_SEMANTIC_ID = "widget:welcome:resize"
RESIZE_HANDLE_POINT_RADIUS_DP = 6
HTML_RESIZE_OVERFLOW_ARTIFACT_NAME = "html-resize-overflow.png"
HTML_RESIZE_OVERFLOW_CSS = (
    ".resize-overflow{box-sizing:border-box;display:block;width:200%;height:200%;"
    "padding:16px;background:#d32f2f;color:#ffffff;font:600 18px system-ui,sans-serif}"
)
HTML_RESIZE_OVERFLOW_HTML = '<div class="resize-overflow">Resize overlay boundary</div>'
HTML_RESIZE_OVERFLOW_MARKER = "Resize overlay boundary"
RESIZE_SELECTION_ARTIFACT_NAME = "resize-selection.png"
RESIZE_FILL_HEIGHT_SEMANTIC_ID = "widget:welcome:resize-fill-height"
RESIZE_FILL_WIDTH_SEMANTIC_ID = "widget:welcome:resize-fill-width"
WELCOME_LOCK_INPUT_DESCRIPTION = "Lock Dikciz"
WELCOME_MOVE_HANDLE_SEMANTIC_ID = "widget:welcome:move-handle"
WELCOME_MOVE_ACTION_SEMANTIC_ID = "widget:welcome:move"
WIDGET_MOVE_HANDLE_DESCRIPTION = "Drag {} to move"
WIDGET_REFERENCE_FIRST_ID = "my-widget-1"
WIDGET_REFERENCE_FIRST_LOCATION = "1H1V"
WIDGET_REFERENCE_FIRST_RENAMED_TITLE = "Renamed Widget"
WIDGET_REFERENCE_FIRST_TITLE = "My Widget"
WIDGET_REFERENCE_FIRST_VALUE = "First reference body"
WIDGET_REFERENCE_ACTIVITY_LOCATION = "1H2V"
WIDGET_REFERENCE_SECOND_ID = "my-widget-2"
WIDGET_REFERENCE_SECOND_LOCATION = "2H1V"
WIDGET_REFERENCE_SECOND_TITLE = "My Widget"
WIDGET_REFERENCE_SECOND_VALUE = "Second reference body"
WIDGET_REFERENCE_TYPE = "text"
UTC_DATE_PATTERN = re.compile(r"\d{4}-\d{2}-\d{2}")
