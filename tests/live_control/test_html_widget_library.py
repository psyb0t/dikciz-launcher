"""Exercise public HTML widget package replacement and recovery."""

from . import *


def library_path(*parts: str) -> str:
    return "/".join((HTML_WIDGET_LIBRARY_DEVICE_DIRECTORY, *parts))


def package_picker_entry_semantic_id(package_id: str) -> str:
    return f"{HTML_WIDGET_LIBRARY_PICKER_PREFIX}:entry:{package_id}"


def package_picker_load_semantic_id(package_id: str) -> str:
    return f"{HTML_WIDGET_LIBRARY_PICKER_PREFIX}:load:{package_id}"


def public_package_manifest(package_directory: str) -> dict[str, object]:
    return json.loads(
        device_command(
            f"cat {library_path(package_directory, HTML_WIDGET_LIBRARY_PACKAGE_MANIFEST_FILE_NAME)}",
        ),
    )


def public_package_source(package_directory: str) -> str:
    return device_command(
        f"cat {library_path(package_directory, HTML_WIDGET_LIBRARY_PACKAGE_SOURCE_FILE_NAME)}",
    )


def open_html_package_picker(
    websocket_control: socket.socket,
    package_id: str = HTML_WIDGET_LIBRARY_PACKAGE_ID,
) -> dict[str, object]:
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_LONG_PRESS,
        semanticId=HTML_WIDGET_LIBRARY_PAGE_CANVAS_SEMANTIC_ID,
    )
    wait_for_snapshot_node(
        websocket_control,
        HTML_WIDGET_LIBRARY_PAGE_MENU_WIDGETS_SEMANTIC_ID,
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=HTML_WIDGET_LIBRARY_PAGE_MENU_WIDGETS_SEMANTIC_ID,
    )
    wait_for_snapshot_node(
        websocket_control,
        HTML_WIDGET_LIBRARY_PICKER_SEARCH_SEMANTIC_ID,
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=HTML_WIDGET_LIBRARY_PICKER_SEARCH_SEMANTIC_ID,
        text=HTML_WIDGET_LIBRARY_PICKER_SEARCH_TEXT,
    )
    wait_for_snapshot_node(
        websocket_control,
        HTML_WIDGET_LIBRARY_PICKER_HTML_ENTRY_SEMANTIC_ID,
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=HTML_WIDGET_LIBRARY_PICKER_HTML_ENTRY_SEMANTIC_ID,
    )
    return wait_for_snapshot_node(
        websocket_control,
        package_picker_entry_semantic_id(package_id),
    )


def close_active_dialog(websocket_control: socket.socket) -> None:
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=DIALOG_CLOSE_SEMANTIC_ID,
    )
    wait_for_snapshot_node(websocket_control, DIALOG_CLOSE_SEMANTIC_ID, should_exist=False)


def save_package_from_welcome_editor(
    websocket_control: socket.socket,
    title: str,
    html: str,
) -> None:
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=HTML_WIDGET_LIBRARY_EDITOR_TITLE_SEMANTIC_ID,
        text=title,
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=HTML_WIDGET_LIBRARY_EDITOR_HTML_SEMANTIC_ID,
        text=html,
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=HTML_WIDGET_LIBRARY_EDITOR_PACKAGE_ID_SEMANTIC_ID,
        text=HTML_WIDGET_LIBRARY_PACKAGE_ID,
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=HTML_WIDGET_LIBRARY_EDITOR_SAVE_TO_LIBRARY_SEMANTIC_ID,
    )


def wait_for_library_status(prefixes: tuple[str, ...]) -> str:
    deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
    visible_texts: list[str] = []
    while time.monotonic() < deadline:
        visible_texts = physical_visible_texts()
        matched_text = next(
            (
                text
                for text in visible_texts
                if text.startswith(prefixes)
            ),
            None,
        )
        if matched_text is not None:
            return matched_text
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(
        "HTML widget library status did not appear; "
        f"prefixes={prefixes!r}, visible={visible_texts!r}",
    )


