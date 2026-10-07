SHELL := /bin/bash

ANDROID_LAB_VERSION ?= 0.13.0
.DEFAULT_GOAL := help
DEV_IMAGE ?= android-lab:$(ANDROID_LAB_VERSION)
EMULATOR_IMAGE ?= android-lab:$(ANDROID_LAB_VERSION)-emulator-api36
ANDROID_API ?= 36
ANDROID_PROJECT ?= .
GRADLE_TASK ?= :app:assembleCoreDebug
ANDROID_LAB_FORWARD_HOST_PORT ?= 19001
ANDROID_LAB_FORWARD_DEVICE_PORT ?= 19001
ANDROID_LAB_MCP_HOST_PORT ?= 19002
ANDROID_LAB_MCP_DEVICE_PORT ?= 19002
ANDROID_LAB_INSTANCE ?= shared
DIKCIZ_AUTOMATION_MUTATE ?= false
DIKCIZ_MCP_MUTATE ?= false
DIKCIZ_TEST_FIXTURE ?= tests/fixtures/dikciz-ui-fixture.json
ANDROID_LAB_VNC_HOST_PORT ?= 61326
DIKCIZ_TEST_SELECTOR ?=
DIKCIZ_GUIDE_CAPTURE ?=
DIKCIZ_TEST_INTERRUPT_AFTER_PREPARE ?= false
DIKCIZ_TEST_RESET_ONLY ?= false
DIKCIZ_TEST_PROVIDER_INVENTORY_ONLY ?= false
DIKCIZ_TEST_DEVICE_ADMIN_SETTINGS_ONLY ?= false
DIKCIZ_TEST_INSPECT_ONLY ?= false
DIKCIZ_CONFIGURABLE_PROVIDER_APK := tests/configurable-widget-provider/app/build/outputs/apk/debug/app-debug.apk
DIKCIZ_CONFIGURABLE_PROVIDER_PACKAGE := eu.psyb0t.dikciz.fixture
DIKCIZ_MEDIA_SESSION_SECONDARY_FIXTURE_APK := tests/configurable-widget-provider/app/build-secondary/outputs/apk/debug/app-debug.apk
DIKCIZ_MEDIA_SESSION_SECONDARY_FIXTURE_BUILD_DIRECTORY := build-secondary
DIKCIZ_MEDIA_SESSION_SECONDARY_FIXTURE_PACKAGE := eu.psyb0t.dikciz.fixture.secondary
DIKCIZ_CONFIGURABLE_PROVIDER_SELECTOR := test_real_configurable_provider_cancel_and_reconfiguration_preserve_identity
DIKCIZ_CONFIGURABLE_PROVIDER_ACTIONS_SELECTOR := test_provider_actions_refresh_semantic_ids_after_remote_views_update
DIKCIZ_CONFIGURABLE_PROVIDER_MEDIA_SELECTOR := test_provider_media_controls_rotate_semantic_ids_and_dispatch_in_order
DIKCIZ_CONFIGURABLE_PROVIDER_WEATHER_SELECTOR := test_provider_weather_refresh_recovers_from_unavailable_state
DIKCIZ_MEDIA_SESSION_SELECTOR := test_automation_media_session
DIKCIZ_MEDIA_VOLUME_SELECTOR := test_automation_media_volume_is_policy_gated_and_reads_back_the_real_emulator_index
DIKCIZ_BLUETOOTH_SELECTOR := test_automation_bluetooth_state_requires_permission_and_tracks_real_emulator_changes
DIKCIZ_THERMAL_STATUS_SELECTOR := test_automation_thermal_status_is_policy_gated_and_uses_the_real_emulator_listener
DIKCIZ_POWER_SAVE_SELECTOR := test_automation_power_save_mode_is_policy_gated_and_uses_the_real_emulator_state
DIKCIZ_DEVICE_IDLE_SELECTOR := test_automation_device_idle_mode_is_policy_gated_and_uses_the_real_emulator_state
DIKCIZ_NIGHT_MODE_SELECTOR := test_automation_night_mode_is_policy_gated_and_uses_the_real_emulator_state
DIKCIZ_DEVICE_CONFIGURATION_SELECTOR := test_automation_device_configuration_is_policy_gated_and_uses_the_real_emulator_state
DIKCIZ_PACKAGE_LIFECYCLE_SELECTOR := test_automation_package_lifecycle_is_policy_gated_and_uses_real_android_broadcasts
DIKCIZ_RINGER_MODE_SELECTOR := test_automation_ringer_mode_is_policy_gated_and_uses_the_real_emulator_state
DIKCIZ_INTERRUPTION_FILTER_SELECTOR := test_automation_interruption_filter_requires_notification_policy_access_and_tracks_real_emulator_changes
DIKCIZ_CALENDAR_SELECTOR := test_automation_calendar_events_require_permission_and_redact_content_by_policy
DIKCIZ_CONTACTS_SELECTOR := test_automation_contacts_changed_requires_permission_and_uses_the_real_provider
DIKCIZ_MANUAL_TRIGGER_SELECTOR := test_manual_trigger_is_policy_gated_targeted_and_available_through_both_control_planes
DIKCIZ_PHONE_STATE_SELECTOR := test_automation_phone_state_requires_permission_and_tracks_debug_platform_states
DIKCIZ_SMS_SELECTOR := test_automation_sms_reception_requires_permission_and_redacts_content_by_policy
DIKCIZ_SEND_SMS_SELECTOR := test_send_sms_action_is_permission_and_policy_gated
DIKCIZ_HEALTH_CONNECT_SELECTOR := test_automation_health_daily_steps_is_bounded_policy_gated_and_available_through_both_control_planes
DIKCIZ_DEVICE_ADMINISTRATION_SELECTOR := test_automation_device_administration_is_opt_in_policy_gated_and_uses_real_device_policy_manager
DIKCIZ_CLIPBOARD_SELECTOR := test_automation_clipboard_is_foreground_only_policy_gated_redacted_and_bounded
DIKCIZ_NOTIFICATION_ACTION_SELECTOR := test_automation_notification_action_is_allowlisted_single_use_and_listener_gated
DIKCIZ_SCRIPT_DIAGNOSTICS_SELECTOR := test_script_failures_are_logged_viewable_notified_and_rate_limited
DIKCIZ_CONFIGURATION_ERROR_SELECTOR := test_fatal_configuration_error_is_inspectable_and_recovers_through_both_local_planes
DIKCIZ_STORAGE_ACCESS_SELECTOR := test_storage_access_handoff_is_semantic_and_configuration_is_still_gated
DIKCIZ_PAGE_PROVISIONAL_SELECTOR := test_page_canvas_and_custom_rail_edges_are_semantic_and_reversible
DIKCIZ_REMOTE_BEARER_AUTH_SELECTOR := test_phone_managed_remote_bearer_auth_controls_websocket_and_mcp
DIKCIZ_SYSTEM_SOURCES_SELECTOR := test_automation_time_user_presence_and_connectivity_events_use_isolated_android_wifi
DIKCIZ_APP_DRAWER_ENTRY_SELECTOR := test_app_drawer_opens_from_the_visible_control_and_the_upward_home_gesture
DIKCIZ_APP_DRAWER_SEARCH_SELECTOR := test_app_drawer_search_matches_label_and_package_and_shows_an_empty_state
DIKCIZ_APP_DRAWER_LAUNCH_SELECTOR := test_app_drawer_row_tap_launches_the_selected_android_app
DIKCIZ_APP_CATALOGUE_SELECTOR := test_app_catalogue_is_ordered_bounded_and_carries_stable_ids_and_labels
DIKCIZ_APP_SHORTCUT_SELECTOR := test_app_action_add_shortcut_persists_a_native_app_widget
DIKCIZ_APP_INFO_SELECTOR := test_app_action_app_info_opens_the_real_android_package_details_screen
DIKCIZ_APP_FORCE_STOP_SELECTOR := test_app_action_force_stop_reports_root_unavailable_and_offers_app_info
DIKCIZ_LAUNCHER_CONTROL_SELECTOR := test_launcher_control_is_visible_on_a_full_page_and_exposes_every_route
DIKCIZ_HOME_CHROME_SELECTOR := test_home_chrome_carries_no_developer_page_coordinates
DIKCIZ_CONTROL_PLACEMENT_SELECTOR := test_bottom_launcher_control_clears_the_page_canvas_and_the_indicator
DIKCIZ_PAGE_MENU_SELECTOR := test_page_menu_is_titled_grouped_and_uses_verb_labels
DIKCIZ_PAGE_SEARCH_SELECTOR := test_page_search
DIKCIZ_GUIDE_CAPTURE_SELECTOR := test_capture_user_guide_screenshots
DIKCIZ_APP_GROUPS_SELECTOR := test_app_groups
DIKCIZ_GRID_PLACEMENT_SELECTOR := test_add_widget_takes_the_first_fitting_free_cells
DIKCIZ_GRID_PAGE_FULL_SELECTOR := test_a_full_page_refuses_insertion_and_writes_nothing
DIKCIZ_GRID_SETTINGS_SELECTOR := test_page_grid_change_persists_and_is_reported
DIKCIZ_GRID_CONFLICT_SELECTOR := test_page_grid_change_that_orphans_a_widget_is_rejected_whole
DIKCIZ_GRID_ROTATION_SELECTOR := test_rotation_round_trip_keeps_selected_page_cells_unchanged
DIKCIZ_WIDGET_MOVE_SELECTOR := test_widget_action_sheet_exposes_a_visible_move_action
DIKCIZ_DIALOG_REACHABILITY_SELECTOR := test_settings_and_automation_expose_their_last_control_to_a_real_touch
DIKCIZ_STARTER_CLEANUP_SELECTOR := test_targeted_run_cleanup_restores_the_bundled_starter_home
DIKCIZ_STARTER_RECOVERY_SELECTOR := test_bundled_starter_names_android_settings_and_offers_setup_and_retry
DIKCIZ_ACCESSIBILITY_SELECTOR := test_accessibility_service_inspects_and_acts_on_a_real_external_android_window_through_both_control_planes
DIKCIZ_ACCESSIBILITY_THIRD_PARTY_SELECTOR := test_accessibility_service_acts_on_a_real_third_party_window_through_both_control_planes
DIKCIZ_UPSTREAM_DIR ?= .research_files/FossifyOrg-Launcher
DIKCIZ_UPSTREAM_BACKUP_DIR ?= .android-lab/fossify-refresh
DIKCIZ_UPSTREAM_REFRESH ?= 0
DIKCIZ_TEST_CONFIGURABLE_PROVIDER_APK ?=
DIKCIZ_TEST_CONFIGURABLE_PROVIDER_PACKAGE ?=
DIKCIZ_TEST_MEDIA_SESSION_SECONDARY_FIXTURE_APK ?=
DIKCIZ_TEST_MEDIA_SESSION_SECONDARY_FIXTURE_PACKAGE ?=
DIKCIZ_MEDIA_SESSION_FIXTURE_APPLICATION_ID ?=
DIKCIZ_MEDIA_SESSION_FIXTURE_BUILD_DIRECTORY ?=
REAL_TEST_ENV_FILE ?= .env
ANDROID_LAB_FORCE_NAVIGATION_REBOOT ?= false
ANDROID_LAB_TOOL_CPU_LIMIT ?= $(shell docker info --format '{{.NCPU}}' | awk -v maximum=10 '{print ($$1 < maximum ? $$1 : maximum)}')
override ANDROID_LAB_TOOL_MEMORY_LIMIT := 8g
override ANDROID_LAB_TOOL_PIDS_LIMIT := 2048
ANDROID_LAB_GRADLE_MAX_WORKERS ?= $(shell printf '%s\n' '$(ANDROID_LAB_TOOL_CPU_LIMIT)' | awk '{print ($$1 < 1 ? 1 : int($$1))}')
override ANDROID_LAB_BUILD_CPU_PERIOD := 100000
override ANDROID_LAB_BUILD_CPU_QUOTA := 1000000
override ANDROID_LAB_BUILD_MEMORY_LIMIT := 8g
override ANDROID_LAB_BUILD_PROCESS_LIMIT := 2048
ANDROID_LAB_STATE_DIR := .android-lab
ANDROID_LAB_LICENSE_MARKER := $(ANDROID_LAB_STATE_DIR)/licenses/android-sdk-license.accepted
ANDROID_LAB_ACCEPT_ANDROID_LICENSES ?= $(if $(wildcard $(ANDROID_LAB_LICENSE_MARKER)),yes,)
PACKAGE_NAME ?=
ACTIVITY ?=
DEVICE_COMMAND ?=
DEVICE_FILE ?=
ARTIFACT_FILE ?=
EMULATOR_GEO_LATITUDE ?=
EMULATOR_GEO_LONGITUDE ?=
EMULATOR_SENSOR_NAME ?=
EMULATOR_SENSOR_VALUES ?=
EMULATOR_SMS_SENDER ?=
EMULATOR_SMS_BODY ?=
EMULATOR_POWER_AC ?=
EMULATOR_WIFI_STATE ?=
EMULATOR_BLUETOOTH_STATE ?=
EMULATOR_THERMAL_STATE ?=
EMULATOR_POWER_SAVE_MODE ?=
EMULATOR_DEVICE_IDLE_MODE ?=
EMULATOR_NIGHT_MODE ?=
EMULATOR_DEVICE_ORIENTATION ?=
EMULATOR_FONT_SCALE_PERCENT ?=
EMULATOR_RINGER_MODE ?=
EMULATOR_NOTIFICATION_POLICY_ACCESS ?=
EMULATOR_INTERRUPTION_FILTER ?=
UID := $(shell id -u)
GID := $(shell id -g)
DOCKER_SOCKET := /var/run/docker.sock
DOCKER_GID := $(shell stat -c '%g' $(DOCKER_SOCKET) 2>/dev/null || echo 0)

