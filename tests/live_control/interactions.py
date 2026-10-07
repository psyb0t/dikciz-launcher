"""Shared picker, provider, theme, and control-plane helpers."""

from .configuration import *

def open_page_menu_on_selected_page() -> dict[str, int]:
    page_bounds = physical_page_scroll_bounds()
    direct_long_press_at(center(page_bounds)[0], center(page_bounds)[1])
    return wait_for_physical_text_bounds(PAGE_MENU_ADD_WIDGET_LABEL)


def open_picker_on_selected_page() -> None:
    direct_tap(open_page_menu_on_selected_page())
    wait_for_physical_text_bounds("Add widget")


def assert_widget_picker_categories() -> None:
    physical_button_bounds(WIDGET_PICKER_DIKCIZ_CATEGORY_LABEL)
    physical_button_bounds(WIDGET_PICKER_APPS_CATEGORY_LABEL)
    assert all(
        node.get("text") != WIDGET_PICKER_LEGACY_DEBUG_CATEGORY_LABEL
        for node in physical_nodes()
    )


def open_command_sheet() -> dict[str, int]:
    open_page_menu_on_selected_page()
    direct_tap(wait_for_physical_text_bounds(COMMAND_PAGE_MENU_LABEL))
    return wait_for_physical_description_bounds(COMMAND_SEARCH_DESCRIPTION)


def wait_for_command_app_entry(
    connection: socket.socket,
    application_label: str,
) -> str:
    deadline = time.monotonic() + CONTROL_PLANE_RESTART_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SNAPSHOT)
        for node in snapshot["nodes"]:
            semantic_id = node.get("semanticId")
            if (
                isinstance(semantic_id, str)
                and semantic_id.startswith(COMMAND_APP_ENTRY_PREFIX)
                and node.get("text") == application_label
            ):
                return semantic_id
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"command sheet did not expose application: {application_label}")


def select_native_widget_picker_entry(entry_label: str) -> None:
    direct_enter_text(
        physical_description_bounds("Filter widgets and apps"),
        entry_label,
    )
    entry_description = NATIVE_WIDGET_PICKER_ENTRY_DESCRIPTION.format(label=entry_label)
    direct_tap(wait_for_physical_description_bounds(entry_description))


def start_deskclock_provider_addition(connection: socket.socket) -> None:
    direct_select_notes_page(connection)
    begin_deskclock_provider_addition()


def begin_deskclock_provider_addition() -> None:
    open_picker_on_selected_page()
    assert_widget_picker_categories()
    direct_tap(wait_for_physical_text_bounds(WIDGET_PICKER_APPS_CATEGORY_LABEL))
    direct_tap(
        wait_for_physical_description_bounds(
            WIDGET_PICKER_APPLICATION_DESCRIPTION.format(DESKCLOCK_CATEGORY_LABEL),
        ),
    )
    direct_tap(
        wait_for_physical_description_bounds(
            WIDGET_PICKER_PROVIDER_ENTRY_DESCRIPTION.format(
                DESKCLOCK_PROVIDER_LABEL,
                DESKCLOCK_CATEGORY_LABEL,
            ),
        ),
    )
    approve_widget_bind_if_requested()
    complete_deskclock_provider_configuration_if_requested()


def configurable_provider_fixture_package() -> str:
    package_name = os.environ.get(CONFIGURABLE_PROVIDER_FIXTURE_PACKAGE_ENVIRONMENT, "")
    if package_name == "":
        pytest.skip(
            f"{CONFIGURABLE_PROVIDER_FIXTURE_TEST_NAME} requires the focused physical configurable-provider fixture target",
        )
    assert package_name == CONFIGURABLE_PROVIDER_FIXTURE_EXPECTED_PACKAGE
    return package_name


def configurable_provider_fixture_apk() -> str:
    apk_path = os.environ.get(CONFIGURABLE_PROVIDER_FIXTURE_APK_ENVIRONMENT, "")
    if apk_path == "":
        pytest.fail("the configurable provider actions target did not provide its fixture APK")
    assert apk_path == CONFIGURABLE_PROVIDER_FIXTURE_APK_PATH
    assert (LAB_ROOT / apk_path).is_file()
    return apk_path


def install_configurable_provider_fixture() -> str:
    package_name = configurable_provider_fixture_package()
    run_device_operation("install", APK=configurable_provider_fixture_apk())
    return package_name


def uninstall_configurable_provider_fixture(package_name: str) -> None:
    result = device_command(f"pm uninstall {package_name}")
    assert "Success" in result


def start_configurable_provider_fixture_addition(connection: socket.socket) -> None:
    direct_select_notes_page(connection)
    open_picker_on_selected_page()
    assert_widget_picker_categories()
    direct_tap(wait_for_physical_text_bounds(WIDGET_PICKER_APPS_CATEGORY_LABEL))
    direct_enter_text(
        physical_description_bounds("Filter widgets and apps"),
        CONFIGURABLE_PROVIDER_FIXTURE_APPLICATION_LABEL,
    )
    direct_hide_keyboard()
    direct_tap(
        wait_for_physical_description_bounds(
            WIDGET_PICKER_PROVIDER_ENTRY_DESCRIPTION.format(
                CONFIGURABLE_PROVIDER_FIXTURE_PROVIDER_LABEL,
                CONFIGURABLE_PROVIDER_FIXTURE_APPLICATION_LABEL,
            ),
        ),
    )
    approve_widget_bind_if_requested()


