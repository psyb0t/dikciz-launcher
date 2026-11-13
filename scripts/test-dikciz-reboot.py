"""Two-phase recovery assertions; the outer lab runner survives the guest reboot."""

import argparse
import http.client
import importlib.util
import json
import logging
import os
from pathlib import Path
import uuid

LOGGER = logging.getLogger(__name__)
PACKAGE = "eu.psyb0t.dikciz.launcher.debug"
PORT = 19102
ARTIFACT = Path("/artifacts/reboot-recovery-state.json")


def call(command, **fields):
    connection = http.client.HTTPConnection("127.0.0.1", PORT, timeout=45)
    try:
        connection.request("POST", "/v1/automation", json.dumps({"type": command, "requestId": str(uuid.uuid4()), **fields}),
                           {"Content-Type": "application/json"})
        response = connection.getresponse()
        document = json.loads(response.read())
        if response.status != 200 or document.get("type") != "result":
            raise AssertionError((command, response.status, document))
        return document["result"]
    finally:
        connection.close()


def prepare():
    assert call("deviceInfo")["application"]["packageName"] == PACKAGE
    identifier = "fixture_reboot_" + uuid.uuid4().hex[:12]
    call("customAppInstall", manifest={"version": 1, "id": identifier, "title": "Reboot recovery fixture",
         "description": "Private emulator recovery drill", "language": "javascript", "events": []},
         html="<main>Recovery fixture</main>", css="", javascript="",
         backgroundSource="function onEvent(event) { if(event.type !== 'fixture.timer') return {actions:[]}; return {actions:[{type:'patchState',values:{deliveries:(context.state.deliveries||0)+1}}]}; }",
         confirm=True)
    call("customAppState", appId=identifier, action="replace", state={"saved": "reboot fixture", "deliveries": 0})
    path = "/sdcard/Dikciz/apps/" + identifier + "/fixture.bin"
    call("fileWrite", path=path, dataBase64="AP8BAg==", overwrite=True)
    call("customAppSchedule", appId=identifier, action="schedule", timerId="pending", delayMilliseconds=600000, event="fixture.timer")
    ARTIFACT.write_text(json.dumps({"appId": identifier, "boot": call("customAppRuntime")["boot"], "path": path}))
    LOGGER.info("Reboot fixture persisted")


def verify():
    saved = json.loads(ARTIFACT.read_text())
    assert saved["appId"].startswith("fixture_reboot_")
    assert call("customAppRuntime")["boot"] != saved["boot"], "Android did not actually reboot"
    app = call("customAppRead", appId=saved["appId"])
    assert app["state"] == {"saved": "reboot fixture", "deliveries": 0}, app
    assert app["enabled"] is True
    assert app["timers"][0]["state"] == "needs_review", app["timers"]
    assert call("fileRead", path=saved["path"])["dataBase64"] == "AP8BAg=="
    call("customAppControl", appId=saved["appId"], action="remove", confirm=True)
    LOGGER.info("Reboot recovery verified", extra={"timer_state": "needs_review", "duplicate_deliveries": 0})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=("prepare", "verify"))
    args = parser.parse_args()
    if os.environ.get("DIKCIZ_TEST_IN_FORWARD") != "true" or os.environ.get("ANDROID_LAB_INSTANCE") != "shared":
        parser.error("Run make dikciz-test-reboot against the private shared lab")
    specification = importlib.util.spec_from_file_location("recovery_logging", Path(__file__).with_name("dikciz-control.py"))
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    module.configure_logging(Path("/artifacts/reboot-recovery.log"))
    try:
        (prepare if args.phase == "prepare" else verify)()
    except (OSError, ValueError, AssertionError) as exception:
        LOGGER.error("Reboot recovery failed", extra={"error_class": type(exception).__name__})
        raise


if __name__ == "__main__":
    main()