export ANDROID_PROJECT GRADLE_TASK
export ANDROID_LAB_FORWARD_HOST_PORT ANDROID_LAB_FORWARD_DEVICE_PORT
export ANDROID_LAB_MCP_HOST_PORT ANDROID_LAB_MCP_DEVICE_PORT
export ANDROID_LAB_VNC_HOST_PORT
export ANDROID_LAB_INSTANCE DIKCIZ_TEST_FIXTURE
export DIKCIZ_AUTOMATION_MUTATE DIKCIZ_MCP_MUTATE
export DIKCIZ_GUIDE_CAPTURE
export DIKCIZ_TEST_SELECTOR DIKCIZ_TEST_INTERRUPT_AFTER_PREPARE DIKCIZ_TEST_RESET_ONLY DIKCIZ_TEST_PROVIDER_INVENTORY_ONLY DIKCIZ_TEST_DEVICE_ADMIN_SETTINGS_ONLY DIKCIZ_TEST_INSPECT_ONLY
export DIKCIZ_UPSTREAM_DIR DIKCIZ_UPSTREAM_BACKUP_DIR DIKCIZ_UPSTREAM_REFRESH
export DIKCIZ_CONFIGURABLE_PROVIDER_APK DIKCIZ_CONFIGURABLE_PROVIDER_PACKAGE
export DIKCIZ_TEST_CONFIGURABLE_PROVIDER_APK DIKCIZ_TEST_CONFIGURABLE_PROVIDER_PACKAGE
export DIKCIZ_MEDIA_SESSION_SECONDARY_FIXTURE_APK DIKCIZ_MEDIA_SESSION_SECONDARY_FIXTURE_PACKAGE
export DIKCIZ_MEDIA_SESSION_SECONDARY_FIXTURE_BUILD_DIRECTORY
export DIKCIZ_TEST_MEDIA_SESSION_SECONDARY_FIXTURE_APK DIKCIZ_TEST_MEDIA_SESSION_SECONDARY_FIXTURE_PACKAGE
export DIKCIZ_MEDIA_SESSION_FIXTURE_APPLICATION_ID DIKCIZ_MEDIA_SESSION_FIXTURE_BUILD_DIRECTORY
export ANDROID_LAB_FORCE_NAVIGATION_REBOOT
export ANDROID_LAB_GRADLE_MAX_WORKERS
export APK TEST_APK TEST_RUNNER
export PACKAGE_NAME ACTIVITY DEVICE_COMMAND DEVICE_FILE ARTIFACT_FILE
export EMULATOR_GEO_LATITUDE EMULATOR_GEO_LONGITUDE EMULATOR_SENSOR_NAME EMULATOR_SENSOR_VALUES EMULATOR_SMS_SENDER EMULATOR_SMS_BODY EMULATOR_POWER_AC EMULATOR_WIFI_STATE
export EMULATOR_BLUETOOTH_STATE EMULATOR_THERMAL_STATE EMULATOR_POWER_SAVE_MODE EMULATOR_DEVICE_IDLE_MODE EMULATOR_NIGHT_MODE EMULATOR_DEVICE_ORIENTATION EMULATOR_FONT_SCALE_PERCENT EMULATOR_RINGER_MODE EMULATOR_NOTIFICATION_POLICY_ACCESS EMULATOR_INTERRUPTION_FILTER

STATE_DIRECTORY := $(shell realpath -m "$(CURDIR)/.android-lab")

DEV_RUN := docker run --rm --init \
	--user $(UID):$(GID) \
	--cpus $(ANDROID_LAB_TOOL_CPU_LIMIT) \
	--memory $(ANDROID_LAB_TOOL_MEMORY_LIMIT) \
	--memory-swap $(ANDROID_LAB_TOOL_MEMORY_LIMIT) \
	--pids-limit $(ANDROID_LAB_TOOL_PIDS_LIMIT) \
	-e HOME=/tmp \
	-e ANDROID_LAB_WORKSPACE=/work \
	-e GRADLE_USER_HOME=/work/.android-lab/gradle \
	-e ANDROID_PROJECT \
	-e GRADLE_TASK \
	-e ANDROID_LAB_GRADLE_MAX_WORKERS \
	-e DIKCIZ_UPSTREAM_DIR \
	-e DIKCIZ_UPSTREAM_BACKUP_DIR \
	-e DIKCIZ_UPSTREAM_REFRESH \
	-e DIKCIZ_MEDIA_SESSION_FIXTURE_APPLICATION_ID \
	-e DIKCIZ_MEDIA_SESSION_FIXTURE_BUILD_DIRECTORY \
	-v "$(CURDIR):/work" \
	-v "$(STATE_DIRECTORY):/work/.android-lab" \
	-w /work \
	$(DEV_IMAGE)

