"""Page, wrapper and HTML instance state ownership on real shared storage."""

import base64
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier

from . import *

INSTANCE_WRAPPER = "instance-wrapper"
OTHER_WRAPPER = "other-wrapper"
INSTANCE_IDS = ("first", "second", "third")
INSTANCE_HTML = "".join(
    f'<article class="card" data-dikciz-item-id="{identifier}"><h2>{identifier}</h2>'
    f'<p data-value="{identifier}"></p></article>'
    for identifier in INSTANCE_IDS
)
INSTANCE_JAVASCRIPT = """
window.addEventListener("dikciz-ready", () => {
    const render = () => {
        for (const element of document.querySelectorAll("[data-value]")) {
            element.textContent = selfWidget.itemFor(element).state.text || "empty";
        }
    };
    render();
    window.addEventListener("dikciz-item-state", render);
    window.addEventListener("dikciz-item-state", () => {
        const renderedValues = Object.fromEntries([...document.querySelectorAll("[data-value]")]
            .map(element => [element.getAttribute("data-value"), element.textContent]));
        selfWidget.patchState({...selfWidget.state, renderedValues})
            .catch(error => { document.body.dataset.probeError = error.code || "failed"; });
    });
    const points = {};
    for (const card of document.querySelectorAll("[data-dikciz-item-id]")) {
        const rect = card.getBoundingClientRect();
        points[card.getAttribute("data-dikciz-item-id")] = {x:rect.x + rect.width / 2, y:rect.y + rect.height / 2};
    }
    selfWidget.patchState({...selfWidget.state, probe:{points, width:innerWidth}, generation:crypto.randomUUID()})
        .catch(error => { document.body.setAttribute("data-error", error.code || "failed"); });
});
"""


@pytest.fixture(autouse=True)
def instance_evidence(websocket_control, isolated_launcher_fixture, request):
    yield
    prefix = ARTIFACT_DIRECTORY / ("instance-" + request.node.name.replace("/", "_").replace(".", "_"))
    prefix.with_suffix(".json").write_text(json.dumps({
        "configuration":saved_configuration(websocket_control),
        "logs":AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SCRIPT_LOGS),
        "snapshot":AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT),
    }), encoding="utf-8")


def instance_configuration(original):
    configured = copy.deepcopy(original)
    for page_id, wrapper_id, prefix in (
        (FIXTURE_HOME_PAGE_ID, INSTANCE_WRAPPER, "home"),
        (FIXTURE_NOTES_PAGE_ID, OTHER_WRAPPER, "other"),
    ):
        configuration_page(configured, page_id)["widgets"] = [{
            "id":wrapper_id, "type":"html", "title":"Instance state", "enabled":True,
            "html":INSTANCE_HTML,
            "css":"body{margin:0;color:white;background:#122133;font:16px sans-serif}.card{height:110px;box-sizing:border-box;padding:12px;margin:8px;background:#20394e}h2,p{margin:6px}",
            "javascript":INSTANCE_JAVASCRIPT + "\n/* fixture " + str(time.monotonic_ns()) + " */", "state":{},
            "itemStates":{identifier:{"text":prefix + "-" + identifier} for identifier in INSTANCE_IDS},
            "cell":{"column":0,"row":0,"columnSpan":4,"rowSpan":6},
        }]
    configured["launcher"]["home"]["selectedPageId"] = FIXTURE_HOME_PAGE_ID
    return configured


def saved_configuration(connection):
    return AUTOMATION.require_configuration(AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET))


def instance_path(page_id, wrapper_id, item_id=None):
    root = f"/sdcard/Dikciz/pages/{page_id}/widgets/{wrapper_id}"
    return root if item_id is None else root + "/items/" + item_id


def read_json_file(connection, path):
    result = AUTOMATION.request(connection, "fileRead", path=path)
    return json.loads(base64.b64decode(result["dataBase64"]))


def write_json_file(connection, path, value):
    return AUTOMATION.request(connection, "fileWrite", path=path, overwrite=True,
                              dataBase64=base64.b64encode(json.dumps(value).encode()).decode())


def wait_instance(connection, identifier, expected, wrapper_id=INSTANCE_WRAPPER, require_runtime=False):
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    observed = None
    while time.monotonic() < deadline:
        widget = configuration_widget(saved_configuration(connection), wrapper_id)
        observed = widget.get("itemStates", {}).get(identifier)
        if observed == expected and (not require_runtime or widget["state"].get("probe")):
            return widget
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"Instance state did not arrive: {identifier}={observed!r}")


def directory_names(connection, path):
    listing = AUTOMATION.request(connection, "fileList", path=path)
    return {entry["name"] for entry in listing["items"]}


