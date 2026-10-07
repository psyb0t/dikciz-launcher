"""Direct Android UI interaction and layout helpers."""

from .device import *

def direct_tap_at(x: int, y: int) -> None:
    device_command(f"input tap {x} {y}")


def direct_tap(bounds: dict[str, int]) -> None:
    direct_tap_at(*center(bounds))


def direct_enter_text(
    bounds: dict[str, int],
    value: str,
    *,
    description: str | None = None,
) -> None:
    direct_tap(bounds)
    device_command(f"input text {value.replace(' ', DEVICE_INPUT_SPACE_ESCAPE)}")
    time.sleep(DIRECT_TEXT_INPUT_SETTLE_SECONDS)
    if description is not None:
        wait_for_physical_description_text(description, value)


def direct_hide_keyboard() -> None:
    device_command(f"input keyevent {KEYCODE_BACK}")


def direct_replace_text(bounds: dict[str, int], value: int) -> None:
    direct_tap(bounds)
    device_command(f"input keyevent {KEYCODE_DELETE}")
    device_command(f"input text {value}")


def direct_long_press(bounds: dict[str, int]) -> None:
    x, y = center(bounds)
    direct_long_press_at(x, y)


def direct_long_press_at_move_edge_start(bounds: dict[str, int]) -> None:
    # The top-edge move zone spans the whole card, so its center can sit under an
    # overlapping card. The leading end stays reachable while the card's own left
    # edge is visible.
    inset = round(TOP_EDGE_MOVE_GESTURE_HEIGHT_DP * device_density_scale())
    direct_long_press_at(bounds["left"] + inset, center(bounds)[1])


def direct_long_press_at(x: int, y: int) -> None:
    device_command(f"input swipe {x} {y} {x} {y} {DIRECT_LONG_PRESS_MILLISECONDS}")


def start_held_long_press_at(x: int, y: int) -> float:
    device_command(
        "input swipe {x} {y} {x} {y} {duration} &".format(
            x=x,
            y=y,
            duration=DIRECT_HELD_LONG_PRESS_MILLISECONDS,
        ),
    )
    return time.monotonic() + DIRECT_HELD_LONG_PRESS_MILLISECONDS / 1_000


def start_held_swipe(
    start: tuple[int, int],
    end: tuple[int, int],
    duration_milliseconds: int = DIRECT_HELD_PAGE_SWIPE_MILLISECONDS,
) -> float:
    device_command(
        "input swipe {start_x} {start_y} {end_x} {end_y} {duration_milliseconds} &".format(
            start_x=start[0],
            start_y=start[1],
            end_x=end[0],
            end_y=end[1],
            duration_milliseconds=duration_milliseconds,
        ),
    )
    return time.monotonic() + duration_milliseconds / 1_000


def direct_swipe(
    start: tuple[int, int],
    end: tuple[int, int],
    duration_milliseconds: int = DIRECT_SWIPE_DURATION_MILLISECONDS,
) -> None:
    device_command(
        "input swipe {start_x} {start_y} {end_x} {end_y} {duration_milliseconds}".format(
            start_x=start[0],
            start_y=start[1],
            end_x=end[0],
            end_y=end[1],
            duration_milliseconds=duration_milliseconds,
        ),
    )


def physical_resize_action_bounds() -> dict[str, int]:
    return physical_bounds_for(
        lambda node: node.get("text") == "Resize" or node.get("content-desc") == "Resize",
    )


def physical_edit_action_bounds() -> dict[str, int]:
    return physical_text_bounds(NATIVE_WIDGET_EDIT_LABEL)


def physical_delete_action_bounds() -> dict[str, int]:
    return physical_text_bounds("Delete")


def physical_text_bounds(text: str) -> dict[str, int]:
    expected = text.casefold()
    return physical_bounds_for(lambda node: node.get("text", "").casefold() == expected)


def physical_text_bounds_from_nodes(
    nodes: list[ElementTree.Element],
    text: str,
) -> dict[str, int]:
    expected = text.casefold()
    return physical_bounds_from_nodes(
        nodes,
        lambda node: node.get("text", "").casefold() == expected,
    )


def physical_text_node(text: str) -> ElementTree.Element:
    expected = text.casefold()
    for node in physical_nodes():
        if node.get("text", "").casefold() == expected:
            return node
    raise AssertionError(f"UIAutomator could not find visible text {text!r}")