def test_bundled_html_widget_packages_load_in_the_native_picker(
    websocket_control: socket.socket,
) -> None:
    picker_snapshot = open_html_package_picker(
        websocket_control,
        HTML_WIDGET_LIBRARY_SYSTEM_STATUS_PACKAGE_ID,
    )
    for package_id, package_title in HTML_WIDGET_LIBRARY_BUNDLED_PACKAGES.items():
        package_entry = AUTOMATION.require_node(
            picker_snapshot,
            package_picker_entry_semantic_id(package_id),
        )
        assert package_title in str(package_entry["text"])

    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=package_picker_entry_semantic_id(
            HTML_WIDGET_LIBRARY_SYSTEM_STATUS_PACKAGE_ID,
        ),
    )
    wait_for_snapshot_node(
        websocket_control,
        package_picker_load_semantic_id(HTML_WIDGET_LIBRARY_SYSTEM_STATUS_PACKAGE_ID),
    )
    run_device_operation("screenshot")
    run_device_operation("uiautomator-dump")
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=package_picker_load_semantic_id(
            HTML_WIDGET_LIBRARY_SYSTEM_STATUS_PACKAGE_ID,
        ),
    )
    wait_for_snapshot_node(
        websocket_control,
        f"widget:{HTML_WIDGET_LIBRARY_SYSTEM_STATUS_WIDGET_ID}",
    )
    saved_configuration = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    saved_widget = configuration_widget(
        saved_configuration,
        HTML_WIDGET_LIBRARY_SYSTEM_STATUS_WIDGET_ID,
    )
    assert saved_widget["title"] == HTML_WIDGET_LIBRARY_BUNDLED_PACKAGES[
        HTML_WIDGET_LIBRARY_SYSTEM_STATUS_PACKAGE_ID
    ]

    wait_for_physical_text_bounds(
        HTML_WIDGET_LIBRARY_BUNDLED_PACKAGES[HTML_WIDGET_LIBRARY_SYSTEM_STATUS_PACKAGE_ID],
    )
    direct_tap(
        wait_for_physical_button_bounds(
            HTML_WIDGET_LIBRARY_SYSTEM_STATUS_REFRESH_LABEL,
        ),
    )
    wait_for_library_status((HTML_WIDGET_LIBRARY_SYSTEM_STATUS_DIAGNOSTICS_PREFIX,))
    direct_tap(
        wait_for_physical_button_bounds(
            HTML_WIDGET_LIBRARY_SYSTEM_STATUS_LOGS_LABEL,
        ),
    )
    wait_for_library_status(
        (
            HTML_WIDGET_LIBRARY_SYSTEM_STATUS_LOG_PREFIX,
            HTML_WIDGET_LIBRARY_SYSTEM_STATUS_LOG_EMPTY,
        ),
    )
    run_device_operation("screenshot")
    run_device_operation("uiautomator-dump")


