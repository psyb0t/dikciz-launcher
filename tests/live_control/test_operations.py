"""Persistent activity metadata and restricted token lifecycle through real listeners."""

import concurrent.futures
import time

import pytest

from .test_remote_tokens import (
    PROTOCOLS, admin, call, catalogue, create, http_command, token_owner,
)
from . import *


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_operations_token_lifecycle(token_owner, protocol):
    result = admin(token_owner, "create", name="Expiring reader", permissions=["sms.read"],
                   expiresAt=int(time.time() * 1000) + 300000, requestsPerMinute=100,
                   smsAddresses=["5550101"])
    token, item = result["token"], result["item"]
    assert item["requestsPerMinute"] == 100 and item["smsAddresses"] == ["5550101"]
    assert not item["expired"] and item["lastUsedAt"] is None
    identity = call(token, "remoteAccessInfo", protocol=protocol)
    assert identity["permissions"] == ["sms.read"]
    assert identity["smsAddresses"] == ["5550101"] and identity["requestsPerMinute"] == 100
    assert identity["expiresAt"] == item["expiresAt"]
    call(token, "contentQuery", {"resource": "sms"}, protocol, "remote_permission_denied")
    call(token, "contentQuery", {"resource": "sms", "filter": {"address": "5550102"}},
         protocol, "remote_permission_denied")
    device_command("pm grant eu.psyb0t.dikciz.launcher.debug android.permission.READ_SMS")
    page = call(token, "contentQuery", {"resource": "sms", "columns": ["_id"],
                "filter": {"address": "5550101"}, "limit": 1}, protocol)
    assert isinstance(page["items"], list)
    for command in ("activityRead", "resourceUsage", "activityCancel"):
        call(token, command, {}, protocol, "remote_permission_denied")
    listed = next(row for row in admin(token_owner, "list")["tokens"] if row["id"] == item["id"])
    assert listed["lastUsedAt"] is not None
    rotated = admin(token_owner, "rotate", id=item["id"])
    assert rotated["item"]["id"] == item["id"] and rotated["token"] != token
    assert http_command(token, "remoteAccessInfo")[0] == 401
    assert call(rotated["token"], "remoteAccessInfo", protocol=protocol)["id"] == item["id"]
    admin(token_owner, "update", id=item["id"], expiresAt=int(time.time() * 1000) - 1)
    assert http_command(rotated["token"], "remoteAccessInfo")[0] == 401
    expired = next(row for row in admin(token_owner, "list")["tokens"] if row["id"] == item["id"])
    assert expired["expired"] is True


def test_operations_token_rate_concurrency(token_owner):
    result = admin(token_owner, "create", name="Limited", permissions=["sms.read"], requestsPerMinute=3)
    token = result["token"]
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        responses = list(pool.map(lambda _: http_command(token, "remoteAccessInfo"), range(8)))
    accepted = [body for status, body in responses if body["type"] == "result"]
    denied = [body for status, body in responses if body["type"] == "error"]
    assert len(accepted) == 3 and len(denied) == 5
    assert {body["code"] for body in denied} == {"remote_rate_limited"}
    admin(token_owner, "update", id=result["item"]["id"], requestsPerMinute=0)
    assert call(token, "remoteAccessInfo")["permissions"] == ["sms.read"]


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_operations_activity_pagination_and_redaction(token_owner, protocol):
    token, identifier = create(token_owner, ["sms.read"], name="Activity reader")
    for _ in range(13):
        call(token, "remoteAccessInfo", protocol=protocol)
    identifiers = []
    for offset, count, more in ((0, 5, True), (5, 5, True), (10, 3, False)):
        page = call(token_owner, "activityRead", {"actor": identifier, "offset": offset, "limit": 5}, protocol)
        assert page["total"] == 13 and len(page["items"]) == count and page["hasMore"] is more
        for row in page["items"]:
            assert row["command"] == "remoteAccessInfo" and row["outcome"] == "completed"
            assert row["outcomeKnown"] and row["durationMilliseconds"] >= 0
            assert token not in str(row)
            assert not {"arguments", "result", "body", "recipient", "token"} & row.keys()
        identifiers.extend(row["id"] for row in page["items"])
    assert len(set(identifiers)) == 13
    assert call(token_owner, "activityRead", {"actor": identifier, "offset": 13}, protocol)["items"] == []
    assert call(token_owner, "activityRead", {"actor": identifier, "offset": 99999}, protocol)["items"] == []
    resources = call(token_owner, "resourceUsage", protocol=protocol)
    assert resources["batteryAttributionAvailable"] is False
    assert any(row["actor"] == identifier and row["commands"] == 13 for row in resources["actors"])
    call(token_owner, "activityRead", {"limit": 0}, protocol, "validation_failed")
    call(token_owner, "activityRead", {"offset": -1}, protocol, "validation_failed")


@pytest.mark.parametrize("fields", [
    {"expiresAt": -1}, {"expiresAt": True}, {"expiresAt": "tomorrow"},
    {"requestsPerMinute": -1}, {"requestsPerMinute": 10001}, {"requestsPerMinute": 1.5},
    {"smsAddresses": [None]}, {"smsAddresses": [""]}, {"smsAddresses": ["a", "a"]},
    {"smsAddresses": "any"}, {"smsAddresses": ["a"] * 65},
])
def test_operations_token_policy_validation(token_owner, fields):
    call(token_owner, "remoteTokens", {"action": "create", "name": "Invalid",
         "permissions": ["sms.read"], **fields}, error="validation_failed")
