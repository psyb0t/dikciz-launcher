"""The reference preserves complete discovery schemas and rejects ambiguous input."""

import importlib.util
import json
from pathlib import Path
import pytest

SPEC = importlib.util.spec_from_file_location("command_reference", Path(__file__).resolve().parents[2] / "scripts/generate-dikciz-command-reference.py")
REFERENCE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REFERENCE)


def tool(name):
    return {"name": name, "description": "Fixture command.", "inputSchema": {"type": "object", "properties": {"nested": {"type": "object", "properties": {"value": {"type": "integer", "minimum": 1}}}}, "required": ["nested"], "additionalProperties": False}}


def test_complete_schema_and_order():
    first, second = tool("alpha"), tool("beta")
    output = REFERENCE.render({"tools": [second, first]})
    assert output.index("## alpha") < output.index("## beta")
    schemas = [json.loads(block.split("\n```", 1)[0]) for block in output.split("```json\n")[1:]]
    assert schemas == [first["inputSchema"], second["inputSchema"]]
    assert "2 discovered MCP tools" in output


@pytest.mark.parametrize("document", [{}, {"tools": []}, {"tools": [tool("same"), tool("same")]}, {"tools": [tool("bad name")]}, {"tools": [{"name": "missing"}]}, {"tools": [1]}])
def test_reject_invalid_discovery(document):
    with pytest.raises(ValueError):
        REFERENCE.render(document)