def test_html_widget_packages_replace_and_recover_in_the_native_picker(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    package_directory = library_path(HTML_WIDGET_LIBRARY_PACKAGE_ID)
    staging_directory = library_path(HTML_WIDGET_LIBRARY_STAGING_DIRECTORY)
    backup_directory = library_path(HTML_WIDGET_LIBRARY_BACKUP_DIRECTORY)
    invalid_directory = library_path(HTML_WIDGET_LIBRARY_INVALID_DIRECTORY)
    recovered_source_artifact = ARTIFACT_DIRECTORY / HTML_WIDGET_LIBRARY_RECOVERED_HTML_ARTIFACT_NAME
    invalid_manifest_artifact = ARTIFACT_DIRECTORY / HTML_WIDGET_LIBRARY_INVALID_MANIFEST_ARTIFACT_NAME
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_LONG_PRESS,
            semanticId=HTML_WIDGET_LIBRARY_WIDGET_MOVE_HANDLE_SEMANTIC_ID,
        )
        wait_for_snapshot_node(websocket_control, HTML_WIDGET_LIBRARY_WIDGET_EDIT_SEMANTIC_ID)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=HTML_WIDGET_LIBRARY_WIDGET_EDIT_SEMANTIC_ID,
        )
        wait_for_snapshot_node(websocket_control, HTML_WIDGET_LIBRARY_EDITOR_TITLE_SEMANTIC_ID)

        save_package_from_welcome_editor(
            websocket_control,
            HTML_WIDGET_LIBRARY_FIRST_TITLE,
            HTML_WIDGET_LIBRARY_FIRST_HTML,
        )
        first_manifest = public_package_manifest(HTML_WIDGET_LIBRARY_PACKAGE_ID)
        assert first_manifest["title"] == HTML_WIDGET_LIBRARY_FIRST_TITLE
        assert public_package_source(HTML_WIDGET_LIBRARY_PACKAGE_ID) == HTML_WIDGET_LIBRARY_FIRST_HTML

        save_package_from_welcome_editor(
            websocket_control,
            HTML_WIDGET_LIBRARY_UPDATED_TITLE,
            HTML_WIDGET_LIBRARY_UPDATED_HTML,
        )
        updated_manifest = public_package_manifest(HTML_WIDGET_LIBRARY_PACKAGE_ID)
        assert updated_manifest["title"] == HTML_WIDGET_LIBRARY_UPDATED_TITLE
        assert public_package_source(HTML_WIDGET_LIBRARY_PACKAGE_ID) == HTML_WIDGET_LIBRARY_UPDATED_HTML
        close_active_dialog(websocket_control)
        wait_for_snapshot_node(websocket_control, HTML_WIDGET_LIBRARY_PAGE_CANVAS_SEMANTIC_ID)

        device_command(f"cp -R {package_directory} {staging_directory}")
        device_command(f"mv {package_directory} {backup_directory}")
        recovered_source_artifact.write_text(HTML_WIDGET_LIBRARY_RECOVERED_HTML, encoding="utf-8")
        run_device_operation(
            "file-push",
            DEVICE_FILE=library_path(
                HTML_WIDGET_LIBRARY_STAGING_DIRECTORY,
                HTML_WIDGET_LIBRARY_PACKAGE_SOURCE_FILE_NAME,
            ),
            ARTIFACT_FILE=HTML_WIDGET_LIBRARY_RECOVERED_HTML_ARTIFACT_NAME,
        )
        staged_manifest = public_package_manifest(HTML_WIDGET_LIBRARY_STAGING_DIRECTORY)
        assert staged_manifest["id"] == HTML_WIDGET_LIBRARY_PACKAGE_ID
        assert public_package_source(
            HTML_WIDGET_LIBRARY_STAGING_DIRECTORY,
        ) == HTML_WIDGET_LIBRARY_RECOVERED_HTML

        picker_snapshot = open_html_package_picker(websocket_control)
        package_entry = AUTOMATION.require_node(
            picker_snapshot,
            package_picker_entry_semantic_id(HTML_WIDGET_LIBRARY_PACKAGE_ID),
        )
        assert HTML_WIDGET_LIBRARY_UPDATED_TITLE in str(package_entry["text"])
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=package_picker_entry_semantic_id(HTML_WIDGET_LIBRARY_PACKAGE_ID),
        )
        wait_for_snapshot_node(
            websocket_control,
            package_picker_load_semantic_id(HTML_WIDGET_LIBRARY_PACKAGE_ID),
        )
        run_device_operation("screenshot")
        run_device_operation("uiautomator-dump")
        assert public_package_source(HTML_WIDGET_LIBRARY_PACKAGE_ID) == HTML_WIDGET_LIBRARY_RECOVERED_HTML
        device_command(f"test ! -e {staging_directory} && test ! -e {backup_directory}")

        close_active_dialog(websocket_control)
        device_command(f"cp -R {package_directory} {staging_directory}")
        device_command(f"mv {package_directory} {backup_directory}")
        invalid_manifest_artifact.write_text(
            HTML_WIDGET_LIBRARY_INVALID_MANIFEST_CONTENT,
            encoding="utf-8",
        )
        run_device_operation(
            "file-push",
            DEVICE_FILE=library_path(
                HTML_WIDGET_LIBRARY_STAGING_DIRECTORY,
                HTML_WIDGET_LIBRARY_PACKAGE_MANIFEST_FILE_NAME,
            ),
            ARTIFACT_FILE=HTML_WIDGET_LIBRARY_INVALID_MANIFEST_ARTIFACT_NAME,
        )
        device_command(f"mkdir -p {invalid_directory}")
        run_device_operation(
            "file-push",
            DEVICE_FILE=library_path(
                HTML_WIDGET_LIBRARY_INVALID_DIRECTORY,
                HTML_WIDGET_LIBRARY_PACKAGE_MANIFEST_FILE_NAME,
            ),
            ARTIFACT_FILE=HTML_WIDGET_LIBRARY_INVALID_MANIFEST_ARTIFACT_NAME,
        )

        restored_picker = open_html_package_picker(websocket_control)
        assert AUTOMATION.require_node(
            restored_picker,
            package_picker_entry_semantic_id(HTML_WIDGET_LIBRARY_PACKAGE_ID),
        )
        assert all(
            node.get("semanticId") != package_picker_entry_semantic_id(
                HTML_WIDGET_LIBRARY_INVALID_DIRECTORY,
            )
            for node in restored_picker["nodes"]
        )
        assert public_package_source(HTML_WIDGET_LIBRARY_PACKAGE_ID) == HTML_WIDGET_LIBRARY_RECOVERED_HTML
        device_command(f"test ! -e {staging_directory} && test ! -e {backup_directory}")
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