def physical_button_bounds(text: str) -> dict[str, int]:
    expected = text.casefold()
    return physical_bounds_for(
        lambda node: node.get("class") == "android.widget.Button"
        and node.get("text", "").casefold() == expected,
    )


def wait_for_physical_text_bounds(text: str) -> dict[str, int]:
    deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
    visible_texts: list[str] = []
    while time.monotonic() < deadline:
        nodes = physical_nodes()
        visible_texts = physical_visible_texts_from_nodes(nodes)
        try:
            return physical_text_bounds_from_nodes(nodes, text)
        except AssertionError:
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(
        f"UIAutomator could not find visible text {text!r}; visible text: {visible_texts!r}",
    )


def wait_for_physical_button_bounds(text: str) -> dict[str, int]:
    deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        try:
            return physical_button_bounds(text)
        except AssertionError:
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    return physical_button_bounds(text)


def physical_description_bounds(description: str) -> dict[str, int]:
    return physical_bounds_for(lambda node: node.get("content-desc") == description)


def app_shortcut_content_description(title: str, style_label: str) -> str:
    return APP_SHORTCUT_CONTENT_DESCRIPTION_FORMAT.format(title, style_label)


def direct_open_widget_editor(title: str) -> None:
    direct_long_press(
        physical_description_bounds(WIDGET_MOVE_HANDLE_DESCRIPTION.format(title)),
    )
    direct_tap(physical_edit_action_bounds())


def physical_description_text(description: str) -> str:
    for node in physical_nodes():
        if node.get("content-desc") == description:
            return node.get("text", "")
    raise AssertionError(f"UIAutomator could not find {description}")


def wait_for_physical_description_text(description: str, expected: str) -> None:
    deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        if physical_description_text(description) == expected:
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    assert physical_description_text(description) == expected


def wait_for_physical_description_bounds(description: str) -> dict[str, int]:
    deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        try:
            return physical_description_bounds(description)
        except AssertionError:
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    return physical_description_bounds(description)


def scroll_appearance_editor_to_description(description: str) -> dict[str, int]:
    for _ in range(APPEARANCE_EDITOR_SCROLL_ATTEMPTS):
        try:
            return physical_description_bounds(description)
        except AssertionError:
            editor_bounds = physical_description_bounds(APPEARANCE_EDITOR_DESCRIPTION)
            x, _ = center(editor_bounds)
            direct_swipe(
                (x, editor_bounds["bottom"] - APPEARANCE_EDITOR_SCROLL_INSET_PIXELS),
                (x, editor_bounds["top"] + APPEARANCE_EDITOR_SCROLL_INSET_PIXELS),
            )
    return physical_description_bounds(description)


def physical_widget_bounds() -> dict[str, int]:
    return physical_widget_bounds_for_title(FIXTURE_WIDGET_TITLES["welcome"])


def physical_widget_bounds_for_title(title: str) -> dict[str, int]:
    return physical_bounds_for(lambda node: node.get("content-desc") == title)


def physical_widget_move_gesture_bounds() -> dict[str, int]:
    description = WIDGET_MOVE_HANDLE_DESCRIPTION.format(FIXTURE_WIDGET_TITLES["welcome"])
    return physical_bounds_for(lambda node: node.get("content-desc") == description)


def assert_move_gesture_spans_widget_top_edge() -> None:
    widget_bounds = physical_widget_bounds()
    gesture_bounds = physical_widget_move_gesture_bounds()
    expected_height = round(TOP_EDGE_MOVE_GESTURE_HEIGHT_DP * device_density_scale())
    assert gesture_bounds["left"] == widget_bounds["left"]
    assert gesture_bounds["right"] == widget_bounds["right"]
    assert gesture_bounds["top"] == widget_bounds["top"]
    assert gesture_bounds["bottom"] - gesture_bounds["top"] == expected_height


def wait_for_landscape_page_viewport() -> dict[str, int]:
    deadline = time.monotonic() + DEVICE_UI_TIMEOUT_SECONDS
    bounds: dict[str, int] = {}
    while time.monotonic() < deadline:
        bounds = physical_page_scroll_bounds()
        if bounds["right"] - bounds["left"] > bounds["bottom"] - bounds["top"]:
            return bounds
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"page viewport did not rotate to landscape: {bounds}")


