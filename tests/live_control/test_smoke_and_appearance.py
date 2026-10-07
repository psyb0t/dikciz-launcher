"""Control-plane smoke coverage and visible appearance behavior."""

from . import *

def test_release_websocket_smoke_with_mutations() -> None:
    result = AUTOMATION.run_smoke_test(
        argparse.Namespace(
            host=AUTOMATION_HOST,
            port=AUTOMATION_PORT,
            expected_control_port=DEVICE_AUTOMATION_PORT,
            mutate=True,
        ),
    )
    assert result["status"] == "passed"
    assert result["mutations"] is True


def test_release_mcp_smoke_with_mutations() -> None:
    result = MCP.run_smoke_test(
        argparse.Namespace(
            host=AUTOMATION_HOST,
            port=MCP_PORT,
            expected_control_port=DEVICE_MCP_PORT,
            mutate=True,
        ),
    )
    assert result["status"] == "passed"
    assert result["mutations"] is True


def test_fixture_cards_render_at_the_configured_grid_cells() -> None:
    assert fixture_widget_cells() == {
        "welcome": {"column": 0, "row": 0, "columnSpan": 2, "rowSpan": 1},
        "focus": {"column": 2, "row": 0, "columnSpan": 2, "rowSpan": 1},
        "fixture-detail": {"column": 0, "row": 1, "columnSpan": 4, "rowSpan": 1},
    }
    assert_visible_configured_layout(fixture_document())
    assert_move_gesture_spans_widget_top_edge()


@pytest.mark.parametrize("font_id", BUNDLED_FONT_IDS)
def test_bundled_font_defaults_reload_on_the_isolated_android_device(
    websocket_control: socket.socket,
    font_id: str,
) -> None:
    configured = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configure_default_font(configured, font_id)
    persisted = AUTOMATION.require_configuration(
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        ),
    )
    assert persisted["launcher"]["home"]["styleDefaults"]["widget"]["text"]["font"] == {
        "source": "bundled",
        "id": font_id,
    }
    wait_for_physical_text_bounds(FIXTURE_WIDGET_TITLES["welcome"])
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / f"bundled-font-{font_id}.png",
    )


def test_widget_style_and_lock_render_then_unlock_from_the_page_menu(
    websocket_control: socket.socket,
) -> None:
    before = physical_widget_bounds()
    configured = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configure_welcome_style(
        configured,
        {
            "background": {"color": "#173A5E", "opacity": 0.75},
            "border": {
                "color": "#FFFFFFFF",
                "radius": APPEARANCE_WIDGET_BORDER_RADIUS_DP,
                "widths": {"all": 2},
            },
            "margin": {
                "left": APPEARANCE_WIDGET_MARGIN_LEFT_DP,
                "top": APPEARANCE_WIDGET_MARGIN_TOP_DP,
                "right": APPEARANCE_WIDGET_MARGIN_RIGHT_DP,
                "bottom": APPEARANCE_WIDGET_MARGIN_BOTTOM_DP,
            },
            "padding": {"all": 10, "bottom": 14},
            "text": {
                "color": "#FFFFFFFF",
                "sizeSp": 18,
                "font": {"source": "bundled", "id": "comic-neue"},
            },
        },
    )
    configured_welcome = configuration_widget(configured, "welcome")
    configured_welcome["locked"] = True
    persisted = AUTOMATION.require_configuration(
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        ),
    )
    persisted_welcome = configuration_widget(persisted, "welcome")
    assert persisted_welcome["locked"] is True
    assert persisted_welcome["style"] == configured_welcome["style"]
    wait_for_snapshot_node(websocket_control, WELCOME_MOVE_HANDLE_SEMANTIC_ID, should_exist=False)
    styled = physical_widget_bounds()
    density_scale = device_density_scale()
    assert abs(
        styled["left"] - before["left"] - round(
            (APPEARANCE_WIDGET_MARGIN_LEFT_DP - DEFAULT_THEME_WIDGET_MARGIN_DP) * density_scale,
        ),
    ) <= PHYSICAL_BOUNDS_TOLERANCE_PIXELS
    assert abs(
        styled["top"] - before["top"] - round(
            (APPEARANCE_WIDGET_MARGIN_TOP_DP - DEFAULT_THEME_WIDGET_MARGIN_DP) * density_scale,
        ),
    ) <= PHYSICAL_BOUNDS_TOLERANCE_PIXELS
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / "styled-locked-widget.png",
    )

    open_page_menu_on_selected_page()
    direct_tap(wait_for_physical_text_bounds(PAGE_MENU_WIDGET_LOCKS_LABEL))
    direct_tap(wait_for_physical_description_bounds(WELCOME_LOCK_INPUT_DESCRIPTION))
    direct_tap(wait_for_physical_button_bounds("Save"))

    wait_for_snapshot_node(websocket_control, WELCOME_MOVE_HANDLE_SEMANTIC_ID)
    unlocked = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert configuration_widget(unlocked, "welcome")["locked"] is False

    direct_long_press(physical_widget_move_gesture_bounds())
    direct_tap(wait_for_physical_button_bounds("Appearance"))
    scroll_appearance_editor_to_description(APPEARANCE_INCREASE_BORDER_RADIUS_DESCRIPTION)
    wait_for_physical_description_text(
        APPEARANCE_BORDER_RADIUS_LABEL,
        str(APPEARANCE_WIDGET_BORDER_RADIUS_DP),
    )
    direct_tap(wait_for_physical_button_bounds("Cancel"))