def complete_configurable_provider_fixture_configuration(save: bool) -> None:
    fixture_package = configurable_provider_fixture_package()
    action_label = (
        CONFIGURABLE_PROVIDER_FIXTURE_SAVE_LABEL
        if save
        else CONFIGURABLE_PROVIDER_FIXTURE_CANCEL_LABEL
    )
    deadline = time.monotonic() + APP_WIDGET_CONFIGURATION_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        foreground = run_device_operation("app-current")
        if fixture_package not in foreground:
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
            continue
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / CONFIGURABLE_PROVIDER_FIXTURE_CONFIGURATION_ARTIFACT_NAME,
        )
        direct_tap(physical_button_bounds(action_label))
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
        return
    raise AssertionError("configurable provider fixture configuration did not open")


def start_chrome_search_provider_addition(connection: socket.socket) -> None:
    direct_select_notes_page(connection)
    open_picker_on_selected_page()
    assert_widget_picker_categories()
    direct_tap(wait_for_physical_text_bounds(WIDGET_PICKER_APPS_CATEGORY_LABEL))
    direct_enter_text(
        physical_description_bounds("Filter widgets and apps"),
        CHROME_CATEGORY_LABEL,
    )
    direct_hide_keyboard()
    direct_tap(
        wait_for_physical_description_bounds(
            WIDGET_PICKER_PROVIDER_ENTRY_DESCRIPTION.format(
                CHROME_PROVIDER_LABEL,
                CHROME_CATEGORY_LABEL,
            ),
        ),
    )
    approve_widget_bind_if_requested()
    wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)


def approve_widget_bind_if_requested() -> bool:
    deadline = time.monotonic() + APP_WIDGET_BIND_APPROVAL_TIMEOUT_SECONDS
    foreground = ""
    while time.monotonic() < deadline:
        foreground = run_device_operation("app-current")
        if DIKCIZ_DEBUG_PACKAGE in foreground:
            return False
        if APP_WIDGET_BIND_ACTIVITY_COMPONENT not in foreground:
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
            continue
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / APP_WIDGET_BIND_APPROVAL_ARTIFACT_NAME,
        )
        visible_texts = physical_visible_texts()
        assert any(
            APP_WIDGET_BIND_APPLICATION_LABEL in text
            for text in visible_texts
        )
        assert all(
            WIDGET_PICKER_LEGACY_DEBUG_CATEGORY_LABEL not in text
            for text in visible_texts
        )
        approval_bounds = physical_bounds_for(
            lambda node: node.get("resource-id") == APP_WIDGET_BIND_APPROVAL_RESOURCE_ID,
        )
        direct_tap(approval_bounds)
        return True
    raise AssertionError(
        "Android did not complete the app widget bind flow: "
        f"{foreground}",
    )


def complete_deskclock_provider_configuration_if_requested() -> bool:
    deadline = time.monotonic() + APP_WIDGET_CONFIGURATION_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        foreground = run_device_operation("app-current")
        if DIKCIZ_DEBUG_PACKAGE in foreground:
            # Android returns to the host between bind approval and starting a
            # provider configuration activity.
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
            continue
        if APP_WIDGET_BIND_ACTIVITY_COMPONENT in foreground:
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
            continue
        if DESKCLOCK_APP_PACKAGE not in foreground:
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
            continue
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / APP_WIDGET_CONFIGURATION_ARTIFACT_NAME,
        )
        clock_face_bounds = physical_bounds_for(
            lambda node: node.get("clickable") == "true"
            and node.get("content-desc", "").startswith(
                DESKCLOCK_CONFIGURATION_FACE_DESCRIPTION_PREFIX,
            ),
        )
        direct_tap(clock_face_bounds)
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
        return True
    raise AssertionError("DeskClock configuration did not return to Dikciz")


def configure_welcome_cell(document: dict[str, Any]) -> dict[str, Any]:
    """Leaves welcome free grid room on every edge so a resize handle never collides."""
    configured = copy.deepcopy(document)
    configured["launcher"]["home"]["selectedPageId"] = "home"
    configuration_widget(configured, "welcome")["cell"] = {
        "column": 1,
        "row": 2,
        "columnSpan": 2,
        "rowSpan": 2,
    }
    configuration_widget(configured, "focus")["cell"] = {
        "column": 0,
        "row": 5,
        "columnSpan": 2,
        "rowSpan": 1,
    }
    configuration_widget(configured, "fixture-detail")["cell"] = {
        "column": 2,
        "row": 5,
        "columnSpan": 2,
        "rowSpan": 1,
    }
    return configured


def selected_page(document: dict[str, Any]) -> str:
    value = document["launcher"]["home"]["selectedPageId"]
    assert isinstance(value, str)
    return value