def physical_page_indicator_bounds(axis: str = "horizontal") -> dict[str, int]:
    description_prefix = {
        "horizontal": HORIZONTAL_PAGE_INDICATOR_DESCRIPTION_PREFIX,
        "vertical": VERTICAL_PAGE_INDICATOR_DESCRIPTION_PREFIX,
    }[axis]
    return physical_bounds_for(
        lambda node: node.get("content-desc", "").startswith(description_prefix),
    )


def page_indicator_dot_center(
    bounds: dict[str, int],
    dot_index: int,
    page_count: int,
    axis: str = "horizontal",
) -> tuple[int, int]:
    assert page_count > 0
    assert 0 <= dot_index < page_count
    dot_gap_pixels = round(PAGE_INDICATOR_DOT_GAP_DP * device_density_scale())
    strip_length = dot_gap_pixels * (page_count - 1)
    if axis == "horizontal":
        first_dot_x = center(bounds)[0] - strip_length // 2
        return (first_dot_x + dot_index * dot_gap_pixels, center(bounds)[1])
    if axis == "vertical":
        first_dot_y = center(bounds)[1] - strip_length // 2
        return (center(bounds)[0], first_dot_y + dot_index * dot_gap_pixels)
    raise AssertionError(f"unknown page indicator axis: {axis}")


def page_indicator_empty_rail_point(axis: str, after_dots: bool) -> tuple[int, int]:
    bounds = physical_page_indicator_bounds(axis)
    if axis == "horizontal":
        x = (
            bounds["right"] - DIRECT_BOTTOM_EDGE_INSET_PIXELS
            if after_dots
            else bounds["left"] + DIRECT_BOTTOM_EDGE_INSET_PIXELS
        )
        return x, center(bounds)[1]
    if axis == "vertical":
        y = (
            bounds["bottom"] - DIRECT_BOTTOM_EDGE_INSET_PIXELS
            if after_dots
            else bounds["top"] + DIRECT_BOTTOM_EDGE_INSET_PIXELS
        )
        return center(bounds)[0], y
    raise AssertionError(f"unknown page indicator axis: {axis}")


def physical_bounds_for(predicate: Callable[[ElementTree.Element], bool]) -> dict[str, int]:
    return physical_bounds_from_nodes(physical_nodes(), predicate)


def physical_bounds_from_nodes(
    nodes: list[ElementTree.Element],
    predicate: Callable[[ElementTree.Element], bool],
) -> dict[str, int]:
    for node in nodes:
        if not predicate(node):
            continue
        raw_bounds = node.get("bounds")
        if raw_bounds is None:
            continue
        values = [int(value) for value in raw_bounds.replace("][", ",").strip("[]").split(",")]
        if len(values) == 4:
            return dict(zip(("left", "top", "right", "bottom"), values, strict=True))
    raise AssertionError("UIAutomator could not find the requested visible control")


def physical_visible_texts() -> list[str]:
    return physical_visible_texts_from_nodes(physical_nodes())


def physical_visible_texts_from_nodes(nodes: list[ElementTree.Element]) -> list[str]:
    return sorted(
        {
            text
            for node in nodes
            for text in (node.get("text", ""), node.get("content-desc", ""))
            if text
        },
    )


def physical_page_scroll_bounds() -> dict[str, int]:
    return physical_bounds_for(
        lambda node: node.get("resource-id") == PAGE_SCROLL_RESOURCE_ID,
    )


def wait_for_page_viewport_orientation(orientation: str) -> dict[str, int]:
    assert orientation in EMULATOR_DEVICE_ORIENTATIONS
    expected_landscape = orientation == EMULATOR_DEVICE_ORIENTATION_LANDSCAPE
    deadline = time.monotonic() + DEVICE_UI_TIMEOUT_SECONDS
    bounds: dict[str, int] = {}
    while time.monotonic() < deadline:
        bounds = physical_page_scroll_bounds()
        is_landscape = bounds["right"] - bounds["left"] > bounds["bottom"] - bounds["top"]
        if is_landscape == expected_landscape:
            return bounds
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"page viewport did not rotate to {orientation}: {bounds}")


def assert_compact_page_indicator_bounds(
    horizontal_bounds: dict[str, int],
    vertical_bounds: dict[str, int],
) -> None:
    expected_thickness = round(PAGE_INDICATOR_THICKNESS_DP * device_density_scale())
    assert horizontal_bounds["bottom"] - horizontal_bounds["top"] == expected_thickness
    assert vertical_bounds["right"] - vertical_bounds["left"] == expected_thickness