# Only lifecycle and device targets get the Docker socket. They do not receive
# host ADB state, USB devices, or host networking.
DEV_RUN_DIND := docker run --rm --init \
	--user $(UID):$(GID) \
	--group-add $(DOCKER_GID) \
	--cpus $(ANDROID_LAB_TOOL_CPU_LIMIT) \
	--memory $(ANDROID_LAB_TOOL_MEMORY_LIMIT) \
	--memory-swap $(ANDROID_LAB_TOOL_MEMORY_LIMIT) \
	--pids-limit $(ANDROID_LAB_TOOL_PIDS_LIMIT) \
	-e DOCKER_HOST=unix:///var/run/docker.sock \
	-e ANDROID_LAB_WORKSPACE="$(CURDIR)" \
	-e ANDROID_LAB_EXTENSION=scripts/dikciz-lab.sh \
	-e ANDROID_LAB_COMPOSE_PROJECT \
	-e ANDROID_LAB_ACCESSIBILITY_SERVICE_CLASS=org.fossify.home.dikciz.DikcizAccessibilityAutomationService \
	-e HOME=/tmp \
	-e ANDROID_LAB_DEV_IMAGE=$(DEV_IMAGE) \
	-e ANDROID_LAB_EMULATOR_IMAGE=$(EMULATOR_IMAGE) \
	-e ANDROID_LAB_UID=$(UID) \
	-e ANDROID_LAB_GID=$(GID) \
	-e ANDROID_LAB_API=$(ANDROID_API) \
	-e ANDROID_LAB_ACCEPT_ANDROID_LICENSES=$(ANDROID_LAB_ACCEPT_ANDROID_LICENSES) \
	-e ANDROID_LAB_FORWARD_HOST_PORT \
	-e ANDROID_LAB_FORWARD_DEVICE_PORT \
	-e ANDROID_LAB_MCP_HOST_PORT \
	-e ANDROID_LAB_MCP_DEVICE_PORT \
	-e ANDROID_LAB_VNC_HOST_PORT \
	-e ANDROID_LAB_INSTANCE \
	-e DIKCIZ_TEST_FIXTURE \
	-e DIKCIZ_TEST_SELECTOR \
	-e DIKCIZ_GUIDE_CAPTURE \
	-e DIKCIZ_TEST_INTERRUPT_AFTER_PREPARE \
	-e DIKCIZ_UPSTREAM_DIR \
	-e DIKCIZ_UPSTREAM_BACKUP_DIR \
	-e DIKCIZ_UPSTREAM_REFRESH \
	-e DIKCIZ_TEST_CONFIGURABLE_PROVIDER_APK \
	-e DIKCIZ_TEST_CONFIGURABLE_PROVIDER_PACKAGE \
	-e DIKCIZ_TEST_MEDIA_SESSION_SECONDARY_FIXTURE_APK \
	-e DIKCIZ_TEST_MEDIA_SESSION_SECONDARY_FIXTURE_PACKAGE \
	-e ANDROID_LAB_FORCE_NAVIGATION_REBOOT \
	-e DIKCIZ_AUTOMATION_MUTATE \
	-e DIKCIZ_MCP_MUTATE \
	-e APK \
	-e TEST_APK \
	-e TEST_RUNNER \
	-e PACKAGE_NAME \
	-e ACTIVITY \
	-e DEVICE_COMMAND \
	-e DEVICE_FILE \
	-e ARTIFACT_FILE \
	-e EMULATOR_GEO_LATITUDE \
	-e EMULATOR_GEO_LONGITUDE \
	-e EMULATOR_SENSOR_NAME \
	-e EMULATOR_SENSOR_VALUES \
	-e EMULATOR_SMS_SENDER \
	-e EMULATOR_SMS_BODY \
	-e EMULATOR_POWER_AC \
	-e EMULATOR_WIFI_STATE \
	-e EMULATOR_BLUETOOTH_STATE \
	-e EMULATOR_THERMAL_STATE \
	-e EMULATOR_POWER_SAVE_MODE \
	-e EMULATOR_DEVICE_IDLE_MODE \
	-e EMULATOR_NIGHT_MODE \
	-e EMULATOR_DEVICE_ORIENTATION \
	-e EMULATOR_FONT_SCALE_PERCENT \
	-e EMULATOR_RINGER_MODE \
	-e EMULATOR_NOTIFICATION_POLICY_ACCESS \
	-e EMULATOR_INTERRUPTION_FILTER \
	-e LOG_FILE \
	-v "$(CURDIR):$(CURDIR)" \
	-v "$(STATE_DIRECTORY):$(CURDIR)/.android-lab" \
	-v "$(DOCKER_SOCKET):$(DOCKER_SOCKET)" \
	-w "$(CURDIR)" \
	$(DEV_IMAGE)

.PHONY: help dev-image shell lint format test audit-compose image run restart stop status \
	device-info apk-install screenshot uiautomator-dump ui-layout uiautomator-run \
	app-current app-start app-stop device-shell emulator-geo-fix emulator-sensor-set emulator-sms-send emulator-power-set emulator-wifi-set emulator-bluetooth-set emulator-thermal-set emulator-power-save-set emulator-device-idle-set emulator-night-mode-set emulator-device-orientation-set emulator-font-scale-set emulator-ringer-mode-set emulator-notification-policy-access-set emulator-interruption-filter-set emulator-calendar-fixture-create emulator-calendar-fixture-update emulator-calendar-fixture-delete emulator-contacts-fixture-create emulator-contacts-fixture-delete dikciz-accessibility-on dikciz-accessibility-off gradle app-build app-test app-lint \
	home-role-set home-start navigation-buttons \
	adb-pull adb-push forward unforward test-emulator test-forward test-vnc \
	dikciz-automation-smoke dikciz-mcp-smoke dikciz-control-ready dikciz-run dikciz-reset dikciz-test dikciz-test-existing-apk dikciz-test-interrupt dikciz-test-provider-actions dikciz-test-provider-media dikciz-test-provider-weather dikciz-test-media-sessions dikciz-test-media-volume dikciz-test-bluetooth dikciz-test-thermal-status dikciz-test-power-save-mode dikciz-test-device-idle dikciz-test-night-mode dikciz-test-device-configuration dikciz-test-package-lifecycle dikciz-test-ringer-mode dikciz-test-interruption-filter dikciz-test-calendar dikciz-test-contacts dikciz-test-manual-trigger dikciz-test-phone-state dikciz-test-sms dikciz-test-send-sms dikciz-test-health-connect dikciz-test-clipboard dikciz-test-notification-action dikciz-test-script-diagnostics dikciz-test-configuration-error dikciz-test-storage-access dikciz-test-page-provisional-controls dikciz-test-remote-bearer-auth dikciz-test-system-sources dikciz-test-app-drawer-entry dikciz-test-app-drawer-search dikciz-test-app-drawer-launch dikciz-test-app-catalogue dikciz-test-app-shortcut dikciz-test-app-info dikciz-test-app-force-stop dikciz-test-launcher-control dikciz-test-home-chrome dikciz-test-control-placement dikciz-test-page-menu dikciz-test-page-search dikciz-test-app-groups dikciz-guide-capture dikciz-test-grid-placement dikciz-test-grid-page-full dikciz-test-grid-settings dikciz-test-grid-conflict dikciz-test-grid-rotation dikciz-test-widget-move dikciz-test-dialog-reachability dikciz-test-starter-cleanup dikciz-test-starter-recovery dikciz-test-accessibility dikciz-test-accessibility-third-party dikciz-upstream-compare dikciz-upstream-refresh-dry-run dikciz-upstream-refresh dikciz-test-real dikciz-test-real-existing-apk dikciz-test-real-reset dikciz-test-real-inspect dikciz-test-real-device-admin-settings dikciz-test-real-provider-inventory dikciz-test-real-configurable-provider dikciz-talkback-on dikciz-talkback-off dikciz-test-stop ensure-dev-image emulator-ready clean

help: ## List every Android lab command
	@grep -E '^[a-zA-Z_-]+:.*## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*## "}; {printf "%-22s %s\n", $$1, $$2}' | sort

.PHONY: build check-images control test-tooling ci-build version
ci-build: ## CI-only bootstrap of the pinned lab tooling, then compile Dikciz
	@ANDROID_LAB_VERSION=$(ANDROID_LAB_VERSION) bash scripts/ci-build.sh
version: ## Print the launcher repository version
	@cat VERSION
test-tooling: audit-compose ## Check embedded lab contracts and reversible upstream refresh
	@$(DEV_RUN) bash scripts/test-upstream-refresh.sh
check-images: emulator-ready ## Check both local lab images and print setup instructions if missing
build: GRADLE_TASK := :app:assembleDikcizDebug
build: gradle ## Build the Dikciz debug APK using the local lab image
control: ensure-dev-image ## Run the Dikciz control client, passing arguments through CONTROL_ARGS
	@$(subst --init,--init --network host -e DIKCIZ_REMOTE_BEARER_TOKEN,$(DEV_RUN)) python3 scripts/dikciz-control.py $(CONTROL_ARGS)

dev-image: ensure-dev-image ## Verify the required local lab image

emulator-ready: ensure-dev-image ## Verify the required local emulator image
	@docker image inspect "$(EMULATOR_IMAGE)" >/dev/null 2>&1 || { printf '%s\n' "Missing local image: $(EMULATOR_IMAGE)" "Build it: https://github.com/psyb0t/android-lab#build-the-images-locally" >&2; exit 1; }

