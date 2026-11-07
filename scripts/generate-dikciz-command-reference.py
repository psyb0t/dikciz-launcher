"""Render the complete command input reference from captured MCP discovery."""

import importlib.util
import json
import logging
from pathlib import Path
import re
from string import Template

INPUT = Path(".testing/mcp-command-discovery.json")
OUTPUT = Path("docs/command-reference.md")
LOG_PATH = Path(".testing/command-reference-generation.log")
LOGGER = logging.getLogger(__name__)
NAME_PATTERN = re.compile(r"[A-Za-z0-9_.-]+")
TOOL_TEMPLATE = Template("## $name\n\n$description\n\n```json\n$schema\n```\n")
HEADER = Template("""---
title: "Command reference"
description: "Look up every discovered MCP tool and ordinary command, with complete input schemas, parameters, defaults, bounds and language invocations."
permalink: /command-reference/
nav_order: 3
parent: "Remote control"
---

# Command reference

<!-- Regenerate with make dikciz-command-docs from captured tools/list discovery. -->

This reference contains $count discovered MCP tools and their complete input schemas. Discovery from the installed build is authoritative. Transport-independent procedures, Android permissions and results are described in the [automation guide](automation-control.md), [documentation index](index.md) and focused task guides. MCP tools use the same ordinary commands as HTTP, WebSocket, HTML and Lua. The [developer manual](developer-manual.md) defines runtimes, results and the task-guide index. [JavaScript](javascript-api.md), [Lua](lua-api.md), [typed actions](typed-actions.md) and [token permissions](token-permissions.md) provide the language and authorization contracts.

Schemas include nested fields, required values, defaults and bounds. autoRequestPermissions requests owner review; it never grants access or retries the operation. Inspect returned state and errors before continuing.

""")


def parameter_table(schema):
    rows = [
        "| Parameter | Required | Type | Default | Definition and bounds |",
        "| --- | --- | --- | --- | --- |",
    ]
    required = schema.get("required", [])
    for name, field in sorted(schema.get("properties", {}).items()):
        kind = field.get("type", "JSON value")
        description = field.get("description", "See the complete schema below.")
        constraints = {
            key: field[key]
            for key in (
                "enum",
                "minimum",
                "maximum",
                "minLength",
                "maxLength",
                "minItems",
                "maxItems",
                "pattern",
            )
            if key in field
        }
        if constraints:
            description += " " + json.dumps(constraints, ensure_ascii=False)
        description = description.replace("|", "\\|").replace("\n", " ")
        default = json.dumps(field["default"]) if "default" in field else "Not declared"
        rows.append(
            f"| `{name}` | {'Yes' if name in required else 'No'} | `{kind}` | `{default}` | {description} |"
        )
    return "\n".join(rows) + "\n\n"


def binding_reference(tool):
    metadata = tool.get("_meta", {}).get("dikciz")
    if not metadata:
        return ""
    command = metadata["command"]
    return f"""Ordinary command: `{command}`. [Token scope definitions](token-permissions.md#{command.lower()}). [Result envelope and task guides](developer-manual.md#result-contract).

WebView invocation: `await dikciz.command("{command}", fields)`. Headless JavaScript: `context.request(Object.assign({{}}, fields, {{type: "{command}"}}))`. Lua: set `request.type = "{command}"`, then call `context.request(request)`. `fields`/`request` must contain the required parameters below. WebView `command` unwraps `result`; backend requests return the complete response envelope. [JavaScript methods](javascript-api.md) and [Lua types and errors](lua-api.md).

"""


def render(document):
    if (
        not isinstance(document, dict)
        or not isinstance(document.get("tools"), list)
        or not document["tools"]
    ):
        raise ValueError("Discovery must contain a nonempty tools array")
    if any(not isinstance(tool, dict) for tool in document["tools"]):
        raise ValueError("Each tool must be an object")
    seen = set()
    sections = []
    index = ["## Command index", "", "| Ordinary command | MCP tool |", "| --- | --- |"]
    for tool in sorted(document["tools"], key=lambda entry: str(entry.get("name", ""))):
        name = tool.get("name")
        description = tool.get("description")
        schema = tool.get("inputSchema")
        if (
            not isinstance(name, str)
            or not NAME_PATTERN.fullmatch(name)
            or name in seen
        ):
            raise ValueError("Tool names must be unique valid identifiers")
        if (
            not isinstance(description, str)
            or not isinstance(schema, dict)
            or schema.get("type") != "object"
        ):
            raise ValueError("Each tool needs a description and object input schema")
        seen.add(name)
        command = tool.get("_meta", {}).get("dikciz", {}).get("command", name)
        index.append(
            f"| [`{command}`](#{name.lower()}) | [`{name}`](#{name.lower()}) |"
        )
        sections.append(
            TOOL_TEMPLATE.substitute(
                name=name,
                description=description
                + "\n\n"
                + binding_reference(tool)
                + parameter_table(schema),
                schema=json.dumps(schema, indent=2, ensure_ascii=False),
            )
        )
    return (
        HEADER.substitute(count=len(seen))
        + "\n".join(index)
        + "\n\n"
        + "\n".join(sections)
    )


def main():
    specification = importlib.util.spec_from_file_location(
        "dikciz_reference_logging", Path(__file__).with_name("dikciz-control.py")
    )
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    module.configure_logging(LOG_PATH)
    try:
        document = json.loads(INPUT.read_text())
        text = render(document)
        temporary = OUTPUT.with_suffix(".md.new")
        temporary.write_text(text)
        temporary.replace(OUTPUT)
        LOGGER.info(
            "Command reference generated", extra={"tool_count": len(document["tools"])}
        )
    except (OSError, ValueError, TypeError) as exception:
        LOGGER.error(
            "Command reference generation failed",
            extra={"error_class": type(exception).__name__},
        )
        raise


if __name__ == "__main__":
    main()