def assert_page_chrome_reaches_screen_edges(
    horizontal_bounds: dict[str, int],
    vertical_bounds: dict[str, int],
    page_bounds: dict[str, int],
) -> None:
    window_bounds = node_bounds(physical_nodes()[0])
    assert window_bounds is not None
    assert horizontal_bounds["left"] == window_bounds["left"]
    assert horizontal_bounds["right"] == window_bounds["right"]
    assert page_bounds["left"] == window_bounds["left"]
    assert vertical_bounds["right"] == horizontal_bounds["right"]
    assert page_bounds["bottom"] == horizontal_bounds["top"]


def assert_page_viewport_starts_at_top(snapshot: dict[str, Any]) -> None:
    scroll = snapshot["scroll"]
    assert isinstance(scroll, dict)
    assert isinstance(scroll["range"], int)
    assert scroll["range"] >= 0
    assert scroll["y"] == 0
    assert isinstance(scroll["extent"], int)
    assert scroll["extent"] > 0


def assert_widget_frame_fills_page_with_theme_margin(
    widget_bounds: dict[str, int],
    page_bounds: dict[str, int],
) -> None:
    expected_margin = round(DEFAULT_THEME_WIDGET_MARGIN_DP * device_density_scale())
    expected_edges = {
        "left": page_bounds["left"] + expected_margin,
        "top": page_bounds["top"] + expected_margin,
        "right": page_bounds["right"] - expected_margin,
        "bottom": page_bounds["bottom"] - expected_margin,
    }
    for edge, expected_value in expected_edges.items():
        assert abs(widget_bounds[edge] - expected_value) <= PHYSICAL_BOUNDS_TOLERANCE_PIXELS


def node_bounds(node: ElementTree.Element) -> dict[str, int] | None:
    raw_bounds = node.get("bounds")
    if raw_bounds is None:
        return None
    values = [int(value) for value in raw_bounds.replace("][", ",").strip("[]").split(",")]
    if len(values) != 4:
        return None
    return dict(zip(("left", "top", "right", "bottom"), values, strict=True))


def bounds_contain(container: dict[str, int], candidate: dict[str, int]) -> bool:
    return (
        container["left"] <= candidate["left"]
        and container["top"] <= candidate["top"]
        and candidate["right"] <= container["right"]
        and candidate["bottom"] <= container["bottom"]
    )


def visible_provider_content(expected_content: str | None = None) -> ElementTree.Element:
    nodes = physical_nodes()
    host = next(
        (node for node in nodes if node.get("class") == APP_WIDGET_HOST_VIEW_CLASS_NAME),
        None,
    )
    assert host is not None, "UIAutomator could not find the Android AppWidget host"
    host_bounds = node_bounds(host)
    assert host_bounds is not None
    for node in nodes:
        if node is host:
            continue
        content = node.get("text") or node.get("content-desc")
        if not content:
            continue
        if expected_content is not None and content != expected_content:
            continue
        bounds = node_bounds(node)
        if bounds is not None and bounds_contain(host_bounds, bounds):
            return node
    if expected_content is None:
        raise AssertionError("the Android AppWidget host rendered no visible provider content")
    raise AssertionError(f"the Android AppWidget host did not render {expected_content!r}")


def provider_host_bounds() -> dict[str, int]:
    return physical_bounds_for(
        lambda node: node.get("class") == APP_WIDGET_HOST_VIEW_CLASS_NAME,
    )


def wait_for_provider_host_height_at_least(minimum_height: int) -> dict[str, int]:
    # An injected drag lands within the same physical bounds tolerance the rest
    # of this suite accepts, so the resize can stop a pixel or two short.
    accepted_height = minimum_height - PHYSICAL_BOUNDS_TOLERANCE_PIXELS
    deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        bounds = provider_host_bounds()
        if bounds["bottom"] - bounds["top"] >= accepted_height:
            return bounds
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    bounds = provider_host_bounds()
    actual_height = bounds["bottom"] - bounds["top"]
    raise AssertionError(
        f"the live AppWidget host height stayed at {actual_height}px, expected at least {accepted_height}px"
    )


def wait_for_visible_provider_content(expected_content: str | None = None) -> ElementTree.Element:
    deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        try:
            return visible_provider_content(expected_content)
        except AssertionError:
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    return visible_provider_content(expected_content)