ensure-dev-image: ## Verify the required local lab image without pulling or rebuilding
	@docker image inspect "$(DEV_IMAGE)" >/dev/null 2>&1 || { printf '%s\n' "Missing local image: $(DEV_IMAGE)" "Build it: https://github.com/psyb0t/android-lab#build-the-images-locally" >&2; exit 1; }
	@mkdir --parents "$(STATE_DIRECTORY)"

shell: ensure-dev-image ## Open an isolated controller shell with no Docker socket
	@$(subst --rm,--rm -it,$(DEV_RUN)) bash

gradle: ensure-dev-image ## Run GRADLE_TASK in ANDROID_PROJECT through its checked-in wrapper
	@$(DEV_RUN) android-lab gradle

app-build: GRADLE_TASK := :app:assembleCoreDebug
app-build: gradle ## Build the default Fossify-compatible debug APK

app-test: GRADLE_TASK := :app:testCoreDebugUnitTest
app-test: gradle ## Run the default Fossify-compatible local Kotlin tests

app-lint: GRADLE_TASK := :app:lintCoreDebug
app-lint: gradle ## Run Android lint for the default Fossify-compatible debug variant

lint: ensure-dev-image ## ShellCheck every lab script and verify formatting
	@$(DEV_RUN) bash scripts/lint.sh

format: ensure-dev-image ## Format the lab shell and Python sources
	@$(DEV_RUN) bash scripts/format.sh

test: audit-compose dikciz-test ## Run the Android lab checks and Dikciz release UI tests

audit-compose: dev-image ## Validate the private Compose isolation contract
	@$(DEV_RUN) android-lab check

image: emulator-ready ## Verify both required local lab images

run: emulator-ready ## Start the private Android emulator with loopback-only web VNC
	@$(DEV_RUN_DIND) android-lab start

restart: emulator-ready ## Replace the private Android emulator
	@$(DEV_RUN_DIND) android-lab restart

stop: ensure-dev-image ## Stop only the named Android lab services
	@$(DEV_RUN_DIND) android-lab stop

status: ensure-dev-image ## Show only the Android lab Compose services
	@$(DEV_RUN_DIND) android-lab status

device-info: ensure-dev-image ## Show the pinned virtual device identity and Android version
	@$(DEV_RUN_DIND) android-lab device-info

apk-install: ensure-dev-image ## Install APK=relative/path.apk into the virtual device
	@$(DEV_RUN_DIND) android-lab apk-install

screenshot: ensure-dev-image ## Save .android-lab/shared/artifacts/screenshot.png from the shared phone
	@$(DEV_RUN_DIND) android-lab screenshot

uiautomator-dump: ensure-dev-image ## Save .android-lab/shared/artifacts/window.xml from the shared phone
	@$(DEV_RUN_DIND) android-lab uiautomator-dump

ui-layout: ensure-dev-image ## Save a compact JSON UI tree at .android-lab/shared/artifacts/layout.json
	@$(DEV_RUN_DIND) android-lab ui-layout

uiautomator-run: ensure-dev-image ## Install TEST_APK=path.apk then run TEST_RUNNER=package/runner
	@$(DEV_RUN_DIND) android-lab uiautomator-run

app-current: ensure-dev-image ## Print the foreground app in the shared emulator
	@$(DEV_RUN_DIND) android-lab app-current

app-start: ensure-dev-image ## Start PACKAGE_NAME with optional ACTIVITY in the shared emulator
	@$(DEV_RUN_DIND) android-lab app-start

app-stop: ensure-dev-image ## Force-stop PACKAGE_NAME in the shared emulator
	@$(DEV_RUN_DIND) android-lab app-stop

home-role-set: ensure-dev-image ## Make PACKAGE_NAME the Android Home role holder
	@$(DEV_RUN_DIND) android-lab home-role-set

home-start: ensure-dev-image ## Launch Android Home and wait for PACKAGE_NAME
	@$(DEV_RUN_DIND) android-lab home-start

navigation-buttons: ANDROID_LAB_FORCE_NAVIGATION_REBOOT := true
navigation-buttons: emulator-ready ## Apply Android's visible Back, Home, and Overview buttons
	@ANDROID_LAB_FORCE_NAVIGATION_REBOOT=true $(DEV_RUN_DIND) android-lab configure-navigation

device-shell: ensure-dev-image ## Run DEVICE_COMMAND only inside the shared emulator
	@$(DEV_RUN_DIND) android-lab device-shell

emulator-geo-fix: ensure-dev-image ## Inject one validated GPS fix into the shared emulator
	@$(DEV_RUN_DIND) android-lab emulator-geo-fix

emulator-sensor-set: ensure-dev-image ## Inject one validated three-axis sensor vector into the shared emulator
	@$(DEV_RUN_DIND) android-lab emulator-sensor-set

emulator-sms-send: ensure-dev-image ## Deliver one validated incoming SMS to the shared emulator
	@$(DEV_RUN_DIND) android-lab emulator-sms-send

emulator-power-set: ensure-dev-image ## Set validated emulator AC power on or off in the shared emulator
	@$(DEV_RUN_DIND) android-lab emulator-power-set

emulator-wifi-set: ensure-dev-image ## Connect or disconnect the shared emulator's built-in WiFi station
	@$(DEV_RUN_DIND) android-lab emulator-wifi-set

emulator-bluetooth-set: ensure-dev-image ## Set shared emulator Bluetooth on or off through a fixed command
	@$(DEV_RUN_DIND) android-lab emulator-bluetooth-set

emulator-thermal-set: ensure-dev-image ## Set or reset one finite thermal state on the shared emulator
	@$(DEV_RUN_DIND) android-lab emulator-thermal-set

emulator-power-save-set: ensure-dev-image ## Set Battery Saver on or off through a fixed shared-emulator command
	@$(DEV_RUN_DIND) android-lab emulator-power-save-set

emulator-device-idle-set: ensure-dev-image ## Set or clear device idle through a fixed shared-emulator command
	@$(DEV_RUN_DIND) android-lab emulator-device-idle-set

emulator-night-mode-set: ensure-dev-image ## Set one finite system night mode through a fixed shared-emulator command
	@$(DEV_RUN_DIND) android-lab emulator-night-mode-set

emulator-device-orientation-set: ensure-dev-image ## Set portrait or landscape through a fixed shared-emulator command
	@$(DEV_RUN_DIND) android-lab emulator-device-orientation-set

emulator-font-scale-set: ensure-dev-image ## Set one fixed font scale through a shared-emulator command
	@$(DEV_RUN_DIND) android-lab emulator-font-scale-set

emulator-ringer-mode-set: ensure-dev-image ## Set one finite ringer mode through a fixed shared-emulator command
	@$(DEV_RUN_DIND) android-lab emulator-ringer-mode-set

emulator-notification-policy-access-set: ensure-dev-image ## Grant or revoke Do Not Disturb access through a fixed shared-emulator command
	@$(DEV_RUN_DIND) android-lab emulator-notification-policy-access-set

emulator-interruption-filter-set: ensure-dev-image ## Set one finite Do Not Disturb mode through a fixed shared-emulator command
	@$(DEV_RUN_DIND) android-lab emulator-interruption-filter-set

dikciz-accessibility-on: PACKAGE_NAME := eu.psyb0t.dikciz.launcher.debug
dikciz-accessibility-on: ensure-dev-image ## Enable Dikciz cross-app automation only on the shared emulator
	@$(DEV_RUN_DIND) android-lab accessibility-on

dikciz-accessibility-off: PACKAGE_NAME := eu.psyb0t.dikciz.launcher.debug
dikciz-accessibility-off: ensure-dev-image ## Disable Dikciz cross-app automation only on the shared emulator
	@$(DEV_RUN_DIND) android-lab accessibility-off

emulator-calendar-fixture-create: ensure-dev-image ## Create the fixed Calendar event fixture on the shared emulator
	@$(DEV_RUN_DIND) android-lab calendar-fixture-create

emulator-calendar-fixture-update: ensure-dev-image ## Update the fixed Calendar event fixture on the shared emulator
	@$(DEV_RUN_DIND) android-lab calendar-fixture-update

emulator-calendar-fixture-delete: ensure-dev-image ## Delete the fixed Calendar event fixture from the shared emulator
	@$(DEV_RUN_DIND) android-lab calendar-fixture-delete

emulator-contacts-fixture-create: ensure-dev-image ## Create the fixed contacts-change fixture on the shared emulator
	@$(DEV_RUN_DIND) android-lab contacts-fixture-create

emulator-contacts-fixture-delete: ensure-dev-image ## Delete the fixed contacts-change fixture from the shared emulator
	@$(DEV_RUN_DIND) android-lab contacts-fixture-delete

adb-pull: ensure-dev-image ## Pull DEVICE_FILE=/sdcard/... into ARTIFACT_FILE under .android-lab/shared/artifacts
	@$(DEV_RUN_DIND) android-lab file-pull

adb-push: ensure-dev-image ## Push ARTIFACT_FILE from .android-lab/shared/artifacts to DEVICE_FILE=/sdcard/...
	@$(DEV_RUN_DIND) android-lab file-push

forward: emulator-ready ## Start the lab and forward one device loopback port to host loopback
	@$(DEV_RUN_DIND) android-lab forward

unforward: ensure-dev-image ## Stop only the named device-loopback forwarding service
	@$(DEV_RUN_DIND) android-lab unforward

