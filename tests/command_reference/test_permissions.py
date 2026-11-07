"""Generated references preserve the complete public grant and binding contract."""

import importlib.util
from pathlib import Path
import pytest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("permission_reference", ROOT / "scripts/generate-dikciz-permission-reference.py")
REFERENCE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REFERENCE)
COMMAND_SPEC = importlib.util.spec_from_file_location("binding_reference", ROOT / "scripts/generate-dikciz-command-reference.py")
COMMAND = importlib.util.module_from_spec(COMMAND_SPEC)
COMMAND_SPEC.loader.exec_module(COMMAND)


def discovery():
    scopes = [{"id": "command.example", "selectors": {}}, {"id": "command.example.action.read", "selectors": {"action": "read"}}]
    grants = {"permissions": [{"id": "owner", "title": "Full owner", "description": "Every operation."}] +
              [{"id": item["id"], "title": item["id"], "description": "Example operation."} for item in scopes]}
    tools = {"tools": [{"name": "dikciz_example", "description": "Read an example.",
                       "_meta": {"dikciz": {"command": "example", "operationGrants": scopes}},
                       "inputSchema": {"type": "object", "properties": {"action": {"type": "string", "enum": ["read", "write"], "default": "read", "description": "Read or write."}}, "required": ["action"]}}]}
    return grants, tools


def test_every_grant_has_a_definition_and_command_reference():
    grants, tools = discovery()
    text = REFERENCE.render(grants, tools)
    assert text == REFERENCE.render(grants, tools)
    for grant in grants["permissions"]:
        assert "`" + grant["id"] + "`" in text
    assert "command-reference.md#dikciz_example" in text
    assert "action=read" in text
    assert "not to a particular file path" in text


def test_command_reference_names_bindings_parameters_and_defaults():
    _, tools = discovery()
    text = COMMAND.render(tools)
    assert 'dikciz.command("example", fields)' in text
    assert 'context.request(request)' in text
    assert '| `action` | Yes | `string` | `"read"` |' in text
    assert 'token-permissions.md#example' in text
    assert "## Command index" in text
    assert "[`example`](#dikciz_example)" in text


@pytest.mark.parametrize("case", ["empty", "duplicate", "missing_command", "orphan"])
def test_incomplete_or_ambiguous_permission_catalogue_fails(case):
    grants, tools = discovery()
    if case == "empty":
        grants["permissions"] = []
    elif case == "duplicate":
        grants["permissions"].append(grants["permissions"][0])
    elif case == "missing_command":
        grants["permissions"] = grants["permissions"][:1]
    else:
        grants["permissions"].append({"id": "command.orphan", "title": "Orphan", "description": "Not installed."})
    with pytest.raises(ValueError):
        REFERENCE.render(grants, tools)