def complete_theme_style(
    background_color: str,
    border_color: str,
    text_color: str,
    font_id: str,
    border_radius: int,
) -> dict[str, Any]:
    return {
        "background": {"color": background_color, "opacity": 1},
        "border": {
            "color": border_color,
            "radius": border_radius,
            "widths": {"all": 2},
        },
        "margin": {"all": 3},
        "padding": {"all": 9},
        "text": {
            "color": text_color,
            "font": {"source": "bundled", "id": font_id},
            "sizeSp": 17,
        },
    }


def custom_theme_document() -> dict[str, Any]:
    return {
        "version": 1,
        "title": THEME_CUSTOM_TITLE,
        "background": {
            "color": "#19051F",
            "wallpaper": THEME_WALLPAPER_ARTIFACT_NAME,
        },
        "widgetStyle": complete_theme_style(
            "#4A1764",
            "#F0A8FF",
            "#FFFFFF",
            "jetbrains-mono",
            14,
        ),
    }


def assert_theme_applied(
    document: dict[str, Any],
) -> None:
    home = document["launcher"]["home"]
    assert home["selectedThemeId"] == THEME_CUSTOM_ID
    assert "background" not in home
    assert home["styleDefaults"] == {"widget": {}}
    pages = home["pages"]
    assert isinstance(pages, list)
    for page in pages:
        assert "style" not in page
        widgets = page["widgets"]
        assert isinstance(widgets, list)
        for widget in widgets:
            assert "style" not in widget


def write_test_wallpaper(target: Path) -> None:
    def png_chunk(chunk_type: bytes, payload: bytes) -> bytes:
        checksum = zlib.crc32(chunk_type + payload) & THEME_WALLPAPER_PNG_CRC_MASK
        return (
            struct.pack(">I", len(payload))
            + chunk_type
            + payload
            + struct.pack(">I", checksum)
        )

    pixel_row = THEME_WALLPAPER_COLOR * THEME_WALLPAPER_WIDTH
    image_data = b"".join(
        THEME_WALLPAPER_PNG_FILTER_NONE + pixel_row
        for _ in range(THEME_WALLPAPER_HEIGHT)
    )
    header = struct.pack(
        ">IIBBBBB",
        THEME_WALLPAPER_WIDTH,
        THEME_WALLPAPER_HEIGHT,
        THEME_WALLPAPER_PNG_BIT_DEPTH,
        THEME_WALLPAPER_PNG_COLOR_TYPE_RGBA,
        THEME_WALLPAPER_PNG_COMPRESSION_METHOD,
        THEME_WALLPAPER_PNG_FILTER_METHOD,
        THEME_WALLPAPER_PNG_INTERLACE_METHOD,
    )
    target.write_bytes(
        THEME_WALLPAPER_PNG_SIGNATURE
        + png_chunk(b"IHDR", header)
        + png_chunk(b"IDAT", zlib.compress(image_data))
        + png_chunk(b"IEND", b"")
    )


def page_ids(document: dict[str, Any]) -> list[str]:
    pages = document["launcher"]["home"]["pages"]
    assert isinstance(pages, list)
    return [str(page["id"]) for page in pages]


def page_title(document: dict[str, Any], page_id: str) -> str:
    page = configuration_page(document, page_id)
    title = page["title"]
    assert isinstance(title, str)
    return title


def page_script_address(document: dict[str, Any], page_id: str) -> str:
    """The 1H1V address the product uses for a page in scripts, HTML, WebSocket and MCP."""
    position = configuration_page(document, page_id)["position"]
    horizontal_page = int(position["column"]) + FIRST_SCRIPT_PAGE_COORDINATE
    vertical_page = int(position["row"]) + FIRST_SCRIPT_PAGE_COORDINATE
    return f"{horizontal_page}H{vertical_page}V"


def page_menu_title(document: dict[str, Any], page_id: str) -> str:
    """The exact heading the page menu and the launcher control sheet print for a page."""
    return PAGE_MENU_TITLE_FORMAT.format(
        page_title(document, page_id),
        page_script_address(document, page_id),
    )


def page_search_entry_label(document: dict[str, Any], page_id: str) -> str:
    """The exact label the page search sheet prints on a page's jump button."""
    return PAGE_SEARCH_ENTRY_FORMAT.format(
        page_title(document, page_id),
        page_script_address(document, page_id),
    )


def selected_page_widget_ids(document: dict[str, Any]) -> list[str]:
    home = document["launcher"]["home"]
    selected_page_id = selected_page(document)
    for page in home["pages"]:
        if page["id"] != selected_page_id:
            continue
        return [str(widget["id"]) for widget in page["widgets"]]
    raise AssertionError(f"configuration has no selected page: {selected_page_id}")


def wait_for_grid_layout_change(
    connection: socket.socket,
    original_cells: dict[str, dict[str, int]],
) -> dict[str, Any]:
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        document = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        cells = widget_cells(document)
        if cells != original_cells:
            return document
        time.sleep(0.1)
    raise AssertionError("the direct drag did not save a grid cell")