# Both smoke targets probe the Dikciz shared forward that dikciz-run creates, so
# they default to its host ports. The device ports stay 19001 and 19002 because
# the launcher always listens there.
dikciz-automation-smoke: ANDROID_LAB_FORWARD_HOST_PORT := 19101
dikciz-automation-smoke: ANDROID_LAB_MCP_HOST_PORT := 19102
dikciz-automation-smoke: ensure-dev-image ## Prove the release automation socket through the Dikciz shared ADB forward on 19101
	@$(DEV_RUN_DIND) android-lab automation-smoke

dikciz-mcp-smoke: ANDROID_LAB_FORWARD_HOST_PORT := 19101
dikciz-mcp-smoke: ANDROID_LAB_MCP_HOST_PORT := 19102
dikciz-mcp-smoke: ensure-dev-image ## Prove the release Streamable HTTP MCP endpoint through the Dikciz shared ADB forward on 19102
	@$(DEV_RUN_DIND) android-lab mcp-smoke

dikciz-control-ready: ANDROID_LAB_FORWARD_HOST_PORT := 19101
dikciz-control-ready: ANDROID_LAB_MCP_HOST_PORT := 19102
dikciz-control-ready: ensure-dev-image ## Wait until both Dikciz control planes answer through the shared forward
	@$(DEV_RUN_DIND) android-lab dikciz-control-ready

dikciz-upstream-compare: dev-image ## Stage a Fossify source tree and show only safe refresh differences
	@$(DEV_RUN) bash scripts/dikciz-upstream.sh compare "$(DIKCIZ_UPSTREAM_DIR)" "$(DIKCIZ_UPSTREAM_BACKUP_DIR)"

dikciz-upstream-refresh-dry-run: dev-image ## Stage and validate a Fossify refresh without changing Dikciz
	@$(DEV_RUN) bash scripts/dikciz-upstream.sh dry-run "$(DIKCIZ_UPSTREAM_DIR)" "$(DIKCIZ_UPSTREAM_BACKUP_DIR)"

dikciz-upstream-refresh: ## Replace the base only after DIKCIZ_UPSTREAM_REFRESH=1
	@[[ "$(DIKCIZ_UPSTREAM_REFRESH)" == 1 ]] || { printf '%s\n' "set DIKCIZ_UPSTREAM_REFRESH=1 to replace the Dikciz source tree" >&2; exit 1; }
	@$(MAKE) --no-print-directory dev-image
	@$(DEV_RUN) bash scripts/dikciz-upstream.sh refresh "$(DIKCIZ_UPSTREAM_DIR)" "$(DIKCIZ_UPSTREAM_BACKUP_DIR)"

dikciz-storage-access: PACKAGE_NAME := eu.psyb0t.dikciz.launcher.debug
dikciz-storage-access: ensure-dev-image ## Grant Dikciz file access on the isolated emulator without clearing configuration
	@$(DEV_RUN_DIND) android-lab dikciz-storage-access

.PHONY: dikciz-storage-access

dikciz-run: ANDROID_LAB_INSTANCE := shared
dikciz-run: ANDROID_LAB_FORWARD_HOST_PORT := 19101
dikciz-run: ANDROID_LAB_MCP_HOST_PORT := 19102
dikciz-run: ANDROID_LAB_VNC_HOST_PORT := 61326
dikciz-run: APK := app/build/outputs/apk/dikciz/debug/launcher-16-dikciz-debug.apk
dikciz-run: PACKAGE_NAME := eu.psyb0t.dikciz.launcher.debug
dikciz-run: run ## Build, replace-install, launch Dikciz as Android Home, and expose its local controls
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@$(MAKE) --no-print-directory apk-install APK="$(APK)"
	@$(MAKE) --no-print-directory dikciz-storage-access
	@$(MAKE) --no-print-directory home-role-set PACKAGE_NAME="$(PACKAGE_NAME)"
	@$(MAKE) --no-print-directory home-start PACKAGE_NAME="$(PACKAGE_NAME)"
	@$(MAKE) --no-print-directory forward \
		ANDROID_LAB_INSTANCE="$(ANDROID_LAB_INSTANCE)" \
		ANDROID_LAB_FORWARD_HOST_PORT="$(ANDROID_LAB_FORWARD_HOST_PORT)" \
		ANDROID_LAB_MCP_HOST_PORT="$(ANDROID_LAB_MCP_HOST_PORT)"
	@$(MAKE) --no-print-directory dikciz-control-ready \
		ANDROID_LAB_FORWARD_HOST_PORT="$(ANDROID_LAB_FORWARD_HOST_PORT)" \
		ANDROID_LAB_MCP_HOST_PORT="$(ANDROID_LAB_MCP_HOST_PORT)"

dikciz-reset: ANDROID_LAB_INSTANCE := shared
dikciz-reset: ANDROID_LAB_FORWARD_HOST_PORT := 19101
dikciz-reset: ANDROID_LAB_MCP_HOST_PORT := 19102
dikciz-reset: ANDROID_LAB_VNC_HOST_PORT := 61326
dikciz-reset: APK := app/build/outputs/apk/dikciz/debug/launcher-16-dikciz-debug.apk
dikciz-reset: PACKAGE_NAME := eu.psyb0t.dikciz.launcher.debug
dikciz-reset: emulator-ready ## Uninstall Dikciz, clear /sdcard/Dikciz, install the current debug APK, and start Home
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@$(DEV_RUN_DIND) android-lab dikciz-reset

dikciz-test: ANDROID_LAB_INSTANCE := shared
dikciz-test: ANDROID_LAB_FORWARD_HOST_PORT := 19101
dikciz-test: ANDROID_LAB_MCP_HOST_PORT := 19102
dikciz-test: ANDROID_LAB_VNC_HOST_PORT := 61326
dikciz-test: APK := app/build/outputs/apk/dikciz/debug/launcher-16-dikciz-debug.apk
dikciz-test: PACKAGE_NAME := eu.psyb0t.dikciz.launcher.debug
dikciz-test: emulator-ready ## Build the current Dikciz debug APK, reset the shared phone, then run Dikciz pytest
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@$(MAKE) --no-print-directory dikciz-test-existing-apk \
		ANDROID_LAB_INSTANCE="$(ANDROID_LAB_INSTANCE)" \
		ANDROID_LAB_FORWARD_HOST_PORT="$(ANDROID_LAB_FORWARD_HOST_PORT)" \
		ANDROID_LAB_MCP_HOST_PORT="$(ANDROID_LAB_MCP_HOST_PORT)" \
		ANDROID_LAB_VNC_HOST_PORT="$(ANDROID_LAB_VNC_HOST_PORT)"

dikciz-test-existing-apk: ANDROID_LAB_INSTANCE := shared
dikciz-test-existing-apk: ANDROID_LAB_FORWARD_HOST_PORT := 19101
dikciz-test-existing-apk: ANDROID_LAB_MCP_HOST_PORT := 19102
dikciz-test-existing-apk: ANDROID_LAB_VNC_HOST_PORT := 61326
dikciz-test-existing-apk: APK := app/build/outputs/apk/dikciz/debug/launcher-16-dikciz-debug.apk
dikciz-test-existing-apk: PACKAGE_NAME := eu.psyb0t.dikciz.launcher.debug
dikciz-test-existing-apk: emulator-ready ## Reset the shared phone and test the already-built Dikciz APK
	@[[ -s "$(APK)" ]] || { printf '%s\n' "build the Dikciz APK before using dikciz-test-existing-apk" >&2; exit 1; }
	@ANDROID_LAB_INSTANCE="$(ANDROID_LAB_INSTANCE)" \
		ANDROID_LAB_FORWARD_HOST_PORT="$(ANDROID_LAB_FORWARD_HOST_PORT)" \
		ANDROID_LAB_FORWARD_DEVICE_PORT="$(ANDROID_LAB_FORWARD_DEVICE_PORT)" \
		ANDROID_LAB_MCP_HOST_PORT="$(ANDROID_LAB_MCP_HOST_PORT)" \
		ANDROID_LAB_MCP_DEVICE_PORT="$(ANDROID_LAB_MCP_DEVICE_PORT)" \
		ANDROID_LAB_VNC_HOST_PORT="$(ANDROID_LAB_VNC_HOST_PORT)" \
		DIKCIZ_TEST_SELECTOR="$(DIKCIZ_TEST_SELECTOR)" \
		$(DEV_RUN_DIND) android-lab dikciz-test

dikciz-test-interrupt: ANDROID_LAB_INSTANCE := shared
dikciz-test-interrupt: ANDROID_LAB_FORWARD_HOST_PORT := 19101
dikciz-test-interrupt: ANDROID_LAB_MCP_HOST_PORT := 19102
dikciz-test-interrupt: ANDROID_LAB_VNC_HOST_PORT := 61326
dikciz-test-interrupt: APK := app/build/outputs/apk/dikciz/debug/launcher-16-dikciz-debug.apk
dikciz-test-interrupt: PACKAGE_NAME := eu.psyb0t.dikciz.launcher.debug
dikciz-test-interrupt: override DIKCIZ_TEST_SELECTOR :=
dikciz-test-interrupt: override DIKCIZ_TEST_INTERRUPT_AFTER_PREPARE := true
dikciz-test-interrupt: emulator-ready ## Prove the runner restores the shared starter home after its fixed internal SIGINT
	@[[ -s "$(APK)" ]] || { printf '%s\n' "build the Dikciz APK before using dikciz-test-interrupt" >&2; exit 1; }
	@log_file="$$(mktemp "$(CURDIR)/.android-lab/shared/artifacts/dikciz-test-interrupt.XXXXXX.log")"; \
		set +e; \
		LOG_FILE="$$log_file" $(DEV_RUN_DIND) android-lab dikciz-test; \
		exit_code=$$?; \
		set -e; \
		[[ "$$exit_code" -eq 130 ]] || { printf '%s\n' "Dikciz interruption probe returned unexpected exit=$$exit_code" >&2; exit 1; }; \
		grep -F '"msg":"shared emulator restored to the bundled Dikciz home after the test run"' "$$log_file" >/dev/null || { printf '%s\n' "Dikciz interruption probe did not record starter-home restoration" >&2; exit 1; }; \
		printf '%s\n' "Dikciz interruption cleanup probe passed"