def test_direct_appearance_editors_persist_widget_and_default_styles(
    websocket_control: socket.socket,
) -> None:
    direct_long_press(physical_widget_move_gesture_bounds())
    wait_for_snapshot_node(websocket_control, APPEARANCE_WIDGET_ACTION_SEMANTIC_ID)
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / APPEARANCE_WIDGET_ACTIONS_ARTIFACT_NAME,
    )
    direct_tap(wait_for_physical_button_bounds("Appearance"))
    wait_for_snapshot_node(
        websocket_control,
        f"{APPEARANCE_WIDGET_SCOPE_PREFIX}:{APPEARANCE_FIELD_BACKGROUND_COLOR}",
    )
    wait_for_physical_text_bounds("Appearance: Dikciz")
    wait_for_physical_description_bounds(APPEARANCE_CHOOSE_COLOR_DESCRIPTION)
    wait_for_physical_description_bounds("Opacity")
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / APPEARANCE_DIALOG_ARTIFACT_NAME,
    )
    direct_tap(wait_for_physical_description_bounds(APPEARANCE_CHOOSE_COLOR_DESCRIPTION))
    wait_for_physical_description_bounds(APPEARANCE_USE_WIDGET_COLOR_DESCRIPTION)
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / APPEARANCE_COLOR_PICKER_ARTIFACT_NAME,
    )
    direct_tap(wait_for_physical_description_bounds(APPEARANCE_USE_WIDGET_COLOR_DESCRIPTION))
    direct_tap(wait_for_physical_description_bounds(APPEARANCE_INCREASE_OPACITY_DESCRIPTION))
    direct_tap(
        scroll_appearance_editor_to_description(
            APPEARANCE_INCREASE_BORDER_RADIUS_DESCRIPTION,
        ),
    )
    direct_tap(scroll_appearance_editor_to_description(APPEARANCE_CHOOSE_FONT_DESCRIPTION))
    wait_for_physical_description_bounds(APPEARANCE_COMIC_NEUE_DESCRIPTION)
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / APPEARANCE_FONT_PICKER_ARTIFACT_NAME,
    )
    direct_tap(wait_for_physical_description_bounds(APPEARANCE_COMIC_NEUE_DESCRIPTION))
    direct_tap(wait_for_physical_button_bounds("Save"))
    configured = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert configuration_widget(configured, "welcome")["style"] == {
        "background": {
            "color": APPEARANCE_WIDGET_BACKGROUND_COLOR,
            "opacity": APPEARANCE_WIDGET_BACKGROUND_OPACITY,
        },
        "border": {"radius": APPEARANCE_WIDGET_EDITOR_BORDER_RADIUS_DP},
        "text": {
            "font": {
                "id": "comic-neue",
                "source": "bundled",
            },
        },
    }

    open_page_menu_on_selected_page()
    direct_tap(wait_for_physical_text_bounds(SETTINGS_APP_LABEL))
    wait_for_snapshot_node(websocket_control, SETTINGS_WIDGET_APPEARANCE_SEMANTIC_ID)
    direct_tap(wait_for_physical_button_bounds("New widget default appearance"))
    wait_for_snapshot_node(
        websocket_control,
        f"{APPEARANCE_WIDGET_DEFAULT_SCOPE_PREFIX}:{APPEARANCE_FIELD_BACKGROUND_COLOR}",
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=(
            f"{APPEARANCE_WIDGET_DEFAULT_SCOPE_PREFIX}:{APPEARANCE_FIELD_BACKGROUND_COLOR}"
        ),
        text=APPEARANCE_WIDGET_DEFAULT_BACKGROUND_COLOR,
    )
    direct_tap(wait_for_physical_button_bounds("Save"))
    configured = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert configured["launcher"]["home"]["styleDefaults"]["widget"]["background"] == {
        "color": APPEARANCE_WIDGET_DEFAULT_BACKGROUND_COLOR,
    }
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / APPEARANCE_ARTIFACT_NAME,
    )
    run_device_operation("uiautomator-dump")
    assert (ARTIFACT_DIRECTORY / APPEARANCE_ARTIFACT_NAME).stat().st_size > 0
    assert (ARTIFACT_DIRECTORY / APPEARANCE_COLOR_PICKER_ARTIFACT_NAME).stat().st_size > 0
    assert (ARTIFACT_DIRECTORY / APPEARANCE_DIALOG_ARTIFACT_NAME).stat().st_size > 0
    assert (ARTIFACT_DIRECTORY / APPEARANCE_FONT_PICKER_ARTIFACT_NAME).stat().st_size > 0
    assert (ARTIFACT_DIRECTORY / APPEARANCE_WIDGET_ACTIONS_ARTIFACT_NAME).stat().st_size > 0
    assert (ARTIFACT_DIRECTORY / "window.xml").stat().st_size > 0