def physical_nodes() -> list[ElementTree.Element]:
    run_device_operation("uiautomator-dump")
    document = ElementTree.parse(ARTIFACT_DIRECTORY / "window.xml")
    return list(document.iter("node"))


def physical_node_index(content_description: str) -> int:
    for index, node in enumerate(physical_nodes()):
        if node.get("content-desc") == content_description:
            return index
    raise AssertionError(f"UIAutomator could not find {content_description}")


def physical_drawing_order(content_description: str) -> int:
    for node in physical_nodes():
        if node.get("content-desc") != content_description:
            continue
        drawing_order = node.get("drawing-order")
        assert drawing_order is not None
        return int(drawing_order)
    raise AssertionError(f"UIAutomator could not find {content_description}")


def native_grid_cell_pitch() -> tuple[int, int]:
    """Distance from one cell's leading edge to the next, per axis.

    A resize snaps to whichever cell the pointer is over, so a drag that
    should change a span by one has to carry the pointer into the neighbouring
    cell. One pitch does that from anywhere inside the starting cell, in both
    the shrinking and the growing direction, which a fixed pixel distance
    cannot do while the two axes have different cell sizes.
    """
    bounds = physical_page_scroll_bounds()
    scale = device_density_scale()
    padding = round(NATIVE_GRID_OUTER_PADDING_DP * scale)
    gap = round(NATIVE_GRID_GAP_DP * scale)
    width = bounds["right"] - bounds["left"] - 2 * padding - (NATIVE_GRID_COLUMNS - 1) * gap
    height = bounds["bottom"] - bounds["top"] - 2 * padding - (NATIVE_GRID_ROWS - 1) * gap
    return (
        round(width / NATIVE_GRID_COLUMNS) + gap,
        round(height / NATIVE_GRID_ROWS) + gap,
    )


def resize_handle_coordinate(
    bounds: dict[str, int],
    horizontal: str,
    vertical: str,
) -> tuple[int, int]:
    point_inset = round(RESIZE_HANDLE_POINT_RADIUS_DP * device_density_scale())
    x = {
        "start": bounds["left"] + point_inset,
        "center": center(bounds)[0],
        "end": bounds["right"] - point_inset,
    }[horizontal]
    y = {
        "start": bounds["top"] + point_inset,
        "center": center(bounds)[1],
        "end": bounds["bottom"] - point_inset,
    }[vertical]
    return x, y


def physical_front_window_bounds_from_nodes(
    nodes: list[ElementTree.Element],
) -> dict[str, int]:
    """Bounds of the window currently in front.

    UIAutomator dumps only the front window, so while a dialog is open its own
    outermost node is the first one carrying bounds and the page behind it is
    not in the tree at all.
    """
    return physical_bounds_from_nodes(nodes, lambda node: node.get("bounds") is not None)


def scroll_dialog_to_text(text: str) -> dict[str, int]:
    """Scroll the open dialog until the named control is physically on screen."""
    for _ in range(DIALOG_SCROLL_ATTEMPTS):
        nodes = physical_nodes()
        # A dialog button renders its label in capitals, and the bounds lookup
        # already folds case, so the check for it has to fold case too.
        expected = text.casefold()
        if any(
            visible.casefold() == expected
            for visible in physical_visible_texts_from_nodes(nodes)
        ):
            return physical_text_bounds_from_nodes(nodes, text)
        # The page viewport is not in a dialog's dump, so the scroll is aimed
        # at the middle of the dialog itself.
        viewport = physical_front_window_bounds_from_nodes(nodes)
        scroll_x = (viewport["left"] + viewport["right"]) // 2
        scroll_y = (viewport["top"] + viewport["bottom"]) // 2
        direct_swipe(
            (scroll_x, scroll_y + DIALOG_SCROLL_TRAVEL_PIXELS // 2),
            (scroll_x, scroll_y - DIALOG_SCROLL_TRAVEL_PIXELS // 2),
            DIALOG_SCROLL_DURATION_MILLISECONDS,
        )
    raise AssertionError(f"dialog control never became physically reachable: {text}")


def wait_for_physical_text_absent(text: str) -> None:
    """Wait until the exact visible text leaves the physical screen."""
    deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
    visible_texts: list[str] = []
    while time.monotonic() < deadline:
        visible_texts = physical_visible_texts()
        if text not in visible_texts:
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"visible text {text!r} never left the screen; visible: {visible_texts!r}")
