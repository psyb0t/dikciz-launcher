"""Render all live remote grants and their operation coverage."""

import importlib.util
import json
import logging
from pathlib import Path

INPUT = Path(".testing/remote-grant-discovery.json")
TOOLS = Path(".testing/mcp-command-discovery.json")
OUTPUT = Path("docs/token-permissions.md")
LOGGER = logging.getLogger(__name__)


def cell(value):
    return str(value).replace("|", "\\|").replace("\n", " ")


def render(document, discovery):
    grants = document.get("permissions")
    tools = discovery.get("tools")
    if (
        not isinstance(grants, list)
        or not grants
        or not isinstance(tools, list)
        or not tools
    ):
        raise ValueError("Nonempty grant and tool discovery are required")
    identifiers = [grant["id"] for grant in grants]
    if len(identifiers) != len(set(identifiers)):
        raise ValueError("Duplicate permission ID")
    lines = [
        '---\ntitle: "Token permission reference"\ndescription: "Look up every named token grant and exact command, action and resource selector, including broad authority, validation and access errors."\npermalink: /token-permissions/\nnav_order: 2\nparent: "Remote control"\n---\n\n'
        + "# Token permission reference",
        "",
        "<!-- Regenerate with make dikciz-permission-docs from installed discovery. -->",
        "",
        "Every current remote grant is listed below. Grant IDs are exact and case-sensitive. Existing named grants remain compatible; command grants add explicit operation/action/resource access. HTTP, WebSocket and MCP share discovery and enforcement. Android permissions are separate. See [token setup](remote-access-tokens.md) and the [developer manual](developer-manual.md).",
        "",
        "## Scope rules",
        "",
        "Grants are additive alternatives. A broad grant is not narrowed by adding a narrower one. owner must be selected alone. command.<type> allows every accepted argument of that command. Suffixes such as .action.stat and .resource.sms require those exact explicit selector values. Combined resource/action suffixes require both. Other required parameters and validation still apply. Invalid direct calls fail before Android permission prompts or effects.",
        "",
        "Operation scopes apply across the device, not to a particular file path, package, app ID, session or SDK member unless that operation explicitly enforces such ownership. Explicit launcher UI-action grants can reach Dikciz settings; ui.control remains restricted to other apps. File writes/moves/copies into watched source or configuration can reload executable code. Code/source installation, script/app invocation, generic SDK, shell, intents, configuration changes and credential administration can confer broad owner-equivalent authority. A camera-start grant does not imply image-stream-read or file-read access. Select related result/stop commands separately. Jobs additionally require the nested command's grants and enforce caller ownership of records.",
        "",
        "Basic device/control/authentication status, self access information and packaged documentation/tool search remain available to valid authenticated clients. These baseline tools do not grant phone mutations or owner search collections. Local owner bindings do not use a bearer token. Read-only chat independently rejects mutations. Existing events.read receives the shared event feed; granting a journal command does not enable live WebSocket broadcasts.",
        "",
        "## Named feature grants",
        "",
        "| Grant | Name | Definition | MCP operations |",
        "| --- | --- | --- | --- |",
    ]
    for grant in grants:
        if not grant["id"].startswith("command."):
            operations = (
                "All operations"
                if grant["id"] == "owner"
                else ", ".join(
                    f"[{cell(name)}](command-reference.md#{name})"
                    for name in grant.get("tools", [])
                )
            )
            lines.append(
                f"| `{cell(grant['id'])}` | {cell(grant['title'])} | {cell(grant['description'])} | {operations} |"
            )
    lines += [
        "",
        "## Operation index",
        "",
        "| Ordinary command | MCP tool and parameters |",
        "| --- | --- |",
    ]
    operations = []
    covered = set()
    for tool in sorted(tools, key=lambda value: value["_meta"]["dikciz"]["command"]):
        metadata = tool["_meta"]["dikciz"]
        command = metadata["command"]
        scopes = metadata["operationGrants"]
        if not scopes or "command." + command not in identifiers:
            raise ValueError("Missing explicit operation grant: " + command)
        for scope in scopes:
            if scope["id"] not in identifiers:
                raise ValueError("Tool refers to unknown grant")
            covered.add(scope["id"])
        lines.append(
            f"| [{command}](#{command.lower()}) | [{tool['name']}](command-reference.md#{tool['name']}) |"
        )
        operations.append((command, tool, scopes))
    if covered != {value for value in identifiers if value.startswith("command.")}:
        raise ValueError("Unreferenced command grant")
    for command, tool, scopes in operations:
        lines += [
            "",
            "## " + command,
            "",
            tool["description"],
            "",
            f"MCP: [`{tool['name']}`](command-reference.md#{tool['name']}). The linked reference contains required and optional parameters, full nested schemas and language invocation patterns.",
            "",
            "| Grant | Exact selectors | Definition |",
            "| --- | --- | --- |",
        ]
        for scope in scopes:
            selectors = (
                ", ".join(f"{key}={value}" for key, value in scope["selectors"].items())
                or "All accepted arguments"
            )
            definition = "Allows this command with the listed explicit selectors; other arguments retain the command's validation and documented scope."
            lines.append(
                f"| `{cell(scope['id'])}` | {cell(selectors)} | {definition} |"
            )
    lines += [
        "",
        "## Restrictions and errors",
        "",
        "Missing grants return remote_permission_denied. Unknown/duplicate grants return validation_failed. Invalid credentials fail authentication. Token expiry, enabled state, rotation, revocation, rolling request limits and exact SMS address restrictions are separate checks; see [token administration](remote-access-tokens.md). A previously accepted effect cannot be recalled by revoking a token.",
        "",
        "The picker searches grant IDs, titles and descriptions without dropping checked items. Common feature grants appear first; Show command permissions reveals the operation catalogue. Search also finds command grants directly. Review both the command definition and the Android access it needs before granting it.",
        "",
    ]
    return "\n".join(lines)


def main():
    specification = importlib.util.spec_from_file_location(
        "permission_reference_logging", Path(__file__).with_name("dikciz-control.py")
    )
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    module.configure_logging(Path(".testing/permission-reference-generation.log"))
    try:
        document = json.loads(INPUT.read_text())
        tools = json.loads(TOOLS.read_text())
        text = render(document, tools)
        temporary = OUTPUT.with_suffix(".md.new")
        temporary.write_text(text)
        temporary.replace(OUTPUT)
        LOGGER.info(
            "Permission reference generated",
            extra={"grant_count": len(document["permissions"])},
        )
    except (OSError, ValueError, TypeError, KeyError) as exception:
        LOGGER.error(
            "Permission reference generation failed",
            extra={"error_class": type(exception).__name__},
        )
        raise


if __name__ == "__main__":
    main()
