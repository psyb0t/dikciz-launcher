"""Shared fixtures for release-style Dikciz tests on the shared emulator."""

from __future__ import annotations

import os
import subprocess
from pathlib import Path

import pytest


LAB_ROOT = Path(__file__).resolve().parents[2]
ARTIFACT_DIRECTORY = Path(os.environ.get("ANDROID_LAB_ARTIFACT_DIR", ""))
DEVICE_OPERATION_SCRIPT = LAB_ROOT / "scripts/lab-device.sh"
SHARED_EMULATOR_INSTANCE = "shared"
SHARED_EMULATOR_SERIAL = "emulator:5556"
OPERATION_TIMEOUT_SECONDS = 300
TEST_REAL_ENVIRONMENT_VALUE = "1"
AUDIT_MODULE_NAME = "test_audit.py"


class DeviceOperationError(AssertionError):
    """A required device operation did not complete successfully."""


def run_device_operation(operation: str, **environment: str) -> str:
    is_real_device_test = os.environ.get("TEST_REAL") == TEST_REAL_ENVIRONMENT_VALUE
    if not is_real_device_test and os.environ.get("DIKCIZ_TEST_IN_FORWARD") != "true":
        pytest.fail("Dikciz release UI tests must run through the shared emulator forward service")
    if not is_real_device_test and os.environ.get("ANDROID_LAB_INSTANCE") != SHARED_EMULATOR_INSTANCE:
        pytest.fail("Dikciz release UI tests must run on the shared emulator")
    if not ARTIFACT_DIRECTORY.is_dir():
        pytest.fail("Android lab artifacts directory is unavailable")
    device_environment = {
        **os.environ,
        "ANDROID_LAB_ARTIFACT_DIR": str(ARTIFACT_DIRECTORY),
        "ANDROID_LAB_KEEP_ADB_SERVER": "true",
        **environment,
    }
    if not is_real_device_test:
        device_environment["ANDROID_LAB_SERIAL"] = SHARED_EMULATOR_SERIAL
    completed = subprocess.run(
        ["bash", str(DEVICE_OPERATION_SCRIPT), operation],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        timeout=OPERATION_TIMEOUT_SECONDS,
        env=device_environment,
    )
    if completed.returncode != 0:
        raise DeviceOperationError(
            "shared device operation "
            f"{operation} failed with exit {completed.returncode}:\n"
            f"stdout:\n{completed.stdout}\n"
            f"stderr:\n{completed.stderr}",
        )
    return completed.stdout


def pytest_collection_modifyitems(items: list[pytest.Item]) -> None:
    """Collect the log audit last.

    The audit asserts the day's log records every control surface the suite
    exercises, which only holds once the rest of the suite has run. Its file
    name otherwise sorts it into the middle of the run, where most of those
    surfaces have not been touched yet, and the audit fails on events its own
    ordering made impossible. Sorting is stable, so every other test keeps the
    order it was collected in.
    """
    items.sort(key=lambda item: AUDIT_MODULE_NAME in str(item.path))