def test_html_document_dynamic_legacy_namespaces_are_scoped_watched_and_deleted(websocket_control):
    configured = instance_configuration(saved_configuration(websocket_control))
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    widget = wait_instance(websocket_control, "second", {"text":"home-second"}, require_runtime=True)
    generation = widget["state"]["generation"]
    root = instance_path(FIXTURE_HOME_PAGE_ID, INSTANCE_WRAPPER)
    other = instance_path(FIXTURE_NOTES_PAGE_ID, OTHER_WRAPPER)
    descriptor = read_json_file(websocket_control, root + ".json")
    assert "state" not in descriptor and "itemStates" not in descriptor
    assert read_json_file(websocket_control, root + "/state.json")["generation"] == generation
    for identifier in INSTANCE_IDS:
        assert read_json_file(websocket_control, root + "/items/" + identifier + "/state.json") == {"text":"home-" + identifier}
        assert read_json_file(websocket_control, other + "/items/" + identifier + "/state.json") == {"text":"other-" + identifier}
    write_json_file(websocket_control, root + "/items/second/state.json", {"text":"changed-on-disk"})
    updated = wait_instance(websocket_control, "second", {"text":"changed-on-disk"})
    assert updated["state"]["generation"] == generation
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        observed = configuration_widget(saved_configuration(websocket_control), INSTANCE_WRAPPER)
        if observed["state"].get("renderedValues", {}).get("second") == "changed-on-disk": break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    else: raise AssertionError("The file watcher did not update the live DOM")
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "html-legacy-state-rendered.png")
    AUTOMATION.request(websocket_control, "fileWrite", path=root + "/items/second/custom.txt", overwrite=True, dataBase64="Zml4dHVyZQ==")
    edited = saved_configuration(websocket_control)
    edited_widget = configuration_widget(edited, INSTANCE_WRAPPER)
    removed = '<article class="card" data-dikciz-item-id="second"><h2>second</h2><p data-value="second"></p></article>'
    assert removed in edited_widget["html"]
    edited_widget["html"] = edited_widget["html"].replace(removed, "")
    edited_widget["itemStates"].pop("second")
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=edited)
    widget = configuration_widget(saved_configuration(websocket_control), INSTANCE_WRAPPER)
    assert set(widget["itemStates"]) == {"first", "third"}
    assert directory_names(websocket_control, root + "/items") == {"first", "third"}
    assert read_json_file(websocket_control, other + "/items/second/state.json") == {"text":"other-second"}
    assert read_json_file(websocket_control, root + "/items/first/state.json") == {"text":"home-first"}
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_LONG_PRESS, semanticId="widget:" + INSTANCE_WRAPPER + ":move-handle")
    wait_for_snapshot_node(websocket_control, "widget:" + INSTANCE_WRAPPER + ":delete")
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId="widget:" + INSTANCE_WRAPPER + ":delete")
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        names = directory_names(websocket_control, f"/sdcard/Dikciz/pages/{FIXTURE_HOME_PAGE_ID}/widgets")
        if INSTANCE_WRAPPER not in names and INSTANCE_WRAPPER + ".json" not in names:
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    else:
        raise AssertionError(f"Deleted wrapper files remained: {names!r}")
    assert read_json_file(websocket_control, other + "/items/third/state.json") == {"text":"other-third"}