dikciz-test-provider-actions: ensure-dev-image ## Build the fixture and prove provider action updates through Dikciz controls
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=tests/configurable-widget-provider GRADLE_TASK=:app:assembleDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_CONFIGURABLE_PROVIDER_ACTIONS_SELECTOR)" \
		DIKCIZ_TEST_CONFIGURABLE_PROVIDER_APK="$(DIKCIZ_CONFIGURABLE_PROVIDER_APK)" \
		DIKCIZ_TEST_CONFIGURABLE_PROVIDER_PACKAGE="$(DIKCIZ_CONFIGURABLE_PROVIDER_PACKAGE)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-provider-media: ensure-dev-image ## Build the fixture and prove ordered media-style provider controls
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=tests/configurable-widget-provider GRADLE_TASK=:app:assembleDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_CONFIGURABLE_PROVIDER_MEDIA_SELECTOR)" \
		DIKCIZ_TEST_CONFIGURABLE_PROVIDER_APK="$(DIKCIZ_CONFIGURABLE_PROVIDER_APK)" \
		DIKCIZ_TEST_CONFIGURABLE_PROVIDER_PACKAGE="$(DIKCIZ_CONFIGURABLE_PROVIDER_PACKAGE)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-provider-weather: ensure-dev-image ## Build the fixture and prove weather-style provider recovery
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=tests/configurable-widget-provider GRADLE_TASK=:app:assembleDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_CONFIGURABLE_PROVIDER_WEATHER_SELECTOR)" \
		DIKCIZ_TEST_CONFIGURABLE_PROVIDER_APK="$(DIKCIZ_CONFIGURABLE_PROVIDER_APK)" \
		DIKCIZ_TEST_CONFIGURABLE_PROVIDER_PACKAGE="$(DIKCIZ_CONFIGURABLE_PROVIDER_PACKAGE)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-media-sessions: ensure-dev-image ## Build the fixture and prove typed media-session automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=tests/configurable-widget-provider GRADLE_TASK=:app:assembleDebug
	@DIKCIZ_MEDIA_SESSION_FIXTURE_APPLICATION_ID="$(DIKCIZ_MEDIA_SESSION_SECONDARY_FIXTURE_PACKAGE)" \
		DIKCIZ_MEDIA_SESSION_FIXTURE_BUILD_DIRECTORY="$(DIKCIZ_MEDIA_SESSION_SECONDARY_FIXTURE_BUILD_DIRECTORY)" \
		$(MAKE) --no-print-directory gradle ANDROID_PROJECT=tests/configurable-widget-provider GRADLE_TASK=:app:assembleDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_MEDIA_SESSION_SELECTOR)" \
		DIKCIZ_TEST_CONFIGURABLE_PROVIDER_APK="$(DIKCIZ_CONFIGURABLE_PROVIDER_APK)" \
		DIKCIZ_TEST_CONFIGURABLE_PROVIDER_PACKAGE="$(DIKCIZ_CONFIGURABLE_PROVIDER_PACKAGE)" \
		DIKCIZ_TEST_MEDIA_SESSION_SECONDARY_FIXTURE_APK="$(DIKCIZ_MEDIA_SESSION_SECONDARY_FIXTURE_APK)" \
		DIKCIZ_TEST_MEDIA_SESSION_SECONDARY_FIXTURE_PACKAGE="$(DIKCIZ_MEDIA_SESSION_SECONDARY_FIXTURE_PACKAGE)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-app-drawer-entry: ensure-dev-image ## Build Dikciz and prove the app drawer opens from the visible control and the home gesture
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_APP_DRAWER_ENTRY_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-app-drawer-search: ensure-dev-image ## Build Dikciz and prove live drawer search on label, package, and empty state
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_APP_DRAWER_SEARCH_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-app-drawer-launch: ensure-dev-image ## Build Dikciz and prove a drawer row tap launches the selected Android app
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_APP_DRAWER_LAUNCH_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-app-catalogue: ensure-dev-image ## Build Dikciz and prove the bounded app catalogue command and its stable IDs
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_APP_CATALOGUE_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-app-shortcut: ensure-dev-image ## Build Dikciz and prove the add-shortcut action persists a native app shortcut
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_APP_SHORTCUT_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-app-info: ensure-dev-image ## Build Dikciz and prove App info reaches the real Android package details screen
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_APP_INFO_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-app-force-stop: ensure-dev-image ## Build Dikciz and prove the root-only force stop reports root_unavailable without root
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_APP_FORCE_STOP_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-launcher-control: ensure-dev-image ## Build Dikciz and prove the always-visible launcher control and its four routes
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_LAUNCHER_CONTROL_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-home-chrome: ensure-dev-image ## Build Dikciz and prove the home screen shows no raw page coordinates
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_HOME_CHROME_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-control-placement: ensure-dev-image ## Build Dikciz and prove the launcher control clears the page canvas and the indicator
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_CONTROL_PLACEMENT_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-page-menu: ensure-dev-image ## Build Dikciz and prove the titled, grouped, verb-labelled page menu
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_PAGE_MENU_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-app-groups: ensure-dev-image ## Build Dikciz and prove app groups form by drop and that a group swaps instead
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_APP_GROUPS_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-guide-capture: ensure-dev-image ## Build Dikciz and regenerate every docs/user-guide screenshot from the bundled home
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@staging="$(CURDIR)/.android-lab/shared/artifacts/user-guide"; \
		mkdir --parents "$$staging"; \
		find "$$staging" -maxdepth 1 -type f -name '*.png' -delete
	@DIKCIZ_GUIDE_CAPTURE=true DIKCIZ_TEST_SELECTOR="$(DIKCIZ_GUIDE_CAPTURE_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk
	@captured="$(CURDIR)/.android-lab/shared/artifacts/user-guide"; \
		count="$$(find "$$captured" -maxdepth 1 -type f -name '*.png' | wc -l)"; \
		[ "$$count" -gt 0 ] || { printf '%s\n' "the guide walk captured no screenshots" >&2; exit 1; }; \
		mkdir --parents "$(CURDIR)/docs/images/user-guide"; \
		cp "$$captured"/*.png "$(CURDIR)/docs/images/user-guide/"; \
		printf '%s\n' "user guide screenshots updated: $$count"

dikciz-test-page-search: ensure-dev-image ## Build Dikciz and prove page search filters by name and address and jumps to a page
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_PAGE_SEARCH_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-grid-placement: ensure-dev-image ## Build Dikciz and prove an add lands in the first fitting free cells
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_GRID_PLACEMENT_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-grid-page-full: ensure-dev-image ## Build Dikciz and prove a full page refuses insertion without writing
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_GRID_PAGE_FULL_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-grid-settings: ensure-dev-image ## Build Dikciz and prove the page grid setting persists and is reported
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_GRID_SETTINGS_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-grid-conflict: ensure-dev-image ## Build Dikciz and prove a grid change that orphans a widget is rejected whole
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_GRID_CONFLICT_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-grid-rotation: ensure-dev-image ## Build Dikciz and prove page cells survive a portrait landscape portrait rotation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_GRID_ROTATION_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-widget-move: ensure-dev-image ## Build Dikciz and prove the visible widget Move action
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_WIDGET_MOVE_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-dialog-reachability: ensure-dev-image ## Build Dikciz and prove Settings and Automation last controls are physically reachable
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_DIALOG_REACHABILITY_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-starter-cleanup: ensure-dev-image ## Build Dikciz and prove targeted-test cleanup restores the bundled starter home
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_STARTER_CLEANUP_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-starter-recovery: ensure-dev-image ## Build Dikciz and prove the starter setup-and-retry route and the Android Settings label
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_STARTER_RECOVERY_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-media-volume: ensure-dev-image ## Build Dikciz and prove bounded media-volume automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_MEDIA_VOLUME_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-bluetooth: ensure-dev-image ## Build Dikciz and prove permission-gated Bluetooth state automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_BLUETOOTH_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-thermal-status: ensure-dev-image ## Build Dikciz and prove bounded thermal-status automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_THERMAL_STATUS_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-power-save-mode: ensure-dev-image ## Build Dikciz and prove policy-gated Battery Saver state automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_POWER_SAVE_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-device-idle: ensure-dev-image ## Build Dikciz and prove policy-gated device-idle state automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_DEVICE_IDLE_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-night-mode: ensure-dev-image ## Build Dikciz and prove policy-gated night-mode state automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_NIGHT_MODE_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-device-configuration: ensure-dev-image ## Build Dikciz and prove policy-gated device-configuration automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_DEVICE_CONFIGURATION_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-package-lifecycle: ensure-dev-image ## Build the fixture and prove policy-gated app package lifecycle automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=tests/configurable-widget-provider GRADLE_TASK=:app:assembleDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_PACKAGE_LIFECYCLE_SELECTOR)" \
		DIKCIZ_TEST_CONFIGURABLE_PROVIDER_APK="$(DIKCIZ_CONFIGURABLE_PROVIDER_APK)" \
		DIKCIZ_TEST_CONFIGURABLE_PROVIDER_PACKAGE="$(DIKCIZ_CONFIGURABLE_PROVIDER_PACKAGE)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-ringer-mode: ensure-dev-image ## Build Dikciz and prove policy-gated ringer-mode automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_RINGER_MODE_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-interruption-filter: ensure-dev-image ## Build Dikciz and prove Do Not Disturb state automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_INTERRUPTION_FILTER_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-calendar: ensure-dev-image ## Build Dikciz and prove permission-gated Calendar event automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_CALENDAR_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-contacts: ensure-dev-image ## Build Dikciz and prove permission-gated contacts-change automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_CONTACTS_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-manual-trigger: ensure-dev-image ## Build Dikciz and prove policy-gated local manual script triggers
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_MANUAL_TRIGGER_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-phone-state: ensure-dev-image ## Build Dikciz and prove permission-gated phone-state automation through the debug fixture
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_PHONE_STATE_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-sms: ensure-dev-image ## Build Dikciz and prove permission-gated incoming SMS automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_SMS_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-send-sms: ensure-dev-image ## Build Dikciz and prove the policy-gated outgoing SMS action
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_SEND_SMS_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-health-connect: ensure-dev-image ## Build Dikciz and prove bounded Health Connect daily-step automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_HEALTH_CONNECT_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-device-administration: ensure-dev-image ## Build Dikciz and prove opt-in Device Administration screen-lock automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_DEVICE_ADMINISTRATION_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-clipboard: ensure-dev-image ## Build Dikciz and prove foreground-only bounded clipboard automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_CLIPBOARD_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-notification-action: ensure-dev-image ## Build the fixture and prove allowlisted live notification actions
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=tests/configurable-widget-provider GRADLE_TASK=:app:assembleDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_NOTIFICATION_ACTION_SELECTOR)" \
		DIKCIZ_TEST_CONFIGURABLE_PROVIDER_APK="$(DIKCIZ_CONFIGURABLE_PROVIDER_APK)" \
		DIKCIZ_TEST_CONFIGURABLE_PROVIDER_PACKAGE="$(DIKCIZ_CONFIGURABLE_PROVIDER_PACKAGE)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-script-diagnostics: ensure-dev-image ## Build Dikciz and prove script logs, the viewer, and rate-limited failure notifications
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_SCRIPT_DIAGNOSTICS_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-configuration-error: ensure-dev-image ## Build Dikciz and prove fatal configuration recovery through both local planes
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_CONFIGURATION_ERROR_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-storage-access: ensure-dev-image ## Build Dikciz and prove first-run storage access through both local planes
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_STORAGE_ACCESS_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-page-provisional-controls: ensure-dev-image ## Build Dikciz and prove semantic page canvas and custom rail controls
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_PAGE_PROVISIONAL_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-remote-bearer-auth: ensure-dev-image ## Test remote bearer auth against the already-built Dikciz APK
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_REMOTE_BEARER_AUTH_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-system-sources: ensure-dev-image ## Build Dikciz and prove system-event automation through shared Android WiFi
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_SYSTEM_SOURCES_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-accessibility: ensure-dev-image ## Build Dikciz and prove owner-enabled cross-app Android automation
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_ACCESSIBILITY_SELECTOR)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-accessibility-third-party: ensure-dev-image ## Build the fixed fixture and prove cross-app actions against its real external window
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=tests/configurable-widget-provider GRADLE_TASK=:app:assembleDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_ACCESSIBILITY_THIRD_PARTY_SELECTOR)" \
		DIKCIZ_TEST_CONFIGURABLE_PROVIDER_APK="$(DIKCIZ_CONFIGURABLE_PROVIDER_APK)" \
		DIKCIZ_TEST_CONFIGURABLE_PROVIDER_PACKAGE="$(DIKCIZ_CONFIGURABLE_PROVIDER_PACKAGE)" \
		$(MAKE) --no-print-directory dikciz-test-existing-apk

dikciz-test-real: APK := app/build/outputs/apk/dikciz/debug/launcher-16-dikciz-debug.apk
dikciz-test-real: PACKAGE_NAME := eu.psyb0t.dikciz.launcher.debug
dikciz-test-real: ensure-dev-image ## Build the current Dikciz debug APK, then run one selected test on the configured physical phone
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@$(MAKE) --no-print-directory dikciz-test-real-existing-apk \
		APK="$(APK)" \
		PACKAGE_NAME="$(PACKAGE_NAME)"

dikciz-test-real-reset: APK := app/build/outputs/apk/dikciz/debug/launcher-16-dikciz-debug.apk
dikciz-test-real-reset: PACKAGE_NAME := eu.psyb0t.dikciz.launcher.debug
dikciz-test-real-reset: ensure-dev-image ## Uninstall, clear, and install the current Dikciz build on the configured physical phone
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@DIKCIZ_TEST_RESET_ONLY=true $(MAKE) --no-print-directory dikciz-test-real-existing-apk \
		APK="$(APK)" \
		PACKAGE_NAME="$(PACKAGE_NAME)"

dikciz-test-real-inspect: ensure-dev-image ## Capture the configured physical phone's current screen without changing Dikciz
	@DIKCIZ_TEST_INSPECT_ONLY=true $(MAKE) --no-print-directory dikciz-test-real-existing-apk

dikciz-test-real-device-admin-settings: ensure-dev-image ## Check whether only Dikciz's Device admin Settings page opens on the configured physical phone
	@DIKCIZ_TEST_DEVICE_ADMIN_SETTINGS_ONLY=true $(MAKE) --no-print-directory dikciz-test-real-existing-apk

dikciz-talkback-on: PACKAGE_NAME := eu.psyb0t.dikciz.launcher.debug
dikciz-talkback-on: ensure-dev-image ## Enable TalkBack on only the configured phone and open the current Dikciz Home
	@environment_file="$$(realpath -m "$(REAL_TEST_ENV_FILE)")"; \
		[[ "$$environment_file" == "$(CURDIR)/"* && -r "$$environment_file" ]] || { printf '%s\n' "REAL_TEST_ENV_FILE must be a readable file under this workspace" >&2; exit 1; }; \
		set -a; source "$$environment_file"; set +a; \
		[[ "$${TEST_REAL:-}" == 1 ]] || { printf '%s\n' "set TEST_REAL=1 in the ignored real-device environment file" >&2; exit 1; }; \
		[[ "$${TEST_DEVIVE_ID:-}" =~ ^[A-Za-z0-9._:-]+$$ ]] || { printf '%s\n' "TEST_DEVIVE_ID must contain one configured ADB serial" >&2; exit 1; }; \
		mkdir --parents "$(CURDIR)/.android-lab/real/artifacts"; \
		docker run --rm --init \
			--user $(UID):$(GID) \
			--network host \
			--cpus $(ANDROID_LAB_TOOL_CPU_LIMIT) \
			--memory $(ANDROID_LAB_TOOL_MEMORY_LIMIT) \
			--memory-swap $(ANDROID_LAB_TOOL_MEMORY_LIMIT) \
			--pids-limit $(ANDROID_LAB_TOOL_PIDS_LIMIT) \
			--read-only \
			--tmpfs /tmp:rw,noexec,nosuid,size=256m \
			--cap-drop ALL \
			--security-opt no-new-privileges:true \
			-e TEST_REAL \
			-e TEST_DEVIVE_ID \
			-e DIKCIZ_TEST_UNLOCK_PASSWORD \
			-e PACKAGE_NAME \
			-e LOG_FILE=/work/.android-lab/real/artifacts/dikciz-talkback.log \
			-e ANDROID_LAB_ARTIFACT_DIR=/work/.android-lab/real/artifacts \
			-v "$(CURDIR):/work" \
		-w /work \
		$(DEV_IMAGE) bash scripts/lab-device.sh talkback-on

dikciz-talkback-off: PACKAGE_NAME := eu.psyb0t.dikciz.launcher.debug
dikciz-talkback-off: ensure-dev-image ## Disable only TalkBack on the configured phone and open the current Dikciz Home
	@environment_file="$$(realpath -m "$(REAL_TEST_ENV_FILE)")"; \
		[[ "$$environment_file" == "$(CURDIR)/"* && -r "$$environment_file" ]] || { printf '%s\n' "REAL_TEST_ENV_FILE must be a readable file under this workspace" >&2; exit 1; }; \
		set -a; source "$$environment_file"; set +a; \
		[[ "$${TEST_REAL:-}" == 1 ]] || { printf '%s\n' "set TEST_REAL=1 in the ignored real-device environment file" >&2; exit 1; }; \
		[[ "$${TEST_DEVIVE_ID:-}" =~ ^[A-Za-z0-9._:-]+$$ ]] || { printf '%s\n' "TEST_DEVIVE_ID must contain one configured ADB serial" >&2; exit 1; }; \
		mkdir --parents "$(CURDIR)/.android-lab/real/artifacts"; \
		docker run --rm --init \
			--user $(UID):$(GID) \
			--network host \
			--cpus $(ANDROID_LAB_TOOL_CPU_LIMIT) \
			--memory $(ANDROID_LAB_TOOL_MEMORY_LIMIT) \
			--memory-swap $(ANDROID_LAB_TOOL_MEMORY_LIMIT) \
			--pids-limit $(ANDROID_LAB_TOOL_PIDS_LIMIT) \
			--read-only \
			--tmpfs /tmp:rw,noexec,nosuid,size=256m \
			--cap-drop ALL \
			--security-opt no-new-privileges:true \
			-e TEST_REAL \
			-e TEST_DEVIVE_ID \
			-e DIKCIZ_TEST_UNLOCK_PASSWORD \
			-e PACKAGE_NAME \
			-e LOG_FILE=/work/.android-lab/real/artifacts/dikciz-talkback-off.log \
			-e ANDROID_LAB_ARTIFACT_DIR=/work/.android-lab/real/artifacts \
			-v "$(CURDIR):/work" \
			-w /work \
			$(DEV_IMAGE) bash scripts/lab-device.sh talkback-off

dikciz-test-real-provider-inventory: ensure-dev-image ## Read the configured physical phone's Android AppWidget provider inventory
	@DIKCIZ_TEST_PROVIDER_INVENTORY_ONLY=true $(MAKE) --no-print-directory dikciz-test-real-existing-apk \
		APK="$(APK)" \
		PACKAGE_NAME="$(PACKAGE_NAME)"

dikciz-test-real-configurable-provider: APK := app/build/outputs/apk/dikciz/debug/launcher-16-dikciz-debug.apk
dikciz-test-real-configurable-provider: PACKAGE_NAME := eu.psyb0t.dikciz.launcher.debug
dikciz-test-real-configurable-provider: ensure-dev-image ## Prove the physical configurable AppWidget lifecycle with the isolated local fixture APK
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=. GRADLE_TASK=:app:assembleDikcizDebug
	@$(MAKE) --no-print-directory gradle ANDROID_PROJECT=tests/configurable-widget-provider GRADLE_TASK=:app:assembleDebug
	@DIKCIZ_TEST_SELECTOR="$(DIKCIZ_CONFIGURABLE_PROVIDER_SELECTOR)" $(MAKE) --no-print-directory dikciz-test-real-existing-apk \
		APK="$(APK)" \
		PACKAGE_NAME="$(PACKAGE_NAME)" \
		DIKCIZ_CONFIGURABLE_PROVIDER_APK="$(DIKCIZ_CONFIGURABLE_PROVIDER_APK)" \
		DIKCIZ_CONFIGURABLE_PROVIDER_PACKAGE="$(DIKCIZ_CONFIGURABLE_PROVIDER_PACKAGE)"

dikciz-test-real-existing-apk: APK := app/build/outputs/apk/dikciz/debug/launcher-16-dikciz-debug.apk
dikciz-test-real-existing-apk: PACKAGE_NAME := eu.psyb0t.dikciz.launcher.debug
dikciz-test-real-existing-apk: ensure-dev-image ## Run one selected Dikciz test on only the physical phone configured in .env
	@[[ "$(DIKCIZ_TEST_PROVIDER_INVENTORY_ONLY)" == true || "$(DIKCIZ_TEST_DEVICE_ADMIN_SETTINGS_ONLY)" == true || "$(DIKCIZ_TEST_INSPECT_ONLY)" == true || -s "$(APK)" ]] || { printf '%s\n' "build the Dikciz APK before using dikciz-test-real-existing-apk" >&2; exit 1; }
	@environment_file="$$(realpath -m "$(REAL_TEST_ENV_FILE)")"; \
		[[ "$$environment_file" == "$(CURDIR)/"* && -r "$$environment_file" ]] || { printf '%s\n' "REAL_TEST_ENV_FILE must be a readable file under this workspace" >&2; exit 1; }; \
		set -a; source "$$environment_file"; set +a; \
		[[ "$${TEST_REAL:-}" == 1 ]] || { printf '%s\n' "set TEST_REAL=1 in the ignored real-device environment file" >&2; exit 1; }; \
		[[ "$${TEST_DEVIVE_ID:-}" =~ ^[A-Za-z0-9._:-]+$$ ]] || { printf '%s\n' "TEST_DEVIVE_ID must contain one configured ADB serial" >&2; exit 1; }; \
		[[ "$${DIKCIZ_TEST_RESET_ONLY:-false}" == true || "$${DIKCIZ_TEST_RESET_ONLY:-false}" == false ]] || { printf '%s\n' "DIKCIZ_TEST_RESET_ONLY must be true or false" >&2; exit 1; }; \
		[[ "$${DIKCIZ_TEST_PROVIDER_INVENTORY_ONLY:-false}" == true || "$${DIKCIZ_TEST_PROVIDER_INVENTORY_ONLY:-false}" == false ]] || { printf '%s\n' "DIKCIZ_TEST_PROVIDER_INVENTORY_ONLY must be true or false" >&2; exit 1; }; \
		[[ "$${DIKCIZ_TEST_DEVICE_ADMIN_SETTINGS_ONLY:-false}" == true || "$${DIKCIZ_TEST_DEVICE_ADMIN_SETTINGS_ONLY:-false}" == false ]] || { printf '%s\n' "DIKCIZ_TEST_DEVICE_ADMIN_SETTINGS_ONLY must be true or false" >&2; exit 1; }; \
		[[ "$${DIKCIZ_TEST_INSPECT_ONLY:-false}" == true || "$${DIKCIZ_TEST_INSPECT_ONLY:-false}" == false ]] || { printf '%s\n' "DIKCIZ_TEST_INSPECT_ONLY must be true or false" >&2; exit 1; }; \
		[[ "$${DIKCIZ_TEST_RESET_ONLY:-false}" == true || "$${DIKCIZ_TEST_PROVIDER_INVENTORY_ONLY:-false}" == true || "$${DIKCIZ_TEST_DEVICE_ADMIN_SETTINGS_ONLY:-false}" == true || "$${DIKCIZ_TEST_INSPECT_ONLY:-false}" == true || "$${DIKCIZ_TEST_SELECTOR:-}" =~ ^test_[A-Za-z0-9_]+$$ ]] || { printf '%s\n' "DIKCIZ_TEST_SELECTOR must name one focused Dikciz pytest test" >&2; exit 1; }; \
		docker run --rm --init \
			--user $(UID):$(GID) \
			--network host \
			--cpus $(ANDROID_LAB_TOOL_CPU_LIMIT) \
			--memory $(ANDROID_LAB_TOOL_MEMORY_LIMIT) \
			--memory-swap $(ANDROID_LAB_TOOL_MEMORY_LIMIT) \
			--pids-limit $(ANDROID_LAB_TOOL_PIDS_LIMIT) \
			--read-only \
			--tmpfs /tmp:rw,noexec,nosuid,size=256m \
			--cap-drop ALL \
			--security-opt no-new-privileges:true \
			-e TEST_REAL \
			-e TEST_DEVIVE_ID \
			-e DIKCIZ_TEST_UNLOCK_PASSWORD \
			-e DIKCIZ_TEST_SELECTOR \
			-e DIKCIZ_TEST_RESET_ONLY \
			-e DIKCIZ_TEST_PROVIDER_INVENTORY_ONLY \
			-e DIKCIZ_TEST_DEVICE_ADMIN_SETTINGS_ONLY \
			-e DIKCIZ_TEST_INSPECT_ONLY \
			-e DIKCIZ_CONFIGURABLE_PROVIDER_APK \
			-e DIKCIZ_CONFIGURABLE_PROVIDER_PACKAGE \
			-e LOG_FILE=/work/.android-lab/real/artifacts/dikciz-test-real.log \
			-e APK="$(APK)" \
			-e PACKAGE_NAME="$(PACKAGE_NAME)" \
			-e ANDROID_LAB_ARTIFACT_DIR=/work/.android-lab/real/artifacts \
			-e ANDROID_LAB_FORWARD_HOST_PORT=19101 \
			-e ANDROID_LAB_FORWARD_DEVICE_PORT=19001 \
			-v "$(CURDIR):/work" \
			-w /work \
			$(DEV_IMAGE) bash scripts/dikciz-test-real.sh

dikciz-test-stop: ANDROID_LAB_INSTANCE := shared
dikciz-test-stop: ANDROID_LAB_VNC_HOST_PORT := 61326
dikciz-test-stop: ensure-dev-image ## Stop the persistent shared Dikciz phone
	@$(DEV_RUN_DIND) android-lab stop

test-emulator: emulator-ready ## Boot, identify, dump UI, capture a screenshot, then stop the lab
	@$(DEV_RUN_DIND) android-lab test-emulator

test-forward: ANDROID_LAB_FORWARD_HOST_PORT := 61324
test-forward: ANDROID_LAB_FORWARD_DEVICE_PORT := 61324
test-forward: emulator-ready ## Prove the device-loopback forward then stop the lab
	@$(DEV_RUN_DIND) android-lab test-forward

test-vnc: emulator-ready ## Boot web VNC, verify the private VNC path, then stop the lab
	@$(DEV_RUN_DIND) android-lab test-vnc

clean: ensure-dev-image ## Remove only ignored Android lab build artifacts
	@ANDROID_LAB_DEV_IMAGE=$(DEV_IMAGE) ANDROID_LAB_EMULATOR_IMAGE=$(EMULATOR_IMAGE) ANDROID_LAB_UID=$(UID) ANDROID_LAB_GID=$(GID) $(DEV_RUN) android-lab clean