@pytest.mark.parametrize("invalid", ["../outside", "", ".", "x" * 81])
def test_widget_instance_state_rejects_invalid_ownership_ids(websocket_control, invalid):
    original = saved_configuration(websocket_control)
    configured = instance_configuration(original)
    configuration_widget(configured, INSTANCE_WRAPPER)["itemStates"] = {invalid:{"text":"refused"}}
    _, response = AUTOMATION.request_error_with_id(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    assert response["code"] == "validation_failed"
    assert saved_configuration(websocket_control) == original


def test_widget_instance_state_migrates_legacy_embedded_state(websocket_control):
    configured = instance_configuration(saved_configuration(websocket_control))
    configuration_widget(configured, INSTANCE_WRAPPER)["javascript"] = ""
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    wait_instance(websocket_control, "first", {"text":"home-first"})
    root = instance_path(FIXTURE_HOME_PAGE_ID, INSTANCE_WRAPPER)
    descriptor = read_json_file(websocket_control, root + ".json")
    descriptor["state"] = {"legacy":"kept"}
    write_json_file(websocket_control, root + ".json", descriptor)
    AUTOMATION.request(websocket_control, "shell", root=False,
                       command="mv " + root + "/state.json " + root + "/previous-state.json")
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        widget = configuration_widget(saved_configuration(websocket_control), INSTANCE_WRAPPER)
        if widget["state"].get("legacy") == "kept":
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    else:
        raise AssertionError("Legacy embedded widget state was not migrated")
    assert read_json_file(websocket_control, root + "/state.json")["legacy"] == "kept"
    assert "state" not in read_json_file(websocket_control, root + ".json")


@pytest.mark.parametrize("protocol", ["websocket", "mcp", "http"])
def test_widget_instance_state_updates_use_the_same_store(websocket_control, protocol, monkeypatch):
    from . import test_remote_device_control as remote

    monkeypatch.setitem(remote.TOOLS, "automationDispatch", "dikciz_automation_dispatch")
    configured = instance_configuration(saved_configuration(websocket_control))
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    current = wait_instance(websocket_control, "first", {"text":"home-first"})
    states = {**current["itemStates"], "second":{"text":protocol}}
    address = "1H1V-" + INSTANCE_WRAPPER
    result = remote.remote_call(protocol, "automationDispatch", {
        "sourceWidgetAddress":address,
        "actions":[{"type":"patchWidget", "widgetAddress":address, "values":{"itemStates":states}}],
    })
    assert result["actions"][0]["outcome"] == "executed"
    updated = wait_instance(websocket_control, "second", {"text":protocol})
    assert updated["itemStates"]["first"] == {"text":"home-first"}
    path = instance_path(FIXTURE_HOME_PAGE_ID, INSTANCE_WRAPPER, "second") + "/state.json"
    assert read_json_file(websocket_control, path) == {"text":protocol}
    other = instance_path(FIXTURE_NOTES_PAGE_ID, OTHER_WRAPPER, "second") + "/state.json"
    assert read_json_file(websocket_control, other) == {"text":"other-second"}


def test_widget_instance_state_preserves_existing_long_wrapper_ids(websocket_control):
    configured = instance_configuration(saved_configuration(websocket_control))
    configured["limits"]["maxIdentifierCharacters"] = 128
    identifier = "w" + "x" * 80
    configuration_widget(configured, INSTANCE_WRAPPER)["id"] = identifier
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    path = instance_path(FIXTURE_HOME_PAGE_ID, identifier, "first") + "/state.json"
    assert read_json_file(websocket_control, path) == {"text":"home-first"}


def test_widget_instance_state_bad_file_keeps_last_valid_state(websocket_control):
    configured = instance_configuration(saved_configuration(websocket_control))
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    widget = wait_instance(websocket_control, "second", {"text":"home-second"}, require_runtime=True)
    generation = widget["state"]["generation"]
    path = instance_path(FIXTURE_HOME_PAGE_ID, INSTANCE_WRAPPER, "second") + "/state.json"
    event = "configuration_reload_rejected"
    before_logs = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SCRIPT_LOGS)
    previous_rejections = {record["timestamp"] for record in before_logs.get("records", []) if record.get("event") == event}
    try:
        AUTOMATION.request(websocket_control, "fileWrite", path=path, overwrite=True, dataBase64="W10=")
        deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
        while time.monotonic() < deadline:
            logs = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SCRIPT_LOGS)
            if any(record.get("event") == event and record["timestamp"] not in previous_rejections for record in logs.get("records", [])):
                break
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
        else:
            raise AssertionError("Malformed instance state was not rejected by the watcher")
        current = configuration_widget(saved_configuration(websocket_control), INSTANCE_WRAPPER)
        assert current["itemStates"]["second"] == {"text":"home-second"}
        assert current["state"]["generation"] == generation
    finally:
        write_json_file(websocket_control, path, {"text":"recovered-state"})
    recovered = wait_instance(websocket_control, "second", {"text":"recovered-state"})
    assert recovered["state"]["generation"] == generation


def test_widget_instance_state_moves_owned_files_with_wrapper(websocket_control):
    configured = instance_configuration(saved_configuration(websocket_control))
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    wait_instance(websocket_control, "first", {"text":"home-first"})
    old_root = instance_path(FIXTURE_NOTES_PAGE_ID, OTHER_WRAPPER)
    write_json_file(websocket_control, old_root + "/items/second/custom.json", {"kept":True})
    current = saved_configuration(websocket_control)
    moved = configuration_widget(current, OTHER_WRAPPER)
    configuration_page(current, FIXTURE_HOME_PAGE_ID)["widgets"] = [moved]
    configuration_page(current, FIXTURE_NOTES_PAGE_ID)["widgets"] = []
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=current)
    new_root = instance_path(FIXTURE_HOME_PAGE_ID, OTHER_WRAPPER)
    assert read_json_file(websocket_control, new_root + "/items/second/state.json") == {"text":"other-second"}
    assert read_json_file(websocket_control, new_root + "/items/second/custom.json") == {"kept":True}
    previous_names = directory_names(websocket_control, f"/sdcard/Dikciz/pages/{FIXTURE_NOTES_PAGE_ID}/widgets")
    assert OTHER_WRAPPER not in previous_names
    assert OTHER_WRAPPER + ".json" not in previous_names


def test_widget_instance_state_html_file_helpers_keep_the_owner_path(websocket_control):
    configured = instance_configuration(saved_configuration(websocket_control))
    widget = configuration_widget(configured, INSTANCE_WRAPPER)
    widget["javascript"] += """
window.addEventListener("dikciz-ready", async () => {
    const owner = selfWidget.itemFor(document.querySelector("[data-value=first]"));
    const errors = [];
    try {
        await owner.writeFile("draft.txt", btoa("owned draft"));
        const file = await owner.readFile("draft.txt");
        for (const name of ["../escape", "", "/absolute"]) {
            try { await owner.readFile(name); errors.push("accepted"); }
            catch (error) { errors.push(error.code); }
        }
        try { await owner.writeFile("state.json", btoa("{}")); errors.push("accepted"); }
        catch (error) { errors.push(error.code); }
        const saved = await selfWidget.get();
        await selfWidget.patchState({...saved.state, fileProbe:{path:owner.storagePath, data:file.dataBase64, errors}});
    } catch (error) {
        const saved = await selfWidget.get();
        await selfWidget.patchState({...saved.state, fileProbe:{error:error.code || error.message}});
    }
});
"""
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    probe = None
    while time.monotonic() < deadline:
        probe = configuration_widget(saved_configuration(websocket_control), INSTANCE_WRAPPER)["state"].get("fileProbe")
        if probe:
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    root = instance_path(FIXTURE_HOME_PAGE_ID, INSTANCE_WRAPPER, "first")
    assert probe == {"path":root, "data":base64.b64encode(b"owned draft").decode(), "errors":["validation_failed"] * 4}
    result = AUTOMATION.request(websocket_control, "fileRead", path=root + "/draft.txt")
    assert base64.b64decode(result["dataBase64"]) == b"owned draft"
    assert read_json_file(websocket_control, root + "/state.json") == {"text":"home-first"}


def test_widget_instance_state_concurrent_patches_preserve_other_fields(websocket_control):
    configured = instance_configuration(saved_configuration(websocket_control))
    configuration_widget(configured, INSTANCE_WRAPPER)["javascript"] = ""
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    barrier = Barrier(3)
    iterations = 10
    address = "1H1V-" + INSTANCE_WRAPPER

    def patch_field(field):
        with connected_websocket_control() as connection:
            for counter in range(iterations):
                values = {"counter":counter} if field == "state" else {"first":{"text":"counter-" + str(counter)}, "second":{}, "third":{}}
                barrier.wait(timeout=CONFIGURATION_SAVE_TIMEOUT_SECONDS)
                result = AUTOMATION.request(connection, "automationDispatch", sourceWidgetAddress=address,
                                            actions=[{"type":"patchWidget", "widgetAddress":address, "values":{field:values}}])
                assert result["actions"][0]["outcome"] == "executed"

    def select_page():
        with connected_websocket_control() as connection:
            for counter in range(iterations):
                barrier.wait(timeout=CONFIGURATION_SAVE_TIMEOUT_SECONDS)
                page_id = FIXTURE_NOTES_PAGE_ID if counter % 2 == 0 else FIXTURE_HOME_PAGE_ID
                AUTOMATION.request(connection, AUTOMATION.TYPE_SELECT_PAGE, pageId=page_id)

    with ThreadPoolExecutor(max_workers=3) as executor:
        tasks = [executor.submit(patch_field, field) for field in ("state", "itemStates")]
        tasks.append(executor.submit(select_page))
        for task in tasks:
            task.result(timeout=CONFIGURATION_SAVE_TIMEOUT_SECONDS * iterations)
    widget = configuration_widget(saved_configuration(websocket_control), INSTANCE_WRAPPER)
    assert widget["state"] == {"counter":iterations - 1}
    assert widget["itemStates"]["first"] == {"text":"counter-" + str(iterations - 1)}
    root = instance_path(FIXTURE_HOME_PAGE_ID, INSTANCE_WRAPPER)
    assert read_json_file(websocket_control, root + "/state.json") == widget["state"]
    assert read_json_file(websocket_control, root + "/items/first/state.json") == widget["itemStates"]["first"]
